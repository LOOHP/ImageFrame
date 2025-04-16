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

import org.bukkit.Bukkit;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PendingUpload {

    public static PendingUpload create() {
        return new PendingUpload(UUID.randomUUID(), System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5), new CompletableFuture<>());
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

    public String getUrl(String domain, UUID user) {
        String name = Bukkit.getOfflinePlayer(user).getName();
        if (name == null) {
            return domain + "?user=" + user + "&id=" + id + "&expire=" + expire;
        } else {
            return domain + "?user=" + user + "&id=" + id + "&expire=" + expire + "&name=" + name;
        }
    }
}