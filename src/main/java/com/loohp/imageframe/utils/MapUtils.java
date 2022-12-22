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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.MutablePair;
import com.loohp.imageframe.objectholders.Point2D;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class MapUtils {

    public static final int MAP_WIDTH = 128;
    public static final int COLOR_ARRAY_LENGTH = 16384;
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final List<BlockFace> CARTESIAN_BLOCK_FACES = Collections.unmodifiableList(Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN));

    private static Class<?> craftMapViewClass;
    private static Field craftMapViewWorldMapField;
    private static Class<?> nmsWorldMapClass;
    private static Field nmsWorldMapHumansField;
    private static Field nmsWorldColorsField;
    private static Class<?> nmsEntityHumanClass;
    private static Method nmsEntityHumanGetBukkitEntityMethod;
    private static Class<?> nmsMapIconTypeClass;
    private static Object[] nmsMapIconTypeEnums;
    private static Field nmsMapIconTypeRenderOnFrameField;
    private static Class<?> nmsMapIconClass;
    private static Constructor<?> nmsMapIconConstructor;
    private static Class<?> nmsWorldMapBClass;
    private static Constructor<?> nmsWorldMapBClassConstructor;
    private static Class<?> craftPlayerClass;
    private static Method craftMapViewRenderMethod;
    private static Class<?> craftRenderDataClass;
    private static Field craftRenderDataBufferField;
    private static Field craftRenderDataCursorsField;

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
            nmsMapIconTypeClass = NMSUtils.getNMSClass("net.minecraft.server.%s.MapIcon", "net.minecraft.world.level.saveddata.maps.MapIcon").getDeclaredClasses()[0];
            nmsMapIconTypeEnums = nmsMapIconTypeClass.getEnumConstants();
            nmsMapIconTypeRenderOnFrameField = Arrays.stream(nmsMapIconTypeClass.getDeclaredFields()).filter(each -> each.getType().equals(boolean.class)).findFirst().get();

            nmsMapIconClass = NMSUtils.getNMSClass("net.minecraft.server.%s.MapIcon", "net.minecraft.world.level.saveddata.maps.MapIcon");
            nmsMapIconConstructor = nmsMapIconClass.getConstructors()[0];
            nmsWorldMapClass = NMSUtils.getNMSClass("net.minecraft.server.%s.WorldMap", "net.minecraft.world.level.saveddata.maps.WorldMap");
            if (ImageFrame.version.isNewerOrEqualTo(MCVersion.V1_17)) {
                //noinspection OptionalGetWithoutIsPresent
                nmsWorldMapBClass = Arrays.stream(nmsWorldMapClass.getClasses()).filter(each -> each.getName().endsWith("$b")).findFirst().get();
                nmsWorldMapBClassConstructor = nmsWorldMapBClass.getConstructor(int.class, int.class, int.class, int.class, byte[].class);
            }
            craftPlayerClass = NMSUtils.getNMSClass("org.bukkit.craftbukkit.%s.entity.CraftPlayer");
            craftMapViewRenderMethod = craftMapViewClass.getMethod("render", craftPlayerClass);
            craftRenderDataClass = NMSUtils.getNMSClass("org.bukkit.craftbukkit.%s.map.RenderData");
            craftRenderDataBufferField = craftRenderDataClass.getField("buffer");
            craftRenderDataCursorsField = craftRenderDataClass.getField("cursors");
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    public static <T> Future<T> callSyncMethod(Callable<T> task) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception e) {
                CompletableFuture<T> future = new CompletableFuture<>();
                future.completeExceptionally(e);
                return future;
            }
        }
        return Bukkit.getScheduler().callSyncMethod(ImageFrame.plugin, task);
    }

    public static void sendImageMap(MapView mapView, Collection<? extends Player> players) {
        List<MapRenderer> renderers = mapView.getRenderers();
        if (renderers.isEmpty()) {
            throw new IllegalArgumentException("mapView is not from an image map");
        }
        Optional<MapRenderer> optMapRenderer = renderers.stream().filter(each -> each instanceof ImageMap.ImageMapRenderer).findFirst();
        if (!optMapRenderer.isPresent()) {
            throw new IllegalArgumentException("mapView is not from an image map");
        }
        ImageMap.ImageMapRenderer imageMapManager = (ImageMap.ImageMapRenderer) optMapRenderer.get();

        for (Player player : players) {
            try {
                MutablePair<byte[], Collection<MapCursor>> renderData = imageMapManager.renderPacketData(mapView, player);
                byte[] colors = renderData.getFirst();
                Collection<MapCursor> cursors = renderData.getSecond();

                PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.MAP);
                if (ImageFrame.version.isNewerOrEqualTo(MCVersion.V1_17)) {
                    packet.getIntegers().write(0, mapView.getId());
                    packet.getBytes().write(0, (byte) 0);
                    packet.getBooleans().write(0, false);
                    if (cursors == null) {
                        packet.getModifier().write(3, Collections.emptyList());
                    } else {
                        List<Object> mapIcons = new ArrayList<>();
                        for (MapCursor mapCursor : cursors) {
                            mapIcons.add(toNMSMapIcon(mapCursor));
                        }
                        packet.getModifier().write(3, mapIcons);
                    }
                    if (colors == null) {
                        packet.getModifier().write(4, null);
                    } else {
                        packet.getModifier().write(4, nmsWorldMapBClassConstructor.newInstance(0, 0, 128, 128, colors));
                    }
                } else {
                    packet.getIntegers().write(0, mapView.getId());
                    packet.getBytes().write(0, (byte) 0);
                    packet.getBooleans().write(0, false);
                    packet.getBooleans().write(1, false);
                    if (cursors == null) {
                        packet.getModifier().write(4, Array.newInstance(nmsMapIconClass, 0));
                    } else {
                        Object mapIcons = Array.newInstance(nmsMapIconClass, cursors.size());
                        int i = 0;
                        for (MapCursor mapCursor : cursors) {
                            Array.set(mapIcons, i++, toNMSMapIcon(mapCursor));
                        }
                        packet.getModifier().write(4, mapIcons);
                    }
                    packet.getIntegers().write(1, 0);
                    packet.getIntegers().write(2, 0);
                    if (colors == null) {
                        packet.getIntegers().write(3, 0);
                        packet.getIntegers().write(4, 0);
                        packet.getByteArrays().write(0, EMPTY_BYTE_ARRAY);
                    } else {
                        packet.getIntegers().write(3, 128);
                        packet.getIntegers().write(4, 128);
                        packet.getByteArrays().write(0, colors);
                    }
                }
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage image = new BufferedImage(width * MAP_WIDTH, height * MAP_WIDTH, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.createGraphics();
        double wRatio = (double) image.getWidth() / (double) source.getWidth();
        double hRatio = (double) image.getHeight() / (double) source.getHeight();
        if (wRatio < hRatio) {
            int h = (int) Math.round(source.getHeight() * wRatio);
            g.drawImage(source, 0, (image.getHeight() - h) / 2, image.getWidth(), h, null);
        } else {
            int w = (int) Math.round(source.getWidth() * hRatio);
            g.drawImage(source, (image.getWidth() - w) / 2, 0, w, image.getHeight(), null);
        }
        g.dispose();
        return image;
    }

    public static BufferedImage getSubImage(BufferedImage source, int x, int y) {
        return source.getSubimage(x * MAP_WIDTH, y * MAP_WIDTH, MAP_WIDTH, MAP_WIDTH);
    }

    public static MapView getItemMapView(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().equals(Material.AIR) || !itemStack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof MapMeta)) {
            return null;
        }
        return ((MapMeta) meta).getMapView();
    }

    public static MapView getPlayerMapView(Player player) {
        return getItemMapView(player.getEquipment().getItemInHand());
    }

    public static int removeEmptyMaps(Player player, int count, boolean checkGameMode) {
        if (checkGameMode) {
            GameMode gameMode = player.getGameMode();
            if (gameMode.equals(GameMode.CREATIVE) || gameMode.equals(GameMode.SPECTATOR)) {
                return 0;
            }
        }
        if (player.getInventory().contains(Material.MAP, count)) {
            player.getInventory().removeItem(new ItemStack(Material.MAP, count));
            return count;
        }
        return -1;
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

    public static MapCursorCollection toMapCursorCollection(Collection<MapCursor> mapCursors) {
        MapCursorCollection mapCursorCollection = new MapCursorCollection();
        for (MapCursor mapCursor : mapCursors) {
            mapCursorCollection.addCursor(mapCursor);
        }
        return mapCursorCollection;
    }

    public static RayTraceResult rayTraceItemFrame(Location start, Vector direction, double maxDistance) {
        if (maxDistance < 0.0) {
            return null;
        } else {
            Vector startPos = start.toVector();
            Vector dir = direction.clone().normalize().multiply(maxDistance);
            BoundingBox aabb = BoundingBox.of(startPos, startPos).expandDirectional(dir).expand(0.125);
            Collection<Entity> entities = start.getWorld().getNearbyEntities(aabb, e -> e instanceof ItemFrame);
            Entity nearestHitEntity = null;
            RayTraceResult nearestHitResult = null;
            double nearestDistanceSq = Double.MAX_VALUE;

            for (Entity entity : entities) {
                ItemFrame itemFrame = (ItemFrame) entity;
                Vector facing = itemFrame.getFacing().getDirection().normalize();
                Vector opposite = facing.clone().multiply(-1);
                BoundingBox boundingBox = entity.getBoundingBox();
                for (BlockFace blockFace : CARTESIAN_BLOCK_FACES) {
                    Vector expansion = blockFace.getDirection().normalize();
                    if (!expansion.equals(facing) && !expansion.equals(opposite)) {
                        boundingBox.expandDirectional(expansion.multiply(0.125));
                    }
                }
                RayTraceResult hitResult = boundingBox.rayTrace(startPos, direction, maxDistance);
                if (hitResult != null) {
                    double distanceSq = startPos.distanceSquared(hitResult.getHitPosition());
                    if (distanceSq < nearestDistanceSq) {
                        nearestHitEntity = entity;
                        nearestHitResult = hitResult;
                        nearestDistanceSq = distanceSq;
                    }
                }
            }

            return nearestHitEntity == null ? null : new RayTraceResult(nearestHitResult.getHitPosition(), nearestHitEntity, nearestHitResult.getHitBlockFace());
        }
    }

    public static Point2D getTargetPixelOnItemFrame(Vector center, Vector facing, Vector position, Rotation rotation) {
        Vector offset = position.clone().subtract(center);
        if (facing.getBlockX() != 0) {
            offset.setX(0);
        } else if (facing.getBlockY() != 0) {
            offset.setY(0);
        } else {
            offset.setZ(0);
        }
        offset.rotateAroundAxis(facing, Math.toRadians(rotation.ordinal() * 90));
        int x;
        int y;
        if (facing.getBlockX() != 0) {
            x = (int) Math.round(offset.getZ() * 256);
            if (facing.getBlockX() > 0) {
                x = -x;
            }
            y = (int) Math.round(offset.getY() * -256);
        } else if (facing.getBlockY() != 0) {
            x = (int) Math.round(offset.getX() * 256);
            y = (int) Math.round(offset.getZ() * 256);
            if (facing.getBlockY() < 0) {
                y = -y;
            }
        } else {
            x = (int) Math.round(offset.getX() * -256);
            if (facing.getBlockZ() > 0) {
                x = -x;
            }
            y = (int) Math.round(offset.getY() * -256);
        }
        return new Point2D(Math.min(Math.max(x, Byte.MIN_VALUE), Byte.MAX_VALUE), Math.min(Math.max(y, Byte.MIN_VALUE), Byte.MAX_VALUE));
    }

    public static Object toNMSMapIcon(MapCursor mapCursor) {
        try {
            Object iChat = mapCursor.getCaption() == null ? null : WrappedChatComponent.fromLegacyText(mapCursor.getCaption()).getHandle();
            return nmsMapIconConstructor.newInstance(toNMSMapIconType(mapCursor.getType()), mapCursor.getX(), mapCursor.getY(), mapCursor.getDirection(), iChat);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Object toNMSMapIconType(MapCursor.Type type) {
        int id = type.getValue();
        if (id < 0 || id >= nmsMapIconTypeEnums.length) {
            return null;
        }
        return nmsMapIconTypeEnums[id];
    }

    public static boolean isRenderOnFrame(MapCursor.Type type) {
        Object nmsType = toNMSMapIconType(type);
        if (nmsType == null) {
            return true;
        }
        nmsMapIconTypeRenderOnFrameField.setAccessible(true);
        try {
            return nmsMapIconTypeRenderOnFrameField.getBoolean(nmsType);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static Future<MapView> createMap(World world) {
        if (Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(Bukkit.createMap(world));
        } else {
            return Bukkit.getScheduler().callSyncMethod(ImageFrame.plugin, () -> Bukkit.createMap(world));
        }
    }

    public static Future<MapView> getMap(int id) {
        if (Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(Bukkit.getMap(id));
        } else {
            return Bukkit.getScheduler().callSyncMethod(ImageFrame.plugin, () -> Bukkit.getMap(id));
        }
    }

    @SuppressWarnings("unchecked")
    public static MutablePair<byte[], ArrayList<MapCursor>> bukkitRenderMap(MapView mapView, Player player) {
        try {
            Object craftMapView = craftMapViewClass.cast(mapView);
            Object craftPlayer = craftPlayerClass.cast(player);
            Object craftRenderData = craftMapViewRenderMethod.invoke(craftMapView, craftPlayer);
            byte[] buffer = (byte[]) craftRenderDataBufferField.get(craftRenderData);
            ArrayList<MapCursor> cursors = (ArrayList<MapCursor>) craftRenderDataCursorsField.get(craftRenderData);
            return new MutablePair<>(buffer, cursors);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return new MutablePair<>(new byte[COLOR_ARRAY_LENGTH], new ArrayList<>());
    }

}
