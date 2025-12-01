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

import com.loohp.imageframe.utils.ComponentUtils;
import net.kyori.adventure.text.TranslatableComponent;

public class ComponentTemplate {

    public static ComponentTemplate template(String key, Object... arguments) {
        return new ComponentTemplate(key, arguments);
    }

    private final String key;
    private final Object[] arguments;

    private ComponentTemplate(String key, Object... arguments) {
        this.key = key;
        this.arguments = arguments;
    }

    public TranslatableComponent build(Object... extraArguments) {
        Object[] args = new Object[arguments.length + extraArguments.length];
        System.arraycopy(arguments, 0, args, 0, arguments.length);
        System.arraycopy(extraArguments, 0, args, arguments.length, extraArguments.length);
        return ComponentUtils.translatable(key, args);
    }

}
