/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 *
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.MarriagePair;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import static com.airijko.endlessmarriage.commands.subcommands.MarriageUtil.*;

public class StatusCommand extends AbstractPlayerCommand {

    public StatusCommand() {
        super("status", "View your marriage status");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {

        UUID senderUuid = senderRef.getUuid();
        MarriageDataManager data = dataManager();

        if (!data.isMarried(senderUuid)) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.STATUS_NOT_MARRIED, COLOR_INFO));

            if (data.hasPendingProposal(senderUuid)) {
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.STATUS_OUTGOING_PROPOSAL, COLOR_INFO));
            }
            if (data.hasProposal(senderUuid)) {
                UUID proposer = data.getProposer(senderUuid);
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.STATUS_INCOMING_PROPOSAL, COLOR_INFO,
                        resolvePlayerName(proposer)));
            }
            if (data.hasPendingMarriage(senderUuid)) {
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.STATUS_PENDING_PRIEST, COLOR_INFO));
            }
            return;
        }

        MarriagePair pair = data.getMarriage(senderUuid);
        UUID spouseUuid = pair.getSpouse(senderUuid);
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(pair.timestamp()));

        senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.STATUS_MARRIED_TO, COLOR_SUCCESS,
                resolvePlayerName(spouseUuid)));
        senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.STATUS_SINCE, COLOR_INFO, date));
        if (pair.officiant() != null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.STATUS_OFFICIATED_BY, COLOR_INFO,
                    resolvePlayerName(pair.officiant())));
        }

        if (data.hasPendingDivorce(senderUuid)) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.STATUS_PENDING_DIVORCE, COLOR_WARN));
        }
    }
}
