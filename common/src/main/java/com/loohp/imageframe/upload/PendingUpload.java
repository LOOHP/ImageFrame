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

import java.io.File;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bukkit.Bukkit;

public class PendingUpload {

    public static PendingUpload create(long timeToLive) {
        return new PendingUpload(UUID.randomUUID(), System.currentTimeMillis() + timeToLive, new CompletableFuture<>());
    }

    private final UUID id;
    private final long expire;
    private final CompletableFuture<File> future;

    private PendingUpload(UUID id, long expire, CompletableFuture<File> future) {
        this.id = id;
        this.expire = expire;
        this.future = future;
    }

    public UUID getId() {
        return id;
    }

    public long getExpire() {
        return expire;
    }

    protected CompletableFuture<File> getFuture() {
        return future;
    }

    public File getFileBlocking() throws InterruptedException, ImageUploadManager.LinkTimeoutException {
        try {
            return future.get(expire - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            throw new ImageUploadManager.LinkTimeoutException();
        }
    }

    /**
     * Get the upload URL for this pending upload
     *
     * @param baseUri The base URI of the upload server (e.g.,
     *                http://localhost:8080/images)
     * @param user    The user UUID
     * @return The complete URL with query parameters
     */
    public String getUrl(URI baseUri, UUID user) {
        String name = Bukkit.getOfflinePlayer(user).getName();
        StringBuilder query = new StringBuilder();
        query.append("user=").append(user)
                .append("&id=").append(id)
                .append("&expire=").append(expire);

        if (name != null) {
            query.append("&name=").append(name);
        }

        // Ensure base path ends with /upload
        String basePath = baseUri.toString();
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }

        return basePath + "upload?" + query.toString();
    }

    /**
     * Legacy method for backward compatibility
     */
    @Deprecated
    public String getUrl(String webAddress, UUID user) {
        String name = Bukkit.getOfflinePlayer(user).getName();
        if (name == null) {
            return webAddress + "?user=" + user + "&id=" + id + "&expire=" + expire;
        } else {
            return webAddress + "?user=" + user + "&id=" + id + "&expire=" + expire + "&name=" + name;
        }
    }
}