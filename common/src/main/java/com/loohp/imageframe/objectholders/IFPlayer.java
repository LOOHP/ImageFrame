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
import com.google.gson.JsonObject;
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.storage.ImageFrameStorage;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IFPlayer {

    public static IFPlayer create(IFPlayerManager manager, UUID player) throws Exception {
        IFPlayer ifPlayer = new IFPlayer(manager, player, Collections.emptyMap());
        ifPlayer.save();
        return ifPlayer;
    }

    public static IFPlayer load(IFPlayerManager manager, JsonObject json) {
        UUID uuid = UUID.fromString(json.get("uuid").getAsString());
        Map<IFPlayerPreference<?>, PreferenceState> preferences = new HashMap<>();
        JsonObject preferenceJson = json.get("preferences").getAsJsonObject();
        for (IFPlayerPreference<?> preference : IFPlayerPreference.values()) {
            JsonElement element = preferenceJson.get(preference.getJsonName());
            if (element != null) {
                preferences.put(preference, preference.getDeserializer().apply(element));
            }
        }
        return new IFPlayer(manager, uuid, preferences);
    }

    private final IFPlayerManager manager;

    private final UUID uuid;
    private final Map<IFPlayerPreference<?>, PreferenceState> preferences;

    private IFPlayer(IFPlayerManager manager, UUID uuid, Map<IFPlayerPreference<?>, PreferenceState> preferences) {
        this.manager = manager;
        this.uuid = uuid;
        this.preferences = new ConcurrentHashMap<>(preferences);
    }

    public void applyUpdate(JsonObject json) {
        Map<IFPlayerPreference<?>, PreferenceState> preferences = new HashMap<>();
        JsonObject preferenceJson = json.get("preferences").getAsJsonObject();
        for (IFPlayerPreference<?> preference : IFPlayerPreference.values()) {
            JsonElement element = preferenceJson.get(preference.getJsonName());
            if (element != null) {
                preferences.put(preference, preference.getDeserializer().apply(element));
            }
        }
        this.preferences.clear();
        this.preferences.putAll(preferences);
    }

    public OfflinePlayer getLocalPlayer() {
        return Bukkit.getOfflinePlayer(uuid);
    }

    public String getName() {
        return getLocalPlayer().getName();
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public PreferenceState getPreference(IFPlayerPreference<?> preference) {
        return preferences.computeIfAbsent(preference, k -> preference.getDefaultValue(this));
    }

    @SuppressWarnings("unchecked")
    public <T extends PreferenceState> T getPreference(IFPlayerPreference<?> preference, Class<T> type) {
        return (T) preferences.computeIfAbsent(preference, k -> preference.getDefaultValue(this, type));
    }

    public void setPreference(IFPlayerPreference<?> preference, PreferenceState value) {
        preferences.put(preference, value);
        saveInternal();
    }

    private void saveInternal() {
        Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
            try {
                save();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void save() throws Exception {
        save(manager.getStorage());
    }

    public void save(ImageFrameStorage storage) throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid.toString());
        JsonObject preferenceJson = new JsonObject();
        for (Map.Entry<IFPlayerPreference<?>, PreferenceState> entry : preferences.entrySet()) {
            IFPlayerPreference<?> preference = entry.getKey();
            preferenceJson.add(preference.getJsonName(), preference.getSerializer(PreferenceState.class).apply(entry.getValue()));
        }
        json.add("preferences", preferenceJson);
        storage.savePlayerData(uuid, json);
    }

}
