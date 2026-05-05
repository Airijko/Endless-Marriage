/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.data;

import com.google.gson.*;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages all marriage data: active marriages, pending proposals, pending officiant requests,
 * and officiant records. Persists to JSON files on disk.
 */
public class MarriageDataManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File dataFolder;

    // Active marriages indexed by player UUID for fast lookup
    private final List<MarriagePair> marriages = new CopyOnWriteArrayList<>();
    private final Map<UUID, MarriagePair> marriageIndex = new ConcurrentHashMap<>();

    // Pending proposals: proposer -> target
    private final Map<UUID, UUID> pendingProposals = new ConcurrentHashMap<>();

    // Pending marriages awaiting officiant: one of the pair's UUID -> both UUIDs
    private final Map<UUID, UUID[]> pendingMarriages = new ConcurrentHashMap<>();

    // Pending divorce requests: requesting player UUID
    private final Set<UUID> pendingDivorces = ConcurrentHashMap.newKeySet();

    // Officiant records
    private final List<OfficiantRecord> officiantRecords = new CopyOnWriteArrayList<>();

    // Marriage homes: keyed by either spouse's UUID (both map to the same home)
    private final Map<UUID, MarriageHome> marriageHomes = new ConcurrentHashMap<>();

    // Priest inbox: priest UUID -> list of couple pairs [player1, player2] awaiting officiation
    private final Map<UUID, List<UUID[]>> priestInbox = new ConcurrentHashMap<>();

    // Wedding rings: keyed by either spouse's UUID (both map to the same tier)
    private final Map<UUID, WeddingRingTier> weddingRings = new ConcurrentHashMap<>();

    // Divorce cooldown: UUID -> timestamp of most recent divorce (both ex-spouses are recorded)
    private final Map<UUID, Long> recentDivorces = new ConcurrentHashMap<>();

    public MarriageDataManager(@Nonnull File dataFolder) {
        this.dataFolder = dataFolder;
        dataFolder.mkdirs();
    }

    /**
     * Lazy bridge: copy this player's marriage state onto their entity as a
     * {@link com.airijko.endlessleveling.endlessmarriage.ecs.MarriageComponent}.
     * The disk-persisted {@code marriages}/{@code marriageIndex} maps remain canonical;
     * component is a per-tick mirror so EL core's archetype-scan systems (and any
     * cross-mod readers) can resolve partner state via standard ECS lookup.
     *
     * <p>Safe to call every tick — uses EL's {@code EcsComponents.upsert} which
     * short-circuits on unusable / dead entities.
     */
    public void syncMarriageToComponent(
            @Nonnull com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> playerEntityRef,
            @Nonnull com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> commandBuffer,
            @Nonnull UUID uuid) {
        MarriagePair pair = marriageIndex.get(uuid);
        if (pair == null) {
            // Not married: ensure no stale component lingers.
            com.airijko.endlessleveling.ecs.EcsComponents.remove(commandBuffer, playerEntityRef,
                    com.airijko.endlessleveling.endlessmarriage.ecs.MarriageComponent.getComponentType());
            return;
        }
        final UUID spouse = pair.getSpouse(uuid);
        final long marriedAt = pair.timestamp();
        com.airijko.endlessleveling.ecs.EcsComponents.upsert(commandBuffer, playerEntityRef,
                com.airijko.endlessleveling.endlessmarriage.ecs.MarriageComponent.getComponentType(),
                com.airijko.endlessleveling.endlessmarriage.ecs.MarriageComponent::new,
                c -> {
                    c.setSpouseUuid(spouse);
                    c.setMarriedAtMillis(marriedAt);
                });
    }

    // ---- Marriage queries ----

    public boolean isMarried(@Nonnull UUID uuid) {
        return marriageIndex.containsKey(uuid);
    }

    @Nullable
    public UUID getSpouse(@Nonnull UUID uuid) {
        MarriagePair pair = marriageIndex.get(uuid);
        return pair != null ? pair.getSpouse(uuid) : null;
    }

    /**
     * Delegates to the proximity system so the EndlessLeveling API can query
     * spouse proximity via reflection on the registered "marriage" manager
     * without needing a separate handle to the proximity system.
     */
    public boolean isNearSpouse(@Nonnull UUID uuid) {
        com.airijko.endlessleveling.endlessmarriage.EndlessMarriage plugin =
                com.airijko.endlessleveling.endlessmarriage.EndlessMarriage.getInstance();
        if (plugin == null) {
            return false;
        }
        com.airijko.endlessleveling.endlessmarriage.systems.MarriageProximitySystem proximity =
                plugin.getProximitySystem();
        return proximity != null && proximity.isNearSpouse(uuid);
    }

    @Nullable
    public MarriagePair getMarriage(@Nonnull UUID uuid) {
        return marriageIndex.get(uuid);
    }

    public List<MarriagePair> getAllMarriages() {
        return Collections.unmodifiableList(marriages);
    }

    // ---- Proposals ----

    public boolean hasProposal(@Nonnull UUID target) {
        return pendingProposals.containsValue(target);
    }

    @Nullable
    public UUID getProposer(@Nonnull UUID target) {
        for (Map.Entry<UUID, UUID> entry : pendingProposals.entrySet()) {
            if (entry.getValue().equals(target)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean hasPendingProposal(@Nonnull UUID proposer) {
        return pendingProposals.containsKey(proposer);
    }

    public void addProposal(@Nonnull UUID proposer, @Nonnull UUID target) {
        pendingProposals.put(proposer, target);
    }

    public void removeProposal(@Nonnull UUID proposer) {
        pendingProposals.remove(proposer);
    }

    // ---- Pending marriages (accepted, awaiting officiant) ----

    public boolean hasPendingMarriage(@Nonnull UUID player) {
        return pendingMarriages.containsKey(player);
    }

    @Nullable
    public UUID[] getPendingMarriage(@Nonnull UUID player) {
        return pendingMarriages.get(player);
    }

    public void addPendingMarriage(@Nonnull UUID player1, @Nonnull UUID player2) {
        UUID[] pair = {player1, player2};
        pendingMarriages.put(player1, pair);
        pendingMarriages.put(player2, pair);
    }

    public void removePendingMarriage(@Nonnull UUID player1, @Nonnull UUID player2) {
        pendingMarriages.remove(player1);
        pendingMarriages.remove(player2);
    }

    // ---- Pending divorces ----

    public boolean hasPendingDivorce(@Nonnull UUID player) {
        return pendingDivorces.contains(player);
    }

    public void addPendingDivorce(@Nonnull UUID player) {
        pendingDivorces.add(player);
    }

    public void removePendingDivorce(@Nonnull UUID player) {
        pendingDivorces.remove(player);
    }

    // ---- Priest inbox ----

    public void addPriestRequest(@Nonnull UUID priestUuid, @Nonnull UUID player1, @Nonnull UUID player2) {
        priestInbox.computeIfAbsent(priestUuid, k -> new CopyOnWriteArrayList<>())
                .add(new UUID[]{player1, player2});
        save();
    }

    public List<UUID[]> getPriestInbox(@Nonnull UUID priestUuid) {
        return priestInbox.getOrDefault(priestUuid, Collections.emptyList());
    }

    public void removePriestRequest(@Nonnull UUID priestUuid, @Nonnull UUID player1, @Nonnull UUID player2) {
        List<UUID[]> inbox = priestInbox.get(priestUuid);
        if (inbox != null) {
            inbox.removeIf(pair ->
                    (pair[0].equals(player1) && pair[1].equals(player2))
                    || (pair[0].equals(player2) && pair[1].equals(player1)));
            if (inbox.isEmpty()) {
                priestInbox.remove(priestUuid);
            }
        }
        save();
    }

    /** Remove all inbox entries for a couple across all priests. */
    public void clearPriestRequestsForCouple(@Nonnull UUID player1, @Nonnull UUID player2) {
        for (Map.Entry<UUID, List<UUID[]>> entry : priestInbox.entrySet()) {
            entry.getValue().removeIf(pair ->
                    (pair[0].equals(player1) && pair[1].equals(player2))
                    || (pair[0].equals(player2) && pair[1].equals(player1)));
        }
        priestInbox.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    // ---- Marriage lifecycle ----

    public void marry(@Nonnull UUID player1, @Nonnull UUID player2, @Nullable UUID officiant) {
        marry(player1, player2, officiant, new ArrayList<>());
    }

    public void marry(@Nonnull UUID player1, @Nonnull UUID player2, @Nullable UUID officiant,
            @Nonnull List<UUID> witnesses) {
        MarriagePair pair = new MarriagePair(player1, player2, officiant, System.currentTimeMillis(), witnesses);
        marriages.add(pair);
        marriageIndex.put(player1, pair);
        marriageIndex.put(player2, pair);

        // Clean up pending state
        removePendingMarriage(player1, player2);
        removeProposal(player1);
        removeProposal(player2);
        clearPriestRequestsForCouple(player1, player2);

        if (officiant != null) {
            officiantRecords.add(new OfficiantRecord(officiant, OfficiantRecord.OfficiantType.MARRIAGE,
                    player1, player2, System.currentTimeMillis()));
        }

        save();
        LOGGER.atInfo().log("Marriage created: %s + %s (officiant: %s)", player1, player2, officiant);
    }

    public void divorce(@Nonnull UUID player1, @Nonnull UUID player2, @Nullable UUID officiant) {
        MarriagePair pair = marriageIndex.get(player1);
        if (pair != null) {
            marriages.remove(pair);
            marriageIndex.remove(player1);
            marriageIndex.remove(player2);
        }

        pendingDivorces.remove(player1);
        pendingDivorces.remove(player2);

        // Clean up marriage home and ring
        marriageHomes.remove(player1);
        marriageHomes.remove(player2);
        weddingRings.remove(player1);
        weddingRings.remove(player2);

        // End any active piggyback session between the two players
        try {
            var piggyback = com.airijko.endlessleveling.endlessmarriage.EndlessMarriage.getInstance().getPiggybackService();
            if (piggyback != null) {
                piggyback.dismountAny(player1);
                piggyback.dismountAny(player2);
            }
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to clean up piggyback session on divorce.");
        }

        if (officiant != null) {
            officiantRecords.add(new OfficiantRecord(officiant, OfficiantRecord.OfficiantType.DIVORCE,
                    player1, player2, System.currentTimeMillis()));
        }

        // Record divorce time so both ex-spouses must wait before remarrying
        long divorceTime = System.currentTimeMillis();
        recentDivorces.put(player1, divorceTime);
        recentDivorces.put(player2, divorceTime);

        save();
        LOGGER.atInfo().log("Divorce finalized: %s + %s (officiant: %s)", player1, player2, officiant);
    }

    // ---- Officiant records ----

    public List<OfficiantRecord> getRecordsForOfficiant(@Nonnull UUID officiant) {
        List<OfficiantRecord> result = new ArrayList<>();
        for (OfficiantRecord record : officiantRecords) {
            if (record.officiant().equals(officiant)) {
                result.add(record);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<OfficiantRecord> getAllRecords() {
        return Collections.unmodifiableList(officiantRecords);
    }

    // ---- Marriage homes ----

    @Nullable
    public MarriageHome getHome(@Nonnull UUID uuid) {
        return marriageHomes.get(uuid);
    }

    public void setHome(@Nonnull UUID player1, @Nonnull UUID player2, @Nonnull MarriageHome home) {
        marriageHomes.put(player1, home);
        marriageHomes.put(player2, home);
        save();
    }

    // ---- Wedding rings ----

    @Nullable
    public WeddingRingTier getRing(@Nonnull UUID uuid) {
        return weddingRings.get(uuid);
    }

    public void setRing(@Nonnull UUID player1, @Nonnull UUID player2, @Nonnull WeddingRingTier tier) {
        weddingRings.put(player1, tier);
        weddingRings.put(player2, tier);
        save();
    }

    // ---- Divorce cooldown ----

    /**
     * Returns the timestamp (ms) of the most recent divorce for this player,
     * or {@code null} if no cooldown is recorded.
     */
    @Nullable
    public Long getDivorceTimestamp(@Nonnull UUID uuid) {
        return recentDivorces.get(uuid);
    }

    /**
     * Clears the divorce cooldown for a player (admin use).
     */
    public void clearDivorceCooldown(@Nonnull UUID uuid) {
        recentDivorces.remove(uuid);
        save();
    }

    // ---- Persistence ----

    public void load() {
        loadMarriages();
        loadRecords();
        loadHomes();
        loadPriestInbox();
        loadRings();
        loadDivorceCooldowns();
    }

    public void save() {
        saveMarriages();
        saveRecords();
        saveHomes();
        savePriestInbox();
        saveRings();
        saveDivorceCooldowns();
    }

    private void loadMarriages() {
        File file = new File(dataFolder, "marriages.json");
        if (!file.exists()) {
            return;
        }

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("marriages")) {
                return;
            }

            JsonArray array = root.getAsJsonArray("marriages");
            marriages.clear();
            marriageIndex.clear();

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
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
                            // Skip malformed UUIDs in legacy data.
                        }
                    }
                }

                MarriagePair pair = new MarriagePair(p1, p2, officiant, timestamp, witnesses);
                marriages.add(pair);
                marriageIndex.put(p1, pair);
                marriageIndex.put(p2, pair);
            }

            LOGGER.atInfo().log("Loaded %d marriages from disk.", marriages.size());
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load marriages.json.");
        }
    }

    private void saveMarriages() {
        File file = new File(dataFolder, "marriages.json");
        try {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();

            for (MarriagePair pair : marriages) {
                JsonObject obj = new JsonObject();
                obj.addProperty("player1", pair.player1().toString());
                obj.addProperty("player2", pair.player2().toString());
                obj.addProperty("officiant", pair.officiant() != null ? pair.officiant().toString() : null);
                obj.addProperty("timestamp", pair.timestamp());

                JsonArray witnessArray = new JsonArray();
                for (UUID witness : pair.witnesses()) {
                    witnessArray.add(witness.toString());
                }
                obj.add("witnesses", witnessArray);

                array.add(obj);
            }

            root.add("marriages", array);
            Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to save marriages.json.");
        }
    }

    private void loadRecords() {
        File file = new File(dataFolder, "officiant_records.json");
        if (!file.exists()) {
            return;
        }

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("records")) {
                return;
            }

            JsonArray array = root.getAsJsonArray("records");
            officiantRecords.clear();

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                UUID officiant = UUID.fromString(obj.get("officiant").getAsString());
                OfficiantRecord.OfficiantType type = OfficiantRecord.OfficiantType.valueOf(
                        obj.get("type").getAsString());
                UUID p1 = UUID.fromString(obj.get("player1").getAsString());
                UUID p2 = UUID.fromString(obj.get("player2").getAsString());
                long timestamp = obj.has("timestamp") ? obj.get("timestamp").getAsLong() : 0L;

                officiantRecords.add(new OfficiantRecord(officiant, type, p1, p2, timestamp));
            }

            LOGGER.atInfo().log("Loaded %d officiant records from disk.", officiantRecords.size());
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load officiant_records.json.");
        }
    }

    private void saveRecords() {
        File file = new File(dataFolder, "officiant_records.json");
        try {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();

            for (OfficiantRecord record : officiantRecords) {
                JsonObject obj = new JsonObject();
                obj.addProperty("officiant", record.officiant().toString());
                obj.addProperty("type", record.type().name());
                obj.addProperty("player1", record.player1().toString());
                obj.addProperty("player2", record.player2().toString());
                obj.addProperty("timestamp", record.timestamp());
                array.add(obj);
            }

            root.add("records", array);
            Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to save officiant_records.json.");
        }
    }

    private void loadHomes() {
        File file = new File(dataFolder, "homes.json");
        if (!file.exists()) {
            return;
        }

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("homes")) {
                return;
            }

            JsonArray array = root.getAsJsonArray("homes");
            marriageHomes.clear();

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                UUID p1 = UUID.fromString(obj.get("player1").getAsString());
                UUID p2 = UUID.fromString(obj.get("player2").getAsString());
                String worldName = obj.get("world").getAsString();
                double x = obj.get("x").getAsDouble();
                double y = obj.get("y").getAsDouble();
                double z = obj.get("z").getAsDouble();
                float yaw = obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0f;
                float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0f;

                MarriageHome home = new MarriageHome(worldName, x, y, z, yaw, pitch);
                marriageHomes.put(p1, home);
                marriageHomes.put(p2, home);
            }

            LOGGER.atInfo().log("Loaded %d marriage homes from disk.", array.size());
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load homes.json.");
        }
    }

    private void saveHomes() {
        File file = new File(dataFolder, "homes.json");
        try {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();

            // Deduplicate: only save one entry per marriage pair
            Set<String> saved = new HashSet<>();
            for (MarriagePair pair : marriages) {
                MarriageHome home = marriageHomes.get(pair.player1());
                if (home == null) {
                    continue;
                }
                String key = pair.player1().toString() + ":" + pair.player2().toString();
                if (saved.contains(key)) {
                    continue;
                }
                saved.add(key);

                JsonObject obj = new JsonObject();
                obj.addProperty("player1", pair.player1().toString());
                obj.addProperty("player2", pair.player2().toString());
                obj.addProperty("world", home.worldName());
                obj.addProperty("x", home.x());
                obj.addProperty("y", home.y());
                obj.addProperty("z", home.z());
                obj.addProperty("yaw", home.yaw());
                obj.addProperty("pitch", home.pitch());
                array.add(obj);
            }

            root.add("homes", array);
            Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to save homes.json.");
        }
    }

    private void loadPriestInbox() {
        File file = new File(dataFolder, "priest_inbox.json");
        if (!file.exists()) {
            return;
        }

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("inbox")) {
                return;
            }

            priestInbox.clear();
            JsonArray array = root.getAsJsonArray("inbox");
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                UUID priest = UUID.fromString(obj.get("priest").getAsString());
                UUID p1 = UUID.fromString(obj.get("player1").getAsString());
                UUID p2 = UUID.fromString(obj.get("player2").getAsString());
                priestInbox.computeIfAbsent(priest, k -> new CopyOnWriteArrayList<>())
                        .add(new UUID[]{p1, p2});
            }

            LOGGER.atInfo().log("Loaded priest inbox with %d entries.", array.size());
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load priest_inbox.json.");
        }
    }

    private void savePriestInbox() {
        File file = new File(dataFolder, "priest_inbox.json");
        try {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();

            for (Map.Entry<UUID, List<UUID[]>> entry : priestInbox.entrySet()) {
                for (UUID[] pair : entry.getValue()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("priest", entry.getKey().toString());
                    obj.addProperty("player1", pair[0].toString());
                    obj.addProperty("player2", pair[1].toString());
                    array.add(obj);
                }
            }

            root.add("inbox", array);
            Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to save priest_inbox.json.");
        }
    }

    private void loadRings() {
        File file = new File(dataFolder, "rings.json");
        if (!file.exists()) {
            return;
        }

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("rings")) {
                return;
            }

            JsonArray array = root.getAsJsonArray("rings");
            weddingRings.clear();

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                UUID p1 = UUID.fromString(obj.get("player1").getAsString());
                UUID p2 = UUID.fromString(obj.get("player2").getAsString());
                WeddingRingTier tier = WeddingRingTier.fromName(obj.get("tier").getAsString());
                if (tier != null) {
                    weddingRings.put(p1, tier);
                    weddingRings.put(p2, tier);
                }
            }

            LOGGER.atInfo().log("Loaded %d wedding rings from disk.", array.size());
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load rings.json.");
        }
    }

    private void saveRings() {
        File file = new File(dataFolder, "rings.json");
        try {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();

            // Deduplicate: only save one entry per marriage pair
            Set<String> saved = new HashSet<>();
            for (MarriagePair pair : marriages) {
                WeddingRingTier tier = weddingRings.get(pair.player1());
                if (tier == null) {
                    continue;
                }
                String key = pair.player1().toString() + ":" + pair.player2().toString();
                if (saved.contains(key)) {
                    continue;
                }
                saved.add(key);

                JsonObject obj = new JsonObject();
                obj.addProperty("player1", pair.player1().toString());
                obj.addProperty("player2", pair.player2().toString());
                obj.addProperty("tier", tier.name());
                array.add(obj);
            }

            root.add("rings", array);
            Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to save rings.json.");
        }
    }

    private void loadDivorceCooldowns() {
        File file = new File(dataFolder, "divorce_cooldowns.json");
        if (!file.exists()) {
            return;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("cooldowns")) {
                return;
            }
            recentDivorces.clear();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("cooldowns").entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    long ts = entry.getValue().getAsLong();
                    recentDivorces.put(uuid, ts);
                } catch (Exception ignored) {
                    // skip malformed entries
                }
            }
            LOGGER.atInfo().log("Loaded %d divorce cooldown entries.", recentDivorces.size());
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load divorce_cooldowns.json.");
        }
    }

    private void saveDivorceCooldowns() {
        File file = new File(dataFolder, "divorce_cooldowns.json");
        try {
            JsonObject root = new JsonObject();
            JsonObject cooldowns = new JsonObject();
            for (Map.Entry<UUID, Long> entry : recentDivorces.entrySet()) {
                cooldowns.addProperty(entry.getKey().toString(), entry.getValue());
            }
            root.add("cooldowns", cooldowns);
            Files.writeString(file.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to save divorce_cooldowns.json.");
        }
    }
}
