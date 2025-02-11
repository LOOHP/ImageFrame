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

import com.loohp.imageframe.nms.NMS;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;

public class FakeItemUtils {

    public static void sendFakeItemChange(Player player, List<ItemFrameUpdateData> updateData) {
        List<Object> packets = new ArrayList<>(updateData.size());
        for (ItemFrameUpdateData itemFrameUpdateData : updateData) {
            packets.add(NMS.getInstance().createItemFrameItemChangePacket(itemFrameUpdateData.getEntityId(), itemFrameUpdateData.getItemStack()));
        }
        if (player.isOnline()) {
            for (Object packet : packets) {
                NMS.getInstance().sendPacket(player, packet);
            }
        }
    }

    public static void sendFakeItemChange(Player player, int entityId, ItemStack itemStack) {
        Object packet = NMS.getInstance().createItemFrameItemChangePacket(entityId, itemStack);
        if (player.isOnline()) {
            NMS.getInstance().sendPacket(player, packet);
        }
    }

    public static class ItemFrameUpdateData {

        private final int entityId;
        private final ItemStack itemStack;
        private final int realMapId;
        private final MapView mapView;
        private final int currentPosition;

        public ItemFrameUpdateData(int entityId, ItemStack itemStack, int realMapId, MapView mapView, int currentPosition) {
            this.entityId = entityId;
            this.itemStack = itemStack;
            this.realMapId = realMapId;
            this.mapView = mapView;
            this.currentPosition = currentPosition;
        }

        public int getEntityId() {
            return entityId;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public int getRealMapId() {
            return realMapId;
        }

        public MapView getMapView() {
            return mapView;
        }

        public int getCurrentPosition() {
            return currentPosition;
        }
    }

}
