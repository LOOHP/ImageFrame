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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ImageMapAccessPermissionType {

    public static final ImageMapAccessPermissionType GET = new ImageMapAccessPermissionType("GET");
    public static final ImageMapAccessPermissionType ADJUST_PLAYBACK = new ImageMapAccessPermissionType("ADJUST_PLAYBACK", GET);
    public static final ImageMapAccessPermissionType MARKER = new ImageMapAccessPermissionType("MARKER", GET);
    public static final ImageMapAccessPermissionType EDIT = new ImageMapAccessPermissionType("EDIT", MARKER);
    public static final ImageMapAccessPermissionType EDIT_CLONE = new ImageMapAccessPermissionType("EDIT_CLONE", EDIT);
    public static final ImageMapAccessPermissionType ALL = new ImageMapAccessPermissionType("ALL", EDIT_CLONE);

    /**
     * Special case type: All registered ImageMapAccessPermissionTypes inherits this
     */
    public static final ImageMapAccessPermissionType BASE = new ImageMapAccessPermissionType("BASE") {
        @Override
        public boolean containsPermission(ImageMapAccessPermissionType type) {
            return TYPES.containsKey(type.name());
        }
    };

    private static final Map<String, ImageMapAccessPermissionType> TYPES = new HashMap<>();

    static {
        register(GET);
        register(ADJUST_PLAYBACK);
        register(MARKER);
        register(EDIT);
        register(EDIT_CLONE);
        register(ALL);
    }

    public static void register(ImageMapAccessPermissionType type) {
        TYPES.put(type.name(), type);
    }

    public static ImageMapAccessPermissionType valueOf(String name) {
        ImageMapAccessPermissionType type = TYPES.get(name);
        if (type != null) {
            return type;
        }
        throw new IllegalArgumentException(name + " is not a registered ImageMapAccessPermissionType");
    }

    public static Map<String, ImageMapAccessPermissionType> values() {
        return Collections.unmodifiableMap(TYPES);
    }

    private final String name;
    private final Set<ImageMapAccessPermissionType> inheritance;

    public ImageMapAccessPermissionType(String name, ImageMapAccessPermissionType... inheritance) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        this.name = name;
        this.inheritance = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(inheritance)));
    }

    public String name() {
        return name;
    }

    public boolean containsPermission(ImageMapAccessPermissionType type) {
        if (this.equals(type)) {
            return true;
        }
        for (ImageMapAccessPermissionType inheritedType : inheritance) {
            if (!inheritedType.equals(this) && inheritedType.containsPermission(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageMapAccessPermissionType type = (ImageMapAccessPermissionType) o;
        return name.equals(type.name) && inheritance.equals(type.inheritance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, inheritance);
    }
}