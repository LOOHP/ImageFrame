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
import org.simpleyaml.configuration.file.YamlConfiguration;
import org.simpleyaml.configuration.serialization.ConfigurationSerializable;
import org.simpleyaml.configuration.serialization.ConfigurationSerialization;
import org.simpleyaml.configuration.serialization.SerializableAs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ImageMapMigration implements ExternalPluginMigration {

    public static final String PLUGIN_NAME = "ImageMaps";

    @Override
    public String externalPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public boolean requirePlayer() {
        return true;
    }

    @Override
    public void migrate(UUID owner) {
        if (Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] ImageMaps must be disabled for migration to begin");
            return;
        }
        File migrationMarker = new File(ImageFrame.plugin.getDataFolder().getParent() + "/ImageMaps/imageframe-migrated.bin");
        if (migrationMarker.exists()) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ImageFrame] ImageMaps data already marked as migrated");
            return;
        }
        File folder = new File(ImageFrame.plugin.getDataFolder().getParent() + "/ImageMaps/images");
        if (!folder.exists() || !folder.isDirectory()) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] ImageMaps plugin data folder not found");
            return;
        }
        try {
            ConfigurationSerialization.registerClass(ImageMap.class);
            YamlConfiguration mapConfig = YamlConfiguration.loadConfiguration(new File(ImageFrame.plugin.getDataFolder().getParent() + "/ImageMaps/maps.yml"));
            World world = MapUtils.getMainWorld();
            for (Map.Entry<String, Object> entry : mapConfig.getConfigurationSection("maps").getValues(false).entrySet()) {
                String key = entry.getKey();
                try {
                    int mapId = Integer.parseInt(key);
                    ImageMap data = (ImageMap) entry.getValue();
                    String fileName = data.getFilename();
                    BufferedImage[] images = new BufferedImage[] {MapUtils.getSubImage(ImageIO.read(new File(folder, fileName)), data.getX(), data.getY())};
                    String name = "ImageMaps_" + mapId;
                    NonUpdatableStaticImageMap imageMap = NonUpdatableStaticImageMap.create(ImageFrame.imageMapManager, name, images, Collections.singletonList(mapId), 1, 1, DitheringType.NEAREST_COLOR, owner).get();
                    ImageFrame.imageMapManager.addMap(imageMap);
                    Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] Migrated ImageMaps " + key + " to " + name);
                } catch (Exception e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to migrate ImageMaps " + key);
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            migrationMarker.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] ImageMaps migration complete!");
    }

    @SerializableAs("ImageMaps.Map")
    public static class ImageMap implements ConfigurationSerializable {

        private String filename;
        private int x;
        private int y;
        private double scale;

        public ImageMap(String filename, int x, int y, double scale) {
            this.filename = filename;
            this.x = x;
            this.y = y;
            this.scale = scale;
        }

        public ImageMap(Map<?, ?> map) {
            this.filename = map.get("image").toString();
            this.x = (Integer) map.get("x");
            this.y = (Integer) map.get("y");
            this.scale = (Double) map.get("scale");
        }

        @Override
        public Map<String, Object> serialize() {
            Map<String, Object> map = new HashMap<>();
            map.put("image", filename);
            map.put("x", x);
            map.put("y", y);
            map.put("scale", scale);

            return map;
        }

        public String getFilename() {
            return filename;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public double getScale() {
            return scale;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((filename == null) ? 0 : filename.hashCode());
            long temp;
            temp = Double.doubleToLongBits(scale);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            result = prime * result + x;
            result = prime * result + y;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof ImageMap))
                return false;
            ImageMap other = (ImageMap) obj;
            if (filename == null) {
                if (other.filename != null)
                    return false;
            } else if (!filename.equals(other.filename))
                return false;
            if (Double.doubleToLongBits(scale) != Double.doubleToLongBits(other.scale))
                return false;
            if (x != other.x)
                return false;
            if (y != other.y)
                return false;
            return true;
        }
    }

}
