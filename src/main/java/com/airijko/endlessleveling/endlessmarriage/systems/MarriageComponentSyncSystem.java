/*
 * Airijko Proprietary License
 *
 * Copyright (c) 2026 Airijko - Endless Marriage
 *
 * All rights reserved.
 */

package com.airijko.endlessleveling.endlessmarriage.systems;

import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.util.EntityRefUtil;
import com.airijko.endlessleveling.util.PlayerStoreSelector;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

import java.util.Map;
import java.util.UUID;

/**
 * Periodically pumps {@link MarriageDataManager#syncMarriageToComponent} so the
 * {@code MarriageComponent} stays populated on player entities. Mirrors EL core's
 * {@code PlayerComponentSyncSystem} pattern but addon-scoped — sync runs whether
 * or not EL is loaded (we only depend on EL's util helpers + ECS infrastructure).
 *
 * <p>5s cadence — marriage state changes infrequently and the component is a
 * read-only mirror, so sub-second freshness is unnecessary.
 */
public final class MarriageComponentSyncSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Query<EntityStore> PLAYER_QUERY = Query.any();
    private static final float TICK_INTERVAL_SECONDS = 5.0f;

    private final MarriageDataManager marriageDataManager;
    private float elapsed;

    public MarriageComponentSyncSystem(@Nonnull MarriageDataManager marriageDataManager) {
        this.marriageDataManager = marriageDataManager;
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, @Nonnull Store<EntityStore> store) {
        if (store == null || store.isShutdown() || marriageDataManager == null) {
            return;
        }

        elapsed += deltaSeconds;
        if (elapsed < TICK_INTERVAL_SECONDS) {
            return;
        }
        elapsed -= TICK_INTERVAL_SECONDS;

        Map<Integer, PlayerRef> playersByEntityIndex = PlayerStoreSelector.snapshotPlayersByEntityIndex(store);
        if (playersByEntityIndex.isEmpty()) {
            return;
        }

        store.forEachChunk(PLAYER_QUERY, (ArchetypeChunk<EntityStore> chunk,
                CommandBuffer<EntityStore> commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || !EntityRefUtil.isAliveAndUsable(ref, commandBuffer)) {
                    continue;
                }
                PlayerRef playerRef = playersByEntityIndex.get(ref.getIndex());
                if (playerRef == null || !playerRef.isValid()) {
                    continue;
                }
                UUID uuid = playerRef.getUuid();
                if (uuid == null) {
                    continue;
                }

                try {
                    marriageDataManager.syncMarriageToComponent(ref, commandBuffer, uuid);
                } catch (RuntimeException ex) {
                    LOGGER.atFine().withCause(ex).log(
                            "MarriageComponentSyncSystem skipped player %s due to bridge exception",
                            uuid);
                }
            }
        });
    }
}
