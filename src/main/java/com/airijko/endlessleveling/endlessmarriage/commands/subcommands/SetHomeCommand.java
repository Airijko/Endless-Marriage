/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 *
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageHome;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * /marry sethome - Sets the marriage home at the player's current location.
 */
public class SetHomeCommand extends AbstractPlayerCommand {

    public SetHomeCommand() {
        super("sethome", "Set marriage home at your current location");
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
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.SETHOME_MUST_BE_MARRIED, COLOR_ERROR));
            return;
        }

        UUID spouseUuid = data.getSpouse(senderUuid);
        if (spouseUuid == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.CANNOT_FIND_SPOUSE, COLOR_ERROR));
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.SETHOME_POSITION_UNKNOWN, COLOR_ERROR));
            return;
        }

        Vector3d pos = transform.getPosition();
        float yaw = 0f;
        float pitch = 0f;

        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRotation != null) {
            yaw = headRotation.getRotation().yaw();
            pitch = headRotation.getRotation().pitch();
        }

        MarriageHome home = new MarriageHome(world.getName(), pos.x(), pos.y(), pos.z(), yaw, pitch);
        data.setHome(senderUuid, spouseUuid, home);

        String coords = String.format("%.0f, %.0f, %.0f", pos.x(), pos.y(), pos.z());
        senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.SETHOME_SUCCESS, COLOR_SUCCESS,
                coords, world.getName()));
    }
}
