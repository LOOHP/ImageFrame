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

package com.loohp.imageframe.migration;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.NonUpdatableStaticImageMap;
import com.loohp.imageframe.utils.MapUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.World;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

public class DrMapMigration {

    public static void migrate(UUID owner) {
        if (Bukkit.getPluginManager().isPluginEnabled("DrMap")) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] DrMap must be disabled for migration to begin");
            return;
        }
        File migrationMarker = new File(Bukkit.getWorldContainer() + "/plugins/DrMap/imageframe-migrated.bin");
        if (migrationMarker.exists()) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ImageFrame] DrMap data already marked as migrated");
            return;
        }
        File folder = new File(Bukkit.getWorldContainer() + "/plugins/DrMap/images");
        if (!folder.exists() || !folder.isDirectory()) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] DrMap plugin data folder not found");
            return;
        }
        World world = MapUtils.getMainWorld();
        for (File file : folder.listFiles()) {
            try {
                BufferedImage[] images = new BufferedImage[] {ImageIO.read(file)};
                int mapId = Integer.parseInt(file.getName().substring(0, file.getName().lastIndexOf(".")));
                if (MapUtils.getMap(mapId).get() == null) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ImageFrame] Changed a mapId of DrMap " + file.getName() + " as it could not be found on the server, you might need to redistribute this ImageMap to players and item frames.");
                    mapId = MapUtils.createMap(world).get().getId();
                }
                String name = "DrMap_" + mapId;
                NonUpdatableStaticImageMap imageMap = NonUpdatableStaticImageMap.create(ImageFrame.imageMapManager, name, images, Collections.singletonList(mapId), 1, 1, owner).get();
                ImageFrame.imageMapManager.addMap(imageMap);
                Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] Migrated DrMap " + file.getName() + " to " + name);
            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to migrate DrMap " + file.getAbsolutePath());
                e.printStackTrace();
            }
        }
        try {
            migrationMarker.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] DrMap migration complete!");
    }

}
