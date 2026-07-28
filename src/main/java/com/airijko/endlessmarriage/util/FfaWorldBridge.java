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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Soft (reflection-only) bridge to EndlessLeveling-Core's optional
 * {@code FreeForAllWorlds} registry. EL-Core's {@code FreeForAllWorlds} class is NOT on this
 * mod's compile classpath, so access goes through {@link MethodHandles} and fails open
 * whenever EL-Core is absent or its API shape changes.
 *
 * <p>Why EndlessMarriage needs this: a temporary free-for-all PvP event arena is a world
 * where spouse protection (zero-damage friendly fire, piggyback damage reduction) must NOT
 * apply — spouses take full damage from each other there. {@code SpouseProtectionSystem}
 * checks {@link #isFreeForAll(String)} first and skips both protections when it's
 * {@code true}.
 *
 * <p>Unlike a one-shot latched bridge, a negative resolution here is NOT cached forever:
 * EL-Core may load after EndlessMarriage (or, on an old EL-Core, may never carry this class
 * at all), and these calls are cheap and frequent enough that re-attempting resolution on
 * every call until it succeeds costs nothing. Once resolved, the {@link MethodHandle} is
 * cached in a volatile field and never re-resolved.
 *
 * <p>Target API:
 * <pre>
 *   com.airijko.endlessleveling.util.FreeForAllWorlds.isFreeForAll(String worldName) : boolean
 * </pre>
 */
public final class FfaWorldBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private static final String REGISTRY_CLASS = "com.airijko.endlessleveling.util.FreeForAllWorlds";

    private static volatile MethodHandle handle;   // cached on success only
    private static volatile boolean loggedFailure = false;

    private FfaWorldBridge() {}

    /**
     * True only when EL-Core's {@code FreeForAllWorlds} registry reports {@code worldName}
     * as an active free-for-all arena. Fails open to {@code false} (= normal spouse
     * protections apply) whenever EL-Core is absent, the registry/method is missing, or the
     * call throws for any reason.
     */
    public static boolean isFreeForAll(String worldName) {
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
            // Handle went stale (e.g. hot-reloaded EL-Core) — drop it so the next call
            // re-resolves instead of repeatedly throwing.
            handle = null;
            return false;
        }
    }

    /**
     * Resolves and caches the {@code isFreeForAll(String)} method handle. Deliberately NOT
     * latched on failure — EL-Core is a hard dependency but may not have this class yet on
     * an older build, so every call while unresolved re-attempts the lookup. Only the first
     * failure is logged (at WARN), to avoid spamming the log on every damage tick.
     */
    private static MethodHandle resolve() {
        try {
            Class<?> registry = Class.forName(REGISTRY_CLASS);
            MethodHandle h = MethodHandles.publicLookup().findStatic(
                    registry, "isFreeForAll", MethodType.methodType(boolean.class, String.class));
            handle = h;
            return h;
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                LOGGER.atWarning().withCause(t).log(
                        "EL-Core FreeForAllWorlds.isFreeForAll not resolvable; FFA-world PvP "
                                + "skip is disabled until it becomes available (normal spouse "
                                + "protections apply).");
            }
            return null;
        }
    }
}
