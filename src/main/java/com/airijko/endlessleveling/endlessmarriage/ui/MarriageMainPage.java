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
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageHome;
import com.airijko.endlessleveling.endlessmarriage.data.MarriagePair;
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

    private final PlayerRef playerRef;
    private final Ref<EntityStore> entityRef;
    private final Store<EntityStore> entityStore;

    public MarriageMainPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull Store<EntityStore> entityStore) {
        super(playerRef, lifetime, MarriagePageData.CODEC);
        this.playerRef = playerRef;
        this.entityRef = entityRef;
        this.entityStore = entityStore;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Marriage/MarriageMainPage.ui");

        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (data.isMarried(senderUuid)) {
            buildMarriedView(ui, events, senderUuid, data);
        } else {
            buildUnmarriedView(ui, events, senderUuid, data);
        }
    }

    private void buildMarriedView(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
            @Nonnull UUID senderUuid, @Nonnull MarriageDataManager data) {

        MarriagePair pair = data.getMarriage(senderUuid);
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

        // Button actions
        events.addEventBinding(Activating, "#TpPartnerButton", of("Action", "marry:tp"), false);
        events.addEventBinding(Activating, "#HomeButton", of("Action", "marry:home"), false);
        events.addEventBinding(Activating, "#SetHomeButton", of("Action", "marry:sethome"), false);
        events.addEventBinding(Activating, "#InventoryButton", of("Action", "marry:inventory"), false);
        events.addEventBinding(Activating, "#DivorceButton", of("Action", "marry:divorce"), false);
        events.addEventBinding(Activating, "#StatusButton", of("Action", "marry:status"), false);

        // Disable buttons if spouse is offline
        if (!spouseOnline) {
            ui.set("#TpPartnerButton.Enabled", false);
            ui.set("#InventoryButton.Enabled", false);
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
    }

    private void buildUnmarriedView(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
            @Nonnull UUID senderUuid, @Nonnull MarriageDataManager data) {

        // Show unmarried panel, hide married panel
        ui.set("#MarriedPanel.Visible", false);
        ui.set("#UnmarriedPanel.Visible", true);

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
            playerRef.sendMessage(Message.raw("[Marriage] You are now married to " + resolvePlayerName(proposer) + "!").color("#66ff66"));
            PlayerRef proposerRef = Universe.get().getPlayer(proposer);
            if (proposerRef != null) {
                proposerRef.sendMessage(Message.raw("[Marriage] " + resolvePlayerName(senderUuid) + " accepted! You are now married!").color("#66ff66"));
            }
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

    private String resolvePlayerName(@Nonnull UUID uuid) {
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref != null && ref.getUsername() != null) {
            return ref.getUsername();
        }
        var snapshot = com.airijko.endlessleveling.api.EndlessLevelingAPI.get().getPlayerSnapshot(uuid);
        if (snapshot != null && snapshot.playerName() != null) {
            return snapshot.playerName();
        }
        return uuid.toString().substring(0, 8);
    }
}
