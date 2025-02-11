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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class IFPlayerPreference<T> {

    private static final Map<String, IFPlayerPreference<?>> VALUES = new ConcurrentHashMap<>();

    public static final IFPlayerPreference<BooleanState> VIEW_ANIMATED_MAPS = new IFPlayerPreference<>("VIEW_ANIMATED_MAPS", "viewAnimatedMaps", BooleanState.class, BooleanState.STRING_VALUES_MAP, i -> BooleanState.UNSET, v -> v.getJsonValue(), j -> BooleanState.fromJsonValue(j), s -> StringDeserializerResult.accepted(BooleanState.fromString(s)));

    static {
        register(VIEW_ANIMATED_MAPS);
    }

    public static void register(IFPlayerPreference<?> preference) {
        VALUES.put(preference.name(), preference);
    }

    public static Collection<IFPlayerPreference<?>> values() {
        return Collections.unmodifiableCollection(VALUES.values());
    }

    public static IFPlayerPreference<?> valueOf(String name) {
        return VALUES.get(name);
    }

    private final String name;
    private final String jsonName;
    private final Class<T> type;
    private final Map<String, T> suggestedValues;
    private final Function<IFPlayer, T> defaultValue;
    private final Function<T, JsonElement> serializer;
    private final Function<JsonElement, T> deserializer;
    private final Function<String, StringDeserializerResult<T>> stringDeserializer;

    IFPlayerPreference(String name, String jsonName, Class<T> type, Map<String, T> suggestedValues, Function<IFPlayer, T> defaultValue, Function<T, JsonElement> serializer, Function<JsonElement, T> deserializer, Function<String, StringDeserializerResult<T>> stringDeserializer) {
        this.name = name;
        this.jsonName = jsonName;
        this.type = type;
        this.suggestedValues = Collections.unmodifiableMap(suggestedValues);
        this.defaultValue = defaultValue;
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.stringDeserializer = stringDeserializer;
    }

    public String name() {
        return name;
    }

    public String getJsonName() {
        return jsonName;
    }

    public Class<T> getType() {
        return type;
    }

    public Map<String, T> getSuggestedValues() {
        return suggestedValues;
    }

    public Function<T, JsonElement> getSerializer() {
        return serializer;
    }

    @SuppressWarnings("unchecked")
    public <E> Function<E, JsonElement> getSerializer(Class<E> type) {
        return (Function<E, JsonElement>) serializer;
    }

    public Function<JsonElement, T> getDeserializer() {
        return deserializer;
    }

    @SuppressWarnings("unchecked")
    public <E> Function<JsonElement, E> getDeserializer(Class<E> type) {
        return (Function<JsonElement, E>) deserializer;
    }

    public Function<String, StringDeserializerResult<T>> getStringDeserializer() {
        return stringDeserializer;
    }

    @SuppressWarnings("unchecked")
    public <E> Function<String, StringDeserializerResult<E>> getStringDeserializer(Class<E> type) {
        return (Function<String, StringDeserializerResult<E>>) (Function<String, ?>) stringDeserializer;
    }

    public T getDefaultValue(IFPlayer ifPlayer) {
        return defaultValue.apply(ifPlayer);
    }

    @SuppressWarnings("unchecked")
    public <E> E getDefaultValue(IFPlayer ifPlayer, Class<E> type) {
        return (E) defaultValue.apply(ifPlayer);
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IFPlayerPreference<?> that = (IFPlayerPreference<?>) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public static class StringDeserializerResult<T> {

        public static StringDeserializerResult<?> REJECTED_INSTANCE = new StringDeserializerResult<>(false, null);

        @SuppressWarnings("unchecked")
        public static <T> StringDeserializerResult<T> rejected() {
            return (StringDeserializerResult<T>) REJECTED_INSTANCE;
        }

        public static <T> StringDeserializerResult<T> accepted(T result) {
            return new StringDeserializerResult<>(true, result);
        }

        private final boolean accepted;
        private final T value;

        private StringDeserializerResult(boolean accepted, T value) {
            this.accepted = accepted;
            this.value = value;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public T getValue() {
            return value;
        }
    }
}
