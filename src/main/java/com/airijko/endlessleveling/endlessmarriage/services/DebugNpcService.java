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
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns and tracks per-player in-memory debug NPCs used by /marry debug.
 *
 * <p>The NPC is a regular {@code NPCPlugin}-spawned entity (so it has a model
 * and renders to the client) but it is tracked here, not persisted, and is
 * paired with a synthetic UUID so the existing piggyback / kiss services can
 * reuse their per-UUID indexing without needing a real player on the other
 * end.
 */
public final class DebugNpcService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final MarriageConfig config;
    private final Map<UUID, DebugNpc> npcs = new ConcurrentHashMap<>();

    public DebugNpcService(@Nonnull MarriageConfig config) {
        this.config = config;
    }

    /**
     * Tracked debug NPC instance.
     *
     * @param ref            entity ref of the spawned NPC
     * @param syntheticUuid  fake UUID used to slot the NPC into the
     *                       piggyback rider/carrier indexes
     */
    public record DebugNpc(@Nonnull Ref<EntityStore> ref, @Nonnull UUID syntheticUuid) {}

    @Nullable
    public DebugNpc get(@Nonnull UUID playerUuid) {
        return npcs.get(playerUuid);
    }

    public boolean has(@Nonnull UUID playerUuid) {
        return npcs.containsKey(playerUuid);
    }

    /**
     * Spawns a debug NPC at the given player's current position. If the player
     * already has one, the existing NPC is removed first.
     */
    @Nullable
    public DebugNpc spawnFor(@Nonnull UUID playerUuid,
            @Nonnull Ref<EntityStore> playerRef,
            @Nonnull Store<EntityStore> store) {

        // Clean up any existing NPC for this player.
        despawn(playerUuid, store);

        TransformComponent transform;
        try {
            transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Could not read TransformComponent for debug NPC spawn.");
            return null;
        }
        if (transform == null) {
            return null;
        }

        Vector3d playerPos = transform.getPosition();
        // Offset slightly in front of the player so we can see the NPC.
        Vector3d spawnPos = new Vector3d(playerPos.x() + 1.0, playerPos.y(), playerPos.z());
        Rotation3f rotation = new Rotation3f(0f, 0f, 0f);

        String roleName = config.getDebugNpcRole();
        Object spawnResult;
        try {
            spawnResult = NPCPlugin.get().spawnNPC(store, roleName, null, spawnPos, rotation);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("NPCPlugin.spawnNPC threw for role %s.", roleName);
            return null;
        }
        if (spawnResult == null) {
            LOGGER.atWarning().log("NPCPlugin.spawnNPC returned null for role %s — role may be unknown.", roleName);
            return null;
        }

        Ref<EntityStore> npcRef = extractSpawnedEntityRef(spawnResult);
        if (npcRef == null) {
            LOGGER.atWarning().log("Could not extract Ref<EntityStore> from spawnNPC result for role %s.", roleName);
            return null;
        }

        DebugNpc npc = new DebugNpc(npcRef, UUID.randomUUID());
        npcs.put(playerUuid, npc);
        LOGGER.atInfo().log("Spawned debug NPC (%s) for player %s.", roleName, playerUuid);
        return npc;
    }

    /**
     * Removes the debug NPC associated with the given player, if any. Returns
     * true if an NPC was actually removed.
     */
    public boolean despawn(@Nonnull UUID playerUuid, @Nonnull Store<EntityStore> store) {
        DebugNpc npc = npcs.remove(playerUuid);
        if (npc == null) {
            return false;
        }
        try {
            store.removeEntity(npc.ref(), RemoveReason.REMOVE);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to removeEntity for debug NPC of player %s.", playerUuid);
        }
        LOGGER.atInfo().log("Despawned debug NPC for player %s.", playerUuid);
        return true;
    }

    /**
     * Mirrors the reflective approach used in MobWaveManager — the
     * {@code spawnNPC} return type is {@code Pair<Ref<EntityStore>,
     * INonPlayerCharacter>} but we avoid a hard compile-time dependency on
     * the Pair class by reading the first element via reflection.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static Ref<EntityStore> extractSpawnedEntityRef(@Nullable Object spawnResult) {
        if (spawnResult == null) {
            return null;
        }
        if (spawnResult instanceof Ref<?> directRef) {
            return (Ref<EntityStore>) directRef;
        }
        String[] methodCandidates = {"getLeft", "getFirst", "getKey", "left", "first"};
        for (String methodName : methodCandidates) {
            try {
                Method method = spawnResult.getClass().getMethod(methodName);
                Object value = method.invoke(spawnResult);
                if (value instanceof Ref<?> ref) {
                    return (Ref<EntityStore>) ref;
                }
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }
        String[] fieldCandidates = {"left", "first", "key"};
        for (String fieldName : fieldCandidates) {
            try {
                Field field = spawnResult.getClass().getField(fieldName);
                Object value = field.get(spawnResult);
                if (value instanceof Ref<?> ref) {
                    return (Ref<EntityStore>) ref;
                }
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }
        return null;
    }
}
