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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.FileUtils;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.platformscheduler.ScheduledTask;
import com.loohp.platformscheduler.Scheduler;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ImageMapManager implements AutoCloseable {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    public static final int FAKE_MAP_ID_START_RANGE = Integer.MAX_VALUE / 4 * 3;

    private static final AtomicInteger FAKE_MAP_ID_COUNTER = new AtomicInteger(FAKE_MAP_ID_START_RANGE);

    public static int getNextFakeMapId() {
        return FAKE_MAP_ID_COUNTER.getAndUpdate(i -> i < FAKE_MAP_ID_START_RANGE ? FAKE_MAP_ID_START_RANGE : i + 1);
    }

    private final Map<Integer, ImageMap> maps;
    private final Map<MapView, ImageMap> mapsByView;
    private final AtomicInteger mapIndexCounter;
    private final File dataFolder;
    private final AtomicInteger tickCounter;
    private final List<ImageMapRenderEventListener> renderEventListeners;
    private final Set<Integer> deletedMapIds;

    private final ScheduledTask tickCounterTask;

    public ImageMapManager(File dataFolder) {
        this.maps = new ConcurrentHashMap<>();
        this.mapsByView = new ConcurrentHashMap<>();
        this.mapIndexCounter = new AtomicInteger(0);
        this.dataFolder = dataFolder;
        this.tickCounter = new AtomicInteger(0);
        this.renderEventListeners = new CopyOnWriteArrayList<>();
        this.deletedMapIds = ConcurrentHashMap.newKeySet();

        this.tickCounterTask = Scheduler.runTaskTimerAsynchronously(ImageFrame.plugin, () -> tickCounter.incrementAndGet(), 0, 1);
    }

    public File getDataFolder() {
        return dataFolder;
    }

    protected int getCurrentAnimationTick() {
        return tickCounter.get();
    }

    @Override
    public void close() {
        saveDeletedMaps();
        tickCounterTask.cancel();
    }

    public void appendRenderEventListener(ImageMapRenderEventListener listener) {
        renderEventListeners.add(listener);
    }

    public void prependRenderEventListener(ImageMapRenderEventListener listener) {
        renderEventListeners.add(0, listener);
    }

    public void removeRenderEventListener(ImageMapRenderEventListener listener) {
        renderEventListeners.remove(listener);
    }

    protected void callRenderEventListener(ImageMapManager manager, ImageMap imageMap, MapView map, Player player, MutablePair<byte[], Collection<MapCursor>> renderData) {
        renderEventListeners.forEach(each -> each.accept(manager, imageMap, map, player, renderData));
    }

    public synchronized void addMap(ImageMap map) throws Exception {
        if (map.getManager() != this) {
            throw new IllegalArgumentException("ImageMap's manager is not set to this");
        }
        if (getFromCreator(map.getCreator(), map.getName()) != null) {
            throw new IllegalArgumentException("Duplicated map name for this creator");
        }
        int originalImageIndex = map.getImageIndex();
        if (originalImageIndex < 0) {
            map.imageIndex = mapIndexCounter.getAndIncrement();
        } else {
            mapIndexCounter.updateAndGet(i -> Math.max(originalImageIndex + 1, i));
        }
        maps.put(map.getImageIndex(), map);
        for (MapView mapView : map.getMapViews()) {
            mapsByView.put(mapView, map);
            deletedMapIds.remove(mapView.getId());
        }
        try {
            map.save();
        } catch (Throwable e) {
            maps.remove(originalImageIndex);
            for (MapView mapView : map.getMapViews()) {
                mapsByView.remove(mapView);
            }
            throw e;
        }
        saveDeletedMaps();
    }

    public boolean hasMap(int imageIndex) {
        return maps.containsKey(imageIndex);
    }

    public Collection<ImageMap> getMaps() {
        return Collections.unmodifiableCollection(maps.values());
    }

    public ImageMap getFromMapId(int id) {
        MapView mapView = Bukkit.getMap(id);
        if (mapView == null) {
            return null;
        }
        return getFromMapView(Bukkit.getMap(id));
    }

    public ImageMap getFromImageId(int imageId) {
        return maps.get(imageId);
    }

    public ImageMap getFromMapView(MapView mapView) {
        return mapsByView.get(mapView);
    }

    public Set<ImageMap> getFromCreator(UUID uuid) {
        return maps.values().stream().filter(each -> each.getCreator().equals(uuid)).collect(Collectors.toSet());
    }

    public List<ImageMap> getFromCreator(UUID uuid, Comparator<ImageMap> order) {
        return maps.values().stream().filter(each -> each.getCreator().equals(uuid)).sorted(order).collect(Collectors.toList());
    }

    public ImageMap getFromCreator(UUID uuid, String name) {
        return maps.values().stream().filter(each -> each.getCreator().equals(uuid) && each.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public Set<UUID> getCreators() {
        return maps.values().stream().map(each -> each.getCreator()).collect(Collectors.toSet());
    }

    public boolean deleteMap(int imageIndex) {
        ImageMap imageMap = maps.remove(imageIndex);
        if (imageMap == null) {
            return false;
        }
        List<MapView> mapViews = imageMap.getMapViews();
        for (MapView mapView : mapViews) {
            mapsByView.remove(mapView);
        }
        if (imageMap.trackDeletedMaps()) {
            mapViews.forEach(each -> deletedMapIds.add(each.getId()));
        }
        imageMap.markInvalid();
        dataFolder.mkdirs();
        File folder = new File(dataFolder, String.valueOf(imageIndex));
        if (folder.exists() && folder.isDirectory()) {
            FileUtils.removeFolderRecursively(folder);
        }
        imageMap.stop();
        saveDeletedMaps();
        Scheduler.runTask(ImageFrame.plugin, () -> {
            mapViews.forEach(each -> {
                if (each.getRenderers().isEmpty()) {
                    each.addRenderer(DeletedMapRenderer.INSTANCE);
                }
            });
        });
        return true;
    }

    public boolean isMapDeleted(int mapId) {
        return deletedMapIds.contains(mapId);
    }

    public boolean isMapDeleted(MapView mapView) {
        return isMapDeleted(mapView.getId());
    }

    public synchronized void loadMaps() {
        maps.clear();
        mapsByView.clear();
        dataFolder.mkdirs();
        File[] files = dataFolder.listFiles();
        Arrays.sort(files, FileUtils.BY_NUMBER_THAN_STRING);
        List<MutablePair<File, Future<? extends ImageMap>>> futures = new LinkedList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                try {
                    futures.add(new MutablePair<>(file, ImageMap.load(this, file)));
                } catch (Throwable e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMap data in " + file.getAbsolutePath());
                    e.printStackTrace();
                }
            } else if (file.getName().equalsIgnoreCase("deletedMaps.bin")) {
                try (DataInputStream dataInputStream = new DataInputStream(Files.newInputStream(file.toPath()))) {
                    try {
                        deletedMapIds.add(dataInputStream.readInt());
                    } catch (EOFException ignore) {}
                } catch (IOException e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMapManager data in " + file.getAbsolutePath());
                    e.printStackTrace();
                }
            } else if (file.getName().equalsIgnoreCase("deletedMaps.json")) { //legacy storage support
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    JsonArray deletedMapIdsArray = json.get("mapids").getAsJsonArray();
                    for (JsonElement element : deletedMapIdsArray) {
                        deletedMapIds.add(element.getAsInt());
                    }
                } catch (IOException e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMapManager data in " + file.getAbsolutePath());
                    e.printStackTrace();
                }
                saveDeletedMaps();
                try {
                    Files.move(file.toPath(), new File(dataFolder, "deletedMaps.json.bak").toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
            int count = 0;
            for (MutablePair<File, Future<? extends ImageMap>> pair : futures) {
                try {
                    addMap(pair.getSecond().get());
                    count++;
                } catch (Throwable e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMap data in " + pair.getFirst().getAbsolutePath());
                    e.printStackTrace();
                }
            }
            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] Data loading completed! Loaded " + count + " ImageMaps!");
        });
    }

    public synchronized void saveDeletedMaps() {
        File file = new File(dataFolder, "deletedMaps.bin");
        try (DataOutputStream dataOutputStream = new DataOutputStream(Files.newOutputStream(file.toPath()))) {
            for (int deletedMapId : deletedMapIds) {
                dataOutputStream.writeInt(deletedMapId);
            }
            dataOutputStream.flush();
        } catch (IOException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to save ImageMapManager data in " + file.getAbsolutePath());
            e.printStackTrace();
        }
    }

    public void sendAllMaps(Collection<? extends Player> players) {
        maps.values().forEach(m -> m.send(players));
    }

    public static class DeletedMapRenderer extends MapRenderer {

        public static final DeletedMapRenderer INSTANCE = new DeletedMapRenderer();

        private DeletedMapRenderer() {}

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            List<MapRenderer> mapRenderers = map.getRenderers();
            if (mapRenderers.size() != 1 || mapRenderers.get(0) != this) {
                Scheduler.runTaskLater(ImageFrame.plugin, () -> map.removeRenderer(this), 1);
                return;
            }
            Random random = new Random(map.getId());
            byte[] colors = MapUtils.PALETTE_GRAYSCALE;
            for (int y = 0; y < MapUtils.MAP_WIDTH; y++) {
                for (int x = 0; x < MapUtils.MAP_WIDTH; x++) {
                    canvas.setPixel(x, y, colors[random.nextInt(colors.length)]);
                }
            }
        }
    }

}
