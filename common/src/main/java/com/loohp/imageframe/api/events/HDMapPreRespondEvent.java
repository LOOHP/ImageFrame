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
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;

import static com.loohp.imageframe.utils.ImageUtils.copy;

public class HDMapPreRespondEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    private final int mapId;
    private final ImageMap imageMap;
    private final MapView mapView;

    private boolean requestAccepted;
    private BufferedImage image;

    public HDMapPreRespondEvent(Player player, int mapId, ImageMap imageMap, MapView mapView, boolean requestAccepted, BufferedImage image) {
        super(player, !Scheduler.isPrimaryThread());
        this.player = player;
        this.mapId = mapId;
        this.imageMap = imageMap;
        this.mapView = mapView;
        this.requestAccepted = requestAccepted;
        this.image = image;
    }

    public int getMapId() {
        return mapId;
    }

    public ImageMap getImageMap() {
        return imageMap;
    }

    public MapView getMapView() {
        return mapView;
    }

    public boolean isRequestAccepted() {
        return requestAccepted;
    }

    public void setRequestAccepted(boolean requestAccepted) {
        this.requestAccepted = requestAccepted;
    }

    public BufferedImage getImage() {
        return image == null ? null : copy(image);
    }

    public void setImage(BufferedImage image) {
        this.image = image == null ? null : copy(image);
    }

    public HandlerList getHandlers() {
        return HANDLERS;
    }

}
