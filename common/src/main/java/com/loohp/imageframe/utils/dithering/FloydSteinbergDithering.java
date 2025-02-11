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

package com.loohp.imageframe.utils.dithering;

import com.loohp.imageframe.utils.MapUtils;
import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.LinkedHashSet;
import java.util.Set;

@SuppressWarnings("removal")
public class FloydSteinbergDithering {

    private static final C3[] PALETTE;

    static {
        Set<Byte> bytes = new LinkedHashSet<>();
        for (int i = 0; i < 16777216; i++) {
            bytes.add(MapPalette.matchColor(new Color(i)));
        }
        Set<C3> values = new LinkedHashSet<>();
        for (byte b : bytes) {
            values.add(new C3(MapPalette.getColor(b)));
        }
        PALETTE = values.toArray(new C3[0]);
    }

    static class C3 {

        private final int r;
        private final int g;
        private final int b;
        private final boolean transparent;

        public C3(Color color) {
            this(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() < 128);
        }

        public C3(int r, int g, int b) {
            this(r, g, b, false);
        }

        public C3(int r, int g, int b, boolean transparent) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.transparent = transparent;
        }

        public C3 add(C3 o) {
            return new C3(r + o.r, g + o.g, b + o.b, transparent);
        }

        public int diff(C3 o) {
            int rDiff = o.r - r;
            int gDiff = o.g - g;
            int bDiff = o.b - b;
            return rDiff * rDiff + gDiff * gDiff + bDiff * bDiff;
        }

        public C3 mul(double d) {
            return new C3((int) (d * r), (int) (d * g), (int) (d * b), transparent);
        }

        public C3 sub(C3 o) {
            return new C3(r - o.r, g - o.g, b - o.b, transparent);
        }

        public boolean isTransparent() {
            return transparent;
        }
    }

    private static C3 findClosestPaletteColor(C3 c) {
        C3 closest = PALETTE[0];
        for (C3 n : PALETTE) {
            if (n.diff(c) < closest.diff(c)) {
                closest = n;
            }
        }
        return closest;
    }

    private static int clampColor(int c) {
        return Math.max(0, Math.min(255, c));
    }

    public static byte[] floydSteinbergDithering(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        C3[][] d = new C3[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                d[y][x] = new C3(new Color(img.getRGB(x, y), true));
            }
        }

        byte[] result = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                C3 oldColor = d[y][x];
                if (oldColor.isTransparent()) {
                    result[y * w + x] = MapUtils.PALETTE_TRANSPARENT;
                } else {
                    C3 newColor = findClosestPaletteColor(oldColor);
                    result[y * w + x] = MapPalette.matchColor(clampColor(newColor.r), clampColor(newColor.g), clampColor(newColor.b));
                    C3 err = oldColor.sub(newColor);
                    if (x + 1 < w) {
                        d[y][x + 1] = d[y][x + 1].add(err.mul(7.0 / 16));
                    }
                    if (x - 1 >= 0 && y + 1 < h) {
                        d[y + 1][x - 1] = d[y + 1][x - 1].add(err.mul(3.0 / 16));
                    }
                    if (y + 1 < h) {
                        d[y + 1][x] = d[y + 1][x].add(err.mul(5.0 / 16));
                    }
                    if (x + 1 < w && y + 1 < h) {
                        d[y + 1][x + 1] = d[y + 1][x + 1].add(err.mul(1.0 / 16));
                    }
                }
            }
        }

        return result;
    }

}
