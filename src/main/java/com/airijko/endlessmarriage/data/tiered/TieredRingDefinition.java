/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.data.tiered;

import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.enums.themes.AttributeTheme;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One concrete tiered ring: a tier paired with a single skill attribute and the
 * additive base value the ring contributes when equipped.
 *
 * Display fields (name, icon, color, description) are derived from EL's shared
 * {@link AttributeTheme} so the browser UI automatically matches the look used
 * everywhere else for that attribute.
 */
public record TieredRingDefinition(
        @Nonnull String id,
        @Nonnull TieredRingTier tier,
        @Nonnull SkillAttributeType attribute,
        double baseValue) {

    @Nullable
    private AttributeTheme theme() {
        return AttributeTheme.fromType(attribute);
    }

    /** Display name like "E Strength Ring". */
    @Nonnull
    public String displayName() {
        AttributeTheme theme = theme();
        String attrLabel = theme != null ? theme.labelFallback() : attribute.name();
        return tier.getDisplayName() + " " + attrLabel + " Ring";
    }

    /** Vanilla Hytale item id used as the ring's icon (mirrors the attribute's theme). */
    @Nonnull
    public String iconItemId() {
        AttributeTheme theme = theme();
        if (theme == null) {
            return "Ingredient_Bar_Iron";
        }
        String icon = theme.iconItemId();
        return icon == null || icon.isBlank() ? "Ingredient_Bar_Iron" : icon;
    }

    /** Hex color used for the ring's name in lists (matches AttributeTheme). */
    @Nonnull
    public String color() {
        AttributeTheme theme = theme();
        return theme != null ? theme.valueColor() : "#f4f9ff";
    }

    /** Short flavor / stat description for the browser row. */
    @Nonnull
    public String description() {
        AttributeTheme theme = theme();
        String attrLabel = theme != null ? theme.labelFallback() : attribute.name();
        return String.format("+%s %s while equipped.", formatValue(baseValue), attrLabel);
    }

    private static String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return String.format("%.1f", value);
    }
}
