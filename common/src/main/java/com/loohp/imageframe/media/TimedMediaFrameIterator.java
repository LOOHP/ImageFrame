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

package com.loohp.imageframe.media;

import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class TimedMediaFrameIterator implements Iterator<BufferedImage> {

    private final Iterator<MediaFrame> backing;
    private final int stepMs;

    private MediaFrame current;
    private int elapsedInFrame = 0;
    private boolean finished = false;

    public TimedMediaFrameIterator(Iterator<MediaFrame> backing, int stepMs) {
        this.backing = backing;
        this.stepMs = stepMs;

        if (backing.hasNext()) {
            this.current = backing.next();
        } else {
            finished = true;
        }
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }

    @Override
    public BufferedImage next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        MediaFrame result = current;
        if (current.getDurationMs() == -1) {
            return result.getImage();
        }
        elapsedInFrame += stepMs;
        while (current.getDurationMs() != -1 && elapsedInFrame >= current.getDurationMs()) {
            elapsedInFrame -= current.getDurationMs();
            if (backing.hasNext()) {
                current = backing.next();
            } else {
                finished = true;
                break;
            }
        }
        return result.getImage();
    }
}
