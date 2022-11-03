/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2022. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2022. Contributors
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

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.MapUtils;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapMarkerEditManager implements Listener, AutoCloseable {

    private final Map<Player, MapMarkerEditData> activeEditing;
    private final ImageMapRenderEventListener renderEventListener;
    private final int taskId;

    public MapMarkerEditManager() {
        this.activeEditing = new ConcurrentHashMap<>();
        this.renderEventListener = (manager, imageMap, map, canvas, player) -> {
            for (MapMarkerEditData data : activeEditing.values()) {
                MapView targetMap = data.getCurrentTargetMap();
                if (targetMap != null && targetMap.equals(map) && data.getImageMap().equals(imageMap)) {
                    canvas.getCursors().addCursor(data.getMapCursor());
                }
            }
        };
        ImageFrame.imageMapManager.appendRenderEventListener(renderEventListener);
        this.taskId = Bukkit.getScheduler().runTaskTimer(ImageFrame.plugin, () -> editTask(), 0, 1).getTaskId();
        Bukkit.getPluginManager().registerEvents(this, ImageFrame.plugin);
    }

    @Override
    public void close() {
        ImageFrame.imageMapManager.removeRenderEventListener(renderEventListener);
        Bukkit.getScheduler().cancelTask(taskId);
        HandlerList.unregisterAll(this);
    }

    private void editTask() {
        for (Map.Entry<Player, MapMarkerEditData> entry : activeEditing.entrySet()) {
            Player player = entry.getKey();
            if (!player.isOnline()) {
                continue;
            }
            Location location = player.getEyeLocation();
            RayTraceResult result = MapUtils.rayTraceItemFrame(location, location.getDirection(), 5.0);
            if (result == null) {
                continue;
            }
            ItemFrame itemFrame = (ItemFrame) result.getHitEntity();
            if (itemFrame == null) {
                continue;
            }
            Vector hitPosition = result.getHitPosition();
            ItemStack itemStack = itemFrame.getItem();
            if (itemStack == null || itemStack.getType().equals(Material.AIR)) {
                continue;
            }
            if (!itemStack.hasItemMeta()) {
                continue;
            }
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (!(itemMeta instanceof MapMeta)) {
                continue;
            }
            MapMeta mapMeta = (MapMeta) itemMeta;
            MapView mapView = mapMeta.getMapView();
            if (mapView == null) {
                continue;
            }
            MapMarkerEditData editData = entry.getValue();
            ImageMap imageMap = editData.getImageMap();
            if (!imageMap.getMapViews().contains(mapView)) {
                continue;
            }
            IntIntPair target = MapUtils.getTargetPixelOnItemFrame(itemFrame.getLocation().toVector(), itemFrame.getFacing().getDirection(), hitPosition, itemFrame.getRotation());
            editData.setCurrentTargetMap(mapView);
            editData.getMapCursor().setX((byte) target.leftInt());
            editData.getMapCursor().setY((byte) target.rightInt());
            if (!imageMap.requiresAnimationService()) {
                Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> imageMap.send(imageMap.getViewers()));
            }
        }
    }

    public void setActiveEditing(Player player, String name, MapCursor mapCursor, ImageMap imageMap) {
        activeEditing.put(player, new MapMarkerEditData(name, mapCursor, imageMap));
    }

    public boolean isActiveEditing(Player player) {
        return activeEditing.containsKey(player);
    }

    public MapMarkerEditData leaveActiveEditing(Player player) {
        return activeEditing.remove(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        leaveActiveEditing(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        MapMarkerEditData editData = activeEditing.get(player);
        if (editData == null) {
            return;
        }
        Location location = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTrace(location, location.getDirection(), 5.0, FluidCollisionMode.NEVER, true, 0.125, e -> e instanceof ItemFrame);
        if (result == null) {
            return;
        }
        ItemFrame itemFrame = (ItemFrame) result.getHitEntity();
        if (itemFrame == null) {
            return;
        }
        Vector hitPosition = result.getHitPosition();
        ItemStack itemStack = itemFrame.getItem();
        if (itemStack == null || itemStack.getType().equals(Material.AIR)) {
            return;
        }
        if (!itemStack.hasItemMeta()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (!(itemMeta instanceof MapMeta)) {
            return;
        }
        MapMeta mapMeta = (MapMeta) itemMeta;
        MapView mapView = mapMeta.getMapView();
        if (mapView == null) {
            return;
        }
        ImageMap imageMap = editData.getImageMap();
        if (!imageMap.getMapViews().contains(mapView)) {
            return;
        }
        event.setCancelled(true);
        activeEditing.remove(player);
        Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
            try {
                Map<String, MapCursor> markers = imageMap.getMapMarkers(mapView);
                if (!player.hasPermission("imageframe.marker.unlimited") && markers.size() >= ImageFrame.mapMarkerLimit) {
                    player.sendMessage(ImageFrame.messageMarkersLimitReached.replace("<Limit>", ImageFrame.mapMarkerLimit + ""));
                } else {
                    MapCursor mapCursor = editData.getMapCursor();
                    markers.put(editData.getName(), mapCursor);
                    imageMap.save();
                    imageMap.send(imageMap.getViewers());
                    player.sendMessage(ImageFrame.messageMarkersAddConfirm);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static class MapMarkerEditData {

        private final String name;
        private final MapCursor mapCursor;
        private final ImageMap imageMap;

        private MapView currentTargetMap;

        public MapMarkerEditData(String name, MapCursor mapCursor, ImageMap imageMap) {
            this.name = name;
            this.mapCursor = mapCursor;
            this.imageMap = imageMap;
        }

        public String getName() {
            return name;
        }

        public MapCursor getMapCursor() {
            return mapCursor;
        }

        public ImageMap getImageMap() {
            return imageMap;
        }

        public MapView getCurrentTargetMap() {
            return currentTargetMap;
        }

        public void setCurrentTargetMap(MapView currentTargetMap) {
            this.currentTargetMap = currentTargetMap;
        }

    }

}