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

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.ItemFrameUtils;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
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
import org.bukkit.map.MapView;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;
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
    private final Set<Player> cancelOffhand;
    private final Map<Player, ItemFrame> activeFirstCorners;
    private final Map<CommandSender, SelectedItemFrameResult> confirmedSelection;

    public ItemFrameSelectionManager() {
        this.inSelectionMode = ConcurrentHashMap.newKeySet();
        this.cancelOffhand = ConcurrentHashMap.newKeySet();
        this.activeFirstCorners = new ConcurrentHashMap<>();
        this.confirmedSelection = new ConcurrentHashMap<>();
        Bukkit.getPluginManager().registerEvents(this, ImageFrame.plugin);
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
    }

    public void setInSelection(CommandSender sender, boolean value) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (value) {
                confirmedSelection.remove(player);
                activeFirstCorners.remove(player);
                inSelectionMode.add(player);
            } else {
                activeFirstCorners.remove(player);
                inSelectionMode.remove(player);
            }
        }
    }

    public boolean isInSelection(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return false;
        }
        return inSelectionMode.contains((Player) sender);
    }

    public SelectedItemFrameResult getConfirmedSelections(CommandSender sender) {
        return confirmedSelection.get(sender);
    }

    public SelectedItemFrameResult clearConfirmedSelections(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            activeFirstCorners.remove(player);
            inSelectionMode.remove(player);
        }
        return confirmedSelection.remove(sender);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearConfirmedSelections(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        boolean isOffhand = event.getHand().equals(EquipmentSlot.OFF_HAND);
        if (isOffhand && cancelOffhand.contains(player)) {
            event.setCancelled(true);
            return;
        }
        if (!inSelectionMode.contains(player)) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame)) {
            return;
        }
        event.setCancelled(true);
        if (isOffhand) {
            return;
        }
        ItemFrame selection = activeFirstCorners.remove(player);
        if (selection == null) {
            activeFirstCorners.put(player, (ItemFrame) entity);
            player.sendMessage(ImageFrame.messageSelectionCorner1);
        } else {
            player.sendMessage(ImageFrame.messageSelectionCorner2);
            inSelectionMode.remove(player);
            cancelOffhand.add(player);
            Scheduler.runTaskLater(ImageFrame.plugin, () -> cancelOffhand.remove(player), 1, player);
            SelectedItemFrameResult result = getSelectedItemFrames(player.getLocation().getYaw(), selection, (ItemFrame) entity);
            if (result == null) {
                player.sendMessage(ImageFrame.messageSelectionInvalid);
            } else if (result.isOverSize()) {
                player.sendMessage(ImageFrame.messageSelectionOversize.replace("{MaxSize}", ImageFrame.mapMaxSize + ""));
            } else {
                confirmedSelection.put(player, result);
                player.sendMessage(ImageFrame.messageSelectionSuccess.replace("{Width}", result.getWidth() + "").replace("{Height}", result.getHeight() + ""));
            }
        }
    }

    public boolean applyDirectItemFrameSelection(CommandSender sender, float yaw, BlockPosition pos1, BlockPosition pos2) {
        Collection<Entity> pos1Entity = pos1.getWorld().getNearbyEntities(pos1.toBoundingBox());
        Collection<Entity> pos2Entity = pos2.getWorld().getNearbyEntities(pos2.toBoundingBox());
        MutablePair<ItemFrame, ItemFrame> pair = pos1Entity.stream().filter(e -> e instanceof ItemFrame).map(e -> {
            ItemFrame i = (ItemFrame) e;
            BlockFace facing = i.getAttachedFace();
            return pos2Entity.stream()
                    .filter(e2 -> e2 instanceof ItemFrame && ((ItemFrame) e2).getAttachedFace().equals(facing))
                    .findFirst()
                    .map(e2 -> new MutablePair<>(i, (ItemFrame) e2))
                    .orElse(null);
        }).filter(p -> p != null).findFirst().orElse(null);
        if (pair == null) {
            sender.sendMessage(ImageFrame.messageSelectionInvalid);
            return false;
        }
        SelectedItemFrameResult result = getSelectedItemFrames(yaw, pair.getFirst(), pair.getSecond());
        if (result == null) {
            sender.sendMessage(ImageFrame.messageSelectionInvalid);
        } else if (result.isOverSize()) {
            sender.sendMessage(ImageFrame.messageSelectionOversize.replace("{MaxSize}", ImageFrame.mapMaxSize + ""));
        } else {
            confirmedSelection.put(sender, result);
            sender.sendMessage(ImageFrame.messageSelectionSuccess.replace("{Width}", result.getWidth() + "").replace("{Height}", result.getHeight() + ""));
            return true;
        }
        return false;
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
            return SelectedItemFrameResult.result(Collections.singletonList(left), 1, 1, rotation, false);
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
        boolean oversize = Math.round(boundingBox.getVolume()) > ImageFrame.mapMaxSize;
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
            rotation = ItemFrameUtils.getClosestMapRotation(yaw);
            if (ItemFrameUtils.isOnCeiling(left)) {
                switch (rotation) {
                    case NONE:
                    case FLIPPED:
                        comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ()).reversed();
                        comparator = comparator.thenComparingDouble(each -> each.getLocation().getX());
                        heightFunction = each -> each.getLocation().getBlockZ();
                        break;
                    case CLOCKWISE_45:
                    case FLIPPED_45:
                        comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX());
                        comparator = comparator.thenComparingDouble(each -> each.getLocation().getZ());
                        heightFunction = each -> each.getLocation().getBlockX();
                        rotation = rotation.rotateCounterClockwise().rotateCounterClockwise();
                        break;
                    case CLOCKWISE:
                    case COUNTER_CLOCKWISE:
                        comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ());
                        comparator = comparator.thenComparing(Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX()).reversed());
                        heightFunction = each -> each.getLocation().getBlockZ();
                        break;
                    case CLOCKWISE_135:
                    case COUNTER_CLOCKWISE_45:
                        comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX()).reversed();
                        comparator = comparator.thenComparing(Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ()).reversed());
                        heightFunction = each -> each.getLocation().getBlockX();
                        rotation = rotation.rotateCounterClockwise().rotateCounterClockwise();
                        break;
                    default:
                        throw new RuntimeException("invalid rotation for maps on item frames: " + rotation.name());
                }
            } else {
                switch (rotation) {
                    case NONE:
                    case FLIPPED:
                        comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ());
                        comparator = comparator.thenComparingDouble(each -> each.getLocation().getX());
                        heightFunction = each -> each.getLocation().getBlockZ();
                        break;
                    case CLOCKWISE_45:
                    case FLIPPED_45:
                        comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX()).reversed();
                        comparator = comparator.thenComparingDouble(each -> each.getLocation().getZ());
                        heightFunction = each -> each.getLocation().getBlockX();
                        break;
                    case CLOCKWISE:
                    case COUNTER_CLOCKWISE:
                        comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ()).reversed();
                        comparator = comparator.thenComparing(Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX()).reversed());
                        heightFunction = each -> each.getLocation().getBlockZ();
                        break;
                    case CLOCKWISE_135:
                    case COUNTER_CLOCKWISE_45:
                        comparator = Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getX());
                        comparator = comparator.thenComparing(Comparator.comparingDouble((ItemFrame each) -> each.getLocation().getZ()).reversed());
                        heightFunction = each -> each.getLocation().getBlockX();
                        break;
                    default:
                        throw new RuntimeException("invalid rotation for maps on item frames: " + rotation.name());
                }
            }
        }
        List<ItemFrame> itemFrames = world.getNearbyEntities(boundingBox, e -> e instanceof ItemFrame && e.getFacing().equals(facing)).stream()
                .map(each -> (ItemFrame) each)
                .sorted(comparator).collect(Collectors.toList());
        IntSummaryStatistics statistics = itemFrames.stream().mapToInt(heightFunction).summaryStatistics();
        int height = statistics.getMax() - statistics.getMin() + 1;
        int width = itemFrames.size() / height;
        if (itemFrames.size() != width * height) {
            return null;
        }
        return SelectedItemFrameResult.result(itemFrames, width, height, rotation, oversize);
    }

    public static class SelectedItemFrameResult {

        public static SelectedItemFrameResult result(List<ItemFrame> itemFrames, int width, int height, Rotation rotation, boolean overSize) {
            return new SelectedItemFrameResult(itemFrames, width, height, rotation, overSize);
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

        public List<MapView> getMapViews() {
            return itemFrames.stream().map(each -> MapUtils.getItemMapView(each.getItem())).collect(Collectors.toList());
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
