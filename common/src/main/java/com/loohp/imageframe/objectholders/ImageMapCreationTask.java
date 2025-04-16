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
import com.loohp.imageframe.utils.ThrowingSupplier;
import com.loohp.platformscheduler.ScheduledTask;
import com.loohp.platformscheduler.Scheduler;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ImageMapCreationTask<T> extends CompletableFuture<T> implements Runnable {

    private final ImageMapCreationTaskManager manager;
    private final UUID creationTaskId;
    private final UUID creator;
    private final ThrowingSupplier<T> creationTask;

    private final ScheduledTask monitorTask;
    private final String queuingMessageTemplate;
    private final String processingMessageTemplate;

    private final AtomicBoolean queuing;
    private final AtomicInteger tick;
    private final AtomicInteger processingStart;

    private final Future<?> submission;

    public ImageMapCreationTask(ImageMapCreationTaskManager manager, UUID creator, ThrowingSupplier<T> creationTask, ExecutorService executor, String imageMapName, int imageMapWidth, int imageMapHeight) {
        this.manager = manager;
        this.creationTaskId = UUID.randomUUID();
        this.creator = creator;
        this.creationTask = creationTask;

        this.manager.getTaskInQueue().add(this);

        this.queuingMessageTemplate = ImageFrame.messageImageMapQueuedActionBar
                .replace("{Name}", imageMapName)
                .replace("{Width}", imageMapWidth + "")
                .replace("{Height}", imageMapHeight + "");
        this.processingMessageTemplate = ImageFrame.messageImageMapProcessingActionBar
                .replace("{Name}", imageMapName)
                .replace("{Width}", imageMapWidth + "")
                .replace("{Height}", imageMapHeight + "");
        this.monitorTask = Scheduler.runTaskTimerAsynchronously(ImageFrame.plugin, new MonitorTask(), 0, 10);

        this.tick = new AtomicInteger(0);
        this.processingStart = new AtomicInteger(-1);
        this.queuing = new AtomicBoolean(true);

        this.submission = executor.submit(this);
    }

    public ImageMapCreationTaskManager getManager() {
        return manager;
    }

    public UUID getCreator() {
        return creator;
    }

    public UUID getCreationTaskId() {
        return creationTaskId;
    }

    public boolean isQueuing() {
        return queuing.get();
    }

    public int getPositionInQueue() {
        return manager.getPositionInQueue(this);
    }

    @Override
    public void run() {
        this.manager.getTaskInQueue().remove(this);
        this.queuing.set(false);
        try {
            complete(creationTask.get());
        } catch (Throwable e) {
            completeExceptionally(e);
        }
    }

    public boolean isCompleted() {
        return monitorTask.isCancelled();
    }

    @SuppressWarnings("deprecation")
    public void complete(String message) {
        if (!isCompleted()) {
            monitorTask.cancel();
            manager.getCreatorsInQueue().remove(creator);
            Player player = Bukkit.getPlayer(creator);
            if (player != null) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
            }
        }
    }

    private class MonitorTask implements Runnable {

        @SuppressWarnings("deprecation")
        @Override
        public void run() {
            processingStart.updateAndGet(currentProcessingStart -> {
                int currentTick = tick.get();
                if (currentProcessingStart < 0) {
                    if (!queuing.get()) {
                        return currentTick;
                    }
                } else {
                    if (currentTick - currentProcessingStart > (ImageFrame.maxProcessingTime * 20)) {
                        submission.cancel(true);
                    }
                }
                return currentProcessingStart;
            });
            Player player = Bukkit.getPlayer(creator);
            if (player != null) {
                String message;
                if (queuing.get()) {
                    int position = getPositionInQueue() + 1;
                    message = queuingMessageTemplate.replace("{Position}", String.valueOf(position));
                } else {
                    int dots = tick.getAndIncrement() % 4;
                    StringBuilder dotString = new StringBuilder();
                    for (int i = 0; i < dots; i++) {
                        dotString.append(".");
                    }
                    message = processingMessageTemplate.replace("{Dots}", dotString.toString());
                }
                if (!message.isEmpty()) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                }
            }
        }

    }
}
