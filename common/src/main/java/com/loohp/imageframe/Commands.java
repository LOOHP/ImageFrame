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

import com.loohp.imageframe.api.events.ImageMapUpdatedEvent;
import com.loohp.imageframe.migration.ExternalPluginMigration;
import com.loohp.imageframe.migration.PluginMigrationRegistry;
import com.loohp.imageframe.objectholders.BlockPosition;
import com.loohp.imageframe.objectholders.DitheringType;
import com.loohp.imageframe.objectholders.IFPlayer;
import com.loohp.imageframe.objectholders.IFPlayerPreference;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.ImageMapAccessControl;
import com.loohp.imageframe.objectholders.ImageMapAccessPermissionType;
import com.loohp.imageframe.objectholders.ImageMapCreationTask;
import com.loohp.imageframe.objectholders.ImageMapCreationTaskManager;
import com.loohp.imageframe.objectholders.ImageMapLoader;
import com.loohp.imageframe.objectholders.ImageMapLoaders;
import com.loohp.imageframe.objectholders.ItemFrameSelectionManager;
import com.loohp.imageframe.objectholders.MapMarkerEditManager;
import com.loohp.imageframe.objectholders.MinecraftURLOverlayImageMap;
import com.loohp.imageframe.objectholders.MinecraftURLOverlayImageMapCreateInfo;
import com.loohp.imageframe.objectholders.MutablePair;
import com.loohp.imageframe.objectholders.PreferenceState;
import com.loohp.imageframe.objectholders.URLImageMap;
import com.loohp.imageframe.objectholders.URLImageMapCreateInfo;
import com.loohp.imageframe.storage.ImageFrameStorage;
import com.loohp.imageframe.storage.ImageFrameStorageLoader;
import com.loohp.imageframe.storage.ImageFrameStorageLoaders;
import com.loohp.imageframe.storage.StorageMigrator;
import com.loohp.imageframe.updater.Updater;
import com.loohp.imageframe.upload.ImageUploadManager;
import com.loohp.imageframe.upload.PendingUpload;
import com.loohp.imageframe.utils.ChatColorUtils;
import com.loohp.imageframe.utils.HTTPRequestUtils;
import com.loohp.imageframe.utils.ImageMapUtils;
import com.loohp.imageframe.utils.KeyUtils;
import com.loohp.imageframe.utils.MCVersion;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.imageframe.utils.MathUtils;
import com.loohp.imageframe.utils.PlayerUtils;
import com.loohp.platformscheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;

import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.loohp.imageframe.language.TranslationKey.*;
import static com.loohp.imageframe.utils.CommandSenderUtils.sendMessage;
import static com.loohp.imageframe.utils.ComponentUtils.translatable;
import static net.kyori.adventure.text.Component.text;

public class Commands implements CommandExecutor, TabCompleter {

