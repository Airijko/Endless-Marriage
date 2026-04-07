/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.ui;

import com.airijko.endlessleveling.endlessmarriage.EndlessMarriage;
import com.airijko.endlessleveling.endlessmarriage.MarriageAnnouncer;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageHome;
import com.airijko.endlessleveling.endlessmarriage.data.MarriagePair;
import com.airijko.endlessleveling.endlessmarriage.data.WeddingRingTier;
import com.airijko.endlessleveling.endlessmarriage.services.DebugNpcService;
import com.airijko.endlessleveling.endlessmarriage.services.KissService;
import com.airijko.endlessleveling.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
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
public class MarriageMainPage extends InteractiveCustomUIPage<MarriagePageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String WITNESS_ROW_TEMPLATE = "Pages/Marriage/WitnessRow.ui";

    private final PlayerRef playerRef;
    private final Ref<EntityStore> entityRef;
    private final Store<EntityStore> entityStore;
    /** When non-null, the page renders the married view using this fake pair regardless of real marital status. Used by /marry debug menu. */
    @Nullable
    private final MarriagePair forcedPair;

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

        // XP bonus info
        boolean nearSpouse = EndlessMarriage.getInstance().getProximitySystem().isNearSpouse(senderUuid);
        ui.set("#XpBonusLabel.Text", nearSpouse
                ? "+25% Discipline XP (Near Spouse)"
                : "+25% Discipline XP (Spouse Not Nearby)");
        ui.set("#XpBonusLabel.Style.TextColor", nearSpouse ? "#66ff66" : "#9fb6d3");

        // Home info
        MarriageHome home = data.getHome(senderUuid);
        ui.set("#HomeInfoLabel.Text", home != null
                ? String.format("Home: %.0f, %.0f, %.0f (%s)", home.x(), home.y(), home.z(), home.worldName())
                : "No home set");

        // Ring info
        WeddingRingTier ring = data.getRing(senderUuid);
        WeddingRingTier displayedRing = ring != null ? ring : WeddingRingTier.lowest();
        ui.set("#RingIcon.ItemId", displayedRing.getIconItemId());

        if (ring != null) {
            ui.set("#RingInfoLabel.Text", ring.getDisplayName() + " Ring");
            ui.set("#RingInfoLabel.Style.TextColor", ring.getColor());
        } else {
            ui.set("#RingInfoLabel.Text", "No ring equipped");
            ui.set("#RingInfoLabel.Style.TextColor", "#7a9abf");
        }

        // Upgrade button — always visible; Enabled toggles based on eligibility
        // so the ring card's layout stays stable.
        WeddingRingTier nextTier = ring != null ? ring.next() : WeddingRingTier.lowest();
        int senderPrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(senderUuid);
        int spousePrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(spouseUuid);
        int lowestPrestige = Math.min(senderPrestige, spousePrestige);

        if (nextTier == null) {
            // Player is at max tier
            ui.set("#RingUpgradeButton.Text", "MAX TIER");
            ui.set("#RingUpgradeButton.Disabled", true);
            ui.set("#RingNextLabel.Text", "Max tier reached");
            ui.set("#RingNextLabel.Style.TextColor", "#e0f7fa");
        } else if (lowestPrestige >= nextTier.getPrestigeRequired()) {
            // Eligible for upgrade
            ui.set("#RingUpgradeButton.Text", "UPGRADE TO " + nextTier.getDisplayName().toUpperCase());
            ui.set("#RingUpgradeButton.Disabled", false);
            ui.set("#RingNextLabel.Text", "Ready to upgrade to " + nextTier.getDisplayName() + " Ring");
            ui.set("#RingNextLabel.Style.TextColor", nextTier.getColor());
        } else {
            // Not eligible
            ui.set("#RingUpgradeButton.Text", "LOCKED");
            ui.set("#RingUpgradeButton.Disabled", true);
            ui.set("#RingNextLabel.Text", "Next: " + nextTier.getDisplayName()
                    + " (Prestige " + nextTier.getPrestigeRequired() + ")");
            ui.set("#RingNextLabel.Style.TextColor", "#7a9abf");
        }

        // Always bind the event — the disabled state prevents activation when not eligible
        events.addEventBinding(Activating, "#RingUpgradeButton", of("Action", "marry:ring_upgrade"), false);

        ui.set("#RingCard.Visible", true);

        // Button actions
        events.addEventBinding(Activating, "#TpPartnerButton", of("Action", "marry:tp"), false);
        events.addEventBinding(Activating, "#HomeButton", of("Action", "marry:home"), false);
        events.addEventBinding(Activating, "#SetHomeButton", of("Action", "marry:sethome"), false);
        events.addEventBinding(Activating, "#InventoryButton", of("Action", "marry:inventory"), false);
        events.addEventBinding(Activating, "#DivorceButton", of("Action", "marry:divorce"), false);
        events.addEventBinding(Activating, "#StatusButton", of("Action", "marry:status"), false);
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

        // Piggyback button label flips between actions when already in a session.
        if (inSession) {
            ui.set("#PiggybackButton.Text", "DISMOUNT");
            ui.set("#PiggybackButton.Disabled", false);
            ui.set("#DismountButton.Disabled", false);
        } else {
            ui.set("#PiggybackButton.Text", "PIGGYBACK");
            ui.set("#PiggybackButton.Disabled", !spouseOnline);
            ui.set("#DismountButton.Disabled", true);
        }

        events.addEventBinding(Activating, "#PiggybackButton", of("Action", "marry:piggyback"), false);
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

        // Witness panel is married-only.
        ui.set("#WitnessPanel.Visible", false);

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
            case "marry:inventory" -> handleInventory(ref, store);
            case "marry:divorce" -> handleDivorce();
            case "marry:accept" -> handleAccept();
            case "marry:deny" -> handleDeny();
            case "marry:propose_ui" -> handleOpenProposePage(ref, store);
            case "marry:find_priest" -> handleOpenFindPriestPage(ref, store);
            case "marry:status" -> handleStatus();
            case "marry:records" -> handleRecords();
            case "marry:officiate_ui" -> handleOpenOfficiatePage(ref, store);
            case "marry:rings_ui" -> handleOpenRingPage(ref, store);
            case "marry:ring_upgrade" -> handleRingUpgrade();
            case "marry:piggyback" -> handlePiggyback(ref, store);
            case "marry:dismount" -> handleDismount(ref, store);
            case "marry:kiss" -> handleKiss(ref, store);
        }
    }

    private void handlePiggyback(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        PiggybackService piggyback = EndlessMarriage.getInstance().getPiggybackService();

        if (piggyback.isRiding(senderUuid)) {
            piggyback.dismount(senderUuid, ref, store);
            playerRef.sendMessage(Message.raw("[Marriage] You hop down off your spouse.").color("#4fd7f7"));
            return;
        }
        if (piggyback.isCarrying(senderUuid)) {
            piggyback.dismountAny(senderUuid);
            playerRef.sendMessage(Message.raw("[Marriage] You let your spouse down.").color("#4fd7f7"));
            return;
        }

        // If a debug NPC exists for this player, use it as the carrier instead
        // of looking up a real spouse — this lets the menu's piggyback button
        // exercise the mount path during /marry debug menu testing.
        DebugNpcService debugNpc = EndlessMarriage.getInstance().getDebugNpcService();
        DebugNpcService.DebugNpc npc = debugNpc != null ? debugNpc.get(senderUuid) : null;
        if (npc != null) {
            PiggybackService.MountResult debugResult =
                    piggyback.tryMountTarget(senderUuid, ref, store, npc.ref(), npc.syntheticUuid());
            if (debugResult == PiggybackService.MountResult.SUCCESS) {
                playerRef.sendMessage(Message.raw("[Marriage Debug] You hop onto the debug NPC.").color("#4fd7f7"));
            } else {
                playerRef.sendMessage(Message.raw("[Marriage Debug] Could not piggyback debug NPC: " + debugResult).color("#ff6666"));
            }
            return;
        }

        PiggybackService.MountResult result = piggyback.tryMount(senderUuid, ref, store);
        switch (result) {
            case SUCCESS -> {
                playerRef.sendMessage(Message.raw("[Marriage] You hop onto your spouse's back!").color("#f2a2e8"));
                MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
                UUID spouseUuid = data.getSpouse(senderUuid);
                if (spouseUuid != null) {
                    PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
                    if (spouseRef != null && spouseRef.isValid()) {
                        spouseRef.sendMessage(Message.raw("[Marriage] " + resolvePlayerName(senderUuid)
                                + " is riding piggyback!").color("#f2a2e8"));
                    }
                }
            }
            case NOT_MARRIED ->
                playerRef.sendMessage(Message.raw("[Marriage] You are not married.").color("#ff6666"));
            case SPOUSE_OFFLINE ->
                playerRef.sendMessage(Message.raw("[Marriage] Your spouse is not online.").color("#ff6666"));
            case SPOUSE_NOT_IN_WORLD ->
                playerRef.sendMessage(Message.raw("[Marriage] Your spouse is not in a world.").color("#ff6666"));
            case SPOUSE_DIFFERENT_WORLD ->
                playerRef.sendMessage(Message.raw("[Marriage] Your spouse is in a different world.").color("#ff6666"));
            case TOO_FAR ->
                playerRef.sendMessage(Message.raw("[Marriage] You must be next to your spouse to piggyback.").color("#ff9900"));
            case ALREADY_MOUNTED ->
                playerRef.sendMessage(Message.raw("[Marriage] You are already mounted.").color("#ff9900"));
            case SPOUSE_ALREADY_CARRYING, SPOUSE_IS_RIDING ->
                playerRef.sendMessage(Message.raw("[Marriage] Your spouse is already in a piggyback session.").color("#ff9900"));
            case ERROR ->
                playerRef.sendMessage(Message.raw("[Marriage] Could not piggyback right now.").color("#ff6666"));
        }
    }

    private void handleDismount(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        PiggybackService piggyback = EndlessMarriage.getInstance().getPiggybackService();
        if (piggyback.isRiding(senderUuid)) {
            piggyback.dismount(senderUuid, ref, store);
            playerRef.sendMessage(Message.raw("[Marriage] You hop down off your spouse.").color("#4fd7f7"));
        } else if (piggyback.isCarrying(senderUuid)) {
            piggyback.dismountAny(senderUuid);
            playerRef.sendMessage(Message.raw("[Marriage] You let your spouse down.").color("#4fd7f7"));
        } else {
            playerRef.sendMessage(Message.raw("[Marriage] You are not in a piggyback session.").color("#ff9900"));
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
                playerRef.sendMessage(Message.raw("[Marriage Debug] You kiss the debug NPC.").color("#f2a2e8"));
            } else {
                playerRef.sendMessage(Message.raw("[Marriage Debug] Could not kiss debug NPC: " + debugResult).color("#ff6666"));
            }
            return;
        }

        KissService.KissResult result = kissService.tryKiss(senderUuid, ref, store);
        switch (result) {
            case SUCCESS -> {
                playerRef.sendMessage(Message.raw("[Marriage] You share a kiss with your spouse.").color("#f2a2e8"));
                MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
                UUID spouseUuid = data.getSpouse(senderUuid);
                if (spouseUuid != null) {
                    PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
                    if (spouseRef != null && spouseRef.isValid()) {
                        spouseRef.sendMessage(Message.raw("[Marriage] " + resolvePlayerName(senderUuid)
                                + " kissed you!").color("#f2a2e8"));
                    }
                }
            }
            case NOT_MARRIED ->
                playerRef.sendMessage(Message.raw("[Marriage] You are not married.").color("#ff6666"));
            case SPOUSE_OFFLINE ->
                playerRef.sendMessage(Message.raw("[Marriage] Your spouse is not online.").color("#ff6666"));
            case SPOUSE_NOT_IN_WORLD ->
                playerRef.sendMessage(Message.raw("[Marriage] Your spouse is not in a world.").color("#ff6666"));
            case SPOUSE_DIFFERENT_WORLD ->
                playerRef.sendMessage(Message.raw("[Marriage] Your spouse is in a different world.").color("#ff6666"));
            case TOO_FAR ->
                playerRef.sendMessage(Message.raw("[Marriage] You must be within 1 block of your spouse to kiss.").color("#ff9900"));
            case ERROR ->
                playerRef.sendMessage(Message.raw("[Marriage] Could not kiss right now.").color("#ff6666"));
        }
    }

    private void handleTpPartner() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(Message.raw("[Marriage] You are not married.").color("#ff6666"));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        PlayerRef spouseRef = spouseUuid != null ? Universe.get().getPlayer(spouseUuid) : null;
        if (spouseRef == null || !spouseRef.isValid()) {
            playerRef.sendMessage(Message.raw("[Marriage] Your spouse is not online.").color("#ff6666"));
            return;
        }

        Ref<EntityStore> spouseEntity = spouseRef.getReference();
        if (spouseEntity == null) {
            playerRef.sendMessage(Message.raw("[Marriage] Your spouse is not in a world.").color("#ff6666"));
            return;
        }

        Store<EntityStore> spouseStore = spouseEntity.getStore();
        Vector3d spousePos = resolvePosition(spouseEntity, spouseStore);
        if (spousePos == null) {
            playerRef.sendMessage(Message.raw("[Marriage] Could not locate your spouse.").color("#ff6666"));
            return;
        }

        World spouseWorld = spouseStore.getExternalData().getWorld();
        Teleport teleport = Teleport.createForPlayer(spouseWorld, spousePos.clone(), new Vector3f(0f, 0f, 0f));
        entityStore.addComponent(entityRef, Teleport.getComponentType(), teleport);
        playerRef.sendMessage(Message.raw("[Marriage] Teleporting to " + resolvePlayerName(spouseUuid) + "...").color("#66ff66"));
    }

    private void handleTpHome() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(Message.raw("[Marriage] You are not married.").color("#ff6666"));
            return;
        }

        MarriageHome home = data.getHome(senderUuid);
        if (home == null) {
            playerRef.sendMessage(Message.raw("[Marriage] No marriage home set. Use Set Home first.").color("#ff6666"));
            return;
        }

        World targetWorld = Universe.get().getWorld(home.worldName());
        if (targetWorld == null) {
            playerRef.sendMessage(Message.raw("[Marriage] World '" + home.worldName() + "' no longer exists.").color("#ff6666"));
            return;
        }

        Vector3d pos = new Vector3d(home.x(), home.y(), home.z());
        Vector3f rot = new Vector3f(home.pitch(), home.yaw(), 0f);
        Teleport teleport = Teleport.createForPlayer(targetWorld, pos, rot);
        entityStore.addComponent(entityRef, Teleport.getComponentType(), teleport);
        playerRef.sendMessage(Message.raw("[Marriage] Teleporting to marriage home...").color("#66ff66"));
    }

    private void handleSetHome(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(Message.raw("[Marriage] You are not married.").color("#ff6666"));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        if (spouseUuid == null) {
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            playerRef.sendMessage(Message.raw("[Marriage] Unable to get your position.").color("#ff6666"));
            return;
        }

        Vector3d pos = transform.getPosition();
        World world = store.getExternalData().getWorld();

        float yaw = 0f;
        float pitch = 0f;
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRotation != null) {
            yaw = headRotation.getRotation().getYaw();
            pitch = headRotation.getRotation().getPitch();
        }

        MarriageHome home = new MarriageHome(world.getName(), pos.getX(), pos.getY(), pos.getZ(), yaw, pitch);
        data.setHome(senderUuid, spouseUuid, home);
        playerRef.sendMessage(Message.raw("[Marriage] Marriage home set!").color("#66ff66"));
    }

    private void handleInventory(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(Message.raw("[Marriage] You are not married.").color("#ff6666"));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        PlayerRef spouseRef = spouseUuid != null ? Universe.get().getPlayer(spouseUuid) : null;
        if (spouseRef == null || !spouseRef.isValid()) {
            playerRef.sendMessage(Message.raw("[Marriage] Your spouse is not online.").color("#ff6666"));
            return;
        }

        Ref<EntityStore> spouseEntity = spouseRef.getReference();
        if (spouseEntity == null) {
            playerRef.sendMessage(Message.raw("[Marriage] Your spouse is not in a world.").color("#ff6666"));
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
                playerRef.sendMessage(Message.raw("[Marriage] Could not access spouse's inventory.").color("#ff6666"));
                return;
            }

            Inventory spouseInventory = spousePlayer.getInventory();
            CombinedItemContainer spouseContainer = spouseInventory.getCombinedHotbarFirst();

            World senderWorld = store.getExternalData().getWorld();
            senderWorld.execute(() -> {
                senderPlayer.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, new ContainerWindow(spouseContainer));
                playerRef.sendMessage(Message.raw("[Marriage] Viewing " + resolvePlayerName(spouseUuid) + "'s inventory.").color("#4fd7f7"));
            });
        });
    }

    private void handleDivorce() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(Message.raw("[Marriage] You are not married.").color("#ff6666"));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        var config = EndlessMarriage.getInstance().getMarriageConfig();

        if (!config.isRequireMagistrateForDivorce()) {
            data.divorce(senderUuid, spouseUuid, null);
            playerRef.sendMessage(Message.raw("[Marriage] You are now divorced.").color("#ff9900"));
            PlayerRef spouseRef = spouseUuid != null ? Universe.get().getPlayer(spouseUuid) : null;
            if (spouseRef != null) {
                spouseRef.sendMessage(Message.raw("[Marriage] " + resolvePlayerName(senderUuid) + " has divorced you.").color("#ff9900"));
            }
        } else {
            data.addPendingDivorce(senderUuid);
            playerRef.sendMessage(Message.raw("[Marriage] Divorce requested. A Magistrate must finalize it.").color("#ff9900"));
        }
    }

    private void handleAccept() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
        var config = EndlessMarriage.getInstance().getMarriageConfig();

        UUID proposer = data.getProposer(senderUuid);
        if (proposer == null) {
            playerRef.sendMessage(Message.raw("[Marriage] No pending proposals.").color("#ff6666"));
            return;
        }

        data.removeProposal(proposer);

        if (!config.isRequirePriestForMarriage()) {
            data.marry(proposer, senderUuid, null);
            String senderName = resolvePlayerName(senderUuid);
            String proposerName = resolvePlayerName(proposer);
            playerRef.sendMessage(Message.raw("[Marriage] You are now married to " + proposerName + "!").color("#66ff66"));
            PlayerRef proposerRef = Universe.get().getPlayer(proposer);
            if (proposerRef != null) {
                proposerRef.sendMessage(Message.raw("[Marriage] " + senderName + " accepted! You are now married!").color("#66ff66"));
            }
            // Global wedding announcement: title, chat broadcast, wedding march SFX
            MarriageAnnouncer.announceMarriage(proposerName, senderName, null);
        } else {
            data.addPendingMarriage(proposer, senderUuid);
            playerRef.sendMessage(Message.raw("[Marriage] Accepted! A Priest must now officiate.").color("#66ff66"));
            PlayerRef proposerRef = Universe.get().getPlayer(proposer);
            if (proposerRef != null) {
                proposerRef.sendMessage(Message.raw("[Marriage] " + resolvePlayerName(senderUuid) + " accepted! Find a Priest to officiate.").color("#66ff66"));
            }
        }
    }

    private void handleDeny() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        UUID proposer = data.getProposer(senderUuid);
        if (proposer == null) {
            playerRef.sendMessage(Message.raw("[Marriage] No pending proposals.").color("#ff6666"));
            return;
        }

        data.removeProposal(proposer);
        playerRef.sendMessage(Message.raw("[Marriage] Proposal denied.").color("#ff9900"));
        PlayerRef proposerRef = Universe.get().getPlayer(proposer);
        if (proposerRef != null) {
            proposerRef.sendMessage(Message.raw("[Marriage] " + resolvePlayerName(senderUuid) + " denied your proposal.").color("#ff6666"));
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
            playerRef.sendMessage(Message.raw("[Marriage] You are not married.").color("#ff6666"));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        if (spouseUuid == null) {
            return;
        }

        WeddingRingTier current = data.getRing(senderUuid);
        WeddingRingTier next = current != null ? current.next() : WeddingRingTier.lowest();
        if (next == null) {
            playerRef.sendMessage(Message.raw("[Marriage] You already have the highest tier ring.").color("#ff9900"));
            return;
        }

        int senderPrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(senderUuid);
        int spousePrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(spouseUuid);
        int lowestPrestige = Math.min(senderPrestige, spousePrestige);

        if (lowestPrestige < next.getPrestigeRequired()) {
            playerRef.sendMessage(Message.raw("[Marriage] Both partners need prestige "
                    + next.getPrestigeRequired() + " for this upgrade.").color("#ff6666"));
            return;
        }

        // Placeholder: economy check would go here
        // if (next.getCost() > 0 && !hasBalance(senderUuid, next.getCost())) { ... }

        data.setRing(senderUuid, spouseUuid, next);
        playerRef.sendMessage(Message.raw("[Marriage] Ring upgraded to " + next.getDisplayName() + "!").color("#66ff66"));

        PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
        if (spouseRef != null && spouseRef.isValid()) {
            spouseRef.sendMessage(Message.raw("[Marriage] " + resolvePlayerName(senderUuid)
                    + " upgraded your wedding ring to " + next.getDisplayName() + "!").color("#4fd7f7"));
        }
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

    private void handleStatus() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            playerRef.sendMessage(Message.raw("[Marriage] You are not currently married.").color("#4fd7f7"));
            return;
        }

        MarriagePair pair = data.getMarriage(senderUuid);
        UUID spouseUuid = pair.getSpouse(senderUuid);
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(pair.timestamp()));

        playerRef.sendMessage(Message.raw("[Marriage] Married to: " + resolvePlayerName(spouseUuid)).color("#66ff66"));
        playerRef.sendMessage(Message.raw("[Marriage] Since: " + date).color("#4fd7f7"));
        if (pair.officiant() != null) {
            playerRef.sendMessage(Message.raw("[Marriage] Officiated by: " + resolvePlayerName(pair.officiant())).color("#4fd7f7"));
        }
    }

    private void handleRecords() {
        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
        var records = data.getRecordsForOfficiant(senderUuid);

        if (records.isEmpty()) {
            playerRef.sendMessage(Message.raw("[Marriage] You have no officiant records.").color("#4fd7f7"));
            return;
        }

        playerRef.sendMessage(Message.raw("[Marriage] Your officiant records (" + records.size() + "):").color("#4fd7f7"));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (var record : records) {
            String typeName = record.type() == com.airijko.endlessleveling.endlessmarriage.data.OfficiantRecord.OfficiantType.MARRIAGE
                    ? "Marriage" : "Divorce";
            String dateStr = fmt.format(new Date(record.timestamp()));
            playerRef.sendMessage(Message.raw("  " + typeName + ": "
                    + resolvePlayerName(record.player1()) + " & "
                    + resolvePlayerName(record.player2()) + " (" + dateStr + ")").color("#4fd7f7"));
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
        String fallback = uuid.toString().substring(0, 8);
        return fallback != null ? fallback : "";
    }
}
