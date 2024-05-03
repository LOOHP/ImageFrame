/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2024. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2024. Contributors
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
import com.loohp.imageframe.objectholders.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class FutureUtils {

    public static Future<Void> callSyncMethod(Runnable task) {
        if (Scheduler.FOLIA) {
            if (Bukkit.isGlobalTickThread()) {
                task.run();
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(ImageFrame.plugin, st -> {
                task.run();
                future.complete(null);
            });
            return future;
        } else {
            if (Bukkit.isPrimaryThread()) {
                task.run();
                return CompletableFuture.completedFuture(null);
            }
            return Bukkit.getScheduler().callSyncMethod(ImageFrame.plugin, () -> {
                task.run();
                return null;
            });
        }
    }

    public static <T> Future<T> callSyncMethod(Callable<T> task) {
        if (Scheduler.FOLIA) {
            if (Bukkit.isGlobalTickThread()) {
                try {
                    return CompletableFuture.completedFuture(task.call());
                } catch (Exception e) {
                    CompletableFuture<T> future = new CompletableFuture<>();
                    future.completeExceptionally(e);
                    return future;
                }
            }
            CompletableFuture<T> future = new CompletableFuture<>();
            Bukkit.getGlobalRegionScheduler().run(ImageFrame.plugin, st -> {
                try {
                    future.complete(task.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        } else {
            if (Bukkit.isPrimaryThread()) {
                try {
                    return CompletableFuture.completedFuture(task.call());
                } catch (Exception e) {
                    CompletableFuture<T> future = new CompletableFuture<>();
                    future.completeExceptionally(e);
                    return future;
                }
            }
            return Bukkit.getScheduler().callSyncMethod(ImageFrame.plugin, task);
        }
    }

    public static <T> Future<T> callSyncMethod(Callable<T> task, Callable<T> retired, Entity entity) {
        if (Scheduler.FOLIA) {
            if (Bukkit.isOwnedByCurrentRegion(entity)) {
                try {
                    return CompletableFuture.completedFuture(task.call());
                } catch (Exception e) {
                    CompletableFuture<T> future = new CompletableFuture<>();
                    future.completeExceptionally(e);
                    return future;
                }
            }
            CompletableFuture<T> future = new CompletableFuture<>();
            entity.getScheduler().run(ImageFrame.plugin, st -> {
                try {
                    future.complete(task.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }, () -> {
                try {
                    future.complete(retired.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        } else {
            if (Bukkit.isPrimaryThread()) {
                try {
                    return CompletableFuture.completedFuture(task.call());
                } catch (Exception e) {
                    CompletableFuture<T> future = new CompletableFuture<>();
                    future.completeExceptionally(e);
                    return future;
                }
            }
            return Bukkit.getScheduler().callSyncMethod(ImageFrame.plugin, task);
        }
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

    public static Runnable errorHandled(ThrowingRunnable runnable, Consumer<Throwable> errorHandler) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                errorHandler.accept(e);
            }
        };
    }

    public static <T> void applyWhenComplete(Future<T> future, Consumer<T> completionTask, Consumer<Throwable> errorHandler, boolean synced) {
        Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
            try {
                T t = future.get();
                if (synced) {
                    Scheduler.runTask(ImageFrame.plugin, () -> completionTask.accept(t));
                } else {
                    completionTask.accept(t);
                }
            } catch (InterruptedException | ExecutionException e) {
                errorHandler.accept(e);
            }
        });
    }

    @FunctionalInterface
    public interface ThrowingRunnable {

        void run() throws Throwable;

    }

}
