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

package com.loohp.imageframe.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loohp.imageframe.objectholders.IFPlayer;
import com.loohp.imageframe.objectholders.IFPlayerManager;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.ImageMapLoaders;
import com.loohp.imageframe.objectholders.ImageMapManager;
import com.loohp.imageframe.objectholders.LazyDataSource;
import com.loohp.imageframe.objectholders.MutablePair;
import com.loohp.imageframe.utils.FileUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static com.loohp.imageframe.objectholders.ImageMapManager.GSON;

public class FileImageFrameStorage implements ImageFrameStorage {

    private final File imageMapFolder;
    private final File playerDataFolder;
    private final AtomicInteger mapIndexCounter;
    private final UUID instanceId;

    public FileImageFrameStorage(File imageMapFolder, File playerDataFolder) {
        this.imageMapFolder = imageMapFolder;
        this.playerDataFolder = playerDataFolder;
        this.mapIndexCounter = new AtomicInteger(0);

        this.imageMapFolder.mkdirs();
        File localDataFile = new File(imageMapFolder, "data.json");
        if (localDataFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(localDataFile.toPath()), StandardCharsets.UTF_8))) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                this.instanceId = UUID.fromString(json.get("instanceId").getAsString());
            } catch (Throwable e) {
                throw new RuntimeException("Unable to read " + localDataFile.getAbsolutePath(), e);
            }
        } else {
            JsonObject json = new JsonObject();
            this.instanceId = UUID.randomUUID();
            json.addProperty("instanceId", instanceId.toString());
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(localDataFile.toPath()), StandardCharsets.UTF_8))) {
                pw.println(GSON.toJson(json));
                pw.flush();
            } catch (Throwable e) {
                throw new RuntimeException("Unable to save " + localDataFile.getAbsolutePath(), e);
            }
        }
    }

    public File getImageMapFolder() {
        return imageMapFolder;
    }

    public File getPlayerDataFolder() {
        return playerDataFolder;
    }

    @Override
    public UUID getInstanceId() {
        return instanceId;
    }

    @Override
    public FileImageFrameStorageLoader getLoader() {
        return ImageFrameStorageLoaders.FILE;
    }

    @Override
    public LazyDataSource getSource(int imageIndex, String fileName) {
        return new FileLazyDataSource(this, imageIndex, fileName);
    }

    @Override
    public Set<Integer> getAllImageIndexes() {
        imageMapFolder.mkdirs();
        File[] files = imageMapFolder.listFiles();
        Arrays.sort(files, FileUtils.BY_NUMBER_THEN_STRING);
        Set<Integer> result = new HashSet<>();
        for (File file : files) {
            if (file.isDirectory()) {
                try {
                    int imageIndex = Integer.parseInt(file.getName());
                    result.add(imageIndex);
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return result;
    }

    @Override
    public boolean hasImageMapData(int imageIndex) {
        File folder = new File(imageMapFolder, String.valueOf(imageIndex));
        if (!folder.exists()) {
            return false;
        }
        return new File(folder, "data.json").exists();
    }

    @Override
    public JsonObject loadImageMapData(int imageIndex) throws IOException {
        File folder = new File(imageMapFolder, String.valueOf(imageIndex));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(new File(folder, "data.json").toPath()), StandardCharsets.UTF_8))) {
            return GSON.fromJson(reader, JsonObject.class);
        }
    }

    @Override
    public void prepareImageIndex(ImageMap map, IntConsumer imageIndexSetter) {
        int originalImageIndex = map.getImageIndex();
        if (originalImageIndex < 0) {
            imageIndexSetter.accept(mapIndexCounter.getAndIncrement());
        } else {
            mapIndexCounter.updateAndGet(i -> Math.max(originalImageIndex + 1, i));
        }
    }

    @Override
    public void deleteMap(int imageIndex) {
        imageMapFolder.mkdirs();
        File folder = new File(imageMapFolder, String.valueOf(imageIndex));
        if (folder.exists() && folder.isDirectory()) {
            FileUtils.removeFolderRecursively(folder);
        }
    }

    @Override
    public List<MutablePair<String, Future<? extends ImageMap>>> loadMaps(ImageMapManager manager, Set<Integer> deletedMapIds, IFPlayerManager ifPlayerManager) {
        imageMapFolder.mkdirs();
        File[] files = imageMapFolder.listFiles();
        Arrays.sort(files, FileUtils.BY_NUMBER_THEN_STRING);
        List<MutablePair<String, Future<? extends ImageMap>>> futures = new LinkedList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                try {
                    int imageIndex = Integer.parseInt(file.getName());
                    JsonObject json = loadImageMapData(imageIndex);
                    futures.add(new MutablePair<>(file.getAbsolutePath(), ImageMapLoaders.load(manager, json)));
                } catch (Throwable e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMap data in " + file.getAbsolutePath());
                    e.printStackTrace();
                }
            } else if (file.getName().equalsIgnoreCase("deletedMaps.bin")) {
                deletedMapIds.addAll(loadDeletedMaps());
            } else if (file.getName().equalsIgnoreCase("deletedMaps.json")) { //legacy storage support
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    JsonArray deletedMapIdsArray = json.get("mapids").getAsJsonArray();
                    for (JsonElement element : deletedMapIdsArray) {
                        deletedMapIds.add(element.getAsInt());
                    }
                } catch (IOException e) {
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMapManager data in " + file.getAbsolutePath());
                    e.printStackTrace();
                }
                saveDeletedMaps(deletedMapIds);
                try {
                    Files.move(file.toPath(), new File(imageMapFolder, "deletedMaps.json.bak").toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return futures;
    }

    @Override
    public void saveImageMapData(int imageIndex, JsonObject json) throws IOException {
        File folder = new File(imageMapFolder, String.valueOf(imageIndex));
        folder.mkdirs();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(new File(folder, "data.json").toPath()), StandardCharsets.UTF_8))) {
            pw.println(GSON.toJson(json));
            pw.flush();
        }
    }

    @Override
    public Set<Integer> loadDeletedMaps() {
        File file = new File(imageMapFolder, "deletedMaps.bin");
        Set<Integer> deletedMapIds = new HashSet<>();
        try (DataInputStream dataInputStream = new DataInputStream(Files.newInputStream(file.toPath()))) {
            try {
                deletedMapIds.add(dataInputStream.readInt());
            } catch (EOFException ignore) {}
        } catch (IOException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMapManager data in " + file.getAbsolutePath());
            e.printStackTrace();
        }
        return deletedMapIds;
    }

    @Override
    public void saveDeletedMaps(Set<Integer> deletedMapIds) {
        File file = new File(imageMapFolder, "deletedMaps.bin");
        try (DataOutputStream dataOutputStream = new DataOutputStream(Files.newOutputStream(file.toPath()))) {
            for (int deletedMapId : deletedMapIds) {
                dataOutputStream.writeInt(deletedMapId);
            }
            dataOutputStream.flush();
        } catch (IOException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to save ImageMapManager data in " + file.getAbsolutePath());
            e.printStackTrace();
        }
    }

    @Override
    public JsonObject loadPlayerData(IFPlayerManager manager, UUID uuid) {
        playerDataFolder.mkdirs();
        File file = new File(playerDataFolder, uuid + ".json");
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                IFPlayer.load(manager, json);
                return json;
            } catch (Exception e) {
                new RuntimeException("Unable to load ImageFrame player data from " + file.getAbsolutePath(), e).printStackTrace();
                try {
                    Files.copy(file.toPath(), new File(file.getParentFile(), file.getName() + ".bak").toPath());
                } catch (IOException ex) {
                    new RuntimeException("Unable to backup ImageFrame player data from " + file.getAbsolutePath(), ex).printStackTrace();
                }
            }
        }
        return null;
    }

    @Override
    public void savePlayerData(UUID uuid, JsonObject json) throws IOException {
        playerDataFolder.mkdirs();
        File file = new File(playerDataFolder, uuid + ".json");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
            pw.println(GSON.toJson(json));
            pw.flush();
        }
    }

    @Override
    public Set<UUID> getAllSavedPlayerData() {
        Set<UUID> players = new HashSet<>();
        playerDataFolder.mkdirs();
        File[] files = playerDataFolder.listFiles();
        for (File file : files) {
            if (file.isFile()) {
                try {
                    String fileName = file.getName();
                    players.add(UUID.fromString(fileName.substring(0, fileName.indexOf("."))));
                } catch (Throwable ignore) {
                }
            }
        }
        return players;
    }

    @Override
    public void close() {
        //do nothing
    }

    public static class FileLazyDataSource implements LazyDataSource {

        private final FileImageFrameStorage storage;
        private final int imageIndex;
        private final String fileName;

        public FileLazyDataSource(FileImageFrameStorage storage, int imageIndex, String fileName) {
            this.storage = storage;
            this.imageIndex = imageIndex;
            this.fileName = fileName;
        }

        @Override
        public <T> T load(Reader<T> reader) throws IOException {
            File folder = new File(storage.imageMapFolder, String.valueOf(imageIndex));
            File file = new File(folder, fileName);
            try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                return reader.read(inputStream);
            }
        }

        @Override
        public void save(Writer writer) throws IOException {
            File folder = new File(storage.imageMapFolder, String.valueOf(imageIndex));
            folder.mkdirs();
            File file = new File(folder, fileName);
            File tempFile = new File(folder, fileName + ".tmp");
            try (OutputStream outputStream = Files.newOutputStream(tempFile.toPath())) {
                writer.write(outputStream);
            }
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        public int getImageIndex() {
            return imageIndex;
        }

        @Override
        public String getFileName() {
            return fileName;
        }

        @Override
        public FileLazyDataSource withFileName(String fileName) {
            return new FileLazyDataSource(storage, imageIndex, fileName);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            FileLazyDataSource that = (FileLazyDataSource) o;
            return imageIndex == that.imageIndex && Objects.equals(storage.imageMapFolder, that.storage.imageMapFolder) && Objects.equals(fileName, that.fileName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(storage.imageMapFolder, imageIndex, fileName);
        }
    }

}
