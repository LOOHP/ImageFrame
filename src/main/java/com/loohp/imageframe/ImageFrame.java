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

package com.loohp.imageframe;

import com.loohp.imageframe.config.Config;
import com.loohp.imageframe.debug.Debug;
import com.loohp.imageframe.metrics.Charts;
import com.loohp.imageframe.metrics.Metrics;
import com.loohp.imageframe.objectholders.ImageMapManager;
import com.loohp.imageframe.updater.Updater;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

public class ImageFrame extends JavaPlugin {

    public static final int BSTATS_PLUGIN_ID = 16773;
    public static final String CONFIG_ID = "config";

    public static ImageFrame plugin;

    public Metrics metrics;

    public static boolean updaterEnabled;
    public static int updaterTaskID = -1;

    public static String messageReloaded;
    public static String messageImageMapCreated;
    public static String messageImageMapRefreshed;
    public static String messageUnableToLoadMap;
    public static String messageNotAnImageMap;
    public static List<String> messageURLImageMapInfo;
    public static String messageNoPermission;
    public static String messageNoConsole;
    public static String messageInvalidUsage;
    public static String messageNotEnoughMaps;
    public static SimpleDateFormat dateFormat;

    public static boolean requireEmptyMaps;

    public static ImageMapManager imageMapManager;

    @Override
    public void onEnable() {
        plugin = this;

        metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        Charts.setup(metrics);

        getDataFolder().mkdirs();

        try {
            Config.loadConfig(CONFIG_ID, new File(getDataFolder(), "config.yml"), getClass().getClassLoader().getResourceAsStream("config.yml"), getClass().getClassLoader().getResourceAsStream("config.yml"), true);
        } catch (IOException e) {
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        reloadConfig();

        getCommand("imageframe").setExecutor(new Commands());

        getServer().getPluginManager().registerEvents(new Debug(), this);
        getServer().getPluginManager().registerEvents(new Updater(), this);

        imageMapManager = new ImageMapManager(new File(getDataFolder(), "data"));
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> imageMapManager.loadMaps());

        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] ImageFrame has been Enabled!");
    }

    @Override
    public void onDisable() {
        if (imageMapManager != null) {
            imageMapManager.close();
        }
        getServer().getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] ImageFrame has been Disabled!");
    }

    @Override
    public void reloadConfig() {
        Config config = Config.getConfig(CONFIG_ID);
        config.reload();

        messageReloaded = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Reloaded"));
        messageImageMapCreated = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapCreated"));
        messageImageMapRefreshed = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapRefreshed"));
        messageUnableToLoadMap = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.UnableToLoadMap"));
        messageNotAnImageMap = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NotAnImageMap"));
        messageURLImageMapInfo = config.getConfiguration().getStringList("Messages.URLImageMapInfo").stream().map(each -> ChatColor.translateAlternateColorCodes('&', each)).collect(Collectors.toList());
        messageNoPermission = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NoPermission"));
        messageNoConsole = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NoConsole"));
        messageInvalidUsage = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.InvalidUsage"));
        messageNotEnoughMaps = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NotEnoughMaps"));

        dateFormat = new SimpleDateFormat(config.getConfiguration().getString("Messages.DateFormat"));

        requireEmptyMaps = config.getConfiguration().getBoolean("Settings.RequireEmptyMaps");

        if (updaterTaskID >= 0) {
            Bukkit.getScheduler().cancelTask(updaterTaskID);
        }
        updaterEnabled = config.getConfiguration().getBoolean("Updater");
        if (updaterEnabled) {
            Bukkit.getPluginManager().registerEvents(new Updater(), this);
        }
    }

}
