/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.systems;

import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.MarriagePair;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticking system that checks proximity between married couples.
 * When spouses are within range, they gain the marriage Discipline bonus.
 * Also handles XP sharing via the XP grant listener registered in the main plugin.
 *
 * <p>The proximity check runs <em>per store</em>: each store's tick looks up
 * both spouses through {@code store.getExternalData().getRefFromUUID()} so the
 * subsequent {@code TransformComponent} reads happen on the correct store
 * thread. The previous implementation went through {@code Universe.getPlayer()
 * .getReference()} which is fine for global lookups but then called
 * {@code store.getComponent} from whatever thread happened to be ticking,
 * tripping the store's {@code assertThread()} guard and silently leaving the
 * couple flagged as "not near" until something else woke up the entity.
 */
public class MarriageProximitySystem extends TickingSystem<EntityStore> {

    private static final float CHECK_INTERVAL_SECONDS = 2.0f;

    private final MarriageDataManager dataManager;
    private final MarriageConfig config;

    // Players who are currently near their spouse (eligible for marriage buffs)
    private final Set<UUID> playersNearSpouse = ConcurrentHashMap.newKeySet();

    /**
     * Per-store throttle accumulator. The system ticks once per store per
     * server tick, so a single shared accumulator would advance N× faster on
     * a multi-world server.
     */
    private final Map<Store<EntityStore>, Float> timeSinceLastCheck = new ConcurrentHashMap<>();

    public MarriageProximitySystem(@Nonnull MarriageDataManager dataManager,
            @Nonnull MarriageConfig config) {
        this.dataManager = dataManager;
        this.config = config;
    }

    /**
     * Returns true if the player is currently within range of their spouse.
     */
    public boolean isNearSpouse(@Nonnull UUID uuid) {
        return playersNearSpouse.contains(uuid);
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, Store<EntityStore> store) {
        if (store == null || store.isShutdown()) {
            return;
        }

        float accumulated = timeSinceLastCheck.merge(store, deltaSeconds, (a, b) -> a + b);
        if (accumulated < CHECK_INTERVAL_SECONDS) {
            return;
        }
        timeSinceLastCheck.put(store, 0f);

        EntityStore entityStore = store.getExternalData();

        double range = config.getProximityRange();
        double rangeSq = range * range;

        // Check each marriage pair within this store. Couples whose players
        // are not both present in this store are simply skipped — the store
        // they actually live in will tick the same system on its own thread.
        for (MarriagePair pair : dataManager.getAllMarriages()) {
            checkCouple(pair, store, entityStore, rangeSq);
        }
    }

    private void checkCouple(@Nonnull MarriagePair pair,
            @Nonnull Store<EntityStore> store,
            @Nonnull EntityStore entityStore,
            double rangeSq) {
        UUID p1 = pair.player1();
        UUID p2 = pair.player2();

        Ref<EntityStore> entity1 = entityStore.getRefFromUUID(p1);
        Ref<EntityStore> entity2 = entityStore.getRefFromUUID(p2);

        boolean has1 = entity1 != null && entity1.isValid();
        boolean has2 = entity2 != null && entity2.isValid();

        // Only one (or neither) spouse is in this store. If exactly one is
        // here, their partner is in a different world / offline — they cannot
        // be "near" by definition, so clear the flag for the present spouse.
        // The absent spouse is left alone; another store's tick (or none, if
        // they are offline) is responsible for them.
        if (!has1 || !has2) {
            if (has1) {
                playersNearSpouse.remove(p1);
            }
            if (has2) {
                playersNearSpouse.remove(p2);
            }
            return;
        }

        Vector3d pos1 = resolvePosition(entity1, store);
        Vector3d pos2 = resolvePosition(entity2, store);

        if (pos1 == null || pos2 == null) {
            playersNearSpouse.remove(p1);
            playersNearSpouse.remove(p2);
            return;
        }

        double dx = pos1.x() - pos2.x();
        double dy = pos1.y() - pos2.y();
        double dz = pos1.z() - pos2.z();
        double distSq = (dx * dx) + (dy * dy) + (dz * dz);

        if (distSq <= rangeSq) {
            playersNearSpouse.add(p1);
            playersNearSpouse.add(p2);
        } else {
            playersNearSpouse.remove(p1);
            playersNearSpouse.remove(p2);
        }
    }

    /**
     * Drops the per-store throttle accumulator when a store shuts down so the
     * Map does not hold onto the dead Store reference.
     */
    public void onStoreShutdown(@Nonnull Store<EntityStore> store) {
        timeSinceLastCheck.remove(store);
    }

    @Nullable
    private Vector3d resolvePosition(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || store == null) {
            return null;
        }
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            return transform != null ? transform.getPosition() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
