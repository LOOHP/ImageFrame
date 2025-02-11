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

package com.loohp.imageframe.hooks.viaversion;

import com.viaversion.viaversion.api.Via;
import org.bukkit.entity.Player;

public class ViaHook {

    public static final int MAP_ID_EXPANSION_VERSION = 346;

    public static boolean isPlayerLegacy(Player player) {
        int version = Via.getAPI().getPlayerVersion(player.getUniqueId());
        return version >= 0 && version < MAP_ID_EXPANSION_VERSION;
    }

}
