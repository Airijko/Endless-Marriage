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
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.data.WeddingRingTier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * UI page for browsing and equipping wedding rings.
 * Rings are tiered E-S and require both partners to meet a prestige threshold.
 */
public class MarriageRingPage extends InteractiveCustomUIPage<MarriagePageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String RING_ROW_TEMPLATE = "Pages/Marriage/RingRow.ui";

    private final PlayerRef playerRef;

    public MarriageRingPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, MarriagePageData.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Marriage/MarriageRingPage.ui");

        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();

        if (!data.isMarried(senderUuid)) {
            ui.set("#RingInfoLabel.Text", "You must be married to browse rings.");
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        WeddingRingTier currentRing = data.getRing(senderUuid);

        int senderPrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(senderUuid);
        int spousePrestige = spouseUuid != null
                ? EndlessLevelingAPI.get().getPlayerPrestigeLevel(spouseUuid)
                : 0;
        int lowestPrestige = Math.min(senderPrestige, spousePrestige);

        // Show current ring
        if (currentRing != null) {
            ui.set("#CurrentRingLabel.Text", "Equipped: " + currentRing.getDisplayName());
            ui.set("#CurrentRingLabel.Style.TextColor", currentRing.getColor());
        } else {
            ui.set("#CurrentRingLabel.Text", "No ring equipped");
        }

        ui.set("#PrestigeInfoLabel.Text",
                "Your prestige: " + senderPrestige + " | Spouse prestige: " + spousePrestige);

        // Build ring rows
        ui.clear("#RingRows");

        WeddingRingTier[] tiers = WeddingRingTier.values();
        for (int i = 0; i < tiers.length; i++) {
            WeddingRingTier tier = tiers[i];
            ui.append("#RingRows", RING_ROW_TEMPLATE);
            String base = "#RingRows[" + i + "]";

            ui.set(base + " #RingRowIcon.ItemId", tier.getIconItemId());
            ui.set(base + " #RingName.Text", tier.getDisplayName() + " Ring");
            ui.set(base + " #RingName.Style.TextColor", tier.getColor());
            ui.set(base + " #RingDesc.Text", tier.getDescription());

            boolean isEquipped = tier == currentRing;
            boolean meetsPrestige = lowestPrestige >= tier.getPrestigeRequired();

            if (tier.getPrestigeRequired() > 0) {
                ui.set(base + " #PrestigeReq.Text", "Prestige " + tier.getPrestigeRequired());
                ui.set(base + " #PrestigeReq.Style.TextColor", meetsPrestige ? "#66ff66" : "#ff6666");
                ui.set(base + " #PrestigeReq.Visible", true);
            } else {
                ui.set(base + " #PrestigeReq.Visible", false);
            }

            // Cost placeholder
            if (tier.getCost() > 0) {
                ui.set(base + " #CostLabel.Text", "Cost: " + tier.getCost());
                ui.set(base + " #CostLabel.Visible", true);
            } else {
                ui.set(base + " #CostLabel.Text", "Free");
                ui.set(base + " #CostLabel.Style.TextColor", "#8be0b2");
                ui.set(base + " #CostLabel.Visible", true);
            }

            if (isEquipped) {
                ui.set(base + " #ActionButton.Text", "EQUIPPED");
                ui.set(base + " #ActionButton.Disabled", true);
            } else if (!meetsPrestige) {
                ui.set(base + " #ActionButton.Text", "LOCKED");
                ui.set(base + " #ActionButton.Disabled", true);
            } else {
                ui.set(base + " #ActionButton.Text", "EQUIP");
                events.addEventBinding(Activating,
                        base + " #ActionButton",
                        of("Action", "marry:ring:" + tier.name()),
                        false);
            }
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

        if (data.action.startsWith("marry:ring:")) {
            String tierName = data.action.substring("marry:ring:".length());
            handleEquipRing(tierName);
        }
    }

    private void handleEquipRing(@Nonnull String tierName) {
        WeddingRingTier tier = WeddingRingTier.fromName(tierName);
        if (tier == null) {
            playerRef.sendMessage(Message.raw("[Marriage] Invalid ring tier.").color("#ff6666"));
            return;
        }

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

        int senderPrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(senderUuid);
        int spousePrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(spouseUuid);
        int lowestPrestige = Math.min(senderPrestige, spousePrestige);

        if (lowestPrestige < tier.getPrestigeRequired()) {
            playerRef.sendMessage(Message.raw("[Marriage] Both partners need prestige "
                    + tier.getPrestigeRequired() + " to equip this ring.").color("#ff6666"));
            return;
        }

        // Placeholder: economy check would go here
        // if (tier.getCost() > 0 && !hasBalance(senderUuid, tier.getCost())) { ... }

        data.setRing(senderUuid, spouseUuid, tier);
        playerRef.sendMessage(Message.raw("[Marriage] Equipped " + tier.getDisplayName() + " ring!").color("#66ff66"));

        PlayerRef spouseRef = com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(spouseUuid);
        if (spouseRef != null && spouseRef.isValid()) {
            String senderName = playerRef.getUsername() != null
                    ? playerRef.getUsername()
                    : senderUuid.toString().substring(0, 8);
            spouseRef.sendMessage(Message.raw("[Marriage] " + senderName
                    + " equipped a " + tier.getDisplayName() + " wedding ring!").color("#4fd7f7"));
        }
    }
}
