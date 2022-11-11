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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loohp.imageframe.utils.MapUtils;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public class MinecraftURLOverlayImageMap extends URLStaticImageMap {

    public static MinecraftURLOverlayImageMap create(ImageMapManager manager, String name, String url, List<MapView> mapViews, int width, int height, UUID creator) throws Exception {
        int mapsCount = width * height;
        List<Integer> mapIds = new ArrayList<>(mapsCount);
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapsCount);
        for (int i = 0; i < mapsCount; i++) {
            MapView mapView = mapViews.get(i);
            mapIds.add(mapView.getId());
            for (MapRenderer mapRenderer : mapView.getRenderers()) {
                if (mapRenderer.getClass().getName().equals(MinecraftURLOverlayImageMapRenderer.class.getName())) {
                    mapView.removeRenderer(mapRenderer);
                }
            }
            markers.add(new ConcurrentHashMap<>());
        }
        MinecraftURLOverlayImageMap map = new MinecraftURLOverlayImageMap(manager, -1, name, url, new BufferedImage[mapsCount], mapViews, mapIds, markers, width, height, creator, System.currentTimeMillis());
        for (int i = 0; i < mapViews.size(); i++) {
            MapView mapView = mapViews.get(i);
            mapView.addRenderer(new MinecraftURLOverlayImageMapRenderer(map, i));
        }
        map.update(false);
        return map;
    }

    @SuppressWarnings("unused")
    public static MinecraftURLOverlayImageMap load(ImageMapManager manager, File folder, JsonObject json) throws Exception {
        if (!json.get("type").getAsString().equals(MinecraftURLOverlayImageMap.class.getName())) {
            throw new IllegalArgumentException("invalid type");
        }
        int imageIndex = json.get("index").getAsInt();
        String name = json.has("name") ? json.get("name").getAsString() : "Unnamed";
        String url = json.get("url").getAsString();
        int width = json.get("width").getAsInt();
        int height = json.get("height").getAsInt();
        long creationTime = json.get("creationTime").getAsLong();
        UUID creator = UUID.fromString(json.get("creator").getAsString());
        JsonArray mapDataJson = json.get("mapdata").getAsJsonArray();
        List<Future<MapView>> mapViewsFuture = new ArrayList<>(mapDataJson.size());
        List<Integer> mapIds = new ArrayList<>(mapDataJson.size());
        BufferedImage[] cachedImages = new BufferedImage[mapDataJson.size()];
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapDataJson.size());
        int i = 0;
        for (JsonElement dataJson : mapDataJson) {
            JsonObject jsonObject = dataJson.getAsJsonObject();
            int mapId = jsonObject.get("mapid").getAsInt();
            mapIds.add(mapId);
            mapViewsFuture.add(MapUtils.getMap(mapId));
            cachedImages[i] = ImageIO.read(new File(folder, jsonObject.get("image").getAsString()));
            Map<String, MapCursor> mapCursors = new ConcurrentHashMap<>();
            if (jsonObject.has("markers")) {
                JsonArray markerArray = jsonObject.get("markers").getAsJsonArray();
                for (JsonElement element : markerArray) {
                    JsonObject markerData = element.getAsJsonObject();
                    String markerName = markerData.get("name").getAsString();
                    byte x = markerData.get("x").getAsByte();
                    byte y = markerData.get("y").getAsByte();
                    MapCursor.Type type = MapCursor.Type.valueOf(markerData.get("type").getAsString().toUpperCase());
                    byte direction = markerData.get("direction").getAsByte();
                    boolean visible = markerData.get("visible").getAsBoolean();
                    JsonElement caption = markerData.get("caption");
                    mapCursors.put(markerName, new MapCursor(x, y, direction, type, visible, caption.isJsonNull() ? null : caption.getAsString()));
                }
            }
            markers.add(mapCursors);
            i++;
        }
        List<MapView> mapViews = new ArrayList<>(mapViewsFuture.size());
        for (Future<MapView> future : mapViewsFuture) {
            mapViews.add(future.get());
        }
        MinecraftURLOverlayImageMap map = new MinecraftURLOverlayImageMap(manager, imageIndex, name, url, cachedImages, mapViews, mapIds, markers, width, height, creator, creationTime);
        for (int u = 0; u < mapViews.size(); u++) {
            MapView mapView = mapViews.get(u);
            for (MapRenderer mapRenderer : mapView.getRenderers()) {
                if (mapRenderer.getClass().getName().equals(MinecraftURLOverlayImageMapRenderer.class.getName())) {
                    mapView.removeRenderer(mapRenderer);
                }
            }
            mapView.addRenderer(new MinecraftURLOverlayImageMapRenderer(map, u));
        }
        return map;
    }

    protected MinecraftURLOverlayImageMap(ImageMapManager manager, int imageIndex, String name, String url, BufferedImage[] cachedImages, List<MapView> mapViews, List<Integer> mapIds, List<Map<String, MapCursor>> mapMarkers, int width, int height, UUID creator, long creationTime) {
        super(manager, imageIndex, name, url, cachedImages, mapViews, mapIds, mapMarkers, width, height, creator, creationTime);
    }

    @Override
    public ImageMap deepClone(String name, UUID creator) throws Exception {
        MinecraftURLOverlayImageMap imageMap = create(manager, name, url, mapViews, width, height, creator);
        List<Map<String, MapCursor>> newList = imageMap.getMapMarkers();
        int i = 0;
        for (Map<String, MapCursor> map : getMapMarkers()) {
            Map<String, MapCursor> newMap = newList.get(i++);
            for (Map.Entry<String, MapCursor> entry : map.entrySet()) {
                MapCursor mapCursor = entry.getValue();
                newMap.put(entry.getKey(), new MapCursor(mapCursor.getX(), mapCursor.getY(), mapCursor.getDirection(), mapCursor.getType(), mapCursor.isVisible(), mapCursor.getCaption()));
            }
        }
        return imageMap;
    }

    @Override
    public boolean trackDeletedMaps() {
        return false;
    }

    @Override
    public void stop() {
        for (MapView mapView : mapViews) {
            for (MapRenderer mapRenderer : mapView.getRenderers()) {
                if (mapRenderer.getClass().getName().equals(MinecraftURLOverlayImageMapRenderer.class.getName())) {
                    mapView.removeRenderer(mapRenderer);
                }
            }
        }
    }

    public static class MinecraftURLOverlayImageMapRenderer extends URLStaticImageMapRenderer {

        private final MinecraftURLOverlayImageMap parent;

        public MinecraftURLOverlayImageMapRenderer(MinecraftURLOverlayImageMap parent, int index) {
            super(parent, index);
            this.parent = parent;
        }

        @Override
        public void render(MapView mapView, MapCanvas canvas, Player player) {
            byte[] colors;
            if (parent.cachedColors != null && parent.cachedColors[index] != null) {
                colors = parent.cachedColors[index];
            } else if (parent.cachedImages[index] != null) {
                colors = MapPalette.imageToBytes(parent.cachedImages[index]);
            } else {
                colors = null;
            }
            MutablePair<byte[], Collection<MapCursor>> renderData = new MutablePair<>(colors, parent.getMapMarkers().get(index).values());
            manager.callRenderEventListener(manager, imageMap, mapView, player, renderData);
            colors = renderData.getFirst();
            if (colors != null) {
                for (int i = 0; i < colors.length; i++) {
                    byte color = colors[i];
                    if (color != MapPalette.TRANSPARENT) {
                        canvas.setPixel(i % MapUtils.MAP_WIDTH, i / MapUtils.MAP_WIDTH, color);
                    }
                }
            }
            canvas.setCursors(MapUtils.toMapCursorCollection(renderData.getSecond()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public MutablePair<byte[], Collection<MapCursor>> renderMap(MapView mapView, Player player) {
            return (MutablePair<byte[], Collection<MapCursor>>) (MutablePair<byte[], ?>) MapUtils.bukkitRenderMap(mapView, player);
        }
    }

}
