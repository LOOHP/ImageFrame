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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class StandardLazyMappedBufferedImage implements LazyMappedBufferedImage {

    public static StandardLazyMappedBufferedImage fromSource(LazyBufferedImageSource source) {
        return new StandardLazyMappedBufferedImage(source, null, null);
    }

    public static StandardLazyMappedBufferedImage fromImage(BufferedImage image) {
        return new StandardLazyMappedBufferedImage(null, image, null);
    }

    public static StandardLazyMappedBufferedImage fromImageToFile(LazyBufferedImageSource source, BufferedImage image) throws IOException {
        source.saveImage(image);
        return new StandardLazyMappedBufferedImage(source, null, new WeakReference<>(image));
    }

    private LazyBufferedImageSource source;
    private BufferedImage strongReference;
    private WeakReference<BufferedImage> weakReference;

    private StandardLazyMappedBufferedImage(LazyBufferedImageSource source, BufferedImage strongReference, WeakReference<BufferedImage> weakReference) {
        if (source == null && strongReference == null) {
            throw new IllegalArgumentException("One of source and strongReference must not be null");
        }
        if (source != null && strongReference != null) {
            throw new IllegalArgumentException("Source and strongReference cannot both be not null");
        }
        this.source = source;
        this.strongReference = strongReference;
        this.weakReference = weakReference;
    }

    @Override
    public LazyBufferedImageSource getSource() {
        return source;
    }

    @Override
    public boolean canSetSource(LazyBufferedImageSource source) {
        if (this.source != null) {
            return this.source.equals(source);
        }
        return source != null;
    }

    @Override
    public synchronized void setSource(LazyBufferedImageSource source) {
        if (this.source != null) {
            if (this.source.equals(source)) {
                return;
            }
            throw new IllegalStateException("Cannot change source location");
        }
        if (source == null) {
            throw new IllegalArgumentException("Cannot set source to null");
        }
        try {
            source.saveImage(strongReference);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.source = source;
        this.weakReference = new WeakReference<>(strongReference);
        this.strongReference = null;
    }

    @Override
    public synchronized BufferedImage get() {
        if (strongReference != null) {
            return strongReference;
        }
        BufferedImage image;
        if (weakReference != null && (image = weakReference.get()) != null) {
            return image;
        }
        try {
            image = source.loadImage();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.weakReference = new WeakReference<>(image);
        return image;
    }

    @Override
    public synchronized BufferedImage getIfLoaded() {
        if (strongReference != null) {
            return strongReference;
        }
        return weakReference.get();
    }

}
