/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessmarriage.commands.MarriageCommandRegistrar;
import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.config.migration.PluginFolderMigrator;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.TieredRingDataManager;
import com.airijko.endlessmarriage.data.tiered.TieredRingCatalog;
import com.airijko.endlessmarriage.listeners.MarriageInteractListener;
import com.airijko.endlessmarriage.managers.MarriageFilesManager;
import com.airijko.endlessmarriage.services.DebugNpcService;
import com.airijko.endlessmarriage.services.KissBuffService;
import com.airijko.endlessmarriage.services.KissService;
import com.airijko.endlessmarriage.services.PiggybackService;
import com.airijko.endlessmarriage.systems.MarriageProximitySystem;
import com.airijko.endlessmarriage.systems.PiggybackFollowSystem;
import com.airijko.endlessmarriage.systems.SpouseProtectionSystem;
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
import java.util.function.Function;

public class EndlessMarriage extends JavaPlugin {

    private static EndlessMarriage INSTANCE;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private MarriageFilesManager filesManager;
    private MarriageConfig marriageConfig;
    private MarriageDataManager marriageDataManager;
    private TieredRingDataManager tieredRingDataManager;
    private com.airijko.endlessmarriage.data.MarriageOverflowLog marriageOverflowLog;
    private com.airijko.endlessmarriage.services.MarriageOverflowService marriageOverflowService;
    private MarriageProximitySystem proximitySystem;
    private PiggybackService piggybackService;
    private KissBuffService kissBuffService;
    private KissService kissService;
    private DebugNpcService debugNpcService;
    private SpouseProtectionSystem spouseProtectionSystem;
    private PiggybackFollowSystem piggybackFollowSystem;
    private com.airijko.endlessmarriage.systems.PiggybackSeatStreamSystem piggybackSeatStreamSystem;
    private com.airijko.endlessmarriage.systems.PiggybackDeathDetachSystem piggybackDeathDetachSystem;
    private MarriageInteractListener marriageInteractListener;
    private BiConsumer<UUID, Double> xpGrantListener;
    private EndlessLevelingAPI.XpOverflowListener xpOverflowListener;
    private Function<UUID, Double> disciplineBonusProvider;
    private Consumer<UUID> preTeleportListener;
    private Consumer<EndlessLevelingAPI.AugmentSelectionChangedEvent> augmentSelectionListener;

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

    public com.airijko.endlessmarriage.data.MarriageOverflowLog getMarriageOverflowLog() {
        return marriageOverflowLog;
    }

