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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarUtils {

    public static final char JAR_SEPARATOR = '/';

    public static void copyFolderFromJar(String folderName, File destFolder, CopyOption option) throws IOException {
        copyFolderFromJar(folderName, destFolder, option, null);
    }

    public static void copyFolderFromJar(String folderName, File destFolder, CopyOption option, PathTrimmer trimmer) throws IOException {
        if (!destFolder.exists()) {
            destFolder.mkdirs();
        }

        byte[] buffer = new byte[1024];

        File fullPath = null;
        String path = JarUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (trimmer != null) {
            path = trimmer.trim(path);
        }
        try {
            if (!path.startsWith("file")) {
                path = "file://" + path;
            }

            fullPath = new File(new URI(path));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fullPath));

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (!entry.getName().startsWith(folderName + JAR_SEPARATOR)) {
                continue;
            }

            String fileName = entry.getName();

            if (fileName.charAt(fileName.length() - 1) == JAR_SEPARATOR) {
                File file = new File(destFolder + File.separator + fileName);
                if (file.isFile()) {
                    file.delete();
                }
                file.mkdirs();
                continue;
            }

            File file = new File(destFolder + File.separator + fileName);
            if (option == CopyOption.COPY_IF_NOT_EXIST && file.exists()) {
                continue;
            }

            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file);

            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
        }

        zis.closeEntry();
        zis.close();
    }

    public enum CopyOption {
        COPY_IF_NOT_EXIST, REPLACE_IF_EXIST
    }

    @FunctionalInterface
    public interface PathTrimmer {

        String trim(String original);

    }

}
