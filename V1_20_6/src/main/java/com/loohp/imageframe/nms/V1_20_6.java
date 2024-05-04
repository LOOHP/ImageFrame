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

import com.loohp.imageframe.objectholders.MutablePair;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.protocol.game.PacketPlayOutMap;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.decoration.EntityItemFrame;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapIcon;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.WorldMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R4.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R4.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R4.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R4.map.CraftMapCursor;
import org.bukkit.craftbukkit.v1_20_R4.map.CraftMapView;
import org.bukkit.craftbukkit.v1_20_R4.map.RenderData;
import org.bukkit.craftbukkit.v1_20_R4.util.CraftChatMessage;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class V1_20_6 extends NMSWrapper {

    private final Field craftMapViewWorldMapField;

    public V1_20_6() {
        try {
            craftMapViewWorldMapField = CraftMapView.class.getDeclaredField("worldMap");
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
        nmsWorldMap.g = colors;
    }

    @Override
    public Set<Player> getViewers(MapView mapView) {
        WorldMap nmsWorldMap = getWorldMap(mapView);
        Map<EntityHuman, WorldMap.WorldMapHumanTracker> humansMap = nmsWorldMap.o;
        return humansMap.keySet().stream().map(e -> (Player) e).collect(Collectors.toSet());
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

    @SuppressWarnings("deprecation")
    @Override
    public MapView getMapOrCreateMissing(World world, int id) {
        MapView mapView = Bukkit.getMap(id);
        if (mapView != null) {
            return mapView;
        }
        Location spawnLocation = world.getSpawnLocation();
        WorldServer worldServer = ((CraftWorld) world).getHandle();
        ResourceKey<net.minecraft.world.level.World> worldTypeKey = worldServer.af();
        WorldMap worldMap = WorldMap.a(spawnLocation.getX(), spawnLocation.getZ(), (byte) 3, false, false, worldTypeKey);
        MapId mapId = new MapId(id);
        worldServer.a(mapId, worldMap);
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
        return new HashSet<>(entity.getTrackedBy());
    }

    @Override
    public PacketPlayOutMap createMapPacket(int mapId, byte[] colors, Collection<MapCursor> cursors) {
        List<MapIcon> mapIcons = cursors == null ? null : cursors.stream().map(this::toNMSMapIcon).collect(Collectors.toList());
        WorldMap.b b = colors == null ? null : new WorldMap.b(0, 0, 128, 128, colors);
        return new PacketPlayOutMap(new MapId(mapId), (byte) 0, false, Optional.ofNullable(mapIcons), Optional.ofNullable(b));
    }

    @Override
    public PacketPlayOutEntityMetadata createItemFrameItemChangePacket(ItemFrame itemFrame, ItemStack itemStack) {
        List<DataWatcher.c<?>> dataWatchers = Collections.singletonList(DataWatcher.c.a(EntityItemFrame.g, CraftItemStack.asNMSCopy(itemStack)));
        return new PacketPlayOutEntityMetadata(itemFrame.getEntityId(), dataWatchers);
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        ((CraftPlayer) player).getHandle().c.sendPacket((Packet<?>) packet);
    }
}
