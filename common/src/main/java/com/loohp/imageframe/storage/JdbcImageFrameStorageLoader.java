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
import java.util.Map;

public class JdbcImageFrameStorageLoader implements ImageFrameStorageLoader<JdbcImageFrameStorage> {

    private static final Key IDENTIFIER = Key.key("imageframe", "jdbc");
    private static final String[] REQUIRED_OPTIONS = new String[] {"JdbcUrl", "Username", "Password"};
    private static final String[] OPTIONAL_OPTIONS = new String[] {"ActivePollInterval"};

    @Override
    public Key getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String[] getRequiredOptions() {
        return REQUIRED_OPTIONS;
    }

    @Override
    public String[] getOptionalOptions() {
        return OPTIONAL_OPTIONS;
    }

    @Override
    public JdbcImageFrameStorage create(File dataFolder, Map<String, String> options) {
        String jdbcUrl = options.get("JdbcUrl");
        String username = options.get("Username");
        String password = options.get("Password");
        int activePollInterval = 100;
        try {
            activePollInterval = Integer.parseInt(options.get("ActivePollInterval")) * 20;
        } catch (NumberFormatException ignore) {
        }
        if (jdbcUrl == null || username == null || password == null) {
            throw new IllegalArgumentException("Missing database details");
        }
        return new JdbcImageFrameStorage(new File(dataFolder, "data"), jdbcUrl, username, password, activePollInterval);
    }
}
