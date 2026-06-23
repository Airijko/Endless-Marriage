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
import com.airijko.endlessleveling.ui.base.SafeInteractiveCustomUIPage;
import com.airijko.endlessmarriage.EndlessMarriage;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.MarriageOverflowLog;
import com.airijko.endlessmarriage.data.OverflowEvent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Per-couple log of XP-overflow funnel events: how much over-cap XP each spouse has
 * passed to the other, with a bounded list of recent rolled-up windows. Opened from the
 * married main page via the "EXP OVERFLOW" button.
 */
public class MarriageOverflowLogPage extends SafeInteractiveCustomUIPage<MarriagePageData> {

    private static final String ROW_TEMPLATE = "Pages/Marriage/MarriageOverflowRow.ui";

    private final PlayerRef playerRef;

    public MarriageOverflowLogPage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, MarriagePageData.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {

        ui.append("Pages/Marriage/MarriageOverflowLogPage.ui");

        UUID self = playerRef.getUuid();
        MarriageDataManager data = EndlessMarriage.getInstance().getMarriageDataManager();
        UUID spouse = data.getSpouse(self);

        // Flush any in-flight window so the numbers shown are current.
        var service = EndlessMarriage.getInstance().getMarriageOverflowService();
        if (service != null) {
            service.flushForPlayer(self);
        }

        MarriageOverflowLog log = EndlessMarriage.getInstance().getMarriageOverflowLog();
        double lifetime = (log != null && spouse != null) ? log.getCoupleLifetimeTotal(self, spouse) : 0.0D;
        java.util.List<OverflowEvent> recentEvents = (log != null && spouse != null)
                ? log.snapshotRecent(self, spouse)
                : java.util.Collections.emptyList();

        ui.clear("#OverflowRows");
        ui.set("#OverflowTotalLabel.Text", formatXp(lifetime) + " XP");

        // Live kiss-buff status (active countdown / cooldown / ready), shared with
        // the main marriage page so both surfaces report the same state.
        KissBuffStatus.Display kissBuff = KissBuffStatus.describe(self);
        ui.set("#KissBuffStatusLabel.Text", kissBuff.text());
        ui.set("#KissBuffStatusLabel.Style.TextColor", kissBuff.color());

        // Full lifecycle breakdown: when the buff activated, when it expires, and
        // when it comes off cooldown (absolute server time + relative countdown).
        KissBuffStatus.Lifecycle lifecycle = KissBuffStatus.describeLifecycle(self);
        ui.set("#KissActivatedLabel.Text", lifecycle.activated());
        ui.set("#KissExpiresLabel.Text", lifecycle.expires());
        ui.set("#KissReadyLabel.Text", lifecycle.offCooldown());

        if (recentEvents.isEmpty()) {
            ui.set("#EmptyLabel.Visible", true);
            return;
        }
        ui.set("#EmptyLabel.Visible", false);

        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        int index = 0;
        for (OverflowEvent event : recentEvents) {
            ui.append("#OverflowRows", ROW_TEMPLATE);
            String base = "#OverflowRows[" + index + "]";

            String fromName = resolvePlayerName(event.from());
            String toName = resolvePlayerName(event.to());

            ui.set(base + " #OverflowRowDirection.Text", fromName + "  →  " + toName);
            ui.set(base + " #OverflowRowAmount.Text", "+" + formatXp(event.amount()) + " XP");
            ui.set(base + " #OverflowRowKind.Text", kindLabel(event.kind()));
            ui.set(base + " #OverflowRowDate.Text", dateFmt.format(new Date(event.timestamp())));

            index++;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull MarriagePageData data) {
        super.handleDataEvent(ref, store, data);
        // Read-only page beyond dismissal.
    }

    @Nonnull
    private static String kindLabel(@Nonnull String kind) {
        return switch (kind) {
            case OverflowEvent.KIND_RAID -> "RAID REWARD";
            case OverflowEvent.KIND_MIXED -> "COMBAT + RAID";
            default -> "COMBAT";
        };
    }

    @Nonnull
    private static String formatXp(double amount) {
        return String.format(Locale.US, "%,d", Math.round(amount));
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
        java.util.Map<String, String> names =
                EndlessLevelingAPI.get().getPlayerNames(java.util.Collections.singletonList(uuid));
        String name = names.get(uuid.toString());
        if (name != null && !name.isBlank()) {
            return name;
        }
        return uuid.toString().substring(0, 8);
    }
}
