/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.services;

import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active piggyback sessions between married couples and provides
 * mount/dismount helpers. The MountedComponent itself is owned by the rider
 * entity in the ECS; this service mirrors the relationship in-memory so
 * other systems (damage reduction, UI) can query it cheaply.
 *
 * <p>Riders are kept indexed in a ConcurrentHashMap. When a rider dismounts,
 * dismount() must be called so the index stays consistent.
 */
public final class PiggybackService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    /** Default offset placing the rider 1.5 blocks above the carrier's transform. */
    private static final Vector3f DEFAULT_OFFSET = new Vector3f(0f, 1.5f, 0f);

    /** rider UUID -> carrier UUID */
    private final Map<UUID, UUID> riders = new ConcurrentHashMap<>();
    /** carrier UUID -> rider UUID (reverse for cleanup) */
    private final Map<UUID, UUID> carriers = new ConcurrentHashMap<>();

    private final MarriageDataManager dataManager;
    private final MarriageConfig config;

    public PiggybackService(@Nonnull MarriageDataManager dataManager,
            @Nonnull MarriageConfig config) {
        this.dataManager = dataManager;
        this.config = config;
    }

    public boolean isRiding(@Nonnull UUID uuid) {
        return riders.containsKey(uuid);
    }

    public boolean isCarrying(@Nonnull UUID uuid) {
        return carriers.containsKey(uuid);
    }

    /** Returns true if the player is participating in a piggyback session. */
    public boolean isInActivePiggyback(@Nonnull UUID uuid) {
        return riders.containsKey(uuid) || carriers.containsKey(uuid);
    }

    /** Result codes for {@link #tryMount}. */
    public enum MountResult {
        SUCCESS,
        NOT_MARRIED,
        SPOUSE_OFFLINE,
        SPOUSE_NOT_IN_WORLD,
        SPOUSE_DIFFERENT_WORLD,
        TOO_FAR,
        ALREADY_MOUNTED,
        SPOUSE_ALREADY_CARRYING,
        SPOUSE_IS_RIDING,
        ERROR
    }

    /**
     * Attempts to mount the given rider on top of their spouse.
     */
    @Nonnull
    public MountResult tryMount(@Nonnull UUID riderUuid,
            @Nonnull Ref<EntityStore> riderRef,
            @Nonnull Store<EntityStore> riderStore) {

        if (!dataManager.isMarried(riderUuid)) {
            return MountResult.NOT_MARRIED;
        }
        UUID spouseUuid = dataManager.getSpouse(riderUuid);
        if (spouseUuid == null) {
            return MountResult.NOT_MARRIED;
        }

        // Already mounted on someone (or someone is riding us, or our spouse already has a rider)
        if (riders.containsKey(riderUuid)) {
            return MountResult.ALREADY_MOUNTED;
        }
        if (carriers.containsKey(riderUuid)) {
            return MountResult.SPOUSE_IS_RIDING;
        }
        if (carriers.containsKey(spouseUuid)) {
            return MountResult.SPOUSE_ALREADY_CARRYING;
        }
        if (riders.containsKey(spouseUuid)) {
            // Spouse is riding someone else (shouldn't normally happen, but guard).
            return MountResult.SPOUSE_ALREADY_CARRYING;
        }

        PlayerRef spousePlayer = Universe.get().getPlayer(spouseUuid);
        if (spousePlayer == null || !spousePlayer.isValid()) {
            return MountResult.SPOUSE_OFFLINE;
        }
        Ref<EntityStore> spouseRef = spousePlayer.getReference();
        if (spouseRef == null) {
            return MountResult.SPOUSE_NOT_IN_WORLD;
        }
        Store<EntityStore> spouseStore = spouseRef.getStore();
        if (spouseStore != riderStore) {
            return MountResult.SPOUSE_DIFFERENT_WORLD;
        }

        Vector3d riderPos = positionOf(riderRef, riderStore);
        Vector3d spousePos = positionOf(spouseRef, spouseStore);
        if (riderPos == null || spousePos == null) {
            return MountResult.ERROR;
        }
        double dx = riderPos.getX() - spousePos.getX();
        double dy = riderPos.getY() - spousePos.getY();
        double dz = riderPos.getZ() - spousePos.getZ();
        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
        double maxRange = config.getPiggybackMaxRange();
        if (distSq > maxRange * maxRange) {
            return MountResult.TOO_FAR;
        }

        try {
            MountedComponent mounted = new MountedComponent(spouseRef, DEFAULT_OFFSET, MountController.Minecart);
            riderStore.addComponent(riderRef, MountedComponent.getComponentType(), mounted);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to attach MountedComponent for piggyback rider %s.", riderUuid);
            return MountResult.ERROR;
        }

        riders.put(riderUuid, spouseUuid);
        carriers.put(spouseUuid, riderUuid);
        LOGGER.atInfo().log("Piggyback started: %s riding %s", riderUuid, spouseUuid);
        return MountResult.SUCCESS;
    }

    /**
     * Debug-only mount on an arbitrary target entity (e.g. an in-memory NPC
     * spawned by the debug commands). Skips marriage and proximity checks.
     * The carrier is tracked under a synthetic UUID so the regular dismount /
     * cleanup paths still work.
     */
    @Nonnull
    public MountResult tryMountTarget(@Nonnull UUID riderUuid,
            @Nonnull Ref<EntityStore> riderRef,
            @Nonnull Store<EntityStore> riderStore,
            @Nonnull Ref<EntityStore> targetRef,
            @Nonnull UUID syntheticCarrierUuid) {

        if (riders.containsKey(riderUuid)) {
            return MountResult.ALREADY_MOUNTED;
        }
        if (carriers.containsKey(riderUuid)) {
            return MountResult.SPOUSE_IS_RIDING;
        }
        if (carriers.containsKey(syntheticCarrierUuid)) {
            return MountResult.SPOUSE_ALREADY_CARRYING;
        }

        try {
            MountedComponent mounted = new MountedComponent(targetRef, DEFAULT_OFFSET, MountController.Minecart);
            riderStore.addComponent(riderRef, MountedComponent.getComponentType(), mounted);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to attach MountedComponent for debug piggyback rider %s.", riderUuid);
            return MountResult.ERROR;
        }

        riders.put(riderUuid, syntheticCarrierUuid);
        carriers.put(syntheticCarrierUuid, riderUuid);
        LOGGER.atInfo().log("Debug piggyback started: %s riding NPC %s", riderUuid, syntheticCarrierUuid);
        return MountResult.SUCCESS;
    }

    /**
     * Removes the MountedComponent from the rider and clears in-memory tracking.
     * Returns true if a session was actually ended.
     */
    public boolean dismount(@Nonnull UUID riderUuid,
            @Nonnull Ref<EntityStore> riderRef,
            @Nonnull Store<EntityStore> riderStore) {
        if (!riders.containsKey(riderUuid)) {
            return false;
        }
        try {
            riderStore.removeComponent(riderRef, MountedComponent.getComponentType());
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to remove MountedComponent during dismount for %s.", riderUuid);
        }
        UUID carrierUuid = riders.remove(riderUuid);
        if (carrierUuid != null) {
            carriers.remove(carrierUuid);
        }
        LOGGER.atInfo().log("Piggyback ended: %s no longer riding %s", riderUuid, carrierUuid);
        return true;
    }

    /**
     * Forcibly clears any piggyback session involving the given player without
     * touching the ECS (used when a player disconnects, dies, or divorces — the
     * framework / cleanup logic will have already handled the component side).
     */
    public void clearForPlayer(@Nonnull UUID uuid) {
        UUID carrierUuid = riders.remove(uuid);
        if (carrierUuid != null) {
            carriers.remove(carrierUuid);
        }
        UUID riderUuid = carriers.remove(uuid);
        if (riderUuid != null) {
            riders.remove(riderUuid);
        }
    }

    /**
     * Convenience: dismount whichever side of the relationship the given player
     * is on. If they are the rider, removes the component from them. If they
     * are the carrier, locates the rider and removes the component from the rider.
     */
    public boolean dismountAny(@Nonnull UUID uuid) {
        if (riders.containsKey(uuid)) {
            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref != null && ref.isValid()) {
                Ref<EntityStore> entityRef = ref.getReference();
                if (entityRef != null) {
                    return dismount(uuid, entityRef, entityRef.getStore());
                }
            }
            // Fallback: drop tracking even if we can't reach the entity.
            clearForPlayer(uuid);
            return true;
        }
        if (carriers.containsKey(uuid)) {
            UUID riderUuid = carriers.get(uuid);
            if (riderUuid != null) {
                PlayerRef riderPlayer = Universe.get().getPlayer(riderUuid);
                if (riderPlayer != null && riderPlayer.isValid()) {
                    Ref<EntityStore> riderEntity = riderPlayer.getReference();
                    if (riderEntity != null) {
                        return dismount(riderUuid, riderEntity, riderEntity.getStore());
                    }
                }
            }
            clearForPlayer(uuid);
            return true;
        }
        return false;
    }

    @Nullable
    private Vector3d positionOf(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store) {
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
