/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.managers;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class MarriageFilesManager {

    private static final String DATA_FOLDER_NAME = "data";
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final File pluginFolder;
    private final File dataFolder;
    private final File configFile;

    public MarriageFilesManager(Path dataDirectory) {
        if (dataDirectory == null) {
            throw new IllegalStateException("dataDirectory is required for EndlessMarriage");
        }
        this.pluginFolder = dataDirectory.toFile();
        this.dataFolder = new File(pluginFolder, DATA_FOLDER_NAME);

        createFolders();

        this.configFile = initConfigFile();
    }

    private void createFolders() {
        try {
            Files.createDirectories(pluginFolder.toPath());
            Files.createDirectories(dataFolder.toPath());
            LOGGER.atInfo().log("Plugin folders initialized at: %s", pluginFolder.getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create EndlessMarriage folders", e);
        }
    }

    private File initConfigFile() {
        File file = new File(pluginFolder, CONFIG_FILE_NAME);
        if (file.exists()) {
            return file;
        }

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (in != null) {
                Files.copy(in, file.toPath());
                LOGGER.atInfo().log("Created default %s at %s", CONFIG_FILE_NAME, file.getAbsolutePath());
            } else {
                LOGGER.atWarning().log("Bundled %s not found in resources.", CONFIG_FILE_NAME);
            }
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to copy default %s.", CONFIG_FILE_NAME);
        }

        return file;
    }

    @Nonnull
    public File getPluginFolder() {
        return pluginFolder;
    }

    @Nonnull
    public File getDataFolder() {
        return dataFolder;
    }

    @Nonnull
    public File getConfigFile() {
        return configFile;
    }
}
