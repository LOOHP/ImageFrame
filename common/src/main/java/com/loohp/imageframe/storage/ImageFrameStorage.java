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

import com.google.gson.JsonObject;
import com.loohp.imageframe.objectholders.IFPlayerManager;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.ImageMapManager;
import com.loohp.imageframe.objectholders.LazyBufferedImageSource;
import com.loohp.imageframe.objectholders.MutablePair;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

public interface ImageFrameStorage extends AutoCloseable {

    UUID getInstanceId();

    ImageFrameStorageLoader<?> getLoader();

    LazyBufferedImageSource getSource(int imageIndex, String fileName);

    Set<Integer> getAllImageIndexes();

    boolean hasImageMapData(int imageIndex);

    JsonObject loadImageMapData(int imageIndex) throws IOException;

    void prepareImageIndex(ImageMap map, IntConsumer imageIndexSetter) throws Exception;

    void deleteMap(int imageIndex);

    List<MutablePair<String, Future<? extends ImageMap>>> loadMaps(ImageMapManager manager, Set<Integer> deletedMapIds, IFPlayerManager ifPlayerManager);

    void saveImageMapData(int imageIndex, JsonObject json) throws IOException;

    Set<Integer> loadDeletedMaps();

    void saveDeletedMaps(Set<Integer> deletedMapIds);

    JsonObject loadPlayerData(IFPlayerManager manager, UUID uuid);

    void savePlayerData(UUID uuid, JsonObject json) throws IOException;

    Set<UUID> getAllSavedPlayerData();

    @Override
    void close();
}
