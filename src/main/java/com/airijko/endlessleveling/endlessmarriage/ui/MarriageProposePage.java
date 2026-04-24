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
 * UI page listing online, unmarried players for marriage proposals.
 */
public class MarriageProposePage extends SafeInteractiveCustomUIPage<MarriagePageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String PLAYER_ROW_TEMPLATE = "Pages/Marriage/PlayerRow.ui";

    private final PlayerRef playerRef;

    public MarriageProposePage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, MarriagePageData.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Marriage/MarriageProposePage.ui");
        buildPlayerList(ui, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull MarriagePageData data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null || data.action.isBlank()) {
            return;
        }

        if (data.action.startsWith("marry:propose:")) {
            String targetUuidStr = data.action.substring("marry:propose:".length());
            handlePropose(targetUuidStr);
        }
    }

    private void buildPlayerList(@Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events) {
        ui.clear("#PlayerRows");

        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        List<PlayerRef> eligible = new ArrayList<>();
        for (PlayerRef ref : Universe.get().getPlayers()) {
            if (ref == null || !ref.isValid()) {
                continue;
            }
            UUID uuid = ref.getUuid();
            if (uuid.equals(senderUuid)) {
                continue;
            }
            if (data.isMarried(uuid)) {
                continue;
            }
            eligible.add(ref);
        }

        ui.set("#PlayerCountLabel.Text", eligible.size() + (eligible.size() == 1 ? " player" : " players"));

        for (int i = 0; i < eligible.size(); i++) {
            PlayerRef target = eligible.get(i);
            ui.append("#PlayerRows", PLAYER_ROW_TEMPLATE);
            String base = "#PlayerRows[" + i + "]";

            String name = target.getUsername() != null ? target.getUsername() : target.getUuid().toString().substring(0, 8);
            ui.set(base + " #PlayerName.Text", name);
            ui.set(base + " #ActionButton.Text", "PROPOSE");

            events.addEventBinding(Activating,
                    base + " #ActionButton",
                    of("Action", "marry:propose:" + target.getUuid()),
                    false);
        }

        if (eligible.isEmpty()) {
            ui.set("#InstructionLabel.Text", "No eligible players are online right now.");
        }
    }

    private void handlePropose(@Nonnull String targetUuidStr) {
        try {
            UUID targetUuid = UUID.fromString(targetUuidStr);
            UUID senderUuid = playerRef.getUuid();
            MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

            if (data.isMarried(senderUuid)) {
                playerRef.sendMessage(Message.raw("[Marriage] You are already married!").color("#ff6666"));
                return;
            }

            if (data.hasPendingProposal(senderUuid)) {
                playerRef.sendMessage(Message.raw("[Marriage] You already have a pending proposal.").color("#ff6666"));
                return;
            }

            if (data.isMarried(targetUuid)) {
                playerRef.sendMessage(Message.raw("[Marriage] That player is already married.").color("#ff6666"));
                return;
            }

            PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
            if (targetRef == null || !targetRef.isValid()) {
                playerRef.sendMessage(Message.raw("[Marriage] That player is no longer online.").color("#ff6666"));
                return;
            }

            String targetName = targetRef.getUsername() != null ? targetRef.getUsername() : targetUuidStr.substring(0, 8);
            String senderName = playerRef.getUsername() != null ? playerRef.getUsername() : senderUuid.toString().substring(0, 8);

            data.addProposal(senderUuid, targetUuid);
            playerRef.sendMessage(Message.raw("[Marriage] You proposed to " + targetName + "!").color("#66ff66"));
            targetRef.sendMessage(Message.raw("[Marriage] " + senderName + " has proposed to you! Use /marry accept or /marry deny").color("#4fd7f7"));

        } catch (IllegalArgumentException ex) {
            playerRef.sendMessage(Message.raw("[Marriage] Invalid player.").color("#ff6666"));
        }
    }
}
