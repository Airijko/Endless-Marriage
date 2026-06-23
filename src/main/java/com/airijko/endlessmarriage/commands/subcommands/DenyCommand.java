/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessmarriage.data.MarriageDataManager;
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

public class DenyCommand extends AbstractPlayerCommand {

    public DenyCommand() {
        super("deny", "Deny a marriage proposal");
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

        UUID proposer = data.getProposer(senderUuid);
        if (proposer == null) {
            senderRef.sendMessage(msg("You have no pending proposals.", COLOR_ERROR));
            return;
        }

        data.removeProposal(proposer);
        senderRef.sendMessage(msg("You denied the proposal.", COLOR_WARN));
        PlayerRef proposerRef = Universe.get().getPlayer(proposer);
        if (proposerRef != null) {
            proposerRef.sendMessage(msg(resolvePlayerName(senderUuid)
                    + " denied your proposal.", COLOR_ERROR));
        }
    }
}
