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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandSenderUtils {

    public static void sendMessage(CommandSender sender, String message) {
        if (sender instanceof BlockCommandSender) {
            Chunk chunk = ((BlockCommandSender) sender).getBlock().getChunk();
            Scheduler.executeOrScheduleSync(ImageFrame.plugin, () -> sender.sendMessage(message), chunk);
        } else {
            sender.sendMessage(message);
        }
    }

    public static void sendMessage(CommandSender sender, Component message) {
        sendMessage(sender, null, message);
    }

    @SuppressWarnings("deprecation")
    public static void sendMessage(CommandSender sender, ChatMessageType position, Component message) {
        String language = sender instanceof Player ? PlayerUtils.getPlayerLanguage((Player) sender) : ImageFrame.language;
        message = ImageFrame.languageManager.resolve(message, language);
        BaseComponent[] spigotComponent = ComponentSerializer.parse(GsonComponentSerializer.gson().serialize(message));
        if (sender instanceof BlockCommandSender) {
            Chunk chunk = ((BlockCommandSender) sender).getBlock().getChunk();
            Scheduler.executeOrScheduleSync(ImageFrame.plugin, () -> sender.spigot().sendMessage(spigotComponent), chunk);
        } else if (position != null && sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(position, spigotComponent);
        } else {
            sender.spigot().sendMessage(spigotComponent);
        }
    }

}
