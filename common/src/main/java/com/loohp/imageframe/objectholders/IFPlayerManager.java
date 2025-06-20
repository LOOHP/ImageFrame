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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.loohp.imageframe.ImageFrame;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IFPlayerManager implements AutoCloseable, Listener {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private final File dataFolder;
    private final Map<UUID, IFPlayer> loadedPlayers;
    private final Set<IFPlayer> persistentLoadedPlayers;

    public IFPlayerManager(File dataFolder) {
        this.dataFolder = dataFolder;
        Cache<UUID, IFPlayer> playersCache = CacheBuilder.newBuilder().weakValues().build();
        this.loadedPlayers = playersCache.asMap();
        this.persistentLoadedPlayers = ConcurrentHashMap.newKeySet();
        Bukkit.getPluginManager().registerEvents(this, ImageFrame.plugin);
    }

    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> persistentLoadedPlayers.add(getIFPlayer(event.getPlayer().getUniqueId())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        IFPlayer ifPlayer = loadedPlayers.get(event.getPlayer().getUniqueId());
        if (ifPlayer != null) {
            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                try {
                    ifPlayer.save();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                persistentLoadedPlayers.remove(ifPlayer);
            });
        }
    }

    public IFPlayer getIFPlayer(UUID uuid) {
        return loadedPlayers.computeIfAbsent(uuid, k -> {
            dataFolder.mkdirs();
            File file = new File(dataFolder, uuid + ".json");
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
                    return IFPlayer.load(this, GSON.fromJson(reader, JsonObject.class));
                } catch (Exception e) {
                    new RuntimeException("Unable to load ImageFrame player data from " + file.getAbsolutePath(), e).printStackTrace();
                    try {
                        Files.copy(file.toPath(), new File(file.getParentFile(), file.getName() + ".bak").toPath());
                    } catch (IOException ex) {
                        new RuntimeException("Unable to backup ImageFrame player data from " + file.getAbsolutePath(), ex).printStackTrace();
                    }
                }
            }
            return createNewIfPlayer(uuid);
        });
    }

    private IFPlayer createNewIfPlayer(UUID uuid) {
        try {
            return IFPlayer.create(this, uuid);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create ImageFrame player data for " + uuid, e);
        }
    }

}
