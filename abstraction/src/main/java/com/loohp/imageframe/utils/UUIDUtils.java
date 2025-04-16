/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2025. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2025. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.loohp.imageframe.utils;

import java.util.UUID;

public class UUIDUtils {

    public static UUID fromIntArray(int[] array) {
        if (array.length != 4) {
            throw new IllegalArgumentException("array length is not 4");
        }
        return new UUID((long) array[0] << 32 | (long) array[1] & 4294967295L, (long) array[2] << 32 | (long) array[3] & 4294967295L);
    }

    public static int[] toIntArray(UUID uuid) {
        long high = uuid.getMostSignificantBits();
        long low = uuid.getLeastSignificantBits();
        return new int[] {(int) (high >>> 32), (int) high, (int) (high >>> low), (int) low};
    }

}
