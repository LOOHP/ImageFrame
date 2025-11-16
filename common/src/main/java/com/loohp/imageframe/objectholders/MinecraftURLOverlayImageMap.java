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

import com.loohp.imageframe.utils.MapUtils;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MinecraftURLOverlayImageMap extends URLStaticImageMap {

    protected MinecraftURLOverlayImageMap(ImageMapManager manager, ImageMapLoader<?, ?> loader, int imageIndex, String name, String url, FileLazyMappedBufferedImage[] cachedImages, List<MapView> mapViews, List<Integer> mapIds, List<Map<String, MapCursor>> mapMarkers, int width, int height, DitheringType ditheringType, UUID creator, Map<UUID, ImageMapAccessPermissionType> hasAccess, long creationTime) {
        super(manager, loader, imageIndex, name, url, cachedImages, mapViews, mapIds, mapMarkers, width, height, ditheringType, creator, hasAccess, creationTime);
    }

    @Override
    public BufferedImage getOriginalImage(int mapId) {
        return null;
    }

    @Override
    public ImageMap deepClone(String name, UUID creator) throws Exception {
        MinecraftURLOverlayImageMap imageMap = ((MinecraftURLOverlayImageMapLoader) loader).create(new MinecraftURLOverlayImageMapCreateInfo(manager, name, url, mapViews, width, height, ditheringType, creator)).get();
        List<Map<String, MapCursor>> newList = imageMap.getMapMarkers();
        int i = 0;
        for (Map<String, MapCursor> map : getMapMarkers()) {
            Map<String, MapCursor> newMap = newList.get(i++);
            for (Map.Entry<String, MapCursor> entry : map.entrySet()) {
                MapCursor mapCursor = entry.getValue();
                newMap.put(entry.getKey(), new MapCursor(mapCursor.getX(), mapCursor.getY(), mapCursor.getDirection(), mapCursor.getType(), mapCursor.isVisible(), mapCursor.getCaption()));
            }
        }
        return imageMap;
    }

    @Override
    public boolean trackDeletedMaps() {
        return false;
    }

    @Override
    public void stop() {
        for (MapView mapView : mapViews) {
            for (MapRenderer mapRenderer : mapView.getRenderers()) {
                if (mapRenderer.getClass().getName().equals(MinecraftURLOverlayImageMapRenderer.class.getName())) {
                    mapView.removeRenderer(mapRenderer);
                }
            }
        }
    }

    public static class MinecraftURLOverlayImageMapRenderer extends URLStaticImageMapRenderer {

        private final MinecraftURLOverlayImageMap parent;

        public MinecraftURLOverlayImageMapRenderer(MinecraftURLOverlayImageMap parent, int index) {
            super(parent, index);
            this.parent = parent;
        }

        @Override
        public void render(MapView mapView, MapCanvas canvas, Player player) {
            byte[] colors;
            if (parent.cachedColors != null && parent.cachedColors[index] != null) {
                colors = parent.cachedColors[index];
            } else if (parent.cachedImages[index] != null) {
                colors = MapUtils.toMapPaletteBytes(parent.cachedImages[index].get(), parent.ditheringType);
            } else {
                colors = null;
            }
            MutablePair<byte[], Collection<MapCursor>> renderData = new MutablePair<>(colors, parent.getMapMarkers().get(index).values());
            manager.callRenderEventListener(manager, imageMap, mapView, player, renderData);
            colors = renderData.getFirst();
            if (colors != null) {
                for (int i = 0; i < colors.length; i++) {
                    byte color = colors[i];
                    if (color != MapUtils.PALETTE_TRANSPARENT) {
                        canvas.setPixel(i % MapUtils.MAP_WIDTH, i / MapUtils.MAP_WIDTH, color);
                    }
                }
            }
            canvas.setCursors(MapUtils.toMapCursorCollection(renderData.getSecond()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public MutablePair<byte[], Collection<MapCursor>> renderMap(MapView mapView, Player player) {
            return (MutablePair<byte[], Collection<MapCursor>>) (MutablePair<byte[], ?>) MapUtils.bukkitRenderMap(mapView, player);
        }
    }

}
