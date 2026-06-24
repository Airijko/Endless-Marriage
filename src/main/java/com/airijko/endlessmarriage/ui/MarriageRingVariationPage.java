/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.ui;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessmarriage.EndlessMarriage;
import com.airijko.endlessmarriage.commands.subcommands.MarriageMessages;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.TieredRingDataManager;
import com.airijko.endlessmarriage.data.tiered.TieredRingCatalog;
import com.airijko.endlessmarriage.data.tiered.TieredRingDefinition;
import com.airijko.endlessmarriage.data.tiered.TieredRingTier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.airijko.endlessleveling.ui.base.SafeInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Per-tier variation picker reached from {@link MarriageRingPage}. Lists the ten
 * attribute variations of a single {@link TieredRingTier} (Strength, Defense,
 * Life Force, ...) and equips the chosen one via the shared
 * {@link TieredRingDataManager}, which applies the attribute bonus.
 *
 * Marriage- and prestige-gated like the tier-selection screen it descends from.
 */
public class MarriageRingVariationPage extends SafeInteractiveCustomUIPage<MarriagePageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String RING_ROW_TEMPLATE = "Pages/Marriage/TieredRingRow.ui";
    private static final String EQUIP_PREFIX = "marry:variation:equip:";
    private static final String UNEQUIP_PREFIX = "marry:variation:unequip:";
    private static final String BACK_ACTION = "marry:variation:back";

    private final PlayerRef playerRef;
    private final TieredRingTier tier;

    public MarriageRingVariationPage(@Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull TieredRingTier tier) {
        super(playerRef, lifetime, MarriagePageData.CODEC);
        this.playerRef = playerRef;
        this.tier = tier;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Marriage/MarriageRingVariationPage.ui");

        ui.set("#VariationTierLabel.Text", tier.getDisplayName() + " TIER RINGS");
        ui.set("#VariationTierLabel.Style.TextColor", tier.getColor());

        events.addEventBinding(Activating, "#VariationBackButton", of("Action", BACK_ACTION), false);

        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager marriage = EndlessMarriage.getInstance().getMarriageDataManager();
        if (!marriage.isMarried(senderUuid)) {
            ui.set("#VariationInfoLabel.Text", "You must be married to browse rings.");
            return;
        }

        UUID spouseUuid = marriage.getSpouse(senderUuid);
        // Highest prestige per partner, persistent across offline/profile switches — matches the
        // tier-list gate in MarriageRingPage so an offline spouse doesn't re-lock every variation.
        int senderPrestige = EndlessLevelingAPI.get().getHighestPrestigeLevel(senderUuid);
        int spousePrestige = spouseUuid != null
                ? EndlessLevelingAPI.get().getHighestPrestigeLevel(spouseUuid)
                : 0;
        int lowestPrestige = Math.min(senderPrestige, spousePrestige);
        boolean meetsPrestige = lowestPrestige >= tier.getPrestigeRequired();

        TieredRingDataManager rings = EndlessMarriage.getInstance().getTieredRingDataManager();
        TieredRingDefinition equipped = rings != null ? rings.getEquippedRing(senderUuid) : null;
        if (equipped != null) {
            ui.set("#VariationCurrentRingLabel.Text", "Equipped: " + equipped.displayName());
            ui.set("#VariationCurrentRingLabel.Style.TextColor", equipped.color());
        } else {
            ui.set("#VariationCurrentRingLabel.Text", "No ring equipped");
            ui.set("#VariationCurrentRingLabel.Style.TextColor", "#c0cee5");
        }

        if (!meetsPrestige) {
            ui.set("#VariationInfoLabel.Text",
                    "Both partners need prestige " + tier.getPrestigeRequired() + " to equip this tier.");
        } else {
            ui.set("#VariationInfoLabel.Text", "Each ring grants an attribute bonus while equipped.");
        }

        ui.clear("#VariationRingRows");

        List<TieredRingDefinition> variations = TieredRingCatalog.byTier(tier);
        for (int i = 0; i < variations.size(); i++) {
            TieredRingDefinition def = variations.get(i);
            ui.append("#VariationRingRows", RING_ROW_TEMPLATE);
            String base = "#VariationRingRows[" + i + "]";

            ui.set(base + " #TieredRingRowIcon.ItemId", def.iconItemId());
            ui.set(base + " #TieredRingName.Text", def.displayName());
            ui.set(base + " #TieredRingName.Style.TextColor", def.color());
            ui.set(base + " #TieredRingDesc.Text", def.description());
            ui.set(base + " #TieredRingTierLabel.Text", "TIER " + tier.getDisplayName());
            ui.set(base + " #TieredRingTierLabel.Style.TextColor", tier.getColor());

            boolean isEquipped = equipped != null && equipped.id().equals(def.id());

            if (isEquipped) {
                ui.set(base + " #TieredRingActionButton.Text", "UNEQUIP");
                events.addEventBinding(Activating,
                        base + " #TieredRingActionButton",
                        of("Action", UNEQUIP_PREFIX + def.id()),
                        false);
            } else if (!meetsPrestige) {
                ui.set(base + " #TieredRingActionButton.Text", "LOCKED");
                ui.set(base + " #TieredRingActionButton.Disabled", true);
            } else {
                ui.set(base + " #TieredRingActionButton.Text", "EQUIP");
                events.addEventBinding(Activating,
                        base + " #TieredRingActionButton",
                        of("Action", EQUIP_PREFIX + def.id()),
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

        if (data.action.equals(BACK_ACTION)) {
            openPage(ref, store, new MarriageRingPage(playerRef, CustomPageLifetime.CanDismiss));
        } else if (data.action.startsWith(EQUIP_PREFIX)) {
            handleEquip(data.action.substring(EQUIP_PREFIX.length()), ref, store);
        } else if (data.action.startsWith(UNEQUIP_PREFIX)) {
            handleUnequip(ref, store);
        }
    }

    private void handleEquip(@Nonnull String ringId,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {

        UUID senderUuid = playerRef.getUuid();
        MarriageDataManager marriage = EndlessMarriage.getInstance().getMarriageDataManager();
        if (!marriage.isMarried(senderUuid)) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.NOT_MARRIED, "#ff6666"));
            return;
        }

        UUID spouseUuid = marriage.getSpouse(senderUuid);
        int senderPrestige = EndlessLevelingAPI.get().getPlayerPrestigeLevel(senderUuid);
        int spousePrestige = spouseUuid != null
                ? EndlessLevelingAPI.get().getPlayerPrestigeLevel(spouseUuid)
                : 0;
        if (Math.min(senderPrestige, spousePrestige) < tier.getPrestigeRequired()) {
            playerRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.RING_NEED_PRESTIGE_TIER, "#ff6666",
                    tier.getPrestigeRequired()));
            return;
        }

        TieredRingDataManager rings = EndlessMarriage.getInstance().getTieredRingDataManager();
        TieredRingDefinition def = TieredRingCatalog.byId(ringId);
        if (rings == null || def == null) {
            playerRef.sendMessage(MarriageMessages.ringsChat(MarriageMessages.RINGS_UNKNOWN_ID, "#ff6666", ringId));
            return;
        }

        boolean ok = rings.equipRing(senderUuid, ringId, ref, store);
        if (!ok) {
            playerRef.sendMessage(MarriageMessages.ringsChat(MarriageMessages.RINGS_FAILED, "#ff6666", def.displayName()));
            return;
        }

        playerRef.sendMessage(MarriageMessages.ringsChat(MarriageMessages.RINGS_EQUIPPED, "#66ff66", def.displayName()));
        notifySpouse(senderUuid, spouseUuid, def.displayName());
        refresh(ref, store);
    }

    private void handleUnequip(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        TieredRingDataManager rings = EndlessMarriage.getInstance().getTieredRingDataManager();
        if (rings == null) {
            return;
        }
        if (rings.unequipRing(playerRef.getUuid(), ref, store)) {
            playerRef.sendMessage(MarriageMessages.ringsChat(MarriageMessages.RINGS_UNEQUIPPED, "#66ff66"));
        }
        refresh(ref, store);
    }

    private void notifySpouse(@Nonnull UUID senderUuid, UUID spouseUuid, @Nonnull String ringName) {
        if (spouseUuid == null) {
            return;
        }
        PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
        if (spouseRef != null && spouseRef.isValid()) {
            String senderName = playerRef.getUsername() != null
                    ? playerRef.getUsername()
                    : senderUuid.toString().substring(0, 8);
            spouseRef.sendMessage(MarriageMessages.shortChat(MarriageMessages.RING_SPOUSE_EQUIPPED, "#4fd7f7",
                    senderName, ringName));
        }
    }

    /** Re-open this same tier's page so the EQUIP/UNEQUIP buttons reflect the new state. */
    private void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        openPage(ref, store, new MarriageRingVariationPage(playerRef, CustomPageLifetime.CanDismiss, tier));
    }

    private void openPage(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull SafeInteractiveCustomUIPage<MarriagePageData> page) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
