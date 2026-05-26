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
import org.joml.Vector3d;
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

import static com.airijko.endlessmarriage.commands.subcommands.MarriageUtil.*;

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
        Vector3d spousePos = resolvePosition(spouseEntity, spouseStore);
        if (spousePos == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.TP_CANNOT_LOCATE, COLOR_ERROR));
            return;
        }

        // Resolve the spouse's world
        World spouseWorld = spouseStore.getExternalData().getWorld();
        Teleport teleport = Teleport.createForPlayer(spouseWorld, new Vector3d(spousePos), new com.hypixel.hytale.math.vector.Rotation3f(0f, 0f, 0f));
        store.addComponent(ref, Teleport.getComponentType(), teleport);

        String spouseName = resolvePlayerName(spouseUuid);
        senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.TP_TELEPORTING_TO, COLOR_SUCCESS, spouseName));
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
