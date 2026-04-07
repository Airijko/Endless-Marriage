/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.endlessmarriage.commands.MarriageCommandRegistrar;
import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.managers.MarriageFilesManager;
import com.airijko.endlessleveling.endlessmarriage.systems.MarriageProximitySystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.function.BiConsumer;

public class EndlessMarriage extends JavaPlugin {

    private static EndlessMarriage INSTANCE;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private MarriageFilesManager filesManager;
    private MarriageConfig marriageConfig;
    private MarriageDataManager marriageDataManager;
    private MarriageProximitySystem proximitySystem;
    private BiConsumer<UUID, Double> xpGrantListener;

    public static EndlessMarriage getInstance() {
        return INSTANCE;
    }

    public MarriageConfig getMarriageConfig() {
        return marriageConfig;
    }

    public MarriageDataManager getMarriageDataManager() {
        return marriageDataManager;
    }

    public MarriageProximitySystem getProximitySystem() {
        return proximitySystem;
    }

    public EndlessMarriage(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("EndlessMarriage initializing...");

        // Initialize folders and config
        filesManager = new MarriageFilesManager();

        // Load config
        marriageConfig = new MarriageConfig();
        marriageConfig.load(filesManager.getConfigFile());

        // Load marriage data
        marriageDataManager = new MarriageDataManager(filesManager.getDataFolder());
        marriageDataManager.load();

        // Register marriage manager with EndlessLeveling API for cross-mod queries
        EndlessLevelingAPI.get().registerManager("marriage", marriageDataManager, false);

        // Register proximity system
        proximitySystem = new MarriageProximitySystem(marriageDataManager, marriageConfig);
        this.getEntityStoreRegistry().registerSystem(proximitySystem);

        // Register XP grant listener for marriage XP sharing and Discipline bonus
        xpGrantListener = createXpGrantListener();
        EndlessLevelingAPI.get().addXpGrantListener(xpGrantListener);

        // Register commands
        MarriageCommandRegistrar.registerCommands(this.getCommandRegistry());

        LOGGER.atInfo().log("EndlessMarriage has been enabled! Loaded %d marriages.",
                marriageDataManager.getAllMarriages().size());
    }

    @Override
    protected void shutdown() {
        // Unregister XP listener
        if (xpGrantListener != null) {
            EndlessLevelingAPI.get().removeXpGrantListener(xpGrantListener);
        }

        // Unregister from manager registry
        if (marriageDataManager != null) {
            EndlessLevelingAPI.get().unregisterManager("marriage", marriageDataManager);
        }

        // Save all data
        if (marriageDataManager != null) {
            marriageDataManager.save();
        }

        LOGGER.atInfo().log("EndlessMarriage has been disabled!");
    }

    /**
     * Creates the XP grant listener that handles:
     * 1. Marriage Discipline bonus: +25% XP when near spouse
     * 2. Marriage XP sharing: 100% XP share to spouse when near each other
     *
     * Uses a ThreadLocal guard to prevent infinite recursion since granting
     * XP to the spouse will trigger this listener again.
     */
    private BiConsumer<UUID, Double> createXpGrantListener() {
        final ThreadLocal<Boolean> inMarriageXpShare = ThreadLocal.withInitial(() -> false);

        return (uuid, adjustedXp) -> {
            // Guard against recursive calls
            if (inMarriageXpShare.get()) {
                return;
            }

            if (marriageDataManager == null || proximitySystem == null || marriageConfig == null) {
                return;
            }

            if (!marriageDataManager.isMarried(uuid)) {
                return;
            }

            if (!proximitySystem.isNearSpouse(uuid)) {
                return;
            }

            UUID spouseUuid = marriageDataManager.getSpouse(uuid);
            if (spouseUuid == null) {
                return;
            }

            inMarriageXpShare.set(true);
            try {
                // 1. Discipline bonus: grant the earning player extra XP (+25% of what they earned)
                double disciplineBonus = adjustedXp * (marriageConfig.getDisciplineBonusPercent() / 100.0);
                if (disciplineBonus > 0) {
                    EndlessLevelingAPI.get().grantXp(uuid, disciplineBonus);
                }

                // 2. XP share: grant spouse 100% of the base XP the player earned
                double spouseXp = adjustedXp * marriageConfig.getXpShareMultiplier();
                if (spouseXp > 0) {
                    EndlessLevelingAPI.get().grantXp(spouseUuid, spouseXp);
                }
            } finally {
                inMarriageXpShare.set(false);
            }
        };
    }
}
