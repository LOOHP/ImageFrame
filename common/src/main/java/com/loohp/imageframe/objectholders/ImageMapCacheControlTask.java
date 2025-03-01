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

public class ImageMapCacheControlTask implements Runnable, AutoCloseable {
    
    private final ImageMap imageMap;

    private int noViewerCounts;
    private boolean locked;

    private final Scheduler.ScheduledTask task;

    public ImageMapCacheControlTask(ImageMap imageMap) {
        this.imageMap = imageMap;
        this.noViewerCounts = 0;
        this.locked = false;

        this.task = Scheduler.runTaskTimerAsynchronously(ImageFrame.plugin, this, 0, 1);
    }

    public ImageMap getImageMap() {
        return imageMap;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public void run() {
        if (locked) {
            return;
        }
        if (imageMap.hasViewers()) {
            noViewerCounts = 0;
            if (!imageMap.hasColorCached()) {
                imageMap.loadColorCache();
            }
        } else {
            if (noViewerCounts++ > 200 && imageMap.hasColorCached()) {
                imageMap.unloadColorCache();
            }
        }
    }

    @Override
    public void close() {
        task.cancel();
    }
}
