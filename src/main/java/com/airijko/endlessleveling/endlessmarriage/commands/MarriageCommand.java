/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands;

import com.airijko.endlessleveling.endlessmarriage.commands.subcommands.*;
import com.airijko.endlessleveling.endlessmarriage.ui.MarriageMainPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Root /marry command. Opens the Marriage UI hub when run without subcommands.
 */
public class MarriageCommand extends AbstractPlayerCommand {

    public MarriageCommand() {
        super("marry", "Marriage system commands");
        this.addAliases("marriage");

        this.addSubCommand(new ProposeCommand());
        this.addSubCommand(new AcceptCommand());
        this.addSubCommand(new DenyCommand());
        this.addSubCommand(new FindPriestCommand());
        this.addSubCommand(new OfficiateCommand());
        this.addSubCommand(new DivorceCommand());
        this.addSubCommand(new GrantDivorceCommand());
        this.addSubCommand(new StatusCommand());
        this.addSubCommand(new RecordsCommand());
        this.addSubCommand(new SetHomeCommand());
        this.addSubCommand(new HomeCommand());
        this.addSubCommand(new TpPartnerCommand());
        this.addSubCommand(new InventoryCommand());
        this.addSubCommand(new DebugCommand());
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

        Player player = context.senderAs(Player.class);
        if (player == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            MarriageMainPage page = new MarriageMainPage(senderRef, CustomPageLifetime.CanDismiss, ref, store);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
    }
}
