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

package com.loohp.imageframe.migration;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PluginMigrationRegistry {

    private static final Map<String, ExternalPluginMigration> REGISTERED_MIGRATIONS = new ConcurrentHashMap<>();

    public static final DrMapMigration DR_MAP_MIGRATION = register(new DrMapMigration());
    public static final ImageOnMapMigration IMAGE_ON_MAP_MIGRATION = register(new ImageOnMapMigration());
    public static final ImageMapMigration IMAGE_MAP_MIGRATION = register(new ImageMapMigration());

    public static Set<String> getPluginNames() {
        return REGISTERED_MIGRATIONS.keySet();
    }

    public static ExternalPluginMigration getMigration(String plugin) {
        ExternalPluginMigration migration = REGISTERED_MIGRATIONS.get(plugin);
        if (migration != null) {
            return migration;
        }
        return REGISTERED_MIGRATIONS.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(plugin))
                .findFirst()
                .map(e -> e.getValue())
                .orElse(null);
    }

    public static <T extends ExternalPluginMigration> T register(T migration) {
        REGISTERED_MIGRATIONS.put(migration.externalPluginName(), migration);
        return migration;
    }

}
