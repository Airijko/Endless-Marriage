/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.data;

import com.airijko.endlessleveling.EndlessLeveling;
import com.airijko.endlessleveling.augments.AugmentRuntimeManager;
import com.airijko.endlessmarriage.data.tiered.TieredRingCatalog;
import com.airijko.endlessmarriage.data.tiered.TieredRingDefinition;
import com.airijko.endlessleveling.player.PlayerData;
import com.airijko.endlessleveling.player.PlayerDataManager;
import com.airijko.endlessleveling.player.SkillManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which {@link TieredRingDefinition} each player has equipped, persists
 * the equipped ring to disk, and applies the ring's additive attribute bonus
 * via EL's {@link AugmentRuntimeManager.AugmentRuntimeState#setAttributeBonus}
 * pipeline.
 *
 * The bonus piggybacks on the augment runtime registry so it is automatically
 * picked up by every {@code SkillManager.calculatePlayer*} path (LIFE_FORCE,
 * STAMINA, FLOW, STRENGTH, DEFENSE, HASTE, PRECISION, FEROCITY, DISCIPLINE,
 * SORCERY) without any further plumbing.
 *
 * For the three stat-backed attributes (LIFE_FORCE / STAMINA / FLOW) the
 * caller is expected to additionally invoke
 * {@link SkillManager#applyAllSkillModifiers} so the player's
 * {@code EntityStatMap} reflects the new max immediately. The other seven
 * attributes are read on demand by EL's combat / movement / XP code so they
 * pick up the bonus naturally on the next tick.
 */
public class TieredRingDataManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "tiered_rings.json";

    /** Source-id prefix used when registering ring bonuses with the augment runtime. */
    public static final String RING_BONUS_SOURCE_PREFIX = "tiered_ring_";

    private final File dataFolder;

    /** player UUID -> currently-equipped ring id. */
    private final Map<UUID, String> equippedRings = new ConcurrentHashMap<>();

    public TieredRingDataManager(@Nonnull File dataFolder) {
        this.dataFolder = dataFolder;
        dataFolder.mkdirs();
    }

    // ---- Queries ----

    @Nullable
    public TieredRingDefinition getEquippedRing(@Nonnull UUID uuid) {
        String id = equippedRings.get(uuid);
        return id == null ? null : TieredRingCatalog.byId(id);
    }

    public boolean hasRingEquipped(@Nonnull UUID uuid) {
        return equippedRings.containsKey(uuid);
    }

    // ---- Equip / unequip ----

    /**
     * Equip the given ring on the player and apply its bonus. The optional
     * {@code ref}/{@code accessor} are only required to refresh the entity
     * stat map for the three stat-backed attributes (LIFE_FORCE / STAMINA /
     * FLOW); pass {@code null} for both when calling from a context where the
     * entity isn't currently available (the bonus still gets registered and
     * will take effect on the next stat refresh).
     */
    public boolean equipRing(@Nonnull UUID uuid,
            @Nonnull String ringId,
            @Nullable Ref<EntityStore> ref,
            @Nullable ComponentAccessor<EntityStore> accessor) {

        TieredRingDefinition def = TieredRingCatalog.byId(ringId);
        if (def == null) {
            LOGGER.atWarning().log("equipRing: unknown ring id %s for %s", ringId, uuid);
            return false;
        }

        // Clear any previously-equipped ring's bonus first so we don't stack two
        // ring bonuses on the same player.
        clearAllRingBonuses(uuid);

        equippedRings.put(uuid, ringId);

        applyRingBonus(uuid, def);
        refreshEntityStats(uuid, ref, accessor);
        save();
        return true;
    }

    public boolean unequipRing(@Nonnull UUID uuid,
            @Nullable Ref<EntityStore> ref,
            @Nullable ComponentAccessor<EntityStore> accessor) {

        if (equippedRings.remove(uuid) == null) {
            return false;
        }

        clearAllRingBonuses(uuid);
        refreshEntityStats(uuid, ref, accessor);
        save();
        return true;
    }

    /**
     * Re-apply the persisted ring bonus on player join (the augment runtime is
     * in-memory only and would otherwise be empty after a server restart).
     */
    public void reapplyOnJoin(@Nonnull UUID uuid,
            @Nullable Ref<EntityStore> ref,
            @Nullable ComponentAccessor<EntityStore> accessor) {

        TieredRingDefinition def = getEquippedRing(uuid);
        if (def == null) {
            return;
        }
        applyRingBonus(uuid, def);
        refreshEntityStats(uuid, ref, accessor);
    }

    /**
     * Re-inject the equipped ring's runtime bonus after EL wiped all permanent
     * attribute bonuses on an augment-selection change.
     *
     * <p>EL's {@code AugmentExecutor} clears every permanent bonus (ours is
     * registered with {@code expiresAt == 0}) on any loadout/profile change and
     * the post-restart first sweep, then re-derives only its own augment-owned
     * passives. Without this re-apply the ring contributes nothing after the
     * first augment reconcile (which is why an equipped ring "does nothing" on
     * relog). Runtime-only — no {@code EntityStatMap} refresh: this fires
     * synchronously inside EL's reconcile pass (see
     * {@code EndlessLevelingAPI#notifyAugmentSelectionChanged}), so the
     * LIFE_FORCE max is reconciled by EL's own health pass on the same tick and
     * the percent attributes are read on demand. Touching the stat map here
     * would re-enter EL mid-reconcile; equip/join already do the full refresh.
     */
    public void reapplyOnAugmentSelectionChanged(@Nonnull UUID uuid) {
        TieredRingDefinition def = getEquippedRing(uuid);
        if (def == null) {
            return;
        }
        applyRingBonus(uuid, def);
    }

    // ---- Bonus plumbing ----

    private void applyRingBonus(@Nonnull UUID uuid, @Nonnull TieredRingDefinition def) {
        AugmentRuntimeManager.AugmentRuntimeState runtime = runtimeState(uuid);
        if (runtime == null) {
            return;
        }
        runtime.setAttributeBonus(def.attribute(),
                RING_BONUS_SOURCE_PREFIX + def.id(),
                def.baseValue(),
                0L /* permanent */);
    }

    /**
     * Zero out every ring-source bonus we may have registered for this player.
     * The augment runtime has no removeBySource API, so we overwrite each
     * known ring's bonus with 0 instead.
     */
    private void clearAllRingBonuses(@Nonnull UUID uuid) {
        AugmentRuntimeManager.AugmentRuntimeState runtime = runtimeState(uuid);
        if (runtime == null) {
            return;
        }
        for (TieredRingDefinition def : TieredRingCatalog.all()) {
            runtime.setAttributeBonus(def.attribute(),
                    RING_BONUS_SOURCE_PREFIX + def.id(),
                    0.0D,
                    0L);
        }
    }

    @Nullable
    private AugmentRuntimeManager.AugmentRuntimeState runtimeState(@Nonnull UUID uuid) {
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            return null;
        }
        AugmentRuntimeManager runtimeManager = plugin.getAugmentRuntimeManager();
        return runtimeManager == null ? null : runtimeManager.getRuntimeState(uuid);
    }

    private void refreshEntityStats(@Nonnull UUID uuid,
            @Nullable Ref<EntityStore> ref,
            @Nullable ComponentAccessor<EntityStore> accessor) {

        if (ref == null || accessor == null) {
            return;
        }
        EndlessLeveling plugin = EndlessLeveling.getInstance();
        if (plugin == null) {
            return;
        }
        SkillManager skillManager = plugin.getSkillManager();
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        if (skillManager == null || playerDataManager == null) {
            return;
        }
        PlayerData data = playerDataManager.get(uuid);
        if (data == null) {
            return;
        }
        try {
            skillManager.applyAllSkillModifiers(ref, accessor, data);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("refreshEntityStats failed for %s", uuid);
        }
    }

    // ---- Persistence ----

    public void load() {
        File file = new File(dataFolder, FILE_NAME);
        if (!file.exists()) {
            return;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("rings")) {
                return;
            }
            JsonArray array = root.getAsJsonArray("rings");
            equippedRings.clear();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("uuid") || !obj.has("ring_id")) {
                    continue;
                }
                UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
                String ringId = obj.get("ring_id").getAsString();
                if (TieredRingCatalog.byId(ringId) != null) {
                    equippedRings.put(uuid, ringId);
                }
            }
            LOGGER.atInfo().log("Loaded %d equipped tiered rings from disk.", equippedRings.size());
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load %s.", FILE_NAME);
        }
    }

    public void save() {
        File file = new File(dataFolder, FILE_NAME);
        try {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            for (Map.Entry<UUID, String> entry : equippedRings.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("uuid", entry.getKey().toString());
                obj.addProperty("ring_id", entry.getValue());
                array.add(obj);
            }
            root.add("rings", array);
            Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to save %s.", FILE_NAME);
        }
    }
}
