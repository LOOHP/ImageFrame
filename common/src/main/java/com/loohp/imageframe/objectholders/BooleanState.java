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

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public enum BooleanState implements UnsetState, PreferenceState {

    TRUE(new JsonPrimitive(true)),
    FALSE(new JsonPrimitive(false)),
    UNSET(new JsonPrimitive("unset"));

    public static final Map<String, BooleanState> STRING_VALUES_MAP = Collections.unmodifiableMap(Arrays.stream(values()).collect(Collectors.toMap(e -> e.toString(), e -> e, (x, y) -> y, LinkedHashMap::new)));

    private final JsonElement jsonValue;

    BooleanState(JsonElement jsonValue) {
        this.jsonValue = jsonValue;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    @Override
    public boolean isUnset() {
        return this.equals(UNSET);
    }

    @Override
    public JsonElement getJsonValue() {
        return jsonValue;
    }

    public Boolean getRawValue() {
        switch (this) {
            case TRUE:
                return Boolean.TRUE;
            case FALSE:
                return Boolean.FALSE;
            case UNSET:
                return null;
        }
        return null;
    }

    public boolean getRawValue(boolean unsetIsTrue) {
        switch (this) {
            case TRUE:
                return Boolean.TRUE;
            case FALSE:
                return Boolean.FALSE;
            case UNSET:
                return unsetIsTrue;
        }
        return unsetIsTrue;
    }

    public boolean getCalculatedValue(BooleanSupplier unsetValueFunction) {
        switch (this) {
            case TRUE:
                return true;
            case FALSE:
                return false;
            case UNSET:
                return unsetValueFunction.getAsBoolean();
        }
        return unsetValueFunction.getAsBoolean();
    }

    public static BooleanState fromBoolean(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static BooleanState fromBoolean(Boolean value) {
        return value == null ? UNSET : fromBoolean(value.booleanValue());
    }

    public static BooleanState fromString(String value) {
        return STRING_VALUES_MAP.getOrDefault(value.toLowerCase(), UNSET);
    }

    public static BooleanState fromJsonValue(JsonElement jsonValue) {
        if (jsonValue.isJsonPrimitive()) {
            JsonPrimitive jsonPrimitive = jsonValue.getAsJsonPrimitive();
            if (jsonPrimitive.isBoolean()) {
                return fromBoolean(jsonPrimitive.getAsBoolean());
            } else if (jsonPrimitive.isString()) {
                return fromString(jsonPrimitive.getAsString());
            }
        }
        return UNSET;
    }

}
