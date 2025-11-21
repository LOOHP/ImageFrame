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

import com.google.gson.JsonObject;
import com.loohp.imageframe.media.MediaFrame;
import com.loohp.imageframe.media.MediaLoader;
import net.kyori.adventure.key.Key;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

public abstract class ImageMapLoader<T extends ImageMap, C extends ImageMapCreateInfo> {

    protected final List<MediaLoader> mediaLoaders;

    protected ImageMapLoader() {
        this.mediaLoaders = new CopyOnWriteArrayList<>();
    }

    public abstract Key getIdentifier();

    public String getLegacyType() {
        return null;
    }

    public abstract Class<T> getImageMapClass();

    public abstract Class<C> getImageMapCreateInfoClass();

    public abstract List<String> getExtraPermissions();

    public abstract boolean isSupported(String imageType);

    public ImageMapLoaderPriority getPriority(String imageType) {
        return ImageMapLoaderPriority.NORMAL;
    }

    public void registerMediaLoaderFirst(MediaLoader mediaLoader) {
        mediaLoaders.add(0, mediaLoader);
    }

    public void registerMediaLoaderLast(MediaLoader mediaLoader) {
        mediaLoaders.add(mediaLoader);
    }

    public void registerMediaLoaderBefore(Key identifier, MediaLoader mediaLoader) {
        int index = indexOfMediaLoader(identifier);
        if (index < 0) {
            registerMediaLoaderFirst(mediaLoader);
        } else {
            mediaLoaders.add(index, mediaLoader);
        }
    }

    public void registerMediaLoaderAfter(Key identifier, MediaLoader mediaLoader) {
        int index = indexOfMediaLoader(identifier);
        if (index < 0) {
            registerMediaLoaderLast(mediaLoader);
        } else {
            mediaLoaders.add(index + 1, mediaLoader);
        }
    }

    protected int indexOfMediaLoader(Key identifier) {
        for (int i = 0; i < mediaLoaders.size(); i++) {
            if (mediaLoaders.get(i).getIdentifier().equals(identifier)) {
                return i;
            }
        }
        return -1;
    }

    public Iterator<MediaFrame> tryLoadMedia(String url) throws IOException {
        List<IOException> exceptions = new ArrayList<>();
        for (MediaLoader mediaLoader : mediaLoaders) {
            try {
                if (mediaLoader.shouldTryRead(url)) {
                    return mediaLoader.tryLoad(url);
                }
            } catch (Exception e) {
                exceptions.add(new IOException("Unable to read or download media with MediaLoader " + mediaLoader.getIdentifier(), e));
            }
        }
        IOException e = new IOException("Unable to read or download media with ImageMapLoader " + getIdentifier().asString() + ", does this url directly links to the gif? (" + url + ")");
        for (IOException ex : exceptions) {
            e.addSuppressed(ex);
        }
        throw e;
    }

    public abstract Future<T> create(C createInfo) throws Exception;

    public abstract Future<T> load(ImageMapManager manager, File folder, JsonObject json) throws Exception;

}
