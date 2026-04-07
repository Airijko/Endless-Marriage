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
import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Officiate panel:
 * - Priests see their marriage inbox (requests from couples). Must be within 5 blocks to approve.
 * - Magistrates see all pending divorces globally. No proximity required.
 */
public class MarriageOfficiatePage extends InteractiveCustomUIPage<MarriagePageData> {

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
            ui.set(base + " #ActionButton.Enabled", isMagistrate);

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
                playerRef.sendMessage(Message.raw("[Marriage] This marriage is no longer pending.").color("#ff6666"));
                return;
            }

            // --- 5-block proximity check ---
            double range = config.getOfficiateRange();
            double rangeSq = range * range;

            Vector3d priestPos = resolvePosition(priestRef, priestStore);
            if (priestPos == null) {
                playerRef.sendMessage(Message.raw("[Marriage] Could not determine your position.").color("#ff6666"));
                return;
            }

            PlayerRef ref1 = Universe.get().getPlayer(p1);
            PlayerRef ref2 = Universe.get().getPlayer(p2);
            if (ref1 == null || !ref1.isValid() || ref2 == null || !ref2.isValid()) {
                playerRef.sendMessage(Message.raw("[Marriage] Both players must be online to officiate.").color("#ff6666"));
                return;
            }

            Ref<EntityStore> ent1 = ref1.getReference();
            Ref<EntityStore> ent2 = ref2.getReference();
            if (ent1 == null || ent2 == null) {
                playerRef.sendMessage(Message.raw("[Marriage] Both players must be in a world.").color("#ff6666"));
                return;
            }

            // Check same world
            Store<EntityStore> store1 = ent1.getStore();
            Store<EntityStore> store2 = ent2.getStore();
            if (store1 != priestStore || store2 != priestStore) {
                playerRef.sendMessage(Message.raw("[Marriage] You must be in the same world as both players.").color("#ff6666"));
                return;
            }

            Vector3d pos1 = resolvePosition(ent1, store1);
            Vector3d pos2 = resolvePosition(ent2, store2);
            if (pos1 == null || pos2 == null) {
                playerRef.sendMessage(Message.raw("[Marriage] Could not locate both players.").color("#ff6666"));
                return;
            }

            if (distanceSq(priestPos, pos1) > rangeSq || distanceSq(priestPos, pos2) > rangeSq) {
                playerRef.sendMessage(Message.raw("[Marriage] You must be within " + (int) range
                        + " blocks of both players to officiate.").color("#ff6666"));
                return;
            }

            // --- All checks passed, marry them ---
            data.marry(p1, p2, priestUuid);
            // marry() already calls clearPriestRequestsForCouple

            playerRef.sendMessage(Message.raw("[Marriage] You officiated the marriage of "
                    + resolvePlayerName(p1) + " & " + resolvePlayerName(p2) + "!").color("#66ff66"));

            if (ref1.isValid()) {
                ref1.sendMessage(Message.raw("[Marriage] You are now married! Officiated by "
                        + resolvePlayerName(priestUuid)).color("#66ff66"));
            }
            if (ref2.isValid()) {
                ref2.sendMessage(Message.raw("[Marriage] You are now married! Officiated by "
                        + resolvePlayerName(priestUuid)).color("#66ff66"));
            }
        } catch (IllegalArgumentException ex) {
            playerRef.sendMessage(Message.raw("[Marriage] Invalid players.").color("#ff6666"));
        }
    }

    private void handleGrantDivorce(@Nonnull String requesterStr) {
        try {
            UUID requester = UUID.fromString(requesterStr);
            UUID senderUuid = playerRef.getUuid();
            MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

            if (!data.hasPendingDivorce(requester)) {
                playerRef.sendMessage(Message.raw("[Marriage] This divorce is no longer pending.").color("#ff6666"));
                return;
            }

            UUID spouse = data.getSpouse(requester);
            if (spouse == null) {
                playerRef.sendMessage(Message.raw("[Marriage] This player is not married.").color("#ff6666"));
                data.removePendingDivorce(requester);
                return;
            }

            data.divorce(requester, spouse, senderUuid);

            playerRef.sendMessage(Message.raw("[Marriage] Divorce granted for "
                    + resolvePlayerName(requester) + " & " + resolvePlayerName(spouse) + ".").color("#66ff66"));

            PlayerRef reqRef = Universe.get().getPlayer(requester);
            PlayerRef spRef = Universe.get().getPlayer(spouse);
            if (reqRef != null) {
                reqRef.sendMessage(Message.raw("[Marriage] Your divorce has been granted.").color("#ff9900"));
            }
            if (spRef != null) {
                spRef.sendMessage(Message.raw("[Marriage] Your divorce has been granted.").color("#ff9900"));
            }
        } catch (IllegalArgumentException ex) {
            playerRef.sendMessage(Message.raw("[Marriage] Invalid player.").color("#ff6666"));
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

    private String resolvePlayerName(@Nonnull UUID uuid) {
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref != null && ref.getUsername() != null) {
            return ref.getUsername();
        }
        var snapshot = EndlessLevelingAPI.get().getPlayerSnapshot(uuid);
        if (snapshot != null && snapshot.playerName() != null) {
            return snapshot.playerName();
        }
        return uuid.toString().substring(0, 8);
    }
}
