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
import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessleveling.util.OperatorHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

import static com.airijko.endlessleveling.endlessmarriage.commands.subcommands.MarriageUtil.*;

/**
 * Priest-only command to officiate a pending marriage.
 * The priest must be nearby both players to complete the ceremony.
 */
public class OfficiateCommand extends AbstractPlayerCommand {

    private static final String WEDDING_MARCH_SOUND_ID = "SFX_EM_Ceremony_WeddingMarch";

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

        // Check priest class requirement (admins bypass)
        if (config.isRequirePriestForMarriage() && !OperatorHelper.isOperator(senderRef)) {
            String priestClassId = config.getPriestClassId();
            String primaryClass = EndlessLevelingAPI.get().getPrimaryClassId(senderUuid);
            String secondaryClass = EndlessLevelingAPI.get().getSecondaryClassId(senderUuid);
            boolean isPriest = priestClassId.equalsIgnoreCase(primaryClass)
                    || priestClassId.equalsIgnoreCase(secondaryClass);

            if (!isPriest) {
                senderRef.sendMessage(Message.raw(PREFIX
                        + "Only players with the Priest class can officiate marriages.").color(COLOR_ERROR));
                return;
            }
        }

        String name1 = player1Arg.get(context);
        String name2 = player2Arg.get(context);

        PlayerRef player1Ref = findPlayerByName(name1);
        PlayerRef player2Ref = findPlayerByName(name2);

        if (player1Ref == null || player2Ref == null) {
            senderRef.sendMessage(Message.raw(PREFIX + "Both players must be online.").color(COLOR_ERROR));
            return;
        }

        UUID p1 = player1Ref.getUuid();
        UUID p2 = player2Ref.getUuid();

        // Verify pending marriage
        if (!data.hasPendingMarriage(p1) || !data.hasPendingMarriage(p2)) {
            senderRef.sendMessage(Message.raw(PREFIX
                    + "These players do not have a pending marriage. They must use /marry to propose and accept first.").color(COLOR_ERROR));
            return;
        }

        UUID[] pendingPair = data.getPendingMarriage(p1);
        if (pendingPair == null
                || !((pendingPair[0].equals(p1) && pendingPair[1].equals(p2))
                        || (pendingPair[0].equals(p2) && pendingPair[1].equals(p1)))) {
            senderRef.sendMessage(Message.raw(PREFIX
                    + "These players are not in a pending marriage with each other.").color(COLOR_ERROR));
            return;
        }

        // Proximity check: priest must be near both players
        double range = config.getProximityRange();
        double rangeSq = range * range;
        Vector3d priestPos = resolvePosition(ref, store);

        if (priestPos == null) {
            senderRef.sendMessage(Message.raw(PREFIX
                    + "Unable to verify your position. Try again.").color(COLOR_ERROR));
            return;
        }

        // Check priest is near player 1
        Ref<EntityStore> entity1 = player1Ref.getReference();
        if (entity1 == null || entity1.getStore() != store) {
            senderRef.sendMessage(Message.raw(PREFIX
                    + resolvePlayerName(p1) + " is not in the same world as you.").color(COLOR_ERROR));
            return;
        }
        Vector3d pos1 = resolvePosition(entity1, entity1.getStore());
        if (pos1 == null || distanceSq(priestPos, pos1) > rangeSq) {
            senderRef.sendMessage(Message.raw(PREFIX
                    + "You must be closer to " + resolvePlayerName(p1)
                    + " to officiate (within " + (int) range + " blocks).").color(COLOR_ERROR));
            return;
        }

        // Check priest is near player 2
        Ref<EntityStore> entity2 = player2Ref.getReference();
        if (entity2 == null || entity2.getStore() != store) {
            senderRef.sendMessage(Message.raw(PREFIX
                    + resolvePlayerName(p2) + " is not in the same world as you.").color(COLOR_ERROR));
            return;
        }
        Vector3d pos2 = resolvePosition(entity2, entity2.getStore());
        if (pos2 == null || distanceSq(priestPos, pos2) > rangeSq) {
            senderRef.sendMessage(Message.raw(PREFIX
                    + "You must be closer to " + resolvePlayerName(p2)
                    + " to officiate (within " + (int) range + " blocks).").color(COLOR_ERROR));
            return;
        }

        // Officiate the marriage
        data.marry(p1, p2, senderUuid);

        String name1Resolved = resolvePlayerName(p1);
        String name2Resolved = resolvePlayerName(p2);
        String priestName = resolvePlayerName(senderUuid);

        // Notify the priest and the newlyweds directly
        senderRef.sendMessage(Message.raw(PREFIX + "You have officiated the marriage of "
                + name1Resolved + " and " + name2Resolved + "!").color(COLOR_SUCCESS));
        player1Ref.sendMessage(Message.raw(PREFIX + "You are now married to "
                + name2Resolved + "! Officiated by " + priestName).color(COLOR_SUCCESS));
        player2Ref.sendMessage(Message.raw(PREFIX + "You are now married to "
                + name1Resolved + "! Officiated by " + priestName).color(COLOR_SUCCESS));

        // Announce to all online players
        announceMarriage(name1Resolved, name2Resolved, priestName);
    }

    private void announceMarriage(String spouse1, String spouse2, String priestName) {
        Message titlePrimary = Message.raw("A Wedding Has Taken Place!");
        Message titleSecondary = Message.raw(spouse1 + " & " + spouse2 + " are now married!");
        Message chatAnnouncement = Message.raw(PREFIX
                + "Congratulations to " + spouse1 + " and " + spouse2
                + " on their marriage! Officiated by " + priestName
                + ". Wishing the newlyweds a lifetime of happiness!").color(COLOR_SUCCESS);

        int soundIndex = SoundEvent.getAssetMap().getIndex(WEDDING_MARCH_SOUND_ID);

        for (PlayerRef player : Universe.get().getPlayers()) {
            if (player == null || !player.isValid()) {
                continue;
            }

            // Show title
            EventTitleUtil.showEventTitleToPlayer(player, titlePrimary, titleSecondary, true);

            // Send chat message
            player.sendMessage(chatAnnouncement);

            // Play wedding march sound
            if (soundIndex != Integer.MIN_VALUE && soundIndex != 0) {
                Ref<EntityStore> playerEntity = player.getReference();
                if (playerEntity != null && playerEntity.isValid() && playerEntity.getStore() != null) {
                    SoundUtil.playSoundEvent2d(playerEntity, soundIndex, SoundCategory.SFX, playerEntity.getStore());
                }
            }
        }
    }

    @Nullable
    private Vector3d resolvePosition(Ref<EntityStore> entityRef, Store<EntityStore> entityStore) {
        if (entityRef == null || entityStore == null) {
            return null;
        }
        try {
            TransformComponent transform = entityStore.getComponent(entityRef, TransformComponent.getComponentType());
            return transform != null ? transform.getPosition() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private double distanceSq(Vector3d a, Vector3d b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
