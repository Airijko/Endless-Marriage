/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.airijko.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * /marry admin reset-cooldown &lt;player&gt; - Clears the post-divorce remarriage
 * cooldown for a player so they may propose/accept again immediately.
 *
 * <p>Access is gated by the auto-generated command permission node
 * {@code airijko.endlessmarriage.command.marry.admin.reset-cooldown} (covered by the
 * {@code ...admin.*} wildcard), so operators, permission holders, and the server
 * console can all run it. This is an {@link AbstractAsyncCommand} rather than a
 * player command precisely so the console can execute it — it never touches the
 * sender's entity/world.
 *
 * <p>Accepts an online player name or a raw UUID (so offline players can be cleared).
 */
public class ResetCooldownCommand extends AbstractAsyncCommand {

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
    @Nonnull
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        String query = playerArg.get(context);
        UUID targetUuid = resolveTargetUuid(query);
        if (targetUuid == null) {
            context.sendMessage(MarriageMessages.adminLine(
                    "Could not find an online player named \"" + query + "\". "
                            + "Use their exact name while online, or pass their UUID.", "#ff6666"));
            return CompletableFuture.completedFuture(null);
        }

        MarriageDataManager data = dataManager();
        if (data.getDivorceTimestamp(targetUuid) == null) {
            context.sendMessage(MarriageMessages.adminLine(
                    resolvePlayerName(targetUuid) + " has no active divorce cooldown.", "#f7c94f"));
            return CompletableFuture.completedFuture(null);
        }

        data.clearDivorceCooldown(targetUuid);
        context.sendMessage(MarriageMessages.adminLine(
                "Cleared the remarriage cooldown for " + resolvePlayerName(targetUuid) + ".", "#4fd7f7"));

        PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
        if (targetRef != null) {
            targetRef.sendMessage(msg("Your remarriage cooldown has been reset. You may marry again.", COLOR_SUCCESS));
        }
        return CompletableFuture.completedFuture(null);
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
