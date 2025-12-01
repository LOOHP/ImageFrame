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
import com.loohp.imageframe.ImageFrame;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class LanguageState implements UnsetState, PreferenceState {

    public static final LanguageState UNSET = new LanguageState(null);

    public static final Supplier<Map<String, LanguageState>> VALUES_MAP_SUPPLIER = () -> {
        Map<String, LanguageState> values = ImageFrame.languageManager.getLoadedLanguages().stream()
                .collect(Collectors.toMap(e -> e, e -> new LanguageState(e), (x, y) -> y, LinkedHashMap::new));
        values.put("unset", UNSET);
        return values;
    };

    private final JsonElement jsonValue;
    private final String languageValue;

    public LanguageState(String languageValue) {
        this.jsonValue = languageValue == null ? UNSET_JSON : new JsonPrimitive(languageValue);
        this.languageValue = languageValue;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    @Override
    public String name() {
        return languageValue.toUpperCase();
    }

    @Override
    public JsonElement getJsonValue() {
        return jsonValue;
    }

    @Override
    public TextColor getDisplayColor() {
        return isUnset() ? NamedTextColor.GRAY : NamedTextColor.WHITE;
    }

    public String getRawValue() {
        return languageValue;
    }

    public String getRawValue(String unsetValue) {
        return languageValue == null ? unsetValue : languageValue;
    }

    public String getCalculatedValue(Supplier<String> unsetValueFunction) {
        return languageValue == null || !ImageFrame.languageManager.getLoadedLanguages().contains(languageValue) ? unsetValueFunction.get() : languageValue;
    }

    public static LanguageState fromString(String value) {
        return new LanguageState(value);
    }

    public static LanguageState fromJsonValue(JsonElement jsonValue) {
        return fromString(UnsetState.isUnset(jsonValue) ? null : jsonValue.getAsString());
    }

}
