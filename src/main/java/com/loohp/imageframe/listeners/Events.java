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

package com.loohp.imageframe.listeners;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.MapUtils;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

public class Events implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        MapView currentMapView = MapUtils.getItemMapView(currentItem);
        if (currentMapView != null) {
            if (ImageFrame.imageMapManager.isMapDeleted(currentMapView)) {
                event.setCurrentItem(new ItemStack(Material.MAP, currentItem.getAmount()));
            }
        }

        Inventory inventory = event.getView().getTopInventory();
        if (inventory.getType().equals(InventoryType.WORKBENCH)) {
            if (event.getRawSlot() == 0) {
                ItemStack map = event.getView().getItem(5);
                MapView mapView = MapUtils.getItemMapView(map);
                if (mapView == null) {
                    return;
                }
                if (ImageFrame.imageMapManager.getFromMapView(mapView) == null) {
                    return;
                }
                int count = 0;
                for (int i = 1; i <= 9; i++) {
                    if (i == 5) {
                        continue;
                    }
                    ItemStack itemStack = event.getView().getItem(i);
                    if (itemStack != null && itemStack.getType().equals(Material.PAPER)) {
                        count++;
                    }
                }
                if (count >= 8) {
                    event.setResult(Event.Result.DENY);
                }
            }
        } else if (inventory.getType().equals(InventoryType.CARTOGRAPHY)) {
            if (event.getRawSlot() == 2) {
                ItemStack map = event.getView().getItem(0);
                MapView mapView = MapUtils.getItemMapView(map);
                if (mapView == null) {
                    return;
                }
                if (ImageFrame.imageMapManager.getFromMapView(mapView) == null) {
                    return;
                }
                ItemStack item = event.getView().getItem(1);
                if (item == null) {
                    return;
                }
                if (item.getType().equals(Material.PAPER) || item.getType().equals(Material.GLASS_PANE)) {
                    event.setResult(Event.Result.DENY);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerSwitchSlot(PlayerItemHeldEvent event) {
        Inventory inventory = event.getPlayer().getInventory();
        int slot = event.getNewSlot();
        ItemStack currentItem = inventory.getItem(slot);
        MapView currentMapView = MapUtils.getItemMapView(currentItem);
        if (currentMapView != null) {
            if (ImageFrame.imageMapManager.isMapDeleted(currentMapView)) {
                inventory.setItem(slot, new ItemStack(Material.MAP, currentItem.getAmount()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction().equals(Action.PHYSICAL)) {
            return;
        }
        EntityEquipment equipment = event.getPlayer().getEquipment();
        EquipmentSlot hand = event.getHand();
        ItemStack currentItem = equipment.getItem(hand);
        MapView currentMapView = MapUtils.getItemMapView(currentItem);
        if (currentMapView != null) {
            if (ImageFrame.imageMapManager.isMapDeleted(currentMapView)) {
                equipment.setItem(hand, new ItemStack(Material.MAP, currentItem.getAmount()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        EntityEquipment equipment = event.getPlayer().getEquipment();
        EquipmentSlot hand = event.getHand();
        ItemStack currentItem = equipment.getItem(hand);
        MapView currentMapView = MapUtils.getItemMapView(currentItem);
        if (currentMapView != null) {
            if (ImageFrame.imageMapManager.isMapDeleted(currentMapView)) {
                equipment.setItem(hand, new ItemStack(Material.MAP, currentItem.getAmount()));
            }
        }
    }

}
