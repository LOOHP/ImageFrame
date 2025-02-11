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

package com.loohp.imageframe.objectholders;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;

public class BlockPosition {

    private final WeakReference<World> world;
    private final int x;
    private final int y;
    private final int z;

    public BlockPosition(World world, int x, int y, int z) {
        this.world = new WeakReference<>(world);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isWorldLoaded() {
        World world = this.world.get();
        return world != null && Bukkit.getWorld(world.getUID()) != null;
    }

    public World getWorld() {
        return world.get();
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public Block getBlock() {
        return getWorld().getBlockAt(x, y, z);
    }

    public Location toLocation() {
        return new Location(getWorld(), x, y, z);
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    public BoundingBox toBoundingBox() {
        return new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
    }

}
