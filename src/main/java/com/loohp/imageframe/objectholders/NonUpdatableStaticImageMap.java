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
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.MapUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class NonUpdatableStaticImageMap extends ImageMap {

    public static NonUpdatableStaticImageMap create(ImageMapManager manager, String name, BufferedImage[] images, int width, int height, UUID creator) throws Exception {
        return create(manager, name, images, null, width, height, creator);
    }

    public static NonUpdatableStaticImageMap create(ImageMapManager manager, String name, BufferedImage[] images, List<Integer> mapIds, int width, int height, UUID creator) throws Exception {
        World world = Bukkit.getWorlds().get(0);
        int mapsCount = width * height;
        if (images.length != mapsCount) {
            throw new IllegalArgumentException("images length not the same as width * height");
        }
        List<Future<MapView>> mapViewsFuture = new ArrayList<>(mapsCount);
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapsCount);
        for (int i = 0; i < mapsCount; i++) {
            if (mapIds == null) {
                mapViewsFuture.add(MapUtils.createMap(world));
            } else {
                mapViewsFuture.add(MapUtils.getMap(mapIds.get(i)));
            }
            markers.add(new ConcurrentHashMap<>());
        }
        List<MapView> mapViews = new ArrayList<>(mapsCount);
        mapIds = new ArrayList<>(mapsCount);
        for (Future<MapView> future : mapViewsFuture) {
            try {
                MapView mapView = future.get();
                Bukkit.getScheduler().runTask(ImageFrame.plugin, () -> {
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
        NonUpdatableStaticImageMap map = new NonUpdatableStaticImageMap(manager, -1, name, images, mapViews, mapIds, markers, width, height, creator, System.currentTimeMillis());
        for (int i = 0; i < mapViews.size(); i++) {
            MapView mapView = mapViews.get(i);
            int finalI = i;
            Bukkit.getScheduler().runTask(ImageFrame.plugin, () -> mapView.addRenderer(new NonUpdatableStaticImageMapRenderer(map, finalI)));
        }
        map.update(false);
        return map;
    }

    @SuppressWarnings("unused")
    public static NonUpdatableStaticImageMap load(ImageMapManager manager, File folder, JsonObject json) throws Exception {
        if (!json.get("type").getAsString().equals(NonUpdatableStaticImageMap.class.getName())) {
            throw new IllegalArgumentException("invalid type");
        }
        int imageIndex = json.get("index").getAsInt();
        String name = json.has("name") ? json.get("name").getAsString() : "Unnamed";
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
        NonUpdatableStaticImageMap map = new NonUpdatableStaticImageMap(manager, imageIndex, name, cachedImages, mapViews, mapIds, markers, width, height, creator, creationTime);
        for (int u = 0; u < mapViews.size(); u++) {
            MapView mapView = mapViews.get(u);
            int finalU = u;
            Bukkit.getScheduler().runTask(ImageFrame.plugin, () -> {
                for (MapRenderer renderer : mapView.getRenderers()) {
                    mapView.removeRenderer(renderer);
                }
                mapView.addRenderer(new NonUpdatableStaticImageMapRenderer(map, finalU));
            });
        }
        return map;
    }

    protected final BufferedImage[] cachedImages;

    protected byte[][] cachedColors;

    protected NonUpdatableStaticImageMap(ImageMapManager manager, int imageIndex, String name, BufferedImage[] cachedImages, List<MapView> mapViews, List<Integer> mapIds, List<Map<String, MapCursor>> mapMarkers, int width, int height, UUID creator, long creationTime) {
        super(manager, imageIndex, name, mapViews, mapIds, mapMarkers, width, height, creator, creationTime);
        this.cachedImages = cachedImages;
        cacheColors();
    }

    public void cacheColors() {
        if (cachedImages == null) {
            return;
        }
        if (cachedImages[0] == null) {
            return;
        }
        cachedColors = new byte[cachedImages.length][];
        int i = 0;
        for (BufferedImage image : cachedImages) {
            cachedColors[i++] = MapPalette.imageToBytes(image);
        }
    }

    @Override
    public ImageMap deepClone(String name, UUID creator) throws Exception {
        BufferedImage[] images = new BufferedImage[cachedImages.length];
        for (int i = 0; i < images.length; i++) {
            BufferedImage image = cachedImages[i];
            BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = newImage.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            images[i] = newImage;
        }
        NonUpdatableStaticImageMap imageMap = create(manager, name, images, width, height, creator);
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
        cacheColors();
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
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("creator", creator.toString());
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
            ImageIO.write(cachedImages[i], "png", new File(folder, i +".png"));
        }
    }

    public static class NonUpdatableStaticImageMapRenderer extends ImageMapRenderer {

        private final NonUpdatableStaticImageMap parent;

        public NonUpdatableStaticImageMapRenderer(NonUpdatableStaticImageMap parent, int index) {
            super(parent.getManager(), parent, index);
            this.parent = parent;
        }

        @Override
        public MutablePair<byte[], Collection<MapCursor>> renderMap(MapView mapView, Player player) {
            byte[] colors;
            if (parent.cachedColors != null && parent.cachedColors[index] != null) {
                colors = parent.cachedColors[index];
            } else if (parent.cachedImages[index] != null) {
                colors = MapPalette.imageToBytes(parent.cachedImages[index]);
            } else {
                colors = null;
            }
            Collection<MapCursor> cursors = parent.getMapMarkers().get(index).values();
            return new MutablePair<>(colors, cursors);
        }
    }

}
