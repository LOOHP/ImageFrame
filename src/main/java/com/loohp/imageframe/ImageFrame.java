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
import com.loohp.imageframe.objectholders.ItemFrameSelectionManager;
import com.loohp.imageframe.updater.Updater;
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
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
    public static String messageImageMapDeleted;
    public static String messageImageMapRenamed;
    public static String messageUnableToLoadMap;
    public static String messageNotAnImageMap;
    public static List<String> messageURLImageMapInfo;
    public static String messageNoPermission;
    public static String messageNoConsole;
    public static String messageInvalidUsage;
    public static String messageNotEnoughMaps;
    public static String messageURLRestricted;
    public static String messagePlayerCreationLimitReached;
    public static String messageOversize;
    public static String messageDuplicateMapName;
    public static String messageMapLookup;
    public static String messageSelectionBegin;
    public static String messageSelectionClear;
    public static String messageSelectionCorner1;
    public static String messageSelectionCorner2;
    public static String messageSelectionInvalid;
    public static String messageSelectionOversize;
    public static String messageSelectionSuccess;
    public static String messageSelectionNoSelection;
    public static String messageSelectionIncorrectSize;

    public static SimpleDateFormat dateFormat;

    public static String mapItemFormat;
    public static boolean requireEmptyMaps;
    public static int mapMaxSize;
    public static boolean restrictImageUrlEnabled;
    public static List<String> restrictImageUrls;
    public static Object2IntMap<String> playerCreationLimit;

    public static ImageMapManager imageMapManager;
    public static ItemFrameSelectionManager itemFrameSelectionManager;

    public static boolean isURLAllowed(String url) {
        if (!restrictImageUrlEnabled) {
            return true;
        }
        String normalized = url.trim().toLowerCase();
        return restrictImageUrls.stream().anyMatch(each -> normalized.startsWith(each.toLowerCase()));
    }

    public static int getPlayerCreationLimit(Player player) {
        if (player.hasPermission("imageframe.createlimit.unlimited")) {
            return -1;
        }
        int limit = Integer.MIN_VALUE;
        for (Object2IntMap.Entry<String> entry : playerCreationLimit.object2IntEntrySet()) {
            if (player.hasPermission("imageframe.createlimit." + entry.getKey())) {
                int value = entry.getIntValue();
                if (value < 0) {
                    return -1;
                } else if (value > limit) {
                    limit = value;
                }
            }
        }
        if (limit == Integer.MIN_VALUE) {
            return playerCreationLimit.getOrDefault("default", -1);
        }
        return limit;
    }

    @Override
    public void onEnable() {
        plugin = this;

        metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        Charts.setup(metrics);

        getDataFolder().mkdirs();

        if (!ImageIO.getImageReadersByFormatName("webp").hasNext()) {
            IIORegistry.getDefaultInstance().registerServiceProvider(new WebPImageReaderSpi());
        }

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
        itemFrameSelectionManager = new ItemFrameSelectionManager();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> imageMapManager.loadMaps());

        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] ImageFrame has been Enabled!");
    }

    @Override
    public void onDisable() {
        if (imageMapManager != null) {
            imageMapManager.close();
        }
        if (itemFrameSelectionManager != null) {
            itemFrameSelectionManager.close();
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
        messageImageMapDeleted = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapDeleted"));
        messageImageMapRenamed = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapRenamed"));
        messageUnableToLoadMap = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.UnableToLoadMap"));
        messageNotAnImageMap = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NotAnImageMap"));
        messageURLImageMapInfo = config.getConfiguration().getStringList("Messages.URLImageMapInfo").stream().map(each -> ChatColor.translateAlternateColorCodes('&', each)).collect(Collectors.toList());
        messageNoPermission = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NoPermission"));
        messageNoConsole = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NoConsole"));
        messageInvalidUsage = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.InvalidUsage"));
        messageNotEnoughMaps = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NotEnoughMaps"));
        messageURLRestricted = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.URLRestricted"));
        messagePlayerCreationLimitReached = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.PlayerCreationLimitReached"));
        messageOversize = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Oversize"));
        messageDuplicateMapName = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.DuplicateMapName"));
        messageMapLookup = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.MapLookup"));
        messageSelectionBegin = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Begin"));
        messageSelectionClear = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Clear"));
        messageSelectionCorner1 = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Corner1"));
        messageSelectionCorner2 = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Corner2"));
        messageSelectionInvalid = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Invalid"));
        messageSelectionOversize = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Oversize"));
        messageSelectionSuccess = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Success"));
        messageSelectionNoSelection = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.NoSelection"));
        messageSelectionIncorrectSize = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.IncorrectSize"));

        dateFormat = new SimpleDateFormat(config.getConfiguration().getString("Messages.DateFormat"));

        mapItemFormat = ChatColor.translateAlternateColorCodes('&', config.getConfiguration().getString("Settings.MapItemFormat"));
        requireEmptyMaps = config.getConfiguration().getBoolean("Settings.RequireEmptyMaps");
        mapMaxSize = config.getConfiguration().getInt("Settings.MaxSize");
        restrictImageUrlEnabled = config.getConfiguration().getBoolean("Settings.RestrictImageUrl.Enabled");
        restrictImageUrls = config.getConfiguration().getStringList("Settings.RestrictImageUrl.Whitelist");
        playerCreationLimit = new Object2IntArrayMap<>();
        for (String group : config.getConfiguration().getConfigurationSection("Settings.PlayerCreationLimit").getKeys(false)) {
            playerCreationLimit.put(group, config.getConfiguration().getInt("Settings.PlayerCreationLimit." + group));
        }

        if (updaterTaskID >= 0) {
            Bukkit.getScheduler().cancelTask(updaterTaskID);
        }
        updaterEnabled = config.getConfiguration().getBoolean("Updater");
        if (updaterEnabled) {
            Bukkit.getPluginManager().registerEvents(new Updater(), this);
        }
    }

}
