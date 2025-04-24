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
import com.loohp.platformscheduler.ScheduledTask;
import com.loohp.platformscheduler.Scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ImageMapDynamicCacheControlTask implements ImageMapCacheControlTask, Runnable {
    
    private final ImageMap imageMap;

    private final AtomicBoolean locked;
    private final AtomicBoolean closed;

    private final AtomicReference<ScheduledTask> task;
    private final AtomicInteger noViewerCounts;

    public ImageMapDynamicCacheControlTask(ImageMap imageMap) {
        this.imageMap = imageMap;
        this.noViewerCounts = new AtomicInteger(0);
        this.locked = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
        this.task = new AtomicReference<>(Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, this, 5));
    }

    @Override
    public ImageMap getImageMap() {
        return imageMap;
    }

    @Override
    public void loadCacheIfManual() {

    }

    public boolean isLocked() {
        return locked.get();
    }

    public void setLocked(boolean locked) {
        this.locked.set(locked);
    }

    @Override
    public void run() {
        if (closed.get()) {
            return;
        }
        if (!locked.get()) {
            if (imageMap.hasViewers()) {
                noViewerCounts.set(0);
                if (!imageMap.hasColorCached()) {
                    imageMap.loadColorCache();
                }
            } else {
                if (noViewerCounts.getAndIncrement() > 200 && imageMap.hasColorCached()) {
                    imageMap.unloadColorCache();
                }
            }
        }
        this.task.set(Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, this, 5));
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        closed.set(true);
        task.updateAndGet(t -> {
            if (t != null) {
                t.cancel();
            }
            return t;
        });
    }
}
