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

package com.loohp.imageframe.storage;

import net.kyori.adventure.key.Key;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImageFrameStorageLoaders {

    private static final Map<Key, ImageFrameStorageLoader<?>> LOADERS = new ConcurrentHashMap<>();

    public static final FileImageFrameStorageLoader FILE = register(new FileImageFrameStorageLoader());
    public static final JdbcImageFrameStorageLoader JDBC = register(new JdbcImageFrameStorageLoader());

    public static <T extends ImageFrameStorageLoader<S>, S extends ImageFrameStorage> T register(T loader) {
        LOADERS.put(loader.getIdentifier(), loader);
        return loader;
    }

    public static Collection<ImageFrameStorageLoader<?>> getRegisteredLoaders() {
        return Collections.unmodifiableCollection(LOADERS.values());
    }

    public static ImageFrameStorageLoader<?> getLoader(Key type) {
        ImageFrameStorageLoader<?> loader = LOADERS.get(type);
        if (loader == null) {
            throw new IllegalStateException("Unknown loader type " + type.asString());
        }
        return loader;
    }

    public static ImageFrameStorage create(Key type, File dataFolder, Map<String, String> options) {
        return getLoader(type).create(dataFolder, options);
    }

}
