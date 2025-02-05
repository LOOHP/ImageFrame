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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ImageMapAccessControl {

    public static final UUID EVERYONE = new UUID(-1, -1);

    private final ImageMap imageMap;
    private final Map<UUID, ImageMapAccessPermissionType> permissions;

    public ImageMapAccessControl(ImageMap imageMap, Map<UUID, ImageMapAccessPermissionType> hasAccess) {
        this.imageMap = imageMap;
        this.permissions = new ConcurrentHashMap<>(hasAccess);
    }

    public ImageMapAccessPermissionType getPermission(UUID player) {
        if (player.equals(imageMap.getCreator())) {
            return ImageMapAccessPermissionType.ALL;
        }
        return permissions.get(player);
    }

    public ImageMapAccessPermissionType getPermissionForEveryone() {
        return getPermission(EVERYONE);
    }

    public Map<UUID, ImageMapAccessPermissionType> getPermissions() {
        return Collections.unmodifiableMap(permissions);
    }

    public boolean hasPermission(UUID player, ImageMapAccessPermissionType permissionType) {
        if (permissionType == null) {
            return true;
        }
        if (player.equals(imageMap.getCreator())) {
            return true;
        }
        if (!player.equals(EVERYONE) && hasPermissionForEveryone(permissionType)) {
            return true;
        }
        ImageMapAccessPermissionType type = permissions.get(player);
        if (type == null) {
            return false;
        }
        return type.containsPermission(permissionType);
    }

    public boolean hasPermissionForEveryone(ImageMapAccessPermissionType permissionType) {
        return hasPermission(EVERYONE, permissionType);
    }

    public void setPermissionForEveryone(ImageMapAccessPermissionType permissionType) throws Exception {
        setPermission(EVERYONE, permissionType);
    }

    protected void setPermissionForEveryoneWithoutSave(ImageMapAccessPermissionType permissionType) {
        setPermissionWithoutSave(EVERYONE, permissionType);
    }

    public void setPermission(UUID player, ImageMapAccessPermissionType permissionType) throws Exception {
        setPermission(player, permissionType, true);
    }

    protected void setPermissionWithoutSave(UUID player, ImageMapAccessPermissionType permissionType) {
        try {
            setPermission(player, permissionType, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setPermission(UUID player, ImageMapAccessPermissionType permissionType, boolean save) throws Exception {
        if (player.equals(imageMap.getCreator())) {
            return;
        }
        if (permissionType == null) {
            permissions.remove(player);
        } else {
            permissions.put(player, permissionType);
        }
        if (save) {
            imageMap.save();
        }
    }

}
