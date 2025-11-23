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
import com.google.gson.JsonObject;
import com.loohp.imageframe.api.events.ImageMapUpdatedEvent;
import com.loohp.imageframe.utils.MapUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NonUpdatableStaticImageMap extends ImageMap {

    protected final LazyMappedBufferedImage[] cachedImages;

    protected byte[][] cachedColors;

    protected NonUpdatableStaticImageMap(ImageMapManager manager, ImageMapLoader<?, ?> loader, int imageIndex, String name, LazyMappedBufferedImage[] cachedImages, List<MapView> mapViews, List<Integer> mapIds, List<Map<String, MapCursor>> mapMarkers, int width, int height, DitheringType ditheringType, UUID creator, Map<UUID, ImageMapAccessPermissionType> hasAccess, long creationTime) {
        super(manager, loader, imageIndex, name, mapViews, mapIds, mapMarkers, width, height, ditheringType, creator, hasAccess, creationTime);
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
        for (LazyMappedBufferedImage image : cachedImages) {
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
    public BufferedImage getOriginalImage(int mapId) {
        int index = mapIds.indexOf(mapId);
        if (index < 0 || index >= cachedImages.length) {
            return null;
        }
        return cachedImages[index].get();
    }

    @Override
    public ImageMap deepClone(String name, UUID creator) throws Exception {
        BufferedImage[] images = new BufferedImage[cachedImages.length];
        for (int i = 0; i < images.length; i++) {
            BufferedImage image = cachedImages[i].get();
            BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = newImage.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            images[i] = newImage;
        }
        NonUpdatableStaticImageMap imageMap = ((NonUpdatableStaticImageMapLoader) loader).create(new NonUpdatableImageMapCreateInfo(manager, name, images, width, height, ditheringType, creator)).get();
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
        JsonObject json = new JsonObject();
        json.addProperty("type", loader.getIdentifier().asString());
        json.addProperty("index", imageIndex);
        json.addProperty("name", name);
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
        for (int i = 0; i < cachedImages.length; i++) {
            cachedImages[i].setSource(manager.getStorage().getSource(imageIndex, i + ".png"));
        }
        manager.getStorage().saveImageMapData(imageIndex, json);
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
                colors = MapUtils.toMapPaletteBytes(parent.cachedImages[index].get(), parent.ditheringType);
            } else {
                colors = null;
            }
            Collection<MapCursor> cursors = parent.getMapMarkers().get(index).values();
            return new MutablePair<>(colors, cursors);
        }
    }

}
