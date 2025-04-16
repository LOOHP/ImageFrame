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

package com.loohp.imageframe.nms;

import com.loohp.imageframe.objectholders.CombinedMapItemInfo;
import com.loohp.imageframe.objectholders.MutablePair;
import net.kyori.adventure.key.Key;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public abstract class NMSWrapper {

    public static final int COLOR_ARRAY_LENGTH = 16384;
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public abstract void setColors(MapView mapView, byte[] colors);

    public abstract Collection<Player> getViewers(MapView mapView);

    public abstract boolean hasViewers(MapView mapView);

    public abstract Object toNMSMapIcon(MapCursor mapCursor);

    public abstract Object toNMSMapIconType(MapCursor.Type type);

    public abstract boolean isRenderOnFrame(MapCursor.Type type);

    public abstract int getNextAvailableMapId(World world);

    public abstract MapView getMapOrCreateMissing(World world, int id);

    public abstract MutablePair<byte[], List<MapCursor>> bukkitRenderMap(MapView mapView, Player player);

    public abstract Set<Player> getEntityTrackers(Entity entity);

    public abstract Object createMapPacket(int mapId, byte[] colors, Collection<MapCursor> cursors);

    public abstract Object createItemFrameItemChangePacket(int entityId, ItemStack itemStack);

    public abstract Object createEntityFlagsPacket(Entity entity, Boolean invisible, Boolean glowing);

    public abstract void sendPacket(Player player, Object packet);

    public abstract CombinedMapItemInfo getCombinedMapItemInfo(ItemStack itemStack);

    public abstract ItemStack withCombinedMapItemInfo(ItemStack itemStack, CombinedMapItemInfo combinedMapItemInfo);

    public abstract ItemStack withInvisibleItemFrameMeta(ItemStack itemStack);

    public abstract List<ItemStack> giveItems(Player player, List<ItemStack> itemStacks);

    public abstract Key getWorldNamespacedKey(World world);

}
