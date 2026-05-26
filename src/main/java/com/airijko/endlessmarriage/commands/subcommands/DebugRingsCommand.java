/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessmarriage.ui.TieredRingBrowserPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * /marry debug rings - Open the tiered (attribute-typed) ring browser so an
 * admin can verify the catalog visually and equip / unequip rings without
 * needing to be married. The browser exercises the same equip pipeline used
 * elsewhere, so any stat application bugs will surface here too.
 */
public class DebugRingsCommand extends AbstractPlayerCommand {

    public DebugRingsCommand() {
        super("rings", "Browse and verify all tiered attribute rings");
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

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        senderRef.sendMessage(Message.raw("[Marriage Debug] Opening tiered ring browser.").color("#4fd7f7"));

        CompletableFuture.runAsync(() -> {
            TieredRingBrowserPage page = new TieredRingBrowserPage(senderRef, CustomPageLifetime.CanDismiss);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
    }
}
