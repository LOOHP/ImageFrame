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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.IFPlayer;
import com.loohp.imageframe.objectholders.IFPlayerManager;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.objectholders.ImageMapLoaders;
import com.loohp.imageframe.objectholders.ImageMapManager;
import com.loohp.imageframe.objectholders.LazyBufferedImageSource;
import com.loohp.imageframe.objectholders.MutablePair;
import com.loohp.imageframe.utils.JsonUtils;
import com.loohp.platformscheduler.ScheduledTask;
import com.loohp.platformscheduler.Scheduler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

@SuppressWarnings("UnnecessarySemicolon")
public class JdbcImageFrameStorage implements ImageFrameStorage {

    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final File localDataFolder;
    private final HikariDataSource dataSource;
    private final UUID instanceId;
    private final int activePollInterval;

    private AtomicLong lastUpdateFetch;
    private ScheduledTask updateFetchTask;
    private ScheduledTask periodicSyncTask;

    public JdbcImageFrameStorage(File localDataFolder, String jdbcUrl, String username, String password, int activePollInterval) {
        this.localDataFolder = localDataFolder;
        this.activePollInterval = activePollInterval;

        File localDataFile = new File(localDataFolder, "data.json");
        if (localDataFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(localDataFile.toPath()), StandardCharsets.UTF_8))) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                this.instanceId = UUID.fromString(json.get("instanceId").getAsString());
            } catch (IOException e) {
                throw new RuntimeException("Unable to read " + localDataFile.getAbsolutePath(), e);
            }
        } else {
            JsonObject json = new JsonObject();
            this.instanceId = UUID.randomUUID();
            json.addProperty("instanceId", instanceId.toString());
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(localDataFile.toPath()), StandardCharsets.UTF_8))) {
                pw.println(GSON.toJson(json));
                pw.flush();
            } catch (IOException e) {
                throw new RuntimeException("Unable to save " + localDataFile.getAbsolutePath(), e);
            }
        }

        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(10);

            this.dataSource = new HikariDataSource(config);

            try (Connection connection = dataSource.getConnection()) {
                Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ImageFrame] Successfully connected to database.");
            }

            prepareDatabase();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to connect to database", e);
        }
    }

    private void setupTasks(ImageMapManager imageMapManager, IFPlayerManager ifPlayerManager) {
        if (lastUpdateFetch == null) {
            lastUpdateFetch = new AtomicLong(System.currentTimeMillis());
        } else {
            lastUpdateFetch.set(System.currentTimeMillis());
        }
        if (updateFetchTask != null) {
            updateFetchTask.cancel();
        }
        updateFetchTask = Scheduler.runTaskTimerAsynchronously(ImageFrame.plugin, () -> activeUpdate(imageMapManager, ifPlayerManager), activePollInterval + 100, activePollInterval);
        if (periodicSyncTask != null) {
            periodicSyncTask.cancel();
        }
        periodicSyncTask = Scheduler.runTaskTimerAsynchronously(ImageFrame.plugin, () -> imageMapManager.syncMaps(), (activePollInterval * 12L) + 100, activePollInterval * 12L);
    }

    private void prepareDatabase() {
        try (
            Connection connection = dataSource.getConnection();
            Statement stmt = connection.createStatement();
        ) {
            // Image map metadata
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS IMAGE_MAPS (IMAGE_INDEX INT NOT NULL PRIMARY KEY, DATA LONGTEXT NOT NULL)");
            // Image map images (PNG blobs).
            // No foreign key to avoid ordering issues between metadata and images.
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS IMAGE_MAP_IMAGES (IMAGE_INDEX INT NOT NULL, FILE_NAME VARCHAR(255) NOT NULL, IMAGE LONGBLOB NOT NULL, PRIMARY KEY (IMAGE_INDEX, FILE_NAME))");
            // Per-instance data for each image_index
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS INSTANCE_IMAGE_MAP_DATA (IMAGE_INDEX INT NOT NULL, INSTANCE_ID CHAR(36) NOT NULL, DATA LONGTEXT NOT NULL, PRIMARY KEY (IMAGE_INDEX, INSTANCE_ID))");
            // Deleted maps set
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS DELETED_MAPS (INSTANCE_ID CHAR(36) NOT NULL, MAP_ID INT NOT NULL, PRIMARY KEY (INSTANCE_ID, MAP_ID))");
            // Sequence table for image indices (only for ID allocation)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS IMAGE_MAP_INDEX_SEQUENCE (ID INT NOT NULL AUTO_INCREMENT PRIMARY KEY)");
            // Track last update time per image_index
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS IMAGE_MAP_UPDATE_STATE (IMAGE_INDEX INT NOT NULL PRIMARY KEY, LAST_UPDATED TIMESTAMP(3) NOT NULL, UPDATED_BY_INSTANCE CHAR(36) NOT NULL)");

            // Player data
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS PLAYERS (UUID CHAR(36) NOT NULL PRIMARY KEY, DATA LONGTEXT NOT NULL)");
            // Track last update time per player so instances can detect changes
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS PLAYER_UPDATE_STATE (UUID CHAR(36) NOT NULL PRIMARY KEY, LAST_UPDATED TIMESTAMP NOT NULL, UPDATED_BY_INSTANCE CHAR(36) NOT NULL)");

            // If sequence table is empty, initialize AUTO_INCREMENT to max(image_index) + 1
            try (
                Statement s2 = connection.createStatement();
                ResultSet rsCount = s2.executeQuery("SELECT COUNT(*) FROM IMAGE_MAP_INDEX_SEQUENCE");
            ) {
                int count = 0;
                if (rsCount.next()) {
                    count = rsCount.getInt(1);
                }
                if (count == 0) {
                    int nextVal = 1;
                    try (Statement s3 = connection.createStatement();
                         ResultSet rsMax = s3.executeQuery("SELECT MAX(IMAGE_INDEX) FROM IMAGE_MAPS")) {
                        if (rsMax.next()) {
                            int max = rsMax.getInt(1);
                            if (!rsMax.wasNull()) {
                                nextVal = max + 1;
                            }
                        }
                    }
                    try (Statement alter = connection.createStatement()) {
                        alter.executeUpdate("ALTER TABLE IMAGE_MAP_INDEX_SEQUENCE AUTO_INCREMENT = " + nextVal);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to prepare database schema", e);
        }
    }

    public File getLocalDataFolder() {
        return localDataFolder;
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    @Override
    public UUID getInstanceId() {
        return instanceId;
    }

    @Override
    public JdbcImageFrameStorageLoader getLoader() {
        return ImageFrameStorageLoaders.JDBC;
    }

    @Override
    public LazyBufferedImageSource getSource(int imageIndex, String fileName) {
        return new MySqlLazyBufferedImageSource(this, imageIndex, fileName);
    }

    @Override
    public Set<Integer> getAllImageIndexes() {
        String sql = "SELECT IMAGE_INDEX FROM IMAGE_MAPS";
        Set<Integer> result = new HashSet<>();
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                result.add(rs.getInt("IMAGE_INDEX"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to fetch all ImageMap indexes from database", e);
        }
        return result;
    }

    @Override
    public boolean hasImageMapData(int imageIndex) {
        String sql = "SELECT 1 FROM IMAGE_MAPS WHERE IMAGE_INDEX = ?";
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(sql);
        ) {
            ps.setInt(1, imageIndex);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to check ImageMap existence for image index " + imageIndex, e);
        }
    }

    @Override
    public void prepareImageIndex(ImageMap map, IntConsumer imageIndexSetter) {
        int originalImageIndex = map.getImageIndex();
        if (originalImageIndex < 0) {
            try {
                int newIndex = allocateNewImageIndex();
                imageIndexSetter.accept(newIndex);
            } catch (SQLException e) {
                throw new RuntimeException("Unable to allocate new image index from database", e);
            }
        }
    }

    private int allocateNewImageIndex() throws SQLException {
        String sql = "INSERT INTO IMAGE_MAP_INDEX_SEQUENCE VALUES (NULL)";
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    throw new SQLException("No generated key returned for image_map_index_sequence insert");
                }
                int id = rs.getInt(1);

                // Occasionally prune old rows (does not affect AUTO_INCREMENT)
                if (id % 50 == 0) {
                    String pruneSql = "DELETE FROM IMAGE_MAP_INDEX_SEQUENCE WHERE ID < ?";
                    try (PreparedStatement prune = connection.prepareStatement(pruneSql)) {
                        prune.setInt(1, id - 25); // keep a small recent window
                        prune.executeUpdate();
                    }
                }
                return id;
            }
        }
    }

    @Override
    public void deleteMap(int imageIndex) {
        String selectInstanceDataSql = "SELECT INSTANCE_ID, DATA FROM INSTANCE_IMAGE_MAP_DATA WHERE IMAGE_INDEX = ?";
        String insertDeletedSql = "INSERT INTO DELETED_MAPS (INSTANCE_ID, MAP_ID) VALUES (?, ?) ON DUPLICATE KEY UPDATE MAP_ID = MAP_ID";
        String deleteImagesSql = "DELETE FROM IMAGE_MAP_IMAGES WHERE IMAGE_INDEX = ?";
        String deleteInstanceDataSql = "DELETE FROM INSTANCE_IMAGE_MAP_DATA WHERE IMAGE_INDEX = ?";
        String deleteMapSql = "DELETE FROM IMAGE_MAPS WHERE IMAGE_INDEX = ?";
        String sqlUpdateState = "INSERT INTO IMAGE_MAP_UPDATE_STATE (IMAGE_INDEX, LAST_UPDATED, UPDATED_BY_INSTANCE) VALUES (?, NOW(), ?) ON DUPLICATE KEY UPDATE LAST_UPDATED = VALUES(LAST_UPDATED), UPDATED_BY_INSTANCE = VALUES(UPDATED_BY_INSTANCE)";
        try (Connection connection = dataSource.getConnection()) {
            // 1) For this imageIndex, collect all mapids from all instances and insert them into deleted_maps
            try (PreparedStatement psSelect = connection.prepareStatement(selectInstanceDataSql)) {
                psSelect.setInt(1, imageIndex);
                try (
                    ResultSet rs = psSelect.executeQuery();
                    PreparedStatement psInsert = connection.prepareStatement(insertDeletedSql);
                ) {
                    while (rs.next()) {
                        String instId = rs.getString("INSTANCE_ID");
                        String json = rs.getString("DATA");
                        if (json == null) {
                            continue;
                        }
                        JsonObject obj = GSON.fromJson(json, JsonObject.class);
                        if (obj == null || !obj.has("mapdata") || !obj.get("mapdata").isJsonArray()) {
                            continue;
                        }
                        JsonArray mapdataArray = obj.getAsJsonArray("mapdata");
                        for (JsonElement el : mapdataArray) {
                            if (!el.isJsonObject()) {
                                continue;
                            }
                            JsonObject entry = el.getAsJsonObject();
                            if (!entry.has("mapid")) {
                                continue;
                            }
                            int mapId = entry.get("mapid").getAsInt();
                            psInsert.setString(1, instId);
                            psInsert.setInt(2, mapId);
                            psInsert.addBatch();
                        }
                    }
                    psInsert.executeBatch();
                }
            }
            // 2) Delete images (PNG blobs)
            try (PreparedStatement ps = connection.prepareStatement(deleteImagesSql)) {
                ps.setInt(1, imageIndex);
                ps.executeUpdate();
            }
            // 3) Delete per-instance JSON payloads
            try (PreparedStatement ps = connection.prepareStatement(deleteInstanceDataSql)) {
                ps.setInt(1, imageIndex);
                ps.executeUpdate();
            }
            // 4) Delete the map metadata itself
            try (PreparedStatement ps = connection.prepareStatement(deleteMapSql)) {
                ps.setInt(1, imageIndex);
                ps.executeUpdate();
            }
            // 5) Record the change in update-state so other instances know something happened
            try (PreparedStatement ps = connection.prepareStatement(sqlUpdateState)) {
                ps.setInt(1, imageIndex);
                ps.setString(2, instanceId.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Error while deleting ImageMap " + imageIndex + " from database.");
            e.printStackTrace();
        }
    }

    @Override
    public JsonObject loadImageMapData(int imageIndex) throws IOException {
        String sql = "SELECT BASE.DATA AS BASE_DATA, INST.DATA AS INST_DATA FROM IMAGE_MAPS BASE LEFT JOIN INSTANCE_IMAGE_MAP_DATA INST ON INST.IMAGE_INDEX = BASE.IMAGE_INDEX AND INST.INSTANCE_ID = ? WHERE BASE.IMAGE_INDEX = ?";
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(sql);
        ) {
            ps.setString(1, instanceId.toString());
            ps.setInt(2, imageIndex);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IOException("Image map data not found for image index " + imageIndex);
                }
                String baseJsonString = rs.getString("BASE_DATA");
                JsonObject baseJson = GSON.fromJson(baseJsonString, JsonObject.class);

                String instanceJsonString = rs.getString("INST_DATA");
                JsonObject instanceJson = instanceJsonString == null ? new JsonObject() : GSON.fromJson(instanceJsonString, JsonObject.class);

                return JsonUtils.merge(baseJson, instanceJson).getAsJsonObject();
            }
        } catch (SQLException e) {
            throw new IOException("Unable to load ImageMap data for image index " + imageIndex, e);
        }
    }

    @Override
    public List<MutablePair<String, Future<? extends ImageMap>>> loadMaps(ImageMapManager imageMapManager, Set<Integer> deletedMapIds, IFPlayerManager ifPlayerManager) {
        List<MutablePair<String, Future<? extends ImageMap>>> futures = new ArrayList<>();

        String sqlMaps = "SELECT BASE.IMAGE_INDEX AS IMAGE_INDEX, BASE.DATA AS BASE_DATA, INST.DATA AS INST_DATA FROM IMAGE_MAPS BASE LEFT JOIN INSTANCE_IMAGE_MAP_DATA INST ON INST.IMAGE_INDEX = BASE.IMAGE_INDEX AND INST.INSTANCE_ID = ? ORDER BY BASE.IMAGE_INDEX ASC";
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(sqlMaps);
        ) {
            ps.setString(1, instanceId.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int imageIndex = rs.getInt("IMAGE_INDEX");

                    String baseJsonString = rs.getString("BASE_DATA");
                    JsonObject baseJson = GSON.fromJson(baseJsonString, JsonObject.class);

                    String instanceJsonString = rs.getString("INST_DATA");
                    JsonObject instanceJson = instanceJsonString == null ? new JsonObject() : GSON.fromJson(instanceJsonString, JsonObject.class);

                    JsonObject mergedJson = JsonUtils.merge(baseJson, instanceJson).getAsJsonObject();

                    Future<? extends ImageMap> future = ImageMapLoaders.load(imageMapManager, mergedJson);
                    futures.add(new MutablePair<>("database:" + imageIndex, future));
                }
            }
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load ImageMap data from database.");
            e.printStackTrace();
        }

        deletedMapIds.addAll(loadDeletedMaps());

        setupTasks(imageMapManager, ifPlayerManager);

        return futures;
    }

    public void activeUpdate(ImageMapManager imageMapManager, IFPlayerManager ifPlayerManager) {
        try {
            long lastUpdated = lastUpdateFetch.get();
            List<ImageMapUpdateInfo> imageMapUpdateInfo = getUpdatedImageIndexesSince(lastUpdated);
            for (ImageMapUpdateInfo info : imageMapUpdateInfo) {
                try {
                    imageMapManager.updateMap(info.getImageIndex(), info.exists());
                    lastUpdated = Math.max(lastUpdated, info.getLastUpdated());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            List<PlayerUpdateInfo> playerUpdateInfo = getUpdatedPlayersSince(lastUpdated);
            Map<UUID, JsonObject> playerInfo = loadPlayerDataBulk(playerUpdateInfo.stream().map(p -> p.getUniqueId()).collect(Collectors.toSet()));
            for (PlayerUpdateInfo info : playerUpdateInfo) {
                try {
                    UUID uuid = info.getUniqueId();
                    IFPlayer ifPlayer = ifPlayerManager.getIFPlayerIfLoaded(uuid);
                    JsonObject playerJson = playerInfo.get(uuid);
                    if (ifPlayer != null && playerJson != null) {
                        ifPlayer.applyUpdate(playerJson);
                    }
                    lastUpdated = Math.max(lastUpdated, info.getLastUpdated());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            lastUpdateFetch.set(lastUpdated);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveImageMapData(int imageIndex, JsonObject json) throws IOException {
        JsonObject instanceJson = new JsonObject();
        JsonArray instanceMapDataJson = new JsonArray();
        JsonArray mapDataJson = json.get("mapdata").getAsJsonArray();
        for (JsonElement dataJson : mapDataJson) {
            int mapId = dataJson.getAsJsonObject().remove("mapid").getAsInt();
            JsonObject instanceDataJson = new JsonObject();
            instanceDataJson.addProperty("mapid", mapId);
            instanceMapDataJson.add(instanceDataJson);
        }
        instanceJson.add("mapdata", instanceMapDataJson);

        String sqlMain = "INSERT INTO IMAGE_MAPS (IMAGE_INDEX, DATA) VALUES (?, ?) ON DUPLICATE KEY UPDATE DATA = VALUES(DATA)";
        String sqlInst = "INSERT INTO INSTANCE_IMAGE_MAP_DATA (IMAGE_INDEX, INSTANCE_ID, DATA) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE DATA = VALUES(DATA)";
        String sqlUpdateState = "INSERT INTO IMAGE_MAP_UPDATE_STATE (IMAGE_INDEX, LAST_UPDATED, UPDATED_BY_INSTANCE) VALUES (?, NOW(), ?) ON DUPLICATE KEY UPDATE LAST_UPDATED = VALUES(LAST_UPDATED), UPDATED_BY_INSTANCE = VALUES(UPDATED_BY_INSTANCE)";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(sqlMain)) {
                ps.setInt(1, imageIndex);
                ps.setString(2, GSON.toJson(json));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(sqlInst)) {
                ps.setInt(1, imageIndex);
                ps.setString(2, instanceId.toString());
                ps.setString(3, GSON.toJson(instanceJson));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(sqlUpdateState)) {
                ps.setInt(1, imageIndex);
                ps.setString(2, instanceId.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IOException("Unable to save ImageMap data for image index " + imageIndex, e);
        }
    }

    @Override
    public Set<Integer> loadDeletedMaps() {
        Set<Integer> deletedMapIds = new HashSet<>();
        String sqlDeleted = "SELECT MAP_ID FROM DELETED_MAPS WHERE INSTANCE_ID = ?";
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(sqlDeleted);
        ) {
            ps.setString(1, instanceId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    deletedMapIds.add(rs.getInt("MAP_ID"));
                }
            }
        } catch (SQLException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to load deleted ImageMap IDs from database.");
            e.printStackTrace();
        }
        return deletedMapIds;
    }

    @Override
    public void saveDeletedMaps(Set<Integer> deletedMapIds) {
        String deleteSql = "DELETE FROM DELETED_MAPS WHERE INSTANCE_ID = ?";
        String insertSql = "INSERT INTO DELETED_MAPS (INSTANCE_ID, MAP_ID) VALUES (?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            // Clear this instance's list
            try (PreparedStatement ps = connection.prepareStatement(deleteSql)) {
                ps.setString(1, instanceId.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (int mapId : deletedMapIds) {
                    ps.setString(1, instanceId.toString());
                    ps.setInt(2, mapId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ImageFrame] Unable to save deleted ImageMap IDs for instance " + instanceId);
            e.printStackTrace();
        }
    }

    @Override
    public JsonObject loadPlayerData(IFPlayerManager manager, UUID uuid) {
        String sql = "SELECT DATA FROM PLAYERS WHERE UUID = ?";
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(sql);
        ) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String jsonString = rs.getString("DATA");
                return GSON.fromJson(jsonString, JsonObject.class);
            }
        } catch (Exception e) {
            new RuntimeException("Unable to load ImageFrame player data for " + uuid + " from database", e).printStackTrace();
            return null;
        }
    }

    public Map<UUID, JsonObject> loadPlayerDataBulk(Set<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, JsonObject> result = new HashMap<>();
        StringBuilder sqlBuilder = new StringBuilder("SELECT UUID, DATA FROM PLAYERS WHERE UUID IN (");
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < uuids.size(); i++) {
            joiner.add("?");
        }
        sqlBuilder.append(joiner).append(")");
        String sql = sqlBuilder.toString();
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(sql);
        ) {
            int index = 1;
            for (UUID uuid : uuids) {
                ps.setString(index++, uuid.toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("UUID");
                    String jsonString = rs.getString("DATA");
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidStr);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    try {
                        JsonObject json = GSON.fromJson(jsonString, JsonObject.class);
                        if (json != null) {
                            result.put(uuid, json);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to bulk load player data from database", e);
        }
        return result;
    }

    @Override
    public void savePlayerData(UUID uuid, JsonObject json) throws IOException {
        String sqlPlayer = "INSERT INTO PLAYERS (UUID, DATA) VALUES (?, ?) ON DUPLICATE KEY UPDATE DATA = VALUES(DATA)";
        String sqlUpdateState = "INSERT INTO PLAYER_UPDATE_STATE (UUID, LAST_UPDATED, UPDATED_BY_INSTANCE) VALUES (?, NOW(), ?) ON DUPLICATE KEY UPDATE LAST_UPDATED = VALUES(LAST_UPDATED), UPDATED_BY_INSTANCE = VALUES(UPDATED_BY_INSTANCE)";

        try (Connection connection = dataSource.getConnection()) {
            // 1) Save player JSON
            try (PreparedStatement ps = connection.prepareStatement(sqlPlayer)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, GSON.toJson(json));
                ps.executeUpdate();
            }
            // 2) Update player update-state
            try (PreparedStatement ps = connection.prepareStatement(sqlUpdateState)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, instanceId.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IOException("Unable to save ImageFrame player data for " + uuid + " to database", e);
        }
    }

    @Override
    public Set<UUID> getAllSavedPlayerData() {
        String sql = "SELECT UUID FROM PLAYERS";
        Set<UUID> result = new HashSet<>();
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
        ) {
            while (rs.next()) {
                String uuidStr = rs.getString("uuid");
                try {
                    result.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignore) {
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Unable to load ImageFrame player data list from database", e);
        }
        return result;
    }

    public List<ImageMapUpdateInfo> getUpdatedImageIndexesSince(long sinceMillis) throws IOException {
        String sql = "SELECT UPD.IMAGE_INDEX AS IMAGE_INDEX, UPD.LAST_UPDATED AS LAST_UPDATED, BASE.IMAGE_INDEX AS EXISTS_FLAG FROM IMAGE_MAP_UPDATE_STATE UPD LEFT JOIN IMAGE_MAPS BASE ON BASE.IMAGE_INDEX = UPD.IMAGE_INDEX WHERE UPD.LAST_UPDATED > ? AND UPD.UPDATED_BY_INSTANCE <> ?";
        String pruneSql = "DELETE FROM IMAGE_MAP_UPDATE_STATE WHERE LAST_UPDATED < (NOW() - INTERVAL 14 DAY)";
        List<ImageMapUpdateInfo> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setTimestamp(1, new Timestamp(sinceMillis));
                ps.setString(2, instanceId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int imageIndex = rs.getInt("IMAGE_INDEX");
                        boolean exists = rs.getObject("EXISTS_FLAG") != null;
                        Timestamp lastUpdated = rs.getTimestamp("LAST_UPDATED");
                        result.add(new ImageMapUpdateInfo(imageIndex, exists, lastUpdated.getTime()));
                    }
                }
            }
            try (PreparedStatement prune = connection.prepareStatement(pruneSql)) {
                prune.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IOException("Unable to fetch updated image indices since " + sinceMillis, e);
        }
        return result;
    }

    public List<PlayerUpdateInfo> getUpdatedPlayersSince(long sinceMillis) throws IOException {
        String sql = "SELECT PUS.UUID AS UUID, PUS.LAST_UPDATED AS LAST_UPDATED FROM PLAYER_UPDATE_STATE PUS WHERE PUS.LAST_UPDATED > ? AND PUS.UPDATED_BY_INSTANCE <> ?";
        String pruneSql = "DELETE FROM PLAYER_UPDATE_STATE WHERE LAST_UPDATED < (NOW() - INTERVAL 14 DAY)";
        List<PlayerUpdateInfo> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setTimestamp(1, new Timestamp(sinceMillis));
                ps.setString(2, instanceId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String uuidStr = rs.getString("UUID");
                        Timestamp lastUpdated = rs.getTimestamp("LAST_UPDATED");
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(uuidStr);
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                        long lastUpdatedMillis = (lastUpdated != null) ? lastUpdated.getTime() : 0L;
                        result.add(new PlayerUpdateInfo(uuid, lastUpdatedMillis));
                    }
                }
            }
            try (PreparedStatement prune = connection.prepareStatement(pruneSql)) {
                prune.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IOException("Unable to fetch updated players since " + sinceMillis, e);
        }
        return result;
    }

    @Override
    public void close() {
        if (updateFetchTask != null) {
            updateFetchTask.cancel();
        }
        if (periodicSyncTask != null) {
            periodicSyncTask.cancel();
        }
        dataSource.close();
    }

    public static class MySqlLazyBufferedImageSource implements LazyBufferedImageSource {

        private final JdbcImageFrameStorage storage;
        private final int imageIndex;
        private final String fileName;

        public MySqlLazyBufferedImageSource(JdbcImageFrameStorage storage, int imageIndex, String fileName) {
            this.storage = storage;
            this.imageIndex = imageIndex;
            this.fileName = fileName;
        }

        @Override
        public BufferedImage loadImage() throws IOException {
            String sql = "SELECT IMAGE FROM IMAGE_MAP_IMAGES WHERE IMAGE_INDEX = ? AND FILE_NAME = ?";
            try (
                Connection connection = storage.getDataSource().getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
            ) {
                ps.setInt(1, imageIndex);
                ps.setString(2, fileName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    byte[] bytes = rs.getBytes("IMAGE");
                    if (bytes == null || bytes.length == 0) {
                        return null;
                    }
                    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
                        return ImageIO.read(inputStream);
                    }
                }
            } catch (SQLException e) {
                throw new IOException("Unable to load image data for imageIndex=" + imageIndex + ", fileName=" + fileName, e);
            }
        }

        @Override
        public void saveImage(BufferedImage image) throws IOException {
            String sql = "INSERT INTO IMAGE_MAP_IMAGES (IMAGE_INDEX, FILE_NAME, IMAGE) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE IMAGE = VALUES(IMAGE)";
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", outputStream);
                byte[] bytes = outputStream.toByteArray();
                try (
                    Connection connection = storage.getDataSource().getConnection();
                    PreparedStatement ps = connection.prepareStatement(sql);
                ) {
                    ps.setInt(1, imageIndex);
                    ps.setString(2, fileName);
                    ps.setBytes(3, bytes);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw new IOException("Unable to save image data for imageIndex=" + imageIndex + ", fileName=" + fileName, e);
            }
        }

        public int getImageIndex() {
            return imageIndex;
        }

        @Override
        public String getFileName() {
            return fileName;
        }

        @Override
        public MySqlLazyBufferedImageSource withFileName(String fileName) {
            return new MySqlLazyBufferedImageSource(storage, imageIndex, fileName);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            MySqlLazyBufferedImageSource that = (MySqlLazyBufferedImageSource) o;
            return imageIndex == that.imageIndex && Objects.equals(storage.dataSource, that.storage.dataSource) && Objects.equals(fileName, that.fileName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(storage.dataSource, imageIndex, fileName);
        }
    }

    public static class ImageMapUpdateInfo {

        private final int imageIndex;
        private final boolean exists;
        private final long lastUpdated;

        public ImageMapUpdateInfo(int imageIndex, boolean exists, long lastUpdated) {
            this.imageIndex = imageIndex;
            this.exists = exists;
            this.lastUpdated = lastUpdated;
        }

        public int getImageIndex() {
            return imageIndex;
        }

        public boolean exists() {
            return exists;
        }

        public long getLastUpdated() {
            return lastUpdated;
        }
    }

    public static class PlayerUpdateInfo {

        private final UUID uuid;
        private final long lastUpdated;

        public PlayerUpdateInfo(UUID uuid, long lastUpdated) {
            this.uuid = uuid;
            this.lastUpdated = lastUpdated;
        }

        public UUID getUniqueId() {
            return uuid;
        }

        public long getLastUpdated() {
            return lastUpdated;
        }
    }
}
