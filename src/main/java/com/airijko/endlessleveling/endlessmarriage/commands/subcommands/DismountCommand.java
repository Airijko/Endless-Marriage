/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.endlessmarriage.EndlessMarriage;
import com.airijko.endlessleveling.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * /marry dismount - End any active piggyback session, whether you are the
 * rider or the carrier.
 */
public class DismountCommand extends AbstractPlayerCommand {

    public DismountCommand() {
        super("dismount", "End an active piggyback session");
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
        PiggybackService piggyback = EndlessMarriage.getInstance().getPiggybackService();

        if (piggyback.isRiding(senderUuid)) {
            piggyback.dismount(senderUuid, ref, store);
            senderRef.sendMessage(Message.raw(PREFIX + "You hop down off your spouse.").color(COLOR_INFO));
            return;
        }
        if (piggyback.isCarrying(senderUuid)) {
            piggyback.dismountAny(senderUuid);
            senderRef.sendMessage(Message.raw(PREFIX + "You let your spouse down.").color(COLOR_INFO));
            return;
        }
        senderRef.sendMessage(Message.raw(PREFIX + "You are not in a piggyback session.").color(COLOR_WARN));
    }
}
