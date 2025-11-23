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

import com.loohp.imageframe.objectholders.IFPlayer;
import com.loohp.imageframe.objectholders.IFPlayerManager;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.ImageMapManager;

import java.util.UUID;

public class StorageMigrator implements AutoCloseable {

    private final ImageMapManager imageMapManager;
    private final IFPlayerManager ifPlayerManager;
    private final ImageFrameStorage targetStorage;

    public StorageMigrator(ImageMapManager imageMapManager, IFPlayerManager ifPlayerManager, ImageFrameStorage targetStorage) {
        this.imageMapManager = imageMapManager;
        this.ifPlayerManager = ifPlayerManager;
        this.targetStorage = targetStorage;
    }

    public boolean isTargetEmpty() {
        return targetStorage.getAllImageIndexes().isEmpty() && targetStorage.loadDeletedMaps().isEmpty() && targetStorage.getAllSavedPlayerData().isEmpty();
    }

    public void migrateImageMaps() throws Exception {
        for (ImageMap imageMap : imageMapManager.getMaps()) {
            imageMap.save(targetStorage, true);
        }
    }

    public void migrateDeletedMaps() {
        targetStorage.saveDeletedMaps(imageMapManager.getDeletedMapIds());
    }

    public void migrateIFPlayers() throws Exception {
        for (UUID uuid : ifPlayerManager.getStorage().getAllSavedPlayerData()) {
            IFPlayer player = IFPlayer.load(ifPlayerManager, ifPlayerManager.getStorage().loadPlayerData(ifPlayerManager, uuid));
            player.save(targetStorage);
        }
    }

    @Override
    public void close() {
        targetStorage.close();
    }
}
