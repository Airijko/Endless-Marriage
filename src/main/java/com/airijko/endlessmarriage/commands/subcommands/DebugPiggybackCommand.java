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
import com.airijko.endlessmarriage.services.PiggybackService;
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
 * /marry debug piggyback - Toggle a piggyback session against the active
 * debug NPC. Bypasses marriage and proximity checks. Spawn the NPC first via
 * /marry debug npc.
 */
public class DebugPiggybackCommand extends AbstractPlayerCommand {

    public DebugPiggybackCommand() {
        super("piggyback", "Mount/dismount the active debug NPC");
        this.addAliases("mount");
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
        PiggybackService piggyback = EndlessMarriage.getInstance().getPiggybackService();

        // Already mounted? Toggle off — works whether the carrier is a real
        // spouse or a debug NPC since the rider/carrier indexes are uniform.
        if (piggyback.isRiding(senderUuid)) {
            piggyback.dismount(senderUuid, ref, store);
            senderRef.sendMessage(Message.raw("[Marriage Debug] You hop down off the debug NPC.").color("#4fd7f7"));
            return;
        }

        DebugNpcService debugNpc = EndlessMarriage.getInstance().getDebugNpcService();
        DebugNpcService.DebugNpc npc = debugNpc != null ? debugNpc.get(senderUuid) : null;
        if (npc == null) {
            senderRef.sendMessage(Message.raw("[Marriage Debug] No debug NPC active. Run /marry debug npc first.")
                    .color("#ff9900"));
            return;
        }

        PiggybackService.MountResult result =
                piggyback.tryMountTarget(senderUuid, ref, store, npc.ref(), npc.syntheticUuid());
        if (result == PiggybackService.MountResult.SUCCESS) {
            senderRef.sendMessage(Message.raw("[Marriage Debug] You hop onto the debug NPC.").color("#f2a2e8"));
        } else {
            senderRef.sendMessage(Message.raw("[Marriage Debug] Could not piggyback debug NPC: " + result).color("#ff6666"));
        }
    }
}
