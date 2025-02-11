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

package com.loohp.imageframe.placeholderapi;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.ImageMap;
import com.loohp.imageframe.utils.ArrayUtils;
import com.loohp.imageframe.utils.ImageMapUtils;
import com.loohp.imageframe.utils.StringUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.concurrent.TimeUnit;

public class Placeholders extends PlaceholderExpansion {

    @Override
    public String getAuthor() {
        return String.join(", ", ImageFrame.plugin.getDescription().getAuthors());
    }

    @Override
    public String getIdentifier() {
        return "imageframe";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getRequiredPlugin() {
        return ImageFrame.plugin.getName();
    }

    @Override
    public String onRequest(OfflinePlayer offlineplayer, String identifier) {
        int firstUnderscore;
        boolean nameQuoted;
        if (identifier.startsWith("\"")) {
            int endQuote = identifier.indexOf("\"", 1);
            firstUnderscore = identifier.indexOf("_", Math.max(0, endQuote));
            nameQuoted = true;
        } else {
            firstUnderscore = identifier.indexOf("_");
            nameQuoted = false;
        }
        if (firstUnderscore >= 0) {
            String name;
            if (nameQuoted) {
                name = identifier.substring(1, firstUnderscore - 1);
            } else {
                name = identifier.substring(0, firstUnderscore);
            }
            String secondPart = identifier.substring(firstUnderscore + 1);
            CommandSender sender = offlineplayer instanceof CommandSender ? (CommandSender) offlineplayer : null;
            ImageMap imageMap = ImageMapUtils.getFromPlayerPrefixedName(sender, name);
            if (imageMap != null) {
                if (imageMap.requiresAnimationService() && secondPart.startsWith("playback_")) {
                    String playbackArg = secondPart.substring("playback_".length());
                    if (playbackArg.startsWith("bar_")) {
                        String[] args = playbackArg.substring("bar_".length()).split("_");
                        int length;
                        try {
                            length = Integer.parseInt(args[0]);
                        } catch (Exception e) {
                            length = 60;
                        }
                        String character = ArrayUtils.getOrElse(args, 1, "▎");
                        String colorCurrent = ArrayUtils.getOrElse(args, 2, "&c");
                        String colorRemaining = ArrayUtils.getOrElse(args, 3, "&7");
                        double position = imageMap.getCurrentPositionInSequence() / (double) (imageMap.getSequenceLength() - 1);
                        int currentLength = (int) Math.floor(length * position);
                        int remainingLength = length - currentLength;
                        return colorCurrent + StringUtils.repeat(character, currentLength) + colorRemaining + StringUtils.repeat(character, remainingLength);
                    } else if (playbackArg.equals("current")) {
                        long time = imageMap.getCurrentPositionInSequence() * 50L;
                        long hours = TimeUnit.MILLISECONDS.toHours(time);
                        long minutes = TimeUnit.MILLISECONDS.toMinutes(time) % 60;
                        long seconds = TimeUnit.MILLISECONDS.toSeconds(time) % 60;
                        long milliseconds = time % 1000;
                        long total = (imageMap.getSequenceLength() - 1) * 50L;
                        if (total >= 3600000) {
                            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
                        } else if (total >= 10000) {
                            return String.format("%02d:%02d", minutes, seconds);
                        } else {
                            return String.format("%01d.%03d", seconds, milliseconds);
                        }
                    } else if (playbackArg.equals("total")) {
                        long time = (imageMap.getSequenceLength() - 1) * 50L;
                        long hours = TimeUnit.MILLISECONDS.toHours(time);
                        long minutes = TimeUnit.MILLISECONDS.toMinutes(time) % 60;
                        long seconds = TimeUnit.MILLISECONDS.toSeconds(time) % 60;
                        long milliseconds = time % 1000;
                        if (time >= 3600000) {
                            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
                        } else if (time >= 10000) {
                            return String.format("%02d:%02d", minutes, seconds);
                        } else {
                            return String.format("%01d.%03d", seconds, milliseconds);
                        }
                    } else if (playbackArg.equals("pause")) {
                        return imageMap.isAnimationPaused() ? "⏸" : "⏵";
                    }
                }
            }
        }
        return null;
    }

}
