/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2024. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2024. Contributors
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

import org.bukkit.entity.ItemFrame;

public class ImageMapHitTargetResult {

    private final ItemFrame itemFrame;
    private final ImageMap imageMap;
    private final IntPosition localTargetPixel;
    private final IntPosition targetPixel;

    public ImageMapHitTargetResult(ItemFrame itemFrame, ImageMap imageMap, IntPosition localTargetPixel, IntPosition targetPixel) {
        this.itemFrame = itemFrame;
        this.imageMap = imageMap;
        this.localTargetPixel = localTargetPixel;
        this.targetPixel = targetPixel;
    }

    public ItemFrame getItemFrame() {
        return itemFrame;
    }

    public ImageMap getImageMap() {
        return imageMap;
    }

    public IntPosition getLocalTargetPixel() {
        return localTargetPixel;
    }

    public IntPosition getTargetPixel() {
        return targetPixel;
    }

}
