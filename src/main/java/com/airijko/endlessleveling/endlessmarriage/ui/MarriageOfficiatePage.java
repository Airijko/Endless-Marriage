/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.ui;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.endlessmarriage.EndlessMarriage;
import com.airijko.endlessleveling.endlessmarriage.MarriageAnnouncer;
import com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageMessages;
import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.services.WitnessCollector;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.airijko.endlessleveling.ui.base.SafeInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Officiate panel:
 * - Priests see their marriage inbox (requests from couples). Must be within 5 blocks to approve.
 * - Magistrates see all pending divorces globally. No proximity required.
 */
public class MarriageOfficiatePage extends SafeInteractiveCustomUIPage<MarriagePageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String PLAYER_ROW_TEMPLATE = "Pages/Marriage/PlayerRow.ui";

    private final PlayerRef playerRef;

    public MarriageOfficiatePage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, MarriagePageData.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Marriage/MarriageOfficiatePage.ui");

        UUID senderUuid = playerRef.getUuid();
        MarriageConfig config = EndlessMarriage.getInstance().getMarriageConfig();

        String primaryClass = EndlessLevelingAPI.get().getPrimaryClassId(senderUuid);
        String secondaryClass = EndlessLevelingAPI.get().getSecondaryClassId(senderUuid);
        boolean isPriest = config.getPriestClassId().equalsIgnoreCase(primaryClass)
                || config.getPriestClassId().equalsIgnoreCase(secondaryClass);
        boolean isMagistrate = config.getMagistrateClassId().equalsIgnoreCase(primaryClass)
                || config.getMagistrateClassId().equalsIgnoreCase(secondaryClass);

        if (!isPriest && !isMagistrate) {
            ui.set("#NoRoleLabel.Visible", true);
        } else {
            ui.set("#NoRoleLabel.Visible", false);
        }

        buildPriestInbox(ui, events, senderUuid, isPriest);
        buildPendingDivorces(ui, events, isMagistrate);
    }

    /**
     * Priest section: shows this priest's inbox (couples who requested them).
     */
    private void buildPriestInbox(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
            @Nonnull UUID priestUuid, boolean isPriest) {
        ui.clear("#MarriageRows");

        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
        List<UUID[]> inbox = isPriest ? data.getPriestInbox(priestUuid) : Collections.emptyList();

        ui.set("#MarriageCountLabel.Text", inbox.size() + " pending");

        for (int i = 0; i < inbox.size(); i++) {
            UUID[] pair = inbox.get(i);
            ui.append("#MarriageRows", PLAYER_ROW_TEMPLATE);
            String base = "#MarriageRows[" + i + "]";

            String name1 = resolvePlayerName(pair[0]);
            String name2 = resolvePlayerName(pair[1]);
            ui.set(base + " #PlayerName.Text", name1 + " & " + name2);
            ui.set(base + " #ActionButton.Text", "OFFICIATE");

            // Button always enabled -- proximity is checked on click
            events.addEventBinding(Activating,
                    base + " #ActionButton",
                    of("Action", "marry:do_officiate:" + pair[0] + ":" + pair[1]),
                    false);
        }
    }

    /**
     * Magistrate section: shows ALL pending divorces globally.
     */
    private void buildPendingDivorces(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, boolean isMagistrate) {
        ui.clear("#DivorceRows");

        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        List<UUID> pendingDivorces = new ArrayList<>();
        for (PlayerRef ref : Universe.get().getPlayers()) {
            if (ref == null || !ref.isValid()) continue;
            if (data.hasPendingDivorce(ref.getUuid())) {
                pendingDivorces.add(ref.getUuid());
            }
        }

        ui.set("#DivorceCountLabel.Text", pendingDivorces.size() + " pending");

        for (int i = 0; i < pendingDivorces.size(); i++) {
            UUID requester = pendingDivorces.get(i);
            UUID spouse = data.getSpouse(requester);
            ui.append("#DivorceRows", PLAYER_ROW_TEMPLATE);
            String base = "#DivorceRows[" + i + "]";

            String name = resolvePlayerName(requester);
            String spouseName = spouse != null ? resolvePlayerName(spouse) : "?";
            ui.set(base + " #PlayerName.Text", name + " & " + spouseName);
            ui.set(base + " #ActionButton.Text", "GRANT");
            ui.set(base + " #ActionButton.Disabled", !isMagistrate);

            events.addEventBinding(Activating,
                    base + " #ActionButton",
                    of("Action", "marry:do_grant_divorce:" + requester),
                    false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull MarriagePageData data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null || data.action.isBlank()) return;

        if (data.action.startsWith("marry:do_officiate:")) {
            String[] parts = data.action.substring("marry:do_officiate:".length()).split(":");
            if (parts.length == 2) {
                handleOfficiate(parts[0], parts[1], ref, store);
            }
        } else if (data.action.startsWith("marry:do_grant_divorce:")) {
            String requesterStr = data.action.substring("marry:do_grant_divorce:".length());
            handleGrantDivorce(requesterStr);
        }
    }

    private void handleOfficiate(@Nonnull String p1Str, @Nonnull String p2Str,
            @Nonnull Ref<EntityStore> priestRef, @Nonnull Store<EntityStore> priestStore) {
        try {
            UUID p1 = UUID.fromString(p1Str);
            UUID p2 = UUID.fromString(p2Str);
            UUID priestUuid = playerRef.getUuid();
            MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
            MarriageConfig config = EndlessMarriage.getInstance().getMarriageConfig();

            if (!data.hasPendingMarriage(p1) || !data.hasPendingMarriage(p2)) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.MARRIAGE_NOT_PENDING, "#ff6666"));
                return;
            }

            // --- 5-block proximity check ---
            double range = config.getOfficiateRange();
            double rangeSq = range * range;

            Vector3d priestPos = resolvePosition(priestRef, priestStore);
            if (priestPos == null) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.CANNOT_DETERMINE_POSITION, "#ff6666"));
                return;
            }

            PlayerRef ref1 = Universe.get().getPlayer(p1);
            PlayerRef ref2 = Universe.get().getPlayer(p2);
            if (ref1 == null || !ref1.isValid() || ref2 == null || !ref2.isValid()) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.BOTH_MUST_BE_ONLINE_OFFICIATE, "#ff6666"));
                return;
            }

            Ref<EntityStore> ent1 = ref1.getReference();
            Ref<EntityStore> ent2 = ref2.getReference();
            if (ent1 == null || ent2 == null) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.BOTH_MUST_BE_IN_WORLD, "#ff6666"));
                return;
            }

            // Check same world
            Store<EntityStore> store1 = ent1.getStore();
            Store<EntityStore> store2 = ent2.getStore();
            if (store1 != priestStore || store2 != priestStore) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.SAME_WORLD_REQUIRED, "#ff6666"));
                return;
            }

            Vector3d pos1 = resolvePosition(ent1, store1);
            Vector3d pos2 = resolvePosition(ent2, store2);
            if (pos1 == null || pos2 == null) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.CANNOT_LOCATE_BOTH, "#ff6666"));
                return;
            }

            if (distanceSq(priestPos, pos1) > rangeSq || distanceSq(priestPos, pos2) > rangeSq) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.OFFICIATE_TOO_FAR, "#ff6666",
                        (int) range));
                return;
            }

            // Collect witnesses (online players in the same world within the witness
            // radius of the priest, excluding the priest and the two newlyweds).
            Set<UUID> excluded = new HashSet<>();
            excluded.add(priestUuid);
            excluded.add(p1);
            excluded.add(p2);
            List<UUID> witnesses = WitnessCollector.collect(priestStore, priestPos,
                    config.getWitnessMaxRange(), excluded);

            // --- All checks passed, marry them ---
            data.marry(p1, p2, priestUuid, witnesses);
            // marry() already calls clearPriestRequestsForCouple

            String name1 = resolvePlayerName(p1);
            String name2 = resolvePlayerName(p2);
            String priestName = resolvePlayerName(priestUuid);

            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.OFFICIATED_OF, "#66ff66", name1, name2));

            if (ref1.isValid()) {
                ref1.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOW_MARRIED_OFFICIATED_BY, "#66ff66", priestName));
            }
            if (ref2.isValid()) {
                ref2.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOW_MARRIED_OFFICIATED_BY, "#66ff66", priestName));
            }

            // Global wedding announcement: title, chat broadcast, wedding march SFX
            MarriageAnnouncer.announceMarriage(name1, name2, priestName);
        } catch (IllegalArgumentException ex) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.INVALID_PLAYERS, "#ff6666"));
        }
    }

    private void handleGrantDivorce(@Nonnull String requesterStr) {
        try {
            UUID requester = UUID.fromString(requesterStr);
            UUID senderUuid = playerRef.getUuid();
            MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

            if (!data.hasPendingDivorce(requester)) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.DIVORCE_NOT_PENDING, "#ff6666"));
                return;
            }

            UUID spouse = data.getSpouse(requester);
            if (spouse == null) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.PLAYER_NOT_MARRIED, "#ff6666"));
                data.removePendingDivorce(requester);
                return;
            }

            data.divorce(requester, spouse, senderUuid);

            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.DIVORCE_GRANTED_FOR_BOTH, "#66ff66",
                    resolvePlayerName(requester), resolvePlayerName(spouse)));

            PlayerRef reqRef = Universe.get().getPlayer(requester);
            PlayerRef spRef = Universe.get().getPlayer(spouse);
            if (reqRef != null) {
                reqRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.YOUR_DIVORCE_GRANTED, "#ff9900"));
            }
            if (spRef != null) {
                spRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.YOUR_DIVORCE_GRANTED, "#ff9900"));
            }
        } catch (IllegalArgumentException ex) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.INVALID_PLAYER, "#ff6666"));
        }
    }

    private double distanceSq(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    @Nullable
    private Vector3d resolvePosition(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (ref == null || store == null) return null;
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
        var snapshot = EndlessLevelingAPI.get().getPlayerSnapshot(uuid);
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
