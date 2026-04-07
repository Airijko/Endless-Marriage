/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.data;

import javax.annotation.Nonnull;

/**
 * Represents a marriage home location that both spouses can teleport to.
 */
public record MarriageHome(@Nonnull String worldName, double x, double y, double z, float yaw, float pitch) {
}
