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

package com.loohp.imageframe.objectholders;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.ItemFrameUtils;
import org.bukkit.Bukkit;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class ItemFrameSelectionManager implements Listener, AutoCloseable {

    private final Set<Player> inSelectionMode;
    private final Map<Player, ItemFrame> playerFirstCorner;
    private final Map<Player, SelectedItemFrameResult> playerSelection;

    public ItemFrameSelectionManager() {
        this.inSelectionMode = ConcurrentHashMap.newKeySet();
        this.playerFirstCorner = new ConcurrentHashMap<>();
        this.playerSelection = new ConcurrentHashMap<>();
        Bukkit.getPluginManager().registerEvents(this, ImageFrame.plugin);
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
    }

    public void setInSelection(Player player, boolean value) {
        if (value) {
            playerSelection.remove(player);
            playerFirstCorner.remove(player);
            inSelectionMode.add(player);
        } else {
            playerFirstCorner.remove(player);
            inSelectionMode.remove(player);
        }
    }

    public boolean isInSelection(Player player) {
        return inSelectionMode.contains(player);
    }

    public SelectedItemFrameResult getPlayerSelection(Player player) {
        return playerSelection.get(player);
    }

    public SelectedItemFrameResult clearPlayerSelection(Player player) {
        playerFirstCorner.remove(player);
        inSelectionMode.remove(player);
        return playerSelection.remove(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPlayerSelection(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand().equals(EquipmentSlot.OFF_HAND)) {
            return;
        }
        Player player = event.getPlayer();
        if (!inSelectionMode.contains(player)) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame)) {
            return;
        }
        event.setCancelled(true);
        ItemFrame selection = playerFirstCorner.remove(player);
        if (selection == null) {
            playerFirstCorner.put(player, (ItemFrame) entity);
            player.sendMessage(ImageFrame.messageSelectionCorner1);
        } else {
            player.sendMessage(ImageFrame.messageSelectionCorner2);
            inSelectionMode.remove(player);
            SelectedItemFrameResult result = getSelectedItemFrames(player.getLocation().getYaw(), selection, (ItemFrame) entity);
            if (result == null) {
                player.sendMessage(ImageFrame.messageSelectionInvalid);
            } else if (result.isOverSize()) {
                player.sendMessage(ImageFrame.messageSelectionOversize.replace("{MaxSize}", ImageFrame.mapMaxSize + ""));
            } else {
                playerSelection.put(player, result);
                player.sendMessage(ImageFrame.messageSelectionSuccess.replace("{Width}", result.getWidth() + "").replace("{Height}", result.getHeight() + ""));
            }
        }
    }

    public SelectedItemFrameResult getSelectedItemFrames(float yaw, ItemFrame left, ItemFrame right) {
        if (left == null || right == null) {
            return null;
        }
        if (left.equals(right)) {
            Rotation rotation;
            if (ItemFrameUtils.isOnWalls(left)) {
                rotation = Rotation.NONE;
            } else {
                rotation = ItemFrameUtils.getClosestMapRotation(yaw + (ItemFrameUtils.isOnCeiling(left) ? 180F : 0F));
            }
            return SelectedItemFrameResult.result(Collections.singletonList(left), 1, 1, rotation);
        }
        if (!left.getAttachedFace().equals(right.getAttachedFace())) {
            return null;
        }
        Block leftBlock = left.getLocation().getBlock();
        Block rightBlock = right.getLocation().getBlock();
        if (leftBlock.getX() != rightBlock.getX() && leftBlock.getY() != rightBlock.getY() && leftBlock.getZ() != rightBlock.getZ()) {
            return null;
        }
        BoundingBox boundingBox = BoundingBox.of(leftBlock, rightBlock);
        if (Math.round(boundingBox.getVolume()) > ImageFrame.mapMaxSize) {
            return SelectedItemFrameResult.oversize();
        }
        World world = left.getWorld();
        BlockFace facing = left.getFacing();
        Rotation rotation;
        ToIntFunction<ItemFrame> heightFunction;
        Comparator<ItemFrame> comparator;
        if (ItemFrameUtils.isOnWalls(left)) {
            rotation = Rotation.NONE;
            Vector vector = facing.getDirection().rotateAroundY(Math.toRadians(90));
            comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getY()).reversed();
            if (Math.abs(vector.getX()) > 0.000001) {
                if (vector.getX() < 0) {
                    comparator = comparator.thenComparing(Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX()).reversed());
                } else {
                    comparator = comparator.thenComparingDouble(each -> each.getLocation().getX());
                }
            } else {
                if (vector.getZ() < 0) {
                    comparator = comparator.thenComparing(Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ()).reversed());
                } else {
                    comparator = comparator.thenComparingDouble(each -> each.getLocation().getZ());
                }
            }
            heightFunction = each -> each.getLocation().getBlockY();
        } else {
            boolean onCeiling = ItemFrameUtils.isOnCeiling(left);
            rotation = ItemFrameUtils.getClosestMapRotation(yaw + (onCeiling ? 180F : 0F));
            switch (rotation) {
                case NONE:
                case FLIPPED:
                    comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ());
                    if (onCeiling) {
                        comparator = comparator.thenComparing(Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX()).reversed());
                    } else {
                        comparator = comparator.thenComparingDouble(each -> each.getLocation().getX());
                    }
                    heightFunction = each -> each.getLocation().getBlockZ();
                    break;
                case CLOCKWISE_45:
                case FLIPPED_45:
                    comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX()).reversed();
                    if (onCeiling) {
                        comparator = comparator.thenComparing(Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ()).reversed());
                    } else {
                        comparator = comparator.thenComparingDouble(each -> each.getLocation().getZ());
                    }
                    heightFunction = each -> each.getLocation().getBlockX();
                    break;
                case CLOCKWISE:
                case COUNTER_CLOCKWISE:
                    comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ()).reversed();
                    if (onCeiling) {
                        comparator = comparator.thenComparingDouble(each -> each.getLocation().getX());
                    } else {
                        comparator = comparator.thenComparing(Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX()).reversed());
                    }
                    heightFunction = each -> each.getLocation().getBlockZ();
                    break;
                case CLOCKWISE_135:
                case COUNTER_CLOCKWISE_45:
                    comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX());
                    if (onCeiling) {
                        comparator = comparator.thenComparingDouble(each -> each.getLocation().getZ());
                    } else {
                        comparator = comparator.thenComparing(Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ()).reversed());
                    }
                    heightFunction = each -> each.getLocation().getBlockX();
                    break;
                default:
                    throw new RuntimeException("invalid rotation for maps on item frames: " + rotation.name());
            }
        }
        List<ItemFrame> itemFrames = world.getNearbyEntities(boundingBox, e -> e instanceof ItemFrame && e.getFacing().equals(facing)).stream()
                .map(each -> (ItemFrame) each)
                .sorted(comparator).collect(Collectors.toList());
        IntSummaryStatistics statistics = itemFrames.stream().mapToInt(heightFunction).summaryStatistics();
        int height = statistics.getMax() - statistics.getMin() + 1;
        int width = itemFrames.size() / height;
        return SelectedItemFrameResult.result(itemFrames, width, height, rotation);
    }

    public static class SelectedItemFrameResult {

        public static SelectedItemFrameResult oversize() {
            return new SelectedItemFrameResult(null, -1, -1, null, true);
        }

        public static SelectedItemFrameResult result(List<ItemFrame> itemFrames, int width, int height, Rotation rotation) {
            return new SelectedItemFrameResult(itemFrames, width, height, rotation, false);
        }

        private final List<ItemFrame> itemFrames;
        private final int width;
        private final int height;
        private final Rotation rotation;
        private final boolean overSize;

        private SelectedItemFrameResult(List<ItemFrame> itemFrames, int width, int height, Rotation rotation, boolean overSize) {
            this.itemFrames = itemFrames;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.overSize = overSize;
        }

        public List<ItemFrame> getItemFrames() {
            return itemFrames;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public Rotation getRotation() {
            return rotation;
        }

        public boolean isOverSize() {
            return overSize;
        }
    }
}
