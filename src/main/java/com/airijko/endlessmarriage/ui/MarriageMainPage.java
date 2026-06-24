/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.ui;

import com.airijko.endlessmarriage.EndlessMarriage;
import com.airijko.endlessmarriage.MarriageAnnouncer;
import com.airijko.endlessmarriage.commands.subcommands.MarriageMessages;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.MarriageHome;
import com.airijko.endlessmarriage.data.MarriagePair;
import com.airijko.endlessmarriage.data.WeddingRingTier;
import com.airijko.endlessmarriage.data.TieredRingDataManager;
import com.airijko.endlessmarriage.data.tiered.TieredRingDefinition;
import com.airijko.endlessmarriage.data.tiered.TieredRingTier;
import com.airijko.endlessmarriage.services.DebugNpcService;
import com.airijko.endlessmarriage.services.KissService;
import com.airijko.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.airijko.endlessleveling.ui.base.SafeInteractiveCustomUIPage;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.ui.menu.MenuRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.*;


import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Main marriage UI hub opened by /marry. Shows different layouts for
 * married and unmarried players with buttons for all features.
 */
public class MarriageMainPage extends SafeInteractiveCustomUIPage<MarriagePageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String WITNESS_ROW_TEMPLATE = "Pages/Marriage/WitnessRow.ui";

    private final PlayerRef playerRef;
    private final Ref<EntityStore> entityRef;
    private final Store<EntityStore> entityStore;
    /** When non-null, the page renders the married view using this fake pair regardless of real marital status. Used by /marry debug menu. */
    @Nullable
    private final MarriagePair forcedPair;

    /** Home coords are hidden by default (anti-leak); VIEW COORDS reveals them
     *  for this open session only. Re-opening the page resets to hidden. */
    private boolean coordsRevealed = false;

    public MarriageMainPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull Store<EntityStore> entityStore) {
        this(playerRef, lifetime, entityRef, entityStore, null);
    }

    public MarriageMainPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull Store<EntityStore> entityStore,
            @Nullable MarriagePair forcedPair) {
        super(playerRef, lifetime, MarriagePageData.CODEC);
        this.playerRef = playerRef;
        this.entityRef = entityRef;
        this.entityStore = entityStore;
        this.forcedPair = forcedPair;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Marriage/MarriageMainPage.ui");

        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        // Debug menu: a forced pair short-circuits the real marriage check so
        // an admin can preview the married view without an actual partner.
        MarriagePair effectivePair = forcedPair != null ? forcedPair : data.getMarriage(senderUuid);
        if (effectivePair != null) {
            buildMarriedView(ui, events, senderUuid, data, effectivePair);
        } else {
            buildUnmarriedView(ui, events, senderUuid, data);
        }
    }

    private void buildMarriedView(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
            @Nonnull UUID senderUuid, @Nonnull MarriageDataManager data, @Nonnull MarriagePair pair) {

        UUID spouseUuid = pair.getSpouse(senderUuid);
        String spouseName = resolvePlayerName(spouseUuid);
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(pair.timestamp()));

        // Show married panel, hide unmarried panel
        ui.set("#MarriedPanel.Visible", true);
        ui.set("#UnmarriedPanel.Visible", false);

        // Status info
        ui.set("#SpouseNameLabel.Text", spouseName);
        ui.set("#MarriageDateLabel.Text", "Since: " + date);
        if (pair.officiant() != null) {
            ui.set("#OfficiantLabel.Text", "Officiated by: " + resolvePlayerName(pair.officiant()));
            ui.set("#OfficiantLabel.Visible", true);
        } else {
            ui.set("#OfficiantLabel.Visible", false);
        }

        // Spouse online status
        PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
        boolean spouseOnline = spouseRef != null && spouseRef.isValid();
        ui.set("#SpouseOnlineLabel.Text", spouseOnline ? "ONLINE" : "OFFLINE");
        ui.set("#SpouseOnlineLabel.Style.TextColor", spouseOnline ? "#66ff66" : "#ff6666");

        // View Profile — opens EndlessGuilds' read-only player profile card for the
        // spouse. Only surfaced when that page is registered (i.e. EndlessGuilds is
        // installed); the card is DB-backed so it works even while the spouse is
        // offline. Hidden by default in the .ui so servers without Guilds never see it.
        boolean profileAvailable = MenuRegistry.hasParamPage("guild-profile");
        ui.set("#ViewProfileButton.Visible", profileAvailable);
        if (profileAvailable) {
            events.addEventBinding(Activating, "#ViewProfileButton", of("Action", "marry:view_profile"), false);
        }

        // XP bonus info
        boolean nearSpouse = EndlessMarriage.getInstance().getProximitySystem().isNearSpouse(senderUuid);
        ui.set("#XpBonusLabel.Text", nearSpouse
                ? "+25% Discipline XP (Near Spouse)"
                : "+25% Discipline XP (Spouse Not Nearby)");
        ui.set("#XpBonusLabel.Style.TextColor", nearSpouse ? "#66ff66" : "#9fb6d3");

        // Home info — coords stay hidden by default to avoid accidental leaks.
        MarriageHome home = data.getHome(senderUuid);
        applyHomeInfo(ui, home);

        // Ring info — sourced from the tiered/attribute ring system (the one the
        // Rings page now drives). The legacy cosmetic WeddingRingTier is no longer
        // shown here.
        TieredRingDataManager rings = EndlessMarriage.getInstance().getTieredRingDataManager();
        TieredRingDefinition equippedRing = rings != null ? rings.getEquippedRing(senderUuid) : null;

        if (equippedRing != null) {
            ui.set("#RingIcon.ItemId", equippedRing.iconItemId());
            ui.set("#RingInfoLabel.Text", equippedRing.displayName());
            ui.set("#RingInfoLabel.Style.TextColor", equippedRing.color());
        } else {
            ui.set("#RingIcon.ItemId", TieredRingTier.E.getIconItemId());
            ui.set("#RingInfoLabel.Text", "No ring equipped");
            ui.set("#RingInfoLabel.Style.TextColor", "#7a9abf");
        }

        // The button now opens the Rings page (tier -> variation picker) instead of
        // cosmetically bumping a tier. The hint label reports unlock progress.
        int senderPrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(senderUuid);
        int spousePrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(spouseUuid);
        int lowestPrestige = Math.min(senderPrestige, spousePrestige);

        TieredRingTier highestUnlocked = TieredRingTier.E;
        TieredRingTier nextLocked = null;
        for (TieredRingTier t : TieredRingTier.values()) {
            if (lowestPrestige >= t.getPrestigeRequired()) {
                highestUnlocked = t;
            } else if (nextLocked == null) {
                nextLocked = t;
            }
        }

        if (nextLocked == null) {
            ui.set("#RingNextLabel.Text", "All tiers unlocked (up to S)");
            ui.set("#RingNextLabel.Style.TextColor", "#e0f7fa");
        } else {
            ui.set("#RingNextLabel.Text", "Unlocked to " + highestUnlocked.getDisplayName()
                    + " — " + nextLocked.getDisplayName() + " tier at Prestige " + nextLocked.getPrestigeRequired());
            ui.set("#RingNextLabel.Style.TextColor", "#7a9abf");
        }

        ui.set("#RingCard.Visible", true);

        // Button actions
        events.addEventBinding(Activating, "#TpPartnerButton", of("Action", "marry:tp"), false);
        events.addEventBinding(Activating, "#HomeButton", of("Action", "marry:home"), false);
        events.addEventBinding(Activating, "#SetHomeButton", of("Action", "marry:sethome"), false);
        events.addEventBinding(Activating, "#SetHomeConfirmButton", of("Action", "marry:sethome_confirm"), false);
        events.addEventBinding(Activating, "#SetHomeCancelButton", of("Action", "marry:sethome_cancel"), false);
        events.addEventBinding(Activating, "#ViewCoordsButton", of("Action", "marry:toggle_coords"), false);
        events.addEventBinding(Activating, "#InventoryButton", of("Action", "marry:inventory"), false);
        events.addEventBinding(Activating, "#DivorceButton", of("Action", "marry:divorce"), false);
        events.addEventBinding(Activating, "#OverflowLogButton", of("Action", "marry:overflow_log"), false);
        events.addEventBinding(Activating, "#RingsButton", of("Action", "marry:rings_ui"), false);

        // Disable buttons if spouse is offline
        if (!spouseOnline) {
            ui.set("#TpPartnerButton.Disabled", true);
            ui.set("#InventoryButton.Disabled", true);
        }

        // Role section (priests / magistrates only)
        var config = EndlessMarriage.getInstance().getMarriageConfig();
        String primaryClass = EndlessLevelingAPI.get().getPrimaryClassId(senderUuid);
        String secondaryClass = EndlessLevelingAPI.get().getSecondaryClassId(senderUuid);
        boolean isPriest = config.getPriestClassId().equalsIgnoreCase(primaryClass)
                || config.getPriestClassId().equalsIgnoreCase(secondaryClass);
        boolean isMagistrate = config.getMagistrateClassId().equalsIgnoreCase(primaryClass)
                || config.getMagistrateClassId().equalsIgnoreCase(secondaryClass);

        if (isPriest || isMagistrate) {
            ui.set("#RoleSection.Visible", true);
            events.addEventBinding(Activating, "#RecordsButton", of("Action", "marry:records"), false);
            events.addEventBinding(Activating, "#OfficiateFromMarriedButton", of("Action", "marry:officiate_ui"), false);
        }

        // ---- Side panel (married) ----
        buildSidePanelMarried(ui, events, senderUuid, spouseOnline);

        // ---- Witness panel (left) ----
        buildWitnessPanel(ui, pair);
    }

    private void buildWitnessPanel(@Nonnull UICommandBuilder ui, @Nonnull MarriagePair pair) {
        ui.set("#WitnessPanel.Visible", true);
        ui.clear("#WitnessRows");

        List<UUID> witnesses = pair.witnesses();
        UUID priestUuid = pair.officiant();

        ui.set("#WitnessCountLabel.Text", witnesses.size() == 1
                ? "1 witness"
                : witnesses.size() + " witnesses");

        int totalEntries = (priestUuid != null ? 1 : 0) + witnesses.size();
        if (totalEntries == 0) {
            return;
        }

        int rowIndex = 0;

        // Priest goes first, marked with a "PRIEST" badge.
        if (priestUuid != null) {
            ui.append("#WitnessRows", WITNESS_ROW_TEMPLATE);
            String base = "#WitnessRows[" + rowIndex + "]";
            ui.set(base + " #WitnessName.Text", resolvePlayerName(priestUuid));
            ui.set(base + " #WitnessName.Style.TextColor", "#f0c040");
            ui.set(base + " #WitnessRoleLabel.Text", "PRIEST");
            ui.set(base + " #WitnessRoleLabel.Visible", true);
            rowIndex++;
        }

        // Then each witness in the order they were collected.
        for (UUID witness : witnesses) {
            ui.append("#WitnessRows", WITNESS_ROW_TEMPLATE);
            String base = "#WitnessRows[" + rowIndex + "]";
            ui.set(base + " #WitnessName.Text", resolvePlayerName(witness));
            rowIndex++;
        }
    }

    private void buildSidePanelMarried(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
            @Nonnull UUID senderUuid, boolean spouseOnline) {

        ui.set("#SideMarriedBlock.Visible", true);
        ui.set("#SideUnmarriedBlock.Visible", false);

        PiggybackService piggyback = EndlessMarriage.getInstance().getPiggybackService();
        boolean isRiding = piggyback.isRiding(senderUuid);
        boolean isCarrying = piggyback.isCarrying(senderUuid);
        boolean inSession = isRiding || isCarrying;

        // Piggyback status text
        String statusText;
        String statusColor;
        if (isRiding) {
            statusText = "Riding your spouse";
            statusColor = "#f2a2e8";
        } else if (isCarrying) {
            statusText = "Carrying your spouse";
            statusColor = "#f2a2e8";
        } else {
            statusText = "Idle";
            statusColor = "#c0cee5";
        }
        ui.set("#PiggybackStatusLabel.Text", statusText);
        ui.set("#PiggybackStatusLabel.Style.TextColor", statusColor);

        // While in a session only DISMOUNT is actionable; PIGGYBACK and CARRY are
        // disabled. Otherwise both mount actions are available (when the spouse is
        // online) and DISMOUNT is greyed out.
        if (inSession) {
            ui.set("#PiggybackButton.Disabled", true);
            ui.set("#CarryButton.Disabled", true);
            ui.set("#DismountButton.Disabled", false);
        } else {
            ui.set("#PiggybackButton.Disabled", !spouseOnline);
            ui.set("#CarryButton.Disabled", !spouseOnline);
            ui.set("#DismountButton.Disabled", true);
        }

        events.addEventBinding(Activating, "#PiggybackButton", of("Action", "marry:piggyback"), false);
        events.addEventBinding(Activating, "#CarryButton", of("Action", "marry:carry"), false);
        events.addEventBinding(Activating, "#DismountButton", of("Action", "marry:dismount"), false);

        // Kiss card
        if (spouseOnline) {
            ui.set("#KissStatusLabel.Text", "Stand within 1 block of your spouse, then kiss.");
            ui.set("#KissStatusLabel.Style.TextColor", "#c0cee5");
            ui.set("#KissButton.Disabled", false);
        } else {
            ui.set("#KissStatusLabel.Text", "Spouse is offline.");
            ui.set("#KissStatusLabel.Style.TextColor", "#ff9999");
            ui.set("#KissButton.Disabled", true);
        }

        // Live kiss-buff status (active countdown / cooldown / ready), shared with
        // the overflow-log page so both surfaces report the same state.
        KissBuffStatus.Display kissBuff = KissBuffStatus.describe(senderUuid);
        ui.set("#KissBuffLabel.Text", kissBuff.text());
        ui.set("#KissBuffLabel.Style.TextColor", kissBuff.color());

        events.addEventBinding(Activating, "#KissButton", of("Action", "marry:kiss"), false);
    }

    private void buildSidePanelUnmarried(@Nonnull UICommandBuilder ui) {
        ui.set("#SideMarriedBlock.Visible", false);
        ui.set("#SideUnmarriedBlock.Visible", true);
    }

    private void buildUnmarriedView(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
            @Nonnull UUID senderUuid, @Nonnull MarriageDataManager data) {

        // Show unmarried panel, hide married panel
        ui.set("#MarriedPanel.Visible", false);
        ui.set("#UnmarriedPanel.Visible", true);

        // Keep the witness panel as a reserved left slot so the 1304-wide wrapper
        // stays symmetric (280|12|720|12|280) and the menu renders centered. Married
        // view fills it with witnesses; unmarried shows a placeholder.
        ui.set("#WitnessPanel.Visible", true);
        ui.clear("#WitnessRows");
        ui.set("#WitnessCountLabel.Text", "Appears after marriage");

        // Check for pending proposals
        if (data.hasProposal(senderUuid)) {
            UUID proposer = data.getProposer(senderUuid);
            ui.set("#PendingProposalLabel.Text", resolvePlayerName(proposer) + " has proposed to you!");
            ui.set("#PendingProposalLabel.Visible", true);
            ui.set("#AcceptButton.Visible", true);
            ui.set("#DenyButton.Visible", true);

            events.addEventBinding(Activating, "#AcceptButton", of("Action", "marry:accept"), false);
            events.addEventBinding(Activating, "#DenyButton", of("Action", "marry:deny"), false);
        } else {
            ui.set("#PendingProposalLabel.Visible", false);
            ui.set("#AcceptButton.Visible", false);
            ui.set("#DenyButton.Visible", false);
        }

        if (data.hasPendingProposal(senderUuid)) {
            ui.set("#OutgoingProposalLabel.Text", "You have an outgoing proposal.");
            ui.set("#OutgoingProposalLabel.Visible", true);
        } else {
            ui.set("#OutgoingProposalLabel.Visible", false);
        }

        if (data.hasPendingMarriage(senderUuid)) {
            ui.set("#PendingMarriageLabel.Text", "Awaiting a Priest to officiate.");
            ui.set("#PendingMarriageLabel.Visible", true);
            ui.set("#FindPriestButton.Visible", true);
            events.addEventBinding(Activating, "#FindPriestButton", of("Action", "marry:find_priest"), false);
        } else {
            ui.set("#PendingMarriageLabel.Visible", false);
            ui.set("#FindPriestButton.Visible", false);
        }

        // Propose button
        events.addEventBinding(Activating, "#ProposeButton", of("Action", "marry:propose_ui"), false);

        // Role section (priests / magistrates only)
        var config = EndlessMarriage.getInstance().getMarriageConfig();
        String primaryClass = EndlessLevelingAPI.get().getPrimaryClassId(senderUuid);
        String secondaryClass = EndlessLevelingAPI.get().getSecondaryClassId(senderUuid);
        boolean isPriest = config.getPriestClassId().equalsIgnoreCase(primaryClass)
                || config.getPriestClassId().equalsIgnoreCase(secondaryClass);
        boolean isMagistrate = config.getMagistrateClassId().equalsIgnoreCase(primaryClass)
                || config.getMagistrateClassId().equalsIgnoreCase(secondaryClass);

        if (isPriest || isMagistrate) {
            ui.set("#OfficiateSection.Visible", true);
            events.addEventBinding(Activating, "#RecordsUnmarriedButton", of("Action", "marry:records"), false);
            events.addEventBinding(Activating, "#OfficiateButton", of("Action", "marry:officiate_ui"), false);
        }

        // Side panel: hide affection options for unmarried players.
        buildSidePanelUnmarried(ui);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull MarriagePageData data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null || data.action.isBlank()) {
            return;
        }

        switch (data.action) {
            case "marry:tp" -> handleTpPartner();
            case "marry:home" -> handleTpHome();
            case "marry:sethome" -> handleSetHome(ref, store);
            case "marry:sethome_confirm" -> handleSetHomeConfirm(ref, store);
            case "marry:sethome_cancel" -> handleSetHomeCancel();
            case "marry:toggle_coords" -> handleToggleCoords();
            case "marry:inventory" -> handleInventory(ref, store);
            case "marry:view_profile" -> handleViewProfile(ref, store);
            case "marry:divorce" -> handleDivorce();
            case "marry:accept" -> handleAccept();
            case "marry:deny" -> handleDeny();
            case "marry:propose_ui" -> handleOpenProposePage(ref, store);
            case "marry:find_priest" -> handleOpenFindPriestPage(ref, store);
            case "marry:overflow_log" -> handleOpenOverflowLogPage(ref, store);
            case "marry:records" -> handleRecords();
            case "marry:officiate_ui" -> handleOpenOfficiatePage(ref, store);
            case "marry:rings_ui" -> handleOpenRingPage(ref, store);
            case "marry:ring_upgrade" -> handleRingUpgrade();
            case "marry:piggyback" -> handlePiggyback(ref, store);
            case "marry:carry" -> handleCarry(ref, store);
            case "marry:dismount" -> handleDismount(ref, store);
            case "marry:kiss" -> handleKiss(ref, store);
        }
    }

    /**
     * Open EndlessGuilds' read-only profile card for the spouse. Routed through the
     * core {@link MenuRegistry} param-page seam (same path EndlessLink uses), so
     * EndlessMarriage needs no compile or reflection dependency on EndlessGuilds —
     * if Guilds is absent the page key is unregistered and we degrade gracefully.
     */
    private void handleViewProfile(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
        UUID spouseUuid = data.getSpouse(senderUuid);
        if (spouseUuid == null) {
            return;
        }
        if (!MenuRegistry.openPage("guild-profile", ref, store, playerRef, spouseUuid.toString())) {
            playerRef.sendMessage(Message.raw("Profile view is unavailable.").color("#ff6666"));
        }
    }

    private void handlePiggyback(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        PiggybackService piggyback = EndlessMarriage.getInstance().getPiggybackService();

        if (piggyback.isRiding(senderUuid)) {
            piggyback.dismount(senderUuid, ref, store);
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_DISMOUNT_SELF, "#4fd7f7"));
            return;
        }
        if (piggyback.isCarrying(senderUuid)) {
            piggyback.dismountAny(senderUuid);
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_SHAKE_OFF, "#4fd7f7"));
            return;
        }

        // If a debug NPC exists for this player, use it as the carrier instead
        // of looking up a real spouse â€” this lets the menu's piggyback button
        // exercise the mount path during /marry debug menu testing.
        DebugNpcService debugNpc = EndlessMarriage.getInstance().getDebugNpcService();
        DebugNpcService.DebugNpc npc = debugNpc != null ? debugNpc.get(senderUuid) : null;
        if (npc != null) {
            PiggybackService.MountResult debugResult =
                    piggyback.tryMountTarget(senderUuid, ref, store, npc.ref(), npc.syntheticUuid());
            if (debugResult == PiggybackService.MountResult.SUCCESS) {
                playerRef.sendMessage(MarriageMessages.debugChat(MarriageMessages.DEBUG_PIGGY_NPC, "#4fd7f7"));
            } else {
                playerRef.sendMessage(MarriageMessages.debugChat(MarriageMessages.DEBUG_PIGGY_NPC_FAIL, "#ff6666", debugResult));
            }
            return;
        }

        PiggybackService.MountResult result = piggyback.tryMount(senderUuid, ref, store);
        switch (result) {
            case SUCCESS -> {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_SUCCESS_SELF, "#f2a2e8"));
                MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
                UUID spouseUuid = data.getSpouse(senderUuid);
                if (spouseUuid != null) {
                    PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
                    if (spouseRef != null && spouseRef.isValid()) {
                        spouseRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_SPOUSE_RIDING,
                                "#f2a2e8", resolvePlayerName(senderUuid)));
                    }
                }
            }
            case NOT_MARRIED ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            case SPOUSE_OFFLINE ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_NOT_ONLINE, "#ff6666"));
            case SPOUSE_NOT_IN_WORLD ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_NOT_IN_WORLD, "#ff6666"));
            case SPOUSE_DIFFERENT_WORLD ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_DIFFERENT_WORLD, "#ff6666"));
            case TOO_FAR ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_TOO_FAR, "#ff9900"));
            case ALREADY_MOUNTED ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_ALREADY_MOUNTED, "#ff9900"));
            case SPOUSE_ALREADY_CARRYING, SPOUSE_IS_RIDING ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_SPOUSE_BUSY, "#ff9900"));
            default ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_ERROR, "#ff6666"));
        }
    }

    private void handleCarry(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        PiggybackService piggyback = EndlessMarriage.getInstance().getPiggybackService();

        // Toggle off: already carrying our spouse -> set them down.
        if (piggyback.isCarrying(senderUuid)) {
            piggyback.dismountAny(senderUuid);
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.CARRY_PUT_DOWN_SELF, "#4fd7f7"));
            return;
        }
        // If we are currently being carried, /carry hops us down instead.
        if (piggyback.isRiding(senderUuid)) {
            piggyback.dismount(senderUuid, ref, store);
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_DISMOUNT_SELF, "#4fd7f7"));
            return;
        }

        PiggybackService.MountResult result = piggyback.tryCarry(senderUuid, ref, store);
        switch (result) {
            case SUCCESS -> {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.CARRY_SUCCESS_SELF, "#f2a2e8"));
                MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
                UUID spouseUuid = data.getSpouse(senderUuid);
                if (spouseUuid != null) {
                    PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
                    if (spouseRef != null && spouseRef.isValid()) {
                        spouseRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.CARRY_SPOUSE_CARRIED,
                                "#f2a2e8", resolvePlayerName(senderUuid)));
                    }
                }
            }
            case NOT_MARRIED ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            case SPOUSE_OFFLINE ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_NOT_ONLINE, "#ff6666"));
            case SPOUSE_NOT_IN_WORLD ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_NOT_IN_WORLD, "#ff6666"));
            case SPOUSE_DIFFERENT_WORLD ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_DIFFERENT_WORLD, "#ff6666"));
            case TOO_FAR ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.CARRY_TOO_FAR, "#ff9900"));
            case ALREADY_CARRYING ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.CARRY_ALREADY_CARRYING, "#ff9900"));
            case SELF_IS_RIDING ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.CARRY_SELF_RIDING, "#ff9900"));
            case SPOUSE_ALREADY_CARRYING, SPOUSE_IS_RIDING ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_SPOUSE_BUSY, "#ff9900"));
            default ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_ERROR, "#ff6666"));
        }
    }

    private void handleDismount(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        PiggybackService piggyback = EndlessMarriage.getInstance().getPiggybackService();
        if (piggyback.isRiding(senderUuid)) {
            piggyback.dismount(senderUuid, ref, store);
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_DISMOUNT_SELF, "#4fd7f7"));
        } else if (piggyback.isCarrying(senderUuid)) {
            piggyback.dismountAny(senderUuid);
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PIGGYBACK_SHAKE_OFF, "#4fd7f7"));
        } else {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.DISMOUNT_NOT_IN_SESSION, "#ff9900"));
        }
    }

    private void handleKiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        KissService kissService = EndlessMarriage.getInstance().getKissService();

        // Route through the debug NPC if one is active for this player.
        DebugNpcService debugNpc = EndlessMarriage.getInstance().getDebugNpcService();
        DebugNpcService.DebugNpc npc = debugNpc != null ? debugNpc.get(senderUuid) : null;
        if (npc != null) {
            KissService.KissResult debugResult =
                    kissService.tryKissTarget(senderUuid, ref, store, npc.ref());
            if (debugResult == KissService.KissResult.SUCCESS) {
                playerRef.sendMessage(MarriageMessages.debugChat(MarriageMessages.DEBUG_KISS_NPC, "#f2a2e8"));
            } else {
                playerRef.sendMessage(MarriageMessages.debugChat(MarriageMessages.DEBUG_KISS_NPC_FAIL, "#ff6666", debugResult));
            }
            return;
        }

        KissService.KissResult result = kissService.tryKiss(senderUuid, ref, store);
        switch (result) {
            case SUCCESS -> {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.KISS_SUCCESS_SELF, "#f2a2e8"));
                MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
                UUID spouseUuid = data.getSpouse(senderUuid);
                if (spouseUuid != null) {
                    PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
                    if (spouseRef != null && spouseRef.isValid()) {
                        spouseRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.KISS_RECEIVED,
                                "#f2a2e8", resolvePlayerName(senderUuid)));
                    }
                }
            }
            case NOT_MARRIED ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            case SPOUSE_OFFLINE ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_NOT_ONLINE, "#ff6666"));
            case SPOUSE_NOT_IN_WORLD ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_NOT_IN_WORLD, "#ff6666"));
            case SPOUSE_DIFFERENT_WORLD ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_DIFFERENT_WORLD, "#ff6666"));
            case TOO_FAR ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.KISS_TOO_FAR, "#ff9900"));
            case ERROR ->
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.KISS_ERROR, "#ff6666"));
        }
    }

    private void handleTpPartner() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        PlayerRef spouseRef = spouseUuid != null ? Universe.get().getPlayer(spouseUuid) : null;
        if (spouseRef == null || !spouseRef.isValid()) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_NOT_ONLINE, "#ff6666"));
            return;
        }

        Ref<EntityStore> spouseEntity = spouseRef.getReference();
        if (spouseEntity == null) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_NOT_IN_WORLD, "#ff6666"));
            return;
        }

        Store<EntityStore> spouseStore = spouseEntity.getStore();
        Vector3d spousePos = resolvePosition(spouseEntity, spouseStore);
        if (spousePos == null) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.TP_CANNOT_LOCATE, "#ff6666"));
            return;
        }

        World spouseWorld = spouseStore.getExternalData().getWorld();
        Teleport teleport = Teleport.createForPlayer(spouseWorld, new Vector3d(spousePos), new Rotation3f(0f, 0f, 0f));
        entityStore.addComponent(entityRef, Teleport.getComponentType(), teleport);
        playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.TP_TELEPORTING_TO, "#66ff66",
                resolvePlayerName(spouseUuid)));
    }

    private void handleTpHome() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            return;
        }

        MarriageHome home = data.getHome(senderUuid);
        if (home == null) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.HOME_NO_HOME_SHORT, "#ff6666"));
            return;
        }

        World targetWorld = Universe.get().getWorld(home.worldName());
        if (targetWorld == null) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.HOME_WORLD_MISSING, "#ff6666",
                    home.worldName()));
            return;
        }

        Vector3d pos = new Vector3d(home.x(), home.y(), home.z());
        Rotation3f rot = new Rotation3f(home.pitch(), home.yaw(), 0f);
        Teleport teleport = Teleport.createForPlayer(targetWorld, pos, rot);
        entityStore.addComponent(entityRef, Teleport.getComponentType(), teleport);
        playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.HOME_TELEPORTING, "#66ff66"));
    }

    private void handleSetHome(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        if (spouseUuid == null) {
            return;
        }

        // A home is already saved — require confirmation before overwriting so a
        // stray click can't move the shared home somewhere unintended.
        if (data.getHome(senderUuid) != null) {
            UICommandBuilder ui = new UICommandBuilder();
            ui.set("#SetHomeConfirmBar.Visible", true);
            ui.set("#SetHomeButton.Disabled", true);
            sendUpdate(ui, false);
            return;
        }

        doSetHome(ref, store, senderUuid, spouseUuid);
    }

    private void handleSetHomeConfirm(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#SetHomeConfirmBar.Visible", false);
        ui.set("#SetHomeButton.Disabled", false);

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            sendUpdate(ui, false);
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        if (spouseUuid != null && doSetHome(ref, store, senderUuid, spouseUuid)) {
            applyHomeInfo(ui, data.getHome(senderUuid));
        }
        sendUpdate(ui, false);
    }

    private void handleSetHomeCancel() {
        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#SetHomeConfirmBar.Visible", false);
        ui.set("#SetHomeButton.Disabled", false);
        sendUpdate(ui, false);
    }

    private void handleToggleCoords() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
        MarriageHome home = data.getHome(senderUuid);
        if (home == null) {
            return;
        }
        coordsRevealed = !coordsRevealed;
        UICommandBuilder ui = new UICommandBuilder();
        applyHomeInfo(ui, home);
        sendUpdate(ui, false);
    }

    /** Captures the player's current location as the shared marriage home.
     *  Returns {@code false} (and notifies the player) if the position is
     *  unavailable. */
    private boolean doSetHome(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull UUID senderUuid, @Nonnull UUID spouseUuid) {
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SETHOME_POSITION_UNKNOWN, "#ff6666"));
            return false;
        }

        Vector3d pos = transform.getPosition();
        World world = store.getExternalData().getWorld();

        float yaw = 0f;
        float pitch = 0f;
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRotation != null) {
            yaw = headRotation.getRotation().yaw();
            pitch = headRotation.getRotation().pitch();
        }

        MarriageHome home = new MarriageHome(world.getName(), pos.x(), pos.y(), pos.z(), yaw, pitch);
        data.setHome(senderUuid, spouseUuid, home);
        playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SETHOME_SUCCESS_SHORT, "#66ff66"));
        return true;
    }

    /** Renders the home card label + VIEW COORDS toggle, keeping the raw
     *  coordinates hidden unless the player has explicitly revealed them. */
    private void applyHomeInfo(@Nonnull UICommandBuilder ui, @Nullable MarriageHome home) {
        if (home == null) {
            ui.set("#HomeInfoLabel.Text", "No home set");
            ui.set("#ViewCoordsButton.Visible", false);
            return;
        }

        ui.set("#ViewCoordsButton.Visible", true);
        if (coordsRevealed) {
            ui.set("#HomeInfoLabel.Text",
                    String.format("Home: %.0f, %.0f, %.0f (%s)", home.x(), home.y(), home.z(), home.worldName()));
            ui.set("#ViewCoordsButton.Text", "HIDE COORDS");
        } else {
            ui.set("#HomeInfoLabel.Text", "Home set · coords hidden");
            ui.set("#ViewCoordsButton.Text", "VIEW COORDS");
        }
    }

    private void handleInventory(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        PlayerRef spouseRef = spouseUuid != null ? Universe.get().getPlayer(spouseUuid) : null;
        if (spouseRef == null || !spouseRef.isValid()) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_NOT_ONLINE, "#ff6666"));
            return;
        }

        Ref<EntityStore> spouseEntity = spouseRef.getReference();
        if (spouseEntity == null) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_NOT_IN_WORLD, "#ff6666"));
            return;
        }

        Store<EntityStore> spouseStore = spouseEntity.getStore();
        World spouseWorld = spouseStore.getExternalData().getWorld();

        Player senderPlayer = store.getComponent(ref, Player.getComponentType());
        if (senderPlayer == null) {
            return;
        }

        spouseWorld.execute(() -> {
            Player spousePlayer = spouseStore.getComponent(spouseEntity, Player.getComponentType());
            if (spousePlayer == null) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.INV_CANNOT_ACCESS, "#ff6666"));
                return;
            }

            Inventory spouseInventory = spousePlayer.getInventory();
            CombinedItemContainer spouseContainer = spouseInventory.getCombinedHotbarFirst();

            World senderWorld = store.getExternalData().getWorld();
            senderWorld.execute(() -> {
                senderPlayer.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, new ContainerWindow(spouseContainer));
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.INV_VIEWING, "#4fd7f7",
                        resolvePlayerName(spouseUuid)));
            });
        });
    }

    private void handleDivorce() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            return;
        }

        // Enforce 72-hour minimum before divorce is allowed
        MarriagePair currentPair = data.getMarriage(senderUuid);
        if (currentPair != null) {
            long elapsed = System.currentTimeMillis() - currentPair.timestamp();
            long minMs = 72L * 60L * 60L * 1000L;
            if (elapsed < minMs) {
                long remaining = minMs - elapsed;
                long hours = remaining / 3_600_000L;
                long minutes = (remaining % 3_600_000L) / 60_000L;
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.MIN_MARRIAGE_TIME, "#ff6666",
                        com.airijko.endlessleveling.util.Lang.tr(MarriageMessages.COOLDOWN_HM, "{0}h {1}m", hours, minutes)));
                return;
            }
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        var config = EndlessMarriage.getInstance().getMarriageConfig();

        if (!config.isRequireMagistrateForDivorce()) {
            data.divorce(senderUuid, spouseUuid, null);
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.DIVORCED_SIMPLE, "#ff9900"));
            PlayerRef spouseRef = spouseUuid != null ? Universe.get().getPlayer(spouseUuid) : null;
            if (spouseRef != null) {
                spouseRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SPOUSE_DIVORCED_YOU, "#ff9900",
                        resolvePlayerName(senderUuid)));
            }
        } else {
            data.addPendingDivorce(senderUuid);
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.DIVORCE_PENDING, "#ff9900"));
        }
    }

    private void handleAccept() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
        var config = EndlessMarriage.getInstance().getMarriageConfig();

        UUID proposer = data.getProposer(senderUuid);
        if (proposer == null) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NO_PENDING_PROPOSALS, "#ff6666"));
            return;
        }

        // Remarriage cooldown: check both the acceptor and the proposer
        final long minMs = 72L * 60L * 60L * 1000L;
        Long senderDivorceTime = data.getDivorceTimestamp(senderUuid);
        if (senderDivorceTime != null) {
            long elapsed = System.currentTimeMillis() - senderDivorceTime;
            if (elapsed < minMs) {
                long remaining = minMs - elapsed;
                long hours = remaining / 3_600_000L;
                long minutes = (remaining % 3_600_000L) / 60_000L;
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.COOLDOWN_ACCEPTOR, "#ff6666",
                        com.airijko.endlessleveling.util.Lang.tr(MarriageMessages.COOLDOWN_HM, "{0}h {1}m", hours, minutes)));
                return;
            }
        }
        Long proposerDivorceTime = data.getDivorceTimestamp(proposer);
        if (proposerDivorceTime != null) {
            long elapsed = System.currentTimeMillis() - proposerDivorceTime;
            if (elapsed < minMs) {
                long remaining = minMs - elapsed;
                long hours = remaining / 3_600_000L;
                long minutes = (remaining % 3_600_000L) / 60_000L;
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.COOLDOWN_PROPOSER, "#ff6666",
                        com.airijko.endlessleveling.util.Lang.tr(MarriageMessages.COOLDOWN_HM, "{0}h {1}m", hours, minutes)));
                return;
            }
        }

        data.removeProposal(proposer);

        if (!config.isRequirePriestForMarriage()) {
            data.marry(proposer, senderUuid, null);
            String senderName = resolvePlayerName(senderUuid);
            String proposerName = resolvePlayerName(proposer);
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOW_MARRIED_TO, "#66ff66", proposerName));
            PlayerRef proposerRef = Universe.get().getPlayer(proposer);
            if (proposerRef != null) {
                proposerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.ACCEPTED_BY_SHORT, "#66ff66", senderName));
            }
            // Global wedding announcement: title, chat broadcast, wedding march SFX
            MarriageAnnouncer.announceMarriage(proposerName, senderName, null);
        } else {
            data.addPendingMarriage(proposer, senderUuid);
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.ACCEPTED_PRIEST_NEEDED_SIMPLE, "#66ff66"));
            PlayerRef proposerRef = Universe.get().getPlayer(proposer);
            if (proposerRef != null) {
                proposerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PROPOSER_ACCEPTED_PRIEST_NEEDED,
                        "#66ff66", resolvePlayerName(senderUuid)));
            }
        }
    }

    private void handleDeny() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        UUID proposer = data.getProposer(senderUuid);
        if (proposer == null) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NO_PENDING_PROPOSALS, "#ff6666"));
            return;
        }

        data.removeProposal(proposer);
        playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PROPOSAL_DENIED, "#ff9900"));
        PlayerRef proposerRef = Universe.get().getPlayer(proposer);
        if (proposerRef != null) {
            proposerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PROPOSER_DENIED, "#ff6666",
                    resolvePlayerName(senderUuid)));
        }
    }

    private void handleOpenProposePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        MarriageProposePage page = new MarriageProposePage(playerRef, CustomPageLifetime.CanDismiss);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void handleOpenFindPriestPage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        MarriagePriestPage page = new MarriagePriestPage(playerRef, CustomPageLifetime.CanDismiss);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void handleRingUpgrade() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        if (spouseUuid == null) {
            return;
        }

        WeddingRingTier current = data.getRing(senderUuid);
        WeddingRingTier next = current != null ? current.next() : WeddingRingTier.lowest();
        if (next == null) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.RING_MAX, "#ff9900"));
            return;
        }

        int senderPrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(senderUuid);
        int spousePrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(spouseUuid);
        int lowestPrestige = Math.min(senderPrestige, spousePrestige);

        if (lowestPrestige < next.getPrestigeRequired()) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.RING_NEED_PRESTIGE, "#ff6666",
                    next.getPrestigeRequired()));
            return;
        }

        // Placeholder: economy check would go here
        // if (next.getCost() > 0 && !hasBalance(senderUuid, next.getCost())) { ... }

        data.setRing(senderUuid, spouseUuid, next);
        playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.RING_UPGRADED, "#66ff66", next.getDisplayName()));

        PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
        if (spouseRef != null && spouseRef.isValid()) {
            spouseRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.RING_SPOUSE_UPGRADED, "#4fd7f7",
                    resolvePlayerName(senderUuid), next.getDisplayName()));
        }
    }

    private void handleOpenOverflowLogPage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        MarriageOverflowLogPage page = new MarriageOverflowLogPage(playerRef, CustomPageLifetime.CanDismiss);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void handleOpenRingPage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        MarriageRingPage page = new MarriageRingPage(playerRef, CustomPageLifetime.CanDismiss);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void handleOpenOfficiatePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        MarriageOfficiatePage page = new MarriageOfficiatePage(playerRef, CustomPageLifetime.CanDismiss);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private void handleRecords() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
        var records = data.getRecordsForOfficiant(senderUuid);

        if (records.isEmpty()) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.RECORDS_NONE, "#4fd7f7"));
            return;
        }

        playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.RECORDS_HEADER, "#4fd7f7", records.size()));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (var record : records) {
            String typeName = record.type() == com.airijko.endlessmarriage.data.OfficiantRecord.OfficiantType.MARRIAGE
                    ? MarriageMessages.text(MarriageMessages.RECORDS_TYPE_MARRIAGE)
                    : MarriageMessages.text(MarriageMessages.RECORDS_TYPE_DIVORCE);
            String dateStr = fmt.format(new Date(record.timestamp()));
            playerRef.sendMessage(MarriageMessages.line(MarriageMessages.RECORDS_ENTRY, "#4fd7f7",
                    typeName,
                    resolvePlayerName(record.player1()),
                    resolvePlayerName(record.player2()),
                    dateStr));
        }
    }

    @Nullable
    private Vector3d resolvePosition(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || store == null) {
            return null;
        }
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            return transform != null ? transform.getPosition() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    @Nonnull
    private String resolvePlayerName(@Nonnull UUID uuid) {
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref != null) {
            String username = ref.getUsername();
            if (username != null) {
                return username;
            }
        }
        var snapshot = com.airijko.endlessleveling.api.EndlessLevelingAPI.get().getPlayerSnapshot(uuid);
        if (snapshot != null) {
            String snapshotName = snapshot.playerName();
            if (snapshotName != null) {
                return snapshotName;
            }
        }
        // Offline players miss the live cache; resolve the name read-only via the
        // name-only DAO query (not a full profile-card build).
        java.util.Map<String, String> names = com.airijko.endlessleveling.api.EndlessLevelingAPI.get()
                .getPlayerNames(java.util.Collections.singletonList(uuid));
        String name = names.get(uuid.toString());
        if (name != null && !name.isBlank()) {
            return name;
        }
        String fallback = uuid.toString().substring(0, 8);
        return fallback != null ? fallback : "";
    }
}
