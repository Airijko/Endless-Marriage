/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.listeners;

import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.airijko.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Reacts to {@link PlayerInteractEvent} so that pressing the use/secondary
 * key on a spouse player toggles a piggyback session.
 */
public final class MarriageInteractListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final MarriageDataManager dataManager;
    private final PiggybackService piggybackService;

    public MarriageInteractListener(@Nonnull MarriageDataManager dataManager,
            @Nonnull PiggybackService piggybackService) {
        this.dataManager = dataManager;
        this.piggybackService = piggybackService;
    }

    public void onPlayerInteract(@Nonnull PlayerInteractEvent event) {
        if (event.isCancelled()) {
            return;
        }
        InteractionType type = event.getActionType();
        if (type != InteractionType.Use && type != InteractionType.Secondary) {
            return;
        }

        Ref<EntityStore> targetRef = event.getTargetRef();
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        Ref<EntityStore> riderRef = event.getPlayerRef();
        if (riderRef == null || !riderRef.isValid()) {
            return;
        }
        Store<EntityStore> riderStore = riderRef.getStore();

        UUID senderUuid = resolveUuid(riderRef);
        if (senderUuid == null) {
            return;
        }
        if (!dataManager.isMarried(senderUuid)) {
            return;
        }
        UUID spouseUuid = dataManager.getSpouse(senderUuid);
        if (spouseUuid == null) {
            return;
        }

        // Only react when looking at the actual spouse.
        UUID targetUuid = resolveUuid(targetRef);
        if (targetUuid == null || !targetUuid.equals(spouseUuid)) {
            return;
        }

        PlayerRef senderPlayerRef = Universe.get().getPlayer(senderUuid);

        // Toggle: if the sender is already riding their spouse, dismount; otherwise mount.
        if (piggybackService.isRiding(senderUuid)) {
            piggybackService.dismount(senderUuid, riderRef, riderStore);
            sendMessage(senderPlayerRef, "[Marriage] Dismounted.", "#9ad4ff");
            event.setCancelled(true);
            return;
        }

        PiggybackService.MountResult result = piggybackService.tryMount(senderUuid, riderRef, riderStore);
        switch (result) {
            case SUCCESS -> {
                sendMessage(senderPlayerRef, "[Marriage] You hop onto your spouse's back!", "#f2a2e8");
                event.setCancelled(true);
            }
            case ALREADY_MOUNTED, SPOUSE_ALREADY_CARRYING, SPOUSE_IS_RIDING ->
                sendMessage(senderPlayerRef, "[Marriage] Cannot piggyback right now.", "#ff9900");
            default -> {
                // TOO_FAR / NOT_MARRIED / SPOUSE_OFFLINE / ERROR — swallow,
                // interact event fires often enough that spamming chat is bad.
            }
        }
    }

    private void sendMessage(@javax.annotation.Nullable PlayerRef ref, @Nonnull String text, @Nonnull String color) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        ref.sendMessage(Message.raw(text).color(color));
    }

    private UUID resolveUuid(@Nonnull Ref<EntityStore> ref) {
        try {
            Store<EntityStore> store = ref.getStore();
            UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
            return uuidComponent != null ? uuidComponent.getUuid() : null;
        } catch (Exception ex) {
            LOGGER.atFiner().withCause(ex).log("Failed to resolve UUID for interact target.");
            return null;
        }
    }
}
