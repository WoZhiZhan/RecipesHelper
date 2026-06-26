package com.wzz.registerhelper.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 用于模拟 1.20.x Forge 的 ItemStack NBT API
 * 目的：
 * - 减少移植成本
 * - 让旧代码尽量不改
 * - 后续可持续扩展
 */
public final class OldUtils {

    private OldUtils() {}

    /**
     * 等价于 1.20.x 的 ItemStack.hasTag()
     * 语义：是否存在任何"非默认数据"
     */
    public static boolean hasTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom != null && !custom.isEmpty()) {
            return true;
        }
        if (stack.has(DataComponents.ENCHANTMENTS)) {
            ItemEnchantments ench = stack.get(DataComponents.ENCHANTMENTS);
            if (ench != null && !ench.isEmpty()) {
                return true;
            }
        }
        if (stack.has(DataComponents.DAMAGE)) {
            Integer damage = stack.get(DataComponents.DAMAGE);
            if (damage != null && damage > 0) {
                return true;
            }
        }
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            return true;
        }
        if (stack.has(DataComponents.LORE)) {
            return true;
        }
        if (stack.has(DataComponents.DYED_COLOR)) {
            return true;
        }
        return false;
    }

    @Nullable
    public static CompoundTag getTag(ItemStack stack) {
        return buildLegacyTagForJson(stack);
    }

    @Nullable
    public static CompoundTag buildLegacyTagForJson(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        CompoundTag tag = new CompoundTag();

        // 获取 RegistryAccess（用于序列化Component）
        HolderLookup.Provider registries = getRegistryAccess();

        // 自定义数据
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom != null && !custom.isEmpty()) {
            tag.merge(custom.copyTag());
        }

        // 耐久度
        if (stack.has(DataComponents.DAMAGE)) {
            Integer damage = stack.get(DataComponents.DAMAGE);
            if (damage != null && damage > 0) {
                tag.putInt("Damage", damage);
            }
        }

        // 附魔（修复Bug #1）
        writeLegacyEnchantments(stack, tag);

        // 自定义名称（修复Bug #2）
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            CompoundTag display = tag.contains("display") ? tag.getCompound("display") : new CompoundTag();
            // 使用正确的序列化方法
            if (registries != null) {
                display.putString("Name", Component.Serializer.toJson(name, registries));
            } else {
                // 降级处理：如果无法获取registries，使用简化格式
                display.putString("Name", "{\"text\":\"" + name.getString() + "\"}");
            }
            tag.put("display", display);
        }

        // Lore
        if (stack.has(DataComponents.LORE)) {
            ListTag loreList = new ListTag();
            for (Component c : stack.get(DataComponents.LORE).lines()) {
                if (registries != null) {
                    loreList.add(StringTag.valueOf(Component.Serializer.toJson(c, registries)));
                } else {
                    // 降级处理
                    loreList.add(StringTag.valueOf("{\"text\":\"" + c.getString() + "\"}"));
                }
            }
            CompoundTag display = tag.contains("display") ? tag.getCompound("display") : new CompoundTag();
            display.put("Lore", loreList);
            tag.put("display", display);
        }

        // 染色（皮革盔甲等）
        if (stack.has(DataComponents.DYED_COLOR)) {
            var dyedColor = stack.get(DataComponents.DYED_COLOR);
            CompoundTag display = tag.contains("display") ? tag.getCompound("display") : new CompoundTag();
            display.putInt("color", dyedColor.rgb());
            tag.put("display", display);
        }

        // 无法破坏
        if (stack.has(DataComponents.UNBREAKABLE)) {
            tag.putBoolean("Unbreakable", true);
        }

        return tag.isEmpty() ? null : tag;
    }

    /**
     * 写入附魔到NBT
     */
    private static void writeLegacyEnchantments(ItemStack stack, CompoundTag tag) {
        if (!stack.has(DataComponents.ENCHANTMENTS)) {
            return;
        }
        ItemEnchantments ench = stack.get(DataComponents.ENCHANTMENTS);

        if (ench == null || ench.isEmpty()) {
            return;
        }

        ListTag list = new ListTag();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : ench.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();

            holder.unwrapKey().ifPresent(key -> {
                CompoundTag enchTag = new CompoundTag();
                enchTag.putString("id", key.location().toString());
                enchTag.putShort("lvl", (short) level);
                list.add(enchTag);
            });
        }

        if (!list.isEmpty()) {
            tag.put("Enchantments", list);
        }
    }

    /**
     * 获取RegistryAccess（用于Component序列化）
     */
    @Nullable
    private static HolderLookup.Provider getRegistryAccess() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                return server.registryAccess();
            }
        } catch (Exception e) {
            // 在某些情况下可能无法获取server
        }
        return null;
    }
}