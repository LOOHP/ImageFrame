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

package com.loohp.imageframe;

import com.loohp.imageframe.config.Config;
import com.loohp.imageframe.debug.Debug;
import com.loohp.imageframe.invisibleframe.InvisibleFrameManager;
import com.loohp.imageframe.listeners.Events;
import com.loohp.imageframe.metrics.Charts;
import com.loohp.imageframe.metrics.Metrics;
import com.loohp.imageframe.objectholders.AnimatedFakeMapManager;
import com.loohp.imageframe.objectholders.CombinedMapItemHandler;
import com.loohp.imageframe.objectholders.IFPlayerManager;
import com.loohp.imageframe.objectholders.IFPlayerPreference;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.ImageMapAccessPermissionType;
import com.loohp.imageframe.objectholders.ImageMapCacheControlMode;
import com.loohp.imageframe.objectholders.ImageMapCreationTaskManager;
import com.loohp.imageframe.objectholders.ImageMapManager;
import com.loohp.imageframe.objectholders.IntRange;
import com.loohp.imageframe.objectholders.IntRangeList;
import com.loohp.imageframe.objectholders.ItemFrameSelectionManager;
import com.loohp.imageframe.objectholders.MapMarkerEditManager;
import com.loohp.imageframe.objectholders.RateLimitedPacketSendingManager;
import com.loohp.imageframe.objectholders.UnsetState;
import com.loohp.imageframe.placeholderapi.Placeholders;
import com.loohp.imageframe.updater.Updater;
import com.loohp.imageframe.upload.ImageUploadManager;
import com.loohp.imageframe.utils.ChatColorUtils;
import com.loohp.imageframe.utils.MCVersion;
import com.loohp.imageframe.utils.ModernEventsUtils;
import com.loohp.platformscheduler.ScheduledTask;
import com.loohp.platformscheduler.Scheduler;
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ImageFrame extends JavaPlugin {

    public static final int BSTATS_PLUGIN_ID = 16773;
    public static final String CONFIG_ID = "config";

    public static ImageFrame plugin;

    public static MCVersion version;

    public Metrics metrics;

    public static boolean viaHook = false;
    public static boolean viaDisableSmoothAnimationForLegacyPlayers = false;

    public static boolean updaterEnabled;
    public static ScheduledTask updaterTask = null;

    public static String messageReloaded;
    public static String messageImageMapProcessing;
    public static String messageImageMapProcessingActionBar;
    public static String messageImageMapQueuedActionBar;
    public static String messageImageMapCreated;
    public static String messageImageMapRefreshed;
    public static String messageImageMapDeleted;
    public static String messageImageMapRenamed;
    public static String messageImageMapTogglePaused;
    public static String messageImageMapPlaybackJumpTo;
    public static String messageImageMapPlayerPurge;
    public static String messageSetCreator;
    public static String messageUnableToLoadMap;
    public static String messageUnableToChangeImageType;
    public static String messageUnknownError;
    public static String messageImageOverMaxFileSize;
    public static String messageNotAnImageMap;
    public static String messageImageMapAlreadyQueued;
    public static List<String> messageURLImageMapInfo;
    public static String messageNoPermission;
    public static String messageNoConsole;
    public static String messageInvalidUsage;
    public static String messagePlayerNotFound;
    public static String messageNotEnoughSpace;
    public static String messageInvalidImageMap;
    public static String messageAccessUpdated;
    public static Map<ImageMapAccessPermissionType, String> messageAccessTypes;
    public static String messageAccessNoneType;
    public static String messageNotEnoughMaps;
    public static String messageURLRestricted;
    public static String messagePlayerCreationLimitReached;
    public static String messageOversize;
    public static String messageDuplicateMapName;
    public static String messageMapLookup;
    public static String messageItemFrameOccupied;
    public static String messageSelectionBegin;
    public static String messageSelectionClear;
    public static String messageSelectionCorner1;
    public static String messageSelectionCorner2;
    public static String messageSelectionInvalid;
    public static String messageSelectionOversize;
    public static String messageSelectionSuccess;
    public static String messageSelectionNoSelection;
    public static String messageSelectionIncorrectSize;
    public static String messageMarkersAddBegin;
    public static String messageMarkersAddConfirm;
    public static String messageMarkersRemove;
    public static String messageMarkersClear;
    public static String messageMarkersCancel;
    public static String messageMarkersDuplicateName;
    public static String messageMarkersNotAMarker;
    public static String messageMarkersNotRenderOnFrameWarning;
    public static String messageMarkersLimitReached;
    public static String messageInvalidOverlayMap;
    public static String messageGivenInvisibleFrame;
    public static String messageUploadLink;
    public static String messageUploadExpired;

    public static SimpleDateFormat dateFormat;

    public static String messagePreferencesUpdate;
    public static Map<IFPlayerPreference<?>, String> preferenceNames;
    public static Map<String, String> preferencesValues;

    public static String mapItemFormat;
    public static boolean requireEmptyMaps;
    public static int mapMaxSize;
    public static boolean restrictImageUrlEnabled;
    public static List<URL> restrictImageUrls;
    public static Map<String, Integer> playerCreationLimit;
    public static int mapMarkerLimit;
    public static long maxImageFileSize;
    public static int maxProcessingTime;
    public static int parallelProcessingLimit;

    public static int rateLimit;

    public static String combinedMapItemNameFormat;
    public static List<String> combinedMapItemLoreFormat;

    public static IntRangeList exemptMapIdsFromDeletion;

    public static boolean mapRenderersContextual;
    public static boolean handleAnimatedMapsOnMainThread;
    public static boolean sendAnimatedMapsOnMainThread;

    public static ImageMapCacheControlMode<?> cacheControlMode;
    public static boolean tryDeleteBlankMapFiles;

    public static boolean uploadServiceEnabled;
    public static String uploadServiceDisplayURL;
    public static String uploadServiceServerAddress;
    public static int uploadServiceServerPort;

    public static int invisibleFrameMaxConversionsPerSplash;
    public static boolean invisibleFrameGlowEmptyFrames;

    public static ImageMapManager imageMapManager;
    public static IFPlayerManager ifPlayerManager;
    public static ItemFrameSelectionManager itemFrameSelectionManager;
    public static MapMarkerEditManager mapMarkerEditManager;
    public static CombinedMapItemHandler combinedMapItemHandler;
    public static AnimatedFakeMapManager animatedFakeMapManager;
    public static RateLimitedPacketSendingManager rateLimitedPacketSendingManager;
    public static InvisibleFrameManager invisibleFrameManager;
    public static ImageMapCreationTaskManager imageMapCreationTaskManager;
    public static ImageUploadManager imageUploadManager;

    public static boolean isURLAllowed(String link) {
        if (!restrictImageUrlEnabled) {
            return true;
        }
        if ("upload".equals(link) || imageUploadManager.wasUploaded(link)) {
            return true;
        }
        try {
            URL url = new URL(link);
            return restrictImageUrls.stream().anyMatch(whitelisted -> {
                if (!url.getProtocol().equalsIgnoreCase(whitelisted.getProtocol())) {
                   return false;
                }
                if (!url.getHost().equalsIgnoreCase(whitelisted.getHost())) {
                    return false;
                }
                return url.getPath().toLowerCase().startsWith(whitelisted.getPath().toLowerCase());
            });
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static <T extends UnsetState> T getPreferenceUnsetValue(Player player, IFPlayerPreference<T> preference) {
        String prefix = "imageframe.preference.unsetdefault." + preference.name().toLowerCase() + ".";
        for (Map.Entry<String, T> entry : preference.getSuggestedValues().entrySet()) {
            T value = entry.getValue();
            if (!value.isUnset() && player.hasPermission(prefix + entry.getKey().toLowerCase())) {
                return value;
            }
        }
        return preference.getDefaultValue(ifPlayerManager.getIFPlayer(player.getUniqueId()));
    }

    public static String getPreferenceTranslatedName(IFPlayerPreference<?> preference) {
        return preferenceNames.getOrDefault(preference, preference.name().toLowerCase());
    }

    public static String getPreferenceTranslatedValue(Object value) {
        String str = String.valueOf(value);
        return preferencesValues.getOrDefault(str.toUpperCase(), str);
    }

    public static int getPlayerCreationLimit(Player player) {
        if (player.hasPermission("imageframe.createlimit.unlimited")) {
            return -1;
        }
        int limit = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> entry : playerCreationLimit.entrySet()) {
            if (player.hasPermission("imageframe.createlimit." + entry.getKey())) {
                int value = entry.getValue();
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

    public static boolean hasImageMapPermission(ImageMap imageMap, CommandSender sender, ImageMapAccessPermissionType permissionType) {
        if (permissionType == null) {
            return true;
        }
        if (sender.hasPermission("imageframe.adminbypass")) {
            return true;
        }
        if (!(sender instanceof Player)) {
            return false;
        }
        return imageMap.getAccessControl().hasPermission(((Player) sender).getUniqueId(), permissionType);
    }

    public static boolean isPluginEnabled(String name) {
        return isPluginEnabled(name, true);
    }

    public static boolean isPluginEnabled(String name, boolean checkRunning) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && (!checkRunning || plugin.isEnabled());
    }

    @Override
    public void onEnable() {
        plugin = this;

        version = MCVersion.resolve();

        if (!version.isSupported()) {
            getServer().getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] This version of minecraft is unsupported! (" + version.toString() + ")");
        }

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

        if (isPluginEnabled("ViaVersion")) {
            getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "[ImageFrame] ImageFrame has hooked into ViaVersion!");
            viaHook = true;
        }

        getServer().getPluginManager().registerEvents(new Debug(), this);
        getServer().getPluginManager().registerEvents(new Updater(), this);
        getServer().getPluginManager().registerEvents(new Events(), this);
        if (ModernEventsUtils.modernEventsExists()) {
            getServer().getPluginManager().registerEvents(new Events.ModernEvents(), this);
        }

        imageMapManager = new ImageMapManager(new File(getDataFolder(), "data"));
        ifPlayerManager = new IFPlayerManager(new File(getDataFolder(), "players"));
        itemFrameSelectionManager = new ItemFrameSelectionManager();
        mapMarkerEditManager = new MapMarkerEditManager();
        combinedMapItemHandler = new CombinedMapItemHandler();
        animatedFakeMapManager = new AnimatedFakeMapManager();
        rateLimitedPacketSendingManager = new RateLimitedPacketSendingManager();
        Scheduler.runTaskAsynchronously(this, () -> imageMapManager.loadMaps());
        invisibleFrameManager = new InvisibleFrameManager();
        imageMapCreationTaskManager = new ImageMapCreationTaskManager(ImageFrame.parallelProcessingLimit);
        imageUploadManager = new ImageUploadManager(uploadServiceEnabled, uploadServiceServerAddress, uploadServiceServerPort);

        if (isPluginEnabled("PlaceholderAPI")) {
            new Placeholders().register();
        }

        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] ImageFrame has been Enabled!");
    }

    @Override
    public void onDisable() {
        if (mapMarkerEditManager != null) {
            mapMarkerEditManager.close();
        }
        if (itemFrameSelectionManager != null) {
            itemFrameSelectionManager.close();
        }
        if (imageMapManager != null) {
            imageMapManager.close();
        }
        if (combinedMapItemHandler != null) {
            combinedMapItemHandler.close();
        }
        if (imageUploadManager != null) {
            imageUploadManager.close();
        }
        getServer().getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] ImageFrame has been Disabled!");
    }

    @Override
    public void reloadConfig() {
        Config config = Config.getConfig(CONFIG_ID);
        config.reload();

        viaDisableSmoothAnimationForLegacyPlayers = config.getConfiguration().getBoolean("Hooks.ViaVersion.DisableSmoothAnimationForLegacyPlayers");

        messageReloaded = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Reloaded"));
        messageImageMapProcessing = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapProcessing"));
        messageImageMapProcessingActionBar = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapProcessingActionBar"));
        messageImageMapQueuedActionBar = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapQueuedActionBar"));
        messageImageMapCreated = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapCreated"));
        messageImageMapRefreshed = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapRefreshed"));
        messageImageMapDeleted = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapDeleted"));
        messageImageMapRenamed = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapRenamed"));
        messageImageMapTogglePaused = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapTogglePaused"));
        messageImageMapPlaybackJumpTo = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapPlaybackJumpTo"));
        messageImageMapPlayerPurge = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapPlayerPurge"));
        messageSetCreator = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.SetCreator"));
        messageUnableToLoadMap = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.UnableToLoadMap"));
        messageUnableToChangeImageType = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.UnableToChangeImageType"));
        messageUnknownError = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.UnknownError"));
        messageImageOverMaxFileSize = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageOverMaxFileSize"));
        messageNotAnImageMap = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NotAnImageMap"));
        messageImageMapAlreadyQueued = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ImageMapAlreadyQueued"));
        messageURLImageMapInfo = config.getConfiguration().getStringList("Messages.URLImageMapInfo").stream().map(each -> ChatColorUtils.translateAlternateColorCodes('&', each)).collect(Collectors.toList());
        messageNoPermission = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NoPermission"));
        messageNoConsole = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NoConsole"));
        messageInvalidUsage = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.InvalidUsage"));
        messagePlayerNotFound = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.PlayerNotFound"));
        messageNotEnoughSpace = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NotEnoughSpace"));
        messageInvalidImageMap = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.InvalidImageMap"));
        messageAccessUpdated = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.AccessPermission.Updated"));
        messageAccessTypes = new HashMap<>();
        for (ImageMapAccessPermissionType type : ImageMapAccessPermissionType.values().values()) {
            messageAccessTypes.put(type, ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.AccessPermission.Types." + type.name())));
        }
        messageAccessNoneType = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.AccessPermission.Types.NONE"));
        messageNotEnoughMaps = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.NotEnoughMaps"));
        messageURLRestricted = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.URLRestricted"));
        messagePlayerCreationLimitReached = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.PlayerCreationLimitReached"));
        messageOversize = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Oversize"));
        messageDuplicateMapName = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.DuplicateMapName"));
        messageMapLookup = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.MapLookup"));
        messageItemFrameOccupied = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.ItemFrameOccupied"));
        messageSelectionBegin = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Begin"));
        messageSelectionClear = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Clear"));
        messageSelectionCorner1 = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Corner1"));
        messageSelectionCorner2 = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Corner2"));
        messageSelectionInvalid = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Invalid"));
        messageSelectionOversize = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Oversize"));
        messageSelectionSuccess = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.Success"));
        messageSelectionNoSelection = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.NoSelection"));
        messageSelectionIncorrectSize = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Selection.IncorrectSize"));
        messageMarkersAddBegin = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Markers.AddBegin"));
        messageMarkersAddConfirm = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Markers.AddConfirm"));
        messageMarkersRemove = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Markers.Remove"));
        messageMarkersClear = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Markers.Clear"));
        messageMarkersCancel = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Markers.Cancel"));
        messageMarkersDuplicateName = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Markers.DuplicateName"));
        messageMarkersNotAMarker = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Markers.NotAMarker"));
        messageMarkersNotRenderOnFrameWarning = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Markers.NotRenderOnFrameWarning"));
        messageMarkersLimitReached = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Markers.LimitReached"));
        messageInvalidOverlayMap = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.InvalidOverlayMap"));
        messageGivenInvisibleFrame = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.GivenInvisibleFrame"));
        messageUploadLink = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.UploadLink"));
        messageUploadExpired = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.UploadExpired"));

        dateFormat = new SimpleDateFormat(config.getConfiguration().getString("Messages.DateFormat"));

        messagePreferencesUpdate = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Preferences.UpdateMessage"));
        preferenceNames = new HashMap<>();
        for (IFPlayerPreference<?> preference : IFPlayerPreference.values()) {
            if (config.getConfiguration().contains("Messages.Preferences.Keys." + preference.name())) {
                preferenceNames.put(preference, ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Preferences.Keys." + preference.name())));
            }
        }
        preferencesValues = new HashMap<>();
        for (String key : config.getConfiguration().getConfigurationSection("Messages.Preferences.Values").getKeys(false)) {
            preferencesValues.put(key, ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Messages.Preferences.Values." + key)));
        }

        mapItemFormat = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Settings.MapItemFormat"));
        requireEmptyMaps = config.getConfiguration().getBoolean("Settings.RequireEmptyMaps");
        mapMaxSize = config.getConfiguration().getInt("Settings.MaxSize");
        restrictImageUrlEnabled = config.getConfiguration().getBoolean("Settings.RestrictImageUrl.Enabled");
        restrictImageUrls = config.getConfiguration().getStringList("Settings.RestrictImageUrl.Whitelist").stream().map(s -> {
            try {
                return new URL(s);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return null;
            }
        }).filter(u -> u != null).collect(Collectors.toList());
        playerCreationLimit = new HashMap<>();
        for (String group : config.getConfiguration().getConfigurationSection("Settings.PlayerCreationLimit").getKeys(false)) {
            playerCreationLimit.put(group, config.getConfiguration().getInt("Settings.PlayerCreationLimit." + group));
        }
        mapMarkerLimit = config.getConfiguration().getInt("Settings.MapMarkerLimit");
        maxImageFileSize = config.getConfiguration().getLong("Settings.MaxImageFileSize");
        maxProcessingTime = config.getConfiguration().getInt("Settings.MaxProcessingTime");
        parallelProcessingLimit = config.getConfiguration().getInt("Settings.ParallelProcessingLimit");

        combinedMapItemNameFormat = ChatColorUtils.translateAlternateColorCodes('&', config.getConfiguration().getString("Settings.CombinedMapItem.Name"));
        combinedMapItemLoreFormat = config.getConfiguration().getStringList("Settings.CombinedMapItem.Lore").stream().map(each -> ChatColorUtils.translateAlternateColorCodes('&', each)).collect(Collectors.toList());
        exemptMapIdsFromDeletion = config.getConfiguration().getList("Settings.ExemptMapIdsFromDeletion").stream().map(v -> {
            try {
                if (v instanceof Number) {
                    return IntRange.of(((Number) v).intValue());
                } else {
                    return IntRange.of(v.toString());
                }
            } catch (Throwable e) {
                e.printStackTrace();
                return null;
            }
        }).filter(v -> v != null).collect(Collectors.toCollection(IntRangeList::new));

        rateLimit = config.getConfiguration().getInt("Settings.MapPacketSendingRateLimit");

        mapRenderersContextual = config.getConfiguration().getBoolean("Settings.MapRenderersContextual");
        handleAnimatedMapsOnMainThread = config.getConfiguration().getBoolean("Settings.HandleAnimatedMapsOnMainThread");
        sendAnimatedMapsOnMainThread = config.getConfiguration().getBoolean("Settings.SendAnimatedMapsOnMainThread");

        cacheControlMode = ImageMapCacheControlMode.valueOf(config.getConfiguration().getString("Settings.CacheControlMode"));
        tryDeleteBlankMapFiles = config.getConfiguration().getBoolean("Settings.TryDeleteBlankMapFiles");

        uploadServiceEnabled = config.getConfiguration().getBoolean("UploadService.Enabled");
        uploadServiceDisplayURL = config.getConfiguration().getString("UploadService.DisplayURL");
        uploadServiceServerAddress = config.getConfiguration().getString("UploadService.WebServer.Host");
        uploadServiceServerPort = config.getConfiguration().getInt("UploadService.WebServer.Port");

        invisibleFrameMaxConversionsPerSplash = config.getConfiguration().getInt("InvisibleFrame.MaxConversionsPerSplash");
        invisibleFrameGlowEmptyFrames = config.getConfiguration().getBoolean("InvisibleFrame.GlowEmptyFrames");

        if (updaterTask != null) {
            updaterTask.cancel();
        }
        updaterEnabled = config.getConfiguration().getBoolean("Updater");
        if (updaterEnabled) {
            Bukkit.getPluginManager().registerEvents(new Updater(), this);
        }
    }

}
