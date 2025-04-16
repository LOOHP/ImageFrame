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
import com.loohp.imageframe.nms.NMS;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class RateLimitedPacketSendingManager implements Listener {

    private final Map<Player, Long> loginTime;
    private final Map<Player, Queue<ScheduleEntry>> playerPacketQueue;
    private final ExecutorService packetSendingService;

    public RateLimitedPacketSendingManager() {
        this.loginTime = new ConcurrentHashMap<>();
        this.playerPacketQueue = new ConcurrentHashMap<>();
        this.packetSendingService = Executors.newFixedThreadPool(4);
        Bukkit.getPluginManager().registerEvents(this, ImageFrame.plugin);
        Scheduler.runTaskTimerAsynchronously(ImageFrame.plugin, () -> run(), 0, 1);
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerPacketQueue.put(player, new ConcurrentLinkedQueue<>());
        }
    }

    public boolean queue(Player player, Object packet, BiConsumer<Player, Boolean> completionCallback) {
        Queue<ScheduleEntry> queue = playerPacketQueue.get(player);
        if (queue != null) {
            return queue.add(new ScheduleEntry(packet, completionCallback));
        }
        if (completionCallback != null) {
            completionCallback.accept(player, false);
        }
        return false;
    }

    private void run() {
        int rateLimit = ImageFrame.rateLimit;
        long now = System.currentTimeMillis();
        for (Map.Entry<Player, Queue<ScheduleEntry>> entry : playerPacketQueue.entrySet()) {
            Player player = entry.getKey();
            if (now - loginTime.getOrDefault(player, now) < 500) {
                continue;
            }
            Queue<ScheduleEntry> queue = entry.getValue();
            for (int counter = 0; rateLimit < 0 || counter < rateLimit; counter++) {
                ScheduleEntry scheduleEntry = queue.poll();
                if (scheduleEntry == null) {
                    break;
                }
                packetSendingService.execute(() -> {
                    NMS.getInstance().sendPacket(player, scheduleEntry.getPacket());
                    BiConsumer<Player, Boolean> completionCallback = scheduleEntry.getCompletionCallback();
                    if (completionCallback != null) {
                        completionCallback.accept(player, true);
                    }
                });
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loginTime.put(player, System.currentTimeMillis());
        playerPacketQueue.put(player, new ConcurrentLinkedQueue<>());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        loginTime.remove(player);
        playerPacketQueue.remove(player);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(ImageFrame.plugin)) {
            packetSendingService.shutdown();
        }
    }

    public static class ScheduleEntry {

        private final Object packet;
        private final BiConsumer<Player, Boolean> completionCallback;

        public ScheduleEntry(Object packet, BiConsumer<Player, Boolean> completionCallback) {
            this.packet = packet;
            this.completionCallback = completionCallback;
        }

        public Object getPacket() {
            return packet;
        }

        public BiConsumer<Player, Boolean> getCompletionCallback() {
            return completionCallback;
        }
    }
}
