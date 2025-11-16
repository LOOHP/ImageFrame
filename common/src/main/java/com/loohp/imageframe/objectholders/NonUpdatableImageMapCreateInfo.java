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

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

public class NonUpdatableImageMapCreateInfo extends ImageMapCreateInfo {

    private final BufferedImage[] images;
    private final List<Integer> mapIds;

    public NonUpdatableImageMapCreateInfo(ImageMapManager manager, String name, BufferedImage[] images, List<Integer> mapIds, int width, int height, DitheringType ditheringType, UUID creator) {
        super(manager, name, width, height, ditheringType, creator);
        this.images = images;
        this.mapIds = mapIds;
    }

    public NonUpdatableImageMapCreateInfo(ImageMapManager manager, String name, BufferedImage[] images, int width, int height, DitheringType ditheringType, UUID creator) {
        this(manager, name, images, null, width, height, ditheringType, creator);
    }

    public BufferedImage[] getImages() {
        return images;
    }

    public List<Integer> getMapIds() {
        return mapIds;
    }
}
