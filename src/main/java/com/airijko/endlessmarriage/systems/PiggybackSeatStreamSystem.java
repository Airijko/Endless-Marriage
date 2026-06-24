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
import com.airijko.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockMount;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.protocol.MountedUpdate;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes a piggyback rider's own camera follow the carrier by streaming a
 * server-authoritative {@link MountController#BlockMount} "seat" to the rider's
 * client every tick, with the seat positioned on the carrier.
 *
 * <p><b>Why this and not a normal {@code MountedComponent}.</b> The engine has
 * only two mount controllers ({@code Minecart}, {@code BlockMount}).
 * {@code Minecart} makes the entity holding the {@code MountedComponent} (the
 * rider) the <i>driver</i> — its client simulates the mount locally and writes
 * the mount target's transform — so a carrier that walks under its own power
 * never moves on the rider's client and the camera freezes. {@code BlockMount}
 * is the opposite: the seat's world position is dictated by the server and the
 * seated entity is purely passive. A player's client owns its own position, so
 * the server cannot move the rider's camera by writing the rider's transform —
 * the camera only follows a mount/seat the client is told to anchor to. So we
 * push a {@code BlockMount} {@link MountedUpdate} whose seat position tracks the
 * carrier; the carrier keeps driving normally and the rider cannot steer it.
 *
 * <p>This mirrors {@code MountSystems.TrackerUpdate}: it runs in
 * {@link EntityTrackerSystems#QUEUE_UPDATE_GROUP} (so per-viewer {@code visible}
 * sets are populated and {@code queueUpdate} is safe) and queues the update to
 * every viewer in the rider's {@code visibleTo} — which includes the rider
 * itself (it is a viewer of itself at distance 0), giving the rider the seat and
 * thus the following camera.
 *
 * <p>Onlooker body position and "don't wander off" are handled by
 * {@code PiggybackFollowSystem} (server position/velocity slaving); this system
 * only adds the client-side seat/camera. When a session ends, the seat is
 * removed from clients via {@code queueRemove}.
 */
public final class PiggybackSeatStreamSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    /** Sentinel meaning "block type id not resolved yet". */
    private static final int UNRESOLVED = Integer.MIN_VALUE;

    private final PiggybackService piggybackService;
    private final MarriageConfig config;

    private final ComponentType<EntityStore, EntityTrackerSystems.Visible> visibleComponentType;
    private final ComponentType<EntityStore, PlayerRef> playerRefComponentType;
    private final ComponentType<EntityStore, TransformComponent> transformComponentType;
    private final Query<EntityStore> query;

    /**
     * Rider UUIDs we have streamed a seat to and not yet removed. Lets us send a
     * one-shot seat removal once a session ends (while the rider is still visible
     * to the relevant viewers, which is required for {@code queueRemove}).
     */
    private final Set<UUID> seated = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Cached resolved block type id for the seat (resolved lazily on first tick). */
    private volatile int seatBlockTypeId = UNRESOLVED;

    /** Set once we've logged a stream failure, so a per-tick fault can't spam the log. */
    private volatile boolean loggedStreamFailure = false;

    public PiggybackSeatStreamSystem(@Nonnull PiggybackService piggybackService, @Nonnull MarriageConfig config) {
        this.piggybackService = piggybackService;
        this.config = config;
        this.visibleComponentType = EntityTrackerSystems.Visible.getComponentType();
        this.playerRefComponentType = PlayerRef.getComponentType();
        this.transformComponentType = TransformComponent.getComponentType();
        this.query = Query.and(this.visibleComponentType, this.playerRefComponentType);
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return EntityTrackerSystems.QUEUE_UPDATE_GROUP;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        // Few sessions; queues into shared per-viewer update maps and does
        // cross-entity reads — keep it single-threaded for safety.
        return false;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        if (!config.isPiggybackSeatStreamEnabled()) {
            return;
        }

        PlayerRef playerRef = archetypeChunk.getComponent(index, this.playerRefComponentType);
        if (playerRef == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();

        boolean isRider = piggybackService.isRiding(uuid);
        boolean wasSeated = seated.contains(uuid);
        if (!isRider && !wasSeated) {
            return;
        }

        EntityTrackerSystems.Visible visibleComponent = archetypeChunk.getComponent(index, this.visibleComponentType);
        if (visibleComponent == null) {
            // No viewers: nothing to stream/remove. Drop the seated marker so we
            // don't try to remove forever (the client cleared it on despawn).
            seated.remove(uuid);
            return;
        }
        Ref<EntityStore> riderRef = archetypeChunk.getReferenceTo(index);

        try {
            if (!isRider) {
                // Session ended: tell viewers (incl. the rider) to drop the seat.
                for (EntityTrackerSystems.EntityViewer viewer : visibleComponent.visibleTo.values()) {
                    viewer.queueRemove(riderRef, ComponentUpdateType.Mounted);
                }
                seated.remove(uuid);
                return;
            }

            UUID carrierUuid = piggybackService.getCarrierFor(uuid);
            if (carrierUuid == null) {
                return;
            }
            EntityStore entityStore = store.getExternalData();
            Ref<EntityStore> carrierRef = entityStore.getRefFromUUID(carrierUuid);
            if (carrierRef == null || !carrierRef.isValid()) {
                return; // carrier not in this store (cross-world / synthetic NPC)
            }
            TransformComponent carrierTransform = store.getComponent(carrierRef, this.transformComponentType);
            if (carrierTransform == null) {
                return;
            }

            Vector3d cPos = carrierTransform.getPosition();
            Rotation3f cRot = carrierTransform.getRotation();
            float seatY = (float) (cPos.y() + config.getPiggybackSeatHeight());
            Vector3f seatPos = new Vector3f((float) cPos.x(), seatY, (float) cPos.z());

            // Seat orientation: stream the carrier's LIVE yaw every tick so the
            // rider's camera always points where the carrier is walking, but LEVEL
            // the pitch/roll (rotation Vector3f is (x=pitch, y=yaw, z=roll) radians,
            // per BlockMountPoint.computeRotationEuler). Copying the carrier's pitch
            // (cRot.x) made the rider's view inherit the carrier's slight downward
            // walking-aim, so the rider looked at the ground; zeroing it locks the
            // view to the horizon instead. The seat POSITION already follows the
            // moving carrier; we hard-lock the facing to match.
            //
            // Independent look (the rider aiming up/down or turning on their own) is
            // NOT possible here, for either axis: the seat position must be re-sent
            // every tick (the carrier moves) and BlockMount bundles orientation into
            // that same packet, so the client re-applies the whole orientation each
            // tick and re-snaps the rider's view. Free-look was tried (a yaw cone /
            // capture-once orientation) and reverted — a seat can't both follow the
            // carrier and grant the rider independent look. So we stop fighting it
            // and just face the carrier, level.
            Vector3f seatRot = new Vector3f(0f, cRot.y, 0f);

            BlockMount blockMount = new BlockMount(BlockMountType.Seat, seatPos, seatRot, resolveSeatBlockTypeId());
            MountedUpdate update = new MountedUpdate(0, new Vector3f(0f, 0f, 0f), MountController.BlockMount, blockMount);

            for (EntityTrackerSystems.EntityViewer viewer : visibleComponent.visibleTo.values()) {
                viewer.queueUpdate(riderRef, update);
            }
            seated.add(uuid);
        } catch (Exception ex) {
            // Defensive: a viewer/visibility race must never crash the world tick.
            if (!loggedStreamFailure) {
                loggedStreamFailure = true;
                LOGGER.atWarning().withCause(ex)
                        .log("Piggyback seat stream skipped a tick for %s (logged once).", uuid);
            }
        }
    }

    private int resolveSeatBlockTypeId() {
        int cached = this.seatBlockTypeId;
        if (cached != UNRESOLVED) {
            return cached;
        }
        int resolved = 0;
        try {
            int idx = BlockType.getAssetMap().getIndex(config.getPiggybackSeatBlockId());
            if (idx >= 0) {
                resolved = idx;
            } else {
                LOGGER.atWarning().log("Piggyback seat block id '%s' not found; falling back to block index 0.",
                        config.getPiggybackSeatBlockId());
            }
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to resolve piggyback seat block id; using index 0.");
        }
        this.seatBlockTypeId = resolved;
        return resolved;
    }
}
