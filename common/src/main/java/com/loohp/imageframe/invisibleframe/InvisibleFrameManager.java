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

package com.loohp.imageframe.invisibleframe;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.nms.NMS;
import com.loohp.imageframe.utils.MCVersion;
import com.loohp.platformscheduler.ScheduledTask;
import com.loohp.platformscheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class InvisibleFrameManager implements Listener {

    public static final NamespacedKey INVISIBLE_GLOW_ITEM_FRAME_CRAFTING_KEY = new NamespacedKey(ImageFrame.plugin, "invisible_glow_item_frame");
    public static final NamespacedKey INVISIBLE_KEY = new NamespacedKey(ImageFrame.plugin, "invisible");

    private final Map<UUID, Location> removedInvisibleFrames;
    private final Set<UUID> knownAreaEffectClouds;

    private final AtomicLong itemFramesMadeInvisible;
    private final AtomicLong invisibleItemFramesPlaced;

    public InvisibleFrameManager() {
        this.removedInvisibleFrames = new ConcurrentHashMap<>();
        this.knownAreaEffectClouds = ConcurrentHashMap.newKeySet();

        this.itemFramesMadeInvisible = new AtomicLong(0);
        this.invisibleItemFramesPlaced = new AtomicLong(0);

        Bukkit.getPluginManager().registerEvents(this, ImageFrame.plugin);

        if (ImageFrame.version.isNewerOrEqualTo(MCVersion.V1_17)) {
            addRecipes();
        }
    }

    public AtomicLong getItemFramesMadeInvisible() {
        return itemFramesMadeInvisible;
    }

    public AtomicLong getInvisibleItemFramesPlaced() {
        return invisibleItemFramesPlaced;
    }

    private void addRecipes() {
        ItemStack result = new ItemStack(Material.valueOf("GLOW_ITEM_FRAME"));
        ShapelessRecipe recipe = new ShapelessRecipe(INVISIBLE_GLOW_ITEM_FRAME_CRAFTING_KEY, withInvisibleItemFrameData(result))
                .addIngredient(Material.valueOf("GLOW_INK_SAC"))
                .addIngredient(new RecipeChoice.ExactChoice(withInvisibleItemFrameData(new ItemStack(Material.ITEM_FRAME))));
        if (ImageFrame.version.isNewerOrEqualTo(MCVersion.V1_20)) {
            CraftingRecipe vanillaRecipe = (CraftingRecipe) Bukkit.getRecipesFor(result).stream()
                    .filter(r -> r instanceof CraftingRecipe)
                    .findFirst()
                    .orElse(null);
            if (vanillaRecipe != null) {
                recipe.setCategory(vanillaRecipe.getCategory());
                recipe.setGroup(vanillaRecipe.getGroup());
            }
        }
        Bukkit.addRecipe(recipe);
    }

    public ItemStack withInvisibleItemFrameData(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().equals(Material.AIR)) {
            return itemStack;
        }
        if (isInvisibleItemFrame(itemStack)) {
            return itemStack;
        }
        ItemStack modified = NMS.getInstance().withInvisibleItemFrameMeta(itemStack);
        ItemMeta meta = modified.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        meta.getPersistentDataContainer().set(INVISIBLE_KEY, PersistentDataType.BYTE, (byte) 1);
        modified.setItemMeta(meta);
        return modified;
    }

    public boolean isInvisibleItemFrame(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().getOrDefault(INVISIBLE_KEY, PersistentDataType.BYTE, (byte) 0) > 0;
    }

    public void setInvisibleItemFrameData(Entity entity) {
        if (entity instanceof ItemFrame) {
            entity.getPersistentDataContainer().set(INVISIBLE_KEY, PersistentDataType.BYTE, (byte) 1);
        }
    }

    public boolean isInvisibleItemFrame(Entity entity) {
        return entity instanceof ItemFrame && entity.getPersistentDataContainer().getOrDefault(INVISIBLE_KEY, PersistentDataType.BYTE, (byte) 0) > 0;
    }

    @SuppressWarnings("ConstantValue")
    public void updateInvisibleItemFrame(ItemFrame itemFrame) {
        if (!itemFrame.isValid()) {
            return;
        }
        ItemStack itemStack = itemFrame.getItem();
        if (itemStack == null || itemStack.getType().equals(Material.AIR)) {
            if (ImageFrame.invisibleFrameGlowEmptyFrames) {
                itemFrame.setGlowing(true);
            }
            itemFrame.setVisible(true);
        } else {
            itemFrame.setGlowing(false);
            itemFrame.setVisible(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(HangingPlaceEvent event) {
        if (!isInvisibleItemFrame(event.getItemStack())) {
            return;
        }
        Entity entity = event.getEntity();
        Location location = entity.getLocation();
        setInvisibleItemFrameData(entity);
        invisibleItemFramesPlaced.incrementAndGet();
        Scheduler.runTaskLater(ImageFrame.plugin, () -> updateInvisibleItemFrame((ItemFrame) entity), 1, location);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(HangingBreakEvent event) {
        Entity entity = event.getEntity();
        if (!isInvisibleItemFrame(entity)) {
            return;
        }
        UUID uuid = entity.getUniqueId();
        Location location = entity.getLocation();
        removedInvisibleFrames.put(uuid, entity.getLocation());
        Scheduler.runTaskLater(ImageFrame.plugin, () -> removedInvisibleFrames.remove(uuid), 1, location);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack itemStack = item.getItemStack();
        String type = itemStack.getType().name();
        if (!type.equals("ITEM_FRAME") && !type.equals("GLOW_ITEM_FRAME")) {
            return;
        }
        if (isInvisibleItemFrame(itemStack)) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Location>> itr = removedInvisibleFrames.entrySet().iterator(); itr.hasNext();) {
            Map.Entry<UUID, Location> entry = itr.next();
            Location location = entry.getValue();
            if (location.getWorld().equals(item.getWorld())) {
                BoundingBox boundingBox = BoundingBox.of(location, 1, 1, 1);
                if (boundingBox.contains(item.getLocation().toVector())) {
                    ItemStack modified = withInvisibleItemFrameData(itemStack);
                    item.setItemStack(modified);
                    itr.remove();
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceItem(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isInvisibleItemFrame(entity)) {
            return;
        }
        Location location = entity.getLocation();
        Scheduler.runTaskLater(ImageFrame.plugin, () -> updateInvisibleItemFrame((ItemFrame) entity), 1, location);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemoveItem(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!isInvisibleItemFrame(entity)) {
            return;
        }
        Location location = entity.getLocation();
        Scheduler.runTaskLater(ImageFrame.plugin, () -> updateInvisibleItemFrame((ItemFrame) entity), 1, location);
    }

    public ItemStack splitItemStacks(ItemStack itemStack, int maxSize) {
        if (maxSize < 0) {
            return null;
        }
        int amount = itemStack.getAmount();
        if (amount <= maxSize) {
            return null;
        }
        itemStack.setAmount(maxSize);
        ItemStack splitStack = itemStack.clone();
        splitStack.setAmount(amount - maxSize);
        return splitStack;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion thrownPotion = event.getEntity();
        if (thrownPotion.getEffects().stream().noneMatch(e -> e.getType().equals(PotionEffectType.INVISIBILITY))) {
            return;
        }
        int conversionsRemaining = ImageFrame.invisibleFrameMaxConversionsPerSplash;
        BoundingBox boundingBox = thrownPotion.getBoundingBox().expand(4.0, 2.0, 4.0);
        for (Entity entity : thrownPotion.getWorld().getNearbyEntities(boundingBox)) {
            if (conversionsRemaining == 0) {
                break;
            }
            if (!(entity instanceof Item)) {
                continue;
            }
            Item item = (Item) entity;
            ItemStack itemStack = item.getItemStack();
            String type = itemStack.getType().name();
            if (!type.equals("ITEM_FRAME") && !type.equals("GLOW_ITEM_FRAME")) {
                continue;
            }
            if (isInvisibleItemFrame(itemStack)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(thrownPotion.getLocation()) < 16.0) {
                ItemStack splitStack = splitItemStacks(itemStack, conversionsRemaining);
                Location location = item.getLocation();
                if (itemStack.getAmount() > 0) {
                    itemFramesMadeInvisible.addAndGet(itemStack.getAmount());
                    item.setItemStack(withInvisibleItemFrameData(itemStack));
                    item.getWorld().playSound(item.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1F, 1F);
                } else {
                    item.remove();
                }
                Vector velocity = null;
                if (splitStack != null) {
                    Item splitItem = thrownPotion.getWorld().dropItem(location, splitStack);
                    velocity = splitItem.getVelocity();
                    splitItem.setVelocity(new Vector(0, 0, 0));
                }
                if (velocity != null) {
                    item.setVelocity(velocity);
                }
                if (conversionsRemaining >= 0) {
                    conversionsRemaining = Math.max(0, conversionsRemaining - itemStack.getAmount());
                }
            }
        }
    }

    private boolean hasInvisibilityEffect(AreaEffectCloud areaEffectCloud) {
        PotionType baseType = areaEffectCloud.getBasePotionType();
        if (baseType != null) {
            if (baseType.getPotionEffects().stream().anyMatch(e -> e.getType().equals(PotionEffectType.INVISIBILITY))) {
                return true;
            }
        }
        return areaEffectCloud.hasCustomEffect(PotionEffectType.INVISIBILITY);
    }

    private void handleAreaEffectCloud(AreaEffectCloud areaEffectCloud) {
        UUID uuid = areaEffectCloud.getUniqueId();
        if (!knownAreaEffectClouds.add(uuid)) {
            return;
        }
        AtomicReference<Runnable> runnableReference = new AtomicReference<>(null);
        AtomicReference<ScheduledTask> taskReference = new AtomicReference<>(null);
        runnableReference.set(new Runnable() {
            int conversionsRemaining = ImageFrame.invisibleFrameMaxConversionsPerSplash;
            boolean firstTick = true;
            @Override
            public void run() {
                if (firstTick) {
                    firstTick = false;
                } else if (!areaEffectCloud.isValid()) {
                    ScheduledTask task = taskReference.get();
                    if (task != null) {
                        task.cancel();
                    }
                    knownAreaEffectClouds.remove(uuid);
                    return;
                }
                taskReference.set(Scheduler.runTaskLater(ImageFrame.plugin, runnableReference.get(), 1, areaEffectCloud.getLocation()));
                if (!hasInvisibilityEffect(areaEffectCloud)) {
                    return;
                }
                if (areaEffectCloud.getTicksLived() % 5 != 0) {
                    return;
                }
                BoundingBox boundingBox = areaEffectCloud.getBoundingBox();
                for (Entity entity : areaEffectCloud.getWorld().getNearbyEntities(boundingBox)) {
                    if (conversionsRemaining == 0) {
                        break;
                    }
                    if (!(entity instanceof Item)) {
                        continue;
                    }
                    Item item = (Item) entity;
                    ItemStack itemStack = item.getItemStack();
                    String type = itemStack.getType().name();
                    if (!type.equals("ITEM_FRAME") && !type.equals("GLOW_ITEM_FRAME")) {
                        continue;
                    }
                    if (isInvisibleItemFrame(itemStack)) {
                        continue;
                    }
                    ItemStack splitStack = splitItemStacks(itemStack, conversionsRemaining);
                    Location location = item.getLocation();
                    if (itemStack.getAmount() > 0) {
                        itemFramesMadeInvisible.addAndGet(itemStack.getAmount());
                        item.setItemStack(withInvisibleItemFrameData(itemStack));
                        item.getWorld().playSound(item.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1F, 1F);
                    } else {
                        item.remove();
                    }
                    Vector velocity = null;
                    if (splitStack != null) {
                        Item splitItem = areaEffectCloud.getWorld().dropItem(location, splitStack);
                        velocity = splitItem.getVelocity();
                        splitItem.setVelocity(new Vector(0, 0, 0));
                    }
                    if (velocity != null) {
                        item.setVelocity(velocity);
                    }
                    if (conversionsRemaining >= 0) {
                        conversionsRemaining = Math.max(0, conversionsRemaining - itemStack.getAmount());
                    }
                }
            }
        });
        runnableReference.get().run();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionLinger(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof AreaEffectCloud) {
            handleAreaEffectCloud((AreaEffectCloud) entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionLinger(LingeringPotionSplashEvent event) {
        handleAreaEffectCloud(event.getAreaEffectCloud());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        handleAreaEffectCloud(event.getEntity());
    }

}
