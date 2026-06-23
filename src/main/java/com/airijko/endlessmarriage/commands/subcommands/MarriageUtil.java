/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessmarriage.EndlessMarriage;
import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Shared helpers for marriage subcommands.
 */
final class MarriageUtil {

    static final String COLOR_SUCCESS = MarriageMessages.Color.SUCCESS;
    static final String COLOR_ERROR = MarriageMessages.Color.ERROR;
    static final String COLOR_INFO = MarriageMessages.Color.INFO;
    static final String COLOR_WARN = MarriageMessages.Color.WARN;

    /** 72 hours in milliseconds. */
    static final long HOURS_72_MS = 72L * 60L * 60L * 1000L;

    /** Permission node that allows officiating marriages regardless of class. */
    static final String PERM_OFFICIATE = "endlessmarriage.officiate";

    /** Permission node that allows granting divorces regardless of class. */
    static final String PERM_DIVORCE_GRANT = "endlessmarriage.divorce.grant";

    private MarriageUtil() {
    }

    /**
     * Builds a brand-prefixed chat line for inline (non-localized) command feedback, matching the
     * split-color {@code [Endless Marriage]} prefix used by {@link MarriageMessages}.
     */
    static Message msg(@Nonnull String body, @Nonnull String color) {
        return MarriageMessages.prefixedLine(body, color);
    }

    static MarriageDataManager dataManager() {
        return EndlessMarriage.getInstance().getMarriageDataManager();
    }

    static MarriageConfig config() {
        return EndlessMarriage.getInstance().getMarriageConfig();
    }

    static String resolvePlayerName(@Nonnull UUID uuid) {
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref != null && ref.getUsername() != null) {
            return ref.getUsername();
        }
        var snapshot = EndlessLevelingAPI.get().getPlayerSnapshot(uuid);
        if (snapshot != null && snapshot.playerName() != null) {
            return snapshot.playerName();
        }
        return uuid.toString().substring(0, 8);
    }

    @Nullable
    static PlayerRef findPlayerByName(@Nonnull String name) {
        for (PlayerRef ref : Universe.get().getPlayers()) {
            if (ref != null && name.equalsIgnoreCase(ref.getUsername())) {
                return ref;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the command sender has the given permission node.
     */
    static boolean senderHasPermission(@Nonnull CommandContext context, @Nonnull String node) {
        try {
            return context.sender().hasPermission(node);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Formats a duration in milliseconds as "Xh Ym".
     */
    static String formatDuration(long ms) {
        long hours = ms / 3_600_000L;
        long minutes = (ms % 3_600_000L) / 60_000L;
        return hours + "h " + minutes + "m";
    }
}
