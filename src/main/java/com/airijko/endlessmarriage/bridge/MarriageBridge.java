/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.bridge;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessmarriage.EndlessMarriage;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.data.MarriagePair;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stable, reflection-friendly read API that lets other Endless plugins surface a
 * player's marriage status without importing EndlessMarriage internals.
 *
 * <p>EndlessLink resolves this class by name (no compile-time dependency on
 * EndlessMarriage), so the surface here must stay stable: a single static
 * {@code status(UUID)} returning a flat {@code Map<String,String>} (or
 * {@code null}). Every path degrades to {@code null} on error so an exception
 * never leaks across the plugin boundary.</p>
 */
public final class MarriageBridge {

    private MarriageBridge() {}

    /**
     * Marriage snapshot for {@code uuid}, or {@code null} when the player isn't
     * married (or the plugin isn't ready yet). Keys when present:
     * <ul>
     *   <li>{@code spouse} — spouse UUID string</li>
     *   <li>{@code spouseName} — best-effort resolved spouse display name</li>
     *   <li>{@code marriedAtMillis} — wedding epoch millis (as a string)</li>
     *   <li>{@code officiant} — officiant UUID string (absent when none)</li>
     *   <li>{@code officiantName} — resolved officiant name (absent when none)</li>
     * </ul>
     */
    @Nullable
    public static Map<String, String> status(UUID uuid) {
        if (uuid == null) return null;
        try {
            EndlessMarriage plugin = EndlessMarriage.getInstance();
            if (plugin == null) return null;
            MarriageDataManager data = plugin.getMarriageDataManager();
            if (data == null || !data.isMarried(uuid)) return null;

            MarriagePair pair = data.getMarriage(uuid);
            if (pair == null) return null;
            UUID spouse = pair.getSpouse(uuid);
            if (spouse == null) return null;

            Map<String, String> out = new HashMap<>();
            out.put("spouse", spouse.toString());
            out.put("spouseName", resolveName(spouse));
            out.put("marriedAtMillis", Long.toString(pair.timestamp()));
            UUID officiant = pair.officiant();
            if (officiant != null) {
                out.put("officiant", officiant.toString());
                out.put("officiantName", resolveName(officiant));
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String resolveName(UUID uuid) {
        try {
            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref != null && ref.getUsername() != null) return ref.getUsername();
        } catch (Throwable ignored) {
        }
        try {
            var snapshot = EndlessLevelingAPI.get().getPlayerSnapshot(uuid);
            if (snapshot != null && snapshot.playerName() != null) return snapshot.playerName();
        } catch (Throwable ignored) {
        }
        return uuid.toString().substring(0, 8);
    }
}
