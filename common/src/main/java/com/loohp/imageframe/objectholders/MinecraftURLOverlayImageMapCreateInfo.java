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

import org.bukkit.map.MapView;

import java.util.List;
import java.util.UUID;

public class MinecraftURLOverlayImageMapCreateInfo extends URLImageMapCreateInfo {

    private final List<MapView> mapViews;

    public MinecraftURLOverlayImageMapCreateInfo(ImageMapManager manager, String name, String url, List<MapView> mapViews, int width, int height, DitheringType ditheringType, UUID creator) {
        super(manager, name, url, width, height, ditheringType, creator);
        this.mapViews = mapViews;
    }

    public List<MapView> getMapViews() {
        return mapViews;
    }
}
