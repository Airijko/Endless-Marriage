/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Soft (reflection-only) bridge to Endless-Events's optional
 * {@code MarriageDisabledWorlds} registry. Endless-Events's {@code MarriageDisabledWorlds}
 * class is NOT on this mod's compile classpath, so access goes through {@link MethodHandles}
 * and fails open whenever Endless-Events is absent or its API shape changes.
 *
 * <p>Why EndlessMarriage needs this: an Events world (an FFA arena, a goblin event, etc.)
 * can mark itself as a place where marriage features must NOT apply — spouse protection,
 * proximity buffs, kissing, piggyback, XP sharing, and the marriage commands all gate on
 * {@link #isMarriageDisabled(String)} and skip/refuse when it's {@code true}.
 *
 * <p>Unlike a one-shot latched bridge, a negative resolution here is NOT cached forever:
 * Endless-Events may load after EndlessMarriage (or, on an old Endless-Events, may never
 * carry this class at all, or may not even be installed), and these calls are cheap and
 * frequent enough that re-attempting resolution on every call until it succeeds costs
 * nothing. Once resolved, the {@link MethodHandle} is cached in a volatile field and never
 * re-resolved.
 *
 * <p>Target API:
 * <pre>
 *   com.airijko.endlessevents.api.MarriageDisabledWorlds.isMarriageDisabled(String worldName) : boolean
 * </pre>
 */
public final class EventWorldBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private static final String REGISTRY_CLASS = "com.airijko.endlessevents.api.MarriageDisabledWorlds";

    private static volatile MethodHandle handle;   // cached on success only
    private static volatile boolean loggedFailure = false;

    private EventWorldBridge() {}

    /**
     * True only when Endless-Events's {@code MarriageDisabledWorlds} registry reports
     * {@code worldName} as a world where marriage features are switched off. Fails open to
     * {@code false} (= marriage works normally) whenever Endless-Events is absent, the
     * registry/method is missing, or the call throws for any reason.
     */
    public static boolean isMarriageDisabled(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        MethodHandle h = handle;
        if (h == null) {
            h = resolve();
            if (h == null) {
                return false;
            }
        }
        try {
            return (boolean) h.invokeExact(worldName);
        } catch (Throwable t) {
            // Handle went stale (e.g. hot-reloaded Endless-Events) — drop it so the next
            // call re-resolves instead of repeatedly throwing.
            handle = null;
            return false;
        }
    }

    /**
     * Convenience overload for call sites that already hold a {@link World} rather than a
     * name. Returns {@code false} when {@code world} is {@code null}.
     */
    public static boolean isMarriageDisabled(@Nullable World world) {
        if (world == null) {
            return false;
        }
        return isMarriageDisabled(world.getName());
    }

    /**
     * Resolves and caches the {@code isMarriageDisabled(String)} method handle. Deliberately
     * NOT latched on failure — Endless-Events is an optional dependency that may not have
     * this class yet (or may not be installed at all), so every call while unresolved
     * re-attempts the lookup. Only the first failure is logged (at WARN), to avoid spamming
     * the log on every tick.
     */
    private static MethodHandle resolve() {
        try {
            Class<?> registry = Class.forName(REGISTRY_CLASS);
            MethodHandle h = MethodHandles.publicLookup().findStatic(
                    registry, "isMarriageDisabled", MethodType.methodType(boolean.class, String.class));
            handle = h;
            return h;
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                LOGGER.atWarning().withCause(t).log(
                        "Endless-Events MarriageDisabledWorlds.isMarriageDisabled not resolvable; "
                                + "event-world marriage gating is disabled until it becomes available "
                                + "(normal marriage features apply).");
            }
            return null;
        }
    }
}
