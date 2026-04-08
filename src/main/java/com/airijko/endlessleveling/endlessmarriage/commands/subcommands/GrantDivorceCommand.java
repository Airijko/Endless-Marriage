/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * Magistrate-only command to grant a pending divorce.
 */
public class GrantDivorceCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> playerArg = this.withRequiredArg("player", "Player requesting divorce", ArgTypes.STRING);

    public GrantDivorceCommand() {
        super("grant-divorce", "(Magistrate) Grant a pending divorce");
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

        // Check magistrate class requirement (permission-holders bypass)
        if (config.isRequireMagistrateForDivorce()
                && !senderHasPermission(context, PERM_DIVORCE_GRANT)) {
            String magistrateClassId = config.getMagistrateClassId();
            String primaryClass = EndlessLevelingAPI.get().getPrimaryClassId(senderUuid);
            String secondaryClass = EndlessLevelingAPI.get().getSecondaryClassId(senderUuid);
            boolean isMagistrate = magistrateClassId.equalsIgnoreCase(primaryClass)
                    || magistrateClassId.equalsIgnoreCase(secondaryClass);

            if (!isMagistrate) {
                senderRef.sendMessage(Message.raw(PREFIX
                        + "Only players with the Magistrate class (or the endlessmarriage.divorce.grant permission) can grant divorces.").color(COLOR_ERROR));
                return;
            }
        }

        String targetName = playerArg.get(context);
        PlayerRef targetRef = findPlayerByName(targetName);
        if (targetRef == null) {
            senderRef.sendMessage(Message.raw(PREFIX + "Player '" + targetName + "' is not online.").color(COLOR_ERROR));
            return;
        }

        UUID targetUuid = targetRef.getUuid();
        if (!data.hasPendingDivorce(targetUuid)) {
            senderRef.sendMessage(Message.raw(PREFIX + "This player has not requested a divorce.").color(COLOR_ERROR));
            return;
        }

        UUID spouseUuid = data.getSpouse(targetUuid);
        if (spouseUuid == null) {
            senderRef.sendMessage(Message.raw(PREFIX + "This player is not married.").color(COLOR_ERROR));
            data.removePendingDivorce(targetUuid);
            return;
        }

        // Grant the divorce
        data.divorce(targetUuid, spouseUuid, senderUuid);
        senderRef.sendMessage(Message.raw(PREFIX + "You granted the divorce of "
                + resolvePlayerName(targetUuid) + " and " + resolvePlayerName(spouseUuid) + ".").color(COLOR_SUCCESS));
        targetRef.sendMessage(Message.raw(PREFIX + "Your divorce has been granted by Magistrate "
                + resolvePlayerName(senderUuid) + ".").color(COLOR_WARN));
        PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
        if (spouseRef != null) {
            spouseRef.sendMessage(Message.raw(PREFIX + "Your divorce from "
                    + resolvePlayerName(targetUuid) + " has been granted.").color(COLOR_WARN));
        }
    }
}
