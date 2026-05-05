/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.config.migration;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * One-shot migrator that moves the legacy {@code <mods>/EndlessMarriage/}
 * folder contents into Hytale's canonical {@code <mods>/<group>_<name>/} path
 * (i.e. {@code Airijko_EndlessMarriage/}).
 *
 * <p>Earlier EndlessMarriage builds wrote their data folder directly under
 * {@code MODS_PATH/EndlessMarriage/}, which violated Hytale's convention
 * (see {@code PendingLoadJavaPlugin.load} →
 * {@code MODS_PATH.resolve(group + "_" + name)}). All Hytale infrastructure —
 * {@code getDataDirectory()}, plugin-scoped storage, asset-editor lookups —
 * resolves against the canonical path, so the legacy folder was effectively
 * orphaned from Hytale's view.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>If legacy folder doesn't exist → no-op.</li>
 *   <li>If canonical folder is missing or empty → move every child of
 *       legacy into canonical, then rename legacy to {@code .legacy} so the
 *       migrator becomes a no-op next boot and users have a rollback.</li>
 *   <li>If canonical folder is non-empty (already populated by Hytale on
 *       first boot of the new code path) → leave both alone, log a SEVERE
 *       and rename legacy to {@code .legacy.conflict} so the user can
 *       inspect and merge manually rather than silently losing data.</li>
 * </ul>
 */
public final class PluginFolderMigrator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String LEGACY_FOLDER_NAME = "EndlessMarriage";

    private PluginFolderMigrator() {
    }

    /**
     * @param canonicalDataDirectory The Hytale-managed plugin data directory
     *        (i.e. {@code JavaPlugin.getDataDirectory()}). Must already
     *        resolve to {@code <mods>/<group>_<name>/}.
     */
    public static void migrateIfNeeded(Path canonicalDataDirectory) {
        if (canonicalDataDirectory == null) {
            return;
        }

        Path modsPath = PluginManager.MODS_PATH;
        if (modsPath == null) {
            return;
        }

        Path legacyFolder = modsPath.resolve(LEGACY_FOLDER_NAME);
        if (!Files.isDirectory(legacyFolder)) {
            return;
        }

        // Don't move into self if Hytale's canonical resolution somehow
        // returned the same path as legacy (e.g. group is empty).
        if (legacyFolder.equals(canonicalDataDirectory)) {
            return;
        }

        try {
            Files.createDirectories(canonicalDataDirectory);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log(
                    "Failed to create canonical plugin folder %s; legacy data left in %s.",
                    canonicalDataDirectory, legacyFolder);
            return;
        }

        if (isNonEmpty(canonicalDataDirectory)) {
            Path conflictArchive = replaceArchiveTarget(modsPath, LEGACY_FOLDER_NAME + ".legacy.conflict");
            tryRename(legacyFolder, conflictArchive);
            LOGGER.atSevere().log(
                    "Both legacy %s and canonical %s exist with content. Renamed legacy to %s — "
                            + "merge manually if you want the old data, then delete the .conflict folder.",
                    legacyFolder.getFileName(),
                    canonicalDataDirectory.getFileName(),
                    conflictArchive.getFileName());
            return;
        }

        try {
            moveAllChildren(legacyFolder, canonicalDataDirectory);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log(
                    "Folder migration from %s → %s failed mid-flight; partial state left on disk.",
                    legacyFolder, canonicalDataDirectory);
            return;
        }

        Path archive = replaceArchiveTarget(modsPath, LEGACY_FOLDER_NAME + ".legacy");
        tryRename(legacyFolder, archive);
        LOGGER.atInfo().log(
                "Migrated plugin folder %s → %s (legacy archived as %s)",
                legacyFolder.getFileName(),
                canonicalDataDirectory.getFileName(),
                archive.getFileName());
    }

    private static boolean isNonEmpty(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.findAny().isPresent();
        } catch (IOException e) {
            // Treat unreadable as non-empty to fail safe — better to skip
            // migration than to clobber an existing folder we can't inspect.
            return true;
        }
    }

    /**
     * Move every direct child of {@code source} into {@code target}, recursing
     * via {@link Files#walkFileTree} for files inside sub-directories so the
     * operation works even when source and target sit on different filesystems
     * (where {@link Files#move} would fail with {@code AtomicMoveNotSupported}).
     */
    private static void moveAllChildren(Path source, Path target) throws IOException {
        try (Stream<Path> children = Files.list(source)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                Path destination = target.resolve(child.getFileName().toString());
                moveRecursive(child, destination);
            }
        }
    }

    private static void moveRecursive(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (IOException ignored) {
            // Fall through to copy+delete below for cross-device cases.
        }

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path dest = target.resolve(source.relativize(file).toString());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });

        // Delete source tree only after a successful copy.
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void tryRename(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log(
                    "Could not archive %s → %s; manual cleanup needed.",
                    source.getFileName(),
                    target.getFileName());
        }
    }

    /**
     * Resolve {@code <modsPath>/<baseName>}, deleting any pre-existing folder
     * at that path so the rename never collides. Prevents the legacy archive
     * from accumulating timestamp-stamped siblings when a user keeps recreating
     * the source folder. Always one {@code .legacy} folder, never a stack.
     */
    private static Path replaceArchiveTarget(Path modsPath, String baseName) {
        Path candidate = modsPath.resolve(baseName);
        if (Files.exists(candidate)) {
            deleteRecursively(candidate);
        }
        return candidate;
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to clear %s before archive rename.", root);
        }
    }
}
