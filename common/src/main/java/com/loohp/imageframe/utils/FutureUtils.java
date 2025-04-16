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

package com.loohp.imageframe.utils;

import com.loohp.imageframe.ImageFrame;
import com.loohp.platformscheduler.Scheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class FutureUtils {

    public static Future<Void> callSyncMethod(Runnable task) {
        return callSyncMethod(() -> {
            task.run();
            return null;
        });
    }

    public static <T> Future<T> callSyncMethod(Callable<T> task) {
        return Scheduler.callSyncMethod(ImageFrame.plugin, task);
    }

    public static <T> Future<T> callAsyncMethod(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

}
