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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public enum BooleanState implements UnsetState, PreferenceState {

    TRUE(new JsonPrimitive(true), true, NamedTextColor.GREEN),
    FALSE(new JsonPrimitive(false), false, NamedTextColor.RED),
    UNSET(UNSET_JSON, null, NamedTextColor.GRAY);

    public static final Map<String, BooleanState> STRING_VALUES_MAP = Collections.unmodifiableMap(Arrays.stream(values()).collect(Collectors.toMap(e -> e.toString(), e -> e, (x, y) -> y, LinkedHashMap::new)));
    public static final Map<Boolean, BooleanState> BOOLEAN_VALUES_MAP = Collections.unmodifiableMap(Arrays.stream(values()).collect(Collectors.toMap(e -> e.booleanValue, e -> e, (x, y) -> y, LinkedHashMap::new)));

    private final JsonElement jsonValue;
    private final Boolean booleanValue;
    private final TextColor displayColor;

    BooleanState(JsonElement jsonValue, Boolean booleanValue, TextColor displayColor) {
        this.jsonValue = jsonValue;
        this.booleanValue = booleanValue;
        this.displayColor = displayColor;
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

    @Override
    public TextColor getDisplayColor() {
        return displayColor;
    }

    public Boolean getRawValue() {
        return booleanValue;
    }

    public boolean getRawValue(boolean unsetIsTrue) {
        return booleanValue == null ? unsetIsTrue : booleanValue;
    }

    public boolean getCalculatedValue(BooleanSupplier unsetValueFunction) {
        return booleanValue == null ? unsetValueFunction.getAsBoolean() : booleanValue;
    }

    public static BooleanState fromBoolean(boolean value) {
        return fromBoolean((Boolean) value);
    }

    public static BooleanState fromBoolean(Boolean value) {
        return BOOLEAN_VALUES_MAP.getOrDefault(value, UNSET);
    }

    public static BooleanState fromString(String value) {
        return STRING_VALUES_MAP.getOrDefault(value.toLowerCase(), UNSET);
    }

    public static BooleanState fromJsonValue(JsonElement jsonValue) {
        if (jsonValue.isJsonPrimitive()) {
            JsonPrimitive jsonPrimitive = jsonValue.getAsJsonPrimitive();
            if (jsonPrimitive.isBoolean()) {
                return fromBoolean(jsonPrimitive.getAsBoolean());
            } else if (jsonPrimitive.isNumber()) {
                return fromBoolean(jsonPrimitive.getAsNumber().intValue() != 0);
            } else if (jsonPrimitive.isString()) {
                return fromString(jsonPrimitive.getAsString());
            }
        }
        return UNSET;
    }

}
