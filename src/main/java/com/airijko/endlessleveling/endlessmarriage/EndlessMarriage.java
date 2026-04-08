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
import com.airijko.endlessleveling.endlessmarriage.data.TieredRingDataManager;
import com.airijko.endlessleveling.endlessmarriage.data.tiered.TieredRingCatalog;
import com.airijko.endlessleveling.endlessmarriage.listeners.MarriageInteractListener;
import com.airijko.endlessleveling.endlessmarriage.managers.MarriageFilesManager;
import com.airijko.endlessleveling.endlessmarriage.services.DebugNpcService;
import com.airijko.endlessleveling.endlessmarriage.services.KissBuffService;
import com.airijko.endlessleveling.endlessmarriage.services.KissService;
import com.airijko.endlessleveling.endlessmarriage.services.PiggybackService;
import com.airijko.endlessleveling.endlessmarriage.systems.MarriageProximitySystem;
import com.airijko.endlessleveling.endlessmarriage.systems.PiggybackFollowSystem;
import com.airijko.endlessleveling.endlessmarriage.systems.SpouseProtectionSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EndlessMarriage extends JavaPlugin {

    private static EndlessMarriage INSTANCE;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private MarriageFilesManager filesManager;
    private MarriageConfig marriageConfig;
    private MarriageDataManager marriageDataManager;
    private TieredRingDataManager tieredRingDataManager;
    private MarriageProximitySystem proximitySystem;
    private PiggybackService piggybackService;
    private KissBuffService kissBuffService;
    private KissService kissService;
    private DebugNpcService debugNpcService;
    private SpouseProtectionSystem spouseProtectionSystem;
    private PiggybackFollowSystem piggybackFollowSystem;
    private MarriageInteractListener marriageInteractListener;
    private BiConsumer<UUID, Double> xpGrantListener;
    private Consumer<UUID> preTeleportListener;

    public static EndlessMarriage getInstance() {
        return INSTANCE;
    }

    public MarriageConfig getMarriageConfig() {
        return marriageConfig;
    }

    public MarriageDataManager getMarriageDataManager() {
        return marriageDataManager;
    }

    public TieredRingDataManager getTieredRingDataManager() {
        return tieredRingDataManager;
    }

    public MarriageProximitySystem getProximitySystem() {
        return proximitySystem;
    }

    public PiggybackService getPiggybackService() {
        return piggybackService;
    }

    public KissService getKissService() {
        return kissService;
    }

    public DebugNpcService getDebugNpcService() {
        return debugNpcService;
    }

    /**
     * Re-reads {@code config.json} from disk (running the migrator first so any
     * new keys from a freshly-deployed jar are merged in) and re-applies the
     * values to live state. Services hold a reference to {@link MarriageConfig}
     * rather than snapshotting its values, so calling {@code load()} on the
     * existing instance is enough for them to pick up the new numbers.
     * <p>
     * The tiered ring catalog is rebuilt explicitly because it copies values
     * out of the config at initialize-time into a static cache.
     */
    public void reloadConfig() {
        if (filesManager == null || marriageConfig == null) {
            LOGGER.atWarning().log("reloadConfig() called before initialization; ignoring.");
            return;
        }
        marriageConfig.load(filesManager.getConfigFile());
        TieredRingCatalog.initialize(marriageConfig);
        LOGGER.atInfo().log("EndlessMarriage config reloaded from disk.");
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

        // Build the tiered ring catalog using config-overridable base values.
        // Must run before TieredRingDataManager.load() so persisted ring ids
        // resolve against the freshly-built catalog.
        TieredRingCatalog.initialize(marriageConfig);

        // Load tiered (attribute-typed) ring data
        tieredRingDataManager = new TieredRingDataManager(filesManager.getDataFolder());
        tieredRingDataManager.load();

        // Register marriage manager with EndlessLeveling API for cross-mod queries
        EndlessLevelingAPI.get().registerManager("marriage", marriageDataManager, false);

        // Register proximity system
        proximitySystem = new MarriageProximitySystem(marriageDataManager, marriageConfig);
        this.getEntityStoreRegistry().registerSystem(proximitySystem);

        // Piggyback / kiss services
        piggybackService = new PiggybackService(marriageDataManager, marriageConfig);
        kissBuffService = new KissBuffService(marriageConfig);
        kissService = new KissService(marriageDataManager, marriageConfig, kissBuffService);
        debugNpcService = new DebugNpcService(marriageConfig);

        // Spouse protection: cancels spouse-on-spouse damage outright and
        // applies multiplicative damage reduction while piggybacking.
        spouseProtectionSystem = new SpouseProtectionSystem(piggybackService, marriageDataManager, marriageConfig);
        this.getEntityStoreRegistry().registerSystem(spouseProtectionSystem);

        // Piggyback follow: each tick, copy the carrier's TransformComponent
        // position into the rider's so the rider's camera actually moves with
        // the carrier (the engine's MountedComponent only affects client-side
        // visual rendering — it never updates the rider's server position, so
        // without this system the rider's screen stays glued to the spot
        // where they pressed the use key).
        piggybackFollowSystem = new PiggybackFollowSystem(piggybackService);
        this.getEntityStoreRegistry().registerSystem(piggybackFollowSystem);

        // Interact-key piggyback toggle on a spouse player
        marriageInteractListener = new MarriageInteractListener(marriageDataManager, piggybackService);
        this.getEventRegistry().registerGlobal(PlayerInteractEvent.class,
                marriageInteractListener::onPlayerInteract);

        // Clean up piggyback state when a participant disconnects
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // Re-apply persisted tiered ring bonuses after the player loads in
        // (the augment runtime is in-memory only and would otherwise be empty
        //  for this player after a server restart).
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);

        // Register XP grant listener for marriage XP sharing and Discipline bonus
        xpGrantListener = createXpGrantListener();
        EndlessLevelingAPI.get().addXpGrantListener(xpGrantListener);

        // Dismount piggyback before any cross-world teleport so the rider
        // does not become invisible or desync in the destination world.
        preTeleportListener = uuid -> {
            if (piggybackService != null) {
                piggybackService.dismountAny(uuid);
            }
        };
        EndlessLevelingAPI.get().addPreTeleportListener(preTeleportListener);

        // Register commands
        MarriageCommandRegistrar.registerCommands(this.getCommandRegistry());

        LOGGER.atInfo().log("EndlessMarriage has been enabled! Loaded %d marriages.",
                marriageDataManager.getAllMarriages().size());
    }

    @Override
    protected void shutdown() {
        // Unregister pre-teleport listener
        if (preTeleportListener != null) {
            EndlessLevelingAPI.get().removePreTeleportListener(preTeleportListener);
        }

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
        if (tieredRingDataManager != null) {
            tieredRingDataManager.save();
        }

        LOGGER.atInfo().log("EndlessMarriage has been disabled!");
    }

    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            if (tieredRingDataManager == null) {
                return;
            }
            var player = event.getPlayer();
            if (player == null) {
                return;
            }
            UUID uuid = player.getUuid();
            if (uuid == null) {
                return;
            }
            if (!tieredRingDataManager.hasRingEquipped(uuid)) {
                return;
            }
            var entityRef = event.getPlayerRef();
            var store = entityRef != null ? entityRef.getStore() : null;
            tieredRingDataManager.reapplyOnJoin(uuid, entityRef, store);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to re-apply tiered ring bonus on join.");
        }
    }

    private void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        try {
            var playerRef = event.getPlayerRef();
            if (playerRef == null) {
                return;
            }
            UUID uuid = playerRef.getUuid();
            if (uuid != null && piggybackService != null) {
                piggybackService.clearForPlayer(uuid);
            }
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to clean up piggyback state on disconnect.");
        }
    }

    /**
     * Creates the XP grant listener that handles:
     * 1. Marriage Discipline bonus: +25% XP when near spouse
     * 2. Kiss buff: temporary +10% Discipline XP (configurable) for 1h after a
     *    successful kiss, regardless of spouse proximity
     * 3. Marriage XP sharing: 100% XP share to spouse when near each other
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

            boolean nearSpouse = proximitySystem.isNearSpouse(uuid);
            boolean hasKissBuff = kissBuffService != null && kissBuffService.isActive(uuid);

            if (!nearSpouse && !hasKissBuff) {
                return;
            }

            // Total Discipline bonus: proximity bonus + kiss buff (additive).
            double disciplineBonusPct = 0.0;
            if (nearSpouse) {
                disciplineBonusPct += marriageConfig.getDisciplineBonusPercent();
            }
            if (hasKissBuff) {
                disciplineBonusPct += marriageConfig.getKissBuffDisciplinePercent();
            }

            inMarriageXpShare.set(true);
            try {
                // 1. Discipline bonus: grant the earning player extra XP.
                double disciplineBonus = adjustedXp * (disciplineBonusPct / 100.0);
                if (disciplineBonus > 0) {
                    EndlessLevelingAPI.get().grantXp(uuid, disciplineBonus);
                }

                // 2. XP share: grant spouse a share of the base XP, but only
                //    while the partners are near each other.
                if (nearSpouse) {
                    UUID spouseUuid = marriageDataManager.getSpouse(uuid);
                    if (spouseUuid != null) {
                        double spouseXp = adjustedXp * marriageConfig.getXpShareMultiplier();
                        if (spouseXp > 0) {
                            EndlessLevelingAPI.get().grantXp(spouseUuid, spouseXp);
                        }
                    }
                }
            } finally {
                inMarriageXpShare.set(false);
            }
        };
    }
}
