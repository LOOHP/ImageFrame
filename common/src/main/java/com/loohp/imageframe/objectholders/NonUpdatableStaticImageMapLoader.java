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
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.FutureUtils;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.platformscheduler.Scheduler;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class NonUpdatableStaticImageMapLoader extends ImageMapLoader<NonUpdatableStaticImageMap, NonUpdatableImageMapCreateInfo> {

    private static final Key IDENTIFIER = Key.key("imageframe", "non_updatable_static");
    private static final String LEGACY_TYPE = "com.loohp.imageframe.objectholders.NonUpdatableStaticImageMap";

    @Override
    public Key getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getLegacyType() {
        return LEGACY_TYPE;
    }

    @Override
    public Class<NonUpdatableStaticImageMap> getImageMapClass() {
        return NonUpdatableStaticImageMap.class;
    }

    @Override
    public Class<NonUpdatableImageMapCreateInfo> getImageMapCreateInfoClass() {
        return NonUpdatableImageMapCreateInfo.class;
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
    public Future<NonUpdatableStaticImageMap> create(NonUpdatableImageMapCreateInfo createInfo) throws Exception {
        World world = MapUtils.getMainWorld();
        int mapsCount = createInfo.getWidth() * createInfo.getHeight();
        BufferedImage[] images = createInfo.getImages();
        List<Integer> mapIds = createInfo.getMapIds();
        if (images.length != mapsCount) {
            throw new IllegalArgumentException("images length not the same as width * height");
        }
        List<Future<MapView>> mapViewsFuture = new ArrayList<>(mapsCount);
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapsCount);
        for (int i = 0; i < mapsCount; i++) {
            if (mapIds == null) {
                mapViewsFuture.add(MapUtils.createMap(world));
            } else {
                mapViewsFuture.add(MapUtils.getMapOrCreateMissing(world, mapIds.get(i)));
            }
            markers.add(new ConcurrentHashMap<>());
        }
        List<MapView> mapViews = new ArrayList<>(mapsCount);
        mapIds = new ArrayList<>(mapsCount);
        for (Future<MapView> future : mapViewsFuture) {
            try {
                MapView mapView = future.get();
                Scheduler.runTask(ImageFrame.plugin, () -> {
                    for (MapRenderer renderer : mapView.getRenderers()) {
                        mapView.removeRenderer(renderer);
                    }
                });
                mapViews.add(mapView);
                mapIds.add(mapView.getId());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        NonUpdatableStaticImageMap map = new NonUpdatableStaticImageMap(createInfo.getManager(), this, -1, createInfo.getName(), Arrays.stream(images).map(i -> FileLazyMappedBufferedImage.fromImage(i)).toArray(FileLazyMappedBufferedImage[]::new), mapViews, mapIds, markers, createInfo.getWidth(), createInfo.getHeight(), createInfo.getDitheringType(), createInfo.getCreator(), Collections.emptyMap(), System.currentTimeMillis());
        return FutureUtils.callAsyncMethod(() -> {
            FutureUtils.callSyncMethod(() -> {
                for (int i = 0; i < mapViews.size(); i++) {
                    mapViews.get(i).addRenderer(new NonUpdatableStaticImageMap.NonUpdatableStaticImageMapRenderer(map, i));
                }
            }).get();
            map.update(false);
            return map;
        });
    }

    @Override
    public Future<NonUpdatableStaticImageMap> load(ImageMapManager manager, File folder, JsonObject json) throws Exception {
        int imageIndex = json.get("index").getAsInt();
        String name = json.has("name") ? json.get("name").getAsString() : "Unnamed";
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
        List<Integer> mapIds = new ArrayList<>(mapDataJson.size());
        FileLazyMappedBufferedImage[] cachedImages = new FileLazyMappedBufferedImage[mapDataJson.size()];
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapDataJson.size());
        World world = Bukkit.getWorlds().get(0);
        int i = 0;
        for (JsonElement dataJson : mapDataJson) {
            JsonObject jsonObject = dataJson.getAsJsonObject();
            int mapId = jsonObject.get("mapid").getAsInt();
            mapIds.add(mapId);
            mapViewsFuture.add(MapUtils.getMapOrCreateMissing(world, mapId));
            cachedImages[i] = FileLazyMappedBufferedImage.fromFile(new File(folder, jsonObject.get("image").getAsString()));
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
        NonUpdatableStaticImageMap map = new NonUpdatableStaticImageMap(manager, this, imageIndex, name, cachedImages, mapViews, mapIds, markers, width, height, ditheringType, creator, hasAccess, creationTime);
        return FutureUtils.callSyncMethod(() -> {
            for (int u = 0; u < mapViews.size(); u++) {
                MapView mapView = mapViews.get(u);
                for (MapRenderer renderer : mapView.getRenderers()) {
                    mapView.removeRenderer(renderer);
                }
                mapView.addRenderer(new NonUpdatableStaticImageMap.NonUpdatableStaticImageMapRenderer(map, u));
            }
            return map;
        });
    }

}
