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
import com.loohp.imageframe.objectholders.URLAnimatedImageMap;
import com.loohp.imageframe.objectholders.URLImageMap;
import com.loohp.imageframe.objectholders.URLStaticImageMap;
import com.loohp.imageframe.updater.Updater;
import com.loohp.imageframe.utils.MapUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

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
                    if (args.length == 4) {
                        try {
                            Player player = (Player) sender;
                            int width = Integer.parseInt(args[2]);
                            int height = Integer.parseInt(args[3]);
                            if (ImageFrame.requireEmptyMaps) {
                                if (!MapUtils.removeEmptyMaps(player, width * height, true)) {
                                    sender.sendMessage(ImageFrame.messageNotEnoughMaps.replace("{Amount}", (width * height) + ""));
                                    return true;
                                }
                            }
                            Bukkit.getScheduler().runTaskAsynchronously(ImageFrame.plugin, () -> {
                                try {
                                    ImageMap imageMap;
                                    if (player.hasPermission("imageframe.create.animated") && args[1].toLowerCase().endsWith(".gif")) {
                                        imageMap = URLAnimatedImageMap.create(ImageFrame.imageMapManager, args[1], width, height, player.getUniqueId());
                                    } else {
                                        imageMap = URLStaticImageMap.create(ImageFrame.imageMapManager, args[1], width, height, player.getUniqueId());
                                    }
                                    ImageFrame.imageMapManager.addMap(imageMap);
                                    imageMap.giveMaps(Collections.singleton((Player) sender));
                                    sender.sendMessage(ImageFrame.messageImageMapCreated);
                                } catch (Exception e) {
                                    sender.sendMessage(ImageFrame.messageUnableToLoadMap.replace("{Error}", e.getMessage() == null ? "null" : e.getMessage()));
                                    e.printStackTrace();
                                }
                            });
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ImageFrame.messageInvalidUsage);
                        } catch (Exception e) {
                            sender.sendMessage(ImageFrame.messageUnableToLoadMap.replace("{Error}", e.getMessage() == null ? "null" : e.getMessage()));
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
                                                sender.sendMessage(ImageFrame.messageUnableToLoadMap.replace("{Error}", e.getLocalizedMessage()));
                                                e.printStackTrace();
                                            }
                                        } else {
                                            imageMap.update();
                                            sender.sendMessage(ImageFrame.messageImageMapRefreshed);
                                        }
                                    } catch (Exception e) {
                                        sender.sendMessage(ImageFrame.messageUnableToLoadMap.replace("{Error}", e.getLocalizedMessage()));
                                        e.printStackTrace();
                                    }
                                });
                            } catch (Exception e) {
                                sender.sendMessage(ImageFrame.messageUnableToLoadMap.replace("{Error}", e.getLocalizedMessage()));
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
        } else if (args[0].equalsIgnoreCase("info")) {
            if (sender.hasPermission("imageframe.info")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    MapView mapView = MapUtils.getPlayerMapView(player);
                    if (mapView == null) {
                        sender.sendMessage(ImageFrame.messageNotAnImageMap);
                    } else {
                        ImageMap imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
                        if (imageMap == null) {
                            sender.sendMessage(ImageFrame.messageNotAnImageMap);
                        } else if (imageMap instanceof URLImageMap) {
                            String creatorName = Bukkit.getOfflinePlayer(imageMap.getCreator()).getName();
                            if (creatorName == null) {
                                creatorName = "";
                            }
                            for (String line : ImageFrame.messageURLImageMapInfo) {
                                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line
                                        .replace("{ImageID}", imageMap.getImageIndex() + "")
                                        .replace("{Width}", imageMap.getWidth() + "")
                                        .replace("{Height}", imageMap.getHeight() + "")
                                        .replace("{CreatorName}", creatorName)
                                        .replace("{CreatorUUID}", imageMap.getCreator().toString())
                                        .replace("{TimeCreated}", ImageFrame.dateFormat.format(new Date(imageMap.getCreationTime())))
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
        } else if (args[0].equalsIgnoreCase("get")) {
            if (sender.hasPermission("imageframe.get")) {
                if (args.length > 1) {
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
                                imageMap.giveMaps(Collections.singleton(player));
                                sender.sendMessage(ImageFrame.messageImageMapCreated);
                            }
                        } catch (NumberFormatException e) {
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
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', Bukkit.spigot().getConfig().getString("messages.unknown-command")));
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
                if (sender.hasPermission("imageframe.refresh")) {
                    tab.add("refresh");
                }
                if (sender.hasPermission("imageframe.info")) {
                    tab.add("info");
                }
                if (sender.hasPermission("imageframe.get")) {
                    tab.add("get");
                }
                if (sender.hasPermission("imageframe.update")) {
                    tab.add("update");
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
                if (sender.hasPermission("imageframe.refresh")) {
                    if ("refresh".startsWith(args[0].toLowerCase())) {
                        tab.add("refresh");
                    }
                }
                if (sender.hasPermission("imageframe.info")) {
                    if ("info".startsWith(args[0].toLowerCase())) {
                        tab.add("info");
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
                return tab;
            case 2:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        tab.add("<url>");
                    }
                }
                if (sender.hasPermission("imageframe.refresh")) {
                    if ("refresh".equalsIgnoreCase(args[0])) {
                        tab.add("[url]");
                    }
                }
                if (sender.hasPermission("imageframe.get")) {
                    if ("get".equalsIgnoreCase(args[0])) {
                        tab.add("<image-id>");
                    }
                }
                return tab;
            case 3:
                if (sender.hasPermission("imageframe.create")) {
                    if ("create".equalsIgnoreCase(args[0])) {
                        tab.add("<width>");
                    }
                }
                if (sender.hasPermission("imageframe.get")) {
                    if ("get".equalsIgnoreCase(args[0])) {
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
                        tab.add("<height>");
                    }
                }
                return tab;
            default:
                return tab;
        }
    }

}
