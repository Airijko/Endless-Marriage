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

import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.ui.MarriageProposePage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * /marry propose [player] - Opens proposal UI if no player specified,
 * or proposes directly to the named player.
 */
public class ProposeCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> targetArg = this.withRequiredArg("player", "Player to propose to", ArgTypes.STRING);

    public ProposeCommand() {
        super("propose", "Propose marriage to a player");
        this.addUsageVariant(new ProposeUIVariant());
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
        String targetName = targetArg.get(context);

        if (data.isMarried(senderUuid)) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.ALREADY_MARRIED_SELF, COLOR_ERROR));
            return;
        }

        if (data.hasPendingProposal(senderUuid)) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.ALREADY_HAVE_PROPOSAL, COLOR_ERROR));
            return;
        }

        // Remarriage cooldown check for the sender
        Long senderDivorceTime = data.getDivorceTimestamp(senderUuid);
        if (senderDivorceTime != null) {
            long elapsed = System.currentTimeMillis() - senderDivorceTime;
            if (elapsed < HOURS_72_MS) {
                long remaining = HOURS_72_MS - elapsed;
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.COOLDOWN_SELF, COLOR_ERROR,
                        formatDuration(remaining)));
                return;
            }
        }

        PlayerRef targetRef = findPlayerByName(targetName);
        if (targetRef == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.TARGET_OFFLINE, COLOR_ERROR, targetName));
            return;
        }

        UUID targetUuid = targetRef.getUuid();
        if (targetUuid.equals(senderUuid)) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.CANNOT_PROPOSE_SELF, COLOR_ERROR));
            return;
        }

        if (data.isMarried(targetUuid)) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.TARGET_ALREADY_MARRIED, COLOR_ERROR, targetName));
            return;
        }

        // Remarriage cooldown check for the target
        Long targetDivorceTime = data.getDivorceTimestamp(targetUuid);
        if (targetDivorceTime != null) {
            long elapsed = System.currentTimeMillis() - targetDivorceTime;
            if (elapsed < HOURS_72_MS) {
                long remaining = HOURS_72_MS - elapsed;
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.COOLDOWN_TARGET, COLOR_ERROR,
                        targetName, formatDuration(remaining)));
                return;
            }
        }

        data.addProposal(senderUuid, targetUuid);
        senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PROPOSED_TO, COLOR_SUCCESS, targetName));
        targetRef.sendMessage(MarriageMessages.chat(MarriageMessages.RECEIVED_PROPOSAL, COLOR_INFO,
                resolvePlayerName(senderUuid)));
    }

    /**
     * Usage variant with no arguments — opens the proposal UI.
     */
    private static final class ProposeUIVariant extends AbstractPlayerCommand {

        private ProposeUIVariant() {
            super("Open proposal UI to select a player");
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

            if (data.isMarried(senderUuid)) {
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.ALREADY_MARRIED_SELF, COLOR_ERROR));
                return;
            }

            Player player = context.senderAs(Player.class);
            if (player == null) {
                return;
            }

            CompletableFuture.runAsync(() -> {
                MarriageProposePage page = new MarriageProposePage(senderRef, CustomPageLifetime.CanDismiss);
                player.getPageManager().openCustomPage(ref, store, page);
            }, world);
        }
    }
}
