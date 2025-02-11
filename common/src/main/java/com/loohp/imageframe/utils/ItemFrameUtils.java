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

import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.util.Vector;

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

    public static Vector getClosestCardinalDirection(float yaw) {
        double rotation = (yaw - 90.0F) % 360.0F;
        if (rotation < 0.0D) {
            rotation += 360.0D;
        }
        double alignedYaw;
        if ((0.0D <= rotation) && (rotation < 45.0D)) {
            alignedYaw = 90.0F;
        } else if ((45.0D <= rotation) && (rotation < 135.0D)) {
            alignedYaw = 180.0F;
        } else if ((135.0D <= rotation) && (rotation < 225.0D)) {
            alignedYaw = -90.0F;
        } else if ((225.0D <= rotation) && (rotation < 315.0D)) {
            alignedYaw = 0.0F;
        } else if ((315.0D <= rotation) && (rotation < 360.0D)) {
            alignedYaw = 90.0F;
        } else {
            alignedYaw = 0.0F;
        }
        return new Vector(-Math.sin(Math.toRadians(alignedYaw)), 0, Math.cos(Math.toRadians(alignedYaw)));
    }

}
