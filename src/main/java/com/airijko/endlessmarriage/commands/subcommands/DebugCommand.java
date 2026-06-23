/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * /marry admin - Parent command for admin/debug subcommands.
 */
public class DebugCommand extends AbstractPlayerCommand {

    public DebugCommand() {
        super("admin", "Admin commands for marriage system");
        this.addAliases("debug");

        this.addSubCommand(new DebugInventoryCommand());
        this.addSubCommand(new DebugNpcCommand());
        this.addSubCommand(new DebugKissCommand());
        this.addSubCommand(new DebugPiggybackCommand());
        this.addSubCommand(new DebugMenuCommand());
        this.addSubCommand(new DebugRingsCommand());
        this.addSubCommand(new AdminListCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return true;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {

        if (!OperatorHelper.isOperator(senderRef)) {
            senderRef.sendMessage(MarriageMessages.adminLine("You do not have permission to use this command.", "#ff6666"));
            return;
        }

        senderRef.sendMessage(MarriageMessages.adminLine("Available: /marry admin inv | npc | kiss | piggyback | testmenu | rings | list", "#4fd7f7"));
    }
}
