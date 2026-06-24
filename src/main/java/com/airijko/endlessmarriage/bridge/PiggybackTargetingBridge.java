/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.bridge;

import com.airijko.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Optional, reflection-only bridge to Refixes-Endless' {@code PiggybackPairs} registry.
 *
 * <p>When Refixes-Endless is present, its combat mixins
 * ({@code MixinEntityCollisionProvider}, {@code MixinSelectInteraction}) let two players in
 * a piggyback session pass through each other's attacks and projectiles. Those mixins read
 * a {@code PiggybackPairs} registry that has no idea who is piggybacking until we install a
 * resolver backed by {@link PiggybackService}'s live maps.
 *
 * <p>This is a <b>soft enhancement</b>: Endless-Marriage never compile-depends on Refixes,
 * and if the registry class is absent (Refixes not installed) the install is a no-op — the
 * marriage feature keeps working, partners just can't pass through each other (the core
 * "can't damage each other" guard in {@code SpouseProtectionSystem} is unaffected). Only JDK
 * types ({@link BooleanSupplier}, {@link BiPredicate}) cross the boundary, so there is no
 * shared-class coupling to break.
 */
public final class PiggybackTargetingBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final String REGISTRY_CLASS = "cc.irori.refixes.early.util.PiggybackPairs";

    private PiggybackTargetingBridge() {}

    private static volatile boolean installed = false;

    /** Installs the live resolver into the Refixes registry, if that registry exists. */
    public static void install(@Nonnull PiggybackService service) {
        try {
            Class<?> registry = Class.forName(REGISTRY_CLASS);
            Method install = registry.getMethod("install", BooleanSupplier.class, BiPredicate.class);
            BooleanSupplier activeGate = service::hasActiveSessions;
            BiPredicate<UUID, UUID> resolver = service::arePartners;
            install.invoke(null, activeGate, resolver);
            installed = true;
            LOGGER.atInfo().log(
                    "Piggyback un-targeting bridge installed (Refixes-Endless detected): "
                            + "partners will pass through each other's attacks/projectiles.");
        } catch (ClassNotFoundException notPresent) {
            LOGGER.atInfo().log(
                    "Refixes-Endless not present; piggyback partners can still block each "
                            + "other's attacks/projectiles (core damage cancel is unaffected).");
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("Failed to install piggyback un-targeting bridge.");
        }
    }

    /** Clears the resolver on disable so a reload can't leave a stale reference behind. */
    public static void uninstall() {
        if (!installed) {
            return;
        }
        try {
            Class.forName(REGISTRY_CLASS).getMethod("uninstall").invoke(null);
        } catch (Throwable ignored) {
            // Registry gone or already torn down — nothing to do.
        } finally {
            installed = false;
        }
    }
}
