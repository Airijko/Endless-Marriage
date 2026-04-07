/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.data;

import javax.annotation.Nullable;

/**
 * Wedding ring tiers that sync with EndlessLeveling prestige.
 * Both partners must meet the prestige requirement to equip a ring.
 * Economy cost is a placeholder — currently free, but requires prestige.
 *
 * Each tier maps to a vanilla Hytale item icon (gem or ingot) for display
 * via the {@code ItemIcon} UI element.
 */
public enum WeddingRingTier {

    E("E Tier",  0,  0, "#bfcdd5", "Ingredient_Bar_Iron",  "A simple iron band. No prestige required."),
    D("D Tier",  4,  0, "#66bb6a", "Rock_Gem_Emerald",     "An emerald-set ring for proven adventurers."),
    C("C Tier",  8,  0, "#42a5f5", "Rock_Gem_Sapphire",    "A sapphire ring forged in dedication."),
    B("B Tier", 12,  0, "#9c5cff", "Rock_Gem_Voidstone",   "A voidstone ring of deep commitment."),
    A("A Tier", 16,  0, "#ffa726", "Rock_Gem_Topaz",       "A golden topaz ring radiating with power."),
    S("S Tier", 20,  0, "#e0f7fa", "Rock_Gem_Diamond",     "A legendary diamond ring. The ultimate bond.");

    private final String displayName;
    private final int prestigeRequired;
    private final int cost; // placeholder for future economy
    private final String color;
    private final String iconItemId;
    private final String description;

    WeddingRingTier(String displayName, int prestigeRequired, int cost,
                    String color, String iconItemId, String description) {
        this.displayName = displayName;
        this.prestigeRequired = prestigeRequired;
        this.cost = cost;
        this.color = color;
        this.iconItemId = iconItemId;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPrestigeRequired() {
        return prestigeRequired;
    }

    /** Placeholder cost for future economy integration. Currently 0 (free). */
    public int getCost() {
        return cost;
    }

    public String getColor() {
        return color;
    }

    /** Vanilla Hytale item ID used as the ring's icon (e.g. Rock_Gem_Diamond). */
    public String getIconItemId() {
        return iconItemId;
    }

    public String getDescription() {
        return description;
    }

    /** Returns the next tier above this one, or null if this is already the top tier. */
    @Nullable
    public WeddingRingTier next() {
        WeddingRingTier[] all = values();
        int idx = ordinal() + 1;
        return idx < all.length ? all[idx] : null;
    }

    @Nullable
    public static WeddingRingTier fromName(String name) {
        for (WeddingRingTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return null;
    }

    /** Returns the lowest tier (used as the default starter ring). */
    public static WeddingRingTier lowest() {
        return values()[0];
    }
}
