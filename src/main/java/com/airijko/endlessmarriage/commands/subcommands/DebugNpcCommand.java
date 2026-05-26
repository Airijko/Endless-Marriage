/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessmarriage.EndlessMarriage;
import com.airijko.endlessmarriage.services.DebugNpcService;
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

/**
 * /marry debug npc - Toggle a temporary in-memory NPC at the player's location
 * for use as a target by the debug kiss / piggyback commands. Running it again
 * removes the existing NPC.
 */
public class DebugNpcCommand extends AbstractPlayerCommand {

    public DebugNpcCommand() {
        super("npc", "Spawn or remove a debug marriage target NPC");
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

        UUID senderUuid = senderRef.getUuid();
        DebugNpcService debugNpc = EndlessMarriage.getInstance().getDebugNpcService();
        if (debugNpc == null) {
            senderRef.sendMessage(Message.raw("[Marriage Debug] Debug NPC service unavailable.").color("#ff6666"));
            return;
        }

        if (debugNpc.has(senderUuid)) {
            debugNpc.despawn(senderUuid, store);
            senderRef.sendMessage(Message.raw("[Marriage Debug] Debug NPC removed.").color("#4fd7f7"));
            return;
        }

        DebugNpcService.DebugNpc npc = debugNpc.spawnFor(senderUuid, ref, store);
        if (npc == null) {
            senderRef.sendMessage(Message.raw("[Marriage Debug] Failed to spawn debug NPC. Check server log; "
                    + "the configured 'debug_npc_role' may be invalid.").color("#ff6666"));
            return;
        }

        senderRef.sendMessage(Message.raw("[Marriage Debug] Debug NPC spawned beside you. "
                + "Use /marry debug kiss or /marry debug piggyback to interact, or run this command again to remove it.")
                .color("#4fd7f7"));
    }
}