    @SuppressWarnings({"CallToPrintStackTrace", "deprecation", "NullableProblems"})
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendMessage(sender, ChatColor.DARK_AQUA + "ImageFrame written by LOOHP!");
            sendMessage(sender, ChatColor.GOLD + "You are running ImageFrame version: " + ImageFrame.plugin.getDescription().getVersion());
            sendMessage(sender, ChatColor.YELLOW + "Translations are crowdsourced from the community! Visit https://crowdin.com/project/imageframe");
            if (ImageFrame.imageFrameClientEnabled && sender instanceof Player) {
                Player player = (Player) sender;
                if (ImageFrame.customClientNetworkManager.hasPlayer(player)) {
                    sendMessage(sender, ChatColor.GREEN + "Your ImageFrame Client is supported on the server!");
                } else {
                    ImageFrame.customClientNetworkManager.sendAcknowledgement(player);
                    sendMessage(sender, ChatColor.RED + "This server supports ImageFrame Client but you are not using it.");
                }
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("imageframe.reload")) {
                ImageFrame.plugin.reloadConfig();
                ImageFrame.languageManager.reloadLanguages();
                sendMessage(sender, translatable(RELOADED).color(NamedTextColor.GREEN));
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("resync")) {
            if (sender.hasPermission("imageframe.resync")) {
                sendMessage(sender, translatable(RESYNC).color(NamedTextColor.GREEN));
                Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> ImageFrame.imageMapManager.syncMaps(true));
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("update")) {
            if (sender.hasPermission("imageframe.update")) {
                sendMessage(sender, ChatColor.DARK_AQUA + "[ImageFrame] ImageFrame written by LOOHP!");
                sendMessage(sender, ChatColor.GOLD + "[ImageFrame] You are running ImageFrame version: " + ImageFrame.plugin.getDescription().getVersion());
                Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                    Updater.UpdaterResponse version = Updater.checkUpdate();
                    if (version.getResult().equals("latest")) {
                        if (version.isDevBuildLatest()) {
                            sendMessage(sender, ChatColor.GREEN + "[ImageFrame] You are running the latest version!");
                        } else {
                            Updater.sendUpdateMessage(sender, version.getResult(), version.getSpigotPluginId(), true);
                        }
                    } else {
                        Updater.sendUpdateMessage(sender, version.getResult(), version.getSpigotPluginId());
                    }
                });
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("create")) {
            if (sender.hasPermission("imageframe.create")) {
                if (args.length == 4 || args.length == 5 || args.length == 6 || args.length == 7) {
                    try {
                        MutablePair<UUID, String> pair = ImageMapUtils.extractImageMapPlayerPrefixedName(sender, args[1]);
                        String name = pair.getSecond();
                        boolean isConsole = !(sender instanceof Player);
                        if (pair.getFirst() == null && isConsole) {
                            sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                        } else {
                            Player player = isConsole ? null : (Player) sender;
                            UUID owner = pair.getFirst();
                            boolean isAdmin = isConsole || !owner.equals(player.getUniqueId());
                            if (isAdmin && !sender.hasPermission("imageframe.create.others")) {
                                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                            } else {
                                boolean combined;
                                if (ImageFrame.combinedByDefault) {
                                    combined = !((args.length == 6 && args[5].equalsIgnoreCase("separated")) || (args.length == 7 && args[6].equalsIgnoreCase("separated")));
                                } else {
                                    combined = (args.length == 6 && args[5].equalsIgnoreCase("combined")) || (args.length == 7 && args[6].equalsIgnoreCase("combined"));
                                }
                                ItemFrameSelectionManager.SelectedItemFrameResult selection;
                                if (args[3].equalsIgnoreCase("selection")) {
                                    if (isConsole) {
                                        sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                                        return true;
                                    }
                                    selection = ImageFrame.itemFrameSelectionManager.getConfirmedSelections(player);
                                    if (selection == null) {
                                        sendMessage(sender, translatable(SELECTION_NO_SELECTION).color(NamedTextColor.RED));
                                        return true;
                                    }
                                } else if (args.length == 5 || args.length == 6 || args.length == 7) {
                                    selection = null;
                                } else {
                                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                                    return true;
                                }

                                int width;
                                int height;
                                DitheringType ditheringType;
                                if (selection == null) {
                                    width = Integer.parseInt(args[3]);
                                    height = Integer.parseInt(args[4]);
                                    ditheringType = DitheringType.fromName(args.length > 5 && !args[5].equalsIgnoreCase(ImageFrame.combinedByDefault ? "separated" : "combined") ? args[5].toLowerCase() : null);
                                } else {
                                    width = selection.getWidth();
                                    height = selection.getHeight();
                                    ditheringType = DitheringType.fromName(args.length > 4 ? args[4].toLowerCase() : null);
                                }
                                if (width * height > ImageFrame.mapMaxSize) {
                                    sendMessage(sender, translatable(OVERSIZE, ImageFrame.mapMaxSize).color(NamedTextColor.RED));
                                    return true;
                                }
                                int limit = isAdmin ? -1 : ImageFrame.getPlayerCreationLimit(player);
                                Set<ImageMap> existingMaps = ImageFrame.imageMapManager.getFromCreator(owner);
                                if (limit >= 0 && existingMaps.size() >= limit) {
                                    sendMessage(sender, translatable(PLAYER_CREATION_LIMIT_REACHED, limit).color(NamedTextColor.RED));
                                    return true;
                                }
                                if (existingMaps.stream().anyMatch(each -> each.getName().equalsIgnoreCase(name))) {
                                    sendMessage(sender, translatable(DUPLICATE_MAP_NAME).color(NamedTextColor.RED));
                                    return true;
                                }
                                if (!ImageFrame.isURLAllowed(args[2])) {
                                    sendMessage(sender, translatable(URL_RESTRICTED).color(NamedTextColor.RED));
                                    return true;
                                }
                                int takenMaps;
                                if (ImageFrame.requireEmptyMaps && !isConsole) {
                                    if ((takenMaps = MapUtils.removeEmptyMaps(player, width * height, true)) < 0) {
                                        sendMessage(sender, translatable(NOT_ENOUGH_MAPS, width * height).color(NamedTextColor.RED));
                                        return true;
                                    }
                                } else {
                                    takenMaps = 0;
                                }
                                Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                    ImageMapCreationTask<ImageMap> creationTask = null;
                                    String url = "Pending...";
                                    try {
                                        url = args[2];
                                        if (ImageFrame.imageUploadManager.isOperational() && url.equalsIgnoreCase("upload")) {
                                            UUID user = isConsole ? ImageMap.CONSOLE_CREATOR : player.getUniqueId();
                                            PendingUpload pendingUpload = ImageFrame.imageUploadManager.newPendingUpload(user);
                                            String uploadUrl = pendingUpload.getUrl(ImageFrame.uploadServiceDisplayURL, user);
                                            Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, () -> sendMessage(sender, translatable(UPLOAD_LINK, Component.text(uploadUrl).color(NamedTextColor.YELLOW).clickEvent(ClickEvent.openUrl(uploadUrl))).color(NamedTextColor.GREEN)), 2);
                                            url = pendingUpload.getFileBlocking().toURI().toURL().toString();
                                        }
                                        if (HTTPRequestUtils.getContentSize(url) > ImageFrame.maxImageFileSize) {
                                            sendMessage(sender, translatable(IMAGE_OVER_MAX_FILE_SIZE, ImageFrame.maxImageFileSize).color(NamedTextColor.RED));
                                            throw new IOException("Image over max file size");
                                        }
                                        String imageType = HTTPRequestUtils.getContentType(url);
                                        if (imageType == null) {
                                            imageType = URLConnection.guessContentTypeFromName(url);
                                        }
                                        if (imageType == null) {
                                            imageType = "";
                                        } else {
                                            imageType = imageType.trim();
                                        }
                                        sendMessage(sender, translatable(IMAGE_MAP_PROCESSING).color(NamedTextColor.YELLOW));
                                        String finalUrl = url;
                                        String finalImageType = imageType;
                                        creationTask = ImageFrame.imageMapCreationTaskManager.enqueue(owner, name, () -> {
                                            ImageMapLoader<? extends URLImageMap, URLImageMapCreateInfo> loader = ImageMapLoaders.getLoader(URLImageMap.class, URLImageMapCreateInfo.class, finalImageType, sender);
                                            return loader.create(new URLImageMapCreateInfo(ImageFrame.imageMapManager, name, finalUrl, width, height, ditheringType, owner)).get();
                                        });
                                        ImageMap imageMap = creationTask.get();
                                        ImageFrame.imageMapManager.addMap(imageMap);
                                        if (!isConsole) {
                                            if (combined) {
                                                ImageFrame.combinedMapItemHandler.giveCombinedMap(imageMap, player);
                                            } else if (selection == null) {
                                                imageMap.giveMaps(player, ImageFrame.mapItemFormat);
                                            } else {
                                                AtomicBoolean flag = new AtomicBoolean(false);
                                                imageMap.fillItemFrames(selection.getItemFrames(), selection.getRotation(), (frame, item) -> {
                                                    ItemStack originalItem = frame.getItem();
                                                    if (originalItem != null && !originalItem.getType().equals(Material.AIR)) {
                                                        return false;
                                                    }
                                                    return PlayerUtils.isInteractionAllowed(player, frame);
                                                }, (frame, item) -> {
                                                    PlayerUtils.giveItem(player, item);
                                                    if (!flag.getAndSet(true)) {
                                                        sendMessage(sender, translatable(ITEM_FRAME_OCCUPIED).color(NamedTextColor.RED));
                                                    }
                                                }, ImageFrame.mapItemFormat);
                                            }
                                        }
                                        sendMessage(sender, translatable(IMAGE_MAP_CREATED).color(NamedTextColor.GREEN));
                                        creationTask.complete(translatable(IMAGE_MAP_CREATED).color(NamedTextColor.GREEN));
                                    } catch (ImageUploadManager.LinkTimeoutException e) {
                                        sendMessage(sender, translatable(UPLOAD_EXPIRED).color(NamedTextColor.RED));
                                        if (takenMaps > 0 && !isConsole) {
                                            PlayerUtils.giveItem(player, new ItemStack(Material.MAP, takenMaps));
                                        }
                                    } catch (ImageMapCreationTaskManager.EnqueueRejectedException e) {
                                        sendMessage(sender, translatable(IMAGE_MAP_ALREADY_QUEUED).color(NamedTextColor.RED));
                                        if (takenMaps > 0 && !isConsole) {
                                            PlayerUtils.giveItem(player, new ItemStack(Material.MAP, takenMaps));
                                        }
                                    } catch (Exception e) {
                                        sendMessage(sender, translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                                        if (creationTask != null) {
                                            creationTask.complete(translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                                        }
                                        new IOException("Unable to download image. Make sure you are using a direct link to the image, they usually ends with a file extension like \".png\". Dispatcher: " + sender.getName() + " URL: " + url, e).printStackTrace();
                                        if (takenMaps > 0 && !isConsole) {
                                            PlayerUtils.giveItem(player, new ItemStack(Material.MAP, takenMaps));
                                        }
                                    }
                                });
                            }
                        }
                    } catch (NumberFormatException e) {
                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                    } catch (Exception e) {
                        sendMessage(sender, translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                        e.printStackTrace();
                    }
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("overlay")) {
            if (sender.hasPermission("imageframe.overlay")) {
                if (args.length == 3 || args.length == 4 || args.length == 5) {
                    if (sender instanceof Player) {
                        try {
                            Player player = (Player) sender;
                            MutablePair<UUID, String> pair = ImageMapUtils.extractImageMapPlayerPrefixedName(sender, args[1]);
                            String name = pair.getSecond();
                            if (pair.getFirst() == null) {
                                sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                            } else {
                                UUID owner = pair.getFirst();
                                boolean isAdmin = !owner.equals(player.getUniqueId());
                                if (isAdmin && !sender.hasPermission("imageframe.create.others")) {
                                    sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                                } else {
                                    List<MapView> mapViews;
                                    int width;
                                    int height;
                                    DitheringType ditheringType;
                                    if ((args.length == 4 || args.length == 5) && args[3].equalsIgnoreCase("selection")) {
                                        ItemFrameSelectionManager.SelectedItemFrameResult selection = ImageFrame.itemFrameSelectionManager.getConfirmedSelections(player);
                                        if (selection == null) {
                                            sendMessage(player, translatable(SELECTION_NO_SELECTION).color(NamedTextColor.RED));
                                            return true;
                                        } else {
                                            mapViews = selection.getMapViews();
                                            width = selection.getWidth();
                                            height = selection.getHeight();
                                            ditheringType = DitheringType.fromName(args.length == 5 ? args[4].toLowerCase() : null);
                                        }
                                    } else if (args.length == 3 || args.length == 4) {
                                        mapViews = Collections.singletonList(MapUtils.getPlayerMapView(player));
                                        width = 1;
                                        height = 1;
                                        ditheringType = DitheringType.fromName(args.length == 4 ? args[3].toLowerCase() : null);
                                    } else {
                                        sendMessage(player, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                                        return true;
                                    }
                                    if (mapViews.contains(null)) {
                                        sendMessage(player, translatable(SELECTION_INVALID).color(NamedTextColor.RED));
                                        return true;
                                    }
                                    if (mapViews.stream().anyMatch(each -> ImageFrame.imageMapManager.getFromMapView(each) != null) || mapViews.stream().distinct().count() < mapViews.size()) {
                                        sendMessage(player, translatable(INVALID_OVERLAY_MAP).color(NamedTextColor.RED));
                                        return true;
                                    }

                                    if (width * height > ImageFrame.mapMaxSize) {
                                        sendMessage(sender, translatable(OVERSIZE, ImageFrame.mapMaxSize).color(NamedTextColor.RED));
                                        return true;
                                    }
                                    int limit = isAdmin ? -1 : ImageFrame.getPlayerCreationLimit(player);
                                    Set<ImageMap> existingMaps = ImageFrame.imageMapManager.getFromCreator(owner);
                                    if (limit >= 0 && existingMaps.size() >= limit) {
                                        sendMessage(sender, translatable(PLAYER_CREATION_LIMIT_REACHED, limit).color(NamedTextColor.RED));
                                        return true;
                                    }
                                    if (existingMaps.stream().anyMatch(each -> each.getName().equalsIgnoreCase(name))) {
                                        sendMessage(sender, translatable(DUPLICATE_MAP_NAME).color(NamedTextColor.RED));
                                        return true;
                                    }
                                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                        ImageMapCreationTask<ImageMap> creationTask = null;
                                        try {
                                            String url = args[2];
                                            if (ImageFrame.imageUploadManager.isOperational() && url.equalsIgnoreCase("upload")) {
                                                UUID user = player.getUniqueId();
                                                PendingUpload pendingUpload = ImageFrame.imageUploadManager.newPendingUpload(user);
                                                String uploadUrl = pendingUpload.getUrl(ImageFrame.uploadServiceDisplayURL, user);
                                                Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, () -> sendMessage(sender, translatable(UPLOAD_LINK, Component.text(uploadUrl).color(NamedTextColor.YELLOW).clickEvent(ClickEvent.openUrl(uploadUrl))).color(NamedTextColor.GREEN)), 2);
                                                url = pendingUpload.getFileBlocking().toURI().toURL().toString();
                                            }
                                            if (!ImageFrame.isURLAllowed(url)) {
                                                sendMessage(sender, translatable(URL_RESTRICTED).color(NamedTextColor.RED));
                                                return;
                                            }
                                            if (HTTPRequestUtils.getContentSize(url) > ImageFrame.maxImageFileSize) {
                                                sendMessage(sender, translatable(IMAGE_OVER_MAX_FILE_SIZE, ImageFrame.maxImageFileSize).color(NamedTextColor.RED));
                                                throw new IOException("Image over max file size");
                                            }
                                            String finalUrl = url;
                                            creationTask = ImageFrame.imageMapCreationTaskManager.enqueue(owner, name, () -> {
                                                ImageMapLoader<? extends MinecraftURLOverlayImageMap, MinecraftURLOverlayImageMapCreateInfo> loader = ImageMapLoaders.getLoader(MinecraftURLOverlayImageMap.class, MinecraftURLOverlayImageMapCreateInfo.class, null, sender);
                                                return loader.create(new MinecraftURLOverlayImageMapCreateInfo(ImageFrame.imageMapManager, name, finalUrl, mapViews, width, height, ditheringType, player.getUniqueId())).get();
                                            });
                                            ImageMap imageMap = creationTask.get();
                                            ImageFrame.imageMapManager.addMap(imageMap);
                                            sendMessage(sender, translatable(IMAGE_MAP_CREATED).color(NamedTextColor.GREEN));
                                            creationTask.complete(translatable(IMAGE_MAP_CREATED).color(NamedTextColor.GREEN));
                                        } catch (ImageUploadManager.LinkTimeoutException e) {
                                            sendMessage(sender, translatable(UPLOAD_EXPIRED).color(NamedTextColor.RED));
                                        } catch (ImageMapCreationTaskManager.EnqueueRejectedException e) {
                                            sendMessage(sender, translatable(IMAGE_MAP_ALREADY_QUEUED).color(NamedTextColor.RED));
                                        } catch (Exception e) {
                                            sendMessage(sender, translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                                            if (creationTask != null) {
                                                creationTask.complete(translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                                            }
                                            e.printStackTrace();
                                        }
                                    });
                                }
                            }
                        } catch (NumberFormatException e) {
                            sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                        } catch (Exception e) {
                            sendMessage(sender, translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                            e.printStackTrace();
                        }
                    } else {
                        sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                    }
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("clone")) {
            if (sender.hasPermission("imageframe.clone")) {
                if (args.length == 3 || args.length == 4) {
                    try {
                        MutablePair<UUID, String> pair = ImageMapUtils.extractImageMapPlayerPrefixedName(sender, args[2]);
                        String name = pair.getSecond();
                        boolean isConsole = !(sender instanceof Player);
                        if (pair.getFirst() == null && isConsole) {
                            sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                        } else {
                            Player player = isConsole ? null : (Player) sender;
                            UUID owner = pair.getFirst();
                            boolean isAdmin = isConsole || !owner.equals(player.getUniqueId());
                            if (isAdmin && !sender.hasPermission("imageframe.create.others")) {
                                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                            } else {
                                boolean combined;
                                if (ImageFrame.combinedByDefault) {
                                    combined = !(args.length > 3 && args[3].equalsIgnoreCase("separated"));
                                } else {
                                    combined = args.length > 3 && args[3].equalsIgnoreCase("combined");
                                }
                                ItemFrameSelectionManager.SelectedItemFrameResult selection;
                                if (args.length > 3 && args[3].equalsIgnoreCase("selection")) {
                                    selection = ImageFrame.itemFrameSelectionManager.getConfirmedSelections(player);
                                    if (selection == null) {
                                        sendMessage(sender, translatable(SELECTION_NO_SELECTION).color(NamedTextColor.RED));
                                        return true;
                                    }
                                } else {
                                    selection = null;
                                }
                                ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                                if (imageMap == null) {
                                    sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                                    return true;
                                }
                                if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.EDIT_CLONE)) {
                                    sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                                    return true;
                                }
                                if (selection != null) {
                                    if (imageMap.getWidth() != selection.getWidth() || imageMap.getHeight() != selection.getHeight()) {
                                        sendMessage(sender, translatable(SELECTION_INCORRECT_SIZE, imageMap.getWidth(), imageMap.getHeight()).color(NamedTextColor.RED));
                                        return true;
                                    }
                                }
                                int limit = isAdmin ? -1 : ImageFrame.getPlayerCreationLimit(player);
                                Set<ImageMap> existingMaps = ImageFrame.imageMapManager.getFromCreator(owner);
                                if (limit >= 0 && existingMaps.size() >= limit) {
                                    sendMessage(sender, translatable(PLAYER_CREATION_LIMIT_REACHED, limit).color(NamedTextColor.RED));
                                    return true;
                                }
                                if (existingMaps.stream().anyMatch(each -> each.getName().equalsIgnoreCase(name))) {
                                    sendMessage(sender, translatable(DUPLICATE_MAP_NAME).color(NamedTextColor.RED));
                                    return true;
                                }
                                int takenMaps;
                                if (ImageFrame.requireEmptyMaps && !isConsole) {
                                    if ((takenMaps = MapUtils.removeEmptyMaps(player, imageMap.getWidth() * imageMap.getHeight(), true)) < 0) {
                                        sendMessage(sender, translatable(NOT_ENOUGH_MAPS, imageMap.getWidth() * imageMap.getHeight()).color(NamedTextColor.RED));
                                        return true;
                                    }
                                } else {
                                    takenMaps = 0;
                                }
                                Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                    ImageMapCreationTask<ImageMap> creationTask = null;
                                    try {
                                        creationTask = ImageFrame.imageMapCreationTaskManager.enqueue(owner, name, () -> imageMap.deepClone(name, owner));
                                        ImageMap newImageMap = creationTask.get();
                                        ImageFrame.imageMapManager.addMap(newImageMap);
                                        if (!isConsole) {
                                            if (combined) {
                                                ImageFrame.combinedMapItemHandler.giveCombinedMap(newImageMap, player);
                                            } else if (selection == null) {
                                                newImageMap.giveMaps(player, ImageFrame.mapItemFormat);
                                            } else {
                                                AtomicBoolean flag = new AtomicBoolean(false);
                                                newImageMap.fillItemFrames(selection.getItemFrames(), selection.getRotation(), (frame, item) -> {
                                                    ItemStack originalItem = frame.getItem();
                                                    if (originalItem != null && !originalItem.getType().equals(Material.AIR)) {
                                                        return false;
                                                    }
                                                    return PlayerUtils.isInteractionAllowed(player, frame);
                                                }, (frame, item) -> {
                                                    PlayerUtils.giveItem(player, item);
                                                    if (!flag.getAndSet(true)) {
                                                        sendMessage(sender, translatable(ITEM_FRAME_OCCUPIED).color(NamedTextColor.RED));
                                                    }
                                                }, ImageFrame.mapItemFormat);
                                            }
                                        }
                                        sendMessage(sender, translatable(IMAGE_MAP_CREATED).color(NamedTextColor.GREEN));
                                        creationTask.complete(translatable(IMAGE_MAP_CREATED).color(NamedTextColor.GREEN));
                                    } catch (ImageMapCreationTaskManager.EnqueueRejectedException e) {
                                        sendMessage(sender, translatable(IMAGE_MAP_ALREADY_QUEUED).color(NamedTextColor.RED));
                                    } catch (Exception e) {
                                        sendMessage(sender, translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                                        if (creationTask != null) {
                                            creationTask.complete(translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                                        }
                                        e.printStackTrace();
                                        if (takenMaps > 0 && !isConsole) {
                                            PlayerUtils.giveItem((Player) sender, new ItemStack(Material.MAP, takenMaps));
                                        }
                                    }
                                });
                            }
                        }
                    } catch (NumberFormatException e) {
                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                    } catch (Exception e) {
                        sendMessage(sender, translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                        e.printStackTrace();
                    }
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("playback")) {
            if (sender.hasPermission("imageframe.playback")) {
                if (args.length > 2) {
                    try {
                        MutablePair<UUID, String> pair = ImageMapUtils.extractImageMapPlayerPrefixedName(sender, args[1]);
                        boolean isConsole = !(sender instanceof Player);
                        if (pair.getFirst() == null && isConsole) {
                            sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                        } else {
                            ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                            if (imageMap == null) {
                                sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                                return true;
                            }
                            if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.ADJUST_PLAYBACK)) {
                                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                                return true;
                            }
                            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    if (args[2].equalsIgnoreCase("pause")) {
                                        if (imageMap.requiresAnimationService()) {
                                            boolean targetState = args.length > 3 ? Boolean.parseBoolean(args[3]) : !imageMap.isAnimationPaused();
                                            imageMap.setAnimationPause(targetState);
                                        }
                                        sendMessage(sender, translatable(IMAGE_MAP_TOGGLE_PAUSED).color(NamedTextColor.GREEN));
                                    } else if (args[2].equalsIgnoreCase("jumpto") && args.length > 3) {
                                        try {
                                            double seconds = Double.parseDouble(args[3]);
                                            if (imageMap.requiresAnimationService()) {
                                                imageMap.setAnimationPlaybackTime(seconds);
                                            }
                                            sendMessage(sender, translatable(IMAGE_MAP_PLAYBACK_JUMP_TO, seconds).color(NamedTextColor.GREEN));
                                        } catch (NumberFormatException e) {
                                            sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                                        }
                                    } else {
                                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    } catch (NumberFormatException e) {
                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                    } catch (Exception e) {
                        sendMessage(sender, translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                        e.printStackTrace();
                    }
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("select")) {
            if (sender.hasPermission("imageframe.select")) {
                if (args.length == 1) {
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        if (ImageFrame.itemFrameSelectionManager.isInSelection(player)) {
                            ImageFrame.itemFrameSelectionManager.setInSelection(player, false);
                            sendMessage(sender, translatable(SELECTION_CLEAR).color(NamedTextColor.YELLOW));
                        } else {
                            ImageFrame.itemFrameSelectionManager.setInSelection(player, true);
                            sendMessage(sender, translatable(SELECTION_BEGIN).color(NamedTextColor.GREEN));
                        }
                    } else {
                        sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                    }
                } else if (args.length == 8 || args.length == 9) {
                    try {
                        World world = Bukkit.getWorld(args[1]);
                        if (world != null) {
                            int x1 = Integer.parseInt(args[2]);
                            int y1 = Integer.parseInt(args[3]);
                            int z1 = Integer.parseInt(args[4]);
                            int x2 = Integer.parseInt(args[5]);
                            int y2 = Integer.parseInt(args[6]);
                            int z2 = Integer.parseInt(args[7]);

                            BlockPosition pos1 = new BlockPosition(world, x1, y1, z1);
                            BlockPosition pos2 = new BlockPosition(world, x2, y2, z2);

                            float yaw;
                            if (args.length == 9) {
                                yaw = Float.parseFloat(args[8]);
                            } else {
                                yaw = sender instanceof Player ? ((Player) sender).getLocation().getYaw() : 0F;
                            }

                            if (ImageFrame.itemFrameSelectionManager.isInSelection(sender)) {
                                ImageFrame.itemFrameSelectionManager.setInSelection(sender, false);
                            }
                            ImageFrame.itemFrameSelectionManager.applyDirectItemFrameSelection(sender, yaw, pos1, pos2);
                        } else {
                            sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                        }
                    } catch (NumberFormatException e) {
                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                    }
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("marker")) {
            if (sender.hasPermission("imageframe.marker")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (args.length > 1) {
                        if (args[1].equalsIgnoreCase("add")) {
                            if (args.length > 5) {
                                ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(player, args[2]);
                                if (imageMap == null) {
                                    sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                                } else if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.MARKER)) {
                                    sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                                } else {
                                    if (ImageFrame.mapMarkerEditManager.isActiveEditing(player)) {
                                        ImageFrame.mapMarkerEditManager.leaveActiveEditing(player);
                                    }
                                    try {
                                        String name = args[3];
                                        if (imageMap.getMapMarker(name) == null) {
                                            byte direction = Byte.parseByte(args[4]);
                                            MapCursor.Type type = MapCursor.Type.valueOf(args[5].toUpperCase());
                                            String caption = args.length > 6 ? ChatColorUtils.translateAlternateColorCodes('&', String.join(" ", Arrays.copyOfRange(args, 6, args.length))) : null;
                                            MapCursor mapCursor = new MapCursor((byte) 0, (byte) 0, direction, type, true, caption);
                                            ImageFrame.mapMarkerEditManager.setActiveEditing(player, name, mapCursor, imageMap);
                                            if (!MapUtils.isRenderOnFrame(type)) {
                                                sendMessage(player, translatable(MARKERS_NOT_RENDER_WARNING).color(NamedTextColor.YELLOW));
                                            }
                                            sendMessage(player, translatable(MARKERS_ADD_BEGIN, imageMap.getName()).color(NamedTextColor.GREEN));
                                        } else {
                                            sendMessage(player, translatable(MARKERS_DUPLICATE_NAME).color(NamedTextColor.RED));
                                        }
                                    } catch (IllegalArgumentException e) {
                                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                                    }
                                }
                            } else {
                                sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                            }
                        } else if (args[1].equalsIgnoreCase("remove")) {
                            if (args.length > 3) {
                                ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), args[2]);
                                if (imageMap == null) {
                                    sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                                } else {
                                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                        for (Map<String, MapCursor> map : imageMap.getMapMarkers()) {
                                            if (map.remove(args[3]) != null) {
                                                try {
                                                    sendMessage(sender, translatable(MARKERS_REMOVE).color(NamedTextColor.YELLOW));
                                                    Bukkit.getPluginManager().callEvent(new ImageMapUpdatedEvent(imageMap));
                                                    imageMap.send(imageMap.getViewers());
                                                    imageMap.save();
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                                return;
                                            }
                                        }
                                        sendMessage(sender, translatable(MARKERS_NOT_A_MARKER).color(NamedTextColor.RED));
                                    });
                                }
                            } else {
                                sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                            }
                        } else if (args[1].equalsIgnoreCase("clear")) {
                            if (args.length > 2) {
                                ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), args[2]);
                                if (imageMap == null) {
                                    sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                                } else {
                                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                        try {
                                            imageMap.getMapMarkers().forEach(each -> each.clear());
                                            sendMessage(sender, translatable(MARKERS_CLEAR).color(NamedTextColor.YELLOW));
                                            Bukkit.getPluginManager().callEvent(new ImageMapUpdatedEvent(imageMap));
                                            imageMap.send(imageMap.getViewers());
                                            imageMap.save();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    });
                                }
                            } else {
                                sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                            }
                        } else if (args[1].equalsIgnoreCase("cancel")) {
                            MapMarkerEditManager.MapMarkerEditData editData = ImageFrame.mapMarkerEditManager.leaveActiveEditing(player);
                            sendMessage(sender, translatable(MARKERS_CANCEL).color(NamedTextColor.YELLOW));
                            if (editData != null) {
                                Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> editData.getImageMap().send(editData.getImageMap().getViewers()));
                            }
                        } else {
                            sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                        }
                    } else {
                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                    }
                } else {
                    sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("refresh")) {
            if (sender.hasPermission("imageframe.refresh")) {
                Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                    ImageMap imageMap = null;
                    String url = null;
                    DitheringType ditheringType = null;
                    if (args.length > 1) {
                        imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                        if (imageMap == null) {
                            ditheringType = DitheringType.fromNameOrNull(args[1].toLowerCase());
                            if (ditheringType == null) {
                                url = args[1];
                            }
                        } else if (args.length > 2) {
                            ditheringType = DitheringType.fromNameOrNull(args[2].toLowerCase());
                            if (ditheringType == null) {
                                url = args[2];
                            }
                        }
                    }
                    if (imageMap == null) {
                        if (!(sender instanceof Player)) {
                            sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                            return;
                        }
                        MapView mapView = MapUtils.getPlayerMapView((Player) sender);
                        if (mapView == null) {
                            sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                            return;
                        }
                        imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                    }
                    if (imageMap == null) {
                        sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                    } else {
                        if (ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.EDIT)) {
                            try {
                                if (imageMap instanceof URLImageMap) {
                                    URLImageMap urlImageMap = (URLImageMap) imageMap;
                                    String imageType = HTTPRequestUtils.getContentType(url);
                                    if (imageType == null) {
                                        imageType = URLConnection.guessContentTypeFromName(url);
                                    }
                                    if (imageType == null) {
                                        imageType = "";
                                    } else {
                                        imageType = imageType.trim();
                                    }
                                    if (!ImageFrame.isURLAllowed(url)) {
                                        sendMessage(sender, translatable(URL_RESTRICTED).color(NamedTextColor.RED));
                                        return;
                                    }
                                    if (imageType.equals(MapUtils.GIF_CONTENT_TYPE) == urlImageMap.requiresAnimationService()) {
                                        String oldUrl = urlImageMap.getUrl();
                                        if (url != null) {
                                            urlImageMap.setUrl(url);
                                        }
                                        if (ImageFrame.imageUploadManager.isOperational() && urlImageMap.getUrl().equalsIgnoreCase("upload")) {
                                            UUID user = !(sender instanceof Player) ? ImageMap.CONSOLE_CREATOR : ((Player) sender).getUniqueId();
                                            PendingUpload pendingUpload = ImageFrame.imageUploadManager.newPendingUpload(user);
                                            String uploadUrl = pendingUpload.getUrl(ImageFrame.uploadServiceDisplayURL, user);
                                            Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, () -> sendMessage(sender, translatable(UPLOAD_LINK, Component.text(uploadUrl).color(NamedTextColor.YELLOW).clickEvent(ClickEvent.openUrl(uploadUrl))).color(NamedTextColor.GREEN)), 2);
                                            String newUrl = pendingUpload.getFileBlocking().toURI().toURL().toString();
                                            if (HTTPRequestUtils.getContentSize(newUrl) > ImageFrame.maxImageFileSize) {
                                                sendMessage(sender, translatable(IMAGE_OVER_MAX_FILE_SIZE, ImageFrame.maxImageFileSize).color(NamedTextColor.RED));
                                                throw new IOException("Image over max file size");
                                            }
                                            urlImageMap.setUrl(newUrl);
                                        }
                                        if (ditheringType != null) {
                                            urlImageMap.setDitheringType(ditheringType);
                                        }
                                        try {
                                            imageMap.update();
                                            sendMessage(sender, translatable(IMAGE_MAP_REFRESHED).color(NamedTextColor.GREEN));
                                        } catch (Throwable e) {
                                            urlImageMap.setUrl(oldUrl);
                                            sendMessage(sender, translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                                            e.printStackTrace();
                                        }
                                    } else {
                                        sendMessage(sender, translatable(UNABLE_TO_CHANGE_IMAGE_TYPE).color(NamedTextColor.RED));
                                    }
                                } else {
                                    if (ditheringType != null) {
                                        imageMap.setDitheringType(ditheringType);
                                    }
                                    imageMap.update();
                                    sendMessage(sender, translatable(IMAGE_MAP_REFRESHED).color(NamedTextColor.GREEN));
                                }
                            } catch (ImageUploadManager.LinkTimeoutException e) {
                                sendMessage(sender, translatable(UPLOAD_EXPIRED).color(NamedTextColor.RED));
                            } catch (Exception e) {
                                sendMessage(sender, translatable(UNABLE_TO_LOAD_MAP).color(NamedTextColor.RED));
                                e.printStackTrace();
                            }
                        } else {
                            sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                        }
                    }
                });
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("rename")) {
            if (sender.hasPermission("imageframe.rename")) {
                if (args.length > 2) {
                    ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                    if (imageMap == null) {
                        sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                    } else if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.EDIT)) {
                        sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                    } else {
                        String newName = args[2];
                        if (ImageFrame.imageMapManager.getFromCreator(imageMap.getCreator(), newName) == null) {
                            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    imageMap.rename(newName);
                                    sendMessage(sender, translatable(IMAGE_MAP_RENAMED).color(NamedTextColor.GREEN));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        } else {
                            sendMessage(sender, translatable(DUPLICATE_MAP_NAME).color(NamedTextColor.RED));
                        }
                    }
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("info")) {
            if (sender.hasPermission("imageframe.info")) {
                MapView mapView;
                if (args.length > 1) {
                    ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                    if (imageMap == null) {
                        sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                        return true;
                    }
                    mapView = imageMap.getMapViews().get(0);
                } else if (sender instanceof Player) {
                    mapView = MapUtils.getPlayerMapView((Player) sender);
                } else {
                    mapView = null;
                }
                if (mapView == null) {
                    sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                } else {
                    ImageMap imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                    if (imageMap == null) {
                        sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                    } else {
                        Component access = Component.empty().append(Component.text("[").color(NamedTextColor.AQUA)).append(imageMap.getAccessControl().getPermissions().entrySet().stream()
                                .sorted(Map.Entry.comparingByValue())
                                .map(each -> {
                                    UUID uuid = each.getKey();
                                    Component playerName;
                                    if (uuid.equals(ImageMapAccessControl.EVERYONE)) {
                                        playerName = text("*");
                                    } else {
                                        String offlinePlayerName = Bukkit.getOfflinePlayer(uuid).getName();
                                        playerName = Component.text(offlinePlayerName == null ? uuid.toString() : offlinePlayerName);
                                    }
                                    Component permission = translatable(ACCESS_TYPE(each.getValue())).fallback(each.getValue().name());
                                    return playerName.append(Component.text(" - ")).append(permission).color(NamedTextColor.YELLOW);
                                })
                                .collect(Component.toComponent(Component.text(", ").color(NamedTextColor.AQUA)))
                        ).append(Component.text("]").color(NamedTextColor.AQUA));

                        Component markers = Component.empty().append(Component.text("[").color(NamedTextColor.AQUA)).append(imageMap.getMapMarkers().stream()
                                .flatMap(each -> each.keySet().stream())
                                .map(each -> Component.text(each).color(NamedTextColor.YELLOW))
                                .collect(Component.toComponent(Component.text(", ").color(NamedTextColor.AQUA)))
                        ).append(Component.text("]").color(NamedTextColor.AQUA));
                        
                        sendMessage(sender, translatable(URL_IMAGE_MAP_INFO_1, text(imageMap.getImageIndex()).color(NamedTextColor.YELLOW)).color(NamedTextColor.AQUA));
                        sendMessage(sender, translatable(URL_IMAGE_MAP_INFO_2, text(imageMap.getName()).color(NamedTextColor.YELLOW)).color(NamedTextColor.AQUA));
                        sendMessage(sender, translatable(URL_IMAGE_MAP_INFO_3, text(imageMap.getWidth()).color(NamedTextColor.YELLOW), text(imageMap.getHeight()).color(NamedTextColor.YELLOW)).color(NamedTextColor.AQUA));
                        sendMessage(sender, translatable(URL_IMAGE_MAP_INFO_4, text(imageMap.getDitheringType().getName()).color(NamedTextColor.YELLOW)).color(NamedTextColor.AQUA));
                        sendMessage(sender, translatable(URL_IMAGE_MAP_INFO_5, text(imageMap.getCreatorName()).color(NamedTextColor.YELLOW), text(imageMap.getCreator().toString()).color(NamedTextColor.YELLOW)).color(NamedTextColor.AQUA));
                        sendMessage(sender, translatable(URL_IMAGE_MAP_INFO_6, access.color(NamedTextColor.YELLOW)).color(NamedTextColor.AQUA));
                        sendMessage(sender, translatable(URL_IMAGE_MAP_INFO_7, text(ImageFrame.dateFormat.format(new Date(imageMap.getCreationTime()))).color(NamedTextColor.YELLOW)).color(NamedTextColor.AQUA));
                        sendMessage(sender, translatable(URL_IMAGE_MAP_INFO_8, markers.color(NamedTextColor.YELLOW)).color(NamedTextColor.AQUA));
                        sendMessage(sender, translatable(URL_IMAGE_MAP_INFO_9, text(imageMap instanceof URLImageMap ? ((URLImageMap) imageMap).getUrl() : "-").color(NamedTextColor.YELLOW)).color(NamedTextColor.AQUA));
                    }
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("list")) {
            if (sender.hasPermission("imageframe.list")) {
                Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                    OfflinePlayer player = null;
                    if (args.length > 1) {
                        player = Bukkit.getOfflinePlayer(args[1]);
                    } else if (sender instanceof Player) {
                        player = (Player) sender;
                    }
                    if (player == null) {
                        sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                    } else {
                        if (!player.equals(sender) && !sender.hasPermission("imageframe.list.others")) {
                            sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                        } else {
                            Component prefix = translatable(MAP_LOOKUP, player.getName() == null ? "" : player.getName(), player.getUniqueId()).color(NamedTextColor.AQUA);
                            Component suffix = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), Comparator.comparing(each -> each.getCreationTime()))
                                    .stream()
                                    .map(each -> {
                                        String qualifiedName = each.getCreatorName() + ":" + each.getName();
                                        return text(each.getName()).color(NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/imageframe info " + qualifiedName));
                                    })
                                    .collect(Component.toComponent(text(", ").color(NamedTextColor.AQUA)));
                            Component message = prefix.append(text(" [").color(NamedTextColor.AQUA)).append(suffix).append(text("]").color(NamedTextColor.AQUA));
                            sendMessage(sender, message);
                        }
                    }
                });
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("delete")) {
            if (sender.hasPermission("imageframe.delete")) {
                if (args.length > 1) {
                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                        try {
                            ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                            if (imageMap == null) {
                                if (!(sender instanceof Player)) {
                                    sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                                    return;
                                }
                                MapView mapView = MapUtils.getPlayerMapView((Player) sender);
                                if (mapView == null) {
                                    sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                                    return;
                                }
                                imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                            }
                            if (imageMap == null) {
                                sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                            } else if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.ALL)) {
                                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                            } else {
                                ImageFrame.imageMapManager.deleteMap(imageMap.getImageIndex());
                                sendMessage(sender, translatable(IMAGE_MAP_DELETED).color(NamedTextColor.YELLOW));
                                if (sender instanceof Player) {
                                    Player player = (Player) sender;
                                    Scheduler.runTask(ImageFrame.plugin, () -> {
                                        Inventory inventory = player.getInventory();
                                        for (int i = 0; i < inventory.getSize(); i++) {
                                            ItemStack currentItem = inventory.getItem(i);
                                            MapView currentMapView = MapUtils.getItemMapView(currentItem);
                                            if (currentMapView != null) {
                                                if (ImageFrame.imageMapManager.isMapDeleted(currentMapView) && !ImageFrame.exemptMapIdsFromDeletion.satisfies(currentMapView.getId())) {
                                                    inventory.setItem(i, new ItemStack(Material.MAP, currentItem.getAmount()));
                                                }
                                            }
                                        }
                                    }, player);
                                }
                            }
                        } catch (NumberFormatException e) {
                            sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                        }
                    });
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("purge")) {
            if (sender.hasPermission("imageframe.purge")) {
                if (args.length > 1) {
                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                        Set<UUID> uuids;
                        if (args[1].equalsIgnoreCase("*")) {
                            if (sender.hasPermission("imageframe.purge.all")) {
                                uuids = ImageFrame.imageMapManager.getCreators();
                            } else {
                                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                                uuids = Collections.emptySet();
                            }
                        } else {
                            UUID uuid;
                            try {
                                uuid = UUID.fromString(args[1]);
                            } catch (IllegalArgumentException e) {
                                uuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                            }
                            uuids = Collections.singleton(uuid);
                        }
                        for (UUID uuid : uuids) {
                            List<String> names = new ArrayList<>();
                            for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(uuid)) {
                                if (ImageFrame.imageMapManager.deleteMap(imageMap.getImageIndex())) {
                                    names.add(imageMap.getName());
                                }
                            }
                            String playerName = Bukkit.getOfflinePlayer(uuid).getName();
                            Component mapNames = names.stream().map(n -> text(n).color(NamedTextColor.RED)).collect(Component.toComponent(text(", ").color(NamedTextColor.YELLOW)));
                            sendMessage(sender, translatable(IMAGE_MAP_PLAYER_PURGE, names.size(), playerName == null ? ImageMap.UNKNOWN_CREATOR_NAME : playerName, uuid, mapNames).color(NamedTextColor.YELLOW));
                        }
                    });
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("setaccess")) {
            if (sender.hasPermission("imageframe.setaccess")) {
                if (args.length > 3) {
                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                        try {
                            ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                            if (imageMap == null) {
                                if (!(sender instanceof Player)) {
                                    sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                                    return;
                                }
                                MapView mapView = MapUtils.getPlayerMapView((Player) sender);
                                if (mapView == null) {
                                    sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                                    return;
                                }
                                imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                            }
                            if (imageMap == null) {
                                sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                            } else if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.ALL)) {
                                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                            } else {
                                String targetPlayer = args[2];
                                boolean isEveryone = targetPlayer.equals("*");
                                UUID uuid = isEveryone ? ImageMapAccessControl.EVERYONE : Bukkit.getOfflinePlayer(targetPlayer).getUniqueId();
                                String permissionTypeStr = args[3].toUpperCase();
                                try {
                                    ImageMapAccessControl accessControl = imageMap.getAccessControl();
                                    ImageMapAccessPermissionType permissionType = permissionTypeStr.equals("NONE") ? null : ImageMapAccessPermissionType.valueOf(permissionTypeStr);
                                    accessControl.setPermission(uuid, permissionType);
                                    ImageMapAccessPermissionType newPermission = accessControl.getPermission(uuid);
                                    String newPermissionName = newPermission == null ? "NONE" : newPermission.name();
                                    sendMessage(sender, translatable(ACCESS_UPDATED, targetPlayer, uuid, translatable(ACCESS_TYPE(newPermission)).fallback(newPermissionName)).color(NamedTextColor.GREEN));
                                } catch (IllegalArgumentException e) {
                                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                                } catch (Exception e) {
                                    sendMessage(sender, translatable(UNKNOWN_ERROR).color(NamedTextColor.RED));
                                }
                            }
                        } catch (NumberFormatException e) {
                            sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                        }
                    });
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("get")) {
            if (sender.hasPermission("imageframe.get")) {
                Player senderPlayer = sender instanceof Player ? (Player) sender : null;
                if (args.length > 1) {
                    try {
                        boolean combined;
                        if (ImageFrame.combinedByDefault) {
                            combined = !(args.length > 2 && args[2].equalsIgnoreCase("separated"));
                        } else {
                            combined = args.length > 2 && args[2].equalsIgnoreCase("combined");
                        }
                        ItemFrameSelectionManager.SelectedItemFrameResult selection;
                        if (args.length > 2 && args[2].equalsIgnoreCase("selection") && senderPlayer != null) {
                            selection = ImageFrame.itemFrameSelectionManager.getConfirmedSelections(senderPlayer);
                            if (selection == null) {
                                sendMessage(sender, translatable(SELECTION_NO_SELECTION).color(NamedTextColor.RED));
                                return true;
                            }
                        } else {
                            selection = null;
                        }
                        Player player;
                        if (selection == null && sender.hasPermission("imageframe.get.others")) {
                            int pos = combined ? 3 : 2;
                            if (args.length > pos) {
                                player = Bukkit.getPlayer(args[pos]);
                                if (player == null) {
                                    sendMessage(sender, translatable(PLAYER_NOT_FOUND).color(NamedTextColor.RED));
                                    return true;
                                }
                            } else {
                                if (sender instanceof Player) {
                                    player = (Player) sender;
                                } else {
                                    sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                                    return true;
                                }
                            }
                        } else {
                            if (sender instanceof Player) {
                                player = (Player) sender;
                            } else {
                                sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                                return true;
                            }
                        }

                        ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                        if (imageMap == null) {
                            sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                        } else if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.GET)) {
                            sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                        } else {
                            if (selection != null) {
                                if (imageMap.getWidth() != selection.getWidth() || imageMap.getHeight() != selection.getHeight()) {
                                    sendMessage(sender, translatable(SELECTION_INCORRECT_SIZE, imageMap.getWidth(), imageMap.getHeight()).color(NamedTextColor.RED));
                                    return true;
                                }
                            }
                            if (ImageFrame.requireEmptyMaps && senderPlayer != null) {
                                if (MapUtils.removeEmptyMaps(senderPlayer, imageMap.getMapViews().size(), true) < 0) {
                                    sendMessage(sender, translatable(NOT_ENOUGH_MAPS, imageMap.getMapViews().size()).color(NamedTextColor.RED));
                                    return true;
                                }
                            }
                            if (combined) {
                                ImageFrame.combinedMapItemHandler.giveCombinedMap(imageMap, player);
                            } else if (selection == null) {
                                imageMap.giveMaps(player, ImageFrame.mapItemFormat);
                            } else {
                                AtomicBoolean flag = new AtomicBoolean(false);
                                imageMap.fillItemFrames(selection.getItemFrames(), selection.getRotation(), (frame, item) -> {
                                    ItemStack originalItem = frame.getItem();
                                    if (originalItem != null && !originalItem.getType().equals(Material.AIR)) {
                                        return false;
                                    }
                                    return PlayerUtils.isInteractionAllowed(player, frame);
                                }, (frame, item) -> {
                                    PlayerUtils.giveItem(player, item);
                                    if (!flag.getAndSet(true)) {
                                        sendMessage(sender, translatable(ITEM_FRAME_OCCUPIED).color(NamedTextColor.RED));
                                    }
                                }, ImageFrame.mapItemFormat);
                            }
                            sendMessage(sender, translatable(IMAGE_MAP_CREATED).color(NamedTextColor.GREEN));
                        }
                    } catch (NumberFormatException e) {
                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                    }
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("admindelete")) {
            if (sender.hasPermission("imageframe.admindelete")) {
                if (args.length > 1) {
                    try {
                        int imageId = Integer.parseInt(args[1]);
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(imageId);
                        if (imageMap == null) {
                            sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                        } else {
                            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                ImageFrame.imageMapManager.deleteMap(imageMap.getImageIndex());
                                sendMessage(sender, translatable(IMAGE_MAP_DELETED).color(NamedTextColor.YELLOW));
                                if (sender instanceof Player) {
                                    Player player = (Player) sender;
                                    Scheduler.runTask(ImageFrame.plugin, () -> {
                                        Inventory inventory = player.getInventory();
                                        for (int i = 0; i < inventory.getSize(); i++) {
                                            ItemStack currentItem = inventory.getItem(i);
                                            MapView currentMapView = MapUtils.getItemMapView(currentItem);
                                            if (currentMapView != null) {
                                                if (ImageFrame.imageMapManager.isMapDeleted(currentMapView) && !ImageFrame.exemptMapIdsFromDeletion.satisfies(currentMapView.getId())) {
                                                    inventory.setItem(i, new ItemStack(Material.MAP, currentItem.getAmount()));
                                                }
                                            }
                                        }
                                    }, player);
                                }
                            });
                        }
                    } catch (NumberFormatException e) {
                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                    }
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("adminsetcreator")) {
            if (sender.hasPermission("imageframe.adminsetcreator")) {
                if (args.length > 2) {
                    try {
                        int imageId = Integer.parseInt(args[1]);
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(imageId);
                        if (imageMap == null) {
                            sendMessage(sender, translatable(NOT_AN_IMAGE_MAP).color(NamedTextColor.RED));
                        } else {
                            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    OfflinePlayer player = Bukkit.getOfflinePlayer(args[2]);
                                    imageMap.changeCreator(player.getUniqueId());
                                    sendMessage(sender, translatable(SET_CREATOR, imageMap.getImageIndex(), imageMap.getCreatorName(), imageMap.getCreator()).color(NamedTextColor.GREEN));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    } catch (NumberFormatException e) {
                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                    }
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("adminmigrate")) {
            if (sender.hasPermission("imageframe.adminmigrate")) {
                if (args.length > 1) {
                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                        ExternalPluginMigration migration = PluginMigrationRegistry.getMigration(args[1]);
                        if (migration != null) {
                            if (migration.requirePlayer()) {
                                if (sender instanceof Player) {
                                    sendMessage(sender, ChatColor.YELLOW + "Migration has begun, see console for progress, completion and errors");
                                    migration.migrate(((Player) sender).getUniqueId());
                                } else {
                                    sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                                }
                            } else {
                                sendMessage(sender, ChatColor.YELLOW + "Migration has begun, see console for progress, completion and errors");
                                migration.migrate();
                            }
                        } else {
                            sendMessage(sender, args[1] + " is not a supported plugin for migration");
                        }
                    });
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("preference")) {
            if (sender.hasPermission("imageframe.preference")) {
                if (args.length > 2) {
                    IFPlayerPreference<?> preference = IFPlayerPreference.valueOf(args[1].toUpperCase());
                    if (preference == null) {
                        sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                    } else {
                        IFPlayerPreference.StringDeserializerResult<?> result = preference.getStringDeserializer().apply(args[2]);
                        if (result.isAccepted()) {
                            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                UUID player;
                                if (args.length > 3) {
                                    if (sender.hasPermission("imageframe.preference.others")) {
                                        player = Bukkit.getOfflinePlayer(args[3]).getUniqueId();
                                    } else {
                                        sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                                        return;
                                    }
                                } else if (sender instanceof Player) {
                                    player = ((Player) sender).getUniqueId();
                                } else {
                                    sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                                    return;
                                }
                                PreferenceState value = result.getValue();
                                IFPlayer ifPlayer = ImageFrame.ifPlayerManager.getIFPlayer(player);
                                ifPlayer.setPreference(preference, value);
                                try {
                                    ifPlayer.save();
                                    sendMessage(sender, translatable(PREFERENCES_UPDATE, translatable(PREFERENCES_TYPE(preference)).fallback(preference.name()).color(NamedTextColor.WHITE), translatable(PREFERENCES_VALUE(value)).fallback(value.name()).color(value.getDisplayColor())).color(NamedTextColor.YELLOW));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        } else {
                            sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                        }
                    }
                } else {
                    sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("giveinvisibleframe")) {
            if (args.length > 1) {
                if (sender.hasPermission("imageframe.giveinvisibleframe")) {
                    Player target;
                    if (args.length > 3) {
                        target = Bukkit.getPlayer(args[3]);
                        if (target == null) {
                            sendMessage(sender, translatable(PLAYER_NOT_FOUND).color(NamedTextColor.RED));
                            return true;
                        }
                    } else if (!(sender instanceof Player)) {
                        sendMessage(sender, translatable(NO_CONSOLE).color(NamedTextColor.RED));
                        return true;
                    } else {
                        target = (Player) sender;
                    }
                    String type = args[1];
                    int amount = 1;
                    try {
                        amount = args.length > 2 ? Integer.parseInt(args[2]) : 1;
                    } catch (NumberFormatException ignore) {
                    }
                    amount = Math.max(1, amount);
                    ItemStack itemStack;
                    if (type.equalsIgnoreCase("glowing") && ImageFrame.version.isNewerOrEqualTo(MCVersion.V1_17)) {
                        itemStack = new ItemStack(Material.valueOf("GLOW_ITEM_FRAME"));
                    } else {
                        itemStack = new ItemStack(Material.ITEM_FRAME);
                    }
                    ItemStack modified = ImageFrame.invisibleFrameManager.withInvisibleItemFrameData(itemStack);
                    for (PrimitiveIterator.OfInt itr = MathUtils.splitToChunksOf(amount, 64); itr.hasNext();) {
                        ItemStack finalStack = modified.clone();
                        finalStack.setAmount(itr.nextInt());
                        PlayerUtils.giveItem(target, finalStack);
                    }
                    sendMessage(sender, translatable(GIVEN_INVISIBLE_FRAME, amount, target.getName()).color(NamedTextColor.GREEN));
                } else {
                    sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
            }
            return true;
        } else if (args[0].equalsIgnoreCase("storagemigrate")) {
            if (args.length > 1) {
                if (sender.hasPermission("imageframe.storagemigrate")) {
                    sendMessage(sender, translatable(STORAGE_MIGRATION).color(NamedTextColor.GREEN));
                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                        try {
                            ImageFrameStorageLoader<?> loader = ImageFrameStorageLoaders.getLoader(KeyUtils.imageFrameKey(args[1]));

                            boolean forced = args.length > 2 && args[2].equalsIgnoreCase("forced");
                            Map<String, String> options = new HashMap<>();

                            int argIndex = 3;
                            for (String optionKey : loader.getRequiredOptions()) {
                                if (args.length > argIndex) {
                                    options.put(optionKey, args[argIndex]);
                                } else {
                                    throw new IllegalArgumentException("Missing required option: " + optionKey);
                                }
                                argIndex++;
                            }
                            for (String optionKey : loader.getOptionalOptions()) {
                                if (args.length > argIndex) {
                                    options.put(optionKey, args[argIndex]);
                                }
                                argIndex++;
                            }

                            try (
                                ImageFrameStorage targetStorage = ImageFrameStorageLoaders.create(KeyUtils.imageFrameKey(args[1]), ImageFrame.plugin.getDataFolder(), options);
                                StorageMigrator storageMigrator = new StorageMigrator(ImageFrame.imageMapManager, ImageFrame.ifPlayerManager, targetStorage);
                            ) {
                                if (!storageMigrator.isTargetEmpty() && !forced) {
                                    throw new IllegalStateException("Target storage is not empty, if you wish to migrate anyway, please set the forced flag to true.");
                                } else {
                                    Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + "[ImageFrame] Beginning to migrate image map data to " + loader.getIdentifier().asString());
                                    storageMigrator.migrateImageMaps();
                                    Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + "[ImageFrame] Beginning to migrate deleted maps data to " + loader.getIdentifier().asString());
                                    storageMigrator.migrateDeletedMaps();
                                    Bukkit.getConsoleSender().sendMessage(ChatColor.GRAY + "[ImageFrame] Beginning to migrate ImageFrame player data to " + loader.getIdentifier().asString());
                                    storageMigrator.migrateIFPlayers();
                                    Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] Successfully migrated data to " + loader.getIdentifier().asString() + ", please stop the server and switch to that storage type in the config.");
                                }
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    });
                } else {
                    sendMessage(sender, translatable(NO_PERMISSION).color(NamedTextColor.RED));
                }
            } else {
                sendMessage(sender, translatable(INVALID_USAGE).color(NamedTextColor.RED));
            }
            return true;
        }

        sendMessage(sender, ChatColorUtils.translateAlternateColorCodes('&', Bukkit.spigot().getConfig().getString("messages.unknown-command")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> tab = new LinkedList<>();

        switch (args.length) {
            case 0:
                if (sender.hasPermission("imageframe.reload")) {
                    tab.add("reload");
                }
                if (sender.hasPermission("imageframe.resync")) {
                    tab.add("resync");
                }
                if (sender.hasPermission("imageframe.create")) {
                    tab.add("create");
                }
                if (sender.hasPermission("imageframe.overlay")) {
                    tab.add("overlay");
                }
                if (sender.hasPermission("imageframe.clone")) {
                    tab.add("clone");
                }
                if (sender.hasPermission("imageframe.playback")) {
                    tab.add("playback");
                }
                if (sender.hasPermission("imageframe.select")) {
                    tab.add("select");
                }
                if (sender.hasPermission("imageframe.refresh")) {
                    tab.add("refresh");
                }
                if (sender.hasPermission("imageframe.rename")) {
                    tab.add("rename");
                }
                if (sender.hasPermission("imageframe.info")) {
                    tab.add("info");
                }
                if (sender.hasPermission("imageframe.list")) {
                    tab.add("list");
                }
                if (sender.hasPermission("imageframe.delete")) {
                    tab.add("delete");
                }
                if (sender.hasPermission("imageframe.purge")) {
                    tab.add("purge");
                }
                if (sender.hasPermission("imageframe.get")) {
                    tab.add("get");
                }
                if (sender.hasPermission("imageframe.update")) {
                    tab.add("update");
                }
                if (sender.hasPermission("imageframe.marker")) {
                    tab.add("marker");
                }
                if (sender.hasPermission("imageframe.setaccess")) {
                    tab.add("setaccess");
                }
                if (sender.hasPermission("imageframe.admindelete")) {
                    tab.add("admindelete");
                }
                if (sender.hasPermission("imageframe.adminsetcreator")) {
                    tab.add("adminsetcreator");
                }
                if (sender.hasPermission("imageframe.adminmigrate")) {
                    tab.add("adminmigrate");
                }
                if (sender.hasPermission("imageframe.preference")) {
                    tab.add("preference");
                }
                if (sender.hasPermission("imageframe.giveinvisibleframe")) {
                    tab.add("giveinvisibleframe");
                }
                if (sender.hasPermission("imageframe.storagemigrate")) {
                    tab.add("storagemigrate");
                }
                return tab;
            case 1:
                if (sender.hasPermission("imageframe.reload")) {
                    if ("reload".startsWith(args[0].toLowerCase())) {
                        tab.add("reload");
                    }
                }
                if (sender.hasPermission("imageframe.resync")) {
                    if ("resync".startsWith(args[0].toLowerCase())) {
                        tab.add("resync");
                    }
                }
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".startsWith(args[0].toLowerCase())) {
                        tab.add("create");
                    }
                }
                if (sender.hasPermission("imageframe.overlay")) {
                    if ("overlay".startsWith(args[0].toLowerCase())) {
                        tab.add("overlay");
                    }
                }
                if (sender.hasPermission("imageframe.clone")) {
                    if ("clone".startsWith(args[0].toLowerCase())) {
                        tab.add("clone");
                    }
                }
                if (sender.hasPermission("imageframe.playback")) {
                    if ("playback".startsWith(args[0].toLowerCase())) {
                        tab.add("playback");
                    }
                }
                if (sender.hasPermission("imageframe.select")) {
                    if ("select".startsWith(args[0].toLowerCase())) {
                        tab.add("select");
                    }
                }
                if (sender.hasPermission("imageframe.refresh")) {
                    if ("refresh".startsWith(args[0].toLowerCase())) {
                        tab.add("refresh");
                    }
                }
                if (sender.hasPermission("imageframe.rename")) {
                    if ("rename".startsWith(args[0].toLowerCase())) {
                        tab.add("rename");
                    }
                }
                if (sender.hasPermission("imageframe.info")) {
                    if ("info".startsWith(args[0].toLowerCase())) {
                        tab.add("info");
                    }
                }
                if (sender.hasPermission("imageframe.list")) {
                    if ("list".startsWith(args[0].toLowerCase())) {
                        tab.add("list");
                    }
                }
                if (sender.hasPermission("imageframe.delete")) {
                    if ("delete".startsWith(args[0].toLowerCase())) {
                        tab.add("delete");
                    }
                }
                if (sender.hasPermission("imageframe.purge")) {
                    if ("purge".startsWith(args[0].toLowerCase())) {
                        tab.add("purge");
                    }
                }
                if (sender.hasPermission("imageframe.get")) {
                    if ("get".startsWith(args[0].toLowerCase())) {
                        tab.add("get");
                    }
                }
                if (sender.hasPermission("imageframe.update")) {
                    if ("update".startsWith(args[0].toLowerCase())) {
                        tab.add("update");
                    }
                }
                if (sender.hasPermission("imageframe.marker")) {
                    if ("marker".startsWith(args[0].toLowerCase())) {
                        tab.add("marker");
                    }
                }
                if (sender.hasPermission("imageframe.setaccess")) {
                    if ("setaccess".startsWith(args[0].toLowerCase())) {
                        tab.add("setaccess");
                    }
                }
                if (sender.hasPermission("imageframe.admindelete")) {
                    if ("admindelete".startsWith(args[0].toLowerCase())) {
                        tab.add("admindelete");
                    }
                }
                if (sender.hasPermission("imageframe.adminsetcreator")) {
                    if ("adminsetcreator".startsWith(args[0].toLowerCase())) {
                        tab.add("adminsetcreator");
                    }
                }
                if (sender.hasPermission("imageframe.adminmigrate")) {
                    if ("adminmigrate".startsWith(args[0].toLowerCase())) {
                        tab.add("adminmigrate");
                    }
                }
                if (sender.hasPermission("imageframe.preference")) {
                    if ("preference".startsWith(args[0].toLowerCase())) {
                        tab.add("preference");
                    }
                }
                if (sender.hasPermission("imageframe.giveinvisibleframe")) {
                    if ("giveinvisibleframe".startsWith(args[0].toLowerCase())) {
                        tab.add("giveinvisibleframe");
                    }
                }
                if (sender.hasPermission("imageframe.storagemigrate")) {
                    if ("storagemigrate".startsWith(args[0].toLowerCase())) {
                        tab.add("storagemigrate");
                    }
                }
                return tab;
            case 2:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        tab.add("<name>");
                    }
                }
                if (sender.hasPermission("imageframe.overlay")) {
                    if ("overlay".equalsIgnoreCase(args[0])) {
                        tab.add("<name>");
                    }
                }
                if (sender.hasPermission("imageframe.clone")) {
                    if ("clone".equalsIgnoreCase(args[0])) {
                        tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[1]));
                    }
                }
                if (sender.hasPermission("imageframe.playback")) {
                    if ("playback".equalsIgnoreCase(args[0])) {
                        tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[1], ImageMapAccessPermissionType.ADJUST_PLAYBACK, imageMap -> imageMap.requiresAnimationService()));
                    }
                }
                if (sender.hasPermission("imageframe.refresh")) {
                    if ("refresh".equalsIgnoreCase(args[0])) {
                        tab.add("[url]");
                        if (ImageFrame.imageUploadManager.isOperational() && "upload".startsWith(args[1].toLowerCase())) {
                            tab.add("upload");
                        }
                        tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[1]));
                        for (String ditheringType : DitheringType.values().keySet()) {
                            if (ditheringType.startsWith(args[1].toLowerCase())) {
                                tab.add(ditheringType);
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.select")) {
                    if ("list".equalsIgnoreCase(args[0])) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                tab.add(player.getName());
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.list.others")) {
                    if ("list".equalsIgnoreCase(args[0])) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                tab.add(player.getName());
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.rename")) {
                    if ("rename".equalsIgnoreCase(args[0])) {
                        tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[1]));
                    }
                }
                if (sender.hasPermission("imageframe.info")) {
                    if ("info".equalsIgnoreCase(args[0])) {
                        tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[1]));
                    }
                }
                if (sender.hasPermission("imageframe.delete")) {
                    if ("delete".equalsIgnoreCase(args[0])) {
                        tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[1]));
                    }
                }
                if (sender.hasPermission("imageframe.purge")) {
                    if ("purge".equalsIgnoreCase(args[0])) {
                        tab.add("<player>");
                    }
                }
                if (sender.hasPermission("imageframe.get")) {
                    if ("get".equalsIgnoreCase(args[0])) {
                        tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[1]));
                    }
                }
                if (sender.hasPermission("imageframe.marker")) {
                    if ("marker".equalsIgnoreCase(args[0])) {
                        if ("add".startsWith(args[1].toLowerCase())) {
                            tab.add("add");
                        }
                        if ("remove".startsWith(args[1].toLowerCase())) {
                            tab.add("remove");
                        }
                        if ("clear".startsWith(args[1].toLowerCase())) {
                            tab.add("clear");
                        }
                        if (sender instanceof Player && ImageFrame.mapMarkerEditManager.isActiveEditing((Player) sender)) {
                            if ("cancel".startsWith(args[1].toLowerCase())) {
                                tab.add("cancel");
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.setaccess")) {
                    if ("setaccess".equalsIgnoreCase(args[0])) {
                        tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[1]));
                    }
                }
                if (sender.hasPermission("imageframe.admindelete")) {
                    if ("admindelete".equalsIgnoreCase(args[0])) {
                        tab.add("<image-id>");
                    }
                }
                if (sender.hasPermission("imageframe.adminsetcreator")) {
                    if ("adminsetcreator".equalsIgnoreCase(args[0])) {
                        tab.add("<image-id>");
                    }
                }
                if (sender.hasPermission("imageframe.adminmigrate")) {
                    if ("adminmigrate".equalsIgnoreCase(args[0])) {
                        for (String plugin : PluginMigrationRegistry.getPluginNames()) {
                            if (plugin.toLowerCase().startsWith(args[1].toLowerCase())) {
                                tab.add(plugin);
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.preference")) {
                    if ("preference".equalsIgnoreCase(args[0])) {
                        for (IFPlayerPreference<?> preference : IFPlayerPreference.values()) {
                            if (preference.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                                tab.add(preference.name().toLowerCase());
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.giveinvisibleframe")) {
                    if ("giveinvisibleframe".equalsIgnoreCase(args[0])) {
                        if ("regular".startsWith(args[1].toLowerCase())) {
                            tab.add("regular");
                        }
                        if (ImageFrame.version.isNewerOrEqualTo(MCVersion.V1_17) && "glowing".startsWith(args[1].toLowerCase())) {
                            tab.add("glowing");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.storagemigrate")) {
                    if ("storagemigrate".equalsIgnoreCase(args[0])) {
                        for (ImageFrameStorageLoader<?> loader : ImageFrameStorageLoaders.getRegisteredLoaders()) {
                            if (!ImageFrame.imageFrameStorage.getLoader().getIdentifier().equals(loader.getIdentifier()) && loader.getIdentifier().asString().startsWith(args[1].toLowerCase())) {
                                tab.add(loader.getIdentifier().asString());
                            }
                        }
                    }
                }
                return tab;
            case 3:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        tab.add("<url>");
                        if (ImageFrame.imageUploadManager.isOperational() && "upload".startsWith(args[2].toLowerCase())) {
                            tab.add("upload");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.overlay")) {
                    if ("overlay".equalsIgnoreCase(args[0])) {
                        tab.add("<url>");
                        if (ImageFrame.imageUploadManager.isOperational() && "upload".startsWith(args[2].toLowerCase())) {
                            tab.add("upload");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.clone")) {
                    if ("clone".equalsIgnoreCase(args[0])) {
                        tab.add("<new-name>");
                    }
                }
                if (sender.hasPermission("imageframe.playback")) {
                    if ("playback".equalsIgnoreCase(args[0])) {
                        if ("pause".startsWith(args[2].toUpperCase())) {
                            tab.add("pause");
                        }
                        if ("jumpto".startsWith(args[2].toLowerCase())) {
                            tab.add("jumpto");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.refresh")) {
                    if ("refresh".equalsIgnoreCase(args[0])) {
                        ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                        if (imageMap != null) {
                            tab.add("[url]");
                            if (ImageFrame.imageUploadManager.isOperational() && "upload".startsWith(args[2].toLowerCase())) {
                                tab.add("upload");
                            }
                            for (String ditheringType : DitheringType.values().keySet()) {
                                if (ditheringType.startsWith(args[2].toLowerCase())) {
                                    tab.add(ditheringType);
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.get")) {
                    if ("get".equalsIgnoreCase(args[0])) {
                        if (sender instanceof Player && "selection".startsWith(args[2].toLowerCase())) {
                            tab.add("selection");
                        }
                        if (ImageFrame.combinedByDefault) {
                            if ("separated".startsWith(args[2].toLowerCase())) {
                                tab.add("separated");
                            }
                        } else {
                            if ("combined".startsWith(args[2].toLowerCase())) {
                                tab.add("combined");
                            }
                        }
                        if (sender.hasPermission("imageframe.get.others")) {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                    tab.add(player.getName());
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.rename")) {
                    if ("rename".equalsIgnoreCase(args[0])) {
                        tab.add("<new-name>");
                    }
                }
                if (sender.hasPermission("imageframe.marker")) {
                    if ("marker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[2]));
                        }
                        if ("remove".equalsIgnoreCase(args[1])) {
                            tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[2]));
                        }
                        if ("clear".equalsIgnoreCase(args[1])) {
                            tab.addAll(ImageMapUtils.getImageMapNameSuggestions(sender, args[2]));
                        }
                    }
                }
                if (sender.hasPermission("imageframe.setaccess")) {
                    if ("setaccess".equalsIgnoreCase(args[0])) {
                        ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                        UUID creator = imageMap == null ? null : imageMap.getCreator();
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (!player.getUniqueId().equals(creator) && player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                tab.add(player.getName());
                            }
                        }
                        if ("*".startsWith(args[2].toLowerCase())) {
                            tab.add("*");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.adminsetcreator")) {
                    if ("adminsetcreator".equalsIgnoreCase(args[0])) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                tab.add(player.getName());
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.preference")) {
                    if ("preference".equalsIgnoreCase(args[0])) {
                        IFPlayerPreference<?> preference = IFPlayerPreference.valueOf(args[1].toUpperCase());
                        if (preference != null) {
                            tab.addAll(preference.getSuggestedValues().keySet());
                        }
                    }
                }
                if (sender.hasPermission("imageframe.giveinvisibleframe")) {
                    if ("giveinvisibleframe".equalsIgnoreCase(args[0])) {
                        tab.add("<amount>");
                    }
                }
                if (sender.hasPermission("imageframe.storagemigrate")) {
                    if ("storagemigrate".equalsIgnoreCase(args[0])) {
                        if ("normal".startsWith(args[2].toLowerCase())) {
                            tab.add("normal");
                        }
                        if ("forced".startsWith(args[2].toLowerCase())) {
                            tab.add("forced");
                        }
                    }
                }
                return tab;
            case 4:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        if (args[3].matches("(?i)[0-9]*")) {
                            tab.add("<width>");
                        }
                        if ("selection".startsWith(args[3].toLowerCase())) {
                            tab.add("selection");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.overlay")) {
                    if ("overlay".equalsIgnoreCase(args[0])) {
                        if ("selection".startsWith(args[3].toLowerCase())) {
                            tab.add("selection");
                        }
                        for (String ditheringType : DitheringType.values().keySet()) {
                            if (ditheringType.startsWith(args[3].toLowerCase())) {
                                tab.add(ditheringType);
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.clone")) {
                    if ("clone".equalsIgnoreCase(args[0])) {
                        if ("selection".startsWith(args[3].toLowerCase())) {
                            tab.add("selection");
                        }
                        if (ImageFrame.combinedByDefault) {
                            if ("separated".startsWith(args[3].toLowerCase())) {
                                tab.add("separated");
                            }
                        } else {
                            if ("combined".startsWith(args[3].toLowerCase())) {
                                tab.add("combined");
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.playback")) {
                    if ("playback".equalsIgnoreCase(args[0])) {
                        if ("pause".equalsIgnoreCase(args[2])) {
                            if ("true".startsWith(args[3].toLowerCase())) {
                                tab.add("true");
                            }
                            if ("false".startsWith(args[3].toLowerCase())) {
                                tab.add("false");
                            }
                        }
                        if ("jumpto".equalsIgnoreCase(args[2])) {
                            tab.add("0.0");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.get")) {
                    if ("get".equalsIgnoreCase(args[0])) {
                        if (args[2].equalsIgnoreCase(ImageFrame.combinedByDefault ? "separated" :"combined")) {
                            if (sender.hasPermission("imageframe.get.others")) {
                                for (Player player : Bukkit.getOnlinePlayers()) {
                                    if (player.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                        tab.add(player.getName());
                                    }
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.marker")) {
                    if ("marker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            tab.add("<marker-name>");
                        }
                        if ("remove".equalsIgnoreCase(args[1])) {
                            if (sender instanceof Player) {
                                ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[2]);
                                if (imageMap != null) {
                                    for (Map<String, MapCursor> map : imageMap.getMapMarkers()) {
                                        for (String name : map.keySet()) {
                                            if (name.toLowerCase().startsWith(args[3].toLowerCase())) {
                                                tab.add(name);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.setaccess")) {
                    if ("setaccess".equalsIgnoreCase(args[0])) {
                        for (String typeName : ImageMapAccessPermissionType.values().keySet()) {
                            if (typeName.toLowerCase().startsWith(args[3].toLowerCase())) {
                                tab.add(typeName);
                            }
                        }
                        if ("NONE".toLowerCase().startsWith(args[3].toLowerCase())) {
                            tab.add("NONE");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.preference.others")) {
                    if ("preference".equalsIgnoreCase(args[0])) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                tab.add(player.getName());
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.giveinvisibleframe")) {
                    if ("giveinvisibleframe".equalsIgnoreCase(args[0])) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                tab.add(player.getName());
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.storagemigrate")) {
                    if ("storagemigrate".equalsIgnoreCase(args[0])) {
                        try {
                            ImageFrameStorageLoader<?> loader = ImageFrameStorageLoaders.getLoader(KeyUtils.imageFrameKey(args[1]));
                            Stream.concat(Arrays.stream(loader.getRequiredOptions()).map(k -> "<" + k + ">"), Arrays.stream(loader.getOptionalOptions()).map(k -> "[" + k + "]"))
                                    .findFirst()
                                    .ifPresent(optionKey -> tab.add(optionKey));
                        } catch (Throwable ignore) {
                        }
                    }
                }
                return tab;
            case 5:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        if (!args[3].equalsIgnoreCase("selection")) {
                            tab.add("<height>");
                        } else {
                            for (String ditheringType : DitheringType.values().keySet()) {
                                if (ditheringType.startsWith(args[4].toLowerCase())) {
                                    tab.add(ditheringType);
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.overlay")) {
                    if ("overlay".equalsIgnoreCase(args[0])) {
                        if (args[3].equalsIgnoreCase("selection")) {
                            for (String ditheringType : DitheringType.values().keySet()) {
                                if (ditheringType.startsWith(args[4].toLowerCase())) {
                                    tab.add(ditheringType);
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.marker")) {
                    if ("marker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            for (int i = 0; i < 16; i++) {
                                if (String.valueOf(i).startsWith(args[4])) {
                                    tab.add(String.valueOf(i));
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.storagemigrate")) {
                    if ("storagemigrate".equalsIgnoreCase(args[0])) {
                        try {
                            ImageFrameStorageLoader<?> loader = ImageFrameStorageLoaders.getLoader(KeyUtils.imageFrameKey(args[1]));
                            Stream.concat(Arrays.stream(loader.getRequiredOptions()).map(k -> "<" + k + ">"), Arrays.stream(loader.getOptionalOptions()).map(k -> "[" + k + "]"))
                                    .skip(1)
                                    .findFirst()
                                    .ifPresent(optionKey -> tab.add(optionKey));
                        } catch (Throwable ignore) {
                        }
                    }
                }
                return tab;
            case 6:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        if (!args[3].equalsIgnoreCase("selection")) {
                            if (ImageFrame.combinedByDefault) {
                                if ("separated".startsWith(args[5].toLowerCase())) {
                                    tab.add("separated");
                                }
                            } else {
                                if ("combined".startsWith(args[5].toLowerCase())) {
                                    tab.add("combined");
                                }
                            }
                            for (String ditheringType : DitheringType.values().keySet()) {
                                if (ditheringType.startsWith(args[5].toLowerCase())) {
                                    tab.add(ditheringType);
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.marker")) {
                    if ("marker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            for (MapCursor.Type type : MapCursor.Type.values()) {
                                if (type.name().toLowerCase().startsWith(args[5].toLowerCase())) {
                                    tab.add(type.name().toLowerCase());
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.storagemigrate")) {
                    if ("storagemigrate".equalsIgnoreCase(args[0])) {
                        try {
                            ImageFrameStorageLoader<?> loader = ImageFrameStorageLoaders.getLoader(KeyUtils.imageFrameKey(args[1]));
                            Stream.concat(Arrays.stream(loader.getRequiredOptions()).map(k -> "<" + k + ">"), Arrays.stream(loader.getOptionalOptions()).map(k -> "[" + k + "]"))
                                    .skip(2)
                                    .findFirst()
                                    .ifPresent(optionKey -> tab.add(optionKey));
                        } catch (Throwable ignore) {
                        }
                    }
                }
                return tab;
            case 7:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        if (!args[3].equalsIgnoreCase("selection") && !args[5].equalsIgnoreCase(ImageFrame.combinedByDefault ? "separated" :"combined")) {
                            if (ImageFrame.combinedByDefault) {
                                if ("separated".startsWith(args[6].toLowerCase())) {
                                    tab.add("separated");
                                }
                            } else {
                                if ("combined".startsWith(args[6].toLowerCase())) {
                                    tab.add("combined");
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.storagemigrate")) {
                    if ("storagemigrate".equalsIgnoreCase(args[0])) {
                        try {
                            ImageFrameStorageLoader<?> loader = ImageFrameStorageLoaders.getLoader(KeyUtils.imageFrameKey(args[1]));
                            Stream.concat(Arrays.stream(loader.getRequiredOptions()).map(k -> "<" + k + ">"), Arrays.stream(loader.getOptionalOptions()).map(k -> "[" + k + "]"))
                                    .skip(3)
                                    .findFirst()
                                    .ifPresent(optionKey -> tab.add(optionKey));
                        } catch (Throwable ignore) {
                        }
                    }
                }
                return tab;
            default:
                if (sender.hasPermission("imageframe.marker")) {
                    if ("marker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            tab.add("[caption]...");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.storagemigrate")) {
                    if ("storagemigrate".equalsIgnoreCase(args[0])) {
                        try {
                            ImageFrameStorageLoader<?> loader = ImageFrameStorageLoaders.getLoader(KeyUtils.imageFrameKey(args[1]));
                            Stream.concat(Arrays.stream(loader.getRequiredOptions()).map(k -> "<" + k + ">"), Arrays.stream(loader.getOptionalOptions()).map(k -> "[" + k + "]"))
                                    .skip(args.length - 4)
                                    .findFirst()
                                    .ifPresent(optionKey -> tab.add(optionKey));
                        } catch (Throwable ignore) {
                        }
                    }
                }
                return tab;
        }
    }

}
