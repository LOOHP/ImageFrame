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
import com.loohp.imageframe.objectholders.ItemFrameSelectionManager;
import com.loohp.imageframe.objectholders.MapMarkerEditManager;
import com.loohp.imageframe.objectholders.MinecraftURLOverlayImageMap;
import com.loohp.imageframe.objectholders.MutablePair;
import com.loohp.imageframe.objectholders.URLAnimatedImageMap;
import com.loohp.imageframe.objectholders.URLImageMap;
import com.loohp.imageframe.objectholders.URLStaticImageMap;
import com.loohp.imageframe.updater.Updater;
import com.loohp.imageframe.upload.ImageUploadManager;
import com.loohp.imageframe.upload.PendingUpload;
import com.loohp.imageframe.utils.ChatColorUtils;
import com.loohp.imageframe.utils.HTTPRequestUtils;
import com.loohp.imageframe.utils.ImageMapUtils;
import com.loohp.imageframe.utils.MCVersion;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.imageframe.utils.MathUtils;
import com.loohp.imageframe.utils.PlayerUtils;
import com.loohp.platformscheduler.Scheduler;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Commands implements CommandExecutor, TabCompleter {

    @SuppressWarnings({"CallToPrintStackTrace", "deprecation", "NullableProblems"})
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.DARK_AQUA + "ImageFrame written by LOOHP!");
            sender.sendMessage(ChatColor.GOLD + "You are running ImageFrame version: " + ImageFrame.plugin.getDescription().getVersion());
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("imageframe.reload")) {
                ImageFrame.plugin.reloadConfig();
                sender.sendMessage(ImageFrame.messageReloaded);
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("update")) {
            if (sender.hasPermission("imageframe.update")) {
                sender.sendMessage(ChatColor.DARK_AQUA + "[ImageFrame] ImageFrame written by LOOHP!");
                sender.sendMessage(ChatColor.GOLD + "[ImageFrame] You are running ImageFrame version: " + ImageFrame.plugin.getDescription().getVersion());
                Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                    Updater.UpdaterResponse version = Updater.checkUpdate();
                    if (version.getResult().equals("latest")) {
                        if (version.isDevBuildLatest()) {
                            sender.sendMessage(ChatColor.GREEN + "[ImageFrame] You are running the latest version!");
                        } else {
                            Updater.sendUpdateMessage(sender, version.getResult(), version.getSpigotPluginId(), true);
                        }
                    } else {
                        Updater.sendUpdateMessage(sender, version.getResult(), version.getSpigotPluginId());
                    }
                });
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
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
                            sender.sendMessage(ImageFrame.messageNoConsole);
                        } else {
                            Player player = isConsole ? null : (Player) sender;
                            UUID owner = pair.getFirst();
                            boolean isAdmin = isConsole || !owner.equals(player.getUniqueId());
                            if (isAdmin && !sender.hasPermission("imageframe.create.others")) {
                                sender.sendMessage(ImageFrame.messageNoPermission);
                            } else {
                                boolean combined = (args.length == 6 && args[5].equalsIgnoreCase("combined")) || (args.length == 7 && args[6].equalsIgnoreCase("combined"));
                                ItemFrameSelectionManager.SelectedItemFrameResult selection;
                                if (args[3].equalsIgnoreCase("selection")) {
                                    if (isConsole) {
                                        sender.sendMessage(ImageFrame.messageNoConsole);
                                        return true;
                                    }
                                    selection = ImageFrame.itemFrameSelectionManager.getConfirmedSelections(player);
                                    if (selection == null) {
                                        sender.sendMessage(ImageFrame.messageSelectionNoSelection);
                                        return true;
                                    }
                                } else if (args.length == 5 || args.length == 6 || args.length == 7) {
                                    selection = null;
                                } else {
                                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                                    return true;
                                }

                                int width;
                                int height;
                                DitheringType ditheringType;
                                if (selection == null) {
                                    width = Integer.parseInt(args[3]);
                                    height = Integer.parseInt(args[4]);
                                    ditheringType = DitheringType.fromName(args.length > 5 && !args[5].equalsIgnoreCase("combined") ? args[5].toLowerCase() : null);
                                } else {
                                    width = selection.getWidth();
                                    height = selection.getHeight();
                                    ditheringType = DitheringType.fromName(args.length > 4 ? args[4].toLowerCase() : null);
                                }
                                if (width * height > ImageFrame.mapMaxSize) {
                                    sender.sendMessage(ImageFrame.messageOversize.replace("{MaxSize}", ImageFrame.mapMaxSize + ""));
                                    return true;
                                }
                                int limit = isAdmin ? -1 : ImageFrame.getPlayerCreationLimit(player);
                                Set<ImageMap> existingMaps = ImageFrame.imageMapManager.getFromCreator(owner);
                                if (limit >= 0 && existingMaps.size() >= limit) {
                                    sender.sendMessage(ImageFrame.messagePlayerCreationLimitReached.replace("{Limit}", limit + ""));
                                    return true;
                                }
                                if (existingMaps.stream().anyMatch(each -> each.getName().equalsIgnoreCase(name))) {
                                    sender.sendMessage(ImageFrame.messageDuplicateMapName);
                                    return true;
                                }
                                if (!ImageFrame.isURLAllowed(args[2])) {
                                    sender.sendMessage(ImageFrame.messageURLRestricted);
                                    return true;
                                }
                                int takenMaps;
                                if (ImageFrame.requireEmptyMaps && !isConsole) {
                                    if ((takenMaps = MapUtils.removeEmptyMaps(player, width * height, true)) < 0) {
                                        sender.sendMessage(ImageFrame.messageNotEnoughMaps.replace("{Amount}", (width * height) + ""));
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
                                        if (ImageFrame.uploadServiceEnabled && url.equalsIgnoreCase("upload")) {
                                            UUID user = isConsole ? ImageMap.CONSOLE_CREATOR : player.getUniqueId();
                                            PendingUpload pendingUpload = ImageFrame.imageUploadManager.newPendingUpload(user);
                                            Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, () -> sender.sendMessage(ImageFrame.messageUploadLink.replace("{URL}", pendingUpload.getUrl(ImageFrame.uploadServiceDisplayURL, user))), 2);
                                            url = pendingUpload.getFileBlocking().toURI().toURL().toString();
                                        }
                                        if (HTTPRequestUtils.getContentSize(url) > ImageFrame.maxImageFileSize) {
                                            sender.sendMessage(ImageFrame.messageImageOverMaxFileSize.replace("{Size}", ImageFrame.maxImageFileSize + ""));
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
                                        sender.sendMessage(ImageFrame.messageImageMapProcessing);
                                        String finalUrl = url;
                                        String finalImageType = imageType;
                                        creationTask = ImageFrame.imageMapCreationTaskManager.enqueue(owner, name, width, height, () -> {
                                            if (finalImageType.equals(MapUtils.GIF_CONTENT_TYPE) && sender.hasPermission("imageframe.create.animated")) {
                                                return URLAnimatedImageMap.create(ImageFrame.imageMapManager, name, finalUrl, width, height, ditheringType, owner).get();
                                            } else {
                                                return URLStaticImageMap.create(ImageFrame.imageMapManager, name, finalUrl, width, height, ditheringType, owner).get();
                                            }
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
                                                        sender.sendMessage(ImageFrame.messageItemFrameOccupied);
                                                    }
                                                }, ImageFrame.mapItemFormat);
                                            }
                                        }
                                        sender.sendMessage(ImageFrame.messageImageMapCreated);
                                        creationTask.complete(ImageFrame.messageImageMapCreated);
                                    } catch (ImageUploadManager.LinkTimeoutException e) {
                                        sender.sendMessage(ImageFrame.messageUploadExpired);
                                        if (takenMaps > 0 && !isConsole) {
                                            PlayerUtils.giveItem(player, new ItemStack(Material.MAP, takenMaps));
                                        }
                                    } catch (ImageMapCreationTaskManager.EnqueueRejectedException e) {
                                        sender.sendMessage(ImageFrame.messageImageMapAlreadyQueued);
                                        if (takenMaps > 0 && !isConsole) {
                                            PlayerUtils.giveItem(player, new ItemStack(Material.MAP, takenMaps));
                                        }
                                    } catch (Exception e) {
                                        sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                        if (creationTask != null) {
                                            creationTask.complete(ImageFrame.messageUnableToLoadMap);
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
                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                    } catch (Exception e) {
                        sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                        e.printStackTrace();
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
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
                                sender.sendMessage(ImageFrame.messageNoConsole);
                            } else {
                                UUID owner = pair.getFirst();
                                boolean isAdmin = !owner.equals(player.getUniqueId());
                                if (isAdmin && !sender.hasPermission("imageframe.create.others")) {
                                    sender.sendMessage(ImageFrame.messageNoPermission);
                                } else {
                                    List<MapView> mapViews;
                                    int width;
                                    int height;
                                    DitheringType ditheringType;
                                    if ((args.length == 4 || args.length == 5) && args[3].equalsIgnoreCase("selection")) {
                                        ItemFrameSelectionManager.SelectedItemFrameResult selection = ImageFrame.itemFrameSelectionManager.getConfirmedSelections(player);
                                        if (selection == null) {
                                            player.sendMessage(ImageFrame.messageSelectionNoSelection);
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
                                        player.sendMessage(ImageFrame.messageInvalidUsage);
                                        return true;
                                    }
                                    if (mapViews.contains(null)) {
                                        player.sendMessage(ImageFrame.messageSelectionInvalid);
                                        return true;
                                    }
                                    if (mapViews.stream().anyMatch(each -> ImageFrame.imageMapManager.getFromMapView(each) != null) || mapViews.stream().distinct().count() < mapViews.size()) {
                                        player.sendMessage(ImageFrame.messageInvalidOverlayMap);
                                        return true;
                                    }

                                    if (width * height > ImageFrame.mapMaxSize) {
                                        sender.sendMessage(ImageFrame.messageOversize.replace("{MaxSize}", ImageFrame.mapMaxSize + ""));
                                        return true;
                                    }
                                    int limit = isAdmin ? -1 : ImageFrame.getPlayerCreationLimit(player);
                                    Set<ImageMap> existingMaps = ImageFrame.imageMapManager.getFromCreator(owner);
                                    if (limit >= 0 && existingMaps.size() >= limit) {
                                        sender.sendMessage(ImageFrame.messagePlayerCreationLimitReached.replace("{Limit}", limit + ""));
                                        return true;
                                    }
                                    if (existingMaps.stream().anyMatch(each -> each.getName().equalsIgnoreCase(name))) {
                                        sender.sendMessage(ImageFrame.messageDuplicateMapName);
                                        return true;
                                    }
                                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                        ImageMapCreationTask<ImageMap> creationTask = null;
                                        try {
                                            String url = args[2];
                                            if (ImageFrame.uploadServiceEnabled && url.equalsIgnoreCase("upload")) {
                                                UUID user = player.getUniqueId();
                                                PendingUpload pendingUpload = ImageFrame.imageUploadManager.newPendingUpload(user);
                                                Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, () -> sender.sendMessage(ImageFrame.messageUploadLink.replace("{URL}", pendingUpload.getUrl(ImageFrame.uploadServiceDisplayURL, user))), 2);
                                                url = pendingUpload.getFileBlocking().toURI().toURL().toString();
                                            }
                                            if (!ImageFrame.isURLAllowed(url)) {
                                                sender.sendMessage(ImageFrame.messageURLRestricted);
                                                return;
                                            }
                                            if (HTTPRequestUtils.getContentSize(url) > ImageFrame.maxImageFileSize) {
                                                sender.sendMessage(ImageFrame.messageImageOverMaxFileSize.replace("{Size}", ImageFrame.maxImageFileSize + ""));
                                                throw new IOException("Image over max file size");
                                            }
                                            String finalUrl = url;
                                            creationTask = ImageFrame.imageMapCreationTaskManager.enqueue(owner, name, width, height, () -> MinecraftURLOverlayImageMap.create(ImageFrame.imageMapManager, name, finalUrl, mapViews, width, height, ditheringType, player.getUniqueId()).get());
                                            ImageMap imageMap = creationTask.get();
                                            ImageFrame.imageMapManager.addMap(imageMap);
                                            sender.sendMessage(ImageFrame.messageImageMapCreated);
                                            creationTask.complete(ImageFrame.messageImageMapCreated);
                                        } catch (ImageUploadManager.LinkTimeoutException e) {
                                            sender.sendMessage(ImageFrame.messageUploadExpired);
                                        } catch (ImageMapCreationTaskManager.EnqueueRejectedException e) {
                                            sender.sendMessage(ImageFrame.messageImageMapAlreadyQueued);
                                        } catch (Exception e) {
                                            sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                            if (creationTask != null) {
                                                creationTask.complete(ImageFrame.messageUnableToLoadMap);
                                            }
                                            e.printStackTrace();
                                        }
                                    });
                                }
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ImageFrame.messageInvalidUsage);
                        } catch (Exception e) {
                            sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                            e.printStackTrace();
                        }
                    } else {
                        sender.sendMessage(ImageFrame.messageNoConsole);
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
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
                            sender.sendMessage(ImageFrame.messageNoConsole);
                        } else {
                            Player player = isConsole ? null : (Player) sender;
                            UUID owner = pair.getFirst();
                            boolean isAdmin = isConsole || !owner.equals(player.getUniqueId());
                            if (isAdmin && !sender.hasPermission("imageframe.create.others")) {
                                sender.sendMessage(ImageFrame.messageNoPermission);
                            } else {
                                boolean combined = args.length > 3 && args[3].equalsIgnoreCase("combined");
                                ItemFrameSelectionManager.SelectedItemFrameResult selection;
                                if (args.length > 3 && args[3].equalsIgnoreCase("selection")) {
                                    selection = ImageFrame.itemFrameSelectionManager.getConfirmedSelections(player);
                                    if (selection == null) {
                                        sender.sendMessage(ImageFrame.messageSelectionNoSelection);
                                        return true;
                                    }
                                } else {
                                    selection = null;
                                }
                                ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                                if (imageMap == null) {
                                    sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                    return true;
                                }
                                if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.EDIT_CLONE)) {
                                    sender.sendMessage(ImageFrame.messageNoPermission);
                                    return true;
                                }
                                if (selection != null) {
                                    if (imageMap.getWidth() != selection.getWidth() || imageMap.getHeight() != selection.getHeight()) {
                                        sender.sendMessage(ImageFrame.messageSelectionIncorrectSize.replace("{Width}", imageMap.getWidth() + "").replace("{Height}", imageMap.getHeight() + ""));
                                        return true;
                                    }
                                }
                                int limit = isAdmin ? -1 : ImageFrame.getPlayerCreationLimit(player);
                                Set<ImageMap> existingMaps = ImageFrame.imageMapManager.getFromCreator(owner);
                                if (limit >= 0 && existingMaps.size() >= limit) {
                                    sender.sendMessage(ImageFrame.messagePlayerCreationLimitReached.replace("{Limit}", limit + ""));
                                    return true;
                                }
                                if (existingMaps.stream().anyMatch(each -> each.getName().equalsIgnoreCase(name))) {
                                    sender.sendMessage(ImageFrame.messageDuplicateMapName);
                                    return true;
                                }
                                int takenMaps;
                                if (ImageFrame.requireEmptyMaps && !isConsole) {
                                    if ((takenMaps = MapUtils.removeEmptyMaps(player, imageMap.getWidth() * imageMap.getHeight(), true)) < 0) {
                                        sender.sendMessage(ImageFrame.messageNotEnoughMaps.replace("{Amount}", (imageMap.getWidth() * imageMap.getHeight()) + ""));
                                        return true;
                                    }
                                } else {
                                    takenMaps = 0;
                                }
                                Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                    ImageMapCreationTask<ImageMap> creationTask = null;
                                    try {
                                        creationTask = ImageFrame.imageMapCreationTaskManager.enqueue(owner, name, imageMap.getWidth(), imageMap.getHeight(), () -> imageMap.deepClone(name, owner));
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
                                                        sender.sendMessage(ImageFrame.messageItemFrameOccupied);
                                                    }
                                                }, ImageFrame.mapItemFormat);
                                            }
                                        }
                                        sender.sendMessage(ImageFrame.messageImageMapCreated);
                                        creationTask.complete(ImageFrame.messageImageMapCreated);
                                    } catch (ImageMapCreationTaskManager.EnqueueRejectedException e) {
                                        sender.sendMessage(ImageFrame.messageImageMapAlreadyQueued);
                                    } catch (Exception e) {
                                        sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                        if (creationTask != null) {
                                            creationTask.complete(ImageFrame.messageUnableToLoadMap);
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
                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                    } catch (Exception e) {
                        sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                        e.printStackTrace();
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("playback")) {
            if (sender.hasPermission("imageframe.playback")) {
                if (args.length > 2) {
                    try {
                        MutablePair<UUID, String> pair = ImageMapUtils.extractImageMapPlayerPrefixedName(sender, args[1]);
                        boolean isConsole = !(sender instanceof Player);
                        if (pair.getFirst() == null && isConsole) {
                            sender.sendMessage(ImageFrame.messageNoConsole);
                        } else {
                            ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                            if (imageMap == null) {
                                sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                return true;
                            }
                            if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.ADJUST_PLAYBACK)) {
                                sender.sendMessage(ImageFrame.messageNoPermission);
                                return true;
                            }
                            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    if (args[2].equalsIgnoreCase("pause")) {
                                        if (imageMap.requiresAnimationService()) {
                                            imageMap.setAnimationPause(!imageMap.isAnimationPaused());
                                        }
                                        sender.sendMessage(ImageFrame.messageImageMapTogglePaused);
                                    } else if (args[2].equalsIgnoreCase("jumpto") && args.length > 3) {
                                        try {
                                            double seconds = Double.parseDouble(args[3]);
                                            if (imageMap.requiresAnimationService()) {
                                                imageMap.setAnimationPlaybackTime(seconds);
                                            }
                                            sender.sendMessage(ImageFrame.messageImageMapPlaybackJumpTo.replace("{Seconds}", String.valueOf(seconds)));
                                        } catch (NumberFormatException e) {
                                            sender.sendMessage(ImageFrame.messageInvalidUsage);
                                        }
                                    } else {
                                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                    } catch (Exception e) {
                        sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                        e.printStackTrace();
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("select")) {
            if (sender.hasPermission("imageframe.select")) {
                if (args.length == 1) {
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        if (ImageFrame.itemFrameSelectionManager.isInSelection(player)) {
                            ImageFrame.itemFrameSelectionManager.setInSelection(player, false);
                            sender.sendMessage(ImageFrame.messageSelectionClear);
                        } else {
                            ImageFrame.itemFrameSelectionManager.setInSelection(player, true);
                            sender.sendMessage(ImageFrame.messageSelectionBegin);
                        }
                    } else {
                        sender.sendMessage(ImageFrame.messageNoConsole);
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
                            sender.sendMessage(ImageFrame.messageInvalidUsage);
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
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
                                    sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                } else if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.MARKER)) {
                                    sender.sendMessage(ImageFrame.messageNoPermission);
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
                                                player.sendMessage(ImageFrame.messageMarkersNotRenderOnFrameWarning);
                                            }
                                            player.sendMessage(ImageFrame.messageMarkersAddBegin.replace("{Name}", imageMap.getName()));
                                        } else {
                                            player.sendMessage(ImageFrame.messageMarkersDuplicateName);
                                        }
                                    } catch (IllegalArgumentException e) {
                                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                                    }
                                }
                            } else {
                                sender.sendMessage(ImageFrame.messageInvalidUsage);
                            }
                        } else if (args[1].equalsIgnoreCase("remove")) {
                            if (args.length > 3) {
                                ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), args[2]);
                                if (imageMap == null) {
                                    sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                } else {
                                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                        for (Map<String, MapCursor> map : imageMap.getMapMarkers()) {
                                            if (map.remove(args[3]) != null) {
                                                try {
                                                    sender.sendMessage(ImageFrame.messageMarkersRemove);
                                                    Bukkit.getPluginManager().callEvent(new ImageMapUpdatedEvent(imageMap));
                                                    imageMap.send(imageMap.getViewers());
                                                    imageMap.save();
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                                return;
                                            }
                                        }
                                        sender.sendMessage(ImageFrame.messageMarkersNotAMarker);
                                    });
                                }
                            } else {
                                sender.sendMessage(ImageFrame.messageInvalidUsage);
                            }
                        } else if (args[1].equalsIgnoreCase("clear")) {
                            if (args.length > 2) {
                                ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), args[2]);
                                if (imageMap == null) {
                                    sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                } else {
                                    Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                        try {
                                            imageMap.getMapMarkers().forEach(each -> each.clear());
                                            sender.sendMessage(ImageFrame.messageMarkersClear);
                                            Bukkit.getPluginManager().callEvent(new ImageMapUpdatedEvent(imageMap));
                                            imageMap.send(imageMap.getViewers());
                                            imageMap.save();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    });
                                }
                            } else {
                                sender.sendMessage(ImageFrame.messageInvalidUsage);
                            }
                        } else if (args[1].equalsIgnoreCase("cancel")) {
                            MapMarkerEditManager.MapMarkerEditData editData = ImageFrame.mapMarkerEditManager.leaveActiveEditing(player);
                            sender.sendMessage(ImageFrame.messageMarkersCancel);
                            if (editData != null) {
                                Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> editData.getImageMap().send(editData.getImageMap().getViewers()));
                            }
                        } else {
                            sender.sendMessage(ImageFrame.messageInvalidUsage);
                        }
                    } else {
                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageNoConsole);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
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
                            sender.sendMessage(ImageFrame.messageNoConsole);
                            return;
                        }
                        MapView mapView = MapUtils.getPlayerMapView((Player) sender);
                        if (mapView == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                            return;
                        }
                        imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                    }
                    if (imageMap == null) {
                        sender.sendMessage(ImageFrame.messageNotAnImageMap);
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
                                        sender.sendMessage(ImageFrame.messageURLRestricted);
                                        return;
                                    }
                                    if (imageType.equals(MapUtils.GIF_CONTENT_TYPE) == urlImageMap.requiresAnimationService()) {
                                        String oldUrl = urlImageMap.getUrl();
                                        if (url != null) {
                                            urlImageMap.setUrl(url);
                                        }
                                        if (ImageFrame.uploadServiceEnabled && urlImageMap.getUrl().equalsIgnoreCase("upload")) {
                                            UUID user = !(sender instanceof Player) ? ImageMap.CONSOLE_CREATOR : ((Player) sender).getUniqueId();
                                            PendingUpload pendingUpload = ImageFrame.imageUploadManager.newPendingUpload(user);
                                            Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, () -> sender.sendMessage(ImageFrame.messageUploadLink.replace("{URL}", pendingUpload.getUrl(ImageFrame.uploadServiceDisplayURL, user))), 2);
                                            String newUrl = pendingUpload.getFileBlocking().toURI().toURL().toString();
                                            if (HTTPRequestUtils.getContentSize(newUrl) > ImageFrame.maxImageFileSize) {
                                                sender.sendMessage(ImageFrame.messageImageOverMaxFileSize.replace("{Size}", ImageFrame.maxImageFileSize + ""));
                                                throw new IOException("Image over max file size");
                                            }
                                            urlImageMap.setUrl(newUrl);
                                        }
                                        if (ditheringType != null) {
                                            urlImageMap.setDitheringType(ditheringType);
                                        }
                                        try {
                                            imageMap.update();
                                            sender.sendMessage(ImageFrame.messageImageMapRefreshed);
                                        } catch (Throwable e) {
                                            urlImageMap.setUrl(oldUrl);
                                            sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                            e.printStackTrace();
                                        }
                                    } else {
                                        sender.sendMessage(ImageFrame.messageUnableToChangeImageType);
                                    }
                                } else {
                                    if (ditheringType != null) {
                                        imageMap.setDitheringType(ditheringType);
                                    }
                                    imageMap.update();
                                    sender.sendMessage(ImageFrame.messageImageMapRefreshed);
                                }
                            } catch (ImageUploadManager.LinkTimeoutException e) {
                                sender.sendMessage(ImageFrame.messageUploadExpired);
                            } catch (Exception e) {
                                sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                e.printStackTrace();
                            }
                        } else {
                            sender.sendMessage(ImageFrame.messageNoPermission);
                        }
                    }
                });
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("rename")) {
            if (sender.hasPermission("imageframe.rename")) {
                if (args.length > 2) {
                    ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                    if (imageMap == null) {
                        sender.sendMessage(ImageFrame.messageNotAnImageMap);
                    } else if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.EDIT)) {
                        sender.sendMessage(ImageFrame.messageNoPermission);
                    } else {
                        String newName = args[2];
                        if (ImageFrame.imageMapManager.getFromCreator(imageMap.getCreator(), newName) == null) {
                            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    imageMap.rename(newName);
                                    sender.sendMessage(ImageFrame.messageImageMapRenamed);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        } else {
                            sender.sendMessage(ImageFrame.messageDuplicateMapName);
                        }
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("info")) {
            if (sender.hasPermission("imageframe.info")) {
                MapView mapView;
                if (args.length > 1) {
                    ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                    if (imageMap == null) {
                        sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        return true;
                    }
                    mapView = imageMap.getMapViews().get(0);
                } else if (sender instanceof Player) {
                    mapView = MapUtils.getPlayerMapView((Player) sender);
                } else {
                    mapView = null;
                }
                if (mapView == null) {
                    sender.sendMessage(ImageFrame.messageNotAnImageMap);
                } else {
                    ImageMap imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                    if (imageMap == null) {
                        sender.sendMessage(ImageFrame.messageNotAnImageMap);
                    } else {
                        for (String line : ImageFrame.messageURLImageMapInfo) {
                            String access = imageMap.getAccessControl().getPermissions().entrySet().stream().map(each -> {
                                UUID uuid = each.getKey();
                                String playerName = uuid.equals(ImageMapAccessControl.EVERYONE) ? "*" : Bukkit.getOfflinePlayer(uuid).getName();
                                String permission = ImageFrame.messageAccessTypes.get(each.getValue());
                                if (playerName == null) {
                                    return each.getKey().toString() + " - " + permission;
                                }
                                return playerName + " - " + permission;
                            }).sorted().collect(Collectors.joining(", ", "[", "]"));
                            String message = ChatColorUtils.translateAlternateColorCodes('&', line
                                    .replace("{ImageID}", imageMap.getImageIndex() + "")
                                    .replace("{Name}", imageMap.getName())
                                    .replace("{Width}", imageMap.getWidth() + "")
                                    .replace("{Height}", imageMap.getHeight() + "")
                                    .replace("{DitheringType}", imageMap.getDitheringType().getName())
                                    .replace("{CreatorName}", imageMap.getCreatorName())
                                    .replace("{Access}", access)
                                    .replace("{CreatorUUID}", imageMap.getCreator().toString())
                                    .replace("{TimeCreated}", ImageFrame.dateFormat.format(new Date(imageMap.getCreationTime())))
                                    .replace("{Markers}", imageMap.getMapMarkers().stream().flatMap(each -> each.keySet().stream()).collect(Collectors.joining(", ", "[", "]"))));
                            if (imageMap instanceof URLImageMap) {
                                message = message.replace("{URL}", ((URLImageMap) imageMap).getUrl());
                            } else {
                                message = message.replace("{URL}", "-");
                            }
                            sender.sendMessage(message);
                        }
                    }
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
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
                        sender.sendMessage(ImageFrame.messageNoConsole);
                    } else {
                        if (!player.equals(sender) && !sender.hasPermission("imageframe.list.others")) {
                            sender.sendMessage(ImageFrame.messageNoPermission);
                        } else {
                            String prefix = ImageFrame.messageMapLookup
                                    .replace("{CreatorName}", player.getName() == null ? "" : player.getName())
                                    .replace("{CreatorUUID}", player.getUniqueId().toString());
                            String message = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), Comparator.comparing(each -> each.getCreationTime()))
                                    .stream()
                                    .map(each -> each.getName())
                                    .collect(Collectors.joining(", ", prefix + " [", "]"));
                            sender.sendMessage(message);
                        }
                    }
                });
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
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
                                    sender.sendMessage(ImageFrame.messageNoConsole);
                                    return;
                                }
                                MapView mapView = MapUtils.getPlayerMapView((Player) sender);
                                if (mapView == null) {
                                    sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                    return;
                                }
                                imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                            }
                            if (imageMap == null) {
                                sender.sendMessage(ImageFrame.messageNotAnImageMap);
                            } else if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.ALL)) {
                                sender.sendMessage(ImageFrame.messageNoPermission);
                            } else {
                                ImageFrame.imageMapManager.deleteMap(imageMap.getImageIndex());
                                sender.sendMessage(ImageFrame.messageImageMapDeleted);
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
                            sender.sendMessage(ImageFrame.messageInvalidUsage);
                        }
                    });
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
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
                                sender.sendMessage(ImageFrame.messageNoPermission);
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
                            sender.sendMessage(ImageFrame.messageImageMapPlayerPurge
                                    .replace("{Amount}", String.valueOf(names.size()))
                                    .replace("{CreatorName}", playerName == null ? ImageMap.UNKNOWN_CREATOR_NAME : playerName)
                                    .replace("{CreatorUUID}", uuid.toString())
                                    .replace("{ImageMapNames}", String.join(", ", names)));
                        }
                    });
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
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
                                    sender.sendMessage(ImageFrame.messageNoConsole);
                                    return;
                                }
                                MapView mapView = MapUtils.getPlayerMapView((Player) sender);
                                if (mapView == null) {
                                    sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                    return;
                                }
                                imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                            }
                            if (imageMap == null) {
                                sender.sendMessage(ImageFrame.messageNotAnImageMap);
                            } else if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.ALL)) {
                                sender.sendMessage(ImageFrame.messageNoPermission);
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
                                    sender.sendMessage(ImageFrame.messageAccessUpdated
                                            .replace("{PlayerName}", targetPlayer)
                                            .replace("{PlayerUUID}", uuid.toString())
                                            .replace("{Permission}", newPermission == null ? ImageFrame.messageAccessNoneType : ImageFrame.messageAccessTypes.get(newPermission)));
                                } catch (IllegalArgumentException e) {
                                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                                } catch (Exception e) {
                                    sender.sendMessage(ImageFrame.messageUnknownError);
                                }
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ImageFrame.messageInvalidUsage);
                        }
                    });
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("get")) {
            if (sender.hasPermission("imageframe.get")) {
                Player senderPlayer = sender instanceof Player ? (Player) sender : null;
                if (args.length > 1) {
                    try {
                        boolean combined = args.length > 2 && args[2].equalsIgnoreCase("combined");
                        ItemFrameSelectionManager.SelectedItemFrameResult selection;
                        if (args.length > 2 && args[2].equalsIgnoreCase("selection") && senderPlayer != null) {
                            selection = ImageFrame.itemFrameSelectionManager.getConfirmedSelections(senderPlayer);
                            if (selection == null) {
                                sender.sendMessage(ImageFrame.messageSelectionNoSelection);
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
                                    sender.sendMessage(ImageFrame.messagePlayerNotFound);
                                    return true;
                                }
                            } else {
                                if (sender instanceof Player) {
                                    player = (Player) sender;
                                } else {
                                    sender.sendMessage(ImageFrame.messageNoConsole);
                                    return true;
                                }
                            }
                        } else {
                            if (sender instanceof Player) {
                                player = (Player) sender;
                            } else {
                                sender.sendMessage(ImageFrame.messageNoConsole);
                                return true;
                            }
                        }

                        ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, args[1]);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        } else if (!ImageFrame.hasImageMapPermission(imageMap, sender, ImageMapAccessPermissionType.GET)) {
                            sender.sendMessage(ImageFrame.messageNoPermission);
                        } else {
                            if (selection != null) {
                                if (imageMap.getWidth() != selection.getWidth() || imageMap.getHeight() != selection.getHeight()) {
                                    sender.sendMessage(ImageFrame.messageSelectionIncorrectSize.replace("{Width}", imageMap.getWidth() + "").replace("{Height}", imageMap.getHeight() + ""));
                                    return true;
                                }
                            }
                            if (ImageFrame.requireEmptyMaps && senderPlayer != null) {
                                if (MapUtils.removeEmptyMaps(senderPlayer, imageMap.getMapViews().size(), true) < 0) {
                                    sender.sendMessage(ImageFrame.messageNotEnoughMaps.replace("{Amount}", imageMap.getMapViews().size() + ""));
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
                                        sender.sendMessage(ImageFrame.messageItemFrameOccupied);
                                    }
                                }, ImageFrame.mapItemFormat);
                            }
                            sender.sendMessage(ImageFrame.messageImageMapCreated);
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("admindelete")) {
            if (sender.hasPermission("imageframe.admindelete")) {
                if (args.length > 1) {
                    try {
                        int imageId = Integer.parseInt(args[1]);
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(imageId);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        } else {
                            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                ImageFrame.imageMapManager.deleteMap(imageMap.getImageIndex());
                                sender.sendMessage(ImageFrame.messageImageMapDeleted);
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
                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("adminsetcreator")) {
            if (sender.hasPermission("imageframe.adminsetcreator")) {
                if (args.length > 2) {
                    try {
                        int imageId = Integer.parseInt(args[1]);
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(imageId);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        } else {
                            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    OfflinePlayer player = Bukkit.getOfflinePlayer(args[2]);
                                    imageMap.changeCreator(player.getUniqueId());
                                    sender.sendMessage(ImageFrame.messageSetCreator
                                            .replace("{ImageID}", imageMap.getImageIndex() + "")
                                            .replace("{CreatorName}", imageMap.getCreatorName())
                                            .replace("{CreatorUUID}", imageMap.getCreator().toString()));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
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
                                    sender.sendMessage(ChatColor.YELLOW + "Migration has begun, see console for progress, completion and errors");
                                    migration.migrate(((Player) sender).getUniqueId());
                                } else {
                                    sender.sendMessage(ImageFrame.messageNoConsole);
                                }
                            } else {
                                sender.sendMessage(ChatColor.YELLOW + "Migration has begun, see console for progress, completion and errors");
                                migration.migrate();
                            }
                        } else {
                            sender.sendMessage(args[1] + " is not a supported plugin for migration");
                        }
                    });
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("preference")) {
            if (sender.hasPermission("imageframe.preference")) {
                if (args.length > 2) {
                    IFPlayerPreference<?> preference = IFPlayerPreference.valueOf(args[1].toUpperCase());
                    if (preference == null) {
                        sender.sendMessage(ImageFrame.messageInvalidUsage);
                    } else {
                        IFPlayerPreference.StringDeserializerResult<?> result = preference.getStringDeserializer().apply(args[2]);
                        if (result.isAccepted()) {
                            Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
                                UUID player;
                                if (args.length > 3) {
                                    if (sender.hasPermission("imageframe.preference.others")) {
                                        player = Bukkit.getOfflinePlayer(args[3]).getUniqueId();
                                    } else {
                                        sender.sendMessage(ImageFrame.messageNoPermission);
                                        return;
                                    }
                                } else if (sender instanceof Player) {
                                    player = ((Player) sender).getUniqueId();
                                } else {
                                    sender.sendMessage(ImageFrame.messageNoConsole);
                                    return;
                                }
                                Object value = result.getValue();
                                IFPlayer ifPlayer = ImageFrame.ifPlayerManager.getIFPlayer(player);
                                ifPlayer.setPreference(preference, value);
                                try {
                                    ifPlayer.save();
                                    sender.sendMessage(ImageFrame.messagePreferencesUpdate.replace("{Preference}", ImageFrame.getPreferenceTranslatedName(preference)).replace("{Value}", ImageFrame.getPreferenceTranslatedValue(value)));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                        } else {
                            sender.sendMessage(ImageFrame.messageInvalidUsage);
                        }
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("giveinvisibleframe")) {
            if (args.length > 1) {
                if (sender.hasPermission("imageframe.giveinvisibleframe")) {
                    Player target;
                    if (args.length > 3) {
                        target = Bukkit.getPlayer(args[3]);
                        if (target == null) {
                            sender.sendMessage(ImageFrame.messagePlayerNotFound);
                            return true;
                        }
                    } else if (!(sender instanceof Player)) {
                        sender.sendMessage(ImageFrame.messageNoConsole);
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
                    sender.sendMessage(ImageFrame.messageGivenInvisibleFrame.replace("{Amount}", String.valueOf(amount)).replace("{Player}", target.getName()));
                } else {
                    sender.sendMessage(ImageFrame.messageNoPermission);
                }
            } else {
                sender.sendMessage(ImageFrame.messageInvalidUsage);
            }
            return true;
        }

        sender.sendMessage(ChatColorUtils.translateAlternateColorCodes('&', Bukkit.spigot().getConfig().getString("messages.unknown-command")));
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
                return tab;
            case 1:
                if (sender.hasPermission("imageframe.reload")) {
                    if ("reload".startsWith(args[0].toLowerCase())) {
                        tab.add("reload");
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
                        if (ImageFrame.uploadServiceEnabled && "upload".startsWith(args[1].toLowerCase())) {
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
                return tab;
            case 3:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        tab.add("<url>");
                        if (ImageFrame.uploadServiceEnabled && "upload".startsWith(args[2].toLowerCase())) {
                            tab.add("upload");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.overlay")) {
                    if ("overlay".equalsIgnoreCase(args[0])) {
                        tab.add("<url>");
                        if (ImageFrame.uploadServiceEnabled && "upload".startsWith(args[2].toLowerCase())) {
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
                            if (ImageFrame.uploadServiceEnabled && "upload".startsWith(args[2].toLowerCase())) {
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
                        if ("combined".startsWith(args[2].toLowerCase())) {
                            tab.add("combined");
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
                        if ("combined".startsWith(args[3].toLowerCase())) {
                            tab.add("combined");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.playback")) {
                    if ("playback".equalsIgnoreCase(args[0])) {
                        if ("jumpto".equalsIgnoreCase(args[2])) {
                            tab.add("0.0");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.get")) {
                    if ("get".equalsIgnoreCase(args[0])) {
                        if (args[2].equalsIgnoreCase("combined")) {
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
                return tab;
            case 6:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        if (!args[3].equalsIgnoreCase("selection")) {
                            if ("combined".startsWith(args[5].toLowerCase())) {
                                tab.add("combined");
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
                return tab;
            case 7:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        if (!args[3].equalsIgnoreCase("selection") && !args[5].equalsIgnoreCase("combined")) {
                            if ("combined".startsWith(args[6].toLowerCase())) {
                                tab.add("combined");
                            }
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
                return tab;
        }
    }

}
