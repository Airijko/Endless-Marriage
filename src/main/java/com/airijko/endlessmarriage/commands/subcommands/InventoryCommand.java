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

import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.airijko.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * /marry inventory - View and use your spouse's full inventory.
 */
public class InventoryCommand extends AbstractPlayerCommand {

    public InventoryCommand() {
        super("inventory", "View and use your spouse's inventory");
        this.addAliases("inv");
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

        if (!data.isMarried(senderUuid)) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.MUST_BE_MARRIED, COLOR_ERROR));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        if (spouseUuid == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.CANNOT_FIND_SPOUSE, COLOR_ERROR));
            return;
        }

        PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
        if (spouseRef == null || !spouseRef.isValid()) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.SPOUSE_NOT_ONLINE, COLOR_ERROR));
            return;
        }

        Ref<EntityStore> spouseEntity = spouseRef.getReference();
        if (spouseEntity == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.SPOUSE_NOT_IN_WORLD, COLOR_ERROR));
            return;
        }

        Store<EntityStore> spouseStore = spouseEntity.getStore();
        World spouseWorld = spouseStore.getExternalData().getWorld();

        Player senderPlayer = store.getComponent(ref, Player.getComponentType());
        if (senderPlayer == null) {
            return;
        }

        // Execute on the spouse's world thread to access their inventory safely
        spouseWorld.execute(() -> {
            Player spousePlayer = spouseStore.getComponent(spouseEntity, Player.getComponentType());
            if (spousePlayer == null) {
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.INV_CANNOT_ACCESS, COLOR_ERROR));
                return;
            }

            Inventory spouseInventory = spousePlayer.getInventory();
            CombinedItemContainer spouseContainer = spouseInventory.getCombinedHotbarFirst();

            // Open the spouse's inventory for the sender (full access - married couples can use each other's items)
            world.execute(() -> {
                senderPlayer.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, new ContainerWindow(spouseContainer));
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.INV_VIEWING, COLOR_INFO,
                        resolvePlayerName(spouseUuid)));
            });
        });
    }
}
