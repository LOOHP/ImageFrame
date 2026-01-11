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

package com.loohp.imageframe.language;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.debug.Debug;
import com.loohp.imageframe.utils.HTTPRequestUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageManager {

    public static final String LANGUAGE_META_URL = "https://api.loohpjames.com/spigot/plugins/imageframe/language";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Pattern FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?s");

    private final File languageFolder;
    private final Map<String, Map<String, String>> translations;

    public LanguageManager() {
        this.languageFolder = new File(ImageFrame.plugin.getDataFolder(), "language");
        this.languageFolder.mkdirs();
        this.translations = new ConcurrentHashMap<>();

        reloadLanguages();
    }

    public void reloadLanguages() {
        downloadTranslations();
        this.translations.clear();
        loadTranslations();
    }

    public Set<String> getLoadedLanguages() {
        return Collections.unmodifiableSet(translations.keySet());
    }

    public void downloadTranslations() {
        try {
            File languageHashFile = new File(ImageFrame.plugin.getDataFolder(), "language_hashes.json");
            JsonObject languageHashes;
            if (languageHashFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(languageHashFile.toPath()), StandardCharsets.UTF_8))) {
                    languageHashes = GSON.fromJson(reader, JsonObject.class);
                } catch (IOException e) {
                    e.printStackTrace();
                    languageHashes = new JsonObject();
                }
            } else {
                languageHashes = new JsonObject();
            }

            JsonObject json;
            try {
                Debug.debug("Downloading language meta...");
                json = HTTPRequestUtils.getJsonResponse(LANGUAGE_META_URL);
            } catch (Exception e) {
                throw new IOException("Unable to fetch language meta from \"api.loohpjames.com\". This could be an internet issue or \"api.loohpjames.com\" is down. If the plugin functions correctly after this, this error can be ignored.", e);
            }
            for (JsonElement element : json.get("languages").getAsJsonArray()) {
                JsonObject languageObj = element.getAsJsonObject();
                String language = languageObj.get("language").getAsString();
                String url = languageObj.get("url").getAsString();
                String hash = languageObj.get("hash").getAsString();

                String existingHash = languageHashes.has(language) ? languageHashes.get(language).getAsString() : null;
                if (!hash.equalsIgnoreCase(existingHash)) {
                    Debug.debug("Downloading language " + language + "...");
                    try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(HTTPRequestUtils.getInputStream(url), StandardCharsets.UTF_8));
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(new File(languageFolder, language + ".json").toPath()), StandardCharsets.UTF_8));
                    ) {
                        reader.lines().forEachOrdered(l -> writer.println(l));
                        writer.flush();
                    } catch (IOException e) {
                        new IOException("Unable to download language " + language + " from " + url, e).printStackTrace();
                    }
                }

                languageHashes.addProperty(language, hash);
            }

            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(languageHashFile.toPath()), StandardCharsets.UTF_8))) {
                writer.println(GSON.toJson(languageHashes));
                writer.flush();
            } catch (IOException e) {
                new IOException("Unable to save language hashes to " + languageHashFile.getAbsolutePath(), e).printStackTrace();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void loadTranslations() {
        for (File file : languageFolder.listFiles()) {
            String name = file.getName();
            if (name.endsWith(".json")) {
                String language = name.substring(0, name.indexOf("."));
                Map<String, String> translations = new ConcurrentHashMap<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
                    JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);
                    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                        translations.put(entry.getKey(), entry.getValue().getAsString());
                    }
                } catch (Exception e) {
                    new IOException("Unable to load language file " + file.getAbsolutePath(), e).printStackTrace();
                }
                this.translations.put(language, translations);
            }
        }
    }

    public String getTranslation(String key, String language) {
        String translation = translations.getOrDefault(language.toLowerCase(), Collections.emptyMap()).get(key);
        if (translation != null) {
            return translation;
        }
        if (ImageFrame.language.equalsIgnoreCase(language)) {
            return null;
        }
        return getTranslation(key, ImageFrame.language);
    }

    public List<String> getListTranslation(String key, String language) {
        return getListTranslation(key, language, Byte.MAX_VALUE);
    }

    public List<String> getListTranslation(String key, String language, int maxSize) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < maxSize; i++) {
            String translated = getTranslation(key + "." + i, language);
            if (translated == null) {
                return result;
            }
            result.add(translated);
        }
        return result;
    }

    public Component resolve(Component component, String language) {
        if (component instanceof TranslatableComponent) {
            component = convertSingleTranslatable((TranslatableComponent) component, language);
        }
        List<Component> children = new ArrayList<>(component.children());
        children.replaceAll(child -> resolve(child, language));
        return component.children(children);
    }

    private Component convertSingleTranslatable(TranslatableComponent component, String language) {
        String translation = getTranslation(component.key(), language);
        if (translation == null) {
            return component;
        }

        List<TranslationArgument> arguments = component.arguments();
        Matcher matcher = FORMAT_PATTERN.matcher(translation);

        int lastEnd = 0;
        int nextUnindexed = 0;
        List<Component> parts = new ArrayList<>();

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String literal = translation.substring(lastEnd, matcher.start());
                if (!literal.isEmpty()) {
                    parts.add(Component.text(literal));
                }
            }
            String indexGroup = matcher.group(1);
            int argIndex;
            if (indexGroup != null) {
                argIndex = Integer.parseInt(indexGroup) - 1;
            } else {
                argIndex = nextUnindexed++;
            }
            if (argIndex >= 0 && argIndex < arguments.size()) {
                parts.add(arguments.get(argIndex).asComponent());
            } else {
                parts.add(Component.text(matcher.group()));
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < translation.length()) {
            String tail = translation.substring(lastEnd);
            if (!tail.isEmpty()) {
                parts.add(Component.text(tail));
            }
        }
        Component result = Component.empty().style(component.style());
        for (Component part : parts) {
            result = result.append(part);
        }
        for (Component child : component.children()) {
            result = result.append(child);
        }
        return result;
    }

}
