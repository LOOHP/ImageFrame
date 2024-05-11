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

package com.loohp.imageframe.nms;

import com.loohp.imageframe.objectholders.CombinedMapItemInfo;
import com.loohp.imageframe.objectholders.MutablePair;
import com.loohp.imageframe.utils.UUIDUtils;
import net.minecraft.server.v1_16_R2.ChunkProviderServer;
import net.minecraft.server.v1_16_R2.DataWatcher;
import net.minecraft.server.v1_16_R2.DataWatcherObject;
import net.minecraft.server.v1_16_R2.EntityHuman;
import net.minecraft.server.v1_16_R2.EntityItemFrame;
import net.minecraft.server.v1_16_R2.EntityPlayer;
import net.minecraft.server.v1_16_R2.IChatBaseComponent;
import net.minecraft.server.v1_16_R2.ItemWorldMap;
import net.minecraft.server.v1_16_R2.MapIcon;
import net.minecraft.server.v1_16_R2.NBTTagCompound;
import net.minecraft.server.v1_16_R2.Packet;
import net.minecraft.server.v1_16_R2.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_16_R2.PacketPlayOutMap;
import net.minecraft.server.v1_16_R2.PlayerChunkMap;
import net.minecraft.server.v1_16_R2.ResourceKey;
import net.minecraft.server.v1_16_R2.WorldMap;
import net.minecraft.server.v1_16_R2.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.bukkit.craftbukkit.v1_16_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R2.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_16_R2.map.CraftMapView;
import org.bukkit.craftbukkit.v1_16_R2.map.RenderData;
import org.bukkit.craftbukkit.v1_16_R2.util.CraftChatMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class V1_16_2 extends NMSWrapper {

    private final Field craftMapViewWorldMapField;
    private final Field nmsMapIconTypeDisplayField;
    private final Field[] nmsPacketPlayOutEntityMetadataFields;
    private final Field nmsItemFrameItemStackDataWatcherField;

    public V1_16_2() {
        try {
            craftMapViewWorldMapField = CraftMapView.class.getDeclaredField("worldMap");
            nmsMapIconTypeDisplayField = MapIcon.Type.class.getDeclaredField("C");
            nmsPacketPlayOutEntityMetadataFields = PacketPlayOutEntityMetadata.class.getDeclaredFields();
            nmsItemFrameItemStackDataWatcherField = EntityItemFrame.class.getDeclaredField("ITEM");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public WorldMap getWorldMap(MapView mapView) {
        try {
            CraftMapView craftMapView = (CraftMapView) mapView;
            craftMapViewWorldMapField.setAccessible(true);
            return (WorldMap) craftMapViewWorldMapField.get(craftMapView);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setColors(MapView mapView, byte[] colors) {
        if (colors.length != COLOR_ARRAY_LENGTH) {
            throw new IllegalArgumentException("colors array length must be 16384");
        }
        WorldMap nmsWorldMap = getWorldMap(mapView);
        nmsWorldMap.colors = colors;
    }

    @Override
    public Set<Player> getViewers(MapView mapView) {
        WorldMap nmsWorldMap = getWorldMap(mapView);
        Map<EntityHuman, WorldMap.WorldMapHumanTracker> humansMap = nmsWorldMap.humans;
        return humansMap.keySet().stream().map(e -> (Player) e).collect(Collectors.toSet());
    }

    @Override
    public MapIcon toNMSMapIcon(MapCursor mapCursor) {
        MapIcon.Type mapIconType = toNMSMapIconType(mapCursor.getType());
        IChatBaseComponent iChat = CraftChatMessage.fromStringOrNull(mapCursor.getCaption());
        return new MapIcon(mapIconType, mapCursor.getX(), mapCursor.getY(), mapCursor.getDirection(), iChat);
    }

    @SuppressWarnings("deprecation")
    @Override
    public MapIcon.Type toNMSMapIconType(MapCursor.Type type) {
        return MapIcon.Type.a(type.getValue());
    }

    @Override
    public boolean isRenderOnFrame(MapCursor.Type type) {
        MapIcon.Type mapIconType = toNMSMapIconType(type);
        try {
            nmsMapIconTypeDisplayField.setAccessible(true);
            return nmsMapIconTypeDisplayField.getBoolean(mapIconType);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public MapView getMapOrCreateMissing(World world, int id) {
        MapView mapView = Bukkit.getMap(id);
        if (mapView != null) {
            return mapView;
        }
        Location spawnLocation = world.getSpawnLocation();
        WorldServer worldServer = ((CraftWorld) world).getHandle();
        ResourceKey<net.minecraft.server.v1_16_R2.World> worldTypeKey = worldServer.getDimensionKey();
        String mapId = ItemWorldMap.a(id);
        WorldMap worldMap = new WorldMap(mapId);
        worldMap.a((int) spawnLocation.getX(), (int) spawnLocation.getZ(), 3, false, false, worldTypeKey);
        worldServer.a(worldMap);
        return Bukkit.getMap(id);
    }

    @Override
    public MutablePair<byte[], ArrayList<MapCursor>> bukkitRenderMap(MapView mapView, Player player) {
        CraftMapView craftMapView = (CraftMapView) mapView;
        CraftPlayer craftPlayer = (CraftPlayer) player;
        RenderData renderData = craftMapView.render(craftPlayer);
        return new MutablePair<>(renderData.buffer, renderData.cursors);
    }

    @Override
    public Set<Player> getEntityTrackers(Entity entity) {
        WorldServer worldServer = ((CraftWorld) entity.getWorld()).getHandle();
        ChunkProviderServer chunkProviderServer = worldServer.getChunkProvider();
        PlayerChunkMap playerChunkMap = chunkProviderServer.playerChunkMap;
        Int2ObjectMap<PlayerChunkMap.EntityTracker> entityTrackers = playerChunkMap.trackedEntities;
        PlayerChunkMap.EntityTracker entityTracker = entityTrackers.get(entity.getEntityId());
        if (entityTracker == null) {
            return Collections.emptySet();
        } else {
            Set<Player> players = new HashSet<>();
            for (EntityPlayer player : entityTracker.trackedPlayers) {
                players.add(player.getBukkitEntity());
            }
            return players;
        }
    }

    @Override
    public PacketPlayOutMap createMapPacket(int mapId, byte[] colors, Collection<MapCursor> cursors) {
        List<MapIcon> mapIcons = cursors == null ? Collections.emptyList() : cursors.stream().map(this::toNMSMapIcon).collect(Collectors.toList());
        return new PacketPlayOutMap(mapId, (byte) 0, false, false, mapIcons, colors == null ? null : EMPTY_BYTE_ARRAY, 0, 0, 128, 128);
    }

    @SuppressWarnings("unchecked")
    @Override
    public PacketPlayOutEntityMetadata createItemFrameItemChangePacket(ItemFrame itemFrame, ItemStack itemStack) {
        try {
            nmsItemFrameItemStackDataWatcherField.setAccessible(true);
            DataWatcherObject<net.minecraft.server.v1_16_R2.ItemStack> dataWatcherObject = (DataWatcherObject<net.minecraft.server.v1_16_R2.ItemStack>) nmsItemFrameItemStackDataWatcherField.get(null);
            List<DataWatcher.Item<?>> dataWatchers = Collections.singletonList(new DataWatcher.Item<>(dataWatcherObject, CraftItemStack.asNMSCopy(itemStack)));
            PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata();
            nmsPacketPlayOutEntityMetadataFields[0].setAccessible(true);
            nmsPacketPlayOutEntityMetadataFields[0].setInt(packet, itemFrame.getEntityId());
            nmsPacketPlayOutEntityMetadataFields[1].setAccessible(true);
            nmsPacketPlayOutEntityMetadataFields[1].set(packet, dataWatchers);
            return packet;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket((Packet<?>) packet);
    }

    @Override
    public CombinedMapItemInfo getCombinedMapItemInfo(ItemStack itemStack) {
        net.minecraft.server.v1_16_R2.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tag = nmsItemStack.getTag();
        if (tag == null || !tag.hasKey(CombinedMapItemInfo.KEY)) {
            return null;
        }
        int imageMapIndex = tag.getInt(CombinedMapItemInfo.KEY);
        if (!tag.hasKey(CombinedMapItemInfo.PLACEMENT_UUID_KEY) || !tag.hasKey(CombinedMapItemInfo.PLACEMENT_YAW_KEY)) {
            return new CombinedMapItemInfo(imageMapIndex);
        }
        float yaw = tag.getFloat(CombinedMapItemInfo.PLACEMENT_YAW_KEY);
        UUID uuid = UUIDUtils.fromIntArray(tag.getIntArray(CombinedMapItemInfo.PLACEMENT_YAW_KEY));
        return new CombinedMapItemInfo(imageMapIndex, new CombinedMapItemInfo.PlacementInfo(yaw, uuid));
    }

    @Override
    public ItemStack withCombinedMapItemInfo(ItemStack itemStack, CombinedMapItemInfo combinedMapItemInfo) {
        net.minecraft.server.v1_16_R2.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tag = nmsItemStack.getOrCreateTag();
        tag.setInt(CombinedMapItemInfo.KEY, combinedMapItemInfo.getImageMapIndex());
        if (combinedMapItemInfo.hasPlacement()) {
            CombinedMapItemInfo.PlacementInfo placement = combinedMapItemInfo.getPlacement();
            tag.setFloat(CombinedMapItemInfo.PLACEMENT_YAW_KEY, placement.getYaw());
            tag.setIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY, UUIDUtils.toIntArray(placement.getUniqueId()));
        }
        return CraftItemStack.asCraftMirror(nmsItemStack);
    }
}
