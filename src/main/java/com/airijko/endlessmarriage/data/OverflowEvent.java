/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.data;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * A single rolled-up XP-overflow funnel event: while {@code from} was at their level
 * cap, {@code amount} XP that would otherwise have been burned was redirected to their
 * spouse {@code to} (who still had room to level). Events are coalesced over a short
 * window (see {@code xp_overflow_notify_interval_seconds}) so a combat session produces
 * one entry rather than one per kill.
 *
 * @param timestamp epoch millis the window was flushed
 * @param from      the capped spouse whose over-cap XP was salvaged
 * @param to        the spouse the XP was funneled to
 * @param amount    total XP funneled during the window
 * @param kind      coarse source label: {@code COMBAT}, {@code RAID}, or {@code MIXED}
 */
public record OverflowEvent(long timestamp, @Nonnull UUID from, @Nonnull UUID to,
                            double amount, @Nonnull String kind) {

    public static final String KIND_COMBAT = "COMBAT";
    public static final String KIND_RAID = "RAID";
    public static final String KIND_MIXED = "MIXED";
}
