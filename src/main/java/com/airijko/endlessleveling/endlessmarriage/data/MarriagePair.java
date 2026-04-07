/*
 * Copyright (c) 2026 Airijko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
 */

package com.airijko.endlessleveling.endlessmarriage.data;

import java.util.UUID;

/**
 * Represents an active marriage between two players.
 */
public record MarriagePair(UUID player1, UUID player2, UUID officiant, long timestamp) {

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
