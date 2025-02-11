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

package com.loohp.imageframe.objectholders;

import java.util.Arrays;
import java.util.Objects;

public class IntRange {

    public static IntRange of(String input) {
        String[] parts = Arrays.stream(input.split("-")).map(s -> s.trim()).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (parts.length == 1) {
            return of(Integer.parseInt(parts[0]));
        } else if (parts.length == 2) {
            return of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        throw new IllegalArgumentException("Invalid range format: " + input);
    }

    public static IntRange of(int value) {
        return new IntRange(value, value);
    }

    public static IntRange of(int start, int end) {
        return start <= end ? new IntRange(start, end) : new IntRange(end, start);
    }

    private final int start;
    private final int end;

    private IntRange(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public boolean satisfies(int value) {
        return start <= value && value <= end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntRange intRange = (IntRange) o;
        return start == intRange.start && end == intRange.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
