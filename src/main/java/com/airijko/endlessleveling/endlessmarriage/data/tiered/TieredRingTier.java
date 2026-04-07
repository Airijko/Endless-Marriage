/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.data.tiered;

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

    E("E", "#bfcdd5"),
    D("D", "#66bb6a"),
    C("C", "#42a5f5"),
    B("B", "#9c5cff"),
    A("A", "#ffa726"),
    S("S", "#e0f7fa"),
    ;

    private final String displayName;
    private final String color;

    TieredRingTier(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
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
