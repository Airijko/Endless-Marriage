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
import com.airijko.endlessleveling.endlessmarriage.services.KissService;
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
 * /marry kiss - Initiate a kiss with your spouse if you're within 1 block.
 * Spawns heart particles and plays the kiss SFX for both partners.
 */
public class KissCommand extends AbstractPlayerCommand {

    public KissCommand() {
        super("kiss", "Kiss your spouse (must be within 1 block)");
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
        KissService kissService = EndlessMarriage.getInstance().getKissService();

        KissService.KissResult result = kissService.tryKiss(senderUuid, ref, store);
        switch (result) {
            case SUCCESS -> {
                senderRef.sendMessage(Message.raw(PREFIX + "You share a kiss with your spouse.").color("#f2a2e8"));
                UUID spouseUuid = dataManager().getSpouse(senderUuid);
                if (spouseUuid != null) {
                    PlayerRef spouseRef = Universe.get().getPlayer(spouseUuid);
                    if (spouseRef != null && spouseRef.isValid()) {
                        spouseRef.sendMessage(Message.raw(PREFIX + resolvePlayerName(senderUuid)
                                + " kissed you!").color("#f2a2e8"));
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
                senderRef.sendMessage(Message.raw(PREFIX + "You must be within 1 block of your spouse to kiss.").color(COLOR_WARN));
            case ERROR ->
                senderRef.sendMessage(Message.raw(PREFIX + "Could not kiss right now.").color(COLOR_ERROR));
        }
    }
}
