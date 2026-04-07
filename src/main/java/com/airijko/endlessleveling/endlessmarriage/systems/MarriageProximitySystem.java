/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.systems;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.data.MarriagePair;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticking system that checks proximity between married couples.
 * When spouses are within range, they gain the marriage Discipline bonus.
 * Also handles XP sharing via the XP grant listener registered in the main plugin.
 */
public class MarriageProximitySystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final float CHECK_INTERVAL_SECONDS = 2.0f;

    private final MarriageDataManager dataManager;
    private final MarriageConfig config;

    // Players who are currently near their spouse (eligible for marriage buffs)
    private final Set<UUID> playersNearSpouse = ConcurrentHashMap.newKeySet();

    private float timeSinceLastCheck = 0f;

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

        timeSinceLastCheck += deltaSeconds;
        if (timeSinceLastCheck < CHECK_INTERVAL_SECONDS) {
            return;
        }
        timeSinceLastCheck = 0f;

        double range = config.getProximityRange();
        double rangeSq = range * range;

        // Check each marriage pair
        for (MarriagePair pair : dataManager.getAllMarriages()) {
            checkCouple(pair, store, rangeSq);
        }
    }

    private void checkCouple(@Nonnull MarriagePair pair, @Nonnull Store<EntityStore> store, double rangeSq) {
        UUID p1 = pair.player1();
        UUID p2 = pair.player2();

        Universe universe = Universe.get();
        if (universe == null) {
            playersNearSpouse.remove(p1);
            playersNearSpouse.remove(p2);
            return;
        }

        PlayerRef ref1 = universe.getPlayer(p1);
        PlayerRef ref2 = universe.getPlayer(p2);

        if (ref1 == null || !ref1.isValid() || ref2 == null || !ref2.isValid()) {
            playersNearSpouse.remove(p1);
            playersNearSpouse.remove(p2);
            return;
        }

        Ref<EntityStore> entity1 = ref1.getReference();
        Ref<EntityStore> entity2 = ref2.getReference();

        if (entity1 == null || entity2 == null) {
            playersNearSpouse.remove(p1);
            playersNearSpouse.remove(p2);
            return;
        }

        // Must be in the same store (same world)
        Store<EntityStore> store1 = entity1.getStore();
        Store<EntityStore> store2 = entity2.getStore();
        if (store1 != store2) {
            playersNearSpouse.remove(p1);
            playersNearSpouse.remove(p2);
            return;
        }

        Vector3d pos1 = resolvePosition(entity1, store1);
        Vector3d pos2 = resolvePosition(entity2, store2);

        if (pos1 == null || pos2 == null) {
            playersNearSpouse.remove(p1);
            playersNearSpouse.remove(p2);
            return;
        }

        double dx = pos1.getX() - pos2.getX();
        double dy = pos1.getY() - pos2.getY();
        double dz = pos1.getZ() - pos2.getZ();
        double distSq = (dx * dx) + (dy * dy) + (dz * dz);

        if (distSq <= rangeSq) {
            playersNearSpouse.add(p1);
            playersNearSpouse.add(p2);
        } else {
            playersNearSpouse.remove(p1);
            playersNearSpouse.remove(p2);
        }
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
