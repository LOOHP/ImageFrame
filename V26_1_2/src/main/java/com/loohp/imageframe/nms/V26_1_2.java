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

package com.loohp.imageframe.nms;

import com.google.common.collect.Collections2;
import com.loohp.imageframe.objectholders.CombinedMapItemInfo;
import com.loohp.imageframe.objectholders.FilledMapItemInfo;
import com.loohp.imageframe.objectholders.MutablePair;
import com.loohp.imageframe.utils.ReflectionUtils;
import com.loohp.imageframe.utils.UUIDUtils;
import net.kyori.adventure.key.Key;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.map.CraftMapCursor;
import org.bukkit.craftbukkit.map.CraftMapView;
import org.bukkit.craftbukkit.map.RenderData;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class V26_1_2 extends NMSWrapper {

    private final Field nmsEntityByteDataWatcherField;
    private final Field craftMapViewWorldMapField;
    private final Field persistentIdCountsLastMapIdField;
    private final Field renderDataCursorsField;

    public V26_1_2() {
        try {
            nmsEntityByteDataWatcherField = ReflectionUtils.findDeclaredField(net.minecraft.world.entity.Entity.class, EntityDataAccessor.class, "DATA_SHARED_FLAGS_ID");
            craftMapViewWorldMapField = CraftMapView.class.getDeclaredField("worldMap");
            Field persistentIdCountsLastMapIdField0;
            try {
                persistentIdCountsLastMapIdField0 = ReflectionUtils.findDeclaredField(MapIndex.class, int.class, "lastMapId");
            } catch (NoSuchFieldException e) {
                persistentIdCountsLastMapIdField0 = ReflectionUtils.findDeclaredField(MapIndex.class, AtomicInteger.class, "lastMapId");
            }
            persistentIdCountsLastMapIdField = persistentIdCountsLastMapIdField0;
            renderDataCursorsField = RenderData.class.getField("cursors");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public MapItemSavedData getWorldMap(MapView mapView) {
        try {
            CraftMapView craftMapView = (CraftMapView) mapView;
            craftMapViewWorldMapField.setAccessible(true);
            return (MapItemSavedData) craftMapViewWorldMapField.get(craftMapView);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setColors(MapView mapView, byte[] colors) {
        if (colors.length != COLOR_ARRAY_LENGTH) {
            throw new IllegalArgumentException("colors array length must be " + COLOR_ARRAY_LENGTH);
        }
        MapItemSavedData nmsWorldMap = getWorldMap(mapView);
        nmsWorldMap.colors = colors;
    }

    @Override
    public Collection<Player> getViewers(MapView mapView) {
        MapItemSavedData nmsWorldMap = getWorldMap(mapView);
        Map<net.minecraft.world.entity.player.Player, MapItemSavedData.HoldingPlayer> humansMap = nmsWorldMap.carriedByPlayers;
        return Collections2.transform(humansMap.keySet(), e -> (Player) e.getBukkitEntity());
    }

    @Override
    public boolean hasViewers(MapView mapView) {
        MapItemSavedData nmsWorldMap = getWorldMap(mapView);
        Map<net.minecraft.world.entity.player.Player, MapItemSavedData.HoldingPlayer> humansMap = nmsWorldMap.carriedByPlayers;
        return !humansMap.isEmpty();
    }

    @Override
    public MapDecoration toNMSMapIcon(MapCursor mapCursor) {
        Holder<MapDecorationType> decorationTypeHolder = toNMSMapIconType(mapCursor.getType());
        net.minecraft.network.chat.Component iChat = CraftChatMessage.fromStringOrNull(mapCursor.getCaption());
        return new MapDecoration(decorationTypeHolder, mapCursor.getX(), mapCursor.getY(), mapCursor.getDirection(), Optional.ofNullable(iChat));
    }

    @Override
    public Holder<MapDecorationType> toNMSMapIconType(MapCursor.Type type) {
        return CraftMapCursor.CraftType.bukkitToMinecraftHolder(type);
    }

    @Override
    public boolean isRenderOnFrame(MapCursor.Type type) {
        Holder<MapDecorationType> decorationTypeHolder = toNMSMapIconType(type);
        return decorationTypeHolder.value().showOnItemFrame();
    }

    @SuppressWarnings("resource")
    @Override
    public int getNextAvailableMapId(World world) {
        try {
            persistentIdCountsLastMapIdField.setAccessible(true);
            ServerLevel worldServer = ((CraftWorld) world).getHandle();
            MapIndex mapIndex = worldServer.getServer().getDataStorage().get(MapIndex.TYPE);
            if (persistentIdCountsLastMapIdField.getType().equals(AtomicInteger.class)) {
                AtomicInteger atomicInteger = (AtomicInteger) persistentIdCountsLastMapIdField.get(mapIndex);
                return atomicInteger.get() + 1;
            } else {
                return persistentIdCountsLastMapIdField.getInt(mapIndex) + 1;
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"deprecation", "resource", "DataFlowIssue"})
    @Override
    public MapView getMapOrCreateMissing(World world, int id) {
        try {
            MapView mapView = Bukkit.getMap(id);
            if (mapView != null) {
                return mapView;
            }
            persistentIdCountsLastMapIdField.setAccessible(true);
            Location spawnLocation = world.getSpawnLocation();
            ServerLevel worldServer = ((CraftWorld) world).getHandle();
            ResourceKey<Level> dimension = worldServer.dimension();
            MapItemSavedData mapItemSavedData = MapItemSavedData.createFresh(spawnLocation.getX(), spawnLocation.getZ(), (byte) 3, false, false, dimension);
            MapId mapId = new MapId(id);
            worldServer.setMapData(mapId, mapItemSavedData);
            MapIndex mapIndex = worldServer.getServer().getDataStorage().get(MapIndex.TYPE);
            if (persistentIdCountsLastMapIdField.getType().equals(AtomicInteger.class)) {
                AtomicInteger atomicInteger = (AtomicInteger) persistentIdCountsLastMapIdField.get(mapIndex);
                atomicInteger.getAndUpdate(lastMapId -> Math.max(lastMapId, id));
            } else {
                int lastMapId = persistentIdCountsLastMapIdField.getInt(mapIndex);
                persistentIdCountsLastMapIdField.setInt(mapIndex, Math.max(lastMapId, id));
            }
            mapIndex.setDirty();
            return Bukkit.getMap(id);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public MutablePair<byte[], List<MapCursor>> bukkitRenderMap(MapView mapView, Player player) {
        try {
            CraftMapView craftMapView = (CraftMapView) mapView;
            CraftPlayer craftPlayer = (CraftPlayer) player;
            RenderData renderData = craftMapView.render(craftPlayer);
            return new MutablePair<>(renderData.buffer, (List<MapCursor>) renderDataCursorsField.get(renderData));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<Player> getEntityTrackers(Entity entity) {
        return new HashSet<>(entity.getTrackedBy());
    }

    @Override
    public ClientboundMapItemDataPacket createMapPacket(int mapId, byte[] colors, Collection<MapCursor> cursors) {
        List<MapDecoration> mapIcons = cursors == null ? null : cursors.stream().map(this::toNMSMapIcon).collect(Collectors.toList());
        MapItemSavedData.MapPatch mapPatch = colors == null ? null : new MapItemSavedData.MapPatch(0, 0, 128, 128, colors);
        return new ClientboundMapItemDataPacket(new MapId(mapId), (byte) 0, false, Optional.ofNullable(mapIcons), Optional.ofNullable(mapPatch));
    }

    @Override
    public ClientboundSetEntityDataPacket createItemFrameItemChangePacket(int entityId, ItemStack itemStack) {
        List<SynchedEntityData.DataValue<?>> dataWatchers = Collections.singletonList(SynchedEntityData.DataValue.create(net.minecraft.world.entity.decoration.ItemFrame.DATA_ITEM, CraftItemStack.asNMSCopy(itemStack)));
        return new ClientboundSetEntityDataPacket(entityId, dataWatchers);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object createEntityFlagsPacket(Entity entity, Boolean invisible, Boolean glowing) {
        try {
            int entityId = entity.getEntityId();
            net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) entity).getHandle();

            nmsEntityByteDataWatcherField.setAccessible(true);

            SynchedEntityData watcher = nmsEntity.getEntityData();
            EntityDataAccessor<Byte> byteField = (EntityDataAccessor<Byte>) nmsEntityByteDataWatcherField.get(null);
            byte value = watcher.get(byteField);

            if (invisible != null) {
                if (invisible) {
                    value = (byte) (value | 0x20);
                } else {
                    value = (byte) (value & ~0x20);
                }
            }
            if (glowing != null) {
                if (glowing) {
                    value = (byte) (value | 0x40);
                } else {
                    value = (byte) (value & ~0x40);
                }
            }

            List<SynchedEntityData.DataValue<?>> dataWatchers = Collections.singletonList(SynchedEntityData.DataValue.create(byteField, value));
            return new ClientboundSetEntityDataPacket(entityId, dataWatchers);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        ((CraftPlayer) player).getHandle().connection.send((Packet<?>) packet);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public CombinedMapItemInfo getCombinedMapItemInfo(ItemStack itemStack) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        CustomData customData = nmsItemStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(CombinedMapItemInfo.KEY)) {
            return null;
        }
        int imageMapIndex = tag.getIntOr(CombinedMapItemInfo.KEY, -1);
        if (!tag.contains(CombinedMapItemInfo.PLACEMENT_UUID_KEY) || !tag.contains(CombinedMapItemInfo.PLACEMENT_YAW_KEY)) {
            return new CombinedMapItemInfo(imageMapIndex);
        }
        float yaw = tag.getFloatOr(CombinedMapItemInfo.PLACEMENT_YAW_KEY, 0F);
        UUID uuid = UUIDUtils.fromIntArray(tag.getIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY).get());
        return new CombinedMapItemInfo(imageMapIndex, new CombinedMapItemInfo.PlacementInfo(yaw, uuid));
    }

    @Override
    public ItemStack withCombinedMapItemInfo(ItemStack itemStack, CombinedMapItemInfo combinedMapItemInfo) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        CustomData customData = nmsItemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putInt(CombinedMapItemInfo.KEY, combinedMapItemInfo.getImageMapIndex());
        if (combinedMapItemInfo.hasPlacement()) {
            CombinedMapItemInfo.PlacementInfo placement = combinedMapItemInfo.getPlacement();
            tag.putFloat(CombinedMapItemInfo.PLACEMENT_YAW_KEY, placement.getYaw());
            tag.putIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY, UUIDUtils.toIntArray(placement.getUniqueId()));
        }
        nmsItemStack.applyComponents(DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build());
        return CraftItemStack.asCraftMirror(nmsItemStack);
    }

    @Override
    public FilledMapItemInfo getFilledMapItemInfo(ItemStack itemStack) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        CustomData customData = nmsItemStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(FilledMapItemInfo.KEY)) {
            return null;
        }
        int imageMapIndex = tag.getIntOr(FilledMapItemInfo.KEY, -1);
        int mapPartIndex = tag.getIntOr(FilledMapItemInfo.INDEX_KEY, -1);
        return new FilledMapItemInfo(imageMapIndex, mapPartIndex);
    }

    @Override
    public ItemStack withFilledMapItemInfo(ItemStack itemStack, FilledMapItemInfo filledMapItemInfo) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        CustomData customData = nmsItemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putInt(FilledMapItemInfo.KEY, filledMapItemInfo.getImageMapIndex());
        tag.putInt(FilledMapItemInfo.INDEX_KEY, filledMapItemInfo.getMapPartIndex());
        nmsItemStack.applyComponents(DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build());
        return CraftItemStack.asCraftMirror(nmsItemStack);
    }

    @Override
    public ItemStack withInvisibleItemFrameMeta(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().equals(Material.AIR)) {
            return itemStack;
        }
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        ItemLore itemLore = nmsItemStack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<net.minecraft.network.chat.Component> loreLines = new ArrayList<>(itemLore.lines());
        loreLines.add(0, net.minecraft.network.chat.Component.translatable("effect.minecraft.invisibility").withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false)));
        nmsItemStack.applyComponents(DataComponentPatch.builder().set(DataComponents.LORE, new ItemLore(loreLines)).build());
        ItemStack modified = CraftItemStack.asCraftMirror(nmsItemStack);
        ItemMeta itemMeta = modified.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }
        itemMeta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        modified.setItemMeta(itemMeta);
        return modified;
    }

    @Override
    public List<ItemStack> giveItems(Player player, List<ItemStack> itemStacks) {
        List<ItemStack> leftovers = new ArrayList<>();
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        net.minecraft.world.entity.player.Inventory inventory = nmsPlayer.getInventory();
        for (ItemStack itemStack : itemStacks) {
            net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
            boolean added = inventory.add(nmsItemStack);
            if (!added || !nmsItemStack.isEmpty()) {
                leftovers.add(CraftItemStack.asBukkitCopy(nmsItemStack));
            }
        }
        nmsPlayer.containerMenu.broadcastChanges();
        return leftovers;
    }

    @SuppressWarnings("PatternValidation")
    public Key getWorldNamespacedKey(World world) {
        NamespacedKey key = world.getKey();
        return Key.key(key.getNamespace(), key.getKey());
    }

}
