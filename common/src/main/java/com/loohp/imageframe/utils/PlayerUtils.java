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
import com.loohp.imageframe.nms.NMS;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerUtils {

    public static void giveItem(Player player, ItemStack itemStack) {
        giveItem(player, Collections.singletonList(itemStack));
    }

    public static void giveItem(Player player, List<ItemStack> itemStacks) {
        Scheduler.executeOrScheduleSync(ImageFrame.plugin, () -> {
            List<ItemStack> leftovers = NMS.getInstance().giveItems(player, itemStacks);
            if (!leftovers.isEmpty()) {
                Location location = player.getEyeLocation();
                World world = location.getWorld();
                double power = ThreadLocalRandom.current().nextDouble(0.25, 0.35);
                Vector velocity = location.getDirection().multiply(power);
                for (ItemStack stack : leftovers) {
                    world.dropItem(location, stack).setVelocity(velocity);
                }
            }
        }, player);
    }

    public static boolean isInteractionAllowed(Player player, Entity entity) {
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, entity);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @SuppressWarnings("removal")
    public static boolean isDamageAllowed(Player player, Entity entity) {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, entity, EntityDamageEvent.DamageCause.CUSTOM, 1.0);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

}
