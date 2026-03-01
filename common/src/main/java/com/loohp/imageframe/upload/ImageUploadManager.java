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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    public static final long EXPIRATION = TimeUnit.MINUTES.toMillis(5);

    private final URI uri;
    private final String pathPrefix;

    private final File webRootDir;
    private final File uploadDir;

    private final Map<UUID, PendingUpload> pendingUploads;
    private final AtomicLong imagesUploadedCounter;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public ImageUploadManager(boolean enabled, URI uri) {
        this.uri = uri;
        this.pathPrefix = normalizePrefix(uri.getPath());

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
                send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\": \"Bad request\"");
                return;
            }

            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            String uriPath = decoder.path();

            if (!pathPrefix.isEmpty()) {
                if (!uriPath.startsWith(pathPrefix)) {
                    send(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\": \"Not found\"");
                    return;
                }

                uriPath = uriPath.substring(pathPrefix.length());
                if (uriPath.isEmpty()) {
                    uriPath = "/";
                }
            }

            if (request.method() == HttpMethod.GET) {
                handleStatic(ctx, uriPath);
            } else if (request.method() == HttpMethod.POST && uriPath.equals("/upload")) {
                handleUpload(ctx, request, decoder);
            } else {
                send(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, "\"error\": \"Method not allowed\"");
            }
        }
    }

    private void handleStatic(ChannelHandlerContext ctx, String path) throws IOException {
        Path base = webRootDir.toPath().toAbsolutePath();
        Path resolved = base.resolve("." + path).normalize();

        if (!resolved.startsWith(base) || !Files.exists(resolved)) {
            resolved = base.resolve("index.html");
        }

        File file = resolved.toFile();
        long fileLength = file.length();

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileLength);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");

        ctx.write(response);

        // Use ChunkedNioFile for better NIO integration
        ctx.write(new ChunkedNioFile(file));
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
    }

    private void handleUpload(ChannelHandlerContext ctx, FullHttpRequest request, QueryStringDecoder decoder)
            throws Exception {

        Map<String, List<String>> params = decoder.parameters();

        String user = getParam(params, "user");
        String id = getParam(params, "id");

        PendingUpload pending = findPendingUpload(user, id);
        if (pending == null) {
            send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\": \"Invalid upload token\"");
            return;
        }

        HttpPostRequestDecoder postDecoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), request);

        try {
            for (InterfaceHttpData data : postDecoder.getBodyHttpDatas()) {
                if (!(data instanceof FileUpload)) {
                    continue;
                }

                FileUpload upload = (FileUpload) data;
                if (!upload.isCompleted()) {
                    continue;
                }

                byte[] fileData = new byte[(int) upload.length()];
                upload.getByteBuf().readBytes(fileData);

                File outputFile = new File(uploadDir, id + ".png");
                Files.write(outputFile.toPath(), fileData);

                imagesUploadedCounter.incrementAndGet();

                pendingUploads.remove(UUID.fromString(user));
                pending.getFuture().complete(outputFile);

                Scheduler.runTaskLaterAsynchronously(ImageFrame.plugin, outputFile::delete, EXPIRATION / 50);
                send(ctx, HttpResponseStatus.OK, "{\"message\": \"File uploaded successfully\"}");
                return;
            }

            send(ctx, HttpResponseStatus.BAD_REQUEST, "{\"error\": \"Missing image field\"");
        } finally {
            postDecoder.destroy();
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