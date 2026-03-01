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

package com.loohp.imageframe.upload;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.utils.FileUtils;
import com.loohp.platformscheduler.Scheduler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedWriteHandler;

public class ImageUploadManager implements AutoCloseable {
    private static final byte[] PNG_SIGNATURE = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };

    public static final long EXPIRATION = TimeUnit.MINUTES.toMillis(5);

    private final URI uri;
    private final String pathPrefix;
    private final long maxCacheSize;
    private final int maxWidth;
    private final int maxHeight;

    private final File webRootDir;
    private final File uploadDir;

    private final Map<UUID, PendingUpload> pendingUploads;
    private final AtomicLong imagesUploadedCounter;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public ImageUploadManager(boolean enabled, URI uri, long maxCacheSize, int maxImageWidth, int maxImageHeight) {
        this.uri = uri;
        this.pathPrefix = normalizePrefix(uri.getPath());
        this.maxCacheSize = maxCacheSize;
        this.maxWidth = maxImageWidth;
        this.maxHeight = maxImageHeight;

        this.webRootDir = new File(ImageFrame.plugin.getDataFolder(), "upload/web");
        this.uploadDir = new File(ImageFrame.plugin.getDataFolder(), "upload/images");
        this.imagesUploadedCounter = new AtomicLong(0);

        FileUtils.removeFolderRecursively(uploadDir);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        if (!webRootDir.exists()) {
            webRootDir.mkdirs();
        }

        Cache<UUID, PendingUpload> cache = CacheBuilder.newBuilder()
                .expireAfterAccess(EXPIRATION, TimeUnit.MILLISECONDS)
                .removalListener((RemovalNotification<UUID, PendingUpload> notification) -> {
                    if (notification.wasEvicted() && notification.getValue() != null) {
                        notification.getValue().getFuture().completeExceptionally(new LinkTimeoutException());
                    }
                })
                .build();

        this.pendingUploads = cache.asMap();
        if (enabled) {
            startServer();
        }
    }

    private void startServer() {
        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(0, NioIoHandler.newFactory()); // 0 = Netty default (2 * cores)

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channelFactory(NioServerSocketChannel::new)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator((int) ImageFrame.maxImageFileSize));
                            ch.pipeline().addLast(new ChunkedWriteHandler());
                            ch.pipeline().addLast(new UploadServerHandler());
                        }
                    });

            // Default to localhost:80 if no config option is set.
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();

            serverChannel = bootstrap.bind(host, port).sync().channel();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to start Netty upload server", error);
        }
    }

    private String normalizePrefix(String path) {
        if (path == null || path.trim().isEmpty() || path.equals("/")) {
            return "";
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }

    private class UploadServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            if (!request.decoderResult().isSuccess()) {
                send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Bad request\"}");
                return;
            }

            QueryStringDecoder decoder = new QueryStringDecoder(request.uri(), StandardCharsets.UTF_8);
            String rawPath = decoder.path();
            if (rawPath == null || rawPath.isEmpty()) {
                rawPath = "/";
            }

            // Reject null byte attacks
            if (rawPath.indexOf('\0') >= 0) {
                send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Invalid path\"}");
                return;
            }

            // Reject Windows-style separators to prevent bypass tricks
            if (rawPath.contains("\\")) {
                send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Invalid path\"}");
                return;
            }

            // Normalize dot segments safely using URI
            String normalizedPath;
            try {
                normalizedPath = new URI(null, null, rawPath, null).normalize().getPath();
            } catch (Exception e) {
                send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Invalid URI\"}");
                return;
            }

            if (normalizedPath == null || normalizedPath.isEmpty()) {
                normalizedPath = "/";
            }

            // Apply prefix restriction
            if (!pathPrefix.isEmpty()) {

                if (!normalizedPath.startsWith(pathPrefix)) {
                    send(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"Not found\"}");
                    return;
                }

                normalizedPath = normalizedPath.substring(pathPrefix.length());

                if (normalizedPath.isEmpty()) {
                    normalizedPath = "/";
                }
            }

            // At this point:
            // - Path is normalized
            // - No backslashes
            // - No null bytes
            // - Prefix stripped
            // - Dot segments removed
            if (request.method() == HttpMethod.GET || request.method() == HttpMethod.HEAD) {
                handleStatic(ctx, request, normalizedPath);
                return;
            }

            if (request.method() == HttpMethod.POST && "/upload".equals(normalizedPath)) {
                handleUpload(ctx, request, decoder);
                return;
            }

            send(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "{\"error\":\"Method not allowed\"}");
        }
    }

    private void handleStatic(ChannelHandlerContext ctx, FullHttpRequest request, String normalizedPath)
            throws IOException {
        HttpMethod method = request.method();
        if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
            send(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "{\"error\":\"Method not allowed\"}");
            return;
        }

        // Resolve base directory canonically
        Path basePath = webRootDir.getCanonicalFile().toPath();
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            normalizedPath = "/";
        }

        // Canonical check prevents traversal and symlink escape attacks
        Path resolved = basePath.resolve("." + normalizedPath).normalize();
        Path canonical;
        try {
            canonical = resolved.toFile().getCanonicalFile().toPath();
        } catch (IOException e) {
            send(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"Not found\"}");
            return;
        }

        if (!canonical.startsWith(basePath)) {
            send(ctx, HttpResponseStatus.FORBIDDEN, "{\"error\":\"Forbidden\"}");
            return;
        }

        if (Files.isDirectory(canonical)) {
            canonical = canonical.resolve("index.html");
        }

        if (!Files.exists(canonical) || !Files.isRegularFile(canonical)) {
            canonical = basePath.resolve("index.html").toFile().getCanonicalFile().toPath();
            if (!canonical.startsWith(basePath) || !Files.exists(canonical)) {
                send(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"Not found\"}");
                return;
            }
        }

        File file = canonical.toFile();
        long fileLength = file.length();
        String contentType = Files.probeContentType(canonical);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileLength);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);

        // Security Headers
        response.headers().set("X-Content-Type-Options", "nosniff");
        response.headers().set("X-Frame-Options", "DENY");
        response.headers().set("Referrer-Policy", "no-referrer");

        // Since this is upload UI, safest default is no-store
        response.headers().set("Cache-Control", "no-store");

        ctx.write(response);
        if (method == HttpMethod.GET) {
            ctx.write(new ChunkedNioFile(file));
        }

        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
    }

    private void handleUpload(ChannelHandlerContext ctx, FullHttpRequest request, QueryStringDecoder decoder)
            throws Exception {

        Map<String, List<String>> params = decoder.parameters();
        String user = getParam(params, "user");
        String id = getParam(params, "id");

        PendingUpload pending = findPendingUpload(user, id);
        if (pending == null) {
            send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Invalid upload token\"}");
            return;
        }

        UUID userUUID;
        UUID idUUID;

        try {
            userUUID = UUID.fromString(user);
            idUUID = UUID.fromString(id);
        } catch (Exception e) {
            send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Invalid UUID format\"}");
            return;
        }

        // Saves files smaller than 1MB (configurable) in memory, writes bigger files to disk.
        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(maxCacheSize);
        HttpPostRequestDecoder postDecoder = new HttpPostRequestDecoder(factory, request);
        File tempFile = null;

        try {
            for (InterfaceHttpData data : postDecoder.getBodyHttpDatas()) {
                if (!(data instanceof FileUpload)) {
                    continue;
                }

                FileUpload upload = (FileUpload) data;
                if (!upload.isCompleted()) {
                    continue;
                }

                // Enforce field name
                if (!"image".equals(upload.getName())) {
                    continue;
                }

                tempFile = upload.getFile();
                if (tempFile == null || !tempFile.exists()) {
                    send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Upload failed\"}");
                    return;
                }

                // Validate PNG signature
                if (!hasValidPngSignature(tempFile)) {
                    send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Invalid PNG signature\"}");
                    return;
                }

                // Start re-encoding image to prevent attacks
                BufferedImage decoded;
                try (InputStream in = Files.newInputStream(tempFile.toPath())) {
                    decoded = ImageIO.read(in);
                }

                if (decoded == null) {
                    send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Invalid PNG format\"}");
                    return;
                }

                int width = decoded.getWidth();
                int height = decoded.getHeight();

                // Enforce reasonable image dimension limits
                if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight
                        || ((long) width * height) > ((long) maxWidth * maxHeight)) {

                    send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Image dimensions exceed limits\"}");
                    return;
                }

                // Normalize to safe ARGB
                BufferedImage normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = normalized.createGraphics();
                g.drawImage(decoded, 0, 0, null);
                g.dispose();

                // Atomic single-use token removal
                if (!pendingUploads.remove(userUUID, pending)) {
                    send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Token already used\"}");
                    return;
                }

                // Write clean PNG (re-encode strips malicious chunks)
                File outputFile = new File(uploadDir, idUUID + ".png");
                try (OutputStream out = Files.newOutputStream(outputFile.toPath())) {
                    if (!ImageIO.write(normalized, "png", out)) {
                        send(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"Failed to encode PNG\"}");
                        return;
                    }
                }

                imagesUploadedCounter.incrementAndGet();
                pending.getFuture().complete(outputFile);

                Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, outputFile::delete, EXPIRATION / 50);
                send(ctx, HttpResponseStatus.OK, "{\"message\":\"File uploaded successfully\"}");
                return;
            }

            send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Missing image field\"}");
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.deleteIfExists(tempFile.toPath());
                } catch (IOException ignored) {
                }
            }

            postDecoder.destroy();
            factory.cleanAllHttpData();
        }
    }

    private boolean hasValidPngSignature(File file) {
        if (file.length() < PNG_SIGNATURE.length) {
            return false;
        }

        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] header = new byte[PNG_SIGNATURE.length];
            if (in.read(header) != PNG_SIGNATURE.length) {
                return false;
            }

            for (int i = 0; i < PNG_SIGNATURE.length; i++) {
                if (header[i] != PNG_SIGNATURE[i]) {
                    return false;
                }
            }

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String getParam(Map<String, List<String>> map, String key) {
        List<String> values = map.get(key);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private PendingUpload findPendingUpload(String user, String id) {
        try {
            UUID userUUID = UUID.fromString(user);
            UUID idUUID = UUID.fromString(id);

            PendingUpload pending = pendingUploads.get(userUUID);
            if (pending == null || !pending.getId().equals(idUUID)) {
                return null;
            }

            return pending;
        } catch (Exception e) {
            return null;
        }
    }

    private void send(ChannelHandlerContext ctx, HttpResponseStatus status, String msg) {
        ByteBuf buf = Unpooled.copiedBuffer(msg, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);

        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public boolean wasUploaded(String url) {
        try {
            // Parse the URL to get the file path
            URI uri = new URI(url);
            String path = uri.getPath();

            // Extract filename from the path
            String filename = path.substring(path.lastIndexOf('/') + 1);

            // Check if the file exists in the upload directory
            File file = new File(uploadDir, filename);

            // Verify the file exists and is in the upload directory
            return file.exists() && uploadDir.equals(file.getParentFile());
        } catch (Exception e) {
            return false;
        }
    }

    public PendingUpload newPendingUpload(UUID user) {
        PendingUpload existing = pendingUploads.remove(user);
        if (existing != null) {
            existing.getFuture().completeExceptionally(new LinkTimeoutException());
        }

        PendingUpload pendingUpload = PendingUpload.create(EXPIRATION);
        pendingUploads.put(user, pendingUpload);
        return pendingUpload;
    }

    public boolean isOperational() {
        return serverChannel != null && serverChannel.isActive();
    }

    public AtomicLong getImagesUploadedCounter() {
        return imagesUploadedCounter;
    }

    @Override
    public void close() {
        if (serverChannel != null) {
            serverChannel.close();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public static class LinkTimeoutException extends Exception {
        public LinkTimeoutException() {
            super();
        }

        public LinkTimeoutException(String msg) {
            super(msg);
        }
    }
}