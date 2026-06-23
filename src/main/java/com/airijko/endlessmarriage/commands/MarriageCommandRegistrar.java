/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands;

import com.airijko.endlessmarriage.commands.subcommands.CarryCommand;
import com.airijko.endlessmarriage.commands.subcommands.KissCommand;
import com.airijko.endlessmarriage.commands.subcommands.PiggybackCommand;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;

public final class MarriageCommandRegistrar {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private MarriageCommandRegistrar() {
    }

    public static void registerCommands(CommandRegistry commandRegistry) {
        if (commandRegistry == null) {
            LOGGER.atWarning().log("CommandRegistry unavailable; marriage commands will not be registered.");
            return;
        }
        commandRegistry.registerCommand(new MarriageCommand());
        // Top-level shortcuts: previously /marry piggyback and /marry kiss.
        commandRegistry.registerCommand(new PiggybackCommand());
        commandRegistry.registerCommand(new CarryCommand());
        commandRegistry.registerCommand(new KissCommand());
        LOGGER.atInfo().log("Registered /marry, /piggyback, /carry, /kiss commands.");
    }
}
