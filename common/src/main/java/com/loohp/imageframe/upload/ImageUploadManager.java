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

package com.loohp.imageframe.upload;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.FileUtils;
import com.loohp.imageframe.utils.JarUtils;
import com.loohp.imageframe.utils.PlayerUtils;
import com.loohp.imageframe.utils.SizeLimitedByteArrayOutputStream;
import com.loohp.platformscheduler.Scheduler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.twelvemonkeys.net.MIMEUtil;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.fileupload.MultipartStream;
import org.bukkit.Bukkit;

public class ImageUploadManager implements AutoCloseable {

    public static final long EXPIRATION = TimeUnit.MINUTES.toMillis(5);

    private final HttpServer server;
    private final File webRootDir;
    private final File uploadDir;
    private final Map<UUID, PendingUpload> pendingUploads;
    private final AtomicLong imagesUploadedCounter;

    public ImageUploadManager(boolean enabled, String host, int port) {
        this.webRootDir = new File(ImageFrame.plugin.getDataFolder(), "upload/web");
        this.uploadDir = new File(ImageFrame.plugin.getDataFolder(), "upload/images");
        this.imagesUploadedCounter = new AtomicLong(0);

        Cache<UUID, PendingUpload> cache = CacheBuilder.newBuilder()
                .expireAfterAccess(EXPIRATION, TimeUnit.MILLISECONDS)
                .removalListener((RemovalNotification<UUID, PendingUpload> notification) -> {
                    if (notification.wasEvicted()) {
                        PendingUpload pendingUpload = notification.getValue();
                        if (pendingUpload != null) {
                            pendingUpload.invalidateExpired();
                        }
                    }
                })
                .build();
        this.pendingUploads = cache.asMap();

        HttpServer server = null;
        try {
            FileUtils.removeFolderRecursively(uploadDir);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            if (!webRootDir.exists()) {
                webRootDir.mkdirs();
            }
            JarUtils.copyFolderFromJar("upload/web", ImageFrame.plugin.getDataFolder(), JarUtils.CopyOption.COPY_IF_NOT_EXIST);
            if (enabled) {
                System.setProperty("sun.net.httpserver.maxReqTime", "30");
                System.setProperty("sun.net.httpserver.maxRspTime", "30");
                server = HttpServer.create(new InetSocketAddress(host, port), 8);
                server.createContext("/", new FileHandler());
                server.createContext("/upload", new UploadHandler());
                server.setExecutor(Executors.newFixedThreadPool(8));
                server.start();
            }
        } catch (BindException e) {
            new RuntimeException("Unable to start ImageFrame upload server (Perhaps port + " + port + " is already used by another program?)", e).printStackTrace();
        } catch (IOException e) {
            new RuntimeException("Unable to start ImageFrame upload server", e).printStackTrace();
        }
        this.server = server;
    }

    public PendingUpload newPendingUpload(UUID creator, String player, String imageMap, int width, int height) {
        invalidatePendingUploads(creator);
        String language = PlayerUtils.getPlayerLanguage(Bukkit.getPlayer(creator));
        PendingUpload pendingUpload = PendingUpload.create(creator, language, player, imageMap, width, height, EXPIRATION);
        pendingUploads.put(pendingUpload.getId(), pendingUpload);
        return pendingUpload;
    }

    public AtomicLong getImagesUploadedCounter() {
        return imagesUploadedCounter;
    }

    public boolean wasUploaded(String url) {
        try {
            File file = Paths.get(new URL(url).toURI()).toFile();
            return uploadDir.equals(file.getParentFile());
        } catch (Exception e) {
            return false;
        }
    }

    public void invalidatePendingUploads(UUID creator) {
        for (PendingUpload pendingUpload : pendingUploads.values()) {
            if (pendingUpload.getCreator().equals(creator)) {
                pendingUpload.invalidateExpired();
            }
        }
    }

    public boolean isOperational() {
        return server != null;
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }

    private Path resolvePath(Path baseDirPath, Path userPath) {
        Path resolvedPath = baseDirPath.resolve(userPath).normalize();
        if (!resolvedPath.startsWith(baseDirPath)) {
            return baseDirPath;
        }
        return resolvedPath;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    map.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return map;
    }

    private PendingUpload findPendingUpload(String id) {
        try {
            PendingUpload pendingUpload = pendingUploads.get(UUID.fromString(id));
            if (!pendingUpload.getId().equals(UUID.fromString(id))) {
                return null;
            }
            return pendingUpload;
        } catch (Exception e) {
            return null;
        }
    }

    private String localize(String message, String language) {
        for (String key : ImageFrame.languageManager.getTranslationKeys(language)) {
            message = message.replace("{{" + key + "}}", ImageFrame.languageManager.getTranslation(key, language));
        }
        return message;
    }

