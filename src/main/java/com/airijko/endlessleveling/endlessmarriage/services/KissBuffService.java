/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.services;

import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player "kiss buff" state — a temporary Discipline XP boost
 * granted on a successful kiss and gated behind a longer cooldown so it
 * cannot be re-applied or refreshed until the cooldown elapses.
 *
 * State is in-memory only and is not currently persisted across restarts.
 */
public final class KissBuffService {

    private final MarriageConfig config;

    /** Wall-clock millis at which the buff was last applied for the player. */
    private final Map<UUID, Long> lastAppliedMs = new ConcurrentHashMap<>();

    public KissBuffService(@Nonnull MarriageConfig config) {
        this.config = config;
    }

    /**
     * Attempts to apply (or refresh) the kiss buff for the given player. The
     * buff is only applied if the player is not currently within the cooldown
     * window of a previous application.
     *
     * @return {@code true} if the buff was applied, {@code false} if the
     *         player is still on cooldown.
     */
    public boolean tryApply(@Nonnull UUID playerUuid) {
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (config.getKissBuffCooldownSeconds() * 1000.0);
        Long last = lastAppliedMs.get(playerUuid);
        if (last != null && (now - last) < cooldownMs) {
            return false;
        }
        lastAppliedMs.put(playerUuid, now);
        return true;
    }

    /** Returns true if the player currently has the buff active. */
    public boolean isActive(@Nonnull UUID playerUuid) {
        Long last = lastAppliedMs.get(playerUuid);
        if (last == null) {
            return false;
        }
        long durationMs = (long) (config.getKissBuffDurationSeconds() * 1000.0);
        return (System.currentTimeMillis() - last) < durationMs;
    }

    /** Returns true if the player cannot currently re-apply the buff. */
    public boolean isOnCooldown(@Nonnull UUID playerUuid) {
        Long last = lastAppliedMs.get(playerUuid);
        if (last == null) {
            return false;
        }
        long cooldownMs = (long) (config.getKissBuffCooldownSeconds() * 1000.0);
        return (System.currentTimeMillis() - last) < cooldownMs;
    }
}
