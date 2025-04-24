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
import com.loohp.imageframe.api.events.ImageMapUpdatedEvent;
import com.loohp.imageframe.utils.FutureUtils;
import com.loohp.imageframe.utils.HTTPRequestUtils;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class URLStaticImageMap extends URLImageMap {

    public static Future<? extends URLStaticImageMap> create(ImageMapManager manager, String name, String url, int width, int height, DitheringType ditheringType, UUID creator) throws Exception {
        World world = MapUtils.getMainWorld();
        int mapsCount = width * height;
        List<Future<MapView>> mapViewsFuture = new ArrayList<>(mapsCount);
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapsCount);
        for (int i = 0; i < mapsCount; i++) {
            mapViewsFuture.add(MapUtils.createMap(world));
            markers.add(new ConcurrentHashMap<>());
        }
        List<MapView> mapViews = new ArrayList<>(mapsCount);
        List<Integer> mapIds = new ArrayList<>(mapsCount);
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
        URLStaticImageMap map = new URLStaticImageMap(manager, -1, name, url, new FileLazyMappedBufferedImage[mapsCount], mapViews, mapIds, markers, width, height, ditheringType, creator, Collections.emptyMap(), System.currentTimeMillis());
        return FutureUtils.callAsyncMethod(() -> {
            FutureUtils.callSyncMethod(() -> {
                for (int i = 0; i < mapViews.size(); i++) {
                    mapViews.get(i).addRenderer(new URLStaticImageMapRenderer(map, i));
                }
            }).get();
            map.update(false);
            return map;
        });
    }

    @SuppressWarnings("unused")
    public static Future<? extends URLStaticImageMap> load(ImageMapManager manager, File folder, JsonObject json) throws Exception {
        if (!json.get("type").getAsString().equals(URLStaticImageMap.class.getName())) {
            throw new IllegalArgumentException("invalid type");
        }
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
        URLStaticImageMap map = new URLStaticImageMap(manager, imageIndex, name, url, cachedImages, mapViews, mapIds, markers, width, height, ditheringType, creator, hasAccess, creationTime);
        return FutureUtils.callSyncMethod(() -> {
            for (int u = 0; u < mapViews.size(); u++) {
                MapView mapView = mapViews.get(u);
                for (MapRenderer renderer : mapView.getRenderers()) {
                    mapView.removeRenderer(renderer);
                }
                mapView.addRenderer(new URLStaticImageMapRenderer(map, u));
            }
            return map;
        });
    }

    protected final FileLazyMappedBufferedImage[] cachedImages;

    protected byte[][] cachedColors;

    protected URLStaticImageMap(ImageMapManager manager, int imageIndex, String name, String url, FileLazyMappedBufferedImage[] cachedImages, List<MapView> mapViews, List<Integer> mapIds, List<Map<String, MapCursor>> mapMarkers, int width, int height, DitheringType ditheringType, UUID creator, Map<UUID, ImageMapAccessPermissionType> hasAccess, long creationTime) {
        super(manager, imageIndex, name, url, mapViews, mapIds, mapMarkers, width, height, ditheringType, creator, hasAccess, creationTime);
        this.cachedImages = cachedImages;
        this.cacheControlTask.loadCacheIfManual();
    }

    @Override
    public void loadColorCache() {
        if (cachedImages == null) {
            return;
        }
        if (cachedImages[0] == null) {
            return;
        }
        byte[][] cachedColors = new byte[cachedImages.length][];
        BufferedImage combined = new BufferedImage(width * MapUtils.MAP_WIDTH, height * MapUtils.MAP_WIDTH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();
        int index = 0;
        for (FileLazyMappedBufferedImage image : cachedImages) {
            g.drawImage(image.get(), (index % width) * MapUtils.MAP_WIDTH, (index / width) * MapUtils.MAP_WIDTH, null);
            index++;
        }
        g.dispose();
        byte[] combinedData = MapUtils.toMapPaletteBytes(combined, ditheringType);
        for (int i = 0; i < index; i++) {
            byte[] data = new byte[MapUtils.MAP_WIDTH * MapUtils.MAP_WIDTH];
            for (int y = 0; y < MapUtils.MAP_WIDTH; y++) {
                int offset = ((i / width) * MapUtils.MAP_WIDTH + y) * (width * MapUtils.MAP_WIDTH) + ((i % width) * MapUtils.MAP_WIDTH);
                System.arraycopy(combinedData, offset, data, y * MapUtils.MAP_WIDTH, MapUtils.MAP_WIDTH);
            }
            cachedColors[i] = data;
        }
        this.cachedColors = cachedColors;
    }

    @Override
    public boolean hasColorCached() {
        return cachedColors != null;
    }

    @Override
    public void unloadColorCache() {
        cachedColors = null;
    }

    @Override
    public ImageMap deepClone(String name, UUID creator) throws Exception {
        URLStaticImageMap imageMap = create(manager, name, url, width, height, ditheringType, creator).get();
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
    public void update(boolean save) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(HTTPRequestUtils.download(url, ImageFrame.maxImageFileSize)));
        if (image == null) {
            throw new RuntimeException("Unable to read or download image, does this url directly links to an image? (" + url + ")");
        }
        image = MapUtils.resize(image, width, height);
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cachedImages[i++] = FileLazyMappedBufferedImage.fromImage(MapUtils.getSubImage(image, x, y));
            }
        }
        reloadColorCache();
        Bukkit.getPluginManager().callEvent(new ImageMapUpdatedEvent(this));
        send(getViewers());
        if (save) {
            save();
        }
    }

    @Override
    public void save() throws Exception {
        if (imageIndex < 0) {
            throw new IllegalStateException("ImageMap with index < 0 cannot be saved");
        }
        File folder = new File(manager.getDataFolder(), String.valueOf(imageIndex));
        folder.mkdirs();
        JsonObject json = new JsonObject();
        json.addProperty("type", this.getClass().getName());
        json.addProperty("index", imageIndex);
        json.addProperty("name", name);
        json.addProperty("url", url);
        json.addProperty("width", width);
        json.addProperty("height", height);
        if (ditheringType != null) {
            json.addProperty("ditheringType", ditheringType.getName());
        }
        json.addProperty("creator", creator.toString());
        JsonObject accessJson = new JsonObject();
        for (Map.Entry<UUID, ImageMapAccessPermissionType> entry : accessControl.getPermissions().entrySet()) {
            accessJson.addProperty(entry.getKey().toString(), entry.getValue().name());
        }
        json.add("hasAccess", accessJson);
        json.addProperty("creationTime", creationTime);
        JsonArray mapDataJson = new JsonArray();
        for (int i = 0; i < mapViews.size(); i++) {
            JsonObject dataJson = new JsonObject();
            dataJson.addProperty("mapid", mapIds.get(i));
            dataJson.addProperty("image", i + ".png");
            JsonArray markerArray = new JsonArray();
            for (Map.Entry<String, MapCursor> entry : mapMarkers.get(i).entrySet()) {
                MapCursor marker = entry.getValue();
                JsonObject markerData = new JsonObject();
                markerData.addProperty("name", entry.getKey());
                markerData.addProperty("x", marker.getX());
                markerData.addProperty("y", marker.getY());
                markerData.addProperty("type", marker.getType().name());
                markerData.addProperty("direction", marker.getDirection());
                markerData.addProperty("visible", marker.isVisible());
                markerData.addProperty("caption", marker.getCaption());
                markerArray.add(markerData);
            }
            dataJson.add("markers", markerArray);
            mapDataJson.add(dataJson);
        }
        json.add("mapdata", mapDataJson);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(new File(folder, "data.json").toPath()), StandardCharsets.UTF_8))) {
            pw.println(GSON.toJson(json));
            pw.flush();
        }
        for (int i = 0; i < cachedImages.length; i++) {
            cachedImages[i].setFile(new File(folder, i +".png"));
        }
    }

    public static class URLStaticImageMapRenderer extends ImageMapRenderer {

        private final URLStaticImageMap parent;

        public URLStaticImageMapRenderer(URLStaticImageMap parent, int index) {
            super(parent.getManager(), parent, index);
            this.parent = parent;
        }

        @Override
        public MutablePair<byte[], Collection<MapCursor>> renderMap(MapView mapView, Player player) {
            byte[] colors;
            if (parent.cachedColors != null && parent.cachedColors[index] != null) {
                colors = parent.cachedColors[index];
            } else if (parent.cachedImages[index] != null) {
                colors = MapUtils.toMapPaletteBytes(parent.cachedImages[index].get(), parent.ditheringType);
            } else {
                colors = null;
            }
            Collection<MapCursor> cursors = parent.getMapMarkers().get(index).values();
            return new MutablePair<>(colors, cursors);
        }
    }

}
