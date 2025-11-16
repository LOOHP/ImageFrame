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

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.GifReader;
import com.loohp.imageframe.utils.HTTPRequestUtils;
import net.kyori.adventure.key.Key;

import java.util.Iterator;
import java.util.List;

public class GifReaderMediaLoader implements MediaLoader {

    private static final Key IDENTIFIER = Key.key("imageframe", "gif_reader");

    @Override
    public Key getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public boolean shouldTryRead(String url) {
        return true;
    }

    @Override
    public Iterator<MediaFrame> tryLoad(String url) throws Exception {
        List<GifReader.ImageFrame> frames = GifReader.readGif(HTTPRequestUtils.getInputStream(url), ImageFrame.maxImageFileSize).get();
        return frames.stream().map(f -> MediaFrame.animatedFrame(f.getImage(), f.getDelay())).iterator();
    }
}
