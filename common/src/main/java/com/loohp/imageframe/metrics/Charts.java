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

package com.loohp.imageframe.metrics;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.ImageMap;
import org.bukkit.map.MapCursor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class Charts {

    public static void setup(Metrics metrics) {

        metrics.addCustomChart(new Metrics.SingleLineChart("total_images_created", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return ImageFrame.imageMapManager.getMaps().size();
            }
        }));

        metrics.addCustomChart(new Metrics.SingleLineChart("total_maps_created", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return ImageFrame.imageMapManager.getMaps().stream().mapToInt(each -> each.getMapViews().size()).sum();
            }
        }));

        metrics.addCustomChart(new Metrics.AdvancedPie("images_created_by_type", new Callable<Map<String, Integer>>() {
            @Override
            public Map<String, Integer> call() throws Exception {
                Map<String, Integer> valueMap = new HashMap<>();
                for (ImageMap imageMap : ImageFrame.imageMapManager.getMaps()) {
                    String type = imageMap.getClass().getName();
                    valueMap.merge(type, 1, Integer::sum);
                }
                return valueMap;
            }
        }));

        metrics.addCustomChart(new Metrics.SingleLineChart("total_markers_created", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return ImageFrame.imageMapManager.getMaps().stream().flatMap(each -> each.getMapMarkers().stream()).mapToInt(each -> each.size()).sum();
            }
        }));

        metrics.addCustomChart(new Metrics.AdvancedPie("markers_created_by_type", new Callable<Map<String, Integer>>() {
            @Override
            public Map<String, Integer> call() throws Exception {
                Map<String, Integer> valueMap = new HashMap<>();
                for (ImageMap imageMap : ImageFrame.imageMapManager.getMaps()) {
                    for (Map<String, MapCursor> map : imageMap.getMapMarkers()) {
                        for (MapCursor mapCursor : map.values()) {
                            String type = mapCursor.getType().name().toLowerCase();
                            valueMap.merge(type, 1, Integer::sum);
                        }
                    }
                }
                return valueMap;
            }
        }));

        metrics.addCustomChart(new Metrics.SingleLineChart("item_frames_made_invisible_in_last_interval", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                long value = ImageFrame.invisibleFrameManager.getItemFramesMadeInvisible().getAndSet(0);
                return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
            }
        }));

        metrics.addCustomChart(new Metrics.SingleLineChart("invisible_item_frames_placed_in_last_interval", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                long value = ImageFrame.invisibleFrameManager.getInvisibleItemFramesPlaced().getAndSet(0);
                return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
            }
        }));

        metrics.addCustomChart(new Metrics.SingleLineChart("embedded_service_image_uploaded_in_last_interval", new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                long value = ImageFrame.imageUploadManager.getImagesUploadedCounter().getAndSet(0);
                return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
            }
        }));

    }

}
