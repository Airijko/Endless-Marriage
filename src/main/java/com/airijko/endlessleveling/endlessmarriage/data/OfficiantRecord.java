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
 * Tracks a priest or magistrate confirmation of a marriage or divorce.
 */
public record OfficiantRecord(UUID officiant, OfficiantType type, UUID player1, UUID player2, long timestamp) {

    public enum OfficiantType {
        MARRIAGE,
        DIVORCE
    }
}
