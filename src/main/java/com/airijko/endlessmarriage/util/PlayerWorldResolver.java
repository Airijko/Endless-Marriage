/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Resolves the world a player is currently in, purely via the global {@link Universe} +
 * {@link PlayerRef} accessors. Safe to call off the world thread — callers like the XP
 * grant listener and the XP-overflow funnel may fire from raid/dungeon reward paths that
 * aren't ticking on any particular store, so this deliberately never does a raw ECS
 * component read.
 *
 * <p>Every hop is guarded and the whole resolution is wrapped defensively: unresolvable
 * (offline, no entity reference, no store, no world) always returns {@code null} rather
 * than throwing, so callers can treat "unresolvable" the same as "not disabled" and stay
 * fail-open.
 */
public final class PlayerWorldResolver {

    private PlayerWorldResolver() {}

    @Nullable
    public static String worldNameOf(@Nonnull UUID uuid) {
        try {
            Universe universe = Universe.get();
            if (universe == null) {
                return null;
            }
            PlayerRef playerRef = universe.getPlayer(uuid);
            if (playerRef == null || !playerRef.isValid()) {
                return null;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) {
                return null;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                return null;
            }
            EntityStore entityStore = store.getExternalData();
            if (entityStore == null) {
                return null;
            }
            World world = entityStore.getWorld();
            return world == null ? null : world.getName();
        } catch (Throwable t) {
            return null;
        }
    }
}
