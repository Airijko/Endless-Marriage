/*
 * Airijko Proprietary License
 *
 * Copyright (c) 2026 Airijko - Endless Marriage
 *
 * All rights reserved.
 */

package com.airijko.endlessleveling.endlessmarriage.ecs;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

import java.util.UUID;

/**
 * Per-player marriage state. Mirrors the persisted {@code MarriagePair} half that
 * applies to the entity owning this component. {@code MarriageDataManager} remains
 * canonical (UUID-keyed, disk-persisted); component is a per-tick mirror so
 * archetype-scan readers (proximity buffs, spouse-protection, piggyback follow)
 * can resolve partner state without touching the manager every tick.
 *
 * <p>Spouse UUID stored as two longs (msb / lsb) — same encoding as
 * {@code SummonOwnerComponent} in EL core.
 */
public class MarriageComponent implements Component<EntityStore> {

    private long spouseMsb;
    private long spouseLsb;
    private long marriedAtMillis;

    public MarriageComponent() {
    }

    @Nullable
    public UUID getSpouseUuid() {
        if (spouseMsb == 0L && spouseLsb == 0L) return null;
        return new UUID(spouseMsb, spouseLsb);
    }

    public void setSpouseUuid(@Nullable UUID uuid) {
        if (uuid == null) {
            spouseMsb = 0L;
            spouseLsb = 0L;
        } else {
            spouseMsb = uuid.getMostSignificantBits();
            spouseLsb = uuid.getLeastSignificantBits();
        }
    }

    public long getMarriedAtMillis() {
        return marriedAtMillis;
    }

    public void setMarriedAtMillis(long v) {
        this.marriedAtMillis = v;
    }

    public boolean isMarried() {
        return spouseMsb != 0L || spouseLsb != 0L;
    }

    @Nullable
    public Component<EntityStore> clone() {
        MarriageComponent c = new MarriageComponent();
        c.spouseMsb = this.spouseMsb;
        c.spouseLsb = this.spouseLsb;
        c.marriedAtMillis = this.marriedAtMillis;
        return c;
    }

    public static final BuilderCodec<MarriageComponent> CODEC =
            BuilderCodec.builder(MarriageComponent.class, MarriageComponent::new)
                    .addField(new KeyedCodec<>("Spouse_Msb", Codec.LONG),
                            (c, v) -> c.spouseMsb = v, c -> c.spouseMsb)
                    .addField(new KeyedCodec<>("Spouse_Lsb", Codec.LONG),
                            (c, v) -> c.spouseLsb = v, c -> c.spouseLsb)
                    .addField(new KeyedCodec<>("Married_At_Millis", Codec.LONG),
                            (c, v) -> c.marriedAtMillis = v, c -> c.marriedAtMillis)
                    .build();

    private static volatile ComponentType<EntityStore, MarriageComponent> componentType;

    public static ComponentType<EntityStore, MarriageComponent> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<EntityStore, MarriageComponent> type) {
        componentType = type;
    }
}
