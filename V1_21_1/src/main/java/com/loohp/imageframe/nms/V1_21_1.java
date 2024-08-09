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
import com.loohp.imageframe.utils.ReflectionUtils;
import com.loohp.imageframe.utils.UUIDUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.protocol.game.PacketPlayOutMap;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.decoration.EntityItemFrame;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapIcon;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.PersistentIdCounts;
import net.minecraft.world.level.saveddata.maps.WorldMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_21_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_21_R1.map.CraftMapCursor;
import org.bukkit.craftbukkit.v1_21_R1.map.CraftMapView;
import org.bukkit.craftbukkit.v1_21_R1.map.RenderData;
import org.bukkit.craftbukkit.v1_21_R1.util.CraftChatMessage;
import org.bukkit.entity.Entity;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class V1_21_1 extends NMSWrapper {

    private final Field craftMapViewWorldMapField;
    private final Field persistentIdCountsUsedAuxIdsField;

    public V1_21_1() {
        try {
            craftMapViewWorldMapField = CraftMapView.class.getDeclaredField("worldMap");
            persistentIdCountsUsedAuxIdsField = ReflectionUtils.findDeclaredField(PersistentIdCounts.class, Object2IntMap.class, "usedAuxIds", "b");
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
            throw new IllegalArgumentException("colors array length must be " + COLOR_ARRAY_LENGTH);
        }
        WorldMap nmsWorldMap = getWorldMap(mapView);
        nmsWorldMap.g = colors;
    }

    @Override
    public Set<Player> getViewers(MapView mapView) {
        WorldMap nmsWorldMap = getWorldMap(mapView);
        Map<EntityHuman, WorldMap.WorldMapHumanTracker> humansMap = nmsWorldMap.p;
        return humansMap.keySet().stream().map(e -> (Player) e.getBukkitEntity()).collect(Collectors.toSet());
    }

    @Override
    public MapIcon toNMSMapIcon(MapCursor mapCursor) {
        Holder<MapDecorationType> decorationTypeHolder = toNMSMapIconType(mapCursor.getType());
        IChatBaseComponent iChat = CraftChatMessage.fromStringOrNull(mapCursor.getCaption());
        return new MapIcon(decorationTypeHolder, mapCursor.getX(), mapCursor.getY(), mapCursor.getDirection(), Optional.ofNullable(iChat));
    }

    @Override
    public Holder<MapDecorationType> toNMSMapIconType(MapCursor.Type type) {
        return CraftMapCursor.CraftType.bukkitToMinecraftHolder(type);
    }

    @Override
    public boolean isRenderOnFrame(MapCursor.Type type) {
        Holder<MapDecorationType> decorationTypeHolder = toNMSMapIconType(type);
        return decorationTypeHolder.a().c();
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @Override
    public MapView getMapOrCreateMissing(World world, int id) {
        try {
            MapView mapView = Bukkit.getMap(id);
            if (mapView != null) {
                return mapView;
            }
            persistentIdCountsUsedAuxIdsField.setAccessible(true);
            Location spawnLocation = world.getSpawnLocation();
            WorldServer worldServer = ((CraftWorld) world).getHandle();
            ResourceKey<net.minecraft.world.level.World> worldTypeKey = worldServer.af();
            WorldMap worldMap = WorldMap.a(spawnLocation.getX(), spawnLocation.getZ(), (byte) 3, false, false, worldTypeKey);
            MapId mapId = new MapId(id);
            worldServer.a(mapId, worldMap);
            PersistentIdCounts persistentIdCounts = worldServer.o().I().u().a(PersistentIdCounts.a(), PersistentIdCounts.a);
            Object2IntMap<String> usedAuxIds = (Object2IntMap<String>) persistentIdCountsUsedAuxIdsField.get(persistentIdCounts);
            int freeAuxValue = usedAuxIds.getInt("map");
            if (freeAuxValue < id) {
                usedAuxIds.put("map", id);
                persistentIdCounts.c();
            }
            return Bukkit.getMap(id);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
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
        return new HashSet<>(entity.getTrackedBy());
    }

    @Override
    public PacketPlayOutMap createMapPacket(int mapId, byte[] colors, Collection<MapCursor> cursors) {
        List<MapIcon> mapIcons = cursors == null ? null : cursors.stream().map(this::toNMSMapIcon).collect(Collectors.toList());
        WorldMap.b b = colors == null ? null : new WorldMap.b(0, 0, 128, 128, colors);
        return new PacketPlayOutMap(new MapId(mapId), (byte) 0, false, Optional.ofNullable(mapIcons), Optional.ofNullable(b));
    }

    @Override
    public PacketPlayOutEntityMetadata createItemFrameItemChangePacket(int entityId, ItemStack itemStack) {
        List<DataWatcher.c<?>> dataWatchers = Collections.singletonList(DataWatcher.c.a(EntityItemFrame.f, CraftItemStack.asNMSCopy(itemStack)));
        return new PacketPlayOutEntityMetadata(entityId, dataWatchers);
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        ((CraftPlayer) player).getHandle().c.sendPacket((Packet<?>) packet);
    }

    @Override
    public CombinedMapItemInfo getCombinedMapItemInfo(ItemStack itemStack) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        CustomData customData = nmsItemStack.a(DataComponents.b);
        if (customData == null) {
            return null;
        }
        NBTTagCompound tag = customData.c();
        if (!tag.e(CombinedMapItemInfo.KEY)) {
            return null;
        }
        int imageMapIndex = tag.h(CombinedMapItemInfo.KEY);
        if (!tag.e(CombinedMapItemInfo.PLACEMENT_UUID_KEY) || !tag.e(CombinedMapItemInfo.PLACEMENT_YAW_KEY)) {
            return new CombinedMapItemInfo(imageMapIndex);
        }
        float yaw = tag.j(CombinedMapItemInfo.PLACEMENT_YAW_KEY);
        UUID uuid = UUIDUtils.fromIntArray(tag.n(CombinedMapItemInfo.PLACEMENT_UUID_KEY));
        return new CombinedMapItemInfo(imageMapIndex, new CombinedMapItemInfo.PlacementInfo(yaw, uuid));
    }

    @Override
    public ItemStack withCombinedMapItemInfo(ItemStack itemStack, CombinedMapItemInfo combinedMapItemInfo) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        CustomData customData = nmsItemStack.a(DataComponents.b, CustomData.a);
        NBTTagCompound tag = customData.c();
        tag.a(CombinedMapItemInfo.KEY, combinedMapItemInfo.getImageMapIndex());
        if (combinedMapItemInfo.hasPlacement()) {
            CombinedMapItemInfo.PlacementInfo placement = combinedMapItemInfo.getPlacement();
            tag.a(CombinedMapItemInfo.PLACEMENT_YAW_KEY, placement.getYaw());
            tag.a(CombinedMapItemInfo.PLACEMENT_UUID_KEY, UUIDUtils.toIntArray(placement.getUniqueId()));
        }
        nmsItemStack.b(DataComponentPatch.a().a(DataComponents.b, CustomData.a(tag)).a());
        return CraftItemStack.asCraftMirror(nmsItemStack);
    }
}
