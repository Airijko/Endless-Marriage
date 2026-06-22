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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /marry debuginv - Opens a dummy in-memory inventory to test the Bench window.
 * No marriage required. For admin testing only.
 */
public class DebugInventoryCommand extends AbstractPlayerCommand {

    private static final Map<UUID, SimpleItemContainer> DEBUG_CONTAINERS = new ConcurrentHashMap<>();

    public DebugInventoryCommand() {
        super("inv", "Open a dummy inventory for testing");
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
            senderRef.sendMessage(Message.raw("[Marriage Debug] You do not have permission to use this command.").color("#ff6666"));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        UUID uuid = senderRef.getUuid();
        // 36 storage + 9 hotbar = 45 slots (matches a real player inventory)
        SimpleItemContainer container = DEBUG_CONTAINERS.computeIfAbsent(uuid, k -> {
            SimpleItemContainer c = new SimpleItemContainer((short) 45);
            c.addItemStack(new ItemStack("Food_Bread", 10));
            c.addItemStack(new ItemStack("Stone", 32));
            c.addItemStack(new ItemStack("Wood_Log", 16));
            return c;
        });

        player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, new ContainerWindow(container));
        senderRef.sendMessage(Message.raw("[Marriage Debug] Opened dummy inventory (in-memory).").color("#4fd7f7"));
    }
}
