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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loohp.imageframe.utils.FutureUtils;
import com.loohp.imageframe.utils.MapUtils;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public class MinecraftURLOverlayImageMapLoader extends ImageMapLoader<MinecraftURLOverlayImageMap, MinecraftURLOverlayImageMapCreateInfo> {

    private static final Key IDENTIFIER = Key.key("imageframe", "url_static_overlay");
    private static final String LEGACY_TYPE = "com.loohp.imageframe.objectholders.MinecraftURLOverlayImageMap";

    @Override
    public Key getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getLegacyType() {
        return LEGACY_TYPE;
    }

    @Override
    public Class<MinecraftURLOverlayImageMap> getImageMapClass() {
        return MinecraftURLOverlayImageMap.class;
    }

    @Override
    public Class<MinecraftURLOverlayImageMapCreateInfo> getImageMapCreateInfoClass() {
        return MinecraftURLOverlayImageMapCreateInfo.class;
    }

    @Override
    public List<String> getExtraPermissions() {
        return Collections.emptyList();
    }

    @Override
    public boolean isSupported(String imageType) {
        return true;
    }

    @Override
    public ImageMapLoaderPriority getPriority(String imageType) {
        return ImageMapLoaderPriority.LOWEST;
    }

    @Override
    public Future<MinecraftURLOverlayImageMap> create(MinecraftURLOverlayImageMapCreateInfo createInfo) throws Exception {
        int mapsCount = createInfo.getWidth() * createInfo.getHeight();
        List<MapView> mapViews = createInfo.getMapViews();
        List<Integer> mapIds = new ArrayList<>(mapsCount);
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapsCount);
        for (int i = 0; i < mapsCount; i++) {
            MapView mapView = mapViews.get(i);
            mapIds.add(mapView.getId());
            for (MapRenderer mapRenderer : mapView.getRenderers()) {
                if (mapRenderer.getClass().getName().equals(MinecraftURLOverlayImageMap.MinecraftURLOverlayImageMapRenderer.class.getName())) {
                    mapView.removeRenderer(mapRenderer);
                }
            }
            markers.add(new ConcurrentHashMap<>());
        }
        MinecraftURLOverlayImageMap map = new MinecraftURLOverlayImageMap(createInfo.getManager(), this, -1, createInfo.getName(), createInfo.getUrl(), new LazyMappedBufferedImage[mapsCount], mapViews, mapIds, markers, createInfo.getWidth(), createInfo.getHeight(), createInfo.getDitheringType(), createInfo.getCreator(), Collections.emptyMap(), System.currentTimeMillis());
        return FutureUtils.callAsyncMethod(() -> {
            FutureUtils.callSyncMethod(() -> {
                for (int i = 0; i < mapViews.size(); i++) {
                    mapViews.get(i).addRenderer(new MinecraftURLOverlayImageMap.MinecraftURLOverlayImageMapRenderer(map, i));
                }
            }).get();
            map.update(false);
            return map;
        });
    }

    @Override
    public Future<MinecraftURLOverlayImageMap> load(ImageMapManager manager, JsonObject json) throws Exception {
        int imageIndex = json.get("index").getAsInt();
        String name = json.has("name") ? json.get("name").getAsString() : "Unnamed";
        String url = json.get("url").getAsString();
        int width = json.get("width").getAsInt();
        int height = json.get("height").getAsInt();
        DitheringType ditheringType = DitheringType.fromName(json.has("ditheringType") ? json.get("ditheringType").getAsString() : null);
        long creationTime = json.get("creationTime").getAsLong();
        UUID creator = UUID.fromString(json.get("creator").getAsString());
        Map<UUID, ImageMapAccessPermissionType> hasAccess;
        if (json.has("hasAccess")) {
            JsonObject accessJson = json.get("hasAccess").getAsJsonObject();
            hasAccess = new HashMap<>(accessJson.size());
            for (Map.Entry<String, JsonElement> entry : accessJson.entrySet()) {
                hasAccess.put(UUID.fromString(entry.getKey()), ImageMapAccessPermissionType.valueOf(entry.getValue().getAsString().toUpperCase()));
            }
        } else {
            hasAccess = Collections.emptyMap();
        }
        JsonArray mapDataJson = json.get("mapdata").getAsJsonArray();
        List<Future<MapView>> mapViewsFuture = new ArrayList<>(mapDataJson.size());
        LazyMappedBufferedImage[] cachedImages = new LazyMappedBufferedImage[mapDataJson.size()];
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapDataJson.size());
        World world = MapUtils.getMainWorld();
        int i = 0;
        for (JsonElement dataJson : mapDataJson) {
            JsonObject jsonObject = dataJson.getAsJsonObject();
            if (jsonObject.has("mapid")) {
                int mapId = jsonObject.get("mapid").getAsInt();
                mapViewsFuture.add(MapUtils.getMapOrCreateMissing(world, mapId));
            } else {
                mapViewsFuture.add(MapUtils.createMap(world));
            }
            cachedImages[i] = StandardLazyMappedBufferedImage.fromSource(manager.getStorage().getSource(imageIndex, jsonObject.get("image").getAsString()));
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
        List<Integer> mapIds = new ArrayList<>(mapDataJson.size());
        List<MapView> mapViews = new ArrayList<>(mapViewsFuture.size());
        for (Future<MapView> future : mapViewsFuture) {
            MapView mapView = future.get();
            mapViews.add(mapView);
            mapIds.add(mapView.getId());
        }
        MinecraftURLOverlayImageMap map = new MinecraftURLOverlayImageMap(manager, this, imageIndex, name, url, cachedImages, mapViews, mapIds, markers, width, height, ditheringType, creator, hasAccess, creationTime);
        return FutureUtils.callSyncMethod(() -> {
            for (int u = 0; u < mapViews.size(); u++) {
                MapView mapView = mapViews.get(u);
                for (MapRenderer mapRenderer : mapView.getRenderers()) {
                    if (mapRenderer.getClass().getName().equals(MinecraftURLOverlayImageMap.MinecraftURLOverlayImageMapRenderer.class.getName())) {
                        mapView.removeRenderer(mapRenderer);
                    }
                }
                mapView.addRenderer(new MinecraftURLOverlayImageMap.MinecraftURLOverlayImageMapRenderer(map, u));
            }
            return map;
        });
    }

}
