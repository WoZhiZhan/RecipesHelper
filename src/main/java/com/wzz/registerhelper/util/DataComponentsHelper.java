package com.wzz.registerhelper.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * NeoForge 1.21.1 DataComponents 转 JSON 工具
 * 用于配方系统的 components 字段
 */
public class DataComponentsHelper {

    /**
     * 将纯文本包装成 SNBT 单引号字符串字面量。
     *
     * <p>1.21 中 minecraft:custom_name / lore 等文本组件，其值在配方 JSON 里会被
     * 当作文本组件解析。若文本是纯数字（如 "999"）或以数字/特殊符号开头，
     * 不加引号会被解析器误判为数字或非法 token，导致配方无法生成。
     * 用单引号包裹并转义内部的反斜杠与单引号，可强制其作为字符串字面量，
     * 例如 999 -> '999'，使其稳定地保持为文本。
     */
    private static String toSnbtString(String raw) {
        if (raw == null) raw = "";
        // 转义反斜杠和单引号
        String escaped = raw.replace("\\", "\\\\").replace("'", "\\'");
        return "'" + escaped + "'";
    }

    /**
     * 检查物品是否有任何需要保存的组件
     */
    public static boolean hasAnyComponents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        return stack.has(DataComponents.ENCHANTMENTS) ||
                stack.has(DataComponents.STORED_ENCHANTMENTS) ||  // 附魔书
                stack.has(DataComponents.CUSTOM_NAME) ||
                stack.has(DataComponents.LORE) ||
                stack.has(DataComponents.DAMAGE) ||
                stack.has(DataComponents.DYED_COLOR) ||
                stack.has(DataComponents.CUSTOM_DATA) ||
                stack.has(DataComponents.UNBREAKABLE) ||
                stack.has(DataComponents.CUSTOM_MODEL_DATA);
    }

    /**
     * 将 ItemStack 的 DataComponents 转换为 JSON 格式
     * 用于配方的 components 字段
     */
    public static JsonObject toComponentsJson(ItemStack stack) {
        JsonObject components = new JsonObject();

        if (stack == null || stack.isEmpty()) {
            return components;
        }

        // 判断是否是附魔书
        boolean isEnchantedBook = stack.is(Items.ENCHANTED_BOOK);

        // 1. 附魔 (minecraft:enchantments) - 普通物品使用
        if (!isEnchantedBook && stack.has(DataComponents.ENCHANTMENTS)) {
            ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
            if (enchantments != null && !enchantments.isEmpty()) {
                JsonObject enchObj = enchantsToJson(enchantments);
                if (enchObj.size() > 0) {
                    components.add("minecraft:enchantments", enchObj);
                }
            }
        }

        // 2. 存储的附魔 (minecraft:stored_enchantments) - 附魔书使用
        if (stack.has(DataComponents.STORED_ENCHANTMENTS)) {
            ItemEnchantments storedEnchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (storedEnchantments != null && !storedEnchantments.isEmpty()) {
                JsonObject enchObj = enchantsToJson(storedEnchantments);
                if (enchObj.size() > 0) {
                    components.add("minecraft:stored_enchantments", enchObj);
                }
            }
        }

        // 3. 耐久度 (minecraft:damage)
        if (stack.has(DataComponents.DAMAGE)) {
            Integer damage = stack.get(DataComponents.DAMAGE);
            if (damage != null && damage > 0) {
                components.addProperty("minecraft:damage", damage);
            }
        }

        // 4. 自定义名称 (minecraft:custom_name)
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            // 用 SNBT 单引号包裹，避免纯数字名称（如 "999"）被解析为数字
            components.addProperty("minecraft:custom_name", toSnbtString(name.getString()));
        }

        // 5. Lore (minecraft:lore)
        if (stack.has(DataComponents.LORE)) {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (!lore.lines().isEmpty()) {
                JsonArray loreArray = new JsonArray();
                for (Component line : lore.lines()) {
                    loreArray.add(toSnbtString(line.getString()));
                }
                components.add("minecraft:lore", loreArray);
            }
        }

        // 6. 染色 (minecraft:dyed_color)
        if (stack.has(DataComponents.DYED_COLOR)) {
            DyedItemColor dyedColor = stack.get(DataComponents.DYED_COLOR);
            JsonObject colorObj = new JsonObject();
            colorObj.addProperty("rgb", dyedColor.rgb());
            colorObj.addProperty("show_in_tooltip", dyedColor.showInTooltip());
            components.add("minecraft:dyed_color", colorObj);
        }

        // 7. 无法破坏 (minecraft:unbreakable)
        if (stack.has(DataComponents.UNBREAKABLE)) {
            JsonObject unbreakableObj = new JsonObject();
            unbreakableObj.addProperty("show_in_tooltip", true);
            components.add("minecraft:unbreakable", unbreakableObj);
        }

        // 8. 自定义模型数据 (minecraft:custom_model_data)
        if (stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
            var customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            components.addProperty("minecraft:custom_model_data", customModelData.value());
        }

        // 9. 自定义数据 (minecraft:custom_data)
        if (stack.has(DataComponents.CUSTOM_DATA)) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (!customData.isEmpty()) {
                // 将 NBT 转换为 JSON（简化处理）
                components.addProperty("minecraft:custom_data", customData.copyTag().toString());
            }
        }

        return components;
    }

    /**
     * 将附魔数据转换为 JSON 对象
     * 通用方法，支持 enchantments 和 stored_enchantments
     */
    private static JsonObject enchantsToJson(ItemEnchantments enchantments) {
        JsonObject enchObj = new JsonObject();

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();

            holder.unwrapKey().ifPresent(key -> {
                enchObj.addProperty(key.location().toString(), level);
            });
        }

        return enchObj;
    }

    /**
     * 创建带 components 的材料 JSON
     */
    public static JsonObject createIngredientWithComponents(ItemStack stack) {
        JsonObject ingredient = new JsonObject();

        // 物品ID
        ingredient.addProperty("item",
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());

        // components
        if (hasAnyComponents(stack)) {
            JsonObject components = toComponentsJson(stack);
            if (components.size() > 0) {
                ingredient.add("components", components);
            }
        }

        // 数量
        if (stack.getCount() > 1) {
            ingredient.addProperty("count", stack.getCount());
        }

        return ingredient;
    }

    /**
     * 创建带 components 的结果 JSON
     */
    public static JsonObject createResultWithComponents(ItemStack stack) {
        JsonObject result = new JsonObject();

        // 物品ID
        result.addProperty("id",
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());

        // components
        if (hasAnyComponents(stack)) {
            JsonObject components = toComponentsJson(stack);
            if (components.size() > 0) {
                result.add("components", components);
            }
        }

        // 数量
        if (stack.getCount() > 1) {
            result.addProperty("count", stack.getCount());
        }

        return result;
    }
}