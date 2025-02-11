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

import com.loohp.imageframe.utils.dithering.FloydSteinbergDithering;
import org.bukkit.map.MapPalette;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class DitheringType {

    private static final Map<String, DitheringType> REGISTERED_TYPES = new LinkedHashMap<>();

    @SuppressWarnings("removal")
    public static final DitheringType NEAREST_COLOR = register(new DitheringType("nearest-color", image -> MapPalette.imageToBytes(image)));
    public static final DitheringType FLOYD_STEINBERG = register(new DitheringType("floyd-steinberg", image -> FloydSteinbergDithering.floydSteinbergDithering(image)));

    public static DitheringType register(DitheringType ditheringType) {
        REGISTERED_TYPES.put(ditheringType.getName(), ditheringType);
        return ditheringType;
    }

    public static Map<String, DitheringType> values() {
        return Collections.unmodifiableMap(REGISTERED_TYPES);
    }

    public static DitheringType fromName(String name) {
        return REGISTERED_TYPES.getOrDefault(name, NEAREST_COLOR);
    }

    public static DitheringType fromNameOrNull(String name) {
        return REGISTERED_TYPES.get(name);
    }

    private final String name;
    private final Function<BufferedImage, byte[]> applyDithering;

    public DitheringType(String name, Function<BufferedImage, byte[]> applyDithering) {
        this.name = name;
        this.applyDithering = applyDithering;
    }

    public String getName() {
        return name;
    }

    public byte[] applyDithering(BufferedImage image) {
        return applyDithering.apply(image);
    }

}
