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
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.TieredRingDataManager;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Tier-selection screen for wedding rings. Lists the six tiers (E-S); each tier
 * is gated behind a prestige threshold both partners must meet. Selecting an
 * unlocked tier opens {@link MarriageRingVariationPage}, where the player picks
 * which attribute variation of that tier to equip (the stat is applied via the
 * shared {@link TieredRingDataManager}).
 */
public class MarriageRingPage extends SafeInteractiveCustomUIPage<MarriagePageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String RING_ROW_TEMPLATE = "Pages/Marriage/RingRow.ui";
    private static final String SELECT_TIER_PREFIX = "marry:ringtier:";

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

        // Gate on each partner's HIGHEST prestige across their profiles, resolved from
        // persistent storage so an offline spouse still reads their real prestige instead
        // of collapsing to 0 (which previously locked every tier above E while they were away).
        int senderPrestige = EndlessLevelingAPI.get().getHighestPrestigeLevel(senderUuid);
        int spousePrestige = spouseUuid != null
                ? EndlessLevelingAPI.get().getHighestPrestigeLevel(spouseUuid)
                : 0;
        int lowestPrestige = Math.min(senderPrestige, spousePrestige);

        // Show the currently-equipped variation (sourced from the tiered system).
        TieredRingDataManager rings = EndlessMarriage.getInstance().getTieredRingDataManager();
        TieredRingDefinition equipped = rings != null ? rings.getEquippedRing(senderUuid) : null;
        if (equipped != null) {
            ui.set("#CurrentRingLabel.Text", "Equipped: " + equipped.displayName());
            ui.set("#CurrentRingLabel.Style.TextColor", equipped.color());
        } else {
            ui.set("#CurrentRingLabel.Text", "No ring equipped");
        }

        ui.set("#PrestigeInfoLabel.Text",
                "Your prestige: " + senderPrestige + " | Spouse prestige: " + spousePrestige);
        ui.set("#RingInfoLabel.Text", "Select an unlocked tier to choose its ring variation.");

        // One row per tier; selecting an unlocked tier drills into its variations.
        ui.clear("#RingRows");

        TieredRingTier equippedTier = equipped != null ? equipped.tier() : null;
        TieredRingTier[] tiers = TieredRingTier.values();
        for (int i = 0; i < tiers.length; i++) {
            TieredRingTier tier = tiers[i];
            ui.append("#RingRows", RING_ROW_TEMPLATE);
            String base = "#RingRows[" + i + "]";

            ui.set(base + " #RingRowIcon.ItemId", tier.getIconItemId());
            ui.set(base + " #RingName.Text", tier.getDisplayName() + " Tier Ring");
            ui.set(base + " #RingName.Style.TextColor", tier.getColor());
            ui.set(base + " #RingDesc.Text", tier.getDescription());

            boolean meetsPrestige = lowestPrestige >= tier.getPrestigeRequired();

            if (tier.getPrestigeRequired() > 0) {
                ui.set(base + " #PrestigeReq.Text", "Prestige " + tier.getPrestigeRequired());
                ui.set(base + " #PrestigeReq.Style.TextColor", meetsPrestige ? "#66ff66" : "#ff6666");
                ui.set(base + " #PrestigeReq.Visible", true);
            } else {
                ui.set(base + " #PrestigeReq.Visible", false);
            }

            // Show how many variations this tier offers (replaces the old cost slot).
            ui.set(base + " #CostLabel.Text", "10 variations");
            ui.set(base + " #CostLabel.Style.TextColor", "#8be0b2");
            ui.set(base + " #CostLabel.Visible", true);

            if (!meetsPrestige) {
                ui.set(base + " #ActionButton.Text", "LOCKED");
                ui.set(base + " #ActionButton.Disabled", true);
            } else {
                ui.set(base + " #ActionButton.Text", tier == equippedTier ? "CHANGE" : "SELECT");
                events.addEventBinding(Activating,
                        base + " #ActionButton",
                        of("Action", SELECT_TIER_PREFIX + tier.name()),
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

        if (data.action.startsWith(SELECT_TIER_PREFIX)) {
            TieredRingTier tier = TieredRingTier.fromName(data.action.substring(SELECT_TIER_PREFIX.length()));
            if (tier != null) {
                openVariationPage(ref, store, tier);
            }
        }
    }

    private void openVariationPage(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull TieredRingTier tier) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        MarriageRingVariationPage page =
                new MarriageRingVariationPage(playerRef, CustomPageLifetime.CanDismiss, tier);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
