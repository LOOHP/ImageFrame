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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ImageMapCacheControlMode<T extends ImageMapCacheControlTask> {

    private static final Map<String, ImageMapCacheControlMode<?>> MODES = new HashMap<>();

    public static final ImageMapCacheControlMode<ImageMapDynamicCacheControlTask> DYNAMIC = register(new ImageMapCacheControlMode<>("DYNAMIC", ImageMapDynamicCacheControlTask.class, ImageMapDynamicCacheControlTask::new));
    public static final ImageMapCacheControlMode<ImageMapManualPersistentCacheControlTask> MANUAL_PERSISTENT = register(new ImageMapCacheControlMode<>("MANUAL_PERSISTENT", ImageMapManualPersistentCacheControlTask.class, ImageMapManualPersistentCacheControlTask::new));

    public static <T extends ImageMapCacheControlTask> ImageMapCacheControlMode<T> register(ImageMapCacheControlMode<T> cacheControlTasks) {
        MODES.put(cacheControlTasks.getIdentifier(), cacheControlTasks);
        return cacheControlTasks;
    }

    public static Map<String, ImageMapCacheControlMode<?>> values() {
        return Collections.unmodifiableMap(MODES);
    }

    public static ImageMapCacheControlMode<?> valueOf(String identifier) {
        return MODES.get(identifier);
    }

    private final String identifier;
    private final Class<T> clazz;
    private final Function<ImageMap, T> constructor;

    public ImageMapCacheControlMode(String identifier, Class<T> clazz, Function<ImageMap, T> constructor) {
        this.identifier = identifier;
        this.clazz = clazz;
        this.constructor = constructor;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public T newInstance(ImageMap imageMap) {
        return constructor.apply(imageMap);
    }

}
