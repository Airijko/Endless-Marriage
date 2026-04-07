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
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * /marry tp - Teleport to your spouse.
 */
public class TpPartnerCommand extends AbstractPlayerCommand {

    public TpPartnerCommand() {
        super("tp", "Teleport to your spouse");
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

        UUID spouseUuid = data.getSpouse(senderUuid);
        if (spouseUuid == null) {
            senderRef.sendMessage(Message.raw(PREFIX + "Could not find your spouse.").color(COLOR_ERROR));
            return;
        }

        PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
        if (spouseRef == null || !spouseRef.isValid()) {
            senderRef.sendMessage(Message.raw(PREFIX + "Your spouse is not online.").color(COLOR_ERROR));
            return;
        }

        Ref<EntityStore> spouseEntity = spouseRef.getReference();
        if (spouseEntity == null) {
            senderRef.sendMessage(Message.raw(PREFIX + "Your spouse is not in a world.").color(COLOR_ERROR));
            return;
        }

        Store<EntityStore> spouseStore = spouseEntity.getStore();
        Vector3d spousePos = resolvePosition(spouseEntity, spouseStore);
        if (spousePos == null) {
            senderRef.sendMessage(Message.raw(PREFIX + "Could not locate your spouse.").color(COLOR_ERROR));
            return;
        }

        // Resolve the spouse's world
        World spouseWorld = spouseStore.getExternalData().getWorld();
        Teleport teleport = Teleport.createForPlayer(spouseWorld, spousePos.clone(), new com.hypixel.hytale.math.vector.Vector3f(0f, 0f, 0f));
        store.addComponent(ref, Teleport.getComponentType(), teleport);

        String spouseName = resolvePlayerName(spouseUuid);
        senderRef.sendMessage(Message.raw(PREFIX + "Teleporting to " + spouseName + "...").color(COLOR_SUCCESS));
    }

    @Nullable
    private Vector3d resolvePosition(Ref<EntityStore> entityRef, Store<EntityStore> entityStore) {
        if (entityRef == null || entityStore == null) {
            return null;
        }
        try {
            TransformComponent transform = entityStore.getComponent(entityRef, TransformComponent.getComponentType());
            return transform != null ? transform.getPosition() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
