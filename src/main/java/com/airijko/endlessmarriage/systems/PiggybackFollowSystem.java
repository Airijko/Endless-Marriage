/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.systems;

import com.airijko.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

/**
 * Per-tick system that copies the carrier's {@link TransformComponent}
 * position (plus a small vertical seat offset) into the rider's transform so
 * the rider is carried along as an ordinary player. This — not an engine
 * {@code MountedComponent} — is what makes the piggyback work: the rider keeps
 * a normal player camera, and slaving its server position + velocity to the
 * carrier every tick makes that camera track the carrier smoothly. (An engine
 * MountedComponent with the only available {@code MountController.Minecart}
 * would instead put the rider's client into "driver" mode and freeze its
 * camera, because the carrier walks under its own power rather than being
 * steered by the rider — see {@link PiggybackService} for the full rationale.)
 *
 * <p>The rider's velocity is set to match the carrier's each tick (rather than
 * zeroed) so clients interpolate the rider smoothly along the carrier's path
 * instead of snapping per tick.
 *
 * <p>The system is per-store: a piggyback session is only synced if both
 * spouses are present in the currently-ticking store. Cross-world cases are
 * handled by {@code CarrierTeleportFollowSystem} (same-world Teleport
 * components) and {@code EndlessMarriage.preTeleportListener} (cross-world
 * portal teleports), which dismount cleanly before the rider's store
 * disappears underneath them.
 */
public final class PiggybackFollowSystem extends TickingSystem<EntityStore> {

    /**
     * How far ahead, in seconds, to project the rider along the carrier's
     * velocity. Compensates for the carrier client -> server -> rider position
     * round-trip so the rider tracks the carrier instead of lagging behind.
     * Roughly one server tick plus a typical round-trip; tune to taste.
     */
    private static final float FOLLOW_LEAD_SECONDS = 0.10f;

    /**
     * Vertical offset, in blocks, placing the rider above the carrier's
     * transform so they visibly sit on the carrier's back/shoulders rather than
     * clipping inside them. (With no engine MountedComponent there is no
     * client-side attachment offset, so we apply the seat height here.)
     */
    private static final double RIDE_OFFSET_Y = 1.0d;

    private final PiggybackService piggybackService;

    public PiggybackFollowSystem(@Nonnull PiggybackService piggybackService) {
        this.piggybackService = piggybackService;
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, @Nonnull Store<EntityStore> store) {
        if (store == null || store.isShutdown()) {
            return;
        }

        Map<UUID, UUID> sessions = piggybackService.getRiderToCarrierView();
        if (sessions.isEmpty()) {
            return;
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
                // Carrier left this store — could be cross-world teleport in
                // flight, or the carrier is a synthetic debug NPC in a
                // different store. Skip; the dismount/teleport hooks will
                // resolve this on the right side.
                continue;
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
                Vector3d carrierPos = carrierTransform.getPosition();
                riderTransform.getPosition().set(
                        carrierPos.x() + vx * FOLLOW_LEAD_SECONDS,
                        carrierPos.y() + RIDE_OFFSET_Y + vy * FOLLOW_LEAD_SECONDS,
                        carrierPos.z() + vz * FOLLOW_LEAD_SECONDS);

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
            } catch (Exception ignored) {
                // Wrong-thread / race during a teleport — skip this tick.
            }
        }
    }
}
