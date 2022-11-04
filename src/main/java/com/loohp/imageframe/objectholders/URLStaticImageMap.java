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
import com.loohp.imageframe.utils.HTTPRequestUtils;
import com.loohp.imageframe.utils.MapUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectObjectMutablePair;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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

public class URLStaticImageMap extends URLImageMap {

    public static URLStaticImageMap create(ImageMapManager manager, String name, String url, int width, int height, UUID creator) throws Exception {
        World world = Bukkit.getWorlds().get(0);
        int mapsCount = width * height;
        List<MapView> mapViews = new ArrayList<>(mapsCount);
        IntList mapIds = new IntArrayList(mapsCount);
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapsCount);
        for (int i = 0; i < mapsCount; i++) {
            MapView mapView = Bukkit.createMap(world);
            for (MapRenderer renderer : mapView.getRenderers()) {
                mapView.removeRenderer(renderer);
            }
            mapViews.add(mapView);
            mapIds.add(mapView.getId());
            markers.add(new ConcurrentHashMap<>());
        }
        URLStaticImageMap map = new URLStaticImageMap(manager, -1, name, url, new BufferedImage[mapsCount], mapViews, mapIds, markers, width, height, creator, System.currentTimeMillis());
        for (int i = 0; i < mapViews.size(); i++) {
            MapView mapView = mapViews.get(i);
            mapView.addRenderer(new URLStaticImageMapRenderer(map, i));
        }
        map.update();
        return map;
    }

    @SuppressWarnings("unused")
    public static URLStaticImageMap load(ImageMapManager manager, File folder, JsonObject json) throws Exception {
        if (!json.get("type").getAsString().equals(URLStaticImageMap.class.getName())) {
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
        List<MapView> mapViews = new ArrayList<>(mapDataJson.size());
        IntList mapIds = new IntArrayList(mapDataJson.size());
        BufferedImage[] cachedImages = new BufferedImage[mapDataJson.size()];
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapDataJson.size());
        int i = 0;
        for (JsonElement dataJson : mapDataJson) {
            JsonObject jsonObject = dataJson.getAsJsonObject();
            int mapId = jsonObject.get("mapid").getAsInt();
            mapIds.add(mapId);
            mapViews.add(Bukkit.getMap(mapId));
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
        URLStaticImageMap map = new URLStaticImageMap(manager, imageIndex, name, url, cachedImages, mapViews, mapIds, markers, width, height, creator, creationTime);
        for (int u = 0; u < mapViews.size(); u++) {
            MapView mapView = mapViews.get(u);
            for (MapRenderer renderer : mapView.getRenderers()) {
                mapView.removeRenderer(renderer);
            }
            mapView.addRenderer(new URLStaticImageMapRenderer(map, u));
        }
        return map;
    }

    private final BufferedImage[] cachedImages;

    private byte[][] cachedColors;

    private URLStaticImageMap(ImageMapManager manager, int imageIndex, String name, String url, BufferedImage[] cachedImages, List<MapView> mapViews, IntList mapIds, List<Map<String, MapCursor>> mapMarkers, int width, int height, UUID creator, long creationTime) {
        super(manager, imageIndex, name, url, mapViews, mapIds, mapMarkers, width, height, creator, creationTime);
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
    public void update() throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(HTTPRequestUtils.download(url)));
        if (image == null) {
            throw new RuntimeException("Unable to read image");
        }
        image = MapUtils.resize(image, width, height);
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cachedImages[i++] = MapUtils.getSubImage(image, x, y);
            }
        }
        cacheColors();
        send(getViewers());
    }

    @Override
    public void save() throws Exception {
        File folder = new File(manager.getDataFolder(), String.valueOf(imageIndex));
        folder.mkdirs();
        JsonObject json = new JsonObject();
        json.addProperty("type", this.getClass().getName());
        json.addProperty("index", imageIndex);
        json.addProperty("name", name);
        json.addProperty("url", url);
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("creator", creator.toString());
        json.addProperty("creationTime", creationTime);
        JsonArray mapDataJson = new JsonArray();
        for (int i = 0; i < mapViews.size(); i++) {
            JsonObject dataJson = new JsonObject();
            dataJson.addProperty("mapid", mapIds.getInt(i));
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

    public static class URLStaticImageMapRenderer extends ImageMapRenderer {

        private final URLStaticImageMap parent;

        public URLStaticImageMapRenderer(URLStaticImageMap parent, int index) {
            super(parent.getManager(), parent, index);
            this.parent = parent;
        }


        @Override
        public ObjectObjectMutablePair<byte[], Collection<MapCursor>> renderMap(MapView mapView, Player player) {
            byte[] colors;
            if (parent.cachedColors != null && parent.cachedColors[index] != null) {
                colors = parent.cachedColors[index];
            } else if (parent.cachedImages[index] != null) {
                colors = MapPalette.imageToBytes(parent.cachedImages[index]);
            } else {
                colors = null;
            }
            Collection<MapCursor> cursors = parent.getMapMarkers().get(index).values();
            return new ObjectObjectMutablePair<>(colors, cursors);
        }
    }

}
