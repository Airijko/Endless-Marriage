/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.endlessmarriage.EndlessMarriage;
import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Shared helpers for marriage subcommands.
 */
final class MarriageUtil {

    static final String COLOR_SUCCESS = "#66ff66";
    static final String COLOR_ERROR = "#ff6666";
    static final String COLOR_INFO = "#4fd7f7";
    static final String COLOR_WARN = "#ff9900";
    static final String PREFIX = "[Endless Marriage] ";

    private MarriageUtil() {
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
}
