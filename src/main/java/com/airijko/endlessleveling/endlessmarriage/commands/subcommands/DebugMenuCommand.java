/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.endlessmarriage.data.MarriagePair;
import com.airijko.endlessleveling.endlessmarriage.ui.MarriageMainPage;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /marry debug menu - Open the marriage main UI in "fake married" mode so an
 * admin can preview the married view without an actual partner. The menu's
 * kiss / piggyback buttons will route through any active debug NPC (spawn one
 * first with /marry debug npc to test those interactions).
 */
public class DebugMenuCommand extends AbstractPlayerCommand {

    /** Sentinel UUID used as the fake spouse for the debug menu's MarriagePair. */
    private static final UUID DEBUG_SPOUSE_UUID = new UUID(0L, 1L);

    public DebugMenuCommand() {
        super("testmenu", "Open the marriage menu in fake-married preview mode");
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

        Player player = context.senderAs(Player.class);
        if (player == null) {
            return;
        }

        UUID senderUuid = senderRef.getUuid();
        MarriagePair fakePair = new MarriagePair(senderUuid, DEBUG_SPOUSE_UUID, null, System.currentTimeMillis());

        senderRef.sendMessage(Message.raw("[Marriage Debug] Opening menu in fake-married preview mode. "
                + "Spawn a debug NPC with /marry debug npc to test kiss / piggyback buttons.").color("#4fd7f7"));

        CompletableFuture.runAsync(() -> {
            MarriageMainPage page = new MarriageMainPage(senderRef, CustomPageLifetime.CanDismiss, ref, store, fakePair);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
    }
}
