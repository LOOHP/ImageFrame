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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class FileLazyMappedBufferedImage {

    public static FileLazyMappedBufferedImage fromFile(File file) {
        return new FileLazyMappedBufferedImage(file, null, null);
    }

    public static FileLazyMappedBufferedImage fromImage(BufferedImage image) {
        return new FileLazyMappedBufferedImage(null, image, null);
    }

    public static FileLazyMappedBufferedImage fromImageToFile(File file, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", file);
        return new FileLazyMappedBufferedImage(file, null, new WeakReference<>(image));
    }

    private File file;
    private BufferedImage strongReference;
    private WeakReference<BufferedImage> weakReference;

    private FileLazyMappedBufferedImage(File file, BufferedImage strongReference, WeakReference<BufferedImage> weakReference) {
        if (file == null && strongReference == null) {
            throw new IllegalArgumentException("One of file and strongReference must not be null");
        }
        if (file != null && strongReference != null) {
            throw new IllegalArgumentException("File and strongReference cannot both be not null");
        }
        this.file = file;
        this.strongReference = strongReference;
        this.weakReference = weakReference;
    }

    public File getFile() {
        return file;
    }

    public boolean canSetFile(File file) {
        if (this.file != null) {
            return this.file.equals(file);
        }
        return file != null;
    }

    public synchronized void setFile(File file) {
        if (this.file != null) {
            if (this.file.equals(file)) {
                return;
            }
            throw new IllegalStateException("Cannot change file location");
        }
        if (file == null) {
            throw new IllegalArgumentException("Cannot set file to null");
        }
        try {
            ImageIO.write(strongReference, "png", file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.file = file;
        this.weakReference = new WeakReference<>(strongReference);
        this.strongReference = null;
    }

    public synchronized BufferedImage get() {
        if (strongReference != null) {
            return strongReference;
        }
        BufferedImage image;
        if (weakReference != null && (image = weakReference.get()) != null) {
            return image;
        }
        try {
            image = ImageIO.read(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.weakReference = new WeakReference<>(image);
        return image;
    }

    public synchronized BufferedImage getIfLoaded() {
        if (strongReference != null) {
            return strongReference;
        }
        return weakReference.get();
    }

}
