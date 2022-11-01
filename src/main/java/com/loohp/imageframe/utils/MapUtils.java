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

package com.loohp.imageframe.utils;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapUtils {

    public static final int MAP_WIDTH = 128;
    public static final int COLOR_ARRAY_LENGTH = 16384;

    private static Class<?> craftMapViewClass;
    private static Field craftMapViewWorldMapField;
    private static Class<?> nmsWorldMapClass;
    private static Field nmsWorldMapHumansField;
    private static Field nmsWorldColorsField;
    private static Class<?> nmsEntityHumanClass;
    private static Method nmsEntityHumanGetBukkitEntityMethod;

    static {
        try {
            craftMapViewClass = NMSUtils.getNMSClass("org.bukkit.craftbukkit.%s.map.CraftMapView");
            craftMapViewWorldMapField = craftMapViewClass.getDeclaredField("worldMap");
            nmsWorldMapClass = NMSUtils.getNMSClass("net.minecraft.server.%s.WorldMap", "net.minecraft.world.level.saveddata.maps.WorldMap");
            nmsWorldMapHumansField = NMSUtils.reflectiveLookup(Field.class, () -> {
                return nmsWorldMapClass.getDeclaredField("humans");
            }, () -> {
                return nmsWorldMapClass.getDeclaredField("o");
            });
            nmsWorldColorsField = NMSUtils.reflectiveLookup(Field.class, () -> {
                return nmsWorldMapClass.getDeclaredField("colors");
            }, () -> {
                return nmsWorldMapClass.getDeclaredField("g");
            });
            nmsEntityHumanClass = NMSUtils.getNMSClass("net.minecraft.server.%s.EntityHuman", "net.minecraft.world.entity.player.EntityHuman");
            nmsEntityHumanGetBukkitEntityMethod = nmsEntityHumanClass.getMethod("getBukkitEntity");
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    public static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage image = new BufferedImage(width * MAP_WIDTH, height * MAP_WIDTH, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.createGraphics();
        if (source.getWidth() > source.getHeight()) {
            double ratio = (double) image.getWidth() / (double) source.getWidth();
            int h = (int) Math.round(source.getHeight() * ratio);
            g.drawImage(source, 0, (image.getHeight() - h) / 2, image.getWidth(), h, null);
        } else {
            double ratio = (double) image.getHeight() / (double) source.getHeight();
            int w = (int) Math.round(source.getWidth() * ratio);
            g.drawImage(source, (image.getWidth() - w) / 2, 0, w, image.getHeight(), null);
        }
        g.dispose();
        return image;
    }

    public static BufferedImage getSubImage(BufferedImage source, int x, int y) {
        return source.getSubimage(x * MAP_WIDTH, y * MAP_WIDTH, MAP_WIDTH, MAP_WIDTH);
    }

    public static MapView getPlayerMapView(Player player) {
        ItemStack itemStack = player.getEquipment().getItemInHand();
        if (itemStack == null || itemStack.getType().equals(Material.AIR) || !itemStack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof MapMeta)) {
            return null;
        }
        return ((MapMeta) meta).getMapView();
    }

    public static boolean removeEmptyMaps(Player player, int count, boolean checkGameMode) {
        if (checkGameMode) {
            GameMode gameMode = player.getGameMode();
            if (gameMode.equals(GameMode.CREATIVE) || gameMode.equals(GameMode.SPECTATOR)) {
                return true;
            }
        }
        if (player.getInventory().contains(Material.MAP, count)) {
            player.getInventory().removeItem(new ItemStack(Material.MAP, count));
            return true;
        }
        return false;
    }

    public static void setColors(MapView mapView, byte[] colors) {
        if (colors.length != COLOR_ARRAY_LENGTH) {
            throw new IllegalArgumentException("colors array length must be 16384");
        }
        try {
            Object craftMapView = craftMapViewClass.cast(mapView);
            craftMapViewWorldMapField.setAccessible(true);
            Object nmsWorldMap = craftMapViewWorldMapField.get(craftMapView);
            nmsWorldColorsField.setAccessible(true);
            nmsWorldColorsField.set(nmsWorldMap, colors);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static Set<Player> getViewers(MapView mapView) {
        try {
            Object craftMapView = craftMapViewClass.cast(mapView);
            craftMapViewWorldMapField.setAccessible(true);
            Object nmsWorldMap = craftMapViewWorldMapField.get(craftMapView);
            nmsWorldMapHumansField.setAccessible(true);
            Map<?, ?> humansMap = (Map<?, ?>) nmsWorldMapHumansField.get(nmsWorldMap);
            Set<Player> players = new HashSet<>(humansMap.size());
            for (Object obj : humansMap.keySet()) {
                players.add((Player) nmsEntityHumanGetBukkitEntityMethod.invoke(obj));
            }
            return players;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

}
