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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PendingUpload {

    public static PendingUpload create(UUID creator, String language, String player, String imageMap, int width, int height, long timeToLive) {
        return new PendingUpload(UUID.randomUUID(), creator, System.currentTimeMillis() + timeToLive, language, player, imageMap, width, height, new CompletableFuture<>());
    }

    private final UUID id;
    private final UUID creator;
    private final long expire;
    private final String language;
    private final String player;
    private final String imageMap;
    private final int width;
    private final int height;
    private final CompletableFuture<File> future;

    private PendingUpload(UUID id, UUID creator, long expire, String language, String player, String imageMap, int width, int height, CompletableFuture<File> future) {
        this.id = id;
        this.creator = creator;
        this.expire = expire;
        this.language = language;
        this.player = player;
        this.imageMap = imageMap;
        this.width = width;
        this.height = height;
        this.future = future;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCreator() {
        return creator;
    }

    public long getExpire() {
        return expire;
    }

    public String getLanguage() {
        return language;
    }

    public String getPlayer() {
        return player;
    }

    public String getImageMap() {
        return imageMap;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    protected void invalidateExpired() {
        future.completeExceptionally(new PendingUploadExpiredException());
    }

    protected void complete(File file) {
        future.complete(file);
    }

    public Future<File> getFile() {
        return future;
    }

    public File getFileBlocking() throws InterruptedException, PendingUploadExpiredException {
        try {
            return future.get(expire - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            throw new PendingUploadExpiredException(e);
        }
    }

    public String getUrl(String domain) {
        return domain + "?id=" + id;
    }
}