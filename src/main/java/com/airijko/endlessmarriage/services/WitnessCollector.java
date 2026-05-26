/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.services;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Collects "witness" players for a marriage ceremony. A witness is any
 * online player in the same world as the priest, within the configured
 * witness radius, and not already part of the ceremony (priest, bride,
 * or groom).
 */
public final class WitnessCollector {

    private WitnessCollector() {
    }

    /**
     * @param priestStore the priest's entity store; only players in this same store are eligible
     * @param priestPos   the priest's world position used as the centre of the radius
     * @param maxRange    radius in blocks; players within this distance qualify as witnesses
     * @param excluded    UUIDs to skip (typically the priest, player1, player2)
     * @return witness UUIDs in iteration order, never null
     */
    @Nonnull
    public static List<UUID> collect(@Nonnull Store<EntityStore> priestStore,
            @Nonnull Vector3d priestPos,
            double maxRange,
            @Nonnull Set<UUID> excluded) {

        double rangeSq = maxRange * maxRange;
        List<UUID> witnesses = new ArrayList<>();

        for (PlayerRef ref : Universe.get().getPlayers()) {
            if (ref == null || !ref.isValid()) {
                continue;
            }
            UUID uuid = ref.getUuid();
            if (excluded.contains(uuid)) {
                continue;
            }
            Ref<EntityStore> entityRef = ref.getReference();
            if (entityRef == null || entityRef.getStore() != priestStore) {
                continue;
            }
            try {
                TransformComponent transform = priestStore.getComponent(entityRef, TransformComponent.getComponentType());
                if (transform == null) {
                    continue;
                }
                Vector3d pos = transform.getPosition();
                if (pos == null) {
                    continue;
                }
                double dx = pos.x() - priestPos.x();
                double dy = pos.y() - priestPos.y();
                double dz = pos.z() - priestPos.z();
                if ((dx * dx) + (dy * dy) + (dz * dz) <= rangeSq) {
                    witnesses.add(uuid);
                }
            } catch (Exception ignored) {
                // Player skipped if their transform can't be read.
            }
        }
        return witnesses;
    }
}
