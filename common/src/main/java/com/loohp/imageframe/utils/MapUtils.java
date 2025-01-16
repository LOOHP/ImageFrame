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

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.nms.NMS;
import com.loohp.imageframe.objectholders.DitheringType;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.ImageMapHitTargetResult;
import com.loohp.imageframe.objectholders.IntPosition;
import com.loohp.imageframe.objectholders.MapPacketSentCallback;
import com.loohp.imageframe.objectholders.MutablePair;
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
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

public class MapUtils {

    @SuppressWarnings("deprecation")
    public static final byte PALETTE_TRANSPARENT = MapPalette.TRANSPARENT;
    @SuppressWarnings("removal")
    public static final byte PALETTE_WHITE = MapPalette.matchColor(255, 255, 255);

    public static final int MAP_WIDTH = 128;

    public static final String GIF_CONTENT_TYPE = "image/gif";
    public static final List<BlockFace> CARTESIAN_BLOCK_FACES = Collections.unmodifiableList(Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN));

    public static World getMainWorld() {
        return Bukkit.getWorlds().get(0);
    }

    public static void sendImageMap(MapView mapView, Collection<? extends Player> players) {
        sendImageMap(mapView.getId(), mapView, -1, players, null);
    }

    public static void sendImageMap(MapView mapView, Collection<? extends Player> players, boolean now) {
        sendImageMap(mapView.getId(), mapView, -1, players, null, now);
    }

    public static void sendImageMap(MapView mapView, Collection<? extends Player> players, MapPacketSentCallback completionCallback) {
        sendImageMap(mapView.getId(), mapView, -1, players);
    }

    public static void sendImageMap(MapView mapView, Collection<? extends Player> players, MapPacketSentCallback completionCallback, boolean now) {
        sendImageMap(mapView.getId(), mapView, -1, players, now);
    }

    public static void sendImageMap(int mapId, MapView mapView, int currentTick, Collection<? extends Player> players) {
        sendImageMap(mapId, mapView, currentTick, players, null);
    }

    public static void sendImageMap(int mapId, MapView mapView, int currentTick, Collection<? extends Player> players, boolean now) {
        sendImageMap(mapId, mapView, currentTick, players, null, now);
    }

    public static void sendImageMap(int mapId, MapView mapView, int currentTick, Collection<? extends Player> players, MapPacketSentCallback completionCallback) {
        sendImageMap(mapId, mapView, currentTick, players, completionCallback, false);
    }

    public static void sendImageMap(int mapId, MapView mapView, int currentTick, Collection<? extends Player> players, MapPacketSentCallback completionCallback, boolean now) {
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
            MutablePair<byte[], Collection<MapCursor>> renderData = currentTick < 0 ? imageMapManager.renderPacketData(mapView, player) : imageMapManager.renderPacketData(mapView, currentTick, player);
            byte[] colors = renderData.getFirst();
            Collection<MapCursor> cursors = renderData.getSecond();
            Object packet = NMS.getInstance().createMapPacket(mapId, colors, cursors);
            if (now) {
                NMS.getInstance().sendPacket(player, packet);
                if (completionCallback != null) {
                    completionCallback.accept(player, mapId, true);
                }
            } else {
                ImageFrame.rateLimitedPacketSendingManager.queue(player, packet, completionCallback == null ? null : (p, r) -> completionCallback.accept(p, mapId, r));
            }
        }
    }

    public static byte[] toMapPaletteBytes(BufferedImage image, DitheringType ditheringType) {
        return ditheringType == null ? DitheringType.NEAREST_COLOR.applyDithering(image) : ditheringType.applyDithering(image);
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

    @SuppressWarnings("SuspiciousNameCombination")
    public static BufferedImage getSubImage(BufferedImage source, int x, int y) {
        int startX = x * MAP_WIDTH;
        int startY = y * MAP_WIDTH;
        int width = source.getWidth() - startX;
        int height = source.getHeight() - startY;
        if (width < MAP_WIDTH || height < MAP_WIDTH) {
            BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_WIDTH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.drawImage(source.getSubimage(startX, startY, width, height), 0, 0, null);
            g.dispose();
            return image;
        }
        return source.getSubimage(startX, startY, MAP_WIDTH, MAP_WIDTH);
    }

    public static boolean areImagesEqual(BufferedImage img1, BufferedImage img2) {
        if (img1 == img2) {
            return true;
        }
        if (img1 == null || img2 == null) {
            return false;
        }
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            return false;
        }
        for (int x = 0; x < img1.getWidth(); x++) {
            for (int y = 0; y < img1.getHeight(); y++) {
                if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static MapView getItemMapView(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().equals(Material.AIR) || !itemStack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof MapMeta)) {
            return null;
        }
        MapMeta mapMeta = (MapMeta) meta;
        if (!mapMeta.hasMapView()) {
            return null;
        }
        return mapMeta.getMapView();
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
        if (player.getInventory().containsAtLeast(new ItemStack(Material.MAP), count)) {
            player.getInventory().removeItem(new ItemStack(Material.MAP, count));
            return count;
        }
        return -1;
    }

    public static void setColors(MapView mapView, byte[] colors) {
        NMS.getInstance().setColors(mapView, colors);
    }

    public static Set<Player> getViewers(MapView mapView) {
        return NMS.getInstance().getViewers(mapView);
    }

    public static MapCursorCollection toMapCursorCollection(Collection<MapCursor> mapCursors) {
        MapCursorCollection mapCursorCollection = new MapCursorCollection();
        for (MapCursor mapCursor : mapCursors) {
            mapCursorCollection.addCursor(mapCursor);
        }
        return mapCursorCollection;
    }

    public static ImageMapHitTargetResult rayTraceTargetImageMap(Player player, double maxDistance) {
        Location location = player.getEyeLocation();
        return rayTraceTargetImageMap(location, location.getDirection(), maxDistance);
    }

    public static ImageMapHitTargetResult rayTraceTargetImageMap(Location start, Vector direction, double maxDistance) {
        RayTraceResult rayTraceResult = rayTraceItemFrame(start, direction, maxDistance);
        if (rayTraceResult == null) {
            return null;
        }
        ItemFrame itemFrame = (ItemFrame) rayTraceResult.getHitEntity();
        if (itemFrame == null) {
            return null;
        }
        Vector hitPosition = rayTraceResult.getHitPosition();
        ItemStack itemStack = itemFrame.getItem();
        if (itemStack == null || itemStack.getType().equals(Material.AIR) || !itemStack.hasItemMeta()) {
            return null;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (!(itemMeta instanceof MapMeta)) {
            return null;
        }
        MapMeta mapMeta = (MapMeta) itemMeta;
        MapView mapView = mapMeta.getMapView();
        if (mapView == null) {
            return null;
        }
        ImageMap imageMap = ImageFrame.imageMapManager.getFromMapView(mapView);
        if (imageMap == null || !imageMap.isValid()) {
            return null;
        }
        IntPosition target = MapUtils.getTargetPixelOnItemFrame(itemFrame.getLocation().toVector(), itemFrame.getFacing().getDirection(), hitPosition, itemFrame.getRotation());
        IntPosition localTarget = new IntPosition((target.getX() + MAP_WIDTH) / 2, (target.getY() + MAP_WIDTH) / 2);
        int mapViewIndex = imageMap.getMapViews().indexOf(mapView);
        int mapViewX = mapViewIndex % imageMap.getWidth();
        int mapViewY = mapViewIndex / imageMap.getWidth();
        IntPosition globalTarget = new IntPosition(localTarget.getX() + (mapViewX * MAP_WIDTH), localTarget.getY() + (mapViewY * MAP_WIDTH));
        return new ImageMapHitTargetResult(itemFrame, imageMap, localTarget, globalTarget);
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

    public static IntPosition getTargetPixelOnItemFrame(Vector center, Vector facing, Vector position, Rotation rotation) {
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
        return new IntPosition(Math.min(Math.max(x, Byte.MIN_VALUE), Byte.MAX_VALUE), Math.min(Math.max(y, Byte.MIN_VALUE), Byte.MAX_VALUE));
    }

    public static Object toNMSMapIcon(MapCursor mapCursor) {
        return NMS.getInstance().toNMSMapIcon(mapCursor);
    }

    public static Object toNMSMapIconType(MapCursor.Type type) {
        return NMS.getInstance().toNMSMapIconType(type);
    }

    public static boolean isRenderOnFrame(MapCursor.Type type) {
        return NMS.getInstance().isRenderOnFrame(type);
    }

    @SuppressWarnings("DataFlowIssue")
    public static Future<MapView> createMap(World world) {
        return FutureUtils.callSyncMethod(() -> {
            int worldNextId = NMS.getInstance().getNextAvailableMapId(world);
            int ifNextId = ImageFrame.imageMapManager.getMaps().stream().flatMap(i -> i.getMapIds().stream()).mapToInt(i -> i).max().orElse(-1) + 1;
            int worldDataNextId;
            File worldDataFolder = new File(world.getWorldFolder(), "data");
            if (worldDataFolder.exists() && worldDataFolder.isDirectory()) {
                worldDataNextId = Arrays.stream(worldDataFolder.listFiles())
                        .map(f -> f.getName())
                        .filter(s -> s.startsWith("map_"))
                        .map(s -> {
                            try {
                                return Integer.parseInt(s.substring("map_".length(), s.indexOf(".")));
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        })
                        .filter(s -> s != null)
                        .mapToInt(i -> i)
                        .max().orElse(-1) + 1;
            } else {
                worldDataNextId = 0;
            }
            int id = Math.max(worldNextId, Math.max(ifNextId, worldDataNextId));
            return NMS.getInstance().getMapOrCreateMissing(world, id);
        });
    }

    public static Future<MapView> getMap(int id) {
        return FutureUtils.callSyncMethod(() -> Bukkit.getMap(id));
    }

    public static Future<MapView> getMapOrCreateMissing(World world, int id) {
        return FutureUtils.callSyncMethod(() -> NMS.getInstance().getMapOrCreateMissing(world, id));
    }

    public static MutablePair<byte[], ArrayList<MapCursor>> bukkitRenderMap(MapView mapView, Player player) {
        return NMS.getInstance().bukkitRenderMap(mapView, player);
    }

}
