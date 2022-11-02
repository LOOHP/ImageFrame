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
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
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
import java.util.List;
import java.util.UUID;

public class URLStaticImageMap extends URLImageMap {

    public static URLStaticImageMap create(ImageMapManager manager, String name, String url, int width, int height, UUID creator) throws Exception {
        World world = Bukkit.getWorlds().get(0);
        int mapsCount = width * height;
        List<MapView> mapViews = new ArrayList<>(mapsCount);
        IntList mapIds = new IntArrayList(mapsCount);
        for (int i = 0; i < mapsCount; i++) {
            MapView mapView = Bukkit.createMap(world);
            for (MapRenderer renderer : mapView.getRenderers()) {
                mapView.removeRenderer(renderer);
            }
            mapViews.add(mapView);
            mapIds.add(mapView.getId());
        }
        URLStaticImageMap map = new URLStaticImageMap(manager, -1, name, url, new BufferedImage[mapsCount], mapViews, mapIds, width, height, creator, System.currentTimeMillis());
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
        int i = 0;
        for (JsonElement dataJson : mapDataJson) {
            JsonObject jsonObject = dataJson.getAsJsonObject();
            int mapId = jsonObject.get("mapid").getAsInt();
            mapIds.add(mapId);
            mapViews.add(Bukkit.getMap(mapId));
            cachedImages[i] = ImageIO.read(new File(folder, jsonObject.get("image").getAsString()));
            i++;
        }
        URLStaticImageMap map = new URLStaticImageMap(manager, imageIndex, name, url, cachedImages, mapViews, mapIds, width, height, creator, creationTime);
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

    private URLStaticImageMap(ImageMapManager manager, int imageIndex, String name, String url, BufferedImage[] cachedImages, List<MapView> mapViews, IntList mapIds, int width, int height, UUID creator, long creationTime) {
        super(manager, imageIndex, name, url, mapViews, mapIds, width, height, creator, creationTime);
        this.cachedImages = cachedImages;
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

    public static class URLStaticImageMapRenderer extends MapRenderer {

        private final URLStaticImageMap parent;
        private final int index;

        public URLStaticImageMapRenderer(URLStaticImageMap parent, int index) {
            this.parent = parent;
            this.index = index;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (parent.cachedImages[index] != null) {
                canvas.drawImage(0, 0, parent.cachedImages[index]);
            }
        }
    }

}
