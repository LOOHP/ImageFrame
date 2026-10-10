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

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class StandardLazyMappedBufferedImage implements LazyMappedBufferedImage {

    protected static LazyDataSource.Loader<BufferedImage> imageLoader() {
        return in -> ImageIO.read(in);
    }

    protected static LazyDataSource.Reader imageReader(BufferedImage destination, int x, int y) {
        return input -> {
            try (ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
                if (imageInput == null) {
                    throw new IOException("Unable to create image input stream");
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) {
                    throw new IOException("No ImageIO reader found for image");
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(imageInput, true, true);
                    ImageReadParam param = reader.getDefaultReadParam();
                    param.setDestination(destination);
                    param.setDestinationOffset(new Point(x, y));
                    reader.read(0, param);
                } finally {
                    reader.dispose();
                }
            }
        };
    }

    protected static LazyDataSource.Writer imageWriter(BufferedImage image) {
        return out -> ImageIO.write(image, "png", out);
    }

    public static StandardLazyMappedBufferedImage fromSource(LazyDataSource source) {
        return new StandardLazyMappedBufferedImage(source, null, null);
    }

    public static StandardLazyMappedBufferedImage fromImage(BufferedImage image) {
        return new StandardLazyMappedBufferedImage(null, image, null);
    }

    public static StandardLazyMappedBufferedImage fromImageToFile(LazyDataSource source, BufferedImage image) throws IOException {
        source.save(imageWriter(image));
        return new StandardLazyMappedBufferedImage(source, null, new WeakReference<>(image));
    }

    private LazyDataSource source;
    private BufferedImage strongReference;
    private WeakReference<BufferedImage> weakReference;

    private StandardLazyMappedBufferedImage(LazyDataSource source, BufferedImage strongReference, WeakReference<BufferedImage> weakReference) {
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
    public LazyDataSource getSource() {
        return source;
    }

    @Override
    public boolean canSetSource(LazyDataSource source) {
        if (this.source != null) {
            return this.source.equals(source);
        }
        return source != null;
    }

    @Override
    public synchronized void setSource(LazyDataSource source) {
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
            source.save(imageWriter(strongReference));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.source = source;
        this.weakReference = new WeakReference<>(strongReference);
        this.strongReference = null;
    }

    @Override
    public void saveCopy(LazyDataSource source) {
        try {
            source.save(imageWriter(get()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
            image = source.load(imageLoader());
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
        return weakReference == null ? null : weakReference.get();
    }

    @Override
    public synchronized void drawInto(BufferedImage destination, int x, int y) {
        BufferedImage loaded = getIfLoaded();
        if (loaded == null) {
            try {
                source.read(imageReader(destination, x, y));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            Graphics2D g2 = destination.createGraphics();
            try {
                g2.setComposite(AlphaComposite.Src);
                g2.drawImage(loaded, x, y, null);
            } finally {
                g2.dispose();
            }
        }
    }

}
