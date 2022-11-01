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

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.FileUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageMapManager implements AutoCloseable {

    private final Map<Integer, ImageMap> maps;
    private final AtomicInteger mapIndexCounter;
    private final File dataFolder;
    private final AtomicInteger tickCounter;
    private final int taskId;

    public ImageMapManager(File dataFolder) {
        this.maps = new ConcurrentHashMap<>();
        this.mapIndexCounter = new AtomicInteger(0);
        this.dataFolder = dataFolder;
        this.tickCounter = new AtomicInteger(0);
        this.taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(ImageFrame.plugin, () -> animationTask(), 0, 1).getTaskId();
    }

    private void animationTask() {
        tickCounter.getAndIncrement();
        for (ImageMap imageMap : maps.values()) {
            if (imageMap.requiresAnimationService()) {
                imageMap.send(imageMap.getViewers());
            }
        }
    }

    public int getCurrentAnimationTick() {
        return tickCounter.get();
    }

    @Override
    public void close() {
        Bukkit.getScheduler().cancelTask(taskId);
    }

    public synchronized void addMap(ImageMap map) throws Exception {
        int imageIndex = map.getImageIndex();
        if (imageIndex < 0) {
            map.imageIndex = mapIndexCounter.getAndIncrement();
        } else {
            mapIndexCounter.updateAndGet(i -> Math.max(imageIndex + 1, i));
        }
        maps.put(map.getImageIndex(), map);
        map.save(dataFolder);
    }

    public boolean hasMap(int imageIndex) {
        return maps.containsKey(imageIndex);
    }

    public Collection<ImageMap> getMaps() {
        return Collections.unmodifiableCollection(maps.values());
    }

    public ImageMap getFromMapId(int id) {
        return getFromMapView(Bukkit.getMap(id));
    }

    public ImageMap getFromImageId(int imageId) {
        return maps.get(imageId);
    }

    public ImageMap getFromMapView(MapView mapView) {
        return maps.values().stream().filter(each -> each.getMapViews().contains(mapView)).findFirst().orElse(null);
    }

    public void deleteMap(ImageMap imageMap) {
        maps.remove(imageMap.getImageIndex());
        dataFolder.mkdirs();
        File folder = new File(dataFolder, String.valueOf(imageMap.getImageIndex()));
        if (folder.exists() && folder.isDirectory()) {
            FileUtils.removeFolderRecursively(folder);
        }
        imageMap.stop();
    }

    public synchronized void loadMaps() {
        maps.clear();
        dataFolder.mkdirs();
        for (File folder : dataFolder.listFiles()) {
            if (folder.isDirectory()) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "Loading map " + folder.getName());
                try {
                    addMap(ImageMap.load(this, folder));
                } catch (Throwable e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Unable to load map in " + folder.getAbsolutePath());
                    e.printStackTrace();
                }
            }
        }
    }

    public void sendAllMaps(Collection<? extends Player> players) {
        maps.values().forEach(m -> sendAllMaps(players));
    }

}
