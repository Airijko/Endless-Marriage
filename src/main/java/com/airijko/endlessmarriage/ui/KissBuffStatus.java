/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.ui;

import com.airijko.endlessmarriage.EndlessMarriage;
import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.services.KissBuffService;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Single source of truth for the player-facing kiss-buff status line so the
 * main marriage page and the shared-XP-overflow log page render it identically.
 * The buff grants a temporary +{@code kissBuffDisciplinePercent}% Discipline XP
 * boost on a successful kiss and is gated behind a longer cooldown before it can
 * be re-applied (see {@link KissBuffService}).
 */
public final class KissBuffStatus {

    private KissBuffStatus() {
    }

    /** Resolved label + colour for the player's current kiss-buff state. */
    public record Display(@Nonnull String text, @Nonnull String color) {
    }

    /**
     * Describes the player's current kiss-buff state. The buff is active for its
     * full duration (green countdown), then sits on cooldown until it can be
     * re-applied (amber countdown), then becomes ready again (blue prompt).
     * Returns a neutral "no bonus" line when the services are unavailable.
     */
    @Nonnull
    public static Display describe(@Nonnull UUID playerUuid) {
        EndlessMarriage instance = EndlessMarriage.getInstance();
        KissBuffService buff = instance != null ? instance.getKissBuffService() : null;
        MarriageConfig config = instance != null ? instance.getMarriageConfig() : null;
        if (buff == null || config == null) {
            return new Display("Kiss buff: unavailable", "#7a9abf");
        }

        double pct = config.getKissBuffDisciplinePercent();

        if (buff.isActive(playerUuid)) {
            long remaining = buff.getActiveSecondsRemaining(playerUuid);
            return new Display(
                    String.format(Locale.ROOT, "Active: +%.0f%% Discipline XP  •  %s left",
                            pct, formatDuration(remaining)),
                    "#66ff66");
        }

        if (buff.isOnCooldown(playerUuid)) {
            long remaining = buff.getCooldownSecondsRemaining(playerUuid);
            return new Display(
                    String.format(Locale.ROOT, "On cooldown  •  ready in %s", formatDuration(remaining)),
                    "#ffb86b");
        }

        return new Display(
                String.format(Locale.ROOT, "Ready  •  kiss for +%.0f%% Discipline XP", pct),
                "#9fe0ff");
    }

    /**
     * Resolved lifecycle lines for the dedicated bond-status page: when the buff
     * activated, when it expires, and when it comes off cooldown. Each line pairs
     * an absolute server clock time with a relative countdown so players can read
     * it either way. Returns dashes / "Ready now" when no buff is in flight.
     */
    public record Lifecycle(@Nonnull String activated, @Nonnull String expires, @Nonnull String offCooldown) {
    }

    @Nonnull
    public static Lifecycle describeLifecycle(@Nonnull UUID playerUuid) {
        EndlessMarriage instance = EndlessMarriage.getInstance();
        KissBuffService buff = instance != null ? instance.getKissBuffService() : null;
        if (buff == null) {
            return new Lifecycle("—", "—", "—");
        }

        // Once the cooldown has fully elapsed the previous buff is irrelevant, so
        // present the same clean "ready" state the headline shows.
        if (buff.getActivationEpochMs(playerUuid) <= 0L || !buff.isOnCooldown(playerUuid)) {
            return new Lifecycle("—", "—", "Ready now");
        }

        long now = System.currentTimeMillis();
        long activatedAt = buff.getActivationEpochMs(playerUuid);
        long expiresAt = buff.getExpiryEpochMs(playerUuid);
        long readyAt = buff.getReadyEpochMs(playerUuid);

        String activated = formatClock(activatedAt);
        String expires = expiresAt > now
                ? formatClock(expiresAt) + "  (in " + formatDuration((expiresAt - now) / 1000L) + ")"
                : formatClock(expiresAt) + "  (expired)";
        String offCooldown = readyAt > now
                ? formatClock(readyAt) + "  (in " + formatDuration((readyAt - now) / 1000L) + ")"
                : "Ready now";

        return new Lifecycle(activated, expires, offCooldown);
    }

    /** Formats a wall-clock instant as a 24h "HH:mm" server time. */
    @Nonnull
    static String formatClock(long epochMs) {
        return new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(epochMs));
    }

    /** Formats a whole-second span as "Xh Ym", "Ym Zs", or "Zs". */
    @Nonnull
    static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0L) {
            return "0s";
        }
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%dh %dm", hours, minutes);
        }
        if (minutes > 0L) {
            return String.format(Locale.ROOT, "%dm %ds", minutes, seconds);
        }
        return String.format(Locale.ROOT, "%ds", seconds);
    }
}
