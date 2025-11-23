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
import com.loohp.imageframe.media.TimedMediaFrameIterator;
import com.loohp.imageframe.utils.CollectionUtils;
import com.loohp.imageframe.utils.MapUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class URLAnimatedImageMap extends URLImageMap {

    protected final LazyMappedBufferedImage[][] cachedImages;

    protected byte[][][] cachedColors;
    protected int[][] fakeMapIds;
    protected Set<Integer> fakeMapIdsSet;
    protected int pausedAt;
    protected int tickOffset;

    protected URLAnimatedImageMap(ImageMapManager manager, ImageMapLoader<?, ?> loader, int imageIndex, String name, String url, LazyMappedBufferedImage[][] cachedImages, List<MapView> mapViews, List<Integer> mapIds, List<Map<String, MapCursor>> mapMarkers, int width, int height, DitheringType ditheringType, UUID creator, Map<UUID, ImageMapAccessPermissionType> hasAccess, long creationTime, int pausedAt, int tickOffset) {
        super(manager, loader, imageIndex, name, url, mapViews, mapIds, mapMarkers, width, height, ditheringType, creator, hasAccess, creationTime);
        this.cachedImages = cachedImages;
        this.pausedAt = pausedAt;
        this.tickOffset = tickOffset;
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
        byte[][][] cachedColors = new byte[cachedImages.length][][];
        int[][] fakeMapIds = new int[cachedColors.length][];
        Set<Integer> fakeMapIdsSet = new HashSet<>();
        BufferedImage[] combined = new BufferedImage[cachedImages[0].length];
        for (int i = 0; i < combined.length; i++) {
            combined[i] = new BufferedImage(width * MapUtils.MAP_WIDTH, height * MapUtils.MAP_WIDTH, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D[] g = Arrays.stream(combined).map(i -> i.createGraphics()).toArray(Graphics2D[]::new);
        int index = 0;
        for (LazyMappedBufferedImage[] images : cachedImages) {
            int f = 0;
            for (LazyMappedBufferedImage image : images) {
                //noinspection SuspiciousNameCombination
                g[f++].drawImage(image.get(), (index % width) * MapUtils.MAP_WIDTH, (index / width) * MapUtils.MAP_WIDTH, MapUtils.MAP_WIDTH, MapUtils.MAP_WIDTH, null);
            }
            index++;
        }
        for (Graphics2D g2 : g) {
            g2.dispose();
        }
        byte[][] combinedData = new byte[combined.length][];
        for (int i = 0; i < combined.length; i++) {
            combinedData[i] = MapUtils.toMapPaletteBytes(combined[i], ditheringType);
        }
        int i = 0;
        for (LazyMappedBufferedImage[] images : cachedImages) {
            byte[][] data = new byte[images.length][];
            int[] mapIds = new int[data.length];
            Arrays.fill(mapIds, -1);
            byte[] lastDistinctFrame = null;
            for (int u = 0; u < images.length; u++) {
                byte[] b = new byte[MapUtils.MAP_WIDTH * MapUtils.MAP_WIDTH];
                for (int y = 0; y < MapUtils.MAP_WIDTH; y++) {
                    int offset = ((i / width) * MapUtils.MAP_WIDTH + y) * (width * MapUtils.MAP_WIDTH) + ((i % width) * MapUtils.MAP_WIDTH);
                    System.arraycopy(combinedData[u], offset, b, y * MapUtils.MAP_WIDTH, MapUtils.MAP_WIDTH);
                }
                if (u == 0 || !Arrays.equals(b, lastDistinctFrame)) {
                    data[u] = b;
                    int mapId = ImageMapManager.getNextFakeMapId();
                    mapIds[u] = mapId;
                    fakeMapIdsSet.add(mapId);
                    lastDistinctFrame = b;
                }
            }
            cachedColors[i] = data;
            fakeMapIds[i] = mapIds;
            i++;
        }
        this.cachedColors = cachedColors;
        this.fakeMapIds = fakeMapIds;
        this.fakeMapIdsSet = fakeMapIdsSet;
    }

    @Override
    public boolean applyUpdate(JsonObject json) {
        this.pausedAt = json.get("pausedAt").getAsInt();
        this.tickOffset = json.get("tickOffset").getAsInt();
        return super.applyUpdate(json);
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
    public void update(boolean save) throws Exception {
        List<BufferedImage> images = CollectionUtils.toList(new TimedMediaFrameIterator(loader.tryLoadMedia(url), 50));
        for (int i = 0; i < cachedImages.length; i++) {
            cachedImages[i] = new LazyMappedBufferedImage[images.size()];
        }
        int index = 0;
        Map<IntPosition, LazyMappedBufferedImage> previousImages = new HashMap<>();
        for (BufferedImage image : images) {
            image = MapUtils.resize(image, width, height);
            int i = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    IntPosition intPosition = new IntPosition(x, y);
                    BufferedImage subImage = MapUtils.getSubImage(image, x, y);
                    LazyMappedBufferedImage previousFile = previousImages.get(intPosition);
                    LazyMappedBufferedImage file;
                    if (previousFile == null || !MapUtils.areImagesEqual(subImage, previousFile.getIfLoaded())) {
                        file = StandardLazyMappedBufferedImage.fromImage(subImage);
                    } else {
                        file = previousFile;
                    }
                    cachedImages[i++][index] = file;
                    previousImages.put(intPosition, file);
                }
            }
            index++;
        }
        reloadColorCache();
        Bukkit.getPluginManager().callEvent(new ImageMapUpdatedEvent(this));
        if (save) {
            save();
        }
    }

    @Override
    public boolean requiresAnimationService() {
        return true;
    }

    @Override
    public int getCurrentPositionInSequenceWithOffset() {
        if (isAnimationPaused()) {
            return pausedAt;
        }
        int sequenceLength = getSequenceLength();
        int currentPosition = (int) (manager.getCurrentAnimationTick() % sequenceLength) - tickOffset;
        if (currentPosition < 0) {
            currentPosition = sequenceLength + currentPosition;
        }
        return currentPosition;
    }

    @Override
    public boolean isAnimationPaused() {
        return pausedAt >= 0;
    }

    @Override
    public synchronized void setAnimationPause(boolean pause) throws Exception {
        if (pausedAt < 0 && pause) {
            pausedAt = getCurrentPositionInSequenceWithOffset();
            save();
        } else if (pausedAt >= 0 && !pause) {
            setCurrentPositionInSequence(pausedAt);
            pausedAt = -1;
            save();
        }
    }

    @Override
    public void setCurrentPositionInSequence(int position) {
        int sequenceLength = getSequenceLength();
        tickOffset = (int) (manager.getCurrentAnimationTick() % sequenceLength) - position % sequenceLength;
    }

    @Override
    public synchronized void setAnimationPlaybackTime(double seconds) throws Exception {
        int totalTicks = getSequenceLength();
        int ticks;
        if (seconds < 0) {
            ticks = totalTicks + (int) Math.ceil((seconds + 1) * 20);
        } else {
            ticks = (int) Math.floor(seconds * 20);
        }
        ticks = Math.min(Math.max(0, ticks), totalTicks - 1);
        if (isAnimationPaused()) {
            pausedAt = ticks;
            save();
        } else {
            setCurrentPositionInSequence(ticks);
            save();
        }
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
    public int getAnimationFakeMapId(int currentTick, int index, boolean lookbehind) {
        if (fakeMapIds == null) {
            return -1;
        }
        int[] mapIds = fakeMapIds[index];
        if (mapIds == null) {
            return -1;
        }
        int mapIdIndex = currentTick % mapIds.length;
        int mapId = mapIds[mapIdIndex];
        if (mapId >= 0 || !lookbehind) {
            return mapId;
        }
        for (; mapIdIndex >= 0; mapIdIndex--) {
            mapId = mapIds[mapIdIndex];
            if (mapId >= 0) {
                return mapId;
            }
        }
        return mapId;
    }

    @Override
    public void sendAnimationFakeMaps(Collection<? extends Player> players, MapPacketSentCallback completionCallback) {
        int length = getSequenceLength();
        for (int currentTick = 0; currentTick < length; currentTick++) {
            for (int index = 0; index < fakeMapIds.length; index++) {
                int[] mapIds = fakeMapIds[index];
                if (mapIds != null && currentTick < mapIds.length) {
                    int mapId = mapIds[currentTick];
                    if (mapId >= 0) {
                        MapUtils.sendImageMap(mapId, mapViews.get(index), currentTick, players, completionCallback);
                    }
                }
            }
        }
    }

    @Override
    public Set<Integer> getFakeMapIds() {
        return fakeMapIdsSet;
    }

    @Override
    public int getSequenceLength() {
        return cachedImages[0].length;
    }

    @Override
    public ImageMap deepClone(String name, UUID creator) throws Exception {
        URLAnimatedImageMap imageMap = ((URLAnimatedImageMapLoader) loader).create(new URLImageMapCreateInfo(manager, name, url, width, height, ditheringType, creator)).get();
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
        JsonObject json = new JsonObject();
        json.addProperty("type", loader.getIdentifier().asString());
        json.addProperty("index", imageIndex);
        json.addProperty("name", name);
        json.addProperty("url", url);
        json.addProperty("width", width);
        json.addProperty("height", height);
        if (ditheringType != null) {
            json.addProperty("ditheringType", ditheringType.getName());
        }
        json.addProperty("creator", creator.toString());
        json.addProperty("pausedAt", pausedAt);
        json.addProperty("tickOffset", tickOffset);
        JsonObject accessJson = new JsonObject();
        for (Map.Entry<UUID, ImageMapAccessPermissionType> entry : accessControl.getPermissions().entrySet()) {
            accessJson.addProperty(entry.getKey().toString(), entry.getValue().name());
        }
        json.add("hasAccess", accessJson);
        json.addProperty("creationTime", creationTime);
        JsonArray mapDataJson = new JsonArray();
        int u = 0;
        for (int i = 0; i < mapViews.size(); i++) {
            JsonObject dataJson = new JsonObject();
            dataJson.addProperty("mapid", mapIds.get(i));
            JsonArray framesArray = new JsonArray();
            for (LazyMappedBufferedImage image : cachedImages[i]) {
                int index = u++;
                LazyBufferedImageSource source = manager.getStorage().getSource(imageIndex, index + ".png");
                if (image.canSetSource(source)) {
                    image.setSource(source);
                    framesArray.add(index + ".png");
                } else {
                    framesArray.add(image.getSource().getFileName());
                }
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
        manager.getStorage().saveImageMapData(imageIndex, json);
    }

    public static class URLAnimatedImageMapRenderer extends ImageMapRenderer {

        private final URLAnimatedImageMap parent;

        public URLAnimatedImageMapRenderer(URLAnimatedImageMap parent, int index) {
            super(parent.getManager(), parent, index);
            this.parent = parent;
        }

        @Override
        public MutablePair<byte[], Collection<MapCursor>> renderMap(MapView mapView, int currentTick, Player player) {
            byte[] colors = parent.getRawAnimationColors(currentTick, index);
            Collection<MapCursor> cursors = parent.getMapMarkers().get(index).values();
            return new MutablePair<>(colors, cursors);
        }

        @Override
        public MutablePair<byte[], Collection<MapCursor>> renderMap(MapView mapView, Player player) {
            return renderMap(mapView, parent.getCurrentPositionInSequenceWithOffset(), player);
        }
    }

}
