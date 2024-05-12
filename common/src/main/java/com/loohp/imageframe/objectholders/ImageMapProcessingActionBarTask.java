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

package com.loohp.imageframe.objectholders;

import com.loohp.imageframe.ImageFrame;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class ImageMapProcessingActionBarTask implements Runnable {

    private final Player player;
    private final Scheduler.ScheduledTask task;
    private int tick;

    public ImageMapProcessingActionBarTask(Player player) {
        this.player = player;
        this.tick = 0;
        this.task = Scheduler.runTaskTimerAsynchronously(ImageFrame.plugin, this, 0, 10);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void run() {
        int dots = tick++ % 4;
        StringBuilder dotString = new StringBuilder();
        for (int i = 0; i < dots; i++) {
            dotString.append(".");
        }
        String message = ImageFrame.messageImageMapProcessingActionBar.replace("{Dots}", dotString.toString());
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    @SuppressWarnings("deprecation")
    public void complete(String message) {
        task.cancel();
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

}
