package com.wzz.registerhelper.gui.recipe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方材料数据类
 * 支持三种类型：普通物品、标签、自定义标签
 */
public class IngredientData {
    
    public enum Type {
        ITEM("物品"),
        TAG("标签"),
        CUSTOM_TAG("自定义标签");
        
        private final String displayName;
        
        Type(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private Type type;
    private ItemStack itemStack;           // 物品类型（包含NBT）
    private ResourceLocation tagId;        // 标签ID
    private List<ItemStack> customTagItems; // 自定义标签的物品列表
    
    // 私有构造函数
    private IngredientData(Type type) {
        this.type = type;
        this.customTagItems = new ArrayList<>();
    }
    
    // === 静态工厂方法 ===
    
    /**
     * 创建物品材料（带NBT）
     */
    public static IngredientData fromItem(ItemStack itemStack) {
        IngredientData data = new IngredientData(Type.ITEM);
        data.itemStack = itemStack.copy();
        return data;
    }
    
    /**
     * 创建标签材料
     */
    public static IngredientData fromTag(ResourceLocation tagId) {
        IngredientData data = new IngredientData(Type.TAG);
        data.tagId = tagId;
        return data;
    }
    
    /**
     * 创建自定义标签材料
     */
    public static IngredientData fromCustomTag(ResourceLocation tagId, List<ItemStack> items) {
        IngredientData data = new IngredientData(Type.CUSTOM_TAG);
        data.tagId = tagId;
        data.customTagItems = new ArrayList<>();
        for (ItemStack item : items) {
            data.customTagItems.add(item.copy());
        }
        return data;
    }
    
    /**
     * 创建空材料
     */
    public static IngredientData empty() {
        IngredientData data = new IngredientData(Type.ITEM);
        data.itemStack = ItemStack.EMPTY;
        return data;
    }
    
    // === Getters ===
    
    public Type getType() {
        return type;
    }
    
    public ItemStack getItemStack() {
        return itemStack != null ? itemStack : ItemStack.EMPTY;
    }
    
    public ResourceLocation getTagId() {
        return tagId;
    }
    
    public List<ItemStack> getCustomTagItems() {
        return new ArrayList<>(customTagItems);
    }
    
    public boolean isEmpty() {
        return switch (type) {
            case ITEM -> itemStack == null || itemStack.isEmpty();
            case TAG -> tagId == null;
            case CUSTOM_TAG -> tagId == null || customTagItems.isEmpty();
        };
    }
    
    /**
     * 获取用于显示的物品
     * 对于标签，返回第一个匹配的物品
     */
    public ItemStack getDisplayStack() {
        return switch (type) {
            case ITEM -> getItemStack();
            case TAG -> getFirstTagItem();
            case CUSTOM_TAG -> customTagItems.isEmpty() ? ItemStack.EMPTY : customTagItems.get(0);
        };
    }
    
    /**
     * 获取标签的第一个物品（用于显示）
     */
    private ItemStack getFirstTagItem() {
        if (tagId == null) return ItemStack.EMPTY;
        
        // 尝试从注册表中查找带有此标签的物品
        return ForgeRegistries.ITEMS.getValues().stream()
                .filter(item -> item.builtInRegistryHolder().is(tagId))
                .findFirst()
                .map(item -> new ItemStack(item, 1))
                .orElse(ItemStack.EMPTY);
    }
    
    /**
     * 获取显示文本
     */
    public String getDisplayText() {
        return switch (type) {
            case ITEM -> {
                if (itemStack.isEmpty()) {
                    yield "空";
                }
                String name = itemStack.getItem().getDescription().getString();
                if (itemStack.hasTag()) {
                    yield name + " §7(带NBT)";
                }
                yield name;
            }
            case TAG -> tagId != null ? "§6#" + tagId : "未知标签";
            case CUSTOM_TAG -> tagId != null ? "§b自定义#" + tagId : "未知自定义标签";
        };
    }
    
    /**
     * 转换为配方JSON使用的对象
     */
    public Object toRecipeObject() {
        return switch (type) {
            case ITEM -> {
                if (itemStack.isEmpty()) {
                    yield null;
                }
                // 如果有NBT，返回完整的ItemStack信息
                if (itemStack.hasTag()) {
                    yield itemStack; // 返回ItemStack，由构建器处理NBT
                }
                // 否则返回物品ID字符串
                yield ForgeRegistries.ITEMS.getKey(itemStack.getItem()).toString();
            }
            case TAG -> tagId != null ? "#" + tagId : null;
            case CUSTOM_TAG -> tagId != null ? "#" + tagId : null;
        };
    }
    
    /**
     * 检查是否有NBT数据
     */
    public boolean hasNBT() {
        return type == Type.ITEM && itemStack != null && itemStack.hasTag();
    }
    
    /**
     * 获取NBT数据
     */
    public CompoundTag getNBT() {
        if (type == Type.ITEM && itemStack != null) {
            return itemStack.getTag();
        }
        return null;
    }
    
    /**
     * 复制数据
     */
    public IngredientData copy() {
        return switch (type) {
            case ITEM -> fromItem(getItemStack());
            case TAG -> fromTag(tagId);
            case CUSTOM_TAG -> fromCustomTag(tagId, customTagItems);
        };
    }
    
    @Override
    public String toString() {
        return "IngredientData{" +
                "type=" + type +
                ", display=" + getDisplayText() +
                '}';
    }
}