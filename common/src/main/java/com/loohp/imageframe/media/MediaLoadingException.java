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

import net.kyori.adventure.key.Key;

import java.io.IOException;
import java.util.List;

public class MediaLoadingException extends IOException {

    private final List<MediaLoadingExceptionEntry> exceptions;

    public MediaLoadingException(String message, List<MediaLoadingExceptionEntry> exceptions) {
        super(message);
        this.exceptions = exceptions;
    }

    @Override
    public void printStackTrace() {
        super.printStackTrace();
        System.err.println("Exceptions for each MediaLoader");
        for (MediaLoadingExceptionEntry entry : exceptions) {
            System.err.println("MediaLoader " + entry.getIdentifier().asString());
            entry.getException().printStackTrace();
        }
    }

    public static class MediaLoadingExceptionEntry {

        private final Key identifier;
        private final Exception exception;

        public MediaLoadingExceptionEntry(Key identifier, Exception exception) {
            this.identifier = identifier;
            this.exception = exception;
        }

        public Key getIdentifier() {
            return identifier;
        }

        public Exception getException() {
            return exception;
        }
    }

}
