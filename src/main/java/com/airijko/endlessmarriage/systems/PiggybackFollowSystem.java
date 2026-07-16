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
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.TrigMathUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

/**
 * Per-tick system that slaves the rider's server-side {@link TransformComponent}
 * position to the carrier. This does NOT drive the rider's camera (a player's
 * client owns its own position; the camera follows the carrier because the
 * rider holds an engine {@code MountedComponent} anchored to the carrier — see
 * {@link PiggybackService}). Its job is spatial: as the carrier walks away from
 * where the piggyback started, keeping the rider co-located ensures the rider
 * stays inside the view range of players near the carrier, so the engine keeps
 * sending them the rider's MountedUpdate and the rider stays visible riding the
 * carrier. Without it the rider's server position would lag at the mount point
 * and the rider would vanish for onlookers once the carrier moved off.
 *
 * <p>The rider's velocity is set to match the carrier's each tick (rather than
 * zeroed) so the rider's body interpolates smoothly along the carrier's path
 * for onlookers instead of snapping per tick.
 *
 * <p>The system is per-store: a piggyback session is only synced if both
 * spouses are present in the currently-ticking store. Cross-world cases are
 * handled by {@code CarrierTeleportFollowSystem} (same-world Teleport
 * components) and {@code EndlessMarriage.preTeleportListener} (cross-world
 * portal teleports), which dismount cleanly before the rider's store
 * disappears underneath them.
 */