    private String populateData(PendingUpload pendingUpload, String message) {
        if (pendingUpload == null) {
            message = message.replace("%player%", "N/A");
            message = message.replace("%imagemap%", "N/A");
            message = message.replace("%width%", "0");
            message = message.replace("%height%", "0");
            message = message.replace("%expire%", "0");
        } else {
            message = message.replace("%player%", pendingUpload.getPlayer());
            message = message.replace("%imagemap%", pendingUpload.getImageMap());
            message = message.replace("%width%", String.valueOf(pendingUpload.getWidth()));
            message = message.replace("%height%", String.valueOf(pendingUpload.getHeight()));
            message = message.replace("%expire%", String.valueOf(pendingUpload.getExpire()));
        }
        return message;
    }

    private class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                    return;
                }
                if (!webRootDir.exists()) {
                    webRootDir.mkdirs();
                }

                String query = exchange.getRequestURI().getQuery();
                Map<String, String> queryParams = parseQueryParams(query);
                String id = queryParams.get("id");

                PendingUpload pendingUpload = findPendingUpload(id);
                String language = pendingUpload == null ? ImageFrame.language : pendingUpload.getLanguage();

                File file = resolvePath(webRootDir.toPath().toAbsolutePath(), Paths.get("." + exchange.getRequestURI().getPath())).toFile();
                File targetFile;
                byte[] bytes;
                if (file.exists()) {
                    if (file.isDirectory()) {
                        targetFile = new File(file, "index.html");
                        bytes = Files.readAllBytes(targetFile.toPath());
                    } else {
                        targetFile = file;
                        bytes = Files.readAllBytes(file.toPath());
                    }
                } else {
                    targetFile = new File(webRootDir, "index.html");
                    bytes = Files.readAllBytes(new File(webRootDir, "index.html").toPath());
                }
                String targetFileName = targetFile.getName();
                if (targetFileName.contains(".")) {
                    String targetFileExtension = targetFileName.substring(targetFileName.lastIndexOf(".") + 1);
                    String mimeType = MIMEUtil.getMIMEType(targetFileExtension);
                    if (mimeType != null) {
                        exchange.getResponseHeaders().set("Content-Type", mimeType);
                    }
                }

                String contentType = exchange.getResponseHeaders().getFirst("Content-Type");
                if (contentType != null && contentType.startsWith("text/")) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
                        for (Iterator<String> itr = reader.lines().iterator(); itr.hasNext(); ) {
                            outputStream.write((populateData(pendingUpload, localize(itr.next(), language)) + "\n").getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    bytes = outputStream.toByteArray();
                }

                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                exchange.close();
            }
        }
    }

    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                String query = exchange.getRequestURI().getQuery();
                Map<String, String> queryParams = parseQueryParams(query);
                String id = queryParams.get("id");

                PendingUpload pendingUpload = findPendingUpload(id);
                if (pendingUpload == null) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid or missing id\"}");
                    return;
                }

                List<String> contentType = Arrays.asList(exchange.getRequestHeaders().getFirst("Content-Type").split(";"));
                if (!contentType.get(0).trim().equals("multipart/form-data")) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid content type\"}");
                    return;
                }
                byte[] boundary = contentType.stream()
                        .map(s -> s.trim())
                        .filter(s -> s.startsWith("boundary="))
                        .findFirst()
                        .map(s -> s.substring("boundary=".length()).getBytes(StandardCharsets.UTF_8))
                        .orElse(null);
                if (boundary == null) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid multipart boundary\"}");
                    return;
                }

                SizeLimitedByteArrayOutputStream output = new SizeLimitedByteArrayOutputStream(ImageFrame.maxImageFileSize);

                try (InputStream inputStream = exchange.getRequestBody()) {
                    MultipartStream multipartStream = new MultipartStream(inputStream, boundary, 2048, null);
                    boolean nextPart = multipartStream.skipPreamble();
                    while (nextPart) {
                        List<String> headers = Arrays.asList(multipartStream.readHeaders().split(";"));
                        String name = headers.stream()
                                .map(s -> s.trim()).filter(s -> s.startsWith("name="))
                                .findFirst()
                                .map(s -> s.substring("name=".length()))
                                .orElse(null);
                        if ("image".equals(name) || "\"image\"".equals(name)) {
                            multipartStream.readBodyData(output);
                            break;
                        } else {
                            multipartStream.discardBodyData();
                            nextPart = multipartStream.readBoundary();
                        }
                    }
                }
                byte[] fileData = output.toByteArray();

                if (!uploadDir.exists()) {
                    uploadDir.mkdir();
                }

                // Save the file with UUID as the filename
                File outputFile = new File(uploadDir, id + ".png");
                Files.write(outputFile.toPath(), fileData);
                imagesUploadedCounter.incrementAndGet();

                pendingUploads.remove(UUID.fromString(id));
                pendingUpload.complete(outputFile);
                Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, () -> outputFile.delete(), EXPIRATION / 50);

                // Send response
                sendResponse(exchange, 200, "{\"message\":\"File uploaded successfully\"}");
            } finally {
                exchange.close();
            }
        }

        // Send JSON response
        private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, message.length());
            exchange.getResponseBody().write(message.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().close();
        }
    }

}
