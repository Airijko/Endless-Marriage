/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.data.tiered;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Tier ladder for attribute-typed rings (separate from the simple
 * {@code WeddingRingTier} used by /marry rings).
 *
 * Six tiers, scaling roughly 12x in raw base value from E to S. Per-attribute
 * base values for each tier are defined in {@link TieredRingCatalog} (with
 * config.json overrides via {@code MarriageConfig}).
 */
public enum TieredRingTier {

    E("E", "#bfcdd5",  0, "Ingredient_Bar_Iron",  "A simple iron band. No prestige required."),
    D("D", "#66bb6a",  4, "Rock_Gem_Emerald",     "An emerald-set ring for proven adventurers."),
    C("C", "#42a5f5",  8, "Rock_Gem_Sapphire",    "A sapphire ring forged in dedication."),
    B("B", "#9c5cff", 12, "Rock_Gem_Voidstone",   "A voidstone ring of deep commitment."),
    A("A", "#ffa726", 16, "Rock_Gem_Topaz",       "A golden topaz ring radiating with power."),
    S("S", "#e0f7fa", 20, "Rock_Gem_Diamond",     "A legendary diamond ring. The ultimate bond."),
    ;

    private final String displayName;
    private final String color;
    private final int prestigeRequired;
    private final String iconItemId;
    private final String description;

    TieredRingTier(String displayName, String color, int prestigeRequired,
                   String iconItemId, String description) {
        this.displayName = displayName;
        this.color = color;
        this.prestigeRequired = prestigeRequired;
        this.iconItemId = iconItemId;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    /** Prestige both partners must reach before any ring of this tier can be equipped. */
    public int getPrestigeRequired() {
        return prestigeRequired;
    }

    /** Vanilla Hytale item id used as the tier's icon on the tier-selection screen. */
    public String getIconItemId() {
        return iconItemId;
    }

    /** Flavor text shown for the tier on the tier-selection screen. */
    public String getDescription() {
        return description;
    }

    /** Lowercase form used as a config key (e.g. "e", "s"). */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static TieredRingTier fromName(String name) {
        if (name == null) {
            return null;
        }
        for (TieredRingTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return null;
    }
}
