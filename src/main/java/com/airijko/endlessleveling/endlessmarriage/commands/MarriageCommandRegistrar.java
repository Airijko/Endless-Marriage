/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands;

import com.hypixel.hytale.logger.HytaleLogger;

public final class MarriageCommandRegistrar {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private MarriageCommandRegistrar() {
    }

    public static void registerCommands(Object commandRegistry) {
        MarriageCommand marryCommand = new MarriageCommand();
        registerCommand(commandRegistry, marryCommand);
        LOGGER.atInfo().log("Registered /marry command.");
    }

    private static void registerCommand(Object commandRegistry, Object command) {
        if (commandRegistry == null || command == null) {
            return;
        }

        // Try exact type match first
        try {
            var method = commandRegistry.getClass().getMethod("registerCommand", command.getClass());
            method.setAccessible(true);
            method.invoke(commandRegistry, command);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to register command via exact type.");
        }

        // Fallback to Object parameter
        try {
            var method = commandRegistry.getClass().getMethod("registerCommand", Object.class);
            method.setAccessible(true);
            method.invoke(commandRegistry, command);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to register command via Object fallback.");
        }

        // Fallback to CommandManager singleton
        try {
            Class<?> managerClass = Class.forName("com.hypixel.hytale.server.core.command.system.CommandManager");
            Object manager = managerClass.getMethod("get").invoke(null);
            if (manager != null) {
                Class<?> abstractCommandClass = Class.forName(
                        "com.hypixel.hytale.server.core.command.system.AbstractCommand");
                var registerMethod = managerClass.getMethod("register", abstractCommandClass);
                registerMethod.invoke(manager, command);
            }
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to register command via CommandManager fallback.");
        }
    }
}
