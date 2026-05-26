/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage;

import com.airijko.endlessmarriage.commands.subcommands.MarriageMessages;
import com.airijko.endlessleveling.util.Lang;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sends a global wedding announcement to every online player whenever a marriage is
 * confirmed: Hytale event title, congratulatory chat message, and the wedding march SFX.
 */
public final class MarriageAnnouncer {

    private static final String WEDDING_MARCH_SOUND_ID = "SFX_EM_Ceremony_WeddingMarch";
    private static final String COLOR_SUCCESS = "#66ff66";

    private MarriageAnnouncer() {
    }

    /**
     * Broadcasts a wedding announcement to every online player. Per-player message text is
     * resolved against each player's locale via {@link Lang}.
     */
    public static void announceMarriage(@Nonnull String spouse1,
            @Nonnull String spouse2,
            @Nullable String priestName) {

        int soundIndex = SoundEvent.getAssetMap().getIndex(WEDDING_MARCH_SOUND_ID);
        boolean hasSound = soundIndex != Integer.MIN_VALUE && soundIndex != 0;

        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        for (PlayerRef player : universe.getPlayers()) {
            if (player == null || !player.isValid()) {
                continue;
            }

            String officiantSuffix = (priestName != null && !priestName.isBlank())
                    ? Lang.tr(player.getUuid(), MarriageMessages.ANNOUNCE_OFFICIANT_SUFFIX,
                            " Officiated by {0}.", priestName)
                    : "";

            Message titlePrimary = Message.raw(
                    Lang.tr(player.getUuid(), MarriageMessages.ANNOUNCE_TITLE, "A Wedding Has Taken Place!"));
            Message titleSecondary = Message.raw(
                    Lang.tr(player.getUuid(), MarriageMessages.ANNOUNCE_SUBTITLE,
                            "{0} & {1} are now married!", spouse1, spouse2));

            Message chatAnnouncement = Message.raw(
                    Lang.tr(player.getUuid(), MarriageMessages.PREFIX, "[Endless Marriage] ")
                            + Lang.tr(player.getUuid(), MarriageMessages.ANNOUNCE_CHAT,
                                    "Congratulations to {0} and {1} on their marriage!{2} Wishing the newlyweds a lifetime of happiness!",
                                    spouse1, spouse2, officiantSuffix))
                    .color(COLOR_SUCCESS);

            EventTitleUtil.showEventTitleToPlayer(player, titlePrimary, titleSecondary, true);
            player.sendMessage(chatAnnouncement);

            if (hasSound) {
                Ref<EntityStore> playerEntity = player.getReference();
                if (playerEntity != null && playerEntity.isValid() && playerEntity.getStore() != null) {
                    SoundUtil.playSoundEvent2d(playerEntity, soundIndex, SoundCategory.SFX, playerEntity.getStore());
                }
            }
        }
    }
}
