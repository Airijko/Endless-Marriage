/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.util.OperatorHelper;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

import static com.airijko.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * /marry admin reset-cooldown &lt;player&gt; - Clears the post-divorce remarriage
 * cooldown for a player so they may propose/accept again immediately.
 * Accepts an online player name or a raw UUID (so offline players can be cleared).
 */
public class ResetCooldownCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> playerArg =
            this.withRequiredArg("player", "Player name (online) or UUID", ArgTypes.STRING);

    public ResetCooldownCommand() {
        super("reset-cooldown", "Reset a player's post-divorce remarriage cooldown");
        this.addAliases("clear-cooldown");
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

        if (!OperatorHelper.isOperator(senderRef)) {
            senderRef.sendMessage(MarriageMessages.adminLine("You do not have permission to use this command.", "#ff6666"));
            return;
        }

        String query = playerArg.get(context);
        UUID targetUuid = resolveTargetUuid(query);
        if (targetUuid == null) {
            senderRef.sendMessage(MarriageMessages.adminLine(
                    "Could not find an online player named \"" + query + "\". "
                            + "Use their exact name while online, or pass their UUID.", "#ff6666"));
            return;
        }

        MarriageDataManager data = dataManager();
        if (data.getDivorceTimestamp(targetUuid) == null) {
            senderRef.sendMessage(MarriageMessages.adminLine(
                    resolvePlayerName(targetUuid) + " has no active divorce cooldown.", "#f7c94f"));
            return;
        }

        data.clearDivorceCooldown(targetUuid);
        senderRef.sendMessage(MarriageMessages.adminLine(
                "Cleared the remarriage cooldown for " + resolvePlayerName(targetUuid) + ".", "#4fd7f7"));

        PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
        if (targetRef != null) {
            targetRef.sendMessage(msg("Your remarriage cooldown has been reset. You may marry again.", COLOR_SUCCESS));
        }
    }

    /** Resolves an online player by name, falling back to parsing the query as a UUID. */
    @Nullable
    private static UUID resolveTargetUuid(@Nonnull String query) {
        PlayerRef online = findPlayerByName(query);
        if (online != null) {
            return online.getUuid();
        }
        try {
            return UUID.fromString(query);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
