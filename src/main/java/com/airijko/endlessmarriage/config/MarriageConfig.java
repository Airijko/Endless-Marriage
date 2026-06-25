/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.config;

import com.airijko.endlessmarriage.data.tiered.TieredRingCatalog;
import com.airijko.endlessmarriage.data.tiered.TieredRingTier;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class MarriageConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean requirePriestForMarriage = true;
    private boolean requireMagistrateForDivorce = true;
    private double proximityRange = 25.0;
    // How often the proximity check actually runs (per store). Kept deliberately
    // slow — the linger window below covers the gap so the buff stays smooth.
    private double proximityCheckIntervalSeconds = 5.0;
    // How long the "near spouse" flag lingers after a couple was last seen in
    // range. Lets the slow check coast and avoids the buff flickering off when
    // spouses briefly separate or one streams out of the chunk.
    private double proximityLingerSeconds = 30.0;
    private double disciplineBonusPercent = 25.0;
    private double xpShareMultiplier = 1.0;
    // Over-cap XP funnel: when one spouse is at their level cap, redirect the XP they
    // would otherwise burn (mob kills near spouse + raid/boss rewards) to the partner
    // who can still gain. Master on/off switch.
    private boolean xpOverflowFunnelEnabled = true;
    // How often (per capped earner) the funnel coalesces into a single chat message +
    // ledger entry. Funnels fire per XP grant; this throttles the player-facing noise.
    private double xpOverflowNotifyIntervalSeconds = 30.0;
    // How many recent rolled-up overflow events to retain per couple in overflow_log.json.
    private int xpOverflowLogMaxEntriesPerCouple = 50;
    private double officiateRange = 5.0;
    private String priestClassId = "priest";
    private String magistrateClassId = "magistrate";
    // Master switch for the entire piggyback/carry system. When false, /piggyback,
    // /carry and the right-click-spouse interaction are all refused and no mount
    // session can be started (the per-tick follow/seat/detach systems then stay
    // idle because nobody is ever registered as a rider).
    private boolean piggybackEnabled = true;
    private double piggybackDamageReductionPercent = 25.0;
    private double piggybackMaxRange = 5.0;
    // Stream a server-authoritative BlockMount "seat" to the rider's client each
    // tick so the rider's camera follows the carrier (the carrier drives via
    // normal movement). Kill-switch: false falls back to body-only carry (rider
    // moves for onlookers but their own camera will not follow).
    private boolean piggybackSeatStreamEnabled = true;
    // Vertical offset (blocks) of the streamed seat above the carrier's feet.
    private double piggybackSeatHeight = 1.0;
    // Block type id used for the BlockMount seat (affects the client's seated
    // pose). Resolved via BlockType.getAssetMap(); falls back to index 0 if absent.
    private String piggybackSeatBlockId = "Chair";
    // Horizontal offset (blocks) the rider's body is pushed BEHIND the carrier,
    // along the carrier's facing. Keeps the rider's model from overlapping the
    // carrier (covering the camera) and makes the pose read as a piggyback. The
    // rider's camera seat is unaffected. 0 = co-located (old behaviour).
    private double piggybackBackOffset = 0.45;
    // When true, a married couple is routed into the same dungeon instance just
    // like a party (without needing to form one): each spouse clicking the same
    // dungeon lands in the shared instance. Also the master switch consumed by
    // EndlessLeveling core's couple share-key routing. Kill-switch: false →
    // spouses get independent instances.
    private boolean sharedDungeonsEnabled = true;
    private double witnessMaxRange = 50.0;
    private double kissRange = 1.0;
    private double kissBuffDisciplinePercent = 10.0;
    private double kissBuffDurationSeconds = 3600.0;
    private double kissBuffCooldownSeconds = 57600.0;
    private String debugNpcRole = "Klops_Gentleman";

    // Tiered ring base values (attribute -> tier -> value). Falls back to
    // TieredRingCatalog.defaultBaseValue() for any missing cells.
    private final Map<SkillAttributeType, Map<TieredRingTier, Double>> tieredRingBaseValues =
            new EnumMap<>(SkillAttributeType.class);

    public boolean isRequirePriestForMarriage() {
        return requirePriestForMarriage;
    }

    public boolean isRequireMagistrateForDivorce() {
        return requireMagistrateForDivorce;
    }

    public double getProximityRange() {
        return proximityRange;
    }

    public double getProximityCheckIntervalSeconds() {
        return proximityCheckIntervalSeconds;
    }

    public double getProximityLingerSeconds() {
        return proximityLingerSeconds;
    }

    public double getDisciplineBonusPercent() {
        return disciplineBonusPercent;
    }

    public double getXpShareMultiplier() {
        return xpShareMultiplier;
    }

    public boolean isXpOverflowFunnelEnabled() {
        return xpOverflowFunnelEnabled;
    }

    public double getXpOverflowNotifyIntervalSeconds() {
        return xpOverflowNotifyIntervalSeconds;
    }

    public int getXpOverflowLogMaxEntriesPerCouple() {
        return xpOverflowLogMaxEntriesPerCouple;
    }

    public double getOfficiateRange() {
        return officiateRange;
    }

    public String getPriestClassId() {
        return priestClassId;
    }

    public String getMagistrateClassId() {
        return magistrateClassId;
    }

    public boolean isPiggybackEnabled() {
        return piggybackEnabled;
    }

    public double getPiggybackDamageReductionPercent() {
        return piggybackDamageReductionPercent;
    }

    public double getPiggybackMaxRange() {
        return piggybackMaxRange;
    }

    public boolean isPiggybackSeatStreamEnabled() {
        return piggybackSeatStreamEnabled;
    }

    public double getPiggybackSeatHeight() {
        return piggybackSeatHeight;
    }

    public String getPiggybackSeatBlockId() {
        return piggybackSeatBlockId;
    }

    public double getPiggybackBackOffset() {
        return piggybackBackOffset;
    }

    public boolean isSharedDungeonsEnabled() {
        return sharedDungeonsEnabled;
    }

    public double getWitnessMaxRange() {
        return witnessMaxRange;
    }

    public double getKissRange() {
        return kissRange;
    }

    public double getKissBuffDisciplinePercent() {
        return kissBuffDisciplinePercent;
    }

    public double getKissBuffDurationSeconds() {
        return kissBuffDurationSeconds;
    }

    public double getKissBuffCooldownSeconds() {
        return kissBuffCooldownSeconds;
    }

    public String getDebugNpcRole() {
        return debugNpcRole;
    }

    /**
     * Resolve a tiered ring base value from config, falling back to the
     * hardcoded {@link TieredRingCatalog#defaultBaseValue} if the user did not
     * override this (attribute, tier) cell in {@code config.json}.
     */
    public double getTieredRingBaseValue(@Nonnull SkillAttributeType attribute, @Nonnull TieredRingTier tier) {
        Map<TieredRingTier, Double> tierValues = tieredRingBaseValues.get(attribute);
        if (tierValues != null) {
            Double override = tierValues.get(tier);
            if (override != null) {
                return override;
            }
        }
        return TieredRingCatalog.defaultBaseValue(attribute, tier);
    }

    public void load(@Nonnull File configFile) {
        if (!configFile.exists()) {
            LOGGER.atWarning().log("Config file not found at %s, using defaults.", configFile.getAbsolutePath());
            return;
        }

        // Forward-migrate older user files: adds any new keys from the bundled
        // defaults and bumps config_version. Preserves all existing user values.
        MarriageConfigMigrator.migrate(configFile);

        try {
            String json = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                LOGGER.atWarning().log("Config file is empty or invalid, using defaults.");
                return;
            }

            if (root.has("require_priest_for_marriage")) {
                requirePriestForMarriage = root.get("require_priest_for_marriage").getAsBoolean();
            }
            if (root.has("require_magistrate_for_divorce")) {
                requireMagistrateForDivorce = root.get("require_magistrate_for_divorce").getAsBoolean();
            }
            if (root.has("proximity_range")) {
                proximityRange = root.get("proximity_range").getAsDouble();
            }
            if (root.has("proximity_check_interval_seconds")) {
                proximityCheckIntervalSeconds = root.get("proximity_check_interval_seconds").getAsDouble();
            }
            if (root.has("proximity_linger_seconds")) {
                proximityLingerSeconds = root.get("proximity_linger_seconds").getAsDouble();
            }
            if (root.has("discipline_bonus_percent")) {
                disciplineBonusPercent = root.get("discipline_bonus_percent").getAsDouble();
            }
            if (root.has("xp_share_multiplier")) {
                xpShareMultiplier = root.get("xp_share_multiplier").getAsDouble();
            }
            if (root.has("xp_overflow_funnel_enabled")) {
                xpOverflowFunnelEnabled = root.get("xp_overflow_funnel_enabled").getAsBoolean();
            }
            if (root.has("xp_overflow_notify_interval_seconds")) {
                xpOverflowNotifyIntervalSeconds = root.get("xp_overflow_notify_interval_seconds").getAsDouble();
            }
            if (root.has("xp_overflow_log_max_entries_per_couple")) {
                xpOverflowLogMaxEntriesPerCouple = root.get("xp_overflow_log_max_entries_per_couple").getAsInt();
            }
            if (root.has("officiate_range")) {
                officiateRange = root.get("officiate_range").getAsDouble();
            }
            if (root.has("priest_class_id")) {
                priestClassId = root.get("priest_class_id").getAsString();
            }
            if (root.has("magistrate_class_id")) {
                magistrateClassId = root.get("magistrate_class_id").getAsString();
            }
            if (root.has("piggyback_enabled")) {
                piggybackEnabled = root.get("piggyback_enabled").getAsBoolean();
            }
            if (root.has("piggyback_damage_reduction_percent")) {
                piggybackDamageReductionPercent = root.get("piggyback_damage_reduction_percent").getAsDouble();
            }
            if (root.has("piggyback_max_range")) {
                piggybackMaxRange = root.get("piggyback_max_range").getAsDouble();
            }
            if (root.has("piggyback_back_offset")) {
                piggybackBackOffset = root.get("piggyback_back_offset").getAsDouble();
            }
            if (root.has("marriage_shared_dungeons")) {
                sharedDungeonsEnabled = root.get("marriage_shared_dungeons").getAsBoolean();
            }
            if (root.has("witness_max_range")) {
                witnessMaxRange = root.get("witness_max_range").getAsDouble();
            }
            if (root.has("kiss_range")) {
                kissRange = root.get("kiss_range").getAsDouble();
            }
            if (root.has("kiss_buff_discipline_percent")) {
                kissBuffDisciplinePercent = root.get("kiss_buff_discipline_percent").getAsDouble();
            }
            if (root.has("kiss_buff_duration_seconds")) {
                kissBuffDurationSeconds = root.get("kiss_buff_duration_seconds").getAsDouble();
            }
            if (root.has("kiss_buff_cooldown_seconds")) {
                kissBuffCooldownSeconds = root.get("kiss_buff_cooldown_seconds").getAsDouble();
            }
            if (root.has("debug_npc_role")) {
                debugNpcRole = root.get("debug_npc_role").getAsString();
            }

            loadTieredRingValues(root);

            LOGGER.atInfo().log("Marriage config loaded: priest=%b, magistrate=%b, range=%.1f, discipline=%.1f%%, xpShare=%.2f, piggyback=%b (DR=%.1f%%), kissRange=%.1f, kissBuff=%.1f%%/%.0fs (cd %.0fs)",
                    requirePriestForMarriage, requireMagistrateForDivorce, proximityRange,
                    disciplineBonusPercent, xpShareMultiplier,
                    piggybackEnabled, piggybackDamageReductionPercent, kissRange,
                    kissBuffDisciplinePercent, kissBuffDurationSeconds, kissBuffCooldownSeconds);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load config.json, using defaults.");
        }
    }

    /**
     * Parse the optional {@code tiered_rings} section. Schema:
     * <pre>
     * "tiered_rings": {
     *   "life_force": { "e": 40, "d": 65, "c": 100, "b": 160, "a": 260, "s": 480 },
     *   ...
     * }
     * </pre>
     * Attribute keys match {@code SkillAttributeType.getConfigKey()} (lowercase).
     * Tier keys are the lowercased tier names ("e".."s"). Any missing attribute
     * or tier silently falls back to the hardcoded {@link TieredRingCatalog}
     * default at lookup time.
     */
    private void loadTieredRingValues(@Nonnull JsonObject root) {
        tieredRingBaseValues.clear();

        JsonElement raw = root.get("tiered_rings");
        if (raw == null || !raw.isJsonObject()) {
            return;
        }
        JsonObject tieredRings = raw.getAsJsonObject();
        int loaded = 0;

        for (SkillAttributeType attribute : SkillAttributeType.values()) {
            String attributeKey = attribute.getConfigKey();
            JsonElement attributeRaw = tieredRings.get(attributeKey);
            if (attributeRaw == null || !attributeRaw.isJsonObject()) {
                continue;
            }
            JsonObject perTier = attributeRaw.getAsJsonObject();
            EnumMap<TieredRingTier, Double> tierValues = new EnumMap<>(TieredRingTier.class);

            for (TieredRingTier tier : TieredRingTier.values()) {
                String tierKey = tier.configKey();
                JsonElement valueRaw = perTier.get(tierKey);
                if (valueRaw == null || valueRaw.isJsonNull()) {
                    // Try the uppercase form too in case the user wrote "E" instead of "e".
                    valueRaw = perTier.get(tierKey.toUpperCase(Locale.ROOT));
                }
                if (valueRaw == null || valueRaw.isJsonNull() || !valueRaw.isJsonPrimitive()) {
                    continue;
                }
                try {
                    tierValues.put(tier, valueRaw.getAsDouble());
                    loaded++;
                } catch (Exception ex) {
                    LOGGER.atWarning().log("tiered_rings.%s.%s is not a number, skipping.",
                            attributeKey, tierKey);
                }
            }

            if (!tierValues.isEmpty()) {
                tieredRingBaseValues.put(attribute, tierValues);
            }
        }

        LOGGER.atInfo().log("Loaded %d tiered ring base value overrides from config.", loaded);
    }

}