    public com.airijko.endlessmarriage.services.MarriageOverflowService getMarriageOverflowService() {
        return marriageOverflowService;
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

    public KissBuffService getKissBuffService() {
        return kissBuffService;
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
        // Re-push the couple shared-dungeon toggle to EL core so /reload honors it.
        EndlessLevelingAPI.get().setCoupleSharedDungeonsEnabled(marriageConfig.isSharedDungeonsEnabled());
        // If the piggyback/carry system was just turned off, the live config is
        // already honored for new mounts — but tear down any in-flight sessions so
        // the kill-switch is immediate rather than lingering until manual dismount.
        if (!marriageConfig.isPiggybackEnabled() && piggybackService != null) {
            piggybackService.dismountAllSessions();
        }
        LOGGER.atInfo().log("EndlessMarriage config reloaded from disk.");
    }

    public EndlessMarriage(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("EndlessMarriage initializing...");

        // Register the addon's ECS component types BEFORE any system registration so
        // marriage systems (and any external readers) can resolve ComponentType refs at
        // construction time. Same pattern as EL core's EndlessLevelingComponents.
        com.hypixel.hytale.component.ComponentType<
                com.hypixel.hytale.server.core.universe.world.storage.EntityStore,
                com.airijko.endlessmarriage.ecs.MarriageComponent> marriageType =
                this.getEntityStoreRegistry().registerComponent(
                        com.airijko.endlessmarriage.ecs.MarriageComponent.class,
                        "Marriage",
                        com.airijko.endlessmarriage.ecs.MarriageComponent.CODEC);
        com.airijko.endlessmarriage.ecs.MarriageComponent.setComponentType(marriageType);
        LOGGER.atInfo().log("Registered Endless Marriage ECS components: 1 type (Marriage)");

        // Move legacy <mods>/EndlessMarriage/ contents into Hytale's canonical
        // <mods>/Airijko_EndlessMarriage/ before MarriageFilesManager seeds
        // defaults into the canonical path.
        PluginFolderMigrator.migrateIfNeeded(getDataDirectory());

        // Initialize folders and config
        filesManager = new MarriageFilesManager(getDataDirectory());

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

        // Evict the proximity system's per-store throttle accumulator when an
        // instance world is torn down. MarriageProximitySystem.timeSinceLastCheck
        // is keyed by per-world Store; without this hook a dead instance world's
        // Store stays a live map key for the whole server uptime, transitively
        // pinning that world's entire component graph (systemMetrics,
        // HistoricMetric arrays, ECS data, ~2,100 chunk states) — ~9 GB/hour of
        // accumulated dead worlds on a churning instance server. RemoveWorldEvent
        // fires synchronously inside Universe.removeWorld, so the world's
        // EntityStore is still attached and we can resolve its Store here.
        this.getEventRegistry().registerGlobal(
                com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent.class,
                this::onWorldRemoved);

        // Pumps the lazy MarriageComponent bridge every 5s so other systems can resolve
        // spouse state via the standard ECS lookup instead of hitting MarriageDataManager
        // every tick. Without this, the component stays empty.
        this.getEntityStoreRegistry().registerSystem(
                new com.airijko.endlessmarriage.systems.MarriageComponentSyncSystem(
                        marriageDataManager));

        // Piggyback / kiss services
        piggybackService = new PiggybackService(marriageDataManager, marriageConfig);
        // Soft enhancement: if Refixes-Endless is installed, feed its PiggybackPairs
        // registry so piggyback partners pass through each other's attacks/projectiles.
        // No-op (logged) when Refixes is absent; marriage works regardless.
        com.airijko.endlessmarriage.bridge.PiggybackTargetingBridge.install(piggybackService);
        kissBuffService = new KissBuffService(marriageConfig);
        kissService = new KissService(marriageDataManager, marriageConfig, kissBuffService);
        debugNpcService = new DebugNpcService(marriageConfig);

        // Spouse protection: cancels spouse-on-spouse damage outright and
        // applies multiplicative damage reduction while piggybacking.
        spouseProtectionSystem = new SpouseProtectionSystem(piggybackService, marriageDataManager, marriageConfig);
        this.getEntityStoreRegistry().registerSystem(spouseProtectionSystem);

        // Piggyback follow: each tick, slave the rider's server position +
        // velocity to the carrier. This keeps the rider's body co-located for
        // onlookers, prevents the rider wandering off, and keeps the rider inside
        // the tracking range of players near the carrier so the seat stream below
        // reaches them. (It does NOT drive the rider's own camera — a player's
        // client owns its position; see PiggybackSeatStreamSystem for the camera.)
        piggybackFollowSystem = new PiggybackFollowSystem(piggybackService, marriageConfig);
        this.getEntityStoreRegistry().registerSystem(piggybackFollowSystem);

        // Piggyback seat stream: each tick, push a server-authoritative
        // BlockMount "seat" to the rider's client positioned on the carrier, so
        // the rider's camera follows the carrier (who drives via normal movement)
        // without the rider being able to steer them. Runs in the tracker's
        // QUEUE_UPDATE_GROUP so per-viewer visibility is populated.
        piggybackSeatStreamSystem =
                new com.airijko.endlessmarriage.systems.PiggybackSeatStreamSystem(piggybackService, marriageConfig);
        this.getEntityStoreRegistry().registerSystem(piggybackSeatStreamSystem);

        // Piggyback death detach: the instant either participant dies, tear down
        // the in-memory session so the follow system can never snap a dead,
        // now-distant rider back onto the carrier after a dungeon death-kick.
        piggybackDeathDetachSystem =
                new com.airijko.endlessmarriage.systems.PiggybackDeathDetachSystem(piggybackService);
        this.getEntityStoreRegistry().registerSystem(piggybackDeathDetachSystem);

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

        // XP-overflow funnel: ledger + service. The ledger persists how much over-cap
        // XP each couple has redirected; the service holds the funnel policy and the
        // throttled chat / ledger flush.
        marriageOverflowLog = new com.airijko.endlessmarriage.data.MarriageOverflowLog(
                filesManager.getDataFolder(), marriageConfig.getXpOverflowLogMaxEntriesPerCouple());
        marriageOverflowLog.load();
        EndlessLevelingAPI.get().registerBackupParticipant(
                new com.airijko.endlessmarriage.backup.MarriageBackupParticipant(
                        filesManager.getDataFolder(), marriageDataManager, tieredRingDataManager, marriageOverflowLog));
        marriageOverflowService = new com.airijko.endlessmarriage.services.MarriageOverflowService(
                marriageDataManager, marriageConfig, proximitySystem, marriageOverflowLog);

        // Register XP grant listener for marriage XP sharing and Discipline bonus
        xpGrantListener = createXpGrantListener();
        EndlessLevelingAPI.get().addXpGrantListener(xpGrantListener);

        // Register XP-overflow listener: EL core notifies us when a married player's XP
        // grant is discarded at the level cap, so we can funnel it to their partner.
        xpOverflowListener = (uuid, overflowAmount, sourceName) -> {
            if (marriageOverflowService != null) {
                marriageOverflowService.handleOverflow(uuid, overflowAmount, sourceName);
            }
        };
        EndlessLevelingAPI.get().addXpOverflowListener(xpOverflowListener);

        // Expose the live marriage Discipline bonus to EL core so the profile UI
        // can show it in the player's Discipline row while it is active (near
        // spouse / kiss buff). Read-only — the actual XP is granted by the
        // listener above. Same source of truth: marriageDisciplineBonusPercent().
        disciplineBonusProvider = this::marriageDisciplineBonusPercent;
        EndlessLevelingAPI.get().addExternalDisciplineXpBonusProvider(disciplineBonusProvider);

        // Cross-world teleport handling for an active piggyback session:
        //  - CARRIER teleporting cross-world: do NOT dismount. The rider is pulled
        //    into the carrier's destination by the teleport sites themselves (via
        //    EndlessLevelingAPI.pullPiggybackRider) so the pair stays attached
        //    across the world boundary, then the follow/seat-stream systems re-seat
        //    the rider once both are in the destination store.
        //  - RIDER teleporting on its own (admin TP, /spawn, etc.): end the session
        //    — the carrier isn't moving, so there's nothing to follow.
        // (Death is handled separately by PiggybackDeathDetachSystem, which clears
        //  the session BEFORE the death-kick teleport, so this never fires for a
        //  dead rider.)
        preTeleportListener = uuid -> {
            if (piggybackService == null) {
                return;
            }
            if (piggybackService.isCarrying(uuid)) {
                return;
            }
            if (piggybackService.isRiding(uuid)) {
                piggybackService.dismountAny(uuid);
            }
        };
        EndlessLevelingAPI.get().addPreTeleportListener(preTeleportListener);

        // Bridge piggyback state + couple-dungeon routing to EL core / Rifts so
        // they can pull a seated rider into a teleporting carrier's destination and
        // route married couples into the same dungeon instance.
        EndlessLevelingAPI.get().setPiggybackRiderResolver(piggybackService::getRiderFor);
        EndlessLevelingAPI.get().setCoupleSharedDungeonsEnabled(marriageConfig.isSharedDungeonsEnabled());

        // Banked-instance even-split: inside a dungeon/rift/wave, kill XP is diverted
        // into the killer's personal claim-or-lose bank BEFORE the normal XP-grant
        // listener fires, so the overworld 50/50 even-split never runs there. This
        // resolver lets EL core's XP banks apply the same split — they verify the
        // spouse is a live participant in the same instance themselves, so we only
        // answer "who is the married earner's spouse?".
        EndlessLevelingAPI.get().setCoupleBankSplitResolver(earner -> {
            if (marriageDataManager == null || !marriageDataManager.isMarried(earner)) {
                return null;
            }
            UUID spouse = marriageDataManager.getSpouse(earner);
            return (spouse != null && !spouse.equals(earner)) ? spouse : null;
        });

        // EL wipes all permanent attribute bonuses on every augment-selection
        // change (loadout/profile swap, and the first post-join reconcile),
        // re-deriving only its own augment passives. Our tiered-ring bonus is a
        // permanent external source, so without re-applying here it gets wiped
        // and the equipped ring stops contributing stats. Re-inject it whenever
        // EL signals a selection change. (See TieredRingDataManager
        // #reapplyOnAugmentSelectionChanged.)
        augmentSelectionListener = event -> {
            if (tieredRingDataManager == null || event == null) {
                return;
            }
            UUID uuid = event.playerUuid();
            if (uuid == null) {
                return;
            }
            tieredRingDataManager.reapplyOnAugmentSelectionChanged(uuid);
        };
        EndlessLevelingAPI.get().addAugmentSelectionChangedListener(augmentSelectionListener);

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

        // Tear down the piggyback bridge so EL core / Rifts stop resolving a rider
        // from this (now-unloading) plugin.
        EndlessLevelingAPI.get().setPiggybackRiderResolver(null);

        // Clear the Refixes un-targeting resolver so its combat mixins stop calling
        // into this (now-unloading) plugin's PiggybackService.
        com.airijko.endlessmarriage.bridge.PiggybackTargetingBridge.uninstall();

        // Stop EL core's XP banks from splitting banked instance XP to a spouse
        // resolved by this (now-unloading) plugin.
        EndlessLevelingAPI.get().setCoupleBankSplitResolver(null);

        // Unregister augment-selection-changed listener (ring bonus re-apply)
        if (augmentSelectionListener != null) {
            EndlessLevelingAPI.get().removeAugmentSelectionChangedListener(augmentSelectionListener);
        }

        // Unregister XP listener
        if (xpGrantListener != null) {
            EndlessLevelingAPI.get().removeXpGrantListener(xpGrantListener);
        }

        // Unregister XP-overflow listener and flush any pending funnel windows so the
        // last partial window is recorded before the process exits.
        if (xpOverflowListener != null) {
            EndlessLevelingAPI.get().removeXpOverflowListener(xpOverflowListener);
        }
        if (marriageOverflowService != null) {
            marriageOverflowService.flushAll();
        }

        // Unregister profile-UI Discipline bonus provider
        if (disciplineBonusProvider != null) {
            EndlessLevelingAPI.get().removeExternalDisciplineXpBonusProvider(disciplineBonusProvider);
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

    /**
     * Drops the dead world's per-store throttle entry from
     * {@link MarriageProximitySystem} so a torn-down instance world's
     * {@code Store} is no longer pinned as a map key. See the comment at the
     * registration site for the leak this prevents.
     */
    private void onWorldRemoved(
            @Nonnull com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent event) {
        try {
            if (proximitySystem == null || event.getWorld() == null) {
                return;
            }
            var entityStoreHolder = event.getWorld().getEntityStore();
            com.hypixel.hytale.component.Store<
                    com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store =
                    entityStoreHolder != null ? entityStoreHolder.getStore() : null;
            if (store != null) {
                proximitySystem.onStoreShutdown(store);
            }
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log(
                    "Failed to evict proximity throttle entry on world removal.");
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
            // Materialize any pending overflow window for the leaving player so its
            // ledger entry + chat aren't stranded mid-window until the next funnel.
            if (uuid != null && marriageOverflowService != null) {
                marriageOverflowService.flushForPlayer(uuid);
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
     * 3. Marriage XP even split: when near spouse, the earner's adjusted XP is
     *    split 50/50 between both partners. Each partner's individual bonuses
     *    (luck, discipline, level range) are already baked into their own XP
     *    before the split. Over time both partners converge on the same total
     *    XP regardless of who killed what.
     *
     * Marriage XP sharing and party XP sharing are mutually exclusive. If the
     * earner is in a party, only the discipline/kiss bonuses apply — the even
     * split is skipped so party distribution is not doubled.
     *
     * Uses a ThreadLocal guard to prevent infinite recursion since granting
     * XP to the spouse will trigger this listener again.
     */
    /**
     * The total marriage Discipline XP-bonus percent currently active for the
     * player: the proximity bonus while near their spouse (or within the linger
     * window) plus the kiss buff, additive. Returns {@code 0} when neither
     * applies or the player is not married. Shared by the XP grant listener
     * (which actually grants it) and the EL-core profile-UI provider (which
     * displays it), so the two can never disagree.
     */
    private double marriageDisciplineBonusPercent(@Nonnull UUID uuid) {
        if (marriageDataManager == null || proximitySystem == null || marriageConfig == null) {
            return 0.0;
        }
        if (!marriageDataManager.isMarried(uuid)) {
            return 0.0;
        }
        double pct = 0.0;
        if (proximitySystem.isNearSpouse(uuid)) {
            pct += marriageConfig.getDisciplineBonusPercent();
        }
        if (kissBuffService != null && kissBuffService.isActive(uuid)) {
            pct += marriageConfig.getKissBuffDisciplinePercent();
        }
        return pct;
    }

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

            // Pre-bonus base of THIS grant, captured before any re-entrant grant below
            // (the discipline-bonus grantXp) can change the core's per-thread base value.
            // Used by the even-split so each partner's share runs through their own
            // bonuses. NaN when the core didn't supply a base (legacy path) — handled
            // by falling back to the raw split below.
            double baseXp = EndlessLevelingAPI.get().getCurrentGrantBaseXp();

            // Total Discipline bonus: proximity bonus + kiss buff (additive).
            // Single source of truth shared with the profile-UI provider so the
            // bonus the player sees always matches the bonus actually granted.
            double disciplineBonusPct = marriageDisciplineBonusPercent(uuid);

            // Nothing to do unless a buff is active or the couple is near enough
            // to even-split (which needs nearSpouse below).
            if (disciplineBonusPct <= 0.0 && !nearSpouse) {
                return;
            }

            inMarriageXpShare.set(true);
            try {
                // 1. Discipline bonus: grant the earning player extra XP.
                double disciplineBonus = adjustedXp * (disciplineBonusPct / 100.0);
                if (disciplineBonus > 0) {
                    EndlessLevelingAPI.get().grantXp(uuid, disciplineBonus);
                }

                // 2. Even split: pool the earner's XP and divide equally.
                //    Runs when the earner is near their spouse AND either
                //    (a) not in a party, or (b) in a couple-only party (just
                //    the two spouses). When outsiders are in the party the
                //    standard party-share pipeline runs instead. PartyManager
                //    mirrors this check and skips its member distribution for
                //    couple-only parties so the spouse does not double-dip.
                EndlessLevelingAPI api = EndlessLevelingAPI.get();
                boolean inParty = api.isInParty(uuid);
                boolean coupleOnlyParty = inParty && api.isCoupleOnlyParty(uuid);
                if (nearSpouse && (!inParty || coupleOnlyParty)) {
                    UUID spouseUuid = marriageDataManager.getSpouse(uuid);
                    if (spouseUuid != null) {
                        // EVEN SPLIT, then EACH partner's own bonuses apply (mirrors party-share).
                        // The kill is valued at the BEST level-range multiplier among the two
                        // spouses, split once (XP-conserving — total never exceeds the best value),
                        // and the split base is BOOST-FREE so each partner re-applies their OWN
                        // xp-boost (keeping xp-boost per-spouse, exactly like luck/discipline):
                        //   - earnerBase / spouseBase = this kill's level-range-adjusted value at
                        //     each one's level WITHOUT xp-boost (global + additive + level-range +
                        //     scaling + entity mult only); NaN for non-mob grants.
                        //   - poolBase = max(earnerBase, spouseBase): an underleveled killer's
                        //     level-range penalty no longer drags the in-range spouse's half down —
                        //     the in-range partner carries the pool. When the EARNER is in-range,
                        //     spouseBase <= earnerBase so poolBase == earnerBase (boost-low-spouse
                        //     path unchanged). Each partner keeps poolBase/2.
                        //   - earner: adjustedXp = earnerBase * earnerBoost * earnerBonus already
                        //     credited by the pipeline; scale it to (poolBase/2) * earnerBoost *
                        //     earnerBonus — a strip when poolBase==earnerBase, a top-UP when the
                        //     spouse's value carries (both share the carry, each keeping their OWN
                        //     boost, which rides inside adjustedXp so it cancels in the ratio).
                        //   - spouse: re-apply the SPOUSE's own xp-boost to their boost-free half,
                        //     then credit through their pipeline so their luck/discipline/passive
                        //     apply too.
                        //
                        // Funnel-aware: if the spouse is at (or near) their cap they can't absorb a
                        // share, so the split collapses toward 100/0 in favour of whoever can still
                        // gain — the earner keeps the un-shareable half rather than burning it. (The
                        // MIRROR case, where the EARNER is the capped one, is handled by the
                        // XP-overflow listener, which fires only when this listener cannot.)
                        double earnerBase = api.computeMobKillGrantBaseFor(uuid);
                        boolean mobCtx = !Double.isNaN(earnerBase) && earnerBase > 0.0D;

                        boolean funnelEnabled = marriageConfig.isXpOverflowFunnelEnabled();
                        double spouseRoom = funnelEnabled
                                ? api.xpToReachCapForProfile(spouseUuid, -1)
                                : Double.POSITIVE_INFINITY;

                        if (spouseRoom > 0.0D) {
                            if (mobCtx && marriageOverflowService != null) {
                                double spouseBase = api.computeMobKillGrantBaseFor(spouseUuid);
                                double poolBase = (!Double.isNaN(spouseBase) && spouseBase > earnerBase)
                                        ? spouseBase
                                        : earnerBase;
                                double half = poolBase / 2.0;
                                EndlessLevelingAPI.get().adjustRawXp(uuid,
                                        adjustedXp * ((half / earnerBase) - 1.0));
                                double spouseBoost = api.getXpBoostMultiplier(spouseUuid);
                                marriageOverflowService.creditSpouseEvenSplitShare(
                                        spouseUuid, half * spouseBoost);
                            } else if (!Double.isNaN(baseXp) && baseXp > 0.0D
                                    && marriageOverflowService != null) {
                                // Non-mob grant (no level-range/xp-boost context): plain base/2
                                // even split; spouse still gets their own luck/discipline.
                                EndlessLevelingAPI.get().adjustRawXp(uuid, -(adjustedXp / 2.0));
                                marriageOverflowService.creditSpouseEvenSplitShare(spouseUuid, baseXp / 2.0);
                            } else {
                                // Legacy fallback (core didn't supply a base): raw bonused half.
                                EndlessLevelingAPI.get().adjustRawXp(uuid, -(adjustedXp / 2.0));
                                EndlessLevelingAPI.get().adjustRawXp(spouseUuid, adjustedXp / 2.0);
                            }
                        }
                        // else spouse capped: earner keeps everything (no strip, no credit).

                        // Positive handshake to EL core: the couple-only split ran for this
                        // kill, so PartyManager must skip its party-share loop (otherwise the
                        // spouse is paid twice). Stands even when the spouse was capped and got
                        // no share — a capped spouse must not be paid (and wasted) by the party
                        // loop either. Only meaningful for a couple-only party.
                        if (coupleOnlyParty) {
                            api.markCoupleEvenSplitApplied();
                        }
                    }
                }
            } finally {
                inMarriageXpShare.set(false);
            }
        };
    }
}
