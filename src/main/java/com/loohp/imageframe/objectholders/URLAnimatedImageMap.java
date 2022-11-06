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
import com.loohp.imageframe.utils.GifReader;
import com.loohp.imageframe.utils.HTTPRequestUtils;
import com.loohp.imageframe.utils.MapUtils;
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

public class URLAnimatedImageMap extends URLImageMap {

    public static URLAnimatedImageMap create(ImageMapManager manager, String name, String url, int width, int height, UUID creator) throws Exception {
        World world = Bukkit.getWorlds().get(0);
        int mapsCount = width * height;
        List<MapView> mapViews = new ArrayList<>(mapsCount);
        List<Integer> mapIds = new ArrayList<>(mapsCount);
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
        URLAnimatedImageMap map = new URLAnimatedImageMap(manager, -1, name, url, new BufferedImage[mapsCount][], mapViews, mapIds, markers, width, height, creator, System.currentTimeMillis());
        for (int i = 0; i < mapViews.size(); i++) {
            MapView mapView = mapViews.get(i);
            mapView.addRenderer(new URLAnimatedImageMapRenderer(map, i));
        }
        map.update();
        return map;
    }

    @SuppressWarnings("unused")
    public static URLAnimatedImageMap load(ImageMapManager manager, File folder, JsonObject json) throws Exception {
        if (!json.get("type").getAsString().equals(URLAnimatedImageMap.class.getName())) {
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
        List<Integer> mapIds = new ArrayList<>(mapDataJson.size());
        BufferedImage[][] cachedImages = new BufferedImage[mapDataJson.size()][];
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapDataJson.size());
        int i = 0;
        for (JsonElement dataJson : mapDataJson) {
            JsonObject jsonObject = dataJson.getAsJsonObject();
            int mapId = jsonObject.get("mapid").getAsInt();
            mapIds.add(mapId);
            mapViews.add(Bukkit.getMap(mapId));
            JsonArray framesArray = jsonObject.get("images").getAsJsonArray();
            BufferedImage[] images = new BufferedImage[framesArray.size()];
            int u = 0;
            for (JsonElement element : framesArray) {
                images[u++] = ImageIO.read(new File(folder, element.getAsString()));
            }
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
            cachedImages[i++] = images;
        }
        URLAnimatedImageMap map = new URLAnimatedImageMap(manager, imageIndex, name, url, cachedImages, mapViews, mapIds, markers, width, height, creator, creationTime);
        for (int u = 0; u < mapViews.size(); u++) {
            MapView mapView = mapViews.get(u);
            for (MapRenderer renderer : mapView.getRenderers()) {
                mapView.removeRenderer(renderer);
            }
            mapView.addRenderer(new URLAnimatedImageMapRenderer(map, u));
        }
        return map;
    }

    private final BufferedImage[][] cachedImages;

    private byte[][][] cachedColors;

    private URLAnimatedImageMap(ImageMapManager manager, int imageIndex, String name, String url, BufferedImage[][] cachedImages, List<MapView> mapViews, List<Integer> mapIds, List<Map<String, MapCursor>> mapMarkers, int width, int height, UUID creator, long creationTime) {
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
        cachedColors = new byte[cachedImages.length][][];
        int i = 0;
        for (BufferedImage[] images : cachedImages) {
            byte[][] data = new byte[images.length][];
            cachedColors[i++] = data;
            int u = 0;
            for (BufferedImage image : images) {
                data[u++] = MapPalette.imageToBytes(image);
            }
        }
    }

    @Override
    public void update() throws Exception {
        List<GifReader.ImageFrame> frames = GifReader.readGif(new ByteArrayInputStream(HTTPRequestUtils.download(url))).get();
        List<BufferedImage> images = new ArrayList<>();
        for (int currentTime = 0; ; currentTime += 50) {
            int index = GifReader.getFrameAt(frames, currentTime);
            if (index < 0) {
                break;
            }
            images.add(frames.get(index).getImage());
        }
        for (int i = 0; i < cachedImages.length; i++) {
            cachedImages[i] = new BufferedImage[images.size()];
        }
        int index = 0;
        for (BufferedImage image : images) {
            image = MapUtils.resize(image, width, height);
            int i = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    cachedImages[i++][index] = MapUtils.getSubImage(image, x, y);
                }
            }
            index++;
        }
        cacheColors();
    }

    @Override
    public boolean requiresAnimationService() {
        return true;
    }

    @Override
    public byte[] getRawAnimationColors(int currentTick, int index) {
        if (cachedColors == null) {
            return null;
        }
        byte[][] colors = cachedColors[index];
        if (colors == null) {
            return null;
        }
        return colors[currentTick % colors.length];
    }

    @Override
    public ImageMap deepClone(String name, UUID creator) throws Exception {
        URLAnimatedImageMap imageMap = create(manager, name, url, width, height, creator);
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
        json.addProperty("creator", creator.toString());
        json.addProperty("creationTime", creationTime);
        JsonArray mapDataJson = new JsonArray();
        int u = 0;
        for (int i = 0; i < mapViews.size(); i++) {
            JsonObject dataJson = new JsonObject();
            dataJson.addProperty("mapid", mapIds.get(i));
            JsonArray framesArray = new JsonArray();
            for (BufferedImage image : cachedImages[i]) {
                framesArray.add(u++ + ".png");
            }
            dataJson.add("images", framesArray);
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
        int i = 0;
        for (BufferedImage[] images : cachedImages) {
            for (BufferedImage image : images) {
                ImageIO.write(image, "png", new File(folder, i++ + ".png"));
            }
        }
    }

    public static class URLAnimatedImageMapRenderer extends ImageMapRenderer {

        private final URLAnimatedImageMap parent;

        public URLAnimatedImageMapRenderer(URLAnimatedImageMap parent, int index) {
            super(parent.getManager(), parent, index);
            this.parent = parent;
        }

        @Override
        public MutablePair<byte[], Collection<MapCursor>> renderMap(MapView mapView, Player player) {
            byte[] colors = parent.getRawAnimationColors(parent.getManager().getCurrentAnimationTick(), index);
            Collection<MapCursor> cursors = parent.getMapMarkers().get(index).values();
            return new MutablePair<>(colors, cursors);
        }
    }

}
