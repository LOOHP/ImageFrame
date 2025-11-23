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

package com.loohp.imageframe.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

public class JsonUtils {

    public static JsonElement merge(JsonElement first, JsonElement second) {
        if (first.isJsonObject() && second.isJsonObject()) {
            JsonObject firstObj = first.getAsJsonObject();
            JsonObject secondObj = second.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : secondObj.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (firstObj.has(key)) {
                    firstObj.add(key, merge(firstObj.get(key), value));
                } else {
                    firstObj.add(key, value);
                }
            }
            return firstObj;
        }
        if (first.isJsonArray() && second.isJsonArray()) {
            JsonArray firstArray = first.getAsJsonArray();
            JsonArray secondArray = second.getAsJsonArray();
            for (int i = 0; i < Math.min(firstArray.size(), secondArray.size()); i++) {
                firstArray.set(i, merge(firstArray.get(i), secondArray.get(i)));
            }
            return firstArray;
        }
        return second;
    }

}
