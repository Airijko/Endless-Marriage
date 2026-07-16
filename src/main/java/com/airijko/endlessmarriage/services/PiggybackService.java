/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.services;

import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.util.PositionUtil;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3d;
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

    /** Cheap hot-path gate for the Refixes un-targeting bridge: any sessions at all? */
    public boolean hasActiveSessions() {
        return !riders.isEmpty();
    }

    /**
     * Order-independent: are these two UUIDs the rider and carrier of one active
     * piggyback session? Backs the Refixes {@code PiggybackPairs} resolver so the
     * combat mixins can let piggyback partners pass through each other's
     * attacks/projectiles. {@code riders} maps rider→carrier and {@code carriers}
     * maps carrier→rider, so checking both covers {@code a} in either role.
     */
    public boolean arePartners(@Nullable UUID a, @Nullable UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        return b.equals(riders.get(a)) || b.equals(carriers.get(a));
    }

    /**
     * Returns the carrier UUID for the given rider, or {@code null} if the
     * rider is not currently in a piggyback session.
     */
    @Nullable
    public UUID getCarrierFor(@Nonnull UUID riderUuid) {
        return riders.get(riderUuid);
    }

    /**
     * Returns the rider UUID for the given carrier, or {@code null} if the
     * carrier is not currently in a piggyback session.
     */
    @Nullable
    public UUID getRiderFor(@Nonnull UUID carrierUuid) {
        return carriers.get(carrierUuid);
    }

    /**
     * Returns the piggyback partner of the given player — the carrier if they are
     * riding, the rider if they are carrying — or {@code null} if they are not in
     * an active session. Order-independent; two cheap {@link Map#get} lookups.
     *
     * <p>Used by the Refixes collision mixins (via the {@code PiggybackPairs}
     * bridge) to resolve a shooter's partner exactly ONCE per projectile tick, so
     * the per-candidate collision check collapses to a single ref-equality.
     */
    @Nullable
    public UUID getPartnerFor(@Nullable UUID uuid) {
        if (uuid == null) {
            return null;
        }
        UUID carrier = riders.get(uuid);
        return carrier != null ? carrier : carriers.get(uuid);
    }

    /**
     * Live, read-only view of the rider-to-carrier index. The map is backed by
     * a {@link ConcurrentHashMap}, so callers can safely iterate it without
     * copying — but they must not mutate it. Used by tick systems that need to
     * walk every active piggyback session each frame.
     */
    @Nonnull
    public Map<UUID, UUID> getRiderToCarrierView() {
        return riders;
    }

    /** Result codes for {@link #tryMount} / {@link #tryCarry}. */
    public enum MountResult {
        SUCCESS,
        /** The piggyback/carry system is turned off in config (piggyback_enabled=false). */
        DISABLED,
        NOT_MARRIED,
        SPOUSE_OFFLINE,
        SPOUSE_NOT_IN_WORLD,
        SPOUSE_DIFFERENT_WORLD,
        TOO_FAR,
        ALREADY_MOUNTED,
        /** (carry) the sender is already carrying someone. */
        ALREADY_CARRYING,
        /** (carry) the sender is currently being carried and cannot carry. */
        SELF_IS_RIDING,
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

        if (!config.isPiggybackEnabled()) {
            return MountResult.DISABLED;
        }
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

        // Refuse if either spouse already holds an engine MountedComponent (seated in a
        // chair, riding a minecart, or on an NPC mount). The engine's
        // MountedComponent.clone() always rebuilds via the entity constructor, so when our
        // mount triggers an archetype change on a participant that already has a *block*
        // MountedComponent, the clone nulls out mountedToBlock — leaving a component with
        // both mountedToEntity and mountedToBlock null. MountSystems.TrackerUpdate then
        // throws "Couldn't create MountedUpdate packet for MountedComponent" on the next
        // tick and crashes the world. (The carrier gets a MountedByComponent added, which
        // is the archetype change that clones their seated MountedComponent.)
        if (riderStore.getComponent(riderRef, MountedComponent.getComponentType()) != null) {
            return MountResult.ALREADY_MOUNTED;
        }
        if (spouseStore.getComponent(spouseRef, MountedComponent.getComponentType()) != null) {
            return MountResult.SPOUSE_ALREADY_CARRYING;
        }

        Vector3d riderPos = PositionUtil.resolvePosition(riderRef, riderStore);
        Vector3d spousePos = PositionUtil.resolvePosition(spouseRef, spouseStore);
        if (riderPos == null || spousePos == null) {
            return MountResult.ERROR;
        }
        double dx = riderPos.x() - spousePos.x();
        double dy = riderPos.y() - spousePos.y();
        double dz = riderPos.z() - spousePos.z();
        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
        double maxRange = config.getPiggybackMaxRange();
        if (distSq > maxRange * maxRange) {
            return MountResult.TOO_FAR;
        }

        // No engine MountedComponent is attached. The only entity-mount controller
        // (MountController.Minecart) makes the *rider* the driver — it locally
        // simulates the mount and writes the mount target's transform — so a
        // self-walking carrier never moves on the rider's client and the camera
        // freezes. Instead the rider is made a passive, server-authoritative seat
        // (MountController.BlockMount) whose seat position PiggybackSeatStreamSystem
        // streams to follow the carrier every tick — the carrier drives via normal
        // player movement, the rider can't steer it, and the rider's camera tracks
        // the carrier. PiggybackFollowSystem additionally slaves the rider's server
        // position to the carrier (body co-location for onlookers + keeping the
        // rider inside nearby viewers' tracking range so the seat stream reaches
        // them). The rider's box is left full-size — a zero-volume box trips
        // EntityTrackerSystems.LODCull and re-introduces invisibility.
        riders.put(riderUuid, spouseUuid);
        carriers.put(spouseUuid, riderUuid);
        LOGGER.atInfo().log("Piggyback started: %s riding %s", riderUuid, spouseUuid);
        return MountResult.SUCCESS;
    }

    /**
     * Attempts to pick up the sender's spouse and carry them — the mirror image
     * of {@link #tryMount}. Here the sender is the <i>carrier</i> and their spouse
     * becomes the passive <i>rider</i> seated on the sender's back. The session is
     * registered under the same rider→carrier indexes, so the existing seat-stream,
     * follow, damage-reduction and dismount logic all apply unchanged (they key off
     * the maps, not on who initiated).
     */
    @Nonnull
    public MountResult tryCarry(@Nonnull UUID carrierUuid,
            @Nonnull Ref<EntityStore> carrierRef,
            @Nonnull Store<EntityStore> carrierStore) {

        if (!config.isPiggybackEnabled()) {
            return MountResult.DISABLED;
        }
        if (!dataManager.isMarried(carrierUuid)) {
            return MountResult.NOT_MARRIED;
        }
        UUID spouseUuid = dataManager.getSpouse(carrierUuid);
        if (spouseUuid == null) {
            return MountResult.NOT_MARRIED;
        }

        // Sender (carrier) must be free.
        if (carriers.containsKey(carrierUuid)) {
            return MountResult.ALREADY_CARRYING;
        }
        if (riders.containsKey(carrierUuid)) {
            // Sender is currently being carried — can't carry while riding.
            return MountResult.SELF_IS_RIDING;
        }
        // Spouse (would-be rider) must be free.
        if (riders.containsKey(spouseUuid) || carriers.containsKey(spouseUuid)) {
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
        if (spouseStore != carrierStore) {
            return MountResult.SPOUSE_DIFFERENT_WORLD;
        }

        // Same engine clone()-corruption guard as tryMount: refuse if either party
        // already holds a MountedComponent, else the archetype change crashes the world.
        if (carrierStore.getComponent(carrierRef, MountedComponent.getComponentType()) != null) {
            return MountResult.ALREADY_CARRYING;
        }
        if (spouseStore.getComponent(spouseRef, MountedComponent.getComponentType()) != null) {
            return MountResult.SPOUSE_ALREADY_CARRYING;
        }

        Vector3d carrierPos = PositionUtil.resolvePosition(carrierRef, carrierStore);
        Vector3d spousePos = PositionUtil.resolvePosition(spouseRef, spouseStore);
        if (carrierPos == null || spousePos == null) {
            return MountResult.ERROR;
        }
        double dx = carrierPos.x() - spousePos.x();
        double dy = carrierPos.y() - spousePos.y();
        double dz = carrierPos.z() - spousePos.z();
        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
        double maxRange = config.getPiggybackMaxRange();
        if (distSq > maxRange * maxRange) {
            return MountResult.TOO_FAR;
        }

        // Spouse is the rider seated on the sender (carrier). See tryMount for why
        // no engine MountedComponent is attached (PiggybackSeatStreamSystem streams a
        // server-authoritative BlockMount seat to the rider instead).
        riders.put(spouseUuid, carrierUuid);
        carriers.put(carrierUuid, spouseUuid);
        LOGGER.atInfo().log("Carry started: %s carrying %s", carrierUuid, spouseUuid);
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

        // Same engine clone()-corruption guard as tryMount: refuse if rider or target
        // already holds a MountedComponent, else the archetype change crashes the world.
        if (riderStore.getComponent(riderRef, MountedComponent.getComponentType()) != null) {
            return MountResult.ALREADY_MOUNTED;
        }
        if (targetRef.getStore().getComponent(targetRef, MountedComponent.getComponentType()) != null) {
            return MountResult.SPOUSE_ALREADY_CARRYING;
        }

        // See tryMount: no engine MountedComponent — the rider is a passive
        // BlockMount seat streamed to follow the target by PiggybackSeatStreamSystem.
        riders.put(riderUuid, syntheticCarrierUuid);
        carriers.put(syntheticCarrierUuid, riderUuid);
        LOGGER.atInfo().log("Debug piggyback started: %s riding NPC %s", riderUuid, syntheticCarrierUuid);
        return MountResult.SUCCESS;
    }

    /**
     * Removes the rider's MountedComponent and clears in-memory tracking.
     * Returns true if a session was actually ended.
     */
    public boolean dismount(@Nonnull UUID riderUuid,
            @Nonnull Ref<EntityStore> riderRef,
            @Nonnull Store<EntityStore> riderStore) {
        if (!riders.containsKey(riderUuid)) {
            return false;
        }
        try {
            riderStore.tryRemoveComponent(riderRef, MountedComponent.getComponentType());
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
     *
     * <p>The rider's BoundingBox is intentionally left untouched: while mounted
     * the rider keeps its normal full-size box. (An earlier version shrank it to
     * a zero-volume box to avoid a phantom carrier collision push — but players
     * don't physically collide with each other in this engine, and a zero-volume
     * box trips {@code EntityTrackerSystems.LODCull} (getMaximumThickness() == 0
     * is always &lt; ENTITY_LOD_RATIO * distanceSq for any distant viewer),
     * which removed the rider from every viewer's visible set and made the rider
     * invisible — and suppressed the MountedUpdate packet. Do NOT shrink it.)
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

    /**
     * Tears down every active piggyback/carry session, properly removing each
     * rider's MountedComponent. Used when the system is disabled at runtime (e.g.
     * {@code piggyback_enabled} flipped off and {@code /marry reload} run) so the
     * kill-switch takes effect immediately instead of waiting for each couple to
     * dismount manually. Returns the number of sessions ended.
     */
    public int dismountAllSessions() {
        if (riders.isEmpty()) {
            return 0;
        }
        // Snapshot rider UUIDs first — dismount() mutates the maps as it goes.
        int ended = 0;
        for (UUID riderUuid : new java.util.ArrayList<>(riders.keySet())) {
            if (riderUuid != null && dismountAny(riderUuid)) {
                ended++;
            }
        }
        // Defensive: drop any tracking that survived (unreachable entities, etc.).
        riders.clear();
        carriers.clear();
        if (ended > 0) {
            LOGGER.atInfo().log("Tore down %d active piggyback session(s).", ended);
        }
        return ended;
    }

}
