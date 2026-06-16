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

import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class DitheringUtils {

    private static final Diffusion[] FLOYD_STEINBERG = {
            new Diffusion(1, 0, 7.0 / 16),
            new Diffusion(-1, 1, 3.0 / 16),
            new Diffusion(0, 1, 5.0 / 16),
            new Diffusion(1, 1, 1.0 / 16)
    };

    private static final Diffusion[] ATKINSON = {
            new Diffusion(1, 0, 1.0 / 8),
            new Diffusion(2, 0, 1.0 / 8),
            new Diffusion(-1, 1, 1.0 / 8),
            new Diffusion(0, 1, 1.0 / 8),
            new Diffusion(1, 1, 1.0 / 8),
            new Diffusion(0, 2, 1.0 / 8)
    };

    private static final Diffusion[] SIERRA_LITE = {
            new Diffusion(1, 0, 2.0 / 4),
            new Diffusion(-1, 1, 1.0 / 4),
            new Diffusion(0, 1, 1.0 / 4)
    };

    private static final int[][] BAYER_4X4 = {
            {0, 8, 2, 10},
            {12, 4, 14, 6},
            {3, 11, 1, 9},
            {15, 7, 13, 5}
    };

    private static final int[][] BAYER_8X8 = {
            {0, 48, 12, 60, 3, 51, 15, 63},
            {32, 16, 44, 28, 35, 19, 47, 31},
            {8, 56, 4, 52, 11, 59, 7, 55},
            {40, 24, 36, 20, 43, 27, 39, 23},
            {2, 50, 14, 62, 1, 49, 13, 61},
            {34, 18, 46, 30, 33, 17, 45, 29},
            {10, 58, 6, 54, 9, 57, 5, 53},
            {42, 26, 38, 22, 41, 25, 37, 21}
    };

    private static final double BAYER_STRENGTH = 64.0;

    private static class Diffusion {

        private final int x;
        private final int y;
        private final double weight;

        public Diffusion(int x, int y, double weight) {
            this.x = x;
            this.y = y;
            this.weight = weight;
        }
    }

    private static class RGB {

        private final double r;
        private final double g;
        private final double b;
        private final boolean transparent;

        public RGB(Color color) {
            this(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() < 128);
        }

        public RGB(double r, double g, double b, boolean transparent) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.transparent = transparent;
        }

        public RGB add(RGB o) {
            return new RGB(r + o.r, g + o.g, b + o.b, transparent);
        }

        public RGB add(double value) {
            return new RGB(r + value, g + value, b + value, transparent);
        }

        public RGB clamp() {
            return new RGB(clampColor(r), clampColor(g), clampColor(b), transparent);
        }

        public RGB mul(double d) {
            return new RGB(d * r, d * g, d * b, transparent);
        }

        public RGB sub(RGB o) {
            return new RGB(r - o.r, g - o.g, b - o.b, transparent);
        }

        public int getRed() {
            return toColorInt(r);
        }

        public int getGreen() {
            return toColorInt(g);
        }

        public int getBlue() {
            return toColorInt(b);
        }

        public boolean isTransparent() {
            return transparent;
        }
    }

    private static double clampColor(double c) {
        return Math.max(0, Math.min(255, c));
    }

    private static int toColorInt(double c) {
        return (int) Math.round(clampColor(c));
    }

    @SuppressWarnings("removal")
    private static byte toMapColor(RGB color) {
        return MapPalette.matchColor(color.getRed(), color.getGreen(), color.getBlue());
    }

    @SuppressWarnings("removal")
    private static RGB toRGB(byte color) {
        return new RGB(MapPalette.getColor(color));
    }

    public static byte[] floydSteinbergDithering(BufferedImage img) {
        return errorDiffusionDithering(img, FLOYD_STEINBERG);
    }

    public static byte[] atkinsonDithering(BufferedImage img) {
        return errorDiffusionDithering(img, ATKINSON);
    }

    public static byte[] sierraLiteDithering(BufferedImage img) {
        return errorDiffusionDithering(img, SIERRA_LITE);
    }

    public static byte[] orderedBayer4x4Dithering(BufferedImage img) {
        return orderedBayerDithering(img, BAYER_4X4);
    }

    public static byte[] orderedBayer8x8Dithering(BufferedImage img) {
        return orderedBayerDithering(img, BAYER_8X8);
    }

    private static byte[] errorDiffusionDithering(BufferedImage img, Diffusion[] diffusions) {
        int w = img.getWidth();
        int h = img.getHeight();

        RGB[][] d = new RGB[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                d[y][x] = new RGB(new Color(img.getRGB(x, y), true));
            }
        }

        byte[] result = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                RGB oldColor = d[y][x];
                if (oldColor.isTransparent()) {
                    result[y * w + x] = MapUtils.PALETTE_TRANSPARENT;
                } else {
                    RGB quantizedInput = oldColor.clamp();
                    byte mapColor = toMapColor(quantizedInput);
                    RGB newColor = toRGB(mapColor);
                    result[y * w + x] = mapColor;
                    RGB err = quantizedInput.sub(newColor);
                    for (Diffusion diffusion : diffusions) {
                        int targetX = x + diffusion.x;
                        int targetY = y + diffusion.y;
                        if (targetX >= 0 && targetX < w && targetY >= 0 && targetY < h) {
                            d[targetY][targetX] = d[targetY][targetX].add(err.mul(diffusion.weight));
                        }
                    }
                }
            }
        }

        return result;
    }

    private static byte[] orderedBayerDithering(BufferedImage img, int[][] matrix) {
        int w = img.getWidth();
        int h = img.getHeight();
        int matrixSize = matrix.length;
        int matrixArea = matrixSize * matrixSize;

        byte[] result = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                RGB color = new RGB(new Color(img.getRGB(x, y), true));
                if (color.isTransparent()) {
                    result[y * w + x] = MapUtils.PALETTE_TRANSPARENT;
                } else {
                    double threshold = ((matrix[y % matrixSize][x % matrixSize] + 0.5) / matrixArea) - 0.5;
                    result[y * w + x] = toMapColor(color.add(threshold * BAYER_STRENGTH).clamp());
                }
            }
        }

        return result;
    }

}
