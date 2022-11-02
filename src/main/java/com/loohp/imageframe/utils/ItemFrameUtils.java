/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2022. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2022. Contributors
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

import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;

public class ItemFrameUtils {

    public static boolean isOnWalls(ItemFrame itemFrame) {
        BlockFace blockFace = itemFrame.getAttachedFace();
        return !blockFace.equals(BlockFace.DOWN) && !blockFace.equals(BlockFace.UP);
    }

    public static boolean isOnCeiling(ItemFrame itemFrame) {
        return itemFrame.getAttachedFace().equals(BlockFace.UP);
    }

    public static Rotation getClosestMapRotation(float yaw) {
        yaw -= 180;
        while (yaw < 0) {
            yaw += 360F;
        }
        if (yaw >= 360) {
            yaw = yaw % 360;
        }
        Rotation rotation = Rotation.NONE;
        for (float i = 0; ; i += 90F) {
            float remaining =  yaw - i;
            if (remaining < 45F) {
                break;
            } else {
                rotation = rotation.rotateClockwise();
            }
        }
        return rotation;
    }

}
