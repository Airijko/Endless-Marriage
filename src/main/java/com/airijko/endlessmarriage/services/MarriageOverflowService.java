/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.services;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessmarriage.commands.subcommands.MarriageMessages;
import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.data.MarriageOverflowLog;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.OverflowEvent;
import com.airijko.endlessmarriage.systems.MarriageProximitySystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the marriage "over-cap XP funnel": when one spouse hits their level cap,
 * the XP they would otherwise burn (mob kills while near their spouse, plus raid / boss
 * rewards) is redirected to their partner — as long as the partner still has room to
 * level. This turns the normal 50/50 marriage even-split into a 100/0 split toward the
 * spouse who can still gain, and salvages raid overflow that EL would discard.
 *
 * <p>Funnel events fire on every XP grant (potentially many per second in combat), so
 * they are coalesced per capped-earner over a short window
 * ({@code xp_overflow_notify_interval_seconds}); each window produces exactly one
 * persisted {@link OverflowEvent} and one chat notification to each spouse.
 *
 * <p>The actual redistribution is driven by EL core's
 * {@link EndlessLevelingAPI.XpOverflowListener}, registered by the plugin. This service
 * holds the policy + aggregation; the listener just forwards to
 * {@link #handleOverflow(UUID, double, String)}.
 */
public class MarriageOverflowService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    /** Affection-pink accent shared with the rest of the marriage chat palette. */
    private static final String COLOR = MarriageMessages.Color.ROMANCE;

    /**
     * Sources that represent the proximity-gated 50/50 even-split channel (direct mob
     * kills). For these the funnel only runs when the couple is near each other, mirroring
     * the even-split. Everything else (raid / boss / claim / API reward payouts) is funneled
     * regardless of distance — those rewards are not proximity-based.
     */
    private static final Set<String> PROXIMITY_GATED_SOURCES = Set.of(
            "MOB_KILL", "PARTY_KILL", "PARTY_SHARE", "MATCHMAKING_SHARE");

    private final MarriageDataManager dataManager;
    private final MarriageConfig config;
    private final MarriageProximitySystem proximitySystem;
    private final MarriageOverflowLog overflowLog;

    /** Recursion guard: the funnel grant must never re-enter the overflow path. */
    private final ThreadLocal<Boolean> inFunnel = ThreadLocal.withInitial(() -> false);

    /** Pending (un-flushed) funnel accumulation, keyed by the capped earner's UUID. */
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private static final class Pending {
        UUID to;
        double amount;
        long windowStart;
        boolean sawRaid;
        boolean sawCombat;
    }

    public MarriageOverflowService(@Nonnull MarriageDataManager dataManager,
            @Nonnull MarriageConfig config,
            @Nonnull MarriageProximitySystem proximitySystem,
            @Nonnull MarriageOverflowLog overflowLog) {
        this.dataManager = dataManager;
        this.config = config;
        this.proximitySystem = proximitySystem;
        this.overflowLog = overflowLog;
    }

    public MarriageOverflowLog getOverflowLog() {
        return overflowLog;
    }

    private static boolean isProximityGatedSource(@Nonnull String sourceName) {
        return PROXIMITY_GATED_SOURCES.contains(sourceName);
    }

    /**
     * Called by EL core's overflow listener when {@code uuid}'s XP grant of
     * {@code overflowAmount} (from source-channel {@code sourceName}) was discarded
     * because they are at their level cap. Funnels what the spouse can absorb to the
     * spouse, then accumulates the window for the throttled chat / ledger flush.
     */
    public void handleOverflow(@Nonnull UUID uuid, double overflowAmount, @Nonnull String sourceName) {
        if (overflowAmount <= 0.0D || !config.isXpOverflowFunnelEnabled()) {
            return;
        }
        if (inFunnel.get()) {
            return;
        }
        if (!dataManager.isMarried(uuid)) {
            return;
        }
        UUID spouse = dataManager.getSpouse(uuid);
        if (spouse == null || spouse.equals(uuid)) {
            return;
        }

        boolean proximityGated = isProximityGatedSource(sourceName);
        if (proximityGated && !proximitySystem.isNearSpouse(uuid)) {
            return;
        }

        EndlessLevelingAPI api = EndlessLevelingAPI.get();

        // Only fund what the spouse can actually absorb before their own cap — never
        // burn the salvage on a second capped partner.
        double spouseRoom = api.xpToReachCapForProfile(spouse, -1);
        if (spouseRoom <= 0.0D) {
            return;
        }
        double fundable = Math.min(overflowAmount, spouseRoom);
        if (fundable <= 0.0D) {
            return;
        }

        // Raw credit to the spouse's active profile: no bonuses, no XP-grant listeners,
        // and (since they have room) no recursion into this overflow path.
        inFunnel.set(true);
        try {
            api.adjustRawXp(spouse, fundable);
        } finally {
            inFunnel.set(false);
        }

        boolean isRaid = !proximityGated; // reward/raid channels
        long now = System.currentTimeMillis();
        final UUID spouseFinal = spouse;
        pending.compute(uuid, (k, p) -> {
            if (p == null || !spouseFinal.equals(p.to)) {
                p = new Pending();
                p.to = spouseFinal;
                p.windowStart = now;
            }
            p.amount += fundable;
            if (isRaid) {
                p.sawRaid = true;
            } else {
                p.sawCombat = true;
            }
            return p;
        });

        maybeFlush(uuid, now);
    }

    private long intervalMs() {
        double seconds = config.getXpOverflowNotifyIntervalSeconds();
        if (seconds < 1.0D) {
            seconds = 1.0D;
        }
        return (long) (seconds * 1000.0D);
    }

    /** Flush {@code from}'s pending window if its interval has elapsed. */
    private void maybeFlush(@Nonnull UUID from, long now) {
        Pending p = pending.get(from);
        if (p == null || now - p.windowStart < intervalMs()) {
            return;
        }
        Pending due = pending.remove(from);
        if (due != null) {
            emit(from, due);
        }
    }

    /**
     * Materialize any pending window touching this player (as earner or receiver) — used
     * on disconnect and when opening the overflow-log UI so the numbers shown / persisted
     * are current rather than stuck mid-window.
     */
    public void flushForPlayer(@Nonnull UUID uuid) {
        for (Map.Entry<UUID, Pending> e : pending.entrySet()) {
            if (e.getKey().equals(uuid) || uuid.equals(e.getValue().to)) {
                Pending due = pending.remove(e.getKey());
                if (due != null) {
                    emit(e.getKey(), due);
                }
            }
        }
    }

    /** Flush every pending window (server shutdown). */
    public void flushAll() {
        for (UUID from : pending.keySet()) {
            Pending due = pending.remove(from);
            if (due != null) {
                emit(from, due);
            }
        }
    }

    private void emit(@Nonnull UUID from, @Nonnull Pending p) {
        if (p.amount <= 0.0D || p.to == null) {
            return;
        }
        String kind = (p.sawRaid && p.sawCombat) ? OverflowEvent.KIND_MIXED
                : p.sawRaid ? OverflowEvent.KIND_RAID : OverflowEvent.KIND_COMBAT;

        try {
            overflowLog.record(from, p.to, p.amount, kind);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to record overflow event.");
        }

        sendNotifications(from, p.to, p.amount, kind);
    }

    private void sendNotifications(@Nonnull UUID from, @Nonnull UUID to, double amount, @Nonnull String kind) {
        String amountStr = formatXp(amount);
        String sourcePhrase = OverflowEvent.KIND_RAID.equals(kind) ? " from raids"
                : OverflowEvent.KIND_MIXED.equals(kind) ? " (incl. raids)" : "";

        String fromName = resolveName(from);
        String toName = resolveName(to);

        PlayerRef toRef = Universe.get() != null ? Universe.get().getPlayer(to) : null;
        if (toRef != null && toRef.isValid()) {
            // "Sarah is at max level — 12,450 XP overflowed to you from raids!"
            toRef.sendMessage(MarriageMessages.shortLine(
                    fromName + " is at max level — " + amountStr + " XP overflowed to you"
                            + sourcePhrase + "!", COLOR));
        }

        PlayerRef fromRef = Universe.get() != null ? Universe.get().getPlayer(from) : null;
        if (fromRef != null && fromRef.isValid()) {
            // "You're at max level — 12,450 XP was funneled to Tom from raids."
            fromRef.sendMessage(MarriageMessages.shortLine(
                    "You're at max level — " + amountStr + " XP was funneled to " + toName
                            + sourcePhrase + ".", COLOR));
        }
    }

    /** Whole-number XP with thousands separators (e.g. {@code 12,450}). */
    @Nonnull
    private static String formatXp(double amount) {
        return String.format(Locale.US, "%,d", Math.round(amount));
    }

    @Nonnull
    private String resolveName(@Nonnull UUID uuid) {
        PlayerRef ref = Universe.get() != null ? Universe.get().getPlayer(uuid) : null;
        if (ref != null) {
            String username = ref.getUsername();
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        String cached = EndlessLevelingAPI.get().getPlayerName(uuid);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        return uuid.toString().substring(0, 8);
    }
}
