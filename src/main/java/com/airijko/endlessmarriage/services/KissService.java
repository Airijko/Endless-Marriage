/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessmarriage.services;

import com.airijko.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessmarriage.data.MarriageDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Handles initiating a kiss between married partners — proximity check
 * (configurable, default 1 block) and heart-particle emission at the
 * midpoint between the two partners.
 */
public final class KissService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    /** Vanilla heart particle system id (see Test_Particle_Emotions.json). */
    private static final String HEART_PARTICLE_ID = "Hearts";

    /** Sound event id, see SFX_EM_Kiss.json. */
    private static final String KISS_SOUND_ID = "SFX_EM_Kiss";

    private final MarriageDataManager dataManager;
    private final MarriageConfig config;
    private final KissBuffService buffService;

    public KissService(@Nonnull MarriageDataManager dataManager,
            @Nonnull MarriageConfig config,
            @Nonnull KissBuffService buffService) {
        this.dataManager = dataManager;
        this.config = config;
        this.buffService = buffService;
    }

    public enum KissResult {
        SUCCESS,
        NOT_MARRIED,
        SPOUSE_OFFLINE,
        SPOUSE_NOT_IN_WORLD,
        SPOUSE_DIFFERENT_WORLD,
        TOO_FAR,
        ERROR
    }

    /**
     * Attempts to initiate a kiss from {@code initiatorUuid} to their spouse.
     */
    @Nonnull
    public KissResult tryKiss(@Nonnull UUID initiatorUuid,
            @Nonnull Ref<EntityStore> initiatorRef,
            @Nonnull Store<EntityStore> initiatorStore) {

        if (!dataManager.isMarried(initiatorUuid)) {
            return KissResult.NOT_MARRIED;
        }
        UUID spouseUuid = dataManager.getSpouse(initiatorUuid);
        if (spouseUuid == null) {
            return KissResult.NOT_MARRIED;
        }

        PlayerRef spousePlayer = Universe.get().getPlayer(spouseUuid);
        if (spousePlayer == null || !spousePlayer.isValid()) {
            return KissResult.SPOUSE_OFFLINE;
        }
        Ref<EntityStore> spouseRef = spousePlayer.getReference();
        if (spouseRef == null) {
            return KissResult.SPOUSE_NOT_IN_WORLD;
        }
        Store<EntityStore> spouseStore = spouseRef.getStore();
        if (spouseStore != initiatorStore) {
            return KissResult.SPOUSE_DIFFERENT_WORLD;
        }

        Vector3d initiatorPos = positionOf(initiatorRef, initiatorStore);
        Vector3d spousePos = positionOf(spouseRef, spouseStore);
        if (initiatorPos == null || spousePos == null) {
            return KissResult.ERROR;
        }
        double dx = initiatorPos.x() - spousePos.x();
        double dy = initiatorPos.y() - spousePos.y();
        double dz = initiatorPos.z() - spousePos.z();
        double distSq = (dx * dx) + (dy * dy) + (dz * dz);
        double range = config.getKissRange();
        if (distSq > range * range) {
            return KissResult.TOO_FAR;
        }

        if (!performKissVisuals(initiatorPos, spousePos, initiatorStore,
                initiatorRef, spouseRef, initiatorUuid, spouseUuid)) {
            return KissResult.ERROR;
        }

        // Apply the temporary Discipline XP buff to both partners. The service
        // is a no-op for either player who is still on cooldown — kissing
        // remains free, but the buff itself is rate-limited.
        buffService.tryApply(initiatorUuid);
        buffService.tryApply(spouseUuid);

        return KissResult.SUCCESS;
    }

    /**
     * Debug-only kiss against an arbitrary target entity (e.g. an in-memory
     * NPC spawned by the debug commands). Skips marriage and proximity checks
     * — caller is responsible for ensuring the target is valid. The buff is
     * still applied to the initiator so the +10% Discipline path can be tested.
     */
    @Nonnull
    public KissResult tryKissTarget(@Nonnull UUID initiatorUuid,
            @Nonnull Ref<EntityStore> initiatorRef,
            @Nonnull Store<EntityStore> initiatorStore,
            @Nonnull Ref<EntityStore> targetRef) {

        Vector3d initiatorPos = positionOf(initiatorRef, initiatorStore);
        Vector3d targetPos = positionOf(targetRef, initiatorStore);
        if (initiatorPos == null || targetPos == null) {
            return KissResult.ERROR;
        }

        if (!performKissVisuals(initiatorPos, targetPos, initiatorStore,
                initiatorRef, targetRef, initiatorUuid, null)) {
            return KissResult.ERROR;
        }

        buffService.tryApply(initiatorUuid);
        return KissResult.SUCCESS;
    }

    /**
     * Spawns the heart particle effect at the midpoint between the two
     * positions and plays the kiss SFX for the initiator (and the partner if
     * they have a real entity ref).
     */
    private boolean performKissVisuals(@Nonnull Vector3d initiatorPos,
            @Nonnull Vector3d partnerPos,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> initiatorRef,
            @Nonnull Ref<EntityStore> partnerRef,
            @Nonnull UUID initiatorUuid,
            @Nullable UUID partnerUuid) {

        double midX = (initiatorPos.x() + partnerPos.x()) * 0.5;
        double midY = ((initiatorPos.y() + partnerPos.y()) * 0.5) + 1.8;
        double midZ = (initiatorPos.z() + partnerPos.z()) * 0.5;
        Vector3d midpoint = new Vector3d(midX, midY, midZ);

        try {
            ParticleUtil.spawnParticleEffect(HEART_PARTICLE_ID, midpoint, store);
        } catch (Exception ex) {
            LOGGER.atWarning().withCause(ex).log("Failed to spawn heart particles for kiss between %s and %s.",
                    initiatorUuid, partnerUuid);
            return false;
        }

        // Play the kiss SFX as a 3D positional sound at the midpoint so
        // everyone within ~25 blocks can hear it.
        playKissSound3d(midpoint, store);
        return true;
    }

    private void playKissSound3d(@Nonnull Vector3d position, @Nonnull Store<EntityStore> store) {
        try {
            int soundIndex = SoundEvent.getAssetMap().getIndex(KISS_SOUND_ID);
            if (soundIndex == Integer.MIN_VALUE || soundIndex == 0) {
                return;
            }
            SoundUtil.playSoundEvent3d(null, soundIndex, position, store);
        } catch (Exception ex) {
            LOGGER.atFiner().withCause(ex).log("Failed to play kiss sound effect.");
        }
    }

    @Nullable
    private Vector3d positionOf(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store) {
        if (ref == null || store == null) {
            return null;
        }
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            return transform != null ? transform.getPosition() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
