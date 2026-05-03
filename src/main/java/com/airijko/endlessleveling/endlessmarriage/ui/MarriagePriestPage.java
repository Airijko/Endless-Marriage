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
import com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageMessages;
import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.airijko.endlessleveling.ui.base.SafeInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * UI page listing all online priests for marriage officiation requests.
 * The request is stored in the priest's inbox and persists even if the priest logs off.
 */
public class MarriagePriestPage extends SafeInteractiveCustomUIPage<MarriagePageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String PLAYER_ROW_TEMPLATE = "Pages/Marriage/PlayerRow.ui";

    private final PlayerRef playerRef;

    public MarriagePriestPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, MarriagePageData.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Marriage/MarriagePriestPage.ui");
        buildPriestList(ui, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull MarriagePageData data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null || data.action.isBlank()) {
            return;
        }

        if (data.action.startsWith("marry:request_priest:")) {
            String priestUuidStr = data.action.substring("marry:request_priest:".length());
            handleRequestPriest(priestUuidStr);
        }
    }

    private void buildPriestList(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events) {
        ui.clear("#PriestRows");

        UUID senderUuid = playerRef.getUuid();
        MarriageConfig config = EndlessMarriage.getInstance().getMarriageConfig();
        String priestClassId = config.getPriestClassId();

        // Show ALL online priests -- no proximity filter
        List<PlayerRef> priests = new ArrayList<>();
        for (PlayerRef ref : Universe.get().getPlayers()) {
            if (ref == null || !ref.isValid()) continue;
            UUID uuid = ref.getUuid();
            if (uuid.equals(senderUuid)) continue;

            String primaryClass = EndlessLevelingAPI.get().getPrimaryClassId(uuid);
            String secondaryClass = EndlessLevelingAPI.get().getSecondaryClassId(uuid);
            if (priestClassId.equalsIgnoreCase(primaryClass) || priestClassId.equalsIgnoreCase(secondaryClass)) {
                priests.add(ref);
            }
        }

        ui.set("#PriestCountLabel.Text", priests.size() + (priests.size() == 1 ? " priest" : " priests"));

        for (int i = 0; i < priests.size(); i++) {
            PlayerRef priest = priests.get(i);
            ui.append("#PriestRows", PLAYER_ROW_TEMPLATE);
            String base = "#PriestRows[" + i + "]";

            String name = priest.getUsername() != null ? priest.getUsername()
                    : priest.getUuid().toString().substring(0, 8);
            ui.set(base + " #PlayerName.Text", name);
            ui.set(base + " #PlayerStatus.Visible", true);
            ui.set(base + " #PlayerStatus.Text", "PRIEST");
            ui.set(base + " #ActionButton.Text", "REQUEST");

            events.addEventBinding(Activating,
                    base + " #ActionButton",
                    of("Action", "marry:request_priest:" + priest.getUuid()),
                    false);
        }

        ui.set("#NoPriestsLabel.Visible", priests.isEmpty());
        if (priests.isEmpty()) {
            ui.set("#PriestInstructionLabel.Text", "No priests are online. Your request will be sent when one is available.");
        }
    }

    private void handleRequestPriest(@Nonnull String priestUuidStr) {
        try {
            UUID priestUuid = UUID.fromString(priestUuidStr);
            UUID senderUuid = playerRef.getUuid();
            MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

            if (!data.hasPendingMarriage(senderUuid)) {
                playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NO_PENDING_MARRIAGE_SHORT, "#ff6666"));
                return;
            }

            UUID[] pendingPair = data.getPendingMarriage(senderUuid);
            if (pendingPair == null) return;

            // Store in priest's inbox (persists to disk)
            data.addPriestRequest(priestUuid, pendingPair[0], pendingPair[1]);

            UUID partnerId = pendingPair[0].equals(senderUuid) ? pendingPair[1] : pendingPair[0];
            String partnerName = resolvePlayerName(partnerId);
            String priestName = resolvePlayerName(priestUuid);

            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.OFFICIATION_REQUEST_SENT, "#66ff66", priestName));

            // Notify priest if online
            PlayerRef priestRef = Universe.get().getPlayer(priestUuid);
            if (priestRef != null && priestRef.isValid()) {
                String senderName = playerRef.getUsername() != null ? playerRef.getUsername()
                        : senderUuid.toString().substring(0, 8);
                priestRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.OFFICIATION_REQUEST_RECEIVED, "#4fd7f7",
                        senderName, partnerName));
            }
        } catch (IllegalArgumentException ex) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.INVALID_PRIEST, "#ff6666"));
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
