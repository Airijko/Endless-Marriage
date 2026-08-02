/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.data;

import com.airijko.endlessleveling.persistence.spi.BlobCodec;
import com.airijko.endlessleveling.persistence.spi.EntityStore;
import com.airijko.endlessleveling.persistence.spi.IdCodec;
import com.airijko.endlessleveling.persistence.spi.StaleWriteException;
import com.airijko.endlessleveling.persistence.spi.StorageService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cross-server marriage store over EL-Core's shared backend
 * ({@link StorageService#entity}). Two namespaces:
 * <ul>
 *   <li>{@code "marriages"} — pairKey ({@code "<minUuid>:<maxUuid>"}, the two
 *       spouse UUIDs sorted lexicographically) → {@link MarriagePair}.</li>
 *   <li>{@code "marriage_index"} — player uuid → {@link IndexEntry} (the
 *       pairKey they currently belong to). This is the one-marriage-per-player
 *       guard: a player can own at most one index row.</li>
 * </ul>
 *
 * <p><b>Race-proof accept</b> ({@link #tryCreatePair}): create-if-absent the
 * proposer's index row, then the acceptor's, then the pair record, in that
 * order. {@link EntityStore#save} with {@code expectedVersion == 0} IS an
 * atomic create-if-absent (insert-or-{@link StaleWriteException}) — there is
 * no read-then-write window to report here, unlike an SPI that could only
 * express create-if-absent as read→absent→CAS. If a step finds an existing
 * row pointing at a DIFFERENT pair key, the whole attempt aborts and rolls
 * back only the index rows THIS call created (ownership-checked: a rollback
 * never deletes a row whose pairKey isn't ours). Two accepts racing for the
 * SAME couple collide on the same sorted pairKey, so the loser's pair-record
 * insert hits {@link StaleWriteException} too — handled as idempotent success
 * (returns the winner's already-committed record) rather than a duplicate.
 *
 * <p>Local JSON stays the warm cache/fallback: {@link MarriageDataManager}
 * only writes its local caches after a backend call here succeeds (or after
 * confirming no backend is active). {@link #openIfAvailable()} returns empty
 * whenever no licensed backend is active, so callers keep today's JSON-only
 * behavior untouched — mirrors {@code GuildSyncStore.openIfAvailable} /
 * {@code FateDataStore.backend()}.
 */
public final class MarriageSyncStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private static final int SCHEMA_VERSION = 1;
    private static final String PAIR_NAMESPACE = "marriages";
    private static final String INDEX_NAMESPACE = "marriage_index";

    /** Value stored in the {@code "marriage_index"} namespace: the pairKey this uuid belongs to. */
    private record IndexEntry(@Nonnull String pairKey) {
    }

    private static final BlobCodec<MarriagePair> PAIR_CODEC = new BlobCodec<>() {
        @Override
        public String encode(MarriagePair value) {
            JsonObject obj = new JsonObject();
            obj.addProperty("player1", value.player1().toString());
            obj.addProperty("player2", value.player2().toString());
            obj.addProperty("officiant", value.officiant() != null ? value.officiant().toString() : null);
            obj.addProperty("timestamp", value.timestamp());
            JsonArray witnesses = new JsonArray();
            for (UUID witness : value.witnesses()) {
                witnesses.add(witness.toString());
            }
            obj.add("witnesses", witnesses);
            return obj.toString();
        }

        @Override
        public MarriagePair decode(String blob) {
            JsonObject obj = JsonParser.parseString(blob).getAsJsonObject();
            UUID p1 = UUID.fromString(obj.get("player1").getAsString());
            UUID p2 = UUID.fromString(obj.get("player2").getAsString());
            UUID officiant = obj.has("officiant") && !obj.get("officiant").isJsonNull()
                    ? UUID.fromString(obj.get("officiant").getAsString())
                    : null;
            long timestamp = obj.has("timestamp") ? obj.get("timestamp").getAsLong() : 0L;

            List<UUID> witnesses = new ArrayList<>();
            if (obj.has("witnesses") && obj.get("witnesses").isJsonArray()) {
                for (JsonElement w : obj.getAsJsonArray("witnesses")) {
                    try {
                        witnesses.add(UUID.fromString(w.getAsString()));
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed UUIDs.
                    }
                }
            }
            return new MarriagePair(p1, p2, officiant, timestamp, witnesses);
        }
    };

    private static final BlobCodec<IndexEntry> INDEX_CODEC = new BlobCodec<>() {
        @Override
        public String encode(IndexEntry value) {
            JsonObject obj = new JsonObject();
            obj.addProperty("pairKey", value.pairKey());
            return obj.toString();
        }

        @Override
        public IndexEntry decode(String blob) {
            JsonObject obj = JsonParser.parseString(blob).getAsJsonObject();
            return new IndexEntry(obj.get("pairKey").getAsString());
        }
    };

    /** Real pairKeys are always {@code "<uuid>:<uuid>"}; this key can never collide
     *  with one, so it's safe as the distributed one-shot migration guard (see
     *  {@link #migrateLocalToBackendIfEmpty}). */
    private static final String MIGRATION_SENTINEL_KEY = "migration:done";
    private static final MarriagePair MIGRATION_SENTINEL_VALUE =
            new MarriagePair(new UUID(0L, 0L), new UUID(0L, 0L), null, 0L);

    private final EntityStore<String, MarriagePair> pairStore;
    private final EntityStore<String, IndexEntry> indexStore;

    /** Backs every backend call this store makes off the caller's thread (command
     *  thread, join/ready thread, ...) — see {@link #tryCreatePairAsync},
     *  {@link #deletePairAsync}, {@link #loadPairForPlayerAsync}. Single daemon
     *  thread: this store's I/O has no need for concurrency, and a daemon thread
     *  never blocks JVM/plugin shutdown. */
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EM-SyncStore");
        t.setDaemon(true);
        return t;
    });

    private MarriageSyncStore(EntityStore<String, MarriagePair> pairStore, EntityStore<String, IndexEntry> indexStore) {
        this.pairStore = pairStore;
        this.indexStore = indexStore;
    }

    /** Open both namespaces, or empty when no licensed backend is active (caller
     *  falls back to local JSON). Catches Throwable so an absent / older EL core
     *  degrades gracefully instead of crashing plugin setup. */
    public static Optional<MarriageSyncStore> openIfAvailable() {
        try {
            Optional<EntityStore<String, MarriagePair>> pair =
                    StorageService.entity(PAIR_NAMESPACE, SCHEMA_VERSION, IdCodec.STRING_ID, PAIR_CODEC);
            if (pair.isEmpty()) {
                return Optional.empty();
            }
            Optional<EntityStore<String, IndexEntry>> index =
                    StorageService.entity(INDEX_NAMESPACE, SCHEMA_VERSION, IdCodec.STRING_ID, INDEX_CODEC);
            if (index.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new MarriageSyncStore(pair.get(), index.get()));
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log(
                    "MarriageSyncStore: failed to open shared backend; falling back to local JSON.");
            return Optional.empty();
        }
    }

    /** Canonical pair key: the two UUID strings sorted lexicographically, joined with ':'. */
    @Nonnull
    public static String pairKey(@Nonnull UUID a, @Nonnull UUID b) {
        String sa = a.toString();
        String sb = b.toString();
        return sa.compareTo(sb) <= 0 ? (sa + ":" + sb) : (sb + ":" + sa);
    }

    /** Outcome of {@link #tryCreatePair}. */
    public sealed interface CreateResult permits Created, AlreadyMarried, BackendFailure {
    }

    /** The pair (or, on an idempotent double-accept, the already-committed pair) was created. */
    public record Created(@Nonnull MarriagePair pair) implements CreateResult {
    }

    /** One of the two players is already indexed under a DIFFERENT pair key. */
    public record AlreadyMarried() implements CreateResult {
    }

    /** The backend call itself failed (not a conflict) — caller must abort rather
     *  than risk a split-brain marriage that only exists on this server. */
    public record BackendFailure() implements CreateResult {
    }

    private enum IndexClaim { CREATED, ALREADY_OURS, CONFLICT, FAILED }

    /**
     * Race-proof pairing: see class javadoc for the ordered create-if-absent
     * protocol. {@code player1} is treated as the proposer and {@code player2}
     * as the acceptor purely for a deterministic claim order — the resulting
     * pairKey (and thus the guard) is symmetric either way.
     */
    @Nonnull
    public CreateResult tryCreatePair(@Nonnull UUID player1, @Nonnull UUID player2, @Nullable UUID officiant,
            long marriedAtMs, @Nonnull List<UUID> witnesses) {
        return createPair(new MarriagePair(player1, player2, officiant, marriedAtMs, witnesses));
    }

    /**
     * Async wrapper around {@link #tryCreatePair}: runs the backend I/O on
     * {@link #executor} so the caller (the command thread) never does raw
     * synchronous I/O. See {@code MarriageDataManager#marry} for the bounded
     * {@code future.get} that turns this into the deliberate, short, worst-case
     * block callers accept in exchange for that.
     */
    @Nonnull
    public CompletableFuture<CreateResult> tryCreatePairAsync(@Nonnull UUID player1, @Nonnull UUID player2,
            @Nullable UUID officiant, long marriedAtMs, @Nonnull List<UUID> witnesses) {
        return CompletableFuture.supplyAsync(
                () -> tryCreatePair(player1, player2, officiant, marriedAtMs, witnesses), executor);
    }

    /**
     * Shared index-first/pair-last create routine — see class javadoc for the
     * ordered create-if-absent protocol. {@link #tryCreatePair} calls this
     * directly for the live accept path; {@link #migrateLocalToBackendIfEmpty}
     * calls it per-record so both paths get identical orphan-proofing instead of
     * two divergent implementations of the same protocol.
     */
    @Nonnull
    private CreateResult createPair(@Nonnull MarriagePair pair) {
        UUID player1 = pair.player1();
        UUID player2 = pair.player2();
        String key = pairKey(player1, player2);
        boolean weOwnIndex1 = false;
        boolean weOwnIndex2 = false;
        try {
            IndexClaim claim1 = claimIndex(player1, key);
            if (claim1 == IndexClaim.CONFLICT) {
                return new AlreadyMarried();
            }
            if (claim1 == IndexClaim.FAILED) {
                return new BackendFailure();
            }
            weOwnIndex1 = claim1 == IndexClaim.CREATED;

            IndexClaim claim2 = claimIndex(player2, key);
            if (claim2 == IndexClaim.CONFLICT) {
                rollbackIndexIfOwned(player1, key, weOwnIndex1);
                return new AlreadyMarried();
            }
            if (claim2 == IndexClaim.FAILED) {
                rollbackIndexIfOwned(player1, key, weOwnIndex1);
                return new BackendFailure();
            }
            weOwnIndex2 = claim2 == IndexClaim.CREATED;

            try {
                pairStore.save(key, pair, 0L);
                return new Created(pair);
            } catch (StaleWriteException alreadyExists) {
                // Same-couple double-accept (both index rows already pointed at `key`,
                // so this is not a conflict): the winner's record is authoritative.
                EntityStore.Versioned<MarriagePair> existing = pairStore.load(key);
                if (existing != null) {
                    return new Created(existing.value());
                }
                rollbackIndexIfOwned(player1, key, weOwnIndex1);
                rollbackIndexIfOwned(player2, key, weOwnIndex2);
                return new BackendFailure();
            }
        } catch (RuntimeException e) {
            LOGGER.atSevere().withCause(e).log("MarriageSyncStore: create-pair failed for %s", key);
            rollbackIndexIfOwned(player1, key, weOwnIndex1);
            rollbackIndexIfOwned(player2, key, weOwnIndex2);
            return new BackendFailure();
        }
    }

    /** Create-if-absent the index row for {@code uuid} pointing at {@code key}. */
    private IndexClaim claimIndex(UUID uuid, String key) {
        String id = uuid.toString();
        try {
            indexStore.save(id, new IndexEntry(key), 0L);
            return IndexClaim.CREATED;
        } catch (StaleWriteException stale) {
            EntityStore.Versioned<IndexEntry> existing;
            try {
                existing = indexStore.load(id);
            } catch (RuntimeException e) {
                LOGGER.atSevere().withCause(e).log("MarriageSyncStore: index reload failed for %s", id);
                return IndexClaim.FAILED;
            }
            if (existing == null) {
                // Row vanished between the failed insert and our reload (a concurrent
                // divorce deleted it) — safe to claim now.
                try {
                    indexStore.save(id, new IndexEntry(key), 0L);
                    return IndexClaim.CREATED;
                } catch (StaleWriteException stillStale) {
                    return IndexClaim.CONFLICT;
                }
            }
            return key.equals(existing.value().pairKey()) ? IndexClaim.ALREADY_OURS : IndexClaim.CONFLICT;
        } catch (RuntimeException e) {
            LOGGER.atSevere().withCause(e).log("MarriageSyncStore: index claim failed for %s", id);
            return IndexClaim.FAILED;
        }
    }

    /** Compensating delete: only removes the index row THIS call created, and only
     *  if it still points at OUR pairKey (never deletes a row another writer since
     *  repointed). No-op unless {@code createdByUs}. */
    private void rollbackIndexIfOwned(UUID uuid, String key, boolean createdByUs) {
        if (!createdByUs) {
            return;
        }
        deleteIndexIfOwned(uuid, key);
    }

    /** Ownership-checked index delete: only deletes when the current row's pairKey
     *  equals {@code key}. Used by both the accept-flow rollback and real divorce. */
    private void deleteIndexIfOwned(UUID uuid, String key) {
        String id = uuid.toString();
        try {
            EntityStore.Versioned<IndexEntry> current = indexStore.load(id);
            if (current != null && key.equals(current.value().pairKey())) {
                indexStore.delete(id, current.version());
            }
        } catch (StaleWriteException | RuntimeException e) {
            LOGGER.atWarning().withCause(e).log(
                    "MarriageSyncStore: index cleanup for %s (pairKey %s) failed; a stale row may remain", id, key);
        }
    }

    /**
     * Divorce: ownership-checked delete of both index rows, then the pair record.
     * Best-effort — a backend hiccup here does not block the local divorce (the
     * player still wants out); a residual backend row is logged and self-heals on
     * the next successful divorce/marry touching either uuid. Returns {@code true}
     * when every backend delete succeeded.
     */
    public boolean deletePair(@Nonnull UUID player1, @Nonnull UUID player2) {
        String key = pairKey(player1, player2);
        boolean ok = true;
        try {
            deleteIndexIfOwned(player1, key);
            deleteIndexIfOwned(player2, key);
            EntityStore.Versioned<MarriagePair> existing = pairStore.load(key);
            if (existing != null) {
                pairStore.delete(key, existing.version());
            }
        } catch (StaleWriteException | RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("MarriageSyncStore: divorce cleanup failed for %s", key);
            ok = false;
        }
        return ok;
    }

    /**
     * Fully async, fire-and-forget divorce cleanup, run on {@link #executor}: the
     * local divorce proceeds immediately regardless of this (unchanged best-effort
     * contract — see {@link #deletePair}). One scheduled retry (~5s) if the first
     * attempt fails; beyond that, same as before, a residual backend row is logged
     * and self-heals on the next divorce/marry touching either uuid.
     */
    public void deletePairAsync(@Nonnull UUID player1, @Nonnull UUID player2) {
        executor.execute(() -> {
            if (deletePair(player1, player2)) {
                return;
            }
            executor.schedule(() -> {
                if (!deletePair(player1, player2)) {
                    LOGGER.atWarning().log(
                            "MarriageSyncStore: divorce cleanup retry failed for %s + %s; a stale row may remain",
                            player1, player2);
                }
            }, 5, TimeUnit.SECONDS);
        });
    }

    /**
     * Join-time lookup: this player's authoritative marriage per the backend, or
     * {@code null} when unmarried per the backend (or on a transient error, so a
     * blip never masquerades as a divorce). Not for hot-path use — see
     * {@link MarriageDataManager#refreshFromBackendOnJoin}.
     */
    @Nullable
    public MarriagePair loadPairForPlayer(@Nonnull UUID uuid) {
        try {
            EntityStore.Versioned<IndexEntry> idx = indexStore.load(uuid.toString());
            if (idx == null) {
                return null;
            }
            EntityStore.Versioned<MarriagePair> pair = pairStore.load(idx.value().pairKey());
            return pair != null ? pair.value() : null;
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("MarriageSyncStore: join-refresh lookup failed for %s", uuid);
            return null;
        }
    }

    /**
     * Async wrapper around {@link #loadPairForPlayer}, run on {@link #executor}:
     * the join/ready path must not do synchronous backend I/O. Fail-soft behavior
     * is unchanged — a transient error still resolves to {@code null}.
     */
    @Nonnull
    public CompletableFuture<MarriagePair> loadPairForPlayerAsync(@Nonnull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> loadPairForPlayer(uuid), executor);
    }

    /**
     * One-shot cutover: seed the backend from {@code localMarriages} ONLY when the
     * backend's {@code "marriages"} namespace is still completely empty. The
     * empty-check is a cheap fast path, not the guard itself — it's TOCTOU (two
     * servers can both observe empty and both start migrating), so the real guard
     * is the {@link #MIGRATION_SENTINEL_KEY} create-if-absent below: only the
     * server that wins that atomic insert actually migrates, closing the window
     * the empty-check can't close alone. Guarding on "empty" (and then on the
     * sentinel) is what stops a secondary server (booting with its own, possibly
     * stale, local JSON) from clobbering fresher data another server already
     * migrated. Each record is migrated via the same index-first/pair-last
     * {@link #createPair} routine {@link #tryCreatePair} uses, so a conflict on
     * any one record compensating-deletes only that record's own creations and
     * moves on — it never aborts the whole batch. Call once at plugin boot,
     * mirroring {@code FateDataStore#migrateLocalToBackendIfEmpty}. Returns the
     * number migrated.
     */
    public int migrateLocalToBackendIfEmpty(@Nonnull List<MarriagePair> localMarriages) {
        try {
            if (!pairStore.listIds().isEmpty()) {
                return 0; // cheap fast path — backend already has data, never overwrite it from local JSON
            }
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("MarriageSyncStore: migration pre-check failed; skipping");
            return 0;
        }
        if (localMarriages.isEmpty()) {
            return 0;
        }
        // Distributed one-shot guard: create-if-absent the sentinel row. Winner
        // (this save succeeds) proceeds to migrate; loser (StaleWriteException)
        // knows another server already claimed migration and returns untouched.
        try {
            pairStore.save(MIGRATION_SENTINEL_KEY, MIGRATION_SENTINEL_VALUE, 0L);
        } catch (StaleWriteException lost) {
            LOGGER.atInfo().log("MarriageSyncStore: migration already claimed by another server; skipping");
            return 0;
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("MarriageSyncStore: migration sentinel claim failed; skipping");
            return 0;
        }
        int migrated = 0;
        for (MarriagePair pair : localMarriages) {
            String key = pairKey(pair.player1(), pair.player2());
            CreateResult result = createPair(pair);
            if (result instanceof Created) {
                migrated++;
            } else {
                LOGGER.atWarning().log(
                        "MarriageSyncStore: migration skipped %s (already present on backend?)", key);
            }
        }
        LOGGER.atInfo().log("MarriageSyncStore: migrated %d local marriage(s) into the shared backend.", migrated);
        return migrated;
    }
}
