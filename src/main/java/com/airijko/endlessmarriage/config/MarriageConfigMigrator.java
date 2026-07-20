/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Migrates an existing user {@code config.json} forward whenever the bundled
 * default ships new keys or a higher {@code config_version}.
 * <p>
 * The migrator never overwrites values the user has set: it deep-merges the
 * bundled defaults into the user file, adding only the keys that are missing.
 * The {@code config_version} field is then bumped to match the bundled version.
 * If anything changed, the file is rewritten (after a one-shot {@code .bak}
 * backup so the user can recover hand-edits if Gson reformats them oddly).
 *
 * <p>The bundled defaults are read from the classpath resource
 * {@code config.json}, which {@link MarriageFilesManager} also uses to seed a
 * fresh install. Keeping a single source of truth means adding a new field is
 * a one-line change in {@code resources/config.json} plus bumping
 * {@link #BUNDLED_CONFIG_VERSION}.
 */
public final class MarriageConfigMigrator {

    /**
     * Bump this whenever {@code resources/config.json} gains new keys (or any
     * structural change that older user files won't have). On the next server
     * boot, every user file with a lower version will be merged forward.
     */
    public static final int BUNDLED_CONFIG_VERSION = 10;

    public static final String CONFIG_VERSION_KEY = "config_version";
    private static final String BUNDLED_RESOURCE_NAME = "config.json";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MarriageConfigMigrator() {
    }

    /**
     * Migrate the user's config file in place if it is missing keys or its
     * {@code config_version} is older than the bundled version. No-op when the
     * file does not exist (the files manager handles fresh-install seeding).
     */
    public static void migrate(@Nonnull File userFile) {
        if (!userFile.exists()) {
            return;
        }

        JsonObject userRoot = readJsonObject(userFile.toPath());
        if (userRoot == null) {
            LOGGER.atWarning().log("Cannot migrate %s: file is empty or invalid JSON.", userFile.getName());
            return;
        }

        JsonObject bundledRoot = readBundledDefaults();
        if (bundledRoot == null) {
            LOGGER.atWarning().log("Cannot migrate %s: bundled defaults are unavailable.", userFile.getName());
            return;
        }

        int userVersion = readVersion(userRoot);
        int bundledVersion = readVersion(bundledRoot);
        if (bundledVersion <= 0) {
            bundledVersion = BUNDLED_CONFIG_VERSION;
        }

        MergeResult merge = mergeMissingKeys(bundledRoot, userRoot);
        boolean versionOutdated = userVersion < bundledVersion;

        if (!merge.changed && !versionOutdated) {
            return;
        }

        if (versionOutdated) {
            LOGGER.atInfo().log(
                    "Migrating %s from config_version=%s to %d (added %d missing key(s)).",
                    userFile.getName(),
                    userVersion <= 0 ? "missing" : Integer.toString(userVersion),
                    bundledVersion,
                    merge.addedCount);
            backupOnce(userFile, userVersion);
        } else {
            LOGGER.atInfo().log("Adding %d missing key(s) to %s.", merge.addedCount, userFile.getName());
        }

        userRoot.addProperty(CONFIG_VERSION_KEY, bundledVersion);
        writeJsonObject(userFile.toPath(), userRoot);
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private static int readVersion(@Nonnull JsonObject root) {
        if (!root.has(CONFIG_VERSION_KEY)) {
            return 0;
        }
        try {
            return root.get(CONFIG_VERSION_KEY).getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static JsonObject readBundledDefaults() {
        try (InputStream in = MarriageConfigMigrator.class.getClassLoader()
                .getResourceAsStream(BUNDLED_RESOURCE_NAME)) {
            if (in == null) {
                return null;
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return GSON.fromJson(json, JsonObject.class);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to read bundled %s.", BUNDLED_RESOURCE_NAME);
            return null;
        }
    }

    private static JsonObject readJsonObject(@Nonnull Path path) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return GSON.fromJson(json, JsonObject.class);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to read %s.", path);
            return null;
        }
    }

    private static void writeJsonObject(@Nonnull Path path, @Nonnull JsonObject root) {
        try {
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to write migrated config to %s.", path);
        }
    }

    /**
     * Saves a {@code .bak} copy of the original file the first time we migrate
     * away from a given old version. Skipped if a backup for that version
     * already exists, so repeated server boots don't pile up duplicates.
     */
    private static void backupOnce(@Nonnull File userFile, int oldVersion) {
        try {
            String suffix = oldVersion <= 0 ? "premigrate" : ("v" + oldVersion);
            Path backup = userFile.toPath().resolveSibling(userFile.getName() + "." + suffix + ".bak");
            if (Files.exists(backup)) {
                return;
            }
            Files.copy(userFile.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.atInfo().log("Saved pre-migration backup to %s", backup.getFileName());
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to back up %s before migration.", userFile.getName());
        }
    }

    /**
     * Recursively copies any keys present in {@code bundled} but missing from
     * {@code user}. Existing user values are never touched. Nested objects are
     * merged element-by-element so a user's customised {@code tiered_rings}
     * map keeps its tuned numbers while still picking up any new attributes or
     * tiers we ship later. Arrays are treated as opaque user values: if the
     * user has the key at all, their array stays as-is.
     */
    private static MergeResult mergeMissingKeys(@Nonnull JsonObject bundled, @Nonnull JsonObject user) {
        MergeResult result = new MergeResult();
        for (Map.Entry<String, JsonElement> entry : bundled.entrySet()) {
            String key = entry.getKey();
            JsonElement bundledValue = entry.getValue();
            if (CONFIG_VERSION_KEY.equals(key)) {
                continue; // version is bumped explicitly by the caller
            }

            if (!user.has(key)) {
                user.add(key, bundledValue.deepCopy());
                result.addedCount++;
                result.changed = true;
                continue;
            }

            JsonElement userValue = user.get(key);
            if (bundledValue.isJsonObject() && userValue.isJsonObject()) {
                MergeResult nested = mergeMissingKeys(bundledValue.getAsJsonObject(), userValue.getAsJsonObject());
                result.addedCount += nested.addedCount;
                result.changed |= nested.changed;
            }
            // Primitives, nulls, and arrays already in the user file are left untouched.
        }
        return result;
    }

    private static final class MergeResult {
        int addedCount;
        boolean changed;
    }
}
