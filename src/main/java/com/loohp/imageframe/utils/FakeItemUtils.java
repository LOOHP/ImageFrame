/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2023. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2023. Contributors
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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import com.loohp.imageframe.ImageFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;

public class FakeItemUtils {

    public static final WrappedDataWatcher.Serializer ITEM_SERIALIZER = WrappedDataWatcher.Registry.getItemStackSerializer(false);

    public static void sendFakeItemChange(Player player, List<ItemFrameUpdateData> updateData) {
        List<PacketContainer> packets = new ArrayList<>(updateData.size());
        for (ItemFrameUpdateData itemFrameUpdateData : updateData) {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, itemFrameUpdateData.getItemFrame().getEntityId());
            WrappedDataWatcher watcher = new WrappedDataWatcher();
            switch (ImageFrame.version) {
                case V1_19_3:
                case V1_19:
                case V1_18_2:
                case V1_18:
                case V1_17:
                    watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(8, ITEM_SERIALIZER), itemFrameUpdateData.getItemStack());
                    break;
                case V1_16_4:
                case V1_16_2:
                case V1_16:
                    watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(7, ITEM_SERIALIZER), itemFrameUpdateData.getItemStack());
                    break;
            }
            writeMetadataPacket(packet, watcher);
            packets.add(packet);
        }
        if (player.isOnline()) {
            for (PacketContainer packet : packets) {
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
            }
        }
    }

    private static WrappedDataWatcher fromDataValueList(List<WrappedDataValue> dataValues) {
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        for (WrappedDataValue dataValue : dataValues) {
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(dataValue.getIndex(), dataValue.getSerializer()), dataValue.getRawValue());
        }
        return watcher;
    }

    private static List<WrappedDataValue> toDataValueList(WrappedDataWatcher wrappedDataWatcher) {
        List<WrappedWatchableObject> watchableObjectList = wrappedDataWatcher.getWatchableObjects();
        List<WrappedDataValue> wrappedDataValues = new ArrayList<>(watchableObjectList.size());
        for (WrappedWatchableObject wrappedWatchableObject : wrappedDataWatcher.getWatchableObjects()) {
            WrappedDataWatcher.WrappedDataWatcherObject wrappedDataWatcherObject = wrappedWatchableObject.getWatcherObject();
            wrappedDataValues.add(new WrappedDataValue(wrappedDataWatcherObject.getIndex(), wrappedDataWatcherObject.getSerializer(), wrappedWatchableObject.getRawValue()));
        }
        return wrappedDataValues;
    }

    private static WrappedDataWatcher fromMetadataPacket(PacketContainer packet) {
        if (ImageFrame.version.isNewerOrEqualTo(MCVersion.V1_19_3)) {
            List<WrappedDataValue> data = packet.getDataValueCollectionModifier().read(0);
            return fromDataValueList(data);
        } else {
            List<WrappedWatchableObject> data = packet.getWatchableCollectionModifier().read(0);
            return new WrappedDataWatcher(data);
        }
    }

    private static void writeMetadataPacket(PacketContainer packet, WrappedDataWatcher watcher) {
        if (ImageFrame.version.isNewerOrEqualTo(MCVersion.V1_19_3)) {
            packet.getDataValueCollectionModifier().write(0, toDataValueList(watcher));
        } else {
            packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
        }
    }

    public static class ItemFrameUpdateData {

        private final ItemFrame itemFrame;
        private final ItemStack itemStack;
        private final int realMapId;
        private final MapView mapView;

        public ItemFrameUpdateData(ItemFrame itemFrame, ItemStack itemStack, int realMapId, MapView mapView) {
            this.itemFrame = itemFrame;
            this.itemStack = itemStack;
            this.realMapId = realMapId;
            this.mapView = mapView;
        }

        public ItemFrame getItemFrame() {
            return itemFrame;
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
    }

}
