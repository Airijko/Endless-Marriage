/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.systems;

import com.airijko.endlessleveling.endlessmarriage.config.MarriageConfig;
import com.airijko.endlessleveling.endlessmarriage.services.PiggybackService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Damage event system that applies a multiplicative damage reduction to
 * players currently participating in a piggyback session with their spouse.
 *
 * <p>Runs BEFORE {@link DamageSystems.ApplyDamage} so the modified amount
 * is what gets applied. Reduction is multiplicative, so it stacks safely
 * with EndlessLeveling's existing defense reduction without ever pushing
 * the result toward invulnerability — e.g. EL 50% defense + Marriage 25%
 * leaves 100 * 0.5 * 0.75 = 37.5 damage taken.
 */
public class SpouseProtectionSystem extends DamageEventSystem {

    private final PiggybackService piggybackService;
    private final MarriageConfig config;

    public SpouseProtectionSystem(@Nonnull PiggybackService piggybackService,
            @Nonnull MarriageConfig config) {
        this.piggybackService = piggybackService;
        this.config = config;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class));
    }

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        PlayerRef defenderPlayer = commandBuffer.getComponent(targetRef, PlayerRef.getComponentType());
        if (defenderPlayer == null || !defenderPlayer.isValid()) {
            return;
        }

        if (!piggybackService.isInActivePiggyback(defenderPlayer.getUuid())) {
            return;
        }

        double reductionPercent = config.getPiggybackDamageReductionPercent();
        if (reductionPercent <= 0.0) {
            return;
        }
        double multiplier = 1.0 - (reductionPercent / 100.0);
        if (multiplier < 0.0) {
            multiplier = 0.0;
        }

        float current = damage.getAmount();
        if (current <= 0f) {
            return;
        }
        damage.setAmount((float) (current * multiplier));
    }
}
