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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.nms.NMS;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.imageframe.utils.PlayerUtils;
import com.loohp.imageframe.utils.StringUtils;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class ImageMap {

    public static final UUID CONSOLE_CREATOR = new UUID(0, 0);
    public static final String CONSOLE_CREATOR_NAME = "Console";
    public static final String UNKNOWN_CREATOR_NAME = "???";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    @SuppressWarnings("unchecked")
    public static Future<? extends ImageMap> load(ImageMapManager manager, File folder) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(new File(folder, "data.json").toPath()), StandardCharsets.UTF_8))) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            String type = json.get("type").getAsString();
            return (Future<? extends ImageMap>) Class.forName(type).getMethod("load", ImageMapManager.class, File.class, JsonObject.class).invoke(null, manager, folder, json);
        }
    }

    protected final ImageMapManager manager;

    protected int imageIndex;
    protected String name;
    protected final List<MapView> mapViews;
    protected final List<Integer> mapIds;
    protected final List<Map<String, MapCursor>> mapMarkers;
    protected final int width;
    protected final int height;
    protected DitheringType ditheringType;
    protected UUID creator;
    protected ImageMapAccessControl accessControl;
    protected final long creationTime;

    protected final ImageMapCacheControlTask cacheControlTask;
    private boolean isValid;

    public ImageMap(ImageMapManager manager, int imageIndex, String name, List<MapView> mapViews, List<Integer> mapIds, List<Map<String, MapCursor>> mapMarkers, int width, int height, DitheringType ditheringType, UUID creator, Map<UUID, ImageMapAccessPermissionType> hasAccess, long creationTime) {
        if (mapViews.size() != width * height) {
            throw new IllegalArgumentException("mapViews size does not equal width * height");
        }
        if (mapViews.size() != mapMarkers.size()) {
            throw new IllegalArgumentException("mapViews size does not equal mapMarkers size");
        }
        if (mapViews.size() != mapIds.size()) {
            throw new IllegalArgumentException("mapViews size does not equal mapIds size");
        }
        this.manager = manager;
        this.imageIndex = imageIndex;
        this.name = StringUtils.sanitize(name);
        this.mapViews = Collections.unmodifiableList(mapViews);
        this.mapIds = Collections.unmodifiableList(mapIds);
        this.mapMarkers = Collections.unmodifiableList(mapMarkers);
        this.width = width;
        this.height = height;
        this.ditheringType = ditheringType;
        this.creator = creator;
        this.accessControl = new ImageMapAccessControl(this, hasAccess);
        this.creationTime = creationTime;

        this.cacheControlTask = ImageFrame.cacheControlMode.newInstance(this);
        this.isValid = true;

        this.accessControl.setPermissionWithoutSave(creator, null);
    }

    public ImageMapManager getManager() {
        return manager;
    }

    protected abstract void loadColorCache();

    protected void reloadColorCache() {
        if (hasColorCached()) {
            loadColorCache();
        } else {
            cacheControlTask.loadCacheIfManual();
        }
    }

    protected abstract boolean hasColorCached();

    protected abstract void unloadColorCache();

    public BufferedImage getHighResImage(int mapId) {
        return null;
    }

    public int getImageIndex() {
        return imageIndex;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) throws Exception {
        this.name = StringUtils.sanitize(name);
        save();
    }

    public List<Integer> getMapIds() {
        return mapIds;
    }

    protected void markInvalid() {
        this.isValid = false;
        this.cacheControlTask.close();
    }

    public boolean isValid() {
        return isValid;
    }

    public void stop() {
        for (MapView mapView : mapViews) {
            for (MapRenderer mapRenderer : mapView.getRenderers()) {
                mapView.removeRenderer(mapRenderer);
            }
        }
    }

    public boolean requiresAnimationService() {
        return false;
    }

    public int getCurrentPositionInSequenceWithOffset() {
        return 0;
    }

    public boolean isAnimationPaused() {
        throw new UnsupportedOperationException("this map does not require animation");
    }

    public void setAnimationPause(boolean pause) throws Exception {
        throw new UnsupportedOperationException("this map does not require animation");
    }

    public void setCurrentPositionInSequence(int position) {
        //do nothing
    }

    public void setAnimationPlaybackTime(double seconds) throws Exception {
        //do nothing
    }

    public int getCurrentPositionInSequence() {
        return getCurrentPositionInSequenceWithOffset() % getSequenceLength();
    }

    public int getSequenceLength() {
        return 1;
    }

    public byte[] getRawAnimationColors(int currentTick, int index) {
        throw new UnsupportedOperationException("this map does not require animation");
    }

    public int getAnimationFakeMapId(int currentTick, int index, boolean lookbehind) {
        throw new UnsupportedOperationException("this map does not require animation");
    }

    public void sendAnimationFakeMaps(Collection<? extends Player> players, MapPacketSentCallback completionCallback) {
        throw new UnsupportedOperationException("this map does not require animation");
    }

    public Set<Integer> getFakeMapIds() {
        throw new UnsupportedOperationException("this map does not require animation");
    }

    public boolean trackDeletedMaps() {
        return true;
    }

    public DitheringType getDitheringType() {
        return ditheringType == null ? DitheringType.NEAREST_COLOR : ditheringType;
    }

    public void setDitheringType(DitheringType ditheringType) throws Exception {
        this.ditheringType = ditheringType;
        save();
    }

    public abstract ImageMap deepClone(String name, UUID creator) throws Exception;

    public abstract void update(boolean save) throws Exception;

    public void update() throws Exception {
        update(true);
    }

    public void send(Player player) {
        send(Collections.singleton(player));
    }

    public void send(Collection<? extends Player> players) {
        for (MapView mapView : mapViews) {
            MapUtils.sendImageMap(mapView, players);
        }
    }

    public abstract void save() throws Exception;

    public ItemStack getMap(int x, int y, String mapNameFormat) {
        return getMap(x, y, mapNameFormat, itemStack -> itemStack);
    }

    public ItemStack getMap(int x, int y, String mapNameFormat, Function<ItemStack, ItemStack> postCreationFunction) {
        if (x >= width || y >= height) {
            throw new IndexOutOfBoundsException("x, y position out of image map size");
        }
        MapView mapView = mapViews.get(y * width + x);
        ItemStack itemStack = new ItemStack(Material.FILLED_MAP);
        MapMeta mapMeta = (MapMeta) itemStack.getItemMeta();
        mapMeta.setMapView(mapView);
        mapMeta.setLore(Collections.singletonList(mapNameFormat
                .replace("{ImageID}", getImageIndex() + "")
                .replace("{X}", x + "")
                .replace("{Y}", y + "")
                .replace("{Name}", getName())
                .replace("{Width}", getWidth() + "")
                .replace("{Height}", getHeight() + "")
                .replace("{DitheringType}", getDitheringType().getName())
                .replace("{CreatorName}", getCreatorName())
                .replace("{CreatorUUID}", getCreator().toString())
                .replace("{TimeCreated}", ImageFrame.dateFormat.format(new Date(getCreationTime())))));
        itemStack.setItemMeta(mapMeta);
        return postCreationFunction.apply(itemStack);
    }

    public List<ItemStack> getMaps(String mapNameFormat) {
        return getMaps(mapNameFormat, itemStack -> itemStack);
    }

    public List<ItemStack> getMaps(String mapNameFormat, Function<ItemStack, ItemStack> postCreationFunction) {
        List<ItemStack> maps = new ArrayList<>(mapViews.size());
        int i = 0;
        for (MapView mapView : mapViews) {
            ItemStack itemStack = new ItemStack(Material.FILLED_MAP);
            MapMeta mapMeta = (MapMeta) itemStack.getItemMeta();
            mapMeta.setMapView(mapView);
            mapMeta.setLore(Collections.singletonList(mapNameFormat
                    .replace("{ImageID}", getImageIndex() + "")
                    .replace("{X}", (i % width) + "")
                    .replace("{Y}", (i / width) + "")
                    .replace("{Name}", getName())
                    .replace("{Width}", getWidth() + "")
                    .replace("{Height}", getHeight() + "")
                    .replace("{DitheringType}", getDitheringType().getName())
                    .replace("{CreatorName}", getCreatorName())
                    .replace("{CreatorUUID}", getCreator().toString())
                    .replace("{TimeCreated}", ImageFrame.dateFormat.format(new Date(getCreationTime())))));
            itemStack.setItemMeta(mapMeta);
            maps.add(postCreationFunction.apply(itemStack));
            i++;
        }
        return maps;
    }

    public void giveMap(Player player, int x, int y, String mapNameFormat) {
        giveMap(Collections.singleton(player), x, y, mapNameFormat, itemStack -> itemStack);
    }

    public void giveMap(Player player, int x, int y, String mapNameFormat, Function<ItemStack, ItemStack> postCreationFunction) {
        giveMap(Collections.singleton(player), x, y, mapNameFormat, postCreationFunction);
    }

    public void giveMap(Collection<? extends Player> players, int x, int y, String mapNameFormat) {
        giveMap(players, x, y, mapNameFormat, itemStack -> itemStack);
    }

    public void giveMap(Collection<? extends Player> players, int x, int y, String mapNameFormat, Function<ItemStack, ItemStack> postCreationFunction) {
        ItemStack map = getMap(x, y, mapNameFormat, postCreationFunction);
        for (Player player : players) {
            PlayerUtils.giveItem(player, map.clone());
        }
    }

    public void giveMaps(Player player, String mapNameFormat) {
        giveMaps(Collections.singleton(player), mapNameFormat, itemStack -> itemStack);
    }

    public void giveMaps(Player player, String mapNameFormat, Function<ItemStack, ItemStack> postCreationFunction) {
        giveMaps(Collections.singleton(player), mapNameFormat, postCreationFunction);
    }

    public void giveMaps(Collection<? extends Player> players, String mapNameFormat) {
        giveMaps(players, mapNameFormat, itemStack -> itemStack);
    }

    public void giveMaps(Collection<? extends Player> players, String mapNameFormat, Function<ItemStack, ItemStack> postCreationFunction) {
        List<ItemStack> maps = getMaps(mapNameFormat, postCreationFunction);
        for (ItemStack map : maps) {
            for (Player player : players) {
                PlayerUtils.giveItem(player, map.clone());
            }
        }
    }

    public void fillItemFrames(List<ItemFrame> itemFrames, Rotation rotation, BiPredicate<ItemFrame, ItemStack> prePlaceCheck, BiConsumer<ItemFrame, ItemStack> unableToPlaceAction, String mapNameFormat) {
        fillItemFrames(itemFrames, rotation, prePlaceCheck, unableToPlaceAction, mapNameFormat, itemStack -> itemStack);
    }

    public void fillItemFrames(List<ItemFrame> itemFrames, Rotation rotation, BiPredicate<ItemFrame, ItemStack> prePlaceCheck, BiConsumer<ItemFrame, ItemStack> unableToPlaceAction, String mapNameFormat, Function<ItemStack, ItemStack> postCreationFunction) {
        if (itemFrames.size() != mapViews.size()) {
            throw new IllegalArgumentException("itemFrames size does not equal to mapView size");
        }
        List<ItemStack> items = getMaps(mapNameFormat, postCreationFunction);
        Iterator<ItemFrame> itr0 = itemFrames.iterator();
        Iterator<ItemStack> itr1 = items.iterator();
        while (itr0.hasNext() && itr1.hasNext()) {
            ItemFrame frame = itr0.next();
            ItemStack item = itr1.next();
            Scheduler.runTask(ImageFrame.plugin, () -> {
                if (frame.isValid()) {
                    if (prePlaceCheck.test(frame, item)) {
                        frame.setItem(item, false);
                        frame.setRotation(rotation);
                        return;
                    }
                }
                unableToPlaceAction.accept(frame, item);
            }, frame);
        }
    }

    public Set<Player> getViewers() {
        return mapViews.stream().flatMap(m -> NMS.getInstance().getViewers(m).stream()).collect(Collectors.toSet());
    }

    public boolean hasViewers() {
        return mapViews.stream().anyMatch(m -> NMS.getInstance().hasViewers(m));
    }

    public UUID getCreator() {
        return creator;
    }

    public void changeCreator(UUID creator) throws Exception {
        this.creator = creator;
        this.accessControl.setPermissionWithoutSave(creator, null);
        save();
    }

    public ImageMapAccessControl getAccessControl() {
        return accessControl;
    }

    public String getCreatorName() {
        if (creator.equals(CONSOLE_CREATOR)) {
            return CONSOLE_CREATOR_NAME;
        }
        String name = Bukkit.getOfflinePlayer(creator).getName();
        return name == null ? UNKNOWN_CREATOR_NAME : name;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public List<MapView> getMapViews() {
        return mapViews;
    }

    public MapView getMapViewFromMapId(int mapId) {
        return mapViews.get(mapIds.indexOf(mapId));
    }

    public List<Map<String, MapCursor>> getMapMarkers() {
        return mapMarkers;
    }

    public Map<String, MapCursor> getMapMarkers(MapView mapView) {
        return mapMarkers.get(mapViews.indexOf(mapView));
    }

    public MapCursor getMapMarker(String name) {
        return mapMarkers.stream().flatMap(each -> each.entrySet().stream()).filter(each -> each.getKey().equalsIgnoreCase(name)).findFirst().map(each -> each.getValue()).orElse(null);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public static abstract class ImageMapRenderer extends MapRenderer {

        protected final ImageMapManager manager;
        protected final ImageMap imageMap;
        protected final int index;

        public ImageMapRenderer(ImageMapManager manager, ImageMap imageMap, int index) {
            super(ImageFrame.mapRenderersContextual);
            this.manager = manager;
            this.imageMap = imageMap;
            this.index = index;
        }

        @Override
        public void render(MapView mapView, MapCanvas canvas, Player player) {
            MutablePair<byte[], Collection<MapCursor>> renderData = renderMap(mapView, 0, player);
            manager.callRenderEventListener(manager, imageMap, mapView, player, renderData);
            byte[] colors = renderData.getFirst();
            if (colors != null) {
                for (int i = 0; i < colors.length; i++) {
                    canvas.setPixel(i % MapUtils.MAP_WIDTH, i / MapUtils.MAP_WIDTH, colors[i]);
                }
            }
            canvas.setCursors(MapUtils.toMapCursorCollection(renderData.getSecond()));
        }

        public MutablePair<byte[], Collection<MapCursor>> renderPacketData(MapView mapView, int currentTick, Player player) {
            MutablePair<byte[], Collection<MapCursor>> renderData = renderMap(mapView, currentTick, player);
            manager.callRenderEventListener(manager, imageMap, mapView, player, renderData);
            return renderData;
        }

        public MutablePair<byte[], Collection<MapCursor>> renderPacketData(MapView mapView, Player player) {
            MutablePair<byte[], Collection<MapCursor>> renderData = renderMap(mapView, player);
            manager.callRenderEventListener(manager, imageMap, mapView, player, renderData);
            return renderData;
        }

        public MutablePair<byte[], Collection<MapCursor>> renderMap(MapView mapView, int currentTick, Player player) {
            return renderMap(mapView, player);
        }

        public abstract MutablePair<byte[], Collection<MapCursor>> renderMap(MapView mapView, Player player);

    }

}