public final class PiggybackFollowSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    /**
     * How far ahead, in seconds, to project the rider along the carrier's
     * velocity. Compensates for the carrier client -> server -> rider position
     * round-trip so the rider tracks the carrier instead of lagging behind.
     * Roughly one server tick plus a typical round-trip; tune to taste.
     */
    private static final float FOLLOW_LEAD_SECONDS = 0.10f;

    /**
     * Vertical offset, in blocks, applied to the rider's slaved server position.
     * Kept at 0 (pure co-location): the visible "sitting on the back" offset is
     * the engine MountedComponent's client-side attachment offset
     * ({@code PiggybackService.DEFAULT_OFFSET}), not this server position.
     */
    private static final double RIDE_OFFSET_Y = 0.0d;

    /**
     * Grace period (seconds) the carrier may be absent from the rider's store
     * before the session is force-detached. A legitimate cross-world follow pulls
     * the rider out of this store (the pull's tick-safe transfer) well within this
     * window, so the timer only fires for a carrier that vanished without the
     * rider following — e.g. a teleport path that wasn't wired to pull, or a
     * carrier disconnect mid-window. Detaching then prevents the dead/stranded
     * session from snapping the rider back when the carrier reappears.
     */
    private static final float CARRIER_ABSENT_DETACH_SECONDS = 3.0f;

    private final PiggybackService piggybackService;
    private final MarriageConfig config;

    /**
     * rider UUID -&gt; accumulated seconds the carrier has been absent from the
     * rider's store. Keyed by rider; only the rider's own world store touches a
     * given key, but different riders may tick on different world threads, so this
     * is concurrent. Pruned each tick against the live session set.
     */
    private final java.util.Map<UUID, Float> carrierAbsentSeconds = new java.util.concurrent.ConcurrentHashMap<>();

    /** Wall-clock millis the last follow-tick failure log was emitted, so a per-tick race can't spam the log. */
    private volatile long lastFollowFailureLogMillis;

    public PiggybackFollowSystem(@Nonnull PiggybackService piggybackService, @Nonnull MarriageConfig config) {
        this.piggybackService = piggybackService;
        this.config = config;
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, @Nonnull Store<EntityStore> store) {
        if (store == null || store.isShutdown()) {
            return;
        }

        Map<UUID, UUID> sessions = piggybackService.getRiderToCarrierView();
        if (sessions.isEmpty()) {
            // No live sessions — drop any leftover absence timers.
            if (!carrierAbsentSeconds.isEmpty()) {
                carrierAbsentSeconds.clear();
            }
            return;
        }
        // Prune absence timers for sessions that have since ended (death-detach,
        // disconnect, dismount) so the map can't grow unbounded.
        if (!carrierAbsentSeconds.isEmpty()) {
            carrierAbsentSeconds.keySet().retainAll(sessions.keySet());
        }

        EntityStore entityStore = store.getExternalData();

        for (Map.Entry<UUID, UUID> entry : sessions.entrySet()) {
            UUID riderUuid = entry.getKey();
            UUID carrierUuid = entry.getValue();

            Ref<EntityStore> riderRef = entityStore.getRefFromUUID(riderUuid);
            if (riderRef == null || !riderRef.isValid()) {
                continue;
            }
            Ref<EntityStore> carrierRef = entityStore.getRefFromUUID(carrierUuid);
            if (carrierRef == null || !carrierRef.isValid()) {
                // Carrier is absent from the rider's store: a cross-world teleport
                // in flight (the rider should be getting pulled to the carrier), a
                // synthetic debug-NPC carrier in another store, or a carrier that
                // vanished. We must NEVER initiate a transfer from this tick body
                // (the documented transfer-race / interaction-tick IOOBE), so the
                // only action here is a detach-only safety net: if the carrier
                // stays absent past the grace window, tear the session down so the
                // rider can't be snapped back when the carrier reappears.
                float elapsed = carrierAbsentSeconds.merge(riderUuid, deltaSeconds, Float::sum);
                if (elapsed >= CARRIER_ABSENT_DETACH_SECONDS) {
                    carrierAbsentSeconds.remove(riderUuid);
                    piggybackService.clearForPlayer(riderUuid);
                }
                continue;
            }
            // Carrier is present again — reset any pending absence timer.
            if (!carrierAbsentSeconds.isEmpty()) {
                carrierAbsentSeconds.remove(riderUuid);
            }

            try {
                TransformComponent carrierTransform = store.getComponent(
                        carrierRef, TransformComponent.getComponentType());
                TransformComponent riderTransform = store.getComponent(
                        riderRef, TransformComponent.getComponentType());
                if (carrierTransform == null || riderTransform == null) {
                    continue;
                }

                // Carrier's server velocity, used both to lead the rider's
                // position (countering the carrier client -> server -> rider
                // round-trip that otherwise makes the rider visibly trail) and
                // to drive the rider's client-side interpolation below.
                Velocity carrierVelocity = store.getComponent(
                        carrierRef, Velocity.getComponentType());
                double vx = 0d, vy = 0d, vz = 0d;
                if (carrierVelocity != null) {
                    vx = carrierVelocity.getX();
                    vy = carrierVelocity.getY();
                    vz = carrierVelocity.getZ();
                }

                // Direct assign — bypasses Player.moveTo / addLocationChange so
                // we never accumulate collisionPositionOffset on the rider (the
                // rider's box is zero-volume anyway, so collision is a no-op).
                // Lead the carrier by a short extrapolation window so the rider
                // lands where the carrier is heading rather than where it just
                // was. The change is picked up by the entity tracker next sync
                // and pushed to clients.
                // Push the rider's body a small amount BEHIND the carrier along the
                // carrier's facing, so the rider's model doesn't overlap the carrier
                // (covering the camera) and the pose reads as a piggyback. Forward
                // (pitch=0) is (-sin(yaw), -cos(yaw)); "behind" is its negation. Only
                // the body moves — the rider's camera seat (PiggybackSeatStreamSystem)
                // stays on the carrier.
                double backX = 0d, backZ = 0d;
                double backOffset = config.getPiggybackBackOffset();
                if (backOffset != 0d) {
                    Rotation3f cRot = carrierTransform.getRotation();
                    backX = TrigMathUtil.sin(cRot.yaw()) * backOffset;
                    backZ = TrigMathUtil.cos(cRot.yaw()) * backOffset;
                }

                Vector3d carrierPos = carrierTransform.getPosition();
                riderTransform.getPosition().set(
                        carrierPos.x() + backX + vx * FOLLOW_LEAD_SECONDS,
                        carrierPos.y() + RIDE_OFFSET_Y + vy * FOLLOW_LEAD_SECONDS,
                        carrierPos.z() + backZ + vz * FOLLOW_LEAD_SECONDS);

                // Match the carrier's velocity instead of zeroing it. Zeroing
                // told every client the rider was standing still, so each
                // per-tick position assignment arrived as a hard snap with no
                // client-side prediction between ticks. Feeding the carrier's
                // velocity — including the client-prediction channel — lets the
                // rider interpolate smoothly along the carrier's path for both
                // the rider and onlookers.
                Velocity riderVelocity = store.getComponent(
                        riderRef, Velocity.getComponentType());
                if (riderVelocity != null) {
                    riderVelocity.set(vx, vy, vz);
                    riderVelocity.setClient(vx, vy, vz);
                }
            } catch (Exception ex) {
                // Wrong-thread / race during a teleport — skip this tick, but surface
                // a recurring bug instead of swallowing it silently forever.
                long now = System.currentTimeMillis();
                if (now - lastFollowFailureLogMillis > 60_000L) {
                    lastFollowFailureLogMillis = now;
                    LOGGER.atWarning().withCause(ex)
                            .log("Piggyback follow skipped a tick for %s (rate-limited log).", riderUuid);
                }
            }
        }
    }
}
