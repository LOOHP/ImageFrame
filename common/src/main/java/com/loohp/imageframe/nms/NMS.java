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

package com.loohp.imageframe.nms;

import com.loohp.imageframe.ImageFrame;

import java.lang.reflect.InvocationTargetException;

public class NMS {

    private static NMSWrapper instance;

    @SuppressWarnings("unchecked")
    public synchronized static NMSWrapper getInstance() {
        if (instance != null) {
            return instance;
        }
        try {
            Class<NMSWrapper> nmsImplClass = (Class<NMSWrapper>) Class.forName("com.loohp.imageframe.nms." + ImageFrame.version.name());
            return instance = nmsImplClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            if (ImageFrame.version.isSupported()) {
                throw new RuntimeException("Missing NMSWrapper implementation for version " + ImageFrame.version.name(), e);
            } else {
                throw new RuntimeException("No NMSWrapper implementation for UNSUPPORTED version " + ImageFrame.version.name(), e);
            }
        }
    }

}
