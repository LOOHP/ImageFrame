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

package com.loohp.imageframe.utils;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.ImageMapAccessPermissionType;
import com.loohp.imageframe.objectholders.MutablePair;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class ImageMapUtils {

    public static MutablePair<UUID, String> extractImageMapPlayerPrefixedName(CommandSender sender, String str) {
        int index = str.indexOf(':');
        if (index < 0) {
            return new MutablePair<>(sender instanceof Player ? ((Player) sender).getUniqueId() : null, str);
        }
        String playerName = str.substring(0, index);
        String imageMapName = str.substring(index + 1);
        if (playerName.isEmpty() || playerName.trim().isEmpty()) {
            return new MutablePair<>(sender instanceof Player ? ((Player) sender).getUniqueId() : null, imageMapName);
        }
        UUID playerUUID = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        return new MutablePair<>(playerUUID, imageMapName);
    }

    public static ImageMap getFromPlayerPrefixedName(CommandSender sender, String str) {
        if (sender instanceof Player) {
            ImageMap imageMap = ImageFrame.imageMapManager.getFromCreator(((Player) sender).getUniqueId(), str);
            if (imageMap != null) {
                return imageMap;
            }
        }
        MutablePair<UUID, String> extraction = ImageMapUtils.extractImageMapPlayerPrefixedName(sender, str);
        return ImageFrame.imageMapManager.getFromCreator(extraction.getFirst(), extraction.getSecond());
    }

    public static Set<String> getImageMapNameSuggestions(CommandSender sender, String str) {
        return getImageMapNameSuggestions(sender, str, ImageMapAccessPermissionType.BASE, imageMap -> true);
    }

    public static Set<String> getImageMapNameSuggestions(CommandSender sender, String str, ImageMapAccessPermissionType permissionType, Predicate<ImageMap> predicate) {
        Set<String> tab = new LinkedHashSet<>();
        if (sender instanceof Player) {
            for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(((Player) sender).getUniqueId())) {
                if (imageMap.getName().toLowerCase().startsWith(str.toLowerCase()) && predicate.test(imageMap)) {
                    tab.add(imageMap.getName());
                }
            }
        }
        if (!str.contains(":")) {
            return tab;
        }
        MutablePair<UUID, String> extraction = extractImageMapPlayerPrefixedName(sender, str);
        UUID uuid = extraction.getFirst();
        if (uuid == null) {
            return tab;
        }
        for (ImageMap imageMap : ImageFrame.imageMapManager.getFromCreator(extraction.getFirst())) {
            if (ImageFrame.hasImageMapPermission(imageMap, sender, permissionType)) {
                String prefixedName = imageMap.getCreatorName() + ":" + imageMap.getName();
                if (prefixedName.toLowerCase().startsWith(str.toLowerCase()) && predicate.test(imageMap)) {
                    tab.add(prefixedName);
                }
            }
        }
        return tab;
    }

}
