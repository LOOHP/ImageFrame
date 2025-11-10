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

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.api.events.ImageMapUpdatedEvent;
import com.loohp.imageframe.utils.MapUtils;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.map.MapView;
import org.bukkit.plugin.messaging.PluginMessageListener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CustomClientNetworkManager implements PluginMessageListener, Listener {

    public static final String CLIENTBOUND_ACKNOWLEDGEMENT = "imageframe:clientbound_ack";
    public static final String SERVERBOUND_ACKNOWLEDGEMENT = "imageframe:serverbound_ack";

    public static final String SERVERBOUND_HD_IMAGE_REQUEST = "imageframe:serverbound_hd_image";
    public static final String CLIENTBOUND_HD_IMAGE_RESPONSE = "imageframe:clientbound_hd_image";
    public static final String CLIENTBOUND_HD_IMAGE_MULTIPART_RESPONSE = "imageframe:clientbound_hd_image_multi";

    public static final String CLIENTBOUND_HD_UPDATE_SIGNAL = "imageframe:clientbound_update";

    public static final String SERVERBOUND_IMAGEMAP_DETAILS_REQUEST = "imageframe:serverbound_imagemap_details";
    public static final String CLIENTBOUND_IMAGEMAP_DETAILS_RESPONSE = "imageframe:clientbound_imagemap_details";

    private final Map<UUID, Long> acknowledgements;
    private final Set<UUID> acknowledged;

    public CustomClientNetworkManager(boolean enabled) {
        this.acknowledgements = new ConcurrentHashMap<>();
        this.acknowledged = ConcurrentHashMap.newKeySet();

        if (!enabled) {
            return;
        }

        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(ImageFrame.plugin, CLIENTBOUND_ACKNOWLEDGEMENT);
        Bukkit.getServer().getMessenger().registerIncomingPluginChannel(ImageFrame.plugin, SERVERBOUND_ACKNOWLEDGEMENT, this);

        Bukkit.getServer().getMessenger().registerIncomingPluginChannel(ImageFrame.plugin, SERVERBOUND_HD_IMAGE_REQUEST, this);
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(ImageFrame.plugin, CLIENTBOUND_HD_IMAGE_RESPONSE);
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(ImageFrame.plugin, CLIENTBOUND_HD_IMAGE_MULTIPART_RESPONSE);

        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(ImageFrame.plugin, CLIENTBOUND_HD_UPDATE_SIGNAL);

        Bukkit.getServer().getMessenger().registerIncomingPluginChannel(ImageFrame.plugin, SERVERBOUND_IMAGEMAP_DETAILS_REQUEST, this);
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(ImageFrame.plugin, CLIENTBOUND_IMAGEMAP_DETAILS_RESPONSE);

        Bukkit.getServer().getPluginManager().registerEvents(this, ImageFrame.plugin);
    }

    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(acknowledged);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long id = ThreadLocalRandom.current().nextLong();
        acknowledgements.put(player.getUniqueId(), id);

        Scheduler.runTaskLater(ImageFrame.plugin, () -> {
            if (player.isOnline()) {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeLong(id);
                player.sendPluginMessage(ImageFrame.plugin, CLIENTBOUND_ACKNOWLEDGEMENT, out.toByteArray());
            }
        }, 10);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        acknowledgements.remove(uuid);
        acknowledged.remove(uuid);
    }

    @EventHandler
    public void onUpdate(ImageMapUpdatedEvent event) {
        handle(() -> {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            ImageMap imageMap = event.getImageMap();
            writeVarInt(out, 1);
            out.writeInt(imageMap.getImageIndex());
            List<Integer> mapIds = imageMap.getMapIds();
            writeVarInt(out, mapIds.size());
            for (int mapId : mapIds) {
                out.writeInt(mapId);
            }
            return out.toByteArray();
        }, () -> true, out -> {
            for (UUID uuid : acknowledged) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.sendPluginMessage(ImageFrame.plugin, CLIENTBOUND_HD_UPDATE_SIGNAL, out);
                }
            }
        });
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        try {
            switch (channel) {
                case SERVERBOUND_ACKNOWLEDGEMENT: {
                    ByteArrayDataInput in = ByteStreams.newDataInput(data);
                    long id = in.readLong();
                    UUID uuid = player.getUniqueId();
                    if (acknowledgements.get(uuid) == id) {
                        acknowledgements.remove(uuid);
                        acknowledged.add(uuid);
                    }
                    break;
                }
                case SERVERBOUND_HD_IMAGE_REQUEST: {
                    ByteArrayDataInput in = ByteStreams.newDataInput(data);
                    int mapId = in.readInt();
                    ImageMap imageMap = ImageFrame.imageMapManager.getFromMapId(mapId);
                    handle(() -> {
                        List<byte[]> list = new ArrayList<>();
                        ByteArrayDataOutput out = ByteStreams.newDataOutput();
                        out.writeInt(mapId);
                        if (imageMap == null) {
                            out.writeBoolean(true);
                            writeVarInt(out, 0);
                            out.writeBoolean(false);
                        } else {
                            try {
                                MapView mapView = imageMap.getMapViewFromMapId(mapId);
                                if (MapUtils.canViewMap(player, mapView).get()) {
                                    out.writeBoolean(true);
                                    BufferedImage image = imageMap.getOriginalImage(mapId);
                                    if (image == null) {
                                        writeVarInt(out, 0);
                                        out.writeBoolean(false);
                                    } else {
                                        try {
                                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                            ImageIO.write(image, "png", byteArrayOutputStream);
                                            byte[] array = byteArrayOutputStream.toByteArray();
                                            List<byte[]> chunked = chunked(array, 32000);
                                            writeVarInt(out, chunked.get(0).length);
                                            out.write(chunked.get(0));
                                            if (chunked.size() <= 1) {
                                                out.writeBoolean(false);
                                            } else {
                                                int multipartId = ThreadLocalRandom.current().nextInt();
                                                out.writeBoolean(true);
                                                out.writeInt(multipartId);
                                                for (int i = 1; i < chunked.size(); i++) {
                                                    ByteArrayDataOutput outMulti = ByteStreams.newDataOutput();
                                                    outMulti.writeInt(mapId);
                                                    outMulti.writeInt(multipartId);
                                                    outMulti.writeInt(i);
                                                    writeVarInt(outMulti, chunked.get(i).length);
                                                    outMulti.write(chunked.get(i));
                                                    outMulti.writeBoolean(i + 1 >= chunked.size());
                                                    list.add(outMulti.toByteArray());
                                                }
                                            }
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                            writeVarInt(out, 0);
                                            out.writeBoolean(false);
                                        }
                                    }
                                } else {
                                    out.writeBoolean(false);
                                    writeVarInt(out, 0);
                                    out.writeBoolean(false);
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        list.add(0, out.toByteArray());
                        return list;
                    }, () -> player.isOnline(), out -> {
                        player.sendPluginMessage(ImageFrame.plugin, CLIENTBOUND_HD_IMAGE_RESPONSE, out.get(0));
                        for (int i = 1; i < out.size(); i++) {
                            player.sendPluginMessage(ImageFrame.plugin, CLIENTBOUND_HD_IMAGE_MULTIPART_RESPONSE, out.get(i));
                        }
                    });
                    break;
                }
                case SERVERBOUND_IMAGEMAP_DETAILS_REQUEST: {
                    ByteArrayDataInput in = ByteStreams.newDataInput(data);
                    int index = in.readInt();
                    ImageMap imageMap = ImageFrame.imageMapManager.getFromImageId(index);
                    handle(() -> {
                        ByteArrayDataOutput out = ByteStreams.newDataOutput();
                        out.writeInt(index);
                        if (imageMap == null) {
                            out.writeInt(0);
                            out.writeInt(0);
                            writeVarInt(out, 0);
                        } else {
                            out.writeInt(imageMap.getWidth());
                            out.writeInt(imageMap.getHeight());
                            writeVarInt(out, imageMap.getMapIds().size());
                            for (int mapId : imageMap.getMapIds()) {
                                out.writeInt(mapId);
                            }
                        }
                        return out.toByteArray();
                    }, () -> player.isOnline(), out -> {
                        player.sendPluginMessage(ImageFrame.plugin, CLIENTBOUND_IMAGEMAP_DETAILS_RESPONSE, out);
                        if (imageMap != null) {
                            imageMap.send(player);
                        }
                    });
                    break;
                }
            }
        } catch (Throwable e) {
            new RuntimeException("Illegal payload received from " + player.getName() + " on channel " + channel, e).printStackTrace();
        }
    }

    private static void writeVarInt(ByteArrayDataOutput out, int i) {
        while ((i & -128) != 0) {
            out.writeByte(i & 127 | 128);
            i >>>= 7;
        }
        out.writeByte(i);
    }

    private static <T> void handle(Supplier<T> handleAsync, BooleanSupplier syncPredicate, Consumer<T> completeSync) {
        Scheduler.runTaskAsynchronously(ImageFrame.plugin, () -> {
            T value = handleAsync.get();
            if (syncPredicate.getAsBoolean()) {
                Scheduler.runTask(ImageFrame.plugin, () -> completeSync.accept(value));
            }
        });
    }

    private static List<byte[]> chunked(byte[] data, int chunkSize) {
        if (data.length <= chunkSize) {
            return Collections.singletonList(data);
        }
        List<byte[]> chunks = new ArrayList<>();
        for (int i = 0; i < data.length; i += chunkSize) {
            int end = Math.min(data.length, i + chunkSize);
            byte[] chunk = Arrays.copyOfRange(data, i, end);
            chunks.add(chunk);
        }
        return chunks;
    }

}
