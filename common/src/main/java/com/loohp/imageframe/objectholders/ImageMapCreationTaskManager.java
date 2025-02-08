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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.loohp.imageframe.utils.ThrowingSupplier;

import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ImageMapCreationTaskManager {

    private final ThreadPoolExecutor executor;
    private final Set<UUID> creatorsInQueue;
    private final Queue<ImageMapCreationTask<?>> taskInQueue;

    public ImageMapCreationTaskManager(int parallelCount) {
        ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat("ImageFrame Image Map Creation Thread #%d").build();
        this.executor = new ThreadPoolExecutor(parallelCount, parallelCount, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), factory);
        this.creatorsInQueue = ConcurrentHashMap.newKeySet();
        this.taskInQueue = new ConcurrentLinkedQueue<>();
    }

    public <T> ImageMapCreationTask<T> enqueue(UUID creator, String imageMapName, int imageMapWidth, int imageMapHeight, ThrowingSupplier<T> creationTask) throws EnqueueRejectedException {
        if (!creatorsInQueue.add(creator)) {
            throw new EnqueueRejectedException("Creator " + creator + " already in queue");
        }
        return new ImageMapCreationTask<>(this, creator, creationTask, executor, imageMapName, imageMapWidth, imageMapHeight);
    }

    public boolean isCreatorInQueue(UUID creator) {
        return creatorsInQueue.contains(creator);
    }

    protected Set<UUID> getCreatorsInQueue() {
        return creatorsInQueue;
    }

    protected Queue<ImageMapCreationTask<?>> getTaskInQueue() {
        return taskInQueue;
    }

    public int getPositionInQueue(ImageMapCreationTask<?> creationTask) {
        int index = 0;
        for (ImageMapCreationTask<?> queuedTask : taskInQueue) {
            if (creationTask.getCreationTaskId().equals(queuedTask.getCreationTaskId())) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public int getPositionInQueue(UUID creator) {
        int index = 0;
        for (ImageMapCreationTask<?> queuedTask : taskInQueue) {
            if (creator.equals(queuedTask.getCreator())) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public static class EnqueueRejectedException extends Exception {

        public EnqueueRejectedException() {
            super();
        }

        public EnqueueRejectedException(String message) {
            super(message);
        }

        public EnqueueRejectedException(String message, Throwable cause) {
            super(message, cause);
        }

        public EnqueueRejectedException(Throwable cause) {
            super(cause);
        }

    }

}
