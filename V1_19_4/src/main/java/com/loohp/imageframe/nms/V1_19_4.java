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
import com.loohp.imageframe.objectholders.MutablePair;
import com.loohp.imageframe.utils.UUIDUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.kyori.adventure.key.Key;
import net.minecraft.EnumChatFormat;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.chat.ChatModifier;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.protocol.game.PacketPlayOutMap;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkProviderServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.PlayerChunkMap;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.decoration.EntityItemFrame;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.item.ItemWorldMap;
import net.minecraft.world.level.saveddata.maps.MapIcon;
import net.minecraft.world.level.saveddata.maps.PersistentIdCounts;
import net.minecraft.world.level.saveddata.maps.WorldMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_19_R3.map.CraftMapView;
import org.bukkit.craftbukkit.v1_19_R3.map.RenderData;
import org.bukkit.craftbukkit.v1_19_R3.util.CraftChatMessage;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class V1_19_4 extends NMSWrapper {

    private final Field nmsEntityByteDataWatcherField;
    private final Field craftMapViewWorldMapField;
    private final Field persistentIdCountsUsedAuxIdsField;

    public V1_19_4() {
        try {
            nmsEntityByteDataWatcherField = net.minecraft.world.entity.Entity.class.getDeclaredField("an");
            craftMapViewWorldMapField = CraftMapView.class.getDeclaredField("worldMap");
            persistentIdCountsUsedAuxIdsField = PersistentIdCounts.class.getDeclaredField("b");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public WorldMap getWorldMap(MapView mapView) {
        try {
            CraftMapView craftMapView = (CraftMapView) mapView;
            craftMapViewWorldMapField.setAccessible(true);
            return (WorldMap) craftMapViewWorldMapField.get(craftMapView);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setColors(MapView mapView, byte[] colors) {
        if (colors.length != COLOR_ARRAY_LENGTH) {
            throw new IllegalArgumentException("colors array length must be " + COLOR_ARRAY_LENGTH);
        }
        WorldMap nmsWorldMap = getWorldMap(mapView);
        nmsWorldMap.g = colors;
    }

    @Override
    public Collection<Player> getViewers(MapView mapView) {
        WorldMap nmsWorldMap = getWorldMap(mapView);
        Map<EntityHuman, WorldMap.WorldMapHumanTracker> humansMap = nmsWorldMap.o;
        return Collections2.transform(humansMap.keySet(), e -> (Player) e.getBukkitEntity());
    }

    @Override
    public boolean hasViewers(MapView mapView) {
        WorldMap nmsWorldMap = getWorldMap(mapView);
        Map<EntityHuman, WorldMap.WorldMapHumanTracker> humansMap = nmsWorldMap.o;
        return !humansMap.isEmpty();
    }

    @Override
    public MapIcon toNMSMapIcon(MapCursor mapCursor) {
        MapIcon.Type mapIconType = toNMSMapIconType(mapCursor.getType());
        IChatBaseComponent iChat = CraftChatMessage.fromStringOrNull(mapCursor.getCaption());
        return new MapIcon(mapIconType, mapCursor.getX(), mapCursor.getY(), mapCursor.getDirection(), iChat);
    }

    @SuppressWarnings("deprecation")
    @Override
    public MapIcon.Type toNMSMapIconType(MapCursor.Type type) {
        return MapIcon.Type.a(type.getValue());
    }

    @Override
    public boolean isRenderOnFrame(MapCursor.Type type) {
        MapIcon.Type mapIconType = toNMSMapIconType(type);
        return mapIconType.b();
    }

    @SuppressWarnings("unchecked")
    @Override
    public int getNextAvailableMapId(World world) {
        try {
            persistentIdCountsUsedAuxIdsField.setAccessible(true);
            WorldServer worldServer = ((CraftWorld) world).getHandle();
            PersistentIdCounts persistentIdCounts = worldServer.n().D().s().a(PersistentIdCounts::b, PersistentIdCounts::new, PersistentIdCounts.a);
            Object2IntMap<String> usedAuxIds = (Object2IntMap<String>) persistentIdCountsUsedAuxIdsField.get(persistentIdCounts);
            return usedAuxIds.getInt("map") + 1;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @Override
    public MapView getMapOrCreateMissing(World world, int id) {
        try {
            MapView mapView = Bukkit.getMap(id);
            if (mapView != null) {
                return mapView;
            }
            persistentIdCountsUsedAuxIdsField.setAccessible(true);
            Location spawnLocation = world.getSpawnLocation();
            WorldServer worldServer = ((CraftWorld) world).getHandle();
            ResourceKey<net.minecraft.world.level.World> worldTypeKey = worldServer.ab();
            WorldMap worldMap = WorldMap.a(spawnLocation.getX(), spawnLocation.getZ(), (byte) 3, false, false, worldTypeKey);
            worldServer.a(ItemWorldMap.a(id), worldMap);
            PersistentIdCounts persistentIdCounts = worldServer.n().D().s().a(PersistentIdCounts::b, PersistentIdCounts::new, PersistentIdCounts.a);
            Object2IntMap<String> usedAuxIds = (Object2IntMap<String>) persistentIdCountsUsedAuxIdsField.get(persistentIdCounts);
            int freeAuxValue = usedAuxIds.getInt("map");
            if (freeAuxValue < id) {
                usedAuxIds.put("map", id);
                persistentIdCounts.b();
            }
            return Bukkit.getMap(id);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MutablePair<byte[], List<MapCursor>> bukkitRenderMap(MapView mapView, Player player) {
        CraftMapView craftMapView = (CraftMapView) mapView;
        CraftPlayer craftPlayer = (CraftPlayer) player;
        RenderData renderData = craftMapView.render(craftPlayer);
        return new MutablePair<>(renderData.buffer, renderData.cursors);
    }

    @Override
    public Set<Player> getEntityTrackers(Entity entity) {
        WorldServer worldServer = ((CraftWorld) entity.getWorld()).getHandle();
        ChunkProviderServer chunkProviderServer = worldServer.k();
        PlayerChunkMap playerChunkMap = chunkProviderServer.a;
        Int2ObjectMap<PlayerChunkMap.EntityTracker> entityTrackers = playerChunkMap.L;
        PlayerChunkMap.EntityTracker entityTracker = entityTrackers.get(entity.getEntityId());
        if (entityTracker == null) {
            return Collections.emptySet();
        } else {
            Set<Player> players = new HashSet<>();
            for (ServerPlayerConnection connection : entityTracker.f) {
                if (connection instanceof PlayerConnection) {
                    players.add(((PlayerConnection) connection).getCraftPlayer());
                }
            }
            return players;
        }
    }

    @Override
    public PacketPlayOutMap createMapPacket(int mapId, byte[] colors, Collection<MapCursor> cursors) {
        List<MapIcon> mapIcons = cursors == null ? null : cursors.stream().map(this::toNMSMapIcon).collect(Collectors.toList());
        WorldMap.b b = colors == null ? null : new WorldMap.b(0, 0, 128, 128, colors);
        return new PacketPlayOutMap(mapId, (byte) 0, false, mapIcons, b);
    }

    @Override
    public PacketPlayOutEntityMetadata createItemFrameItemChangePacket(int entityId, ItemStack itemStack) {
        List<DataWatcher.b<?>> dataWatchers = Collections.singletonList(DataWatcher.b.a(EntityItemFrame.g, CraftItemStack.asNMSCopy(itemStack)));
        return new PacketPlayOutEntityMetadata(entityId, dataWatchers);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object createEntityFlagsPacket(Entity entity, Boolean invisible, Boolean glowing) {
        try {
            int entityId = entity.getEntityId();
            net.minecraft.world.entity.Entity nmsEntity = ((CraftEntity) entity).getHandle();

            nmsEntityByteDataWatcherField.setAccessible(true);

            DataWatcher watcher = nmsEntity.aj();
            DataWatcherObject<Byte> byteField = (DataWatcherObject<Byte>) nmsEntityByteDataWatcherField.get(null);
            byte value = watcher.a(byteField);

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

            List<DataWatcher.b<?>> dataWatchers = Collections.singletonList(DataWatcher.b.a(byteField, value));
            return new PacketPlayOutEntityMetadata(entityId, dataWatchers);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        ((CraftPlayer) player).getHandle().b.a((Packet<?>) packet);
    }

    @Override
    public CombinedMapItemInfo getCombinedMapItemInfo(ItemStack itemStack) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tag = nmsItemStack.u();
        if (tag == null || !tag.e(CombinedMapItemInfo.KEY)) {
            return null;
        }
        int imageMapIndex = tag.h(CombinedMapItemInfo.KEY);
        if (!tag.e(CombinedMapItemInfo.PLACEMENT_UUID_KEY) || !tag.e(CombinedMapItemInfo.PLACEMENT_YAW_KEY)) {
            return new CombinedMapItemInfo(imageMapIndex);
        }
        float yaw = tag.j(CombinedMapItemInfo.PLACEMENT_YAW_KEY);
        UUID uuid = UUIDUtils.fromIntArray(tag.n(CombinedMapItemInfo.PLACEMENT_UUID_KEY));
        return new CombinedMapItemInfo(imageMapIndex, new CombinedMapItemInfo.PlacementInfo(yaw, uuid));
    }

    @Override
    public ItemStack withCombinedMapItemInfo(ItemStack itemStack, CombinedMapItemInfo combinedMapItemInfo) {
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tag = nmsItemStack.v();
        tag.a(CombinedMapItemInfo.KEY, combinedMapItemInfo.getImageMapIndex());
        if (combinedMapItemInfo.hasPlacement()) {
            CombinedMapItemInfo.PlacementInfo placement = combinedMapItemInfo.getPlacement();
            tag.a(CombinedMapItemInfo.PLACEMENT_YAW_KEY, placement.getYaw());
            tag.a(CombinedMapItemInfo.PLACEMENT_UUID_KEY, UUIDUtils.toIntArray(placement.getUniqueId()));
        }
        return CraftItemStack.asCraftMirror(nmsItemStack);
    }

    @Override
    public ItemStack withInvisibleItemFrameMeta(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().equals(Material.AIR)) {
            return itemStack;
        }
        net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound displayTag = nmsItemStack.a("display");
        List<String> loreLines;
        if (displayTag.d("Lore") == NBTBase.q) {
            NBTTagList loreLineTagList = displayTag.c("Lore", NBTBase.p);
            loreLines = new ArrayList<>(loreLineTagList.size());
            for (int i = 0; i < loreLineTagList.size(); i++) {
                loreLines.add(loreLineTagList.j(i));
            }
        } else {
            loreLines = new ArrayList<>(1);
        }
        loreLines.add(0, CraftChatMessage.toJSON(IChatBaseComponent.c("effect.minecraft.invisibility").c(ChatModifier.a.c(EnumChatFormat.h).b(false))));
        NBTTagList loreLineTagList = new NBTTagList();
        for (int i = 0; i < loreLines.size(); i++) {
            loreLineTagList.b(i, NBTTagString.a(loreLines.get(i)));
        }
        displayTag.a("Lore", loreLineTagList);
        ItemStack modified = CraftItemStack.asCraftMirror(nmsItemStack);
        ItemMeta itemMeta = modified.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }
        itemMeta.addEnchant(Enchantment.LUCK, 1, true);
        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        modified.setItemMeta(itemMeta);
        return modified;
    }

    @Override
    public List<ItemStack> giveItems(Player player, List<ItemStack> itemStacks) {
        List<ItemStack> leftovers = new ArrayList<>();
        EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        PlayerInventory inventory = nmsPlayer.fJ();
        for (ItemStack itemStack : itemStacks) {
            net.minecraft.world.item.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
            boolean added = inventory.e(nmsItemStack);
            if (!added || !nmsItemStack.b()) {
                leftovers.add(CraftItemStack.asBukkitCopy(nmsItemStack));
            }
        }
        nmsPlayer.bP.d();
        return leftovers;
    }

    @SuppressWarnings("PatternValidation")
    public Key getWorldNamespacedKey(World world) {
        NamespacedKey key = world.getKey();
        return Key.key(key.getNamespace(), key.getKey());
    }
}
