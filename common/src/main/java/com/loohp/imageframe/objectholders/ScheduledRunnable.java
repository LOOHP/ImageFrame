/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2023. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2023. Contributors
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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

import static com.loohp.imageframe.objectholders.Scheduler.FOLIA;

public abstract class ScheduledRunnable implements Runnable {
    
    private Object task;
    
    public synchronized boolean isCancelled() {
        checkScheduled();
        if (FOLIA) {
            return ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) task).isCancelled();
        } else {
            return ((BukkitTask) task).isCancelled();
        }
    }
    
    public synchronized void cancel() {
        checkScheduled();
        if (FOLIA) {
            ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) task).cancel();
        } else {
            ((BukkitTask) task).cancel();
        }
    }

    public synchronized Scheduler.ScheduledTask runTask(Plugin plugin, Entity entity) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(entity.getScheduler().run(plugin, st -> run(), null));
        } else {
            return setupTask(Bukkit.getScheduler().runTask(plugin, this));
        }
    }

    public synchronized Scheduler.ScheduledTask runTaskLater(Plugin plugin, long delay, Entity entity) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(entity.getScheduler().runDelayed(plugin, st -> run(), null, delay));
        } else {
            return setupTask(Bukkit.getScheduler().runTaskLater(plugin, this, delay));
        }
    }

    public synchronized Scheduler.ScheduledTask runTaskTimer(Plugin plugin, long delay, long period, Entity entity) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(entity.getScheduler().runAtFixedRate(plugin, st -> run(), null, delay, period));
        } else {
            return setupTask(Bukkit.getScheduler().runTaskTimer(plugin, this, delay, period));
        }
    }

    public synchronized Scheduler.ScheduledTask runTask(Plugin plugin, Location location) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(Bukkit.getRegionScheduler().run(plugin, location, st -> run()));
        } else {
            return setupTask(Bukkit.getScheduler().runTask(plugin, this));
        }
    }

    public synchronized Scheduler.ScheduledTask runTaskLater(Plugin plugin, long delay, Location location) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(Bukkit.getRegionScheduler().runDelayed(plugin, location, st -> run(), delay));
        } else {
            return setupTask(Bukkit.getScheduler().runTaskLater(plugin, this, delay));
        }
    }

    public synchronized Scheduler.ScheduledTask runTaskTimer(Plugin plugin, long delay, long period, Location location) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, st -> run(), delay, period));
        } else {
            return setupTask(Bukkit.getScheduler().runTaskTimer(plugin, this, delay, period));
        }
    }

    public synchronized Scheduler.ScheduledTask runTask(Plugin plugin) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(Bukkit.getGlobalRegionScheduler().run(plugin, st -> run()));
        } else {
            return setupTask(Bukkit.getScheduler().runTask(plugin, this));
        }
    }
    
    public synchronized Scheduler.ScheduledTask runTaskLater(Plugin plugin, long delay) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, st -> run(), delay));
        } else {
            return setupTask(Bukkit.getScheduler().runTaskLater(plugin, this, delay));
        }
    }
    
    public synchronized Scheduler.ScheduledTask runTaskTimer(Plugin plugin, long delay, long period) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, st -> run(), delay, period));
        } else {
            return setupTask(Bukkit.getScheduler().runTaskTimer(plugin, this, delay, period));
        }
    }

    public synchronized Scheduler.ScheduledTask runTaskAsynchronously(Plugin plugin) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(Bukkit.getAsyncScheduler().runNow(plugin, st -> run()));
        } else {
            return setupTask(Bukkit.getScheduler().runTaskAsynchronously(plugin, this));
        }
    }

    public synchronized Scheduler.ScheduledTask runTaskLaterAsynchronously(Plugin plugin, long delay) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(Bukkit.getAsyncScheduler().runDelayed(plugin, st -> run(), delay * 50, TimeUnit.MILLISECONDS));
        } else {
            return setupTask(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this, delay));
        }
    }
    
    public synchronized Scheduler.ScheduledTask runTaskTimerAsynchronously(Plugin plugin, long delay, long period) {
        checkNotYetScheduled();
        if (FOLIA) {
            return setupTask(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, st -> run(), Math.max(1, delay * 50), period * 50, TimeUnit.MILLISECONDS));
        } else {
            return setupTask(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, delay, period));
        }
    }

    private void checkScheduled() {
        if (task == null) {
            throw new IllegalStateException("Not scheduled yet");
        }
    }

    private void checkNotYetScheduled() {
        if (task != null) {
            throw new IllegalStateException("Already scheduled as " + task);
        }
    }

    private Scheduler.ScheduledTask setupTask(Object task) {
        this.task = task;
        return new Scheduler.ScheduledTask(task);
    }
}