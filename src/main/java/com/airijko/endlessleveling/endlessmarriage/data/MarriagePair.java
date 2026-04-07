/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.data;

import java.util.List;
import java.util.UUID;

/**
 * Represents an active marriage between two players.
 *
 * <p>{@code witnesses} contains every player that was within the witness
 * radius of the priest at officiation time, in the order they were collected.
 * The list does not include the priest, the bride, or the groom.
 */
public record MarriagePair(UUID player1, UUID player2, UUID officiant, long timestamp, List<UUID> witnesses) {

    public MarriagePair {
        witnesses = witnesses == null ? List.of() : List.copyOf(witnesses);
    }

    /** Convenience constructor for marriages with no recorded witnesses (e.g. legacy data, debug previews). */
    public MarriagePair(UUID player1, UUID player2, UUID officiant, long timestamp) {
        this(player1, player2, officiant, timestamp, List.of());
    }

    /**
     * Returns the spouse UUID for a given player, or null if the player is not part of this marriage.
     */
    public UUID getSpouse(UUID player) {
        if (player1.equals(player)) {
            return player2;
        }
        if (player2.equals(player)) {
            return player1;
        }
        return null;
    }

    /**
     * Check if the given player is part of this marriage.
     */
    public boolean involves(UUID player) {
        return player1.equals(player) || player2.equals(player);
    }
}
