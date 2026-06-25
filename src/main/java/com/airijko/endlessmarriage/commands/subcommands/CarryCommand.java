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

import com.airijko.endlessmarriage.EndlessMarriage;
import com.airijko.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.airijko.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * /carry - Toggle a "carry" session with your spouse: the mirror of /piggyback.
 * Instead of hopping onto your spouse, you pick <i>them</i> up and carry them on
 * your back. Running it again sets them back down.
 */
public class CarryCommand extends AbstractPlayerCommand {

    private static final String COLOR_HEART = "#f2a2e8";

    public CarryCommand() {
        super("carry", "Pick up and carry your spouse");
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
        PiggybackService piggyback = EndlessMarriage.getInstance().getPiggybackService();

        // Toggle off: already carrying our spouse -> set them down.
        if (piggyback.isCarrying(senderUuid)) {
            piggyback.dismountAny(senderUuid);
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.CARRY_PUT_DOWN_SELF, COLOR_INFO));
            return;
        }

        // If we are currently being carried/riding, /carry hops us down instead.
        if (piggyback.isRiding(senderUuid)) {
            piggyback.dismount(senderUuid, ref, store);
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PIGGYBACK_DISMOUNT_SELF, COLOR_INFO));
            return;
        }

        PiggybackService.MountResult result = piggyback.tryCarry(senderUuid, ref, store);
        switch (result) {
            case SUCCESS -> {
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.CARRY_SUCCESS_SELF, COLOR_SUCCESS));
                UUID spouseUuid = dataManager().getSpouse(senderUuid);
                if (spouseUuid != null) {
                    PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
                    if (spouseRef != null && spouseRef.isValid()) {
                        spouseRef.sendMessage(MarriageMessages.chat(MarriageMessages.CARRY_SPOUSE_CARRIED,
                                COLOR_HEART, resolvePlayerName(senderUuid)));
                    }
                }
            }
            case DISABLED ->
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PIGGYBACK_DISABLED, COLOR_WARN));
            case NOT_MARRIED ->
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.MUST_BE_MARRIED, COLOR_ERROR));
            case SPOUSE_OFFLINE ->
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.SPOUSE_NOT_ONLINE, COLOR_ERROR));
            case SPOUSE_NOT_IN_WORLD ->
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.SPOUSE_NOT_IN_WORLD, COLOR_ERROR));
            case SPOUSE_DIFFERENT_WORLD ->
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.SPOUSE_DIFFERENT_WORLD, COLOR_ERROR));
            case TOO_FAR ->
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.CARRY_TOO_FAR, COLOR_WARN));
            case ALREADY_CARRYING ->
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.CARRY_ALREADY_CARRYING, COLOR_WARN));
            case SELF_IS_RIDING ->
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.CARRY_SELF_RIDING, COLOR_WARN));
            case SPOUSE_ALREADY_CARRYING, SPOUSE_IS_RIDING ->
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PIGGYBACK_SPOUSE_BUSY, COLOR_WARN));
            default ->
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PIGGYBACK_ERROR, COLOR_ERROR));
        }
    }
}
