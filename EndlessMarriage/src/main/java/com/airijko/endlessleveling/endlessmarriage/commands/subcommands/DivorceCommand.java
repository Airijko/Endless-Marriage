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

import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.data.MarriagePair;
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

public class DivorceCommand extends AbstractPlayerCommand {

    public DivorceCommand() {
        super("divorce", "Request a divorce");
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

        if (!data.isMarried(senderUuid)) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.NOT_MARRIED, COLOR_ERROR));
            return;
        }

        // Enforce 72-hour minimum before divorce is allowed
        MarriagePair pair = data.getMarriage(senderUuid);
        if (pair != null) {
            long elapsed = System.currentTimeMillis() - pair.timestamp();
            if (elapsed < HOURS_72_MS) {
                long remaining = HOURS_72_MS - elapsed;
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.MIN_MARRIAGE_TIME, COLOR_ERROR,
                        formatDuration(remaining)));
                return;
            }
        }

        UUID spouseUuid = data.getSpouse(senderUuid);

        if (!config.isRequireMagistrateForDivorce()) {
            // Divorce immediately
            data.divorce(senderUuid, spouseUuid, null);
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.DIVORCED_FROM, COLOR_WARN,
                    resolvePlayerName(spouseUuid)));
            PlayerRef spouseRef = spouseUuid != null ? Universe.get().getPlayer(spouseUuid) : null;
            if (spouseRef != null) {
                spouseRef.sendMessage(MarriageMessages.chat(MarriageMessages.SPOUSE_DIVORCED_YOU, COLOR_WARN,
                        resolvePlayerName(senderUuid)));
            }
        } else {
            // Stage pending divorce
            data.addPendingDivorce(senderUuid);
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.DIVORCE_PENDING, COLOR_WARN));
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.DIVORCE_MAGISTRATE_HINT, COLOR_INFO));
            PlayerRef spouseRef = spouseUuid != null ? Universe.get().getPlayer(spouseUuid) : null;
            if (spouseRef != null) {
                spouseRef.sendMessage(MarriageMessages.chat(MarriageMessages.SPOUSE_REQUESTED_DIVORCE, COLOR_WARN,
                        resolvePlayerName(senderUuid)));
            }
        }
    }
}
