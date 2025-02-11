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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IFPlayer {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    public static IFPlayer create(IFPlayerManager manager, UUID player) throws Exception {
        IFPlayer ifPlayer = new IFPlayer(manager, player, Collections.emptyMap());
        ifPlayer.save();
        return ifPlayer;
    }

    public static IFPlayer load(IFPlayerManager manager, JsonObject json) {
        UUID uuid = UUID.fromString(json.get("uuid").getAsString());
        Map<IFPlayerPreference<?>, Object> preferences = new HashMap<>();
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
    private final Map<IFPlayerPreference<?>, Object> preferences;

    private IFPlayer(IFPlayerManager manager, UUID uuid, Map<IFPlayerPreference<?>, ?> preferences) {
        this.manager = manager;
        this.uuid = uuid;
        this.preferences = new ConcurrentHashMap<>(preferences);
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

    public Object getPreference(IFPlayerPreference<?> preference) {
        return preferences.computeIfAbsent(preference, k -> preference.getDefaultValue(this));
    }

    @SuppressWarnings("unchecked")
    public <T> T getPreference(IFPlayerPreference<?> preference, Class<T> type) {
        return (T) preferences.computeIfAbsent(preference, k -> preference.getDefaultValue(this, type));
    }

    public void setPreference(IFPlayerPreference<?> preference, Object value) {
        preferences.put(preference, value);
    }

    public void save() throws Exception {
        manager.getDataFolder().mkdirs();
        File file = new File(manager.getDataFolder(), uuid + ".json");
        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid.toString());
        JsonObject preferenceJson = new JsonObject();
        for (Map.Entry<IFPlayerPreference<?>, Object> entry : preferences.entrySet()) {
            IFPlayerPreference<?> preference = entry.getKey();
            preferenceJson.add(preference.getJsonName(), preference.getSerializer(Object.class).apply(entry.getValue()));
        }
        json.add("preferences", preferenceJson);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
            pw.println(GSON.toJson(json));
            pw.flush();
        }
    }

}
