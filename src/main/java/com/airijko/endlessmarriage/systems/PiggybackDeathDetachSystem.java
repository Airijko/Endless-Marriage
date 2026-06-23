/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.systems;

import com.airijko.endlessleveling.util.EntityRefUtil;
import com.airijko.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Tears down a piggyback session the instant either participant dies.
 *
 * <p>Why this exists: dungeons kick players out on death. When a piggyback
 * rider dies it is kicked from the instance and (correctly) not returned — but
 * the in-memory session in {@link PiggybackService} would otherwise linger.
 * {@code PiggybackFollowSystem} slaves the rider's position to the carrier every
 * tick, so once the carrier later exits the instance back to the shared world,
 * the still-registered (dead, now far-away) rider would be snapped back onto the
 * carrier from thousands of blocks away. Detaching on death of <em>either</em>
 * side removes the session from the maps before the death-kick teleport runs, so
 * the follow system never re-converges them.
 *
 * <p>The teardown is a pure map removal ({@link PiggybackService#clearForPlayer}):
 * the piggyback rider holds no engine {@code MountedComponent} (the seat is a
 * server-streamed {@code BlockMount} from {@code PiggybackSeatStreamSystem}), so
 * there is no component to remove here. The streamed seat is dropped on the next
 * seat-stream tick (carrier-death case, rider still present) or by the client's
 * own despawn cleanup when the dead rider leaves the world (rider-death case).
 *
 * <p>Mirrors {@code DungeonPlayerDeathSystem}: player-only query so mob deaths
 * (mass casualties in raids) don't dispatch a handler that just returns.
 */
public final class PiggybackDeathDetachSystem extends DeathSystems.OnDeathSystem {

    private final PiggybackService piggybackService;

    public PiggybackDeathDetachSystem(@Nonnull PiggybackService piggybackService) {
        this.piggybackService = piggybackService;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                                 @Nonnull DeathComponent component,
                                 @Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PlayerRef playerRef = EntityRefUtil.tryGetComponent(store, ref, PlayerRef.getComponentType());
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return;
        }
        // Pure in-memory teardown — see class javadoc for why no ECS work is needed.
        piggybackService.clearForPlayer(uuid);
    }
}
