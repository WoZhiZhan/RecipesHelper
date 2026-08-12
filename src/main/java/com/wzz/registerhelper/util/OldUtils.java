package com.wzz.registerhelper.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/** Compatibility helpers for code that still presents item data as legacy NBT. */
public final class OldUtils {
    private OldUtils() {
    }

    /** Equivalent to the old hasTag() concept: any explicit non-default data. */
    public static boolean hasTag(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !stack.isComponentsPatchEmpty();
    }

    /**
     * Returns a legacy-shaped view for existing GUI code. Recipe serialization
     * must use DataComponentsHelper instead of this compatibility projection.
     */
    @Nullable
    public static CompoundTag getTag(ItemStack stack) {
        return buildLegacyTagForJson(stack);
    }

    /** Returns only minecraft:custom_data, without synthesizing legacy fields. */
    @Nullable
    public static CompoundTag getCustomDataTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null || customData.isEmpty() ? null : customData.copyTag();
    }

    /**
     * Legacy setTag() data maps to minecraft:custom_data in 1.21.1. Structured
     * vanilla components should be supplied through the standard stack codec.
     */
    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag == null ? new CompoundTag() : tag);
    }

    @Nullable
    public static CompoundTag buildLegacyTagForJson(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !hasTag(stack)) {
            return null;
        }

        CompoundTag tag = new CompoundTag();
        HolderLookup.Provider registries = getRegistryAccess();

        CompoundTag customData = getCustomDataTag(stack);
        if (customData != null) {
            tag.merge(customData);
        }

        Integer damage = stack.get(DataComponents.DAMAGE);
        if (damage != null && damage > 0) {
            tag.putInt("Damage", damage);
        }

        Integer repairCost = stack.get(DataComponents.REPAIR_COST);
        if (repairCost != null && repairCost > 0) {
            tag.putInt("RepairCost", repairCost);
        }

        writeLegacyEnchantments(stack.get(DataComponents.ENCHANTMENTS), tag, "Enchantments");
        writeLegacyEnchantments(stack.get(DataComponents.STORED_ENCHANTMENTS), tag, "StoredEnchantments");

        if (stack.has(DataComponents.CUSTOM_NAME)) {
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            CompoundTag display = tag.contains("display") ? tag.getCompound("display") : new CompoundTag();
            display.putString("Name", Component.Serializer.toJson(name, registries));
            tag.put("display", display);
        }

        if (stack.has(DataComponents.LORE)) {
            ListTag lore = new ListTag();
            for (Component line : stack.get(DataComponents.LORE).lines()) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(line, registries)));
            }
            CompoundTag display = tag.contains("display") ? tag.getCompound("display") : new CompoundTag();
            display.put("Lore", lore);
            tag.put("display", display);
        }

        if (stack.has(DataComponents.DYED_COLOR)) {
            CompoundTag display = tag.contains("display") ? tag.getCompound("display") : new CompoundTag();
            display.putInt("color", stack.get(DataComponents.DYED_COLOR).rgb());
            tag.put("display", display);
        }

        if (stack.has(DataComponents.UNBREAKABLE)) {
            tag.putBoolean("Unbreakable", true);
        }
        if (stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
            tag.putInt("CustomModelData", stack.get(DataComponents.CUSTOM_MODEL_DATA).value());
        }

        // Preserve a codec-backed snapshot for components that have no 1.20
        // projection. Existing GUI checks can still use the legacy keys above.
        try {
            Tag serialized = stack.save(registries);
            if (serialized instanceof CompoundTag stackTag
                    && stackTag.get("components") instanceof CompoundTag components
                    && !components.isEmpty()) {
                tag.put("components", components.copy());
            }
        } catch (RuntimeException ignored) {
            // Dynamic registry data may not be available during early setup.
        }

        return tag.isEmpty() ? null : tag;
    }

    private static void writeLegacyEnchantments(@Nullable ItemEnchantments enchantments,
                                                 CompoundTag tag,
                                                 String key) {
        if (enchantments == null || enchantments.isEmpty()) {
            return;
        }

        ListTag list = new ListTag();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            entry.getKey().unwrapKey().ifPresent(enchantmentKey -> {
                CompoundTag enchantment = new CompoundTag();
                enchantment.putString("id", enchantmentKey.location().toString());
                enchantment.putShort("lvl", (short) entry.getIntValue());
                list.add(enchantment);
            });
        }
        if (!list.isEmpty()) {
            tag.put(key, list);
        }
    }

    /** Returns the live server/client registry provider, with built-ins as fallback. */
    public static HolderLookup.Provider getRegistryAccess() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                return server.registryAccess();
            }
        } catch (RuntimeException ignored) {
        }

        try {
            HolderLookup.Provider clientRegistries = ClientRegistryAccess.get();
            if (clientRegistries != null) {
                return clientRegistries;
            }
        } catch (Throwable ignored) {
            // Client classes are absent on a dedicated server.
        }

        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /** Isolates client linkage so dedicated servers do not load Minecraft. */
    private static final class ClientRegistryAccess {
        @Nullable
        private static HolderLookup.Provider get() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return null;
            }
            if (minecraft.getConnection() != null) {
                return minecraft.getConnection().registryAccess();
            }
            return minecraft.level != null ? minecraft.level.registryAccess() : null;
        }
    }
}
