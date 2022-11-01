/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2022. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2022. Contributors
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

    }

}
