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

package com.loohp.imageframe.objectholders;

import com.google.gson.JsonObject;
import com.loohp.imageframe.media.GifReaderMediaLoader;
import com.loohp.imageframe.media.ImageIOMediaLoader;
import com.loohp.imageframe.media.MediaLoader;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

public class ImageMapLoaders {

    private static final List<ImageMapLoader<?, ?>> LOADERS_ORDERED = new CopyOnWriteArrayList<>();
    private static final Map<Key, ImageMapLoader<?, ?>> LOADERS_MAP = new ConcurrentHashMap<>();

    public static final URLStaticImageMapLoader URL_STATIC_IMAGE_MAP_LOADER = registerLoader(new URLStaticImageMapLoader());
    public static final URLAnimatedImageMapLoader URL_ANIMATED_IMAGE_MAP_LOADER = registerLoader(new URLAnimatedImageMapLoader());
    public static final MinecraftURLOverlayImageMapLoader MINECRAFT_URL_OVERLAY_IMAGE_MAP_LOADER = registerLoader(new MinecraftURLOverlayImageMapLoader());
    public static final NonUpdatableStaticImageMapLoader NON_UPDATABLE_IMAGE_MAP_LOADER = registerLoader(new NonUpdatableStaticImageMapLoader());

    public static final GifReaderMediaLoader GIF_READER_MEDIA_LOADER = registerLoader(new GifReaderMediaLoader(), URL_ANIMATED_IMAGE_MAP_LOADER);
    public static final ImageIOMediaLoader IMAGE_IO_MEDIA_LOADER = registerLoader(new ImageIOMediaLoader(), URL_STATIC_IMAGE_MAP_LOADER, MINECRAFT_URL_OVERLAY_IMAGE_MAP_LOADER);

    public static void init() {
        /* do nothing */
    }

    public static <T extends ImageMapLoader<?, ?>> T registerLoader(T loader) {
        LOADERS_ORDERED.add(loader);
        LOADERS_MAP.put(loader.getIdentifier(), loader);
        Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.GRAY + "[ImageFrame] Registered ImageMapLoader " + loader.getIdentifier().asString());
        return loader;
    }

    @SuppressWarnings("unchecked")
    public static <T extends ImageMapLoader<?, ?>> T getLoader(Class<T> loaderClass) {
        return (T) LOADERS_ORDERED.stream()
                .filter(l -> loaderClass.equals(l.getClass()))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static <T extends ImageMap, C extends ImageMapCreateInfo> ImageMapLoader<? extends T, C> getLoader(Class<T> imageMapClass, Class<C> createInfoClass, String imageType, CommandSender sender) {
        return (ImageMapLoader<? extends T, C>) LOADERS_ORDERED.stream()
                .filter(l -> imageMapClass.isAssignableFrom(l.getImageMapClass()))
                .filter(l -> createInfoClass.equals(l.getImageMapCreateInfoClass()))
                .filter(l -> l.isSupported(imageType))
                .filter(l -> l.getExtraPermissions().stream().allMatch(p -> sender.hasPermission(p)))
                .max(Comparator.comparing((ImageMapLoader<?, ?> l) -> l.getPriority(imageType)))
                .orElse(null);
    }

    public static <T extends ImageMap> ImageMapLoader<?, ?> getLoader(Key identifier) {
        return LOADERS_MAP.get(identifier);
    }

    @SuppressWarnings("PatternValidation")
    public static <T extends ImageMap> ImageMapLoader<?, ?> getLoader(String identifier) {
        if (identifier.contains(":")) {
            return getLoader(Key.key(identifier));
        }
        return LOADERS_ORDERED.stream()
                .filter(l -> identifier.equalsIgnoreCase(l.getLegacyType()))
                .findFirst()
                .orElse(null);
    }

    public static Future<? extends ImageMap> load(ImageMapManager manager, JsonObject json) throws Exception {
        String type = json.get("type").getAsString();
        ImageMapLoader<?, ?> loader = getLoader(type);
        if (loader == null) {
            throw new IllegalStateException("Unable to find ImageMapLoader " + type);
        }
        return loader.load(manager, json);
    }

    public static <T extends MediaLoader> T registerLoader(T loader, ImageMapLoader<?, ?>... imageMapLoaders) {
        for (ImageMapLoader<?, ?> imageMapLoader : imageMapLoaders) {
            imageMapLoader.registerMediaLoaderLast(loader);
            Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.GRAY + "[ImageFrame] Registered MediaLoader " + loader.getIdentifier().asString() + " to ImageMapLoader " + imageMapLoader.getIdentifier().asString());
        }
        return loader;
    }

}
