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

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.nms.NMS;
import com.loohp.imageframe.objectholders.FilledMapItemInfo;
import com.loohp.imageframe.objectholders.ImageMap;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

public class ImageFilledMapUtils {

    public static void processImageFilledMaps(SlotAccessor slotAccess, int size) {
        processImageFilledMaps(slotAccess, 0, size);
    }

    public static void processImageFilledMaps(SlotAccessor slotAccess, int begin, int size) {
        for (int i = begin; i < size; i++) {
            ItemStack replacement = processImageFilledMap(slotAccess.get(i));
            if (replacement != null) {
                slotAccess.set(i, replacement);
            }
        }
    }

    public static ItemStack processImageFilledMap(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        if (!itemStack.getType().equals(Material.FILLED_MAP)) {
            return null;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (!(itemMeta instanceof MapMeta)) {
            return null;
        }
        MapMeta mapMeta = (MapMeta) itemMeta;
        MapView mapView = mapMeta.hasMapView() ? mapMeta.getMapView() : null;
        FilledMapItemInfo info = NMS.getInstance().getFilledMapItemInfo(itemStack);
        if (info == null) {
            if (mapView != null) {
                ImageMap imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                if (imageMap != null) {
                    int imageIndex = imageMap.getImageIndex();
                    int partIndex = imageMap.getMapIds().indexOf(mapView.getId());
                    if (partIndex >= 0) {
                        return NMS.getInstance().withFilledMapItemInfo(itemStack, new FilledMapItemInfo(imageIndex, partIndex));
                    }
                }
            }
        } else {
            ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(info.getImageMapIndex());
            MapView correctMapView = imageMap.getMapViews().get(info.getMapPartIndex());
            if (mapView == null || correctMapView.getId() != mapView.getId()) {
                mapMeta.setMapView(correctMapView);
                itemStack.setItemMeta(mapMeta);
            }
        }
        return null;
    }

}
