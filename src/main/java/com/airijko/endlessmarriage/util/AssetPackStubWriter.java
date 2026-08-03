package com.airijko.endlessmarriage.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a sentinel {@code manifest.json} into a plugin's data folder so
 * Hytale's {@code AssetModule} stops logging
 * {@code "Skipping pack at <folder>: missing or invalid manifest.json"} at boot.
 *
 * <p>Hytale's {@code AssetModule.loadPacksFromDirectory} walks every non-jar
 * entry under {@code mods/} and tries to parse {@code manifest.json} as an
 * asset-pack descriptor. Plugin data folders ({@code mods/<Group>_<Name>/})
 * share that directory but are not packs, so the scan WARNs. Dropping a stub
 * manifest flagged {@code DisabledByDefault:true} under a distinct identifier
 * downgrades the WARN to an INFO {@code "Skipped disabled pack"} line, without
 * registering anything in the pack registry or interfering with the jar's
 * own embedded-pack registration in {@code JavaPlugin.start0}.
 *
 * <p>The stub identifier MUST differ from the host plugin's manifest
 * identifier — collision triggers
 * {@code "Asset pack X already exists, skipping embedded pack"} on boot.
 */
public final class AssetPackStubWriter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String STUB_GROUP_NAMESPACE = "Airijko_Internal";

    private AssetPackStubWriter() {
    }

    /**
     * Writes the sentinel manifest into {@code pluginFolder} if no
     * {@code manifest.json} already exists. The stub uses
     * {@code Airijko_Internal:<stubName>} as its identifier — pick a name
     * unique across the EL plugin family (e.g. {@code "EndlessLevelingCoreData"}).
     *
     * <p>Idempotent: any existing manifest.json is left untouched, so user
     * edits and prior stubs both survive.
     */
    public static void writeStubIfMissing(Path pluginFolder, String stubName) {
        if (pluginFolder == null || stubName == null || stubName.isBlank()) {
            return;
        }
        Path target = pluginFolder.resolve(MANIFEST_FILE);
        if (Files.exists(target)) {
            return;
        }
        String body = buildStubBody(stubName);
        try {
            Files.createDirectories(pluginFolder);
            Files.writeString(target, body, StandardCharsets.UTF_8);
            LOGGER.atInfo().log(
                    "Wrote AssetModule pack stub %s → silences \"missing or invalid manifest.json\" WARN.",
                    target);
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to write AssetModule pack stub at %s; the boot WARN will keep firing.",
                    target);
        }
    }

    private static String buildStubBody(String stubName) {
        return "{\n"
                + "  \"Group\": \"" + STUB_GROUP_NAMESPACE + "\",\n"
                + "  \"Name\": \"" + stubName + "\",\n"
                + "  \"Version\": \"0.0.0\",\n"
                + "  \"Description\": \"AssetModule pack-scan sentinel for the EndlessLeveling plugin family. Not a real asset pack.\",\n"
                + "  \"Authors\": [],\n"
                + "  \"Dependencies\": {},\n"
                + "  \"OptionalDependencies\": {},\n"
                + "  \"LoadBefore\": {},\n"
                + "  \"SubPlugins\": [],\n"
                + "  \"DisabledByDefault\": true\n"
                + "}\n";
    }
}
