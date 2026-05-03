/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 *
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.commands.subcommands;

import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.endlessmarriage.data.OfficiantRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * View officiant records for priests and magistrates.
 */
public class RecordsCommand extends AbstractPlayerCommand {

    public RecordsCommand() {
        super("records", "View your officiant records");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef senderRef,
            @Nonnull World world) {

        UUID senderUuid = senderRef.getUuid();
        MarriageDataManager data = dataManager();
        List<OfficiantRecord> records = data.getRecordsForOfficiant(senderUuid);

        if (records.isEmpty()) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.RECORDS_NONE, COLOR_INFO));
            return;
        }

        senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.RECORDS_HEADER, COLOR_INFO, records.size()));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (OfficiantRecord record : records) {
            String typeName = record.type() == OfficiantRecord.OfficiantType.MARRIAGE
                    ? MarriageMessages.text(MarriageMessages.RECORDS_TYPE_MARRIAGE)
                    : MarriageMessages.text(MarriageMessages.RECORDS_TYPE_DIVORCE);
            String date = fmt.format(new Date(record.timestamp()));
            senderRef.sendMessage(MarriageMessages.line(MarriageMessages.RECORDS_ENTRY, COLOR_INFO,
                    typeName,
                    resolvePlayerName(record.player1()),
                    resolvePlayerName(record.player2()),
                    date));
        }
    }
}
