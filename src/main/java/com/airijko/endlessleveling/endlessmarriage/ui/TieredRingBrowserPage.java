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
import com.airijko.endlessleveling.endlessmarriage.data.TieredRingDataManager;
import com.airijko.endlessleveling.endlessmarriage.data.tiered.TieredRingCatalog;
import com.airijko.endlessleveling.endlessmarriage.data.tiered.TieredRingDefinition;
import com.airijko.endlessleveling.endlessmarriage.data.tiered.TieredRingTier;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.airijko.endlessleveling.ui.base.SafeInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

import static com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType.Activating;
import static com.hypixel.hytale.server.core.ui.builder.EventData.of;

/**
 * Browser page for the tiered, attribute-typed rings. Lists every ring in
 * {@link TieredRingCatalog} (currently only the E tier) and lets the player
 * equip / unequip them.
 *
 * Reachable from {@code /marry debug rings}; not gated on marriage status.
 */
public class TieredRingBrowserPage extends SafeInteractiveCustomUIPage<MarriagePageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String RING_ROW_TEMPLATE = "Pages/Marriage/TieredRingRow.ui";
    private static final String EQUIP_ACTION_PREFIX = "tiered:ring:equip:";
    private static final String UNEQUIP_ACTION_PREFIX = "tiered:ring:unequip:";

    private final PlayerRef playerRef;

    public TieredRingBrowserPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, MarriagePageData.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Marriage/TieredRingBrowserPage.ui");

        UUID senderUuid = playerRef.getUuid();
        TieredRingDataManager data = EndlessMarriage.getInstance().getTieredRingDataManager();

        TieredRingDefinition equipped = data != null ? data.getEquippedRing(senderUuid) : null;
        if (equipped != null) {
            ui.set("#TieredCurrentRingLabel.Text", "Equipped: " + equipped.displayName());
            ui.set("#TieredCurrentRingLabel.Style.TextColor", equipped.color());
        } else {
            ui.set("#TieredCurrentRingLabel.Text", "No tiered ring equipped");
            ui.set("#TieredCurrentRingLabel.Style.TextColor", "#c0cee5");
        }

        // Render every ring (currently only the E tier exists in the catalog).
        ui.clear("#TieredRingRows");

        List<TieredRingDefinition> rings = TieredRingCatalog.all();
        for (int i = 0; i < rings.size(); i++) {
            TieredRingDefinition def = rings.get(i);
            ui.append("#TieredRingRows", RING_ROW_TEMPLATE);
            String base = "#TieredRingRows[" + i + "]";

            ui.set(base + " #TieredRingRowIcon.ItemId", def.iconItemId());
            ui.set(base + " #TieredRingName.Text", def.displayName());
            ui.set(base + " #TieredRingName.Style.TextColor", def.color());
            ui.set(base + " #TieredRingDesc.Text", def.description());

            TieredRingTier tier = def.tier();
            ui.set(base + " #TieredRingTierLabel.Text", "TIER " + tier.getDisplayName());
            ui.set(base + " #TieredRingTierLabel.Style.TextColor", tier.getColor());

            boolean isEquipped = equipped != null && equipped.id().equals(def.id());
            if (isEquipped) {
                ui.set(base + " #TieredRingActionButton.Text", "UNEQUIP");
                events.addEventBinding(Activating,
                        base + " #TieredRingActionButton",
                        of("Action", UNEQUIP_ACTION_PREFIX + def.id()),
                        false);
            } else {
                ui.set(base + " #TieredRingActionButton.Text", "EQUIP");
                events.addEventBinding(Activating,
                        base + " #TieredRingActionButton",
                        of("Action", EQUIP_ACTION_PREFIX + def.id()),
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

        if (data.action.startsWith(EQUIP_ACTION_PREFIX)) {
            String ringId = data.action.substring(EQUIP_ACTION_PREFIX.length());
            handleEquip(ringId, ref, store);
        } else if (data.action.startsWith(UNEQUIP_ACTION_PREFIX)) {
            handleUnequip(ref, store);
        }
    }

    private void handleEquip(@Nonnull String ringId,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {

        TieredRingDataManager data = EndlessMarriage.getInstance().getTieredRingDataManager();
        if (data == null) {
            return;
        }
        TieredRingDefinition def = TieredRingCatalog.byId(ringId);
        if (def == null) {
            playerRef.sendMessage(Message.raw("[Rings] Unknown ring id: " + ringId).color("#ff6666"));
            return;
        }

        boolean ok = data.equipRing(playerRef.getUuid(), ringId, ref, store);
        if (ok) {
            playerRef.sendMessage(Message.raw("[Rings] Equipped " + def.displayName() + ".").color("#66ff66"));
        } else {
            playerRef.sendMessage(Message.raw("[Rings] Failed to equip " + def.displayName() + ".").color("#ff6666"));
        }
    }

    private void handleUnequip(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        TieredRingDataManager data = EndlessMarriage.getInstance().getTieredRingDataManager();
        if (data == null) {
            return;
        }
        boolean removed = data.unequipRing(playerRef.getUuid(), ref, store);
        if (removed) {
            playerRef.sendMessage(Message.raw("[Rings] Unequipped tiered ring.").color("#66ff66"));
        }
    }
}
