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

import java.io.ByteArrayOutputStream;

public class SizeLimitedByteArrayOutputStream extends ByteArrayOutputStream {

    private final long maxSize;

    public SizeLimitedByteArrayOutputStream(long maxSize) {
        this.maxSize = maxSize;
    }

    private void ensureSize(int added) {
        if ((size() + added) > maxSize) {
            throw new OversizeException("Size exceeded max size of " + maxSize + " bytes");
        }
    }

    @Override
    public synchronized void write(int b) {
        ensureSize(1);
        super.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        ensureSize(len);
        super.write(b, off, len);
    }

    public static class OversizeException extends RuntimeException {

        public OversizeException(String message) {
            super(message);
        }

    }

}
