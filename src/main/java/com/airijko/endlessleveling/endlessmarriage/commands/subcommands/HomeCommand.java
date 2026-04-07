/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageHome;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * /marry home - Teleport to the marriage home.
 */
public class HomeCommand extends AbstractPlayerCommand {

    public HomeCommand() {
        super("home", "Teleport to marriage home");
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
            senderRef.sendMessage(Message.raw(PREFIX + "You must be married to use this.").color(COLOR_ERROR));
            return;
        }

        MarriageHome home = data.getHome(senderUuid);
        if (home == null) {
            senderRef.sendMessage(Message.raw(PREFIX + "No marriage home has been set. Use /marry to set one first.").color(COLOR_ERROR));
            return;
        }

        World targetWorld = Universe.get().getWorld(home.worldName());
        if (targetWorld == null) {
            senderRef.sendMessage(Message.raw(PREFIX + "The world '" + home.worldName() + "' no longer exists.").color(COLOR_ERROR));
            return;
        }

        Vector3d pos = new Vector3d(home.x(), home.y(), home.z());
        Vector3f rot = new Vector3f(home.pitch(), home.yaw(), 0f);
        Teleport teleport = Teleport.createForPlayer(targetWorld, pos, rot);
        store.addComponent(ref, Teleport.getComponentType(), teleport);

        senderRef.sendMessage(Message.raw(PREFIX + "Teleporting to marriage home...").color(COLOR_SUCCESS));
    }
}
