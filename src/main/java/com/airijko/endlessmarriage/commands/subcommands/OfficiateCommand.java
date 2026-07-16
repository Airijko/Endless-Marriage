/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 *
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.commands.subcommands;

import com.airijko.endlessmarriage.MarriageAnnouncer;
import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.services.WitnessCollector;
import com.airijko.endlessmarriage.util.PositionUtil;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.airijko.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * Priest-only command to officiate a pending marriage.
 * The priest must be nearby both players to complete the ceremony.
 */
public class OfficiateCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> player1Arg = this.withRequiredArg("player1", "First player", ArgTypes.STRING);
    private final RequiredArg<String> player2Arg = this.withRequiredArg("player2", "Second player", ArgTypes.STRING);

    public OfficiateCommand() {
        super("officiate", "(Priest) Officiate a pending marriage");
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
        MarriageConfig config = config();

        // Check priest class requirement (admins and permission-holders bypass)
        if (config.isRequirePriestForMarriage()
                && !OperatorHelper.isOperator(senderRef)
                && !senderHasPermission(context, PERM_OFFICIATE)) {
            if (!MarriageUtil.isPriest(senderUuid)) {
                senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PRIEST_ONLY, COLOR_ERROR));
                return;
            }
        }

        String name1 = player1Arg.get(context);
        String name2 = player2Arg.get(context);

        PlayerRef player1Ref = findPlayerByName(name1);
        PlayerRef player2Ref = findPlayerByName(name2);

        if (player1Ref == null || player2Ref == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.BOTH_MUST_BE_ONLINE, COLOR_ERROR));
            return;
        }

        UUID p1 = player1Ref.getUuid();
        UUID p2 = player2Ref.getUuid();

        // Verify pending marriage
        if (!data.hasPendingMarriage(p1) || !data.hasPendingMarriage(p2)) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.NO_PENDING_PAIR, COLOR_ERROR));
            return;
        }

        UUID[] pendingPair = data.getPendingMarriage(p1);
        if (pendingPair == null
                || !((pendingPair[0].equals(p1) && pendingPair[1].equals(p2))
                        || (pendingPair[0].equals(p2) && pendingPair[1].equals(p1)))) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PAIR_MISMATCH, COLOR_ERROR));
            return;
        }

        // Proximity check: priest must be near both players
        double range = config.getProximityRange();
        double rangeSq = range * range;
        Vector3d priestPos = PositionUtil.resolvePosition(ref, store);

        if (priestPos == null) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PRIEST_POSITION_UNKNOWN, COLOR_ERROR));
            return;
        }

        // Check priest is near player 1
        Ref<EntityStore> entity1 = player1Ref.getReference();
        if (entity1 == null || entity1.getStore() != store) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PRIEST_PLAYER_DIFF_WORLD, COLOR_ERROR,
                    resolvePlayerName(p1)));
            return;
        }
        Vector3d pos1 = PositionUtil.resolvePosition(entity1, entity1.getStore());
        if (pos1 == null || PositionUtil.distanceSq(priestPos, pos1) > rangeSq) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PRIEST_TOO_FAR, COLOR_ERROR,
                    resolvePlayerName(p1), (int) range));
            return;
        }

        // Check priest is near player 2
        Ref<EntityStore> entity2 = player2Ref.getReference();
        if (entity2 == null || entity2.getStore() != store) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PRIEST_PLAYER_DIFF_WORLD, COLOR_ERROR,
                    resolvePlayerName(p2)));
            return;
        }
        Vector3d pos2 = PositionUtil.resolvePosition(entity2, entity2.getStore());
        if (pos2 == null || PositionUtil.distanceSq(priestPos, pos2) > rangeSq) {
            senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.PRIEST_TOO_FAR, COLOR_ERROR,
                    resolvePlayerName(p2), (int) range));
            return;
        }

        // Collect witnesses (players within witness radius of the priest, excluding
        // the priest and the two newlyweds themselves).
        Set<UUID> excluded = new HashSet<>();
        excluded.add(senderUuid);
        excluded.add(p1);
        excluded.add(p2);
        List<UUID> witnesses = WitnessCollector.collect(store, priestPos,
                config.getWitnessMaxRange(), excluded);

        // Officiate the marriage
        data.marry(p1, p2, senderUuid, witnesses);

        String name1Resolved = resolvePlayerName(p1);
        String name2Resolved = resolvePlayerName(p2);
        String priestName = resolvePlayerName(senderUuid);

        // Notify the priest and the newlyweds directly
        senderRef.sendMessage(MarriageMessages.chat(MarriageMessages.OFFICIATED_MARRIAGE, COLOR_SUCCESS,
                name1Resolved, name2Resolved));
        player1Ref.sendMessage(MarriageMessages.chat(MarriageMessages.NOW_MARRIED_OFFICIATED, COLOR_SUCCESS,
                name2Resolved, priestName));
        player2Ref.sendMessage(MarriageMessages.chat(MarriageMessages.NOW_MARRIED_OFFICIATED, COLOR_SUCCESS,
                name1Resolved, priestName));

        // Announce to all online players: title, chat broadcast, wedding march SFX
        MarriageAnnouncer.announceMarriage(name1Resolved, name2Resolved, priestName);
    }

}
