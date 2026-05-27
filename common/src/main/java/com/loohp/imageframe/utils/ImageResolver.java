package com.loohp.imageframe.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class ImageResolver {

    public static InputStream getResolvedImageInputStream(String link) throws IOException {
        URLConnection connection = HTTPRequestUtils.openConnection(link);
        String contentType = connection.getContentType();

        // Direct image URL
        if (contentType != null && contentType.startsWith("image/")) {
            return connection.getInputStream();
        }

        // HTML page -> extract image
        if (contentType != null && contentType.contains("text/html")) {
            Document doc = Jsoup.parse(connection.getInputStream(), null, link);

            // Try OpenGraph image first
            Element ogImage = doc.selectFirst("meta[property=og:image]");

            if (ogImage != null) {
                String imageUrl = ogImage.attr("content");
                return HTTPRequestUtils.getInputStream(imageUrl);
            }

            // Fallback: first image on page
            Element img = doc.selectFirst("img[src]");

            if (img != null) {
                String imageUrl = img.absUrl("src");
                return HTTPRequestUtils.getInputStream(imageUrl);
            }
        }

        return HTTPRequestUtils.getInputStream(link);
    }

    public static byte[] downloadResolvedImage(String link, long sizeLimit) throws IOException {
        try (InputStream is = getResolvedImageInputStream(link)) {
            ByteArrayOutputStream baos = new SizeLimitedByteArrayOutputStream(sizeLimit);
            byte[] byteChunk = new byte[4096];
            int n;
            while ((n = is.read(byteChunk)) > 0) {
                baos.write(byteChunk, 0, n);
            }
            return baos.toByteArray();
        }
    }
}