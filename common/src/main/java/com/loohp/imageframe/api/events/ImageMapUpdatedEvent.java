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

package com.loohp.imageframe.api.events;

import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ImageMapUpdatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    private final ImageMap imageMap;

    public ImageMapUpdatedEvent(ImageMap imageMap) {
        super(!Scheduler.isPrimaryThread());
        this.imageMap = imageMap;
    }

    public ImageMap getImageMap() {
        return imageMap;
    }

    public HandlerList getHandlers() {
        return HANDLERS;
    }

}
