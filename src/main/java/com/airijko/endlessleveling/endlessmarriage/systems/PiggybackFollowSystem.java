/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.systems;

import com.airijko.endlessleveling.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

/**
 * Per-tick system that copies the carrier's {@link TransformComponent}
 * position into the rider's transform so the rider's server-side position
 * actually follows the carrier. Without this the rider's camera stays glued
 * to wherever the {@code MountedComponent} was attached, even though the
 * client visually renders them on the carrier's back — the camera anchors to
 * the rider's server position, not the client visual offset.
 *
 * <p>This is safe to do every tick because {@link PiggybackService} shrinks
 * the rider's bounding box to a zero-volume box on mount, so the per-tick
 * position assignment never trips {@code PlayerProcessMovementSystem}'s
 * "Jump in location" collision push guard. The rider's velocity is also
 * cleared each tick so the engine's velocity sampling does not try to push
 * them away from the carrier.
 *
 * <p>The system is per-store: a piggyback session is only synced if both
 * spouses are present in the currently-ticking store. Cross-world cases are
 * handled by {@code CarrierTeleportFollowSystem} (same-world Teleport
 * components) and {@code EndlessMarriage.preTeleportListener} (cross-world
 * portal teleports), which dismount cleanly before the rider's store
 * disappears underneath them.
 */
public final class PiggybackFollowSystem extends TickingSystem<EntityStore> {

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

                // Direct assign — bypasses Player.moveTo / addLocationChange
                // so we never accumulate collisionPositionOffset on the rider
                // (the rider's box is zero-volume anyway, so collision is a
                // no-op for them). The position change is picked up by the
                // entity tracker on the next sync pass and pushed to clients,
                // which is what makes the rider's camera follow the carrier.
                riderTransform.getPosition().set(carrierTransform.getPosition());

                Velocity riderVelocity = store.getComponent(
                        riderRef, Velocity.getComponentType());
                if (riderVelocity != null) {
                    riderVelocity.setZero();
                }
            } catch (Exception ignored) {
                // Wrong-thread / race during a teleport — skip this tick.
            }
        }
    }
}
