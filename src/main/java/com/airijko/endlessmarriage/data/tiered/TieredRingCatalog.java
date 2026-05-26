/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.data.tiered;

import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static catalog of every {@link TieredRingDefinition} in the mod.
 *
 * One ring is registered per (tier x skill attribute) pair, giving
 * {@code TieredRingTier.values().length} * {@code SkillAttributeType.values().length}
 * total rings (currently 6 * 10 = 60). Iteration order matches
 * {@link #ATTRIBUTE_ORDER} grouped by tier so the browser UI lists rings in a
 * consistent layout.
 *
 * Base values come from {@link MarriageConfig} when a config override is
 * present, otherwise the {@link #DEFAULTS} table baked into this file is used.
 * Call {@link #initialize(MarriageConfig)} once after config load to refresh
 * the catalog with the user's tuned values.
 */
public final class TieredRingCatalog {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    /**
     * Display order for attributes within a tier. Mirrors the order of
     * registrations the user requested so each tier's row block reads naturally
     * top to bottom.
     */
    private static final SkillAttributeType[] ATTRIBUTE_ORDER = new SkillAttributeType[] {
            SkillAttributeType.LIFE_FORCE,
            SkillAttributeType.STRENGTH,
            SkillAttributeType.DEFENSE,
            SkillAttributeType.HASTE,
            SkillAttributeType.PRECISION,
            SkillAttributeType.FEROCITY,
            SkillAttributeType.STAMINA,
            SkillAttributeType.DISCIPLINE,
            SkillAttributeType.FLOW,
            SkillAttributeType.SORCERY,
    };

    /**
     * Hardcoded fallback base values per attribute per tier. The values scale
     * roughly 12x from E to S as requested. {@link MarriageConfig} can override
     * any individual cell from config.json.
     */
    private static final Map<SkillAttributeType, Map<TieredRingTier, Double>> DEFAULTS;

    static {
        Map<SkillAttributeType, Map<TieredRingTier, Double>> defaults = new EnumMap<>(SkillAttributeType.class);
        defaults.put(SkillAttributeType.LIFE_FORCE, tierMap(40, 65, 100, 160, 260, 480));
        defaults.put(SkillAttributeType.STRENGTH,   tierMap(10, 16, 25,  40,  65,  120));
        defaults.put(SkillAttributeType.DEFENSE,    tierMap( 3,  5,  8,  12,  20,  36));
        defaults.put(SkillAttributeType.HASTE,      tierMap( 3,  5,  8,  12,  20,  36));
        defaults.put(SkillAttributeType.PRECISION,  tierMap( 3,  5,  8,  12,  20,  36));
        defaults.put(SkillAttributeType.FEROCITY,   tierMap( 6, 10, 15,  25,  40,  72));
        defaults.put(SkillAttributeType.STAMINA,    tierMap( 5,  8, 13,  20,  32,  60));
        defaults.put(SkillAttributeType.DISCIPLINE, tierMap( 4,  6, 10,  16,  26,  48));
        defaults.put(SkillAttributeType.FLOW,       tierMap(15, 25, 40,  65, 100, 180));
        defaults.put(SkillAttributeType.SORCERY,    tierMap(10, 16, 25,  40,  65, 120));
        DEFAULTS = Collections.unmodifiableMap(defaults);
    }

    private static Map<TieredRingTier, Double> tierMap(double e, double d, double c, double b, double a, double s) {
        EnumMap<TieredRingTier, Double> map = new EnumMap<>(TieredRingTier.class);
        map.put(TieredRingTier.E, e);
        map.put(TieredRingTier.D, d);
        map.put(TieredRingTier.C, c);
        map.put(TieredRingTier.B, b);
        map.put(TieredRingTier.A, a);
        map.put(TieredRingTier.S, s);
        return map;
    }

    // Mutable catalog state — rebuilt by initialize(). Insertion order is preserved.
    private static final Map<String, TieredRingDefinition> RINGS = new LinkedHashMap<>();
    private static volatile List<TieredRingDefinition> ringsView = Collections.emptyList();

    static {
        // Pre-populate with hardcoded defaults so the catalog is usable even if
        // initialize() is never called (tests, missing config, etc.).
        rebuild(null);
    }

    private TieredRingCatalog() {
    }

    /**
     * Rebuild the catalog using base values resolved from {@code config} (with
     * fallback to {@link #DEFAULTS}). Safe to call multiple times — every call
     * fully replaces the previous registrations.
     */
    public static synchronized void initialize(@Nullable MarriageConfig config) {
        rebuild(config);
        LOGGER.atInfo().log("TieredRingCatalog initialized: %d rings (%d tiers x %d attributes).",
                RINGS.size(), TieredRingTier.values().length, ATTRIBUTE_ORDER.length);
    }

    private static synchronized void rebuild(@Nullable MarriageConfig config) {
        RINGS.clear();
        for (TieredRingTier tier : TieredRingTier.values()) {
            for (SkillAttributeType attribute : ATTRIBUTE_ORDER) {
                double base = resolveBaseValue(config, attribute, tier);
                String id = idFor(tier, attribute);
                RINGS.put(id, new TieredRingDefinition(id, tier, attribute, base));
            }
        }
        ringsView = Collections.unmodifiableList(new ArrayList<>(RINGS.values()));
    }

    /** Source of truth for a ring's id: "{@code <tier>_<attribute>}" lowercased. */
    @Nonnull
    public static String idFor(@Nonnull TieredRingTier tier, @Nonnull SkillAttributeType attribute) {
        return tier.configKey() + "_" + attribute.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Hardcoded fallback for a (tier, attribute) cell — used when config has
     * no override. Public so {@link MarriageConfig} can read defaults when
     * lazily filling missing entries.
     */
    public static double defaultBaseValue(@Nonnull SkillAttributeType attribute, @Nonnull TieredRingTier tier) {
        Map<TieredRingTier, Double> tierValues = DEFAULTS.get(attribute);
        if (tierValues == null) {
            return 0.0D;
        }
        Double value = tierValues.get(tier);
        return value == null ? 0.0D : value;
    }

    private static double resolveBaseValue(@Nullable MarriageConfig config,
            @Nonnull SkillAttributeType attribute,
            @Nonnull TieredRingTier tier) {
        if (config == null) {
            return defaultBaseValue(attribute, tier);
        }
        return config.getTieredRingBaseValue(attribute, tier);
    }

    /** All rings in registration order. */
    @Nonnull
    public static List<TieredRingDefinition> all() {
        return ringsView;
    }

    /** All rings of a specific tier (registration order). */
    @Nonnull
    public static List<TieredRingDefinition> byTier(@Nonnull TieredRingTier tier) {
        List<TieredRingDefinition> result = new ArrayList<>();
        for (TieredRingDefinition def : ringsView) {
            if (def.tier() == tier) {
                result.add(def);
            }
        }
        return result;
    }

    @Nullable
    public static TieredRingDefinition byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        return RINGS.get(id);
    }
}
