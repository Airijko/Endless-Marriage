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
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.COLOR_ERROR;
import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.COLOR_SUCCESS;
import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.PREFIX;

/**
 * /marry reload - Reloads {@code config.json} from disk (and re-runs the
 * config migrator). Op-only: marriage config tweaks should never be exposed to
 * regular players.
 */
public class ReloadCommand extends AbstractPlayerCommand {

    public ReloadCommand() {
        super("reload", "Reload EndlessMarriage config from disk");
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
            senderRef.sendMessage(Message.raw(PREFIX + "You must be an operator to reload the marriage config.")
                    .color(COLOR_ERROR));
            return;
        }

        try {
            EndlessMarriage.getInstance().reloadConfig();
            senderRef.sendMessage(Message.raw(PREFIX + "Config reloaded from disk.").color(COLOR_SUCCESS));
        } catch (Exception ex) {
            senderRef.sendMessage(Message.raw(PREFIX + "Failed to reload config: " + ex.getMessage())
                    .color(COLOR_ERROR));
        }
    }
}
