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

package com.loohp.imageframe.migration;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.DitheringType;
import com.loohp.imageframe.objectholders.NonUpdatableStaticImageMap;
import com.loohp.imageframe.utils.MapUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.simpleyaml.configuration.file.YamlFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ImageOnMapMigration implements ExternalPluginMigration {

    public static final String PLUGIN_NAME = "ImageOnMap";

    @Override
    public String externalPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean requirePlayer() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void migrate(UUID unused) {
        if (Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] ImageOnMap must be disabled for migration to begin");
            return;
        }
        File migrationMarker = new File(ImageFrame.plugin.getDataFolder().getParent() + "/ImageOnMap/imageframe-migrated.bin");
        if (migrationMarker.exists()) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ImageFrame] ImageOnMap data already marked as migrated");
            return;
        }
        File userFolder = new File(ImageFrame.plugin.getDataFolder().getParent() + "/ImageOnMap/maps");
        if (!userFolder.exists() || !userFolder.isDirectory()) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] ImageOnMap plugin data folder not found");
            return;
        }
        World world = MapUtils.getMainWorld();
        File imageFolder = new File(ImageFrame.plugin.getDataFolder().getParent() + "/ImageOnMap/images");
        for (File file : userFolder.listFiles()) {
            String fileNameWithoutExtension = file.getName().substring(0, file.getName().lastIndexOf("."));
            UUID owner;
            try {
                owner = UUID.fromString(fileNameWithoutExtension);
            } catch (IllegalArgumentException e) {
                owner = Bukkit.getOfflinePlayer(fileNameWithoutExtension).getUniqueId();
            }
            try {
                YamlFile yaml = YamlFile.loadConfiguration(file);
                List<Map<String, Object>> mapList = (List<Map<String, Object>>) yaml.getList("PlayerMapStore.mapList");
                if (mapList == null) {
                    continue;
                }
                int index = 0;
                for (Map<String, Object> section : mapList) {
                    try {
                        String name = ((String) section.get("name")).replace(" ", "_");
                        String iomId = (String) section.get("id");
                        String type = (String) section.get("type");
                        BufferedImage[] images;
                        List<Integer> mapIds;
                        int width;
                        int height;
                        if (type.equalsIgnoreCase("SINGLE")) {
                            int mapId = (int) section.get("mapID");
                            mapIds = Collections.singletonList(mapId);
                            images = new BufferedImage[] {ImageIO.read(new File(imageFolder, "map" + mapId + ".png"))};
                            width = 1;
                            height = 1;
                        } else if (type.equalsIgnoreCase("POSTER")) {
                            mapIds = new ArrayList<>((List<Integer>) section.get("mapsIDs"));
                            images = new BufferedImage[mapIds.size()];
                            width = (int) section.get("columns");
                            height = (int) section.get("rows");
                            for (int i = 0; i < images.length; i++) {
                                images[i] = ImageIO.read(new File(imageFolder, "map" + mapIds.get(i) + ".png"));
                            }
                        } else {
                            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to migrate " + name + " ImageOnMap user file " + file.getName() + ": Unknown type " + type);
                            continue;
                        }
                        NonUpdatableStaticImageMap imageMap;
                        if (ImageFrame.imageMapManager.getFromCreator(owner, name) == null) {
                            imageMap = NonUpdatableStaticImageMap.create(ImageFrame.imageMapManager, name, images, mapIds, width, height, DitheringType.NEAREST_COLOR, owner).get();
                        } else if (ImageFrame.imageMapManager.getFromCreator(owner, iomId) == null) {
                            imageMap = NonUpdatableStaticImageMap.create(ImageFrame.imageMapManager, iomId, images, mapIds, width, height, DitheringType.NEAREST_COLOR, owner).get();
                        } else {
                            imageMap = NonUpdatableStaticImageMap.create(ImageFrame.imageMapManager, "ImageOnMap-" + iomId, images, mapIds, width, height, DitheringType.NEAREST_COLOR, owner).get();
                        }
                        ImageFrame.imageMapManager.addMap(imageMap);
                        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] Migrated ImageOnMap " + file.getName() + " to " + name + " of " + owner);
                    } catch (Exception e) {
                        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to migrate ImageOnMap " + file.getName() + " of index " + index);
                        e.printStackTrace();
                    }
                    index++;
                }
            } catch (IOException e) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to migrate ImageOnMap user file " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }
        try {
            migrationMarker.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] ImageOnMap migration complete!");
    }

}
