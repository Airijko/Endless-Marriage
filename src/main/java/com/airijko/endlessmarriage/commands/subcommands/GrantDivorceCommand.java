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

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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

import static com.airijko.endlessmarriage.commands.subcommands.MarriageUtil.*;

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
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.GRANT_MAGISTRATE_ONLY, COLOR_ERROR));
                return;
            }
        }

        String targetName = playerArg.get(context);
        PlayerRef targetRef = findPlayerByName(targetName);
        if (targetRef == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.TARGET_OFFLINE, COLOR_ERROR, targetName));
            return;
        }

        UUID targetUuid = targetRef.getUuid();
        if (!data.hasPendingDivorce(targetUuid)) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.GRANT_NO_REQUEST, COLOR_ERROR));
            return;
        }

        UUID spouseUuid = data.getSpouse(targetUuid);
        if (spouseUuid == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.GRANT_NOT_MARRIED, COLOR_ERROR));
            data.removePendingDivorce(targetUuid);
            return;
        }

        // Grant the divorce
        data.divorce(targetUuid, spouseUuid, senderUuid);
        senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.GRANT_GRANTED_FOR, COLOR_SUCCESS,
                resolvePlayerName(targetUuid), resolvePlayerName(spouseUuid)));
        targetRef.sendMessage(MarriageMessages.chat(MarriageMessages.GRANT_YOUR_DIVORCE_GRANTED, COLOR_WARN,
                resolvePlayerName(senderUuid)));
        PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
        if (spouseRef != null) {
            spouseRef.sendMessage(MarriageMessages.chat(MarriageMessages.GRANT_SPOUSE_DIVORCE_GRANTED, COLOR_WARN,
                    resolvePlayerName(targetUuid)));
        }
    }
}
