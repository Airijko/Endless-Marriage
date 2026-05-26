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
import com.airijko.endlessmarriage.data.MarriagePair;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.airijko.endlessleveling.ui.base.SafeInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Admin UI page listing all active marriages. Opened by /marry admin list.
 */
public class MarriageAdminListPage extends SafeInteractiveCustomUIPage<MarriagePageData> {

    private static final String ROW_TEMPLATE = "Pages/Marriage/MarriageAdminRow.ui";

    private final PlayerRef playerRef;

    public MarriageAdminListPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, MarriagePageData.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Marriage/MarriageAdminListPage.ui");

        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
        List<MarriagePair> marriages = data.getAllMarriages();

        ui.set("#MarriageCountLabel.Text", marriages.size() + " active marriage" + (marriages.size() == 1 ? "" : "s"));
        ui.clear("#MarriageAdminRows");

        if (marriages.isEmpty()) {
            ui.set("#EmptyLabel.Visible", true);
            return;
        }

        ui.set("#EmptyLabel.Visible", false);

        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
        int index = 0;
        for (MarriagePair pair : marriages) {
            ui.append("#MarriageAdminRows", ROW_TEMPLATE);
            String base = "#MarriageAdminRows[" + index + "]";

            String name1 = resolvePlayerName(pair.player1());
            String name2 = resolvePlayerName(pair.player2());
            String date = dateFmt.format(new Date(pair.timestamp()));

            ui.set(base + " #AdminRowNames.Text", name1 + " & " + name2);
            ui.set(base + " #AdminRowDate.Text", date);

            if (pair.officiant() != null) {
                ui.set(base + " #AdminRowOfficiant.Text", "Officiated by: " + resolvePlayerName(pair.officiant()));
                ui.set(base + " #AdminRowOfficiant.Style.TextColor", "#e0c870");
            } else {
                ui.set(base + " #AdminRowOfficiant.Text", "No officiant");
                ui.set(base + " #AdminRowOfficiant.Style.TextColor", "#555555");
            }

            index++;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull MarriagePageData data) {
        super.handleDataEvent(ref, store, data);
        // No interactive elements beyond dismissal.
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
        return uuid.toString().substring(0, 8);
    }
}
