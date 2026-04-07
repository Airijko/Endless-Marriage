/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.endlessmarriage.EndlessMarriage;
import com.airijko.endlessleveling.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * /marry piggyback - Toggle a piggyback session with your spouse.
 * Mounts you on top of them when nearby; dismounts if already mounted.
 */
public class PiggybackCommand extends AbstractPlayerCommand {

    public PiggybackCommand() {
        super("piggyback", "Toggle piggyback on your spouse");
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

        if (piggyback.isRiding(senderUuid)) {
            piggyback.dismount(senderUuid, ref, store);
            senderRef.sendMessage(Message.raw(PREFIX + "You hop down off your spouse.").color(COLOR_INFO));
            return;
        }

        if (piggyback.isCarrying(senderUuid)) {
            // Carrier wants to shake their rider off.
            piggyback.dismountAny(senderUuid);
            senderRef.sendMessage(Message.raw(PREFIX + "You let your spouse down.").color(COLOR_INFO));
            return;
        }

        PiggybackService.MountResult result = piggyback.tryMount(senderUuid, ref, store);
        switch (result) {
            case SUCCESS -> {
                senderRef.sendMessage(Message.raw(PREFIX + "You hop onto your spouse's back!").color(COLOR_SUCCESS));
                UUID spouseUuid = dataManager().getSpouse(senderUuid);
                if (spouseUuid != null) {
                    PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
                    if (spouseRef != null && spouseRef.isValid()) {
                        spouseRef.sendMessage(Message.raw(PREFIX + resolvePlayerName(senderUuid)
                                + " is riding piggyback!").color("#f2a2e8"));
                    }
                }
            }
            case NOT_MARRIED ->
                senderRef.sendMessage(Message.raw(PREFIX + "You must be married to use this.").color(COLOR_ERROR));
            case SPOUSE_OFFLINE ->
                senderRef.sendMessage(Message.raw(PREFIX + "Your spouse is not online.").color(COLOR_ERROR));
            case SPOUSE_NOT_IN_WORLD ->
                senderRef.sendMessage(Message.raw(PREFIX + "Your spouse is not in a world.").color(COLOR_ERROR));
            case SPOUSE_DIFFERENT_WORLD ->
                senderRef.sendMessage(Message.raw(PREFIX + "Your spouse is in a different world.").color(COLOR_ERROR));
            case TOO_FAR ->
                senderRef.sendMessage(Message.raw(PREFIX + "You must be next to your spouse to piggyback.").color(COLOR_WARN));
            case ALREADY_MOUNTED ->
                senderRef.sendMessage(Message.raw(PREFIX + "You are already mounted.").color(COLOR_WARN));
            case SPOUSE_ALREADY_CARRYING, SPOUSE_IS_RIDING ->
                senderRef.sendMessage(Message.raw(PREFIX + "Your spouse is already in a piggyback session.").color(COLOR_WARN));
            case ERROR ->
                senderRef.sendMessage(Message.raw(PREFIX + "Could not piggyback right now.").color(COLOR_ERROR));
        }
    }
}
