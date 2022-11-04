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

import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.ItemFrameSelectionManager;
import com.loohp.imageframe.objectholders.MapMarkerEditManager;
import com.loohp.imageframe.objectholders.URLAnimatedImageMap;
import com.loohp.imageframe.objectholders.URLImageMap;
import com.loohp.imageframe.objectholders.URLStaticImageMap;
import com.loohp.imageframe.updater.Updater;
import com.loohp.imageframe.utils.ChatColorUtils;
import com.loohp.imageframe.utils.MapUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Commands implements CommandExecutor, TabCompleter {

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
                Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
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
                if (sender instanceof Player) {
                    if (args.length == 4 || args.length == 5) {
                        try {
                            Player player = (Player) sender;

                            ItemFrameSelectionManager.SelectedItemFrameResult selection;
                            if (args[3].equalsIgnoreCase("selection")) {
                                selection = ImageFrame.itemFrameSelectionManager.getPlayerSelection(player);
                                if (selection == null) {
                                    player.sendMessage(ImageFrame.messageSelectionNoSelection);
                                    return true;
                                }
                            } else {
                                selection = null;
                            }

                            int width;
                            int height;
                            if (selection == null) {
                                width = Integer.parseInt(args[3]);
                                height = Integer.parseInt(args[4]);
                            } else {
                                width = selection.getWidth();
                                height = selection.getHeight();
                            }
                            if (width * height > ImageFrame.mapMaxSize) {
                                sender.sendMessage(ImageFrame.messageOversize.replace("{MaxSize}", ImageFrame.mapMaxSize + ""));
                                return true;
                            }
                            int limit = ImageFrame.getPlayerCreationLimit(player);
                            Set<ImageMap> existingMaps = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId());
                            if (limit >= 0 && existingMaps.size() >= limit) {
                                sender.sendMessage(ImageFrame.messagePlayerCreationLimitReached.replace("{Limit}", limit + ""));
                                return true;
                            }
                            if (existingMaps.stream().anyMatch(each -> each.getName().equalsIgnoreCase(args[1]))) {
                                sender.sendMessage(ImageFrame.messageDuplicateMapName);
                                return true;
                            }
                            if (!ImageFrame.isURLAllowed(args[2])) {
                                sender.sendMessage(ImageFrame.messageURLRestricted);
                                return true;
                            }
                            int takenMaps;
                            if (ImageFrame.requireEmptyMaps) {
                                if ((takenMaps = MapUtils.removeEmptyMaps(player, width * height, true)) < 0) {
                                    sender.sendMessage(ImageFrame.messageNotEnoughMaps.replace("{Amount}", (width * height) + ""));
                                    return true;
                                }
                            } else {
                                takenMaps = 0;
                            }
                            Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    ImageMap imageMap;
                                    if (player.hasPermission("imageframe.create.animated") && args[2].toLowerCase().endsWith(".gif")) {
                                        imageMap = URLAnimatedImageMap.create(ImageFrame.imageMapManager, args[1], args[2], width, height, player.getUniqueId());
                                    } else {
                                        imageMap = URLStaticImageMap.create(ImageFrame.imageMapManager, args[1], args[2], width, height, player.getUniqueId());
                                    }
                                    ImageFrame.imageMapManager.addMap(imageMap);
                                    if (selection == null) {
                                        imageMap.giveMaps(Collections.singleton((Player) sender), ImageFrame.mapItemFormat);
                                    } else {
                                        imageMap.fillItemFrames(selection.getItemFrames(), selection.getRotation(), (frame, item) -> {
                                            Player p = (Player) sender;
                                            HashMap<Integer, ItemStack> result = p.getInventory().addItem(item);
                                            for (ItemStack stack : result.values()) {
                                                p.getWorld().dropItem(p.getEyeLocation(), stack).setVelocity(new Vector(0, 0, 0));
                                            }
                                        }, ImageFrame.mapItemFormat);
                                    }
                                    sender.sendMessage(ImageFrame.messageImageMapCreated);
                                } catch (Exception e) {
                                    sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                    e.printStackTrace();
                                    if (takenMaps > 0) {
                                        Bukkit.getScheduler().runTask(ImageFrame.plugin, () -> {
                                            Player p = (Player) sender;
                                            HashMap<Integer, ItemStack> result = p.getInventory().addItem(new ItemStack(Material.MAP, takenMaps));
                                            for (ItemStack stack : result.values()) {
                                                p.getWorld().dropItem(p.getEyeLocation(), stack).setVelocity(new Vector(0, 0, 0));
                                            }
                                        });
                                    }
                                }
                            });
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
                    sender.sendMessage(ImageFrame.messageNoConsole);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("clone")) {
            if (sender.hasPermission("imageframe.clone")) {
                if (sender instanceof Player) {
                    if (args.length == 3 || args.length == 4) {
                        try {
                            Player player = (Player) sender;

                            ItemFrameSelectionManager.SelectedItemFrameResult selection;
                            if (args.length > 3 && args[3].equalsIgnoreCase("selection")) {
                                selection = ImageFrame.itemFrameSelectionManager.getPlayerSelection(player);
                                if (selection == null) {
                                    player.sendMessage(ImageFrame.messageSelectionNoSelection);
                                    return true;
                                }
                            } else {
                                selection = null;
                            }
                            ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), args[1]);
                            if (imageMap == null) {
                                sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                return true;
                            }
                            int limit = ImageFrame.getPlayerCreationLimit(player);
                            Set<ImageMap> existingMaps = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId());
                            if (limit >= 0 && existingMaps.size() >= limit) {
                                sender.sendMessage(ImageFrame.messagePlayerCreationLimitReached.replace("{Limit}", limit + ""));
                                return true;
                            }
                            if (existingMaps.stream().anyMatch(each -> each.getName().equalsIgnoreCase(args[2]))) {
                                sender.sendMessage(ImageFrame.messageDuplicateMapName);
                                return true;
                            }
                            int takenMaps;
                            if (ImageFrame.requireEmptyMaps) {
                                if ((takenMaps = MapUtils.removeEmptyMaps(player, imageMap.getWidth() * imageMap.getHeight(), true)) < 0) {
                                    sender.sendMessage(ImageFrame.messageNotEnoughMaps.replace("{Amount}", (imageMap.getWidth() * imageMap.getHeight()) + ""));
                                    return true;
                                }
                            } else {
                                takenMaps = 0;
                            }
                            Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    ImageMap newImageMap = imageMap.deepClone(args[2], player.getUniqueId());
                                    ImageFrame.imageMapManager.addMap(newImageMap);
                                    if (selection == null) {
                                        newImageMap.giveMaps(Collections.singleton((Player) sender), ImageFrame.mapItemFormat);
                                    } else {
                                        newImageMap.fillItemFrames(selection.getItemFrames(), selection.getRotation(), (frame, item) -> {
                                            Player p = (Player) sender;
                                            HashMap<Integer, ItemStack> result = p.getInventory().addItem(item);
                                            for (ItemStack stack : result.values()) {
                                                p.getWorld().dropItem(p.getEyeLocation(), stack).setVelocity(new Vector(0, 0, 0));
                                            }
                                        }, ImageFrame.mapItemFormat);
                                    }
                                    sender.sendMessage(ImageFrame.messageImageMapCreated);
                                } catch (Exception e) {
                                    sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                    e.printStackTrace();
                                    if (takenMaps > 0) {
                                        Bukkit.getScheduler().runTask(ImageFrame.plugin, () -> {
                                            Player p = (Player) sender;
                                            HashMap<Integer, ItemStack> result = p.getInventory().addItem(new ItemStack(Material.MAP, takenMaps));
                                            for (ItemStack stack : result.values()) {
                                                p.getWorld().dropItem(p.getEyeLocation(), stack).setVelocity(new Vector(0, 0, 0));
                                            }
                                        });
                                    }
                                }
                            });
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
                    sender.sendMessage(ImageFrame.messageNoConsole);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("select")) {
            if (sender.hasPermission("imageframe.select")) {
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
                                ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), args[2]);
                                if (imageMap == null) {
                                    sender.sendMessage(ImageFrame.messageNotAnImageMap);
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
                                    Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
                                        for (Map<String, MapCursor> map : imageMap.getMapMarkers()) {
                                            if (map.remove(args[3]) != null) {
                                                try {
                                                    sender.sendMessage(ImageFrame.messageMarkersRemove);
                                                    imageMap.save();
                                                    imageMap.send(imageMap.getViewers());
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
                                    Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
                                        try {
                                            imageMap.getMapMarkers().forEach(each -> each.clear());
                                            sender.sendMessage(ImageFrame.messageMarkersClear);
                                            imageMap.save();
                                            imageMap.send(imageMap.getViewers());
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
                                Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> editData.getImageMap().send(editData.getImageMap().getViewers()));
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
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    MapView mapView = MapUtils.getPlayerMapView(player);
                    if (mapView == null) {
                        sender.sendMessage(ImageFrame.messageNotAnImageMap);
                    } else {
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        } else {
                            try {
                                Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
                                    try {
                                        if (args.length > 1 && imageMap instanceof URLImageMap) {
                                            URLImageMap urlImageMap = (URLImageMap) imageMap;
                                            String url = urlImageMap.getUrl();
                                            urlImageMap.setUrl(args[1]);
                                            try {
                                                imageMap.update();
                                                sender.sendMessage(ImageFrame.messageImageMapRefreshed);
                                            } catch (Throwable e) {
                                                urlImageMap.setUrl(url);
                                                sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                                e.printStackTrace();
                                            }
                                        } else {
                                            imageMap.update();
                                            sender.sendMessage(ImageFrame.messageImageMapRefreshed);
                                        }
                                    } catch (Exception e) {
                                        sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                        e.printStackTrace();
                                    }
                                });
                            } catch (Exception e) {
                                sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageNoConsole);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("rename")) {
            if (sender.hasPermission("imageframe.rename")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (args.length > 2) {
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), args[1]);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        } else {
                            String newName = args[2];
                            if (ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), newName) == null) {
                                Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
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
                    sender.sendMessage(ImageFrame.messageNoConsole);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("info")) {
            if (sender.hasPermission("imageframe.info")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    MapView mapView;
                    if (args.length > 1) {
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), args[1]);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                            return true;
                        }
                        mapView = imageMap.getMapViews().get(0);
                    } else {
                        mapView = MapUtils.getPlayerMapView(player);
                    }
                    if (mapView == null) {
                        sender.sendMessage(ImageFrame.messageNotAnImageMap);
                    } else {
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        } else if (imageMap instanceof URLImageMap) {
                            for (String line : ImageFrame.messageURLImageMapInfo) {
                                sender.sendMessage(ChatColorUtils.translateAlternateColorCodes('&', line
                                        .replace("{ImageID}", imageMap.getImageIndex() + "")
                                        .replace("{Name}", imageMap.getName())
                                        .replace("{Width}", imageMap.getWidth() + "")
                                        .replace("{Height}", imageMap.getHeight() + "")
                                        .replace("{CreatorName}", imageMap.getCreatorName())
                                        .replace("{CreatorUUID}", imageMap.getCreator().toString())
                                        .replace("{TimeCreated}", ImageFrame.dateFormat.format(new Date(imageMap.getCreationTime())))
                                        .replace("{Markers}", imageMap.getMapMarkers().stream().flatMap(each -> each.keySet().stream()).collect(Collectors.joining(", ", "[", "]")))
                                        .replace("{URL}", ((URLImageMap) imageMap).getUrl())));
                            }
                        }
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageNoConsole);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("list")) {
            if (sender.hasPermission("imageframe.list")) {
                Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
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
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (args.length > 1) {
                        try {
                            String name = args[1];
                            ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), name);
                            if (imageMap == null) {
                                sender.sendMessage(ImageFrame.messageNotAnImageMap);
                            } else {
                                ImageFrame.imageMapManager.deleteMap(imageMap.getImageIndex());
                                sender.sendMessage(ImageFrame.messageImageMapDeleted);
                            }
                        } catch (NumberFormatException e) {
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
        } else if (args[0].equalsIgnoreCase("get")) {
            if (sender.hasPermission("imageframe.get")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (args.length > 1) {
                        try {
                            ItemFrameSelectionManager.SelectedItemFrameResult selection;
                            if (args.length > 2 && args[2].equalsIgnoreCase("selection")) {
                                selection = ImageFrame.itemFrameSelectionManager.getPlayerSelection(player);
                                if (selection == null) {
                                    player.sendMessage(ImageFrame.messageSelectionNoSelection);
                                    return true;
                                }
                            } else {
                                selection = null;
                            }

                            String name = args[1];
                            ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), name);
                            if (imageMap == null) {
                                sender.sendMessage(ImageFrame.messageNotAnImageMap);
                            } else {
                                if (selection != null) {
                                    if (imageMap.getWidth() != selection.getWidth() || imageMap.getHeight() != selection.getHeight()) {
                                        sender.sendMessage(ImageFrame.messageSelectionIncorrectSize.replace("{Width}", imageMap.getWidth() + "").replace("{Height}", imageMap.getHeight() + ""));
                                        return true;
                                    }
                                }
                                if (ImageFrame.requireEmptyMaps) {
                                    if (MapUtils.removeEmptyMaps(player, imageMap.getMapViews().size(), true) < 0) {
                                        sender.sendMessage(ImageFrame.messageNotEnoughMaps.replace("{Amount}", imageMap.getMapViews().size() + ""));
                                        return true;
                                    }
                                }
                                if (selection == null) {
                                    imageMap.giveMaps(Collections.singleton(player), ImageFrame.mapItemFormat);
                                } else {
                                    imageMap.fillItemFrames(selection.getItemFrames(), selection.getRotation(), (frame, item) -> {
                                        HashMap<Integer, ItemStack> result = player.getInventory().addItem(item);
                                        for (ItemStack stack : result.values()) {
                                            player.getWorld().dropItem(player.getEyeLocation(), stack).setVelocity(new Vector(0, 0, 0));
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
                    sender.sendMessage(ImageFrame.messageNoConsole);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("adminrename")) {
            if (sender.hasPermission("imageframe.adminrename")) {
                if (args.length > 2) {
                    try {
                        int imageId = Integer.parseInt(args[1]);
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(imageId);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        } else {
                            String newName = args[2];
                            if (ImageFrame.imageMapManager.getFromCreator(imageMap.getCreator(), newName) == null) {
                                Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
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
        } else if (args[0].equalsIgnoreCase("adminclone")) {
            if (sender.hasPermission("imageframe.adminclone")) {
                if (sender instanceof Player) {
                    if (args.length == 3 || args.length == 4) {
                        try {
                            Player player = (Player) sender;

                            ItemFrameSelectionManager.SelectedItemFrameResult selection;
                            if (args[3].equalsIgnoreCase("selection")) {
                                selection = ImageFrame.itemFrameSelectionManager.getPlayerSelection(player);
                                if (selection == null) {
                                    player.sendMessage(ImageFrame.messageSelectionNoSelection);
                                    return true;
                                }
                            } else {
                                selection = null;
                            }
                            ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(Integer.parseInt(args[1]));
                            if (imageMap == null) {
                                sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                return true;
                            }
                            int limit = ImageFrame.getPlayerCreationLimit(player);
                            Set<ImageMap> existingMaps = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId());
                            if (limit >= 0 && existingMaps.size() >= limit) {
                                sender.sendMessage(ImageFrame.messagePlayerCreationLimitReached.replace("{Limit}", limit + ""));
                                return true;
                            }
                            if (existingMaps.stream().anyMatch(each -> each.getName().equalsIgnoreCase(args[2]))) {
                                sender.sendMessage(ImageFrame.messageDuplicateMapName);
                                return true;
                            }
                            int takenMaps;
                            if (ImageFrame.requireEmptyMaps) {
                                if ((takenMaps = MapUtils.removeEmptyMaps(player, imageMap.getWidth() * imageMap.getHeight(), true)) < 0) {
                                    sender.sendMessage(ImageFrame.messageNotEnoughMaps.replace("{Amount}", (imageMap.getWidth() * imageMap.getHeight()) + ""));
                                    return true;
                                }
                            } else {
                                takenMaps = 0;
                            }
                            Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    ImageMap newImageMap = imageMap.deepClone(args[2], player.getUniqueId());
                                    ImageFrame.imageMapManager.addMap(newImageMap);
                                    if (selection == null) {
                                        newImageMap.giveMaps(Collections.singleton((Player) sender), ImageFrame.mapItemFormat);
                                    } else {
                                        newImageMap.fillItemFrames(selection.getItemFrames(), selection.getRotation(), (frame, item) -> {
                                            Player p = (Player) sender;
                                            HashMap<Integer, ItemStack> result = p.getInventory().addItem(item);
                                            for (ItemStack stack : result.values()) {
                                                p.getWorld().dropItem(p.getEyeLocation(), stack).setVelocity(new Vector(0, 0, 0));
                                            }
                                        }, ImageFrame.mapItemFormat);
                                    }
                                    sender.sendMessage(ImageFrame.messageImageMapCreated);
                                } catch (Exception e) {
                                    sender.sendMessage(ImageFrame.messageUnableToLoadMap);
                                    e.printStackTrace();
                                    if (takenMaps > 0) {
                                        Bukkit.getScheduler().runTask(ImageFrame.plugin, () -> {
                                            Player p = (Player) sender;
                                            HashMap<Integer, ItemStack> result = p.getInventory().addItem(new ItemStack(Material.MAP, takenMaps));
                                            for (ItemStack stack : result.values()) {
                                                p.getWorld().dropItem(p.getEyeLocation(), stack).setVelocity(new Vector(0, 0, 0));
                                            }
                                        });
                                    }
                                }
                            });
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
                    sender.sendMessage(ImageFrame.messageNoConsole);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("admininfo")) {
            if (sender.hasPermission("imageframe.admininfo")) {
                if (args.length > 1) {
                    try {
                        int imageId = Integer.parseInt(args[1]);
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(imageId);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        } else if (imageMap instanceof URLImageMap) {
                            for (String line : ImageFrame.messageURLImageMapInfo) {
                                sender.sendMessage(ChatColorUtils.translateAlternateColorCodes('&', line
                                        .replace("{ImageID}", imageMap.getImageIndex() + "")
                                        .replace("{Name}", imageMap.getName())
                                        .replace("{Width}", imageMap.getWidth() + "")
                                        .replace("{Height}", imageMap.getHeight() + "")
                                        .replace("{CreatorName}", imageMap.getCreatorName())
                                        .replace("{CreatorUUID}", imageMap.getCreator().toString())
                                        .replace("{TimeCreated}", ImageFrame.dateFormat.format(new Date(imageMap.getCreationTime())))
                                        .replace("{Markers}", imageMap.getMapMarkers().stream().flatMap(each -> each.keySet().stream()).collect(Collectors.joining(", ", "[", "]")))
                                        .replace("{URL}", ((URLImageMap) imageMap).getUrl())));
                            }
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
                            ImageFrame.imageMapManager.deleteMap(imageMap.getImageIndex());
                            sender.sendMessage(ImageFrame.messageImageMapDeleted);
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
        } else if (args[0].equalsIgnoreCase("adminget")) {
            if (sender.hasPermission("imageframe.adminget")) {
                if (args.length > 1) {
                    if (args.length > 2 && args[2].equalsIgnoreCase("selection") && sender instanceof Player) {
                        ItemFrameSelectionManager.SelectedItemFrameResult selection;
                        selection = ImageFrame.itemFrameSelectionManager.getPlayerSelection((Player) sender);
                        if (selection == null) {
                            sender.sendMessage(ImageFrame.messageSelectionNoSelection);
                            return true;
                        }
                        try {
                            int imageId = Integer.parseInt(args[1]);
                            ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(imageId);
                            if (imageMap == null) {
                                sender.sendMessage(ImageFrame.messageNotAnImageMap);
                            } else {
                                imageMap.fillItemFrames(selection.getItemFrames(), selection.getRotation(), (frame, item) -> {}, ImageFrame.mapItemFormat);
                                sender.sendMessage(ImageFrame.messageImageMapCreated);
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ImageFrame.messageInvalidUsage);
                        }
                    } else {
                        Player player = null;
                        if (args.length > 2) {
                            player = Bukkit.getPlayer(args[2]);
                        } else if (sender instanceof Player) {
                            player = (Player) sender;
                        }
                        if (player == null) {
                            sender.sendMessage(ImageFrame.messageNoConsole);
                        } else {
                            try {
                                int imageId = Integer.parseInt(args[1]);
                                ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(imageId);
                                if (imageMap == null) {
                                    sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                } else {
                                    imageMap.giveMaps(Collections.singleton(player), ImageFrame.mapItemFormat);
                                    sender.sendMessage(ImageFrame.messageImageMapCreated);
                                }
                            } catch (NumberFormatException e) {
                                sender.sendMessage(ImageFrame.messageInvalidUsage);
                            }
                        }
                    }
                } else {
                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                }
            } else {
                sender.sendMessage(ImageFrame.messageNoPermission);
            }
            return true;
        } else if (args[0].equalsIgnoreCase("adminmarker")) {
            if (sender.hasPermission("imageframe.adminmarker")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (args.length > 1) {
                        if (args[1].equalsIgnoreCase("add")) {
                            if (args.length > 5) {
                                try {
                                    ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(Integer.parseInt(args[2]));
                                    if (imageMap == null) {
                                        sender.sendMessage(ImageFrame.messageNotAnImageMap);
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
                                } catch (NumberFormatException e) {
                                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                                }
                            } else {
                                sender.sendMessage(ImageFrame.messageInvalidUsage);
                            }
                        } else if (args[1].equalsIgnoreCase("remove")) {
                            if (args.length > 3) {
                                try {
                                    ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(Integer.parseInt(args[2]));
                                    if (imageMap == null) {
                                        sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                    } else {
                                        Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
                                            for (Map<String, MapCursor> map : imageMap.getMapMarkers()) {
                                                if (map.remove(args[3]) != null) {
                                                    try {
                                                        sender.sendMessage(ImageFrame.messageMarkersRemove);
                                                        imageMap.save();
                                                        imageMap.send(imageMap.getViewers());
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                    return;
                                                }
                                            }
                                            sender.sendMessage(ImageFrame.messageMarkersNotAMarker);
                                        });
                                    }
                                } catch (NumberFormatException e) {
                                    sender.sendMessage(ImageFrame.messageInvalidUsage);
                                }
                            } else {
                                sender.sendMessage(ImageFrame.messageInvalidUsage);
                            }
                        } else if (args[1].equalsIgnoreCase("clear")) {
                            if (args.length > 2) {
                                try {
                                    ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(Integer.parseInt(args[2]));
                                    if (imageMap == null) {
                                        sender.sendMessage(ImageFrame.messageNotAnImageMap);
                                    } else {
                                        Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
                                            try {
                                                imageMap.getMapMarkers().forEach(each -> each.clear());
                                                sender.sendMessage(ImageFrame.messageMarkersClear);
                                                imageMap.save();
                                                imageMap.send(imageMap.getViewers());
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
        } else if (args[0].equalsIgnoreCase("adminsetcreator")) {
            if (sender.hasPermission("imageframe.adminsetcreator")) {
                if (args.length > 2) {
                    try {
                        int imageId = Integer.parseInt(args[1]);
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(imageId);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        } else {
                            Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    OfflinePlayer player = Bukkit.getOfflinePlayer(args[2]);
                                    imageMap.changeCreator(player.getUniqueId());
                                    sender.sendMessage(ImageFrame.messageImageMapUpdated);
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
                if (sender.hasPermission("imageframe.clone")) {
                    tab.add("clone");
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
                if (sender.hasPermission("imageframe.get")) {
                    tab.add("get");
                }
                if (sender.hasPermission("imageframe.adminclone")) {
                    tab.add("adminclone");
                }
                if (sender.hasPermission("imageframe.adminrename")) {
                    tab.add("adminrename");
                }
                if (sender.hasPermission("imageframe.admininfo")) {
                    tab.add("admininfo");
                }
                if (sender.hasPermission("imageframe.admindelete")) {
                    tab.add("admindelete");
                }
                if (sender.hasPermission("imageframe.adminget")) {
                    tab.add("adminget");
                }
                if (sender.hasPermission("imageframe.update")) {
                    tab.add("update");
                }
                if (sender.hasPermission("imageframe.marker")) {
                    tab.add("marker");
                }
                if (sender.hasPermission("imageframe.adminmarker")) {
                    tab.add("adminmarker");
                }
                if (sender.hasPermission("imageframe.adminsetcreator")) {
                    tab.add("adminsetcreator");
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
                if (sender.hasPermission("imageframe.clone")) {
                    if ("clone".startsWith(args[0].toLowerCase())) {
                        tab.add("clone");
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
                if (sender.hasPermission("imageframe.get")) {
                    if ("get".startsWith(args[0].toLowerCase())) {
                        tab.add("get");
                    }
                }
                if (sender.hasPermission("imageframe.adminclone")) {
                    if ("adminclone".startsWith(args[0].toLowerCase())) {
                        tab.add("adminclone");
                    }
                }
                if (sender.hasPermission("imageframe.adminrename")) {
                    if ("adminrename".startsWith(args[0].toLowerCase())) {
                        tab.add("adminrename");
                    }
                }
                if (sender.hasPermission("imageframe.admininfo")) {
                    if ("admininfo".startsWith(args[0].toLowerCase())) {
                        tab.add("admininfo");
                    }
                }
                if (sender.hasPermission("imageframe.admindelete")) {
                    if ("admindelete".startsWith(args[0].toLowerCase())) {
                        tab.add("admindelete");
                    }
                }
                if (sender.hasPermission("imageframe.adminget")) {
                    if ("adminget".startsWith(args[0].toLowerCase())) {
                        tab.add("adminget");
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
                if (sender.hasPermission("imageframe.adminmarker")) {
                    if ("adminmarker".startsWith(args[0].toLowerCase())) {
                        tab.add("adminmarker");
                    }
                }
                if (sender.hasPermission("imageframe.adminsetcreator")) {
                    if ("adminsetcreator".startsWith(args[0].toLowerCase())) {
                        tab.add("adminsetcreator");
                    }
                }
                return tab;
            case 2:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        tab.add("<name>");
                    }
                }
                if (sender.hasPermission("imageframe.clone")) {
                    if ("clone".equalsIgnoreCase(args[0])) {
                        if (sender instanceof Player) {
                            for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(((Player) sender).getUniqueId())) {
                                if (imageMap.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                    tab.add(imageMap.getName());
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.refresh")) {
                    if ("refresh".equalsIgnoreCase(args[0])) {
                        tab.add("[url]");
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
                        if (sender instanceof Player) {
                            for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(((Player) sender).getUniqueId())) {
                                if (imageMap.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                    tab.add(imageMap.getName());
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.info")) {
                    if ("info".equalsIgnoreCase(args[0])) {
                        if (sender instanceof Player) {
                            for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(((Player) sender).getUniqueId())) {
                                if (imageMap.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                    tab.add(imageMap.getName());
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.delete")) {
                    if ("delete".equalsIgnoreCase(args[0])) {
                        if (sender instanceof Player) {
                            for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(((Player) sender).getUniqueId())) {
                                if (imageMap.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                    tab.add(imageMap.getName());
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.get")) {
                    if ("get".equalsIgnoreCase(args[0])) {
                        if (sender instanceof Player) {
                            for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(((Player) sender).getUniqueId())) {
                                if (imageMap.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                    tab.add(imageMap.getName());
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.refresh")) {
                    if ("refresh".equalsIgnoreCase(args[0])) {
                        tab.add("<image-id>");
                    }
                }
                if (sender.hasPermission("imageframe.adminrename")) {
                    if ("adminrename".equalsIgnoreCase(args[0])) {
                        tab.add("<image-id>");
                    }
                }
                if (sender.hasPermission("imageframe.admininfo")) {
                    if ("admininfo".equalsIgnoreCase(args[0])) {
                        tab.add("<image-id>");
                    }
                }
                if (sender.hasPermission("imageframe.admindelete")) {
                    if ("admindelete".equalsIgnoreCase(args[0])) {
                        tab.add("<image-id>");
                    }
                }
                if (sender.hasPermission("imageframe.adminget")) {
                    if ("adminget".equalsIgnoreCase(args[0])) {
                        tab.add("<image-id>");
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
                if (sender.hasPermission("imageframe.adminmarker")) {
                    if ("adminmarker".equalsIgnoreCase(args[0])) {
                        if ("add".startsWith(args[1].toLowerCase())) {
                            tab.add("add");
                        }
                        if ("remove".startsWith(args[1].toLowerCase())) {
                            tab.add("remove");
                        }
                        if ("clear".startsWith(args[1].toLowerCase())) {
                            tab.add("clear");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.adminsetcreator")) {
                    if ("adminsetcreator".equalsIgnoreCase(args[0])) {
                        tab.add("<image-id>");
                    }
                }
                return tab;
            case 3:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        tab.add("<url>");
                    }
                }
                if (sender.hasPermission("imageframe.clone")) {
                    if ("clone".equalsIgnoreCase(args[0])) {
                        tab.add("<new-name>");
                    }
                }
                if (sender.hasPermission("imageframe.get")) {
                    if ("get".equalsIgnoreCase(args[0])) {
                        if ("selection".startsWith(args[2].toLowerCase())) {
                            tab.add("selection");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.rename")) {
                    if ("rename".equalsIgnoreCase(args[0])) {
                        tab.add("<new-name>");
                    }
                }
                if (sender.hasPermission("imageframe.adminclone")) {
                    if ("adminclone".equalsIgnoreCase(args[0])) {
                        tab.add("<new-name>");
                    }
                }
                if (sender.hasPermission("imageframe.adminget")) {
                    if ("adminget".equalsIgnoreCase(args[0])) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                tab.add(player.getName());
                            }
                        }
                        if ("selection".startsWith(args[2].toLowerCase())) {
                            tab.add("selection");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.adminrename")) {
                    if ("adminrename".equalsIgnoreCase(args[0])) {
                        tab.add("<new-name>");
                    }
                }
                if (sender.hasPermission("imageframe.marker")) {
                    if ("marker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            if (sender instanceof Player) {
                                for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(((Player) sender).getUniqueId())) {
                                    if (imageMap.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                        tab.add(imageMap.getName());
                                    }
                                }
                            }
                        }
                        if ("remove".equalsIgnoreCase(args[1])) {
                            if (sender instanceof Player) {
                                for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(((Player) sender).getUniqueId())) {
                                    if (imageMap.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                        tab.add(imageMap.getName());
                                    }
                                }
                            }
                        }
                        if ("clear".equalsIgnoreCase(args[1])) {
                            if (sender instanceof Player) {
                                for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(((Player) sender).getUniqueId())) {
                                    if (imageMap.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                        tab.add(imageMap.getName());
                                    }
                                }
                            }
                        }
                    }
                }
                if (sender.hasPermission("imageframe.adminmarker")) {
                    if ("adminmarker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            tab.add("<image-id>");
                        }
                        if ("remove".equalsIgnoreCase(args[1])) {
                            tab.add("<image-id>");
                        }
                        if ("clear".equalsIgnoreCase(args[1])) {
                            tab.add("<image-id>");
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
                if (sender.hasPermission("imageframe.marker")) {
                    if ("marker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            tab.add("<marker-name>");
                        }
                        if ("remove".equalsIgnoreCase(args[1])) {
                            if (sender instanceof Player) {
                                Player player = (Player) sender;
                                ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(player.getUniqueId(), args[2]);
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
                if (sender.hasPermission("imageframe.adminmarker")) {
                    if ("adminmarker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            tab.add("<marker-name>");
                        }
                        if ("remove".equalsIgnoreCase(args[1])) {
                            try {
                                ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(Integer.parseInt(args[2]));
                                if (imageMap != null) {
                                    for (Map<String, MapCursor> map : imageMap.getMapMarkers()) {
                                        for (String name : map.keySet()) {
                                            if (name.toLowerCase().startsWith(args[3].toLowerCase())) {
                                                tab.add(name);
                                            }
                                        }
                                    }
                                }
                            } catch (NumberFormatException ignore) {
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
                if (sender.hasPermission("imageframe.adminmarker")) {
                    if ("adminmarker".equalsIgnoreCase(args[0])) {
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
                if (sender.hasPermission("imageframe.adminmarker")) {
                    if ("adminmarker".equalsIgnoreCase(args[0])) {
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
            default:
                if (sender.hasPermission("imageframe.marker")) {
                    if ("marker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            tab.add("[caption]...");
                        }
                    }
                }
                if (sender.hasPermission("imageframe.adminmarker")) {
                    if ("adminmarker".equalsIgnoreCase(args[0])) {
                        if ("add".equalsIgnoreCase(args[1])) {
                            tab.add("[caption]...");
                        }
                    }
                }
                return tab;
        }
    }

}
