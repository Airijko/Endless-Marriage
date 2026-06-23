/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent ledger of marriage XP-overflow funnel events: how much over-cap XP each
 * couple has redirected from a maxed spouse to their partner, broken down into a
 * lifetime total plus a bounded list of recent rolled-up {@link OverflowEvent}s.
 *
 * <p>Backed by {@code overflow_log.json}. Couples are keyed by a canonical (order-
 * independent) UUID-pair string so either spouse resolves the same record. Writes are
 * driven by the (already time-throttled) flush in
 * {@link com.airijko.endlessmarriage.services.MarriageOverflowService}, so saving on
 * every recorded event is cheap.
 */
public class MarriageOverflowLog {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "overflow_log.json";

    /** Per-couple aggregate + recent rolled-up events (newest first). */
    public static final class CoupleLog {
        public final UUID player1;
        public final UUID player2;
        public double lifetimeTotal;
        public long eventCount;
        /** Newest-first, bounded to {@code maxEntries}. */
        public final Deque<OverflowEvent> recent = new ArrayDeque<>();

        CoupleLog(@Nonnull UUID player1, @Nonnull UUID player2) {
            this.player1 = player1;
            this.player2 = player2;
        }
    }

    private final File dataFolder;
    private final int maxEntriesPerCouple;

    private final Map<String, CoupleLog> couples = new ConcurrentHashMap<>();
    private volatile double serverLifetimeTotal;

    public MarriageOverflowLog(@Nonnull File dataFolder, int maxEntriesPerCouple) {
        this.dataFolder = dataFolder;
        this.maxEntriesPerCouple = Math.max(1, maxEntriesPerCouple);
        dataFolder.mkdirs();
    }

    /** Order-independent key so either spouse resolves the same couple record. */
    @Nonnull
    public static String pairKey(@Nonnull UUID a, @Nonnull UUID b) {
        String sa = a.toString();
        String sb = b.toString();
        return sa.compareTo(sb) <= 0 ? sa + ":" + sb : sb + ":" + sa;
    }

    /**
     * Append a rolled-up funnel event and bump both the couple and server lifetime
     * totals, then persist. Thread-safe.
     */
    public synchronized void record(@Nonnull UUID from, @Nonnull UUID to, double amount,
            @Nonnull String kind) {
        if (amount <= 0.0D) {
            return;
        }
        String key = pairKey(from, to);
        CoupleLog log = couples.computeIfAbsent(key, k -> new CoupleLog(from, to));
        log.lifetimeTotal += amount;
        log.eventCount++;
        log.recent.addFirst(new OverflowEvent(System.currentTimeMillis(), from, to, amount, kind));
        while (log.recent.size() > maxEntriesPerCouple) {
            log.recent.removeLast();
        }
        serverLifetimeTotal += amount;
        save();
    }

    /** Couple record for either spouse, or {@code null} if the couple has no funnels yet. */
    @Nullable
    public CoupleLog getCoupleLog(@Nonnull UUID a, @Nonnull UUID b) {
        return couples.get(pairKey(a, b));
    }

    /** Lifetime XP funneled for this couple (0 if none recorded). */
    public double getCoupleLifetimeTotal(@Nonnull UUID a, @Nonnull UUID b) {
        CoupleLog log = couples.get(pairKey(a, b));
        return log != null ? log.lifetimeTotal : 0.0D;
    }

    /** Server-wide lifetime XP funneled across all couples. */
    public double getServerLifetimeTotal() {
        return serverLifetimeTotal;
    }

    /** Number of couples that have ever funneled XP. */
    public int getTrackedCoupleCount() {
        return couples.size();
    }

    /** Snapshot of all couple records (unordered). */
    @Nonnull
    public List<CoupleLog> getAllCoupleLogs() {
        return new ArrayList<>(couples.values());
    }

    // ---- Persistence ----

    public void load() {
        File file = new File(dataFolder, FILE_NAME);
        if (!file.exists()) {
            return;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                return;
            }
            couples.clear();
            serverLifetimeTotal = root.has("server_lifetime_total")
                    ? root.get("server_lifetime_total").getAsDouble() : 0.0D;

            if (root.has("couples") && root.get("couples").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("couples")) {
                    JsonObject obj = el.getAsJsonObject();
                    UUID p1 = UUID.fromString(obj.get("player1").getAsString());
                    UUID p2 = UUID.fromString(obj.get("player2").getAsString());
                    CoupleLog log = new CoupleLog(p1, p2);
                    log.lifetimeTotal = obj.has("lifetime_total") ? obj.get("lifetime_total").getAsDouble() : 0.0D;
                    log.eventCount = obj.has("event_count") ? obj.get("event_count").getAsLong() : 0L;
                    if (obj.has("recent") && obj.get("recent").isJsonArray()) {
                        for (JsonElement evEl : obj.getAsJsonArray("recent")) {
                            JsonObject ev = evEl.getAsJsonObject();
                            try {
                                long ts = ev.get("timestamp").getAsLong();
                                UUID from = UUID.fromString(ev.get("from").getAsString());
                                UUID to = UUID.fromString(ev.get("to").getAsString());
                                double amount = ev.get("amount").getAsDouble();
                                String kind = ev.has("kind") ? ev.get("kind").getAsString() : OverflowEvent.KIND_COMBAT;
                                log.recent.addLast(new OverflowEvent(ts, from, to, amount, kind));
                            } catch (Exception ignored) {
                                // Skip malformed legacy entries.
                            }
                        }
                        while (log.recent.size() > maxEntriesPerCouple) {
                            log.recent.removeLast();
                        }
                    }
                    couples.put(pairKey(p1, p2), log);
                }
            }
            LOGGER.atInfo().log("Loaded XP overflow log: %d couples, %.0f total funneled.",
                    couples.size(), serverLifetimeTotal);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load %s.", FILE_NAME);
        }
    }

    public synchronized void save() {
        File file = new File(dataFolder, FILE_NAME);
        try {
            JsonObject root = new JsonObject();
            root.addProperty("server_lifetime_total", serverLifetimeTotal);
            JsonArray array = new JsonArray();
            for (CoupleLog log : couples.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("player1", log.player1.toString());
                obj.addProperty("player2", log.player2.toString());
                obj.addProperty("lifetime_total", log.lifetimeTotal);
                obj.addProperty("event_count", log.eventCount);
                JsonArray recent = new JsonArray();
                // Persist newest-first to match the in-memory deque order.
                for (OverflowEvent ev : log.recent) {
                    JsonObject evObj = new JsonObject();
                    evObj.addProperty("timestamp", ev.timestamp());
                    evObj.addProperty("from", ev.from().toString());
                    evObj.addProperty("to", ev.to().toString());
                    evObj.addProperty("amount", ev.amount());
                    evObj.addProperty("kind", ev.kind());
                    recent.add(evObj);
                }
                obj.add("recent", recent);
                array.add(obj);
            }
            root.add("couples", array);
            Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to save %s.", FILE_NAME);
        }
    }
}
