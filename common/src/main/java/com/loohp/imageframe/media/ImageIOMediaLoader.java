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
import com.loohp.imageframe.utils.HTTPRequestUtils;
import net.kyori.adventure.key.Key;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

public class ImageIOMediaLoader implements MediaLoader {

    private static final Key IDENTIFIER = Key.key("imageframe", "imageio");

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
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(HTTPRequestUtils.download(url, ImageFrame.maxImageFileSize)));
        return MediaLoader.staticImage(image);
    }
}
