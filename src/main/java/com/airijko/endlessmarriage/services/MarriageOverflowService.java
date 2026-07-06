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
 * spouse who can still gain for combat overflow, and salvages raid overflow that EL
 * would discard — both channels funnel at reduced effectiveness ({@code xp_overflow_combat_effectiveness},
 * default 25%; {@code xp_overflow_raid_effectiveness}, default 35% — two independent
 * knobs) rather than at full value.
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

    /** How an XP-overflow source is allowed to funnel (if at all). */
    private enum Channel {
        /** Direct combat XP — funnels only when the couple is near each other (mirrors the
         *  proximity-gated 50/50 even-split). Funnels at reduced effectiveness
         *  ({@link MarriageConfig#getXpOverflowCombatEffectiveness()}, default 25%). */
        COMBAT,
        /** Raid / boss reward payouts — no distance requirement, but both spouses must be
         *  present (receiving spouse online). "Raids just need both players present."
         *  Funnels at reduced effectiveness ({@link MarriageConfig#getXpOverflowRaidEffectiveness()},
         *  default 35%) — its own independent knob from the combat channel's. */
        RAID,
        /** Everything else (quest/dungeon/outlander claims, generic API grants, raw
         *  adjustments, the marriage redistribution source itself, temp-profile, unknown):
         *  NEVER auto-funnels. These are individual rewards/claims that must not silently
         *  transfer to a spouse — a deliberate allow-list, not a deny-list, so a new XP
         *  source can't accidentally become a funnel vector. */
        IGNORE
    }

    /** Combat sources = the proximity-gated even-split channel. */
    private static final Set<String> COMBAT_SOURCES = Set.of(
            "MOB_KILL", "PARTY_KILL", "PARTY_SHARE", "MATCHMAKING_SHARE");
    /** Raid / boss reward sources = the "both present" channel. */
    private static final Set<String> RAID_SOURCES = Set.of("BOSS_REWARD");

    private static Channel classify(@Nonnull String sourceName) {
        if (COMBAT_SOURCES.contains(sourceName)) {
            return Channel.COMBAT;
        }
        if (RAID_SOURCES.contains(sourceName)) {
            return Channel.RAID;
        }
        return Channel.IGNORE;
    }

    private final MarriageDataManager dataManager;
    private final MarriageConfig config;
    private final MarriageProximitySystem proximitySystem;
    private final MarriageOverflowLog overflowLog;

    /**
     * Recursion / re-entrancy guard for any marriage-driven XP redistribution (the
     * over-cap funnel AND the even-split's spouse credit). While set, the overflow
     * listener is a no-op, so a redistributed grant that itself overflows the recipient's
     * cap can never bounce back into another funnel (no loops, no double-credit, no
     * double chat/ledger entry). Per-thread.
     */
    private final ThreadLocal<Boolean> inRedistribution = ThreadLocal.withInitial(() -> false);

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
        if (inRedistribution.get()) {
            return;
        }
        if (!dataManager.isMarried(uuid)) {
            return;
        }
        UUID spouse = dataManager.getSpouse(uuid);
        if (spouse == null || spouse.equals(uuid)) {
            return;
        }

        // Allow-list the sources that may funnel; everything else (quest/dungeon/outlander
        // claims, generic API grants, raw adjusts, our own MARRIAGE_SHARE, temp-profile,
        // unknown) is ignored so reward/claim XP can never silently transfer to a spouse.
        Channel channel = classify(sourceName);
        if (channel == Channel.IGNORE) {
            return;
        }
        if (channel == Channel.COMBAT) {
            // Combat even-split channel: the couple must be near each other (mirrors the
            // 50/50 proximity gate).
            if (!proximitySystem.isNearSpouse(uuid)) {
                return;
            }
        } else {
            // RAID channel: no distance requirement, but both spouses must be present for
            // the rewards — the receiving spouse has to be online. (The earner is inherently
            // present: they just received the grant.)
            PlayerRef spouseRef = Universe.get() != null ? Universe.get().getPlayer(spouse) : null;
            if (spouseRef == null || !spouseRef.isValid()) {
                return;
            }
        }

        EndlessLevelingAPI api = EndlessLevelingAPI.get();

        // Both channels funnel at reduced effectiveness (each default 35%, two
        // independent config knobs). Applied before the spouse-room clamp so the nerf
        // can't be bypassed by a spouse with plenty of room.
        double effectiveness = channel == Channel.RAID
                ? config.getXpOverflowRaidEffectiveness()
                : config.getXpOverflowCombatEffectiveness();
        double nerfedOverflow = overflowAmount * effectiveness;
        if (nerfedOverflow <= 0.0D) {
            return;
        }

        // Only fund what the spouse can actually absorb before their own cap — never
        // burn the salvage on a second capped partner.
        double spouseRoom = api.xpToReachCapForProfile(spouse, -1);
        if (spouseRoom <= 0.0D) {
            return;
        }
        double fundable = Math.min(nerfedOverflow, spouseRoom);
        if (fundable <= 0.0D) {
            return;
        }

        // Raw credit to the spouse's active profile: no bonuses, no XP-grant listeners,
        // and (since they have room) no recursion into this overflow path.
        inRedistribution.set(true);
        try {
            api.adjustRawXp(spouse, fundable);
        } finally {
            inRedistribution.set(false);
        }

        boolean isRaid = channel == Channel.RAID;

        // RAID overflow is rare and bounded (once per raid completion, unlike the
        // high-frequency combat channel below) and can be a large amount. The ledger
        // + chat notification below are durable/visible essentially immediately
        // (MarriageOverflowLog persists synchronously on this same call), but the
        // credit above only marks the spouse's PlayerData dirty for the normal ~5s
        // coalesced flush — a crash landing in that window loses the XP while the
        // ledger/chat still say it happened. Force it durable now instead. Combat
        // overflow stays on the coalesced path; it's a per-kill hot path where a
        // synchronous save per credit would peg the SQLite writer thread.
        if (isRaid) {
            api.flushPlayerNow(spouse);
        }
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

    /**
     * Credit a spouse their half of an even-split through THEIR OWN bonus pipeline:
     * {@code baseShare} is the pre-personal-bonus base kill XP (already past the EARNER's
     * level-range rule, which is applied once upstream and is NOT re-applied here — that's
     * what lets a high-level partner boost a way-underleveled spouse). It is granted with
     * <b>bonuses applied but the per-kill gain cap bypassed</b>, so:
     * <ul>
     *   <li>the spouse's Discipline / Luck / passive XP multipliers DO apply to their share
     *       (mirrors party-share per-member bonuses), and</li>
     *   <li>the boost is NOT throttled by the spouse's own per-kill gain cap — an
     *       underleveled spouse has the smallest cap, so applying it would gut the boost.
     *       The old even-split used {@code adjustRawXp} (cap-free); preserving that.</li>
     * </ul>
     * The spouse's level cap is still honored by {@code addXp}. Guarded by
     * {@link #inRedistribution} so that if the bonused share spills over the spouse's level
     * cap, the resulting overflow is NOT re-funneled back (no loop / double pay / double
     * log) — the small spill is dropped, exactly like any normal at-cap grant.
     *
     * <p>This is NOT an over-cap funnel and is never logged as one: mob-kill even-split XP
     * isn't tracked as "passed over" — only a fully-capped earner's salvaged XP is.
     */
    public void creditSpouseEvenSplitShare(@Nonnull UUID spouse, double baseShare) {
        if (baseShare <= 0.0D) {
            return;
        }
        inRedistribution.set(true);
        try {
            EndlessLevelingAPI.get().grantXp(spouse, baseShare,
                    com.airijko.endlessleveling.xpstats.XpSource.MARRIAGE_SHARE,
                    /* bypassGainCap */ true, /* bypassBonuses */ false);
        } finally {
            inRedistribution.set(false);
        }
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
            emit(from, due, true);
        }
    }

    /**
     * Materialize any pending window touching this player (as earner or receiver) — used
     * on disconnect and when opening the overflow-log UI so the numbers shown / persisted
     * are current rather than stuck mid-window. Batched: append all, save once.
     */
    public void flushForPlayer(@Nonnull UUID uuid) {
        boolean any = false;
        for (Map.Entry<UUID, Pending> e : pending.entrySet()) {
            if (e.getKey().equals(uuid) || uuid.equals(e.getValue().to)) {
                Pending due = pending.remove(e.getKey());
                if (due != null) {
                    any |= emit(e.getKey(), due, false);
                }
            }
        }
        if (any) {
            overflowLog.save();
        }
    }

    /** Flush every pending window (server shutdown / admin view). Batched: append all, save once. */
    public void flushAll() {
        boolean any = false;
        for (UUID from : pending.keySet()) {
            Pending due = pending.remove(from);
            if (due != null) {
                any |= emit(from, due, false);
            }
        }
        if (any) {
            overflowLog.save();
        }
    }

    /** @return true if an event was actually recorded (amount &gt; 0). */
    private boolean emit(@Nonnull UUID from, @Nonnull Pending p, boolean persist) {
        if (p.amount <= 0.0D || p.to == null) {
            return false;
        }
        String kind = (p.sawRaid && p.sawCombat) ? OverflowEvent.KIND_MIXED
                : p.sawRaid ? OverflowEvent.KIND_RAID : OverflowEvent.KIND_COMBAT;

        try {
            overflowLog.record(from, p.to, p.amount, kind, persist);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to record overflow event.");
        }

        sendNotifications(from, p.to, p.amount, kind);
        return true;
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
