/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 *
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.endlessmarriage.MarriageAnnouncer;
import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.*;

public class AcceptCommand extends AbstractPlayerCommand {

    public AcceptCommand() {
        super("accept", "Accept a marriage proposal");
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
        MarriageConfig config = config();

        UUID proposer = data.getProposer(senderUuid);
        if (proposer == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.NO_PENDING_PROPOSALS, COLOR_ERROR));
            return;
        }

        // Remarriage cooldown: check both the acceptor and the proposer
        Long senderDivorceTime = data.getDivorceTimestamp(senderUuid);
        if (senderDivorceTime != null) {
            long elapsed = System.currentTimeMillis() - senderDivorceTime;
            if (elapsed < HOURS_72_MS) {
                long remaining = HOURS_72_MS - elapsed;
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.COOLDOWN_ACCEPTOR, COLOR_ERROR,
                        formatDuration(remaining)));
                return;
            }
        }
        Long proposerDivorceTime = data.getDivorceTimestamp(proposer);
        if (proposerDivorceTime != null) {
            long elapsed = System.currentTimeMillis() - proposerDivorceTime;
            if (elapsed < HOURS_72_MS) {
                long remaining = HOURS_72_MS - elapsed;
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.COOLDOWN_PROPOSER, COLOR_ERROR,
                        formatDuration(remaining)));
                return;
            }
        }

        data.removeProposal(proposer);

        boolean adminBypass = OperatorHelper.isOperator(senderRef);

        if (!config.isRequirePriestForMarriage() || adminBypass) {
            // Marry directly (no priest needed, or admin bypass)
            data.marry(proposer, senderUuid, null);
            String senderName = resolvePlayerName(senderUuid);
            String proposerName = resolvePlayerName(proposer);
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.NOW_MARRIED_TO, COLOR_SUCCESS, proposerName));
            PlayerRef proposerRef = Universe.get().getPlayer(proposer);
            if (proposerRef != null) {
                proposerRef.sendMessage(MarriageMessages.chat(MarriageMessages.PROPOSAL_ACCEPTED_BY, COLOR_SUCCESS, senderName));
            }
            // Global wedding announcement: title, chat broadcast, wedding march SFX
            MarriageAnnouncer.announceMarriage(proposerName, senderName, null);
        } else {
            // Stage pending marriage awaiting priest
            data.addPendingMarriage(proposer, senderUuid);
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PRIEST_REQUIRED, COLOR_SUCCESS));
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PRIEST_HINT, COLOR_INFO));
            PlayerRef proposerRef = Universe.get().getPlayer(proposer);
            if (proposerRef != null) {
                proposerRef.sendMessage(MarriageMessages.chat(MarriageMessages.PROPOSER_ACCEPTED_PRIEST_NEEDED,
                        COLOR_SUCCESS, resolvePlayerName(senderUuid)));
            }
        }
    }
}
