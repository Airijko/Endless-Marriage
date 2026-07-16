package com.airijko.endlessmarriage.backup;

import com.airijko.endlessleveling.persistence.backup.SuiteBackupParticipant;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.MarriageOverflowLog;
import com.airijko.endlessmarriage.data.TieredRingDataManager;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Backup participant for Endless-Marriage. Marriage persists a set of JSON files
 * ({@code marriages.json}, {@code rings.json}, {@code homes.json}, …) in its data
 * folder, so a snapshot is simply a save-then-copy of that JSON set; restore-all
 * replaces the files and reloads the managers in place.
 *
 * <p>Per-player restore is unsupported: marriage state is couple-scoped (pairs,
 * officiants, shared homes) and can't be safely sliced for one player — use a full
 * {@code /el restore all} when marriage data must be rolled back.
 */
public final class MarriageBackupParticipant implements SuiteBackupParticipant {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final Path dataFolder;
    private final MarriageDataManager marriageData;
    private final TieredRingDataManager tieredRings;
    private final MarriageOverflowLog overflowLog;

    public MarriageBackupParticipant(@Nonnull File dataFolder,
                                     @Nonnull MarriageDataManager marriageData,
                                     @Nonnull TieredRingDataManager tieredRings,
                                     @Nonnull MarriageOverflowLog overflowLog) {
        this.dataFolder = dataFolder.toPath();
        this.marriageData = marriageData;
        this.tieredRings = tieredRings;
        this.overflowLog = overflowLog;
    }

    @Nonnull
    @Override
    public String id() {
        return "marriage";
    }

    @Override
    public int exportTo(@Nonnull Path participantDir) throws Exception {
        // Flush in-memory state to disk first so the snapshot is current.
        marriageData.save();
        tieredRings.save();
        overflowLog.save();

        // The saves above are debounced async writes (AsyncFileWriter, ~1s); drain them
        // so the snapshot copies current bytes, not the pre-save file contents.
        try {
            com.airijko.endlessmarriage.util.AsyncFileWriter.INSTANCE.flushAllNow();
        } catch (Throwable ignored) {
        }

        int copied = 0;
        if (Files.isDirectory(dataFolder)) {
            try (Stream<Path> s = Files.list(dataFolder)) {
                for (Path p : s.filter(Files::isRegularFile).toList()) {
                    if (!p.getFileName().toString().endsWith(".json")) {
                        continue;
                    }
                    Files.copy(p, participantDir.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
        }
        LOGGER.atInfo().log("Marriage backup: exported %d JSON file(s).", copied);
        return copied;
    }

    @Nonnull
    @Override
    public RestoreResult restoreAll(@Nonnull Path participantDir) throws Exception {
        if (!Files.isDirectory(participantDir)) {
            return RestoreResult.failed("snapshot directory missing");
        }
        int restored = 0;
        try (Stream<Path> s = Files.list(participantDir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                if (!p.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                Files.createDirectories(dataFolder);
                Files.copy(p, dataFolder.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                restored++;
            }
        }
        // Reload managers from the freshly-restored files.
        marriageData.load();
        tieredRings.load();
        overflowLog.load();
        LOGGER.atInfo().log("Marriage backup: restored %d JSON file(s) and reloaded.", restored);
        return RestoreResult.ok(restored, restored + " marriage file(s) restored and reloaded");
    }

    @Nonnull
    @Override
    public RestoreResult restorePlayer(@Nonnull Path participantDir, @Nonnull UUID uuid) {
        return RestoreResult.unsupported(
                "marriage data is couple-scoped; use /el restore all to roll back marriage state");
    }

    @Nonnull
    @Override
    public java.util.Map<String, Object> summary(@Nonnull Path participantDir) {
        int files = 0;
        try (Stream<Path> s = Files.list(participantDir)) {
            files = (int) s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json")).count();
        } catch (IOException ignore) {
            // leave 0
        }
        return java.util.Map.of("files", files);
    }
}
