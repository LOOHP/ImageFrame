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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class ImageUtils {

    public static BufferedImage resizeImageFillWidth(BufferedImage source, int width) {
        int height = (int) Math.round(source.getHeight() * ((double) width / (double) source.getWidth()));
        return resizeImageAbs(source, width, height);
    }

    public static BufferedImage resizeImageAbs(BufferedImage source, int width, int height) {
        BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return b;
    }

    public static BufferedImage copy(BufferedImage source) {
        BufferedImage b = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return b;
    }

}
