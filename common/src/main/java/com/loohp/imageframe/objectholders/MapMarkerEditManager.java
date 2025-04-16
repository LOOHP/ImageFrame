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

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.api.events.ImageMapUpdatedEvent;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.platformscheduler.ScheduledTask;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapMarkerEditManager implements Listener, AutoCloseable {

    private final Map<Player, MapMarkerEditData> activeEditing;
    private final ImageMapRenderEventListener renderEventListener;
    private final ScheduledTask task;

    public MapMarkerEditManager() {
        this.activeEditing = new ConcurrentHashMap<>();
        this.renderEventListener = (manager, imageMap, map, player, renderData) -> {
            Collection<MapCursor> cursors = renderData.getSecond();
            List<MapCursor> additionCursors = new LinkedList<>();
            for (MapMarkerEditData data : activeEditing.values()) {
                MapView targetMap = data.getCurrentTargetMap();
                if (targetMap != null && targetMap.equals(map) && data.getImageMap().equals(imageMap)) {
                    additionCursors.add(data.getMapCursor());
                }
            }
            if (!additionCursors.isEmpty()) {
                additionCursors.addAll(cursors);
                renderData.setSecond(additionCursors);
            }
        };
        ImageFrame.imageMapManager.appendRenderEventListener(renderEventListener);
        this.task = Scheduler.runTaskTimer(ImageFrame.plugin, () -> editTask(), 0, 1);
        Bukkit.getPluginManager().registerEvents(this, ImageFrame.plugin);
    }

    @Override
    public void close() {
        ImageFrame.imageMapManager.removeRenderEventListener(renderEventListener);
        task.cancel();
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
            if (!imageMap.isValid()) {
                leaveActiveEditing(player);
                continue;
            }
            if (!imageMap.getMapViews().contains(mapView)) {
                continue;
            }
            IntPosition target = MapUtils.getTargetPixelOnItemFrame(itemFrame.getLocation().toVector(), itemFrame.getFacing().getDirection(), hitPosition, itemFrame.getRotation());
            editData.setCurrentTargetMap(mapView);
            editData.getMapCursor().setX((byte) target.getX());
            editData.getMapCursor().setY((byte) target.getY());
            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> imageMap.send(imageMap.getViewers()));
        }
    }

    public void setActiveEditing(Player player, String name, MapCursor mapCursor, ImageMap imageMap) {
        activeEditing.put(player, new MapMarkerEditData(name, mapCursor, imageMap));
    }

    public boolean isActiveEditing(Player player) {
        return activeEditing.containsKey(player);
    }

    public MapMarkerEditData getActiveEditing(Player player) {
        return activeEditing.get(player);
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
        handlePlayerInteract(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction().equals(Action.RIGHT_CLICK_AIR) || event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            handlePlayerInteract(event);
        }
    }

    public <T extends PlayerEvent & Cancellable> void handlePlayerInteract(T event) {
        Player player = event.getPlayer();
        MapMarkerEditData editData = activeEditing.get(player);
        if (editData == null) {
            return;
        }
        Location location = player.getEyeLocation();
        RayTraceResult result = MapUtils.rayTraceItemFrame(location, location.getDirection(), 5.0);
        if (result == null) {
            return;
        }
        ItemFrame itemFrame = (ItemFrame) result.getHitEntity();
        if (itemFrame == null) {
            return;
        }
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
        Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
            try {
                Map<String, MapCursor> markers = imageMap.getMapMarkers(mapView);
                if (!player.hasPermission("imageframe.marker.unlimited") && markers.size() >= ImageFrame.mapMarkerLimit) {
                    player.sendMessage(ImageFrame.messageMarkersLimitReached.replace("{Limit}", ImageFrame.mapMarkerLimit + ""));
                } else {
                    MapCursor mapCursor = editData.getMapCursor();
                    markers.put(editData.getName(), mapCursor);
                    Bukkit.getPluginManager().callEvent(new ImageMapUpdatedEvent(imageMap));
                    imageMap.send(imageMap.getViewers());
                    imageMap.save();
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

        protected void setCurrentTargetMap(MapView currentTargetMap) {
            this.currentTargetMap = currentTargetMap;
        }

    }

}
