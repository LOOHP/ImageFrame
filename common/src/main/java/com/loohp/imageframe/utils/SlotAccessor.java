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

import org.bukkit.inventory.ItemStack;

import java.util.function.IntFunction;

public interface SlotAccessor extends IntFunction<ItemStack> {

    static SlotAccessor of(IntFunction<ItemStack> getter, IntObjectBiConsumer<ItemStack> setter) {
        return new SlotAccessor() {
            @Override
            public ItemStack get(int slot) {
                return getter.apply(slot);
            }

            @Override
            public void set(int slot, ItemStack itemStack) {
                setter.accept(slot, itemStack);
            }
        };
    }

    default ItemStack apply(int value) {
        return get(value);
    }

    ItemStack get(int slot);

    default void set(int slot, ItemStack itemStack) {
        throw new UnsupportedOperationException("SlotAccessor is read only");
    }

}
