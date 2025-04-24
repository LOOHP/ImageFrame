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

import java.util.concurrent.atomic.AtomicBoolean;

public class ImageMapManualPersistentCacheControlTask implements ImageMapCacheControlTask {

    private final ImageMap imageMap;
    private final AtomicBoolean closed;

    public ImageMapManualPersistentCacheControlTask(ImageMap imageMap) {
        this.imageMap = imageMap;
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public ImageMap getImageMap() {
        return imageMap;
    }

    @Override
    public void loadCacheIfManual() {
        imageMap.loadColorCache();
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
