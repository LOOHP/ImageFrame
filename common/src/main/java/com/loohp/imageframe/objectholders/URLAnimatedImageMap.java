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
import com.loohp.imageframe.utils.GifReader;
import com.loohp.imageframe.utils.HTTPRequestUtils;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public class URLAnimatedImageMap extends URLImageMap {

    public static Future<? extends URLAnimatedImageMap> create(ImageMapManager manager, String name, String url, int width, int height, DitheringType ditheringType, UUID creator) throws Exception {
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
            MapView mapView = future.get();
            Scheduler.runTask(ImageFrame.plugin, () -> {
                for (MapRenderer renderer : mapView.getRenderers()) {
                    mapView.removeRenderer(renderer);
                }
            });
            mapViews.add(mapView);
            mapIds.add(mapView.getId());
        }
        URLAnimatedImageMap map = new URLAnimatedImageMap(manager, -1, name, url, new FileLazyMappedBufferedImage[mapsCount][], mapViews, mapIds, markers, width, height, ditheringType, creator, Collections.emptyMap(), System.currentTimeMillis(), -1, 0);
        return FutureUtils.callAsyncMethod(() -> {
            FutureUtils.callSyncMethod(() -> {
                for (int i = 0; i < mapViews.size(); i++) {
                    mapViews.get(i).addRenderer(new URLAnimatedImageMapRenderer(map, i));
                }
            }).get();
            map.update(false);
            return map;
        });
    }

    @SuppressWarnings("unused")
    public static Future<? extends URLAnimatedImageMap> load(ImageMapManager manager, File folder, JsonObject json) throws Exception {
        if (!json.get("type").getAsString().equals(URLAnimatedImageMap.class.getName())) {
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
        FileLazyMappedBufferedImage[][] cachedImages = new FileLazyMappedBufferedImage[mapDataJson.size()][];
        List<Map<String, MapCursor>> markers = new ArrayList<>(mapDataJson.size());
        World world = Bukkit.getWorlds().get(0);
        int i = 0;
        for (JsonElement dataJson : mapDataJson) {
            JsonObject jsonObject = dataJson.getAsJsonObject();
            int mapId = jsonObject.get("mapid").getAsInt();
            mapIds.add(mapId);
            mapViewsFuture.add(MapUtils.getMapOrCreateMissing(world, mapId));
            JsonArray framesArray = jsonObject.get("images").getAsJsonArray();
            FileLazyMappedBufferedImage[] images = new FileLazyMappedBufferedImage[framesArray.size()];
            int u = 0;
            for (JsonElement element : framesArray) {
                images[u++] = FileLazyMappedBufferedImage.fromFile(new File(folder, element.getAsString()));
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
        List<MapView> mapViews = new ArrayList<>(mapViewsFuture.size());
        for (Future<MapView> future : mapViewsFuture) {
            mapViews.add(future.get());
        }
        int pausedAt = json.has("pausedAt") ? json.get("pausedAt").getAsInt() : -1;
        int tickOffset = json.has("tickOffset") ? json.get("tickOffset").getAsInt() : 0;
        URLAnimatedImageMap map = new URLAnimatedImageMap(manager, imageIndex, name, url, cachedImages, mapViews, mapIds, markers, width, height, ditheringType, creator, hasAccess, creationTime, pausedAt, tickOffset);
        return FutureUtils.callSyncMethod(() -> {
            for (int u = 0; u < mapViews.size(); u++) {
                MapView mapView = mapViews.get(u);
                for (MapRenderer renderer : mapView.getRenderers()) {
                    mapView.removeRenderer(renderer);
                }
                mapView.addRenderer(new URLAnimatedImageMapRenderer(map, u));
            }
            return map;
        });
    }

    protected final FileLazyMappedBufferedImage[][] cachedImages;

    protected byte[][][] cachedColors;
    protected int[][] fakeMapIds;
    protected Set<Integer> fakeMapIdsSet;
    protected int pausedAt;
    protected int tickOffset;

    protected URLAnimatedImageMap(ImageMapManager manager, int imageIndex, String name, String url, FileLazyMappedBufferedImage[][] cachedImages, List<MapView> mapViews, List<Integer> mapIds, List<Map<String, MapCursor>> mapMarkers, int width, int height, DitheringType ditheringType, UUID creator, Map<UUID, ImageMapAccessPermissionType> hasAccess, long creationTime, int pausedAt, int tickOffset) {
        super(manager, imageIndex, name, url, mapViews, mapIds, mapMarkers, width, height, ditheringType, creator, hasAccess, creationTime);
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
        for (FileLazyMappedBufferedImage[] images : cachedImages) {
            int f = 0;
            for (FileLazyMappedBufferedImage image : images) {
                g[f++].drawImage(image.get(), (index % width) * MapUtils.MAP_WIDTH, (index / width) * MapUtils.MAP_WIDTH, null);
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
        for (FileLazyMappedBufferedImage[] images : cachedImages) {
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
    public boolean hasColorCached() {
        return cachedColors != null;
    }

    @Override
    public void unloadColorCache() {
        cachedColors = null;
    }

    @Override
    public void update(boolean save) throws Exception {
        List<GifReader.ImageFrame> frames;
        try {
            frames = GifReader.readGif(HTTPRequestUtils.getInputStream(url), ImageFrame.maxImageFileSize).get();
        } catch (Exception e) {
            throw new RuntimeException("Unable to read or download animated gif, does this url directly links to the gif? (" + url + ")", e);
        }
        List<BufferedImage> images = new ArrayList<>();
        for (int currentTime = 0; ; currentTime += 50) {
            int index = GifReader.getFrameAt(frames, currentTime);
            if (index < 0) {
                break;
            }
            images.add(frames.get(index).getImage());
        }
        for (int i = 0; i < cachedImages.length; i++) {
            cachedImages[i] = new FileLazyMappedBufferedImage[images.size()];
        }
        int index = 0;
        Map<IntPosition, FileLazyMappedBufferedImage> previousImages = new HashMap<>();
        for (BufferedImage image : images) {
            image = MapUtils.resize(image, width, height);
            int i = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    IntPosition intPosition = new IntPosition(x, y);
                    BufferedImage subImage = MapUtils.getSubImage(image, x, y);
                    FileLazyMappedBufferedImage previousFile = previousImages.get(intPosition);
                    FileLazyMappedBufferedImage file;
                    if (previousFile == null || !MapUtils.areImagesEqual(subImage, previousFile.getIfLoaded())) {
                        file = FileLazyMappedBufferedImage.fromImage(subImage);
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
        int currentPosition = manager.getCurrentAnimationTick() % sequenceLength - tickOffset;
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
        tickOffset = manager.getCurrentAnimationTick() % sequenceLength - position % sequenceLength;
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
        URLAnimatedImageMap imageMap = create(manager, name, url, width, height, ditheringType, creator).get();
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
            for (FileLazyMappedBufferedImage image : cachedImages[i]) {
                int index = u++;
                File file = new File(folder, index + ".png");
                if (image.canSetFile(file)) {
                    image.setFile(file);
                    framesArray.add(index + ".png");
                } else {
                    framesArray.add(image.getFile().getName());
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
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(new File(folder, "data.json").toPath()), StandardCharsets.UTF_8))) {
            pw.println(GSON.toJson(json));
            pw.flush();
        }
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
