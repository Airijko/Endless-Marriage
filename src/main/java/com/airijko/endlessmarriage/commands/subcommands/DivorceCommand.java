/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.MarriagePair;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.airijko.endlessmarriage.commands.subcommands.MarriageUtil.*;

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
            senderRef.sendMessage(Message.raw(PREFIX + "You are not married.").color(COLOR_ERROR));
            return;
        }

        // Enforce 72-hour minimum before divorce is allowed
        MarriagePair pair = data.getMarriage(senderUuid);
        if (pair != null) {
            long elapsed = System.currentTimeMillis() - pair.timestamp();
            if (elapsed < HOURS_72_MS) {
                long remaining = HOURS_72_MS - elapsed;
                senderRef.sendMessage(Message.raw(PREFIX
                        + "You must be married for at least 72 hours before divorcing. "
                        + "Time remaining: " + formatDuration(remaining) + ".").color(COLOR_ERROR));
                return;
            }
        }

        UUID spouseUuid = data.getSpouse(senderUuid);

        if (!config.isRequireMagistrateForDivorce()) {
            // Divorce immediately
            data.divorce(senderUuid, spouseUuid, null);
            senderRef.sendMessage(Message.raw(PREFIX + "You are now divorced from "
                    + resolvePlayerName(spouseUuid) + ".").color(COLOR_WARN));
            PlayerRef spouseRef = spouseUuid != null ? Universe.get().getPlayer(spouseUuid) : null;
            if (spouseRef != null) {
                spouseRef.sendMessage(Message.raw(PREFIX + resolvePlayerName(senderUuid)
                        + " has divorced you.").color(COLOR_WARN));
            }
        } else {
            // Stage pending divorce
            data.addPendingDivorce(senderUuid);
            senderRef.sendMessage(Message.raw(PREFIX
                    + "Divorce requested. A Magistrate must finalize it.").color(COLOR_WARN));
            senderRef.sendMessage(Message.raw(PREFIX
                    + "Ask a player with the Magistrate class to use /marry to grant the divorce.").color(COLOR_INFO));
            PlayerRef spouseRef = spouseUuid != null ? Universe.get().getPlayer(spouseUuid) : null;
            if (spouseRef != null) {
                spouseRef.sendMessage(Message.raw(PREFIX + resolvePlayerName(senderUuid)
                        + " has requested a divorce.").color(COLOR_WARN));
            }
        }
    }
}
