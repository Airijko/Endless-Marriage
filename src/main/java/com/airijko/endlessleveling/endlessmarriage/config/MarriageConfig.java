/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class MarriageConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean requirePriestForMarriage = true;
    private boolean requireMagistrateForDivorce = true;
    private double proximityRange = 25.0;
    private double disciplineBonusPercent = 25.0;
    private double xpShareMultiplier = 1.0;
    private double officiateRange = 5.0;
    private String priestClassId = "priest";
    private String magistrateClassId = "magistrate";

    public boolean isRequirePriestForMarriage() {
        return requirePriestForMarriage;
    }

    public boolean isRequireMagistrateForDivorce() {
        return requireMagistrateForDivorce;
    }

    public double getProximityRange() {
        return proximityRange;
    }

    public double getDisciplineBonusPercent() {
        return disciplineBonusPercent;
    }

    public double getXpShareMultiplier() {
        return xpShareMultiplier;
    }

    public double getOfficiateRange() {
        return officiateRange;
    }

    public String getPriestClassId() {
        return priestClassId;
    }

    public String getMagistrateClassId() {
        return magistrateClassId;
    }

    public void load(@Nonnull File configFile) {
        if (!configFile.exists()) {
            LOGGER.atWarning().log("Config file not found at %s, using defaults.", configFile.getAbsolutePath());
            return;
        }

        try {
            String json = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                LOGGER.atWarning().log("Config file is empty or invalid, using defaults.");
                return;
            }

            if (root.has("require_priest_for_marriage")) {
                requirePriestForMarriage = root.get("require_priest_for_marriage").getAsBoolean();
            }
            if (root.has("require_magistrate_for_divorce")) {
                requireMagistrateForDivorce = root.get("require_magistrate_for_divorce").getAsBoolean();
            }
            if (root.has("proximity_range")) {
                proximityRange = root.get("proximity_range").getAsDouble();
            }
            if (root.has("discipline_bonus_percent")) {
                disciplineBonusPercent = root.get("discipline_bonus_percent").getAsDouble();
            }
            if (root.has("xp_share_multiplier")) {
                xpShareMultiplier = root.get("xp_share_multiplier").getAsDouble();
            }
            if (root.has("officiate_range")) {
                officiateRange = root.get("officiate_range").getAsDouble();
            }
            if (root.has("priest_class_id")) {
                priestClassId = root.get("priest_class_id").getAsString();
            }
            if (root.has("magistrate_class_id")) {
                magistrateClassId = root.get("magistrate_class_id").getAsString();
            }

            LOGGER.atInfo().log("Marriage config loaded: priest=%b, magistrate=%b, range=%.1f, discipline=%.1f%%, xpShare=%.2f",
                    requirePriestForMarriage, requireMagistrateForDivorce, proximityRange,
                    disciplineBonusPercent, xpShareMultiplier);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to load config.json, using defaults.");
        }
    }

}
