/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessmarriage.MarriageAnnouncer;
import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.data.MarriageDataManager;
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

import static com.airijko.endlessmarriage.commands.subcommands.MarriageUtil.*;

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
            senderRef.sendMessage(msg("You have no pending proposals.", COLOR_ERROR));
            return;
        }

        // Remarriage cooldown: check both the acceptor and the proposer
        Long senderDivorceTime = data.getDivorceTimestamp(senderUuid);
        if (senderDivorceTime != null) {
            long elapsed = System.currentTimeMillis() - senderDivorceTime;
            if (elapsed < HOURS_72_MS) {
                long remaining = HOURS_72_MS - elapsed;
                senderRef.sendMessage(msg("You must wait 72 hours after a divorce before remarrying. "
                        + "Time remaining: " + formatDuration(remaining) + ".", COLOR_ERROR));
                return;
            }
        }
        Long proposerDivorceTime = data.getDivorceTimestamp(proposer);
        if (proposerDivorceTime != null) {
            long elapsed = System.currentTimeMillis() - proposerDivorceTime;
            if (elapsed < HOURS_72_MS) {
                long remaining = HOURS_72_MS - elapsed;
                senderRef.sendMessage(msg("The proposer recently divorced and must wait before remarrying. "
                        + "Time remaining: " + formatDuration(remaining) + ".", COLOR_ERROR));
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
            senderRef.sendMessage(msg("You are now married to "
                    + proposerName + "!", COLOR_SUCCESS));
            PlayerRef proposerRef = Universe.get().getPlayer(proposer);
            if (proposerRef != null) {
                proposerRef.sendMessage(msg(senderName
                        + " accepted your proposal! You are now married!", COLOR_SUCCESS));
            }
            // Global wedding announcement: title, chat broadcast, wedding march SFX
            MarriageAnnouncer.announceMarriage(proposerName, senderName, null);
        } else {
            // Stage pending marriage awaiting priest
            data.addPendingMarriage(proposer, senderUuid);
            senderRef.sendMessage(msg("Proposal accepted! A Priest must now officiate your marriage.", COLOR_SUCCESS));
            senderRef.sendMessage(msg("Ask a player with the Priest class to use /marry to officiate.", COLOR_INFO));
            PlayerRef proposerRef = Universe.get().getPlayer(proposer);
            if (proposerRef != null) {
                proposerRef.sendMessage(msg(resolvePlayerName(senderUuid)
                        + " accepted! Find a Priest to officiate.", COLOR_SUCCESS));
            }
        }
    }
}
