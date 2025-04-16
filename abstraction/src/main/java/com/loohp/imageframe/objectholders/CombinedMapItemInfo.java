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

import java.util.UUID;

public class CombinedMapItemInfo {

    public static final String KEY = "CombinedImageMap";
    public static final String PLACEMENT_YAW_KEY = "CombinedImageMapPlacementYaw";
    public static final String PLACEMENT_UUID_KEY = "CombinedImageMapPlacementUUID";

    private final int imageMapIndex;
    private final PlacementInfo placementInfo;

    public CombinedMapItemInfo(int imageMapIndex, PlacementInfo placementInfo) {
        this.imageMapIndex = imageMapIndex;
        this.placementInfo = placementInfo;
    }

    public CombinedMapItemInfo(int imageMapIndex) {
        this(imageMapIndex, null);
    }

    public int getImageMapIndex() {
        return imageMapIndex;
    }

    public boolean hasPlacement() {
        return placementInfo != null;
    }

    public PlacementInfo getPlacement() {
        return placementInfo;
    }

    public static class PlacementInfo {

        private final float yaw;
        private final UUID uuid;

        public PlacementInfo(float yaw, UUID uuid) {
            this.yaw = yaw;
            this.uuid = uuid;
        }

        public float getYaw() {
            return yaw;
        }

        public UUID getUniqueId() {
            return uuid;
        }
    }

}
