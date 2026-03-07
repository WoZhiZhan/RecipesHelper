package com.wzz.registerhelper.gui.recipe;

import com.wzz.registerhelper.init.ModConfig;
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
    
    private final Type type;
    private ItemStack itemStack;           // 物品类型（包含NBT）
    private ResourceLocation tagId;        // 标签ID
    private List<ItemStack> customTagItems; // 自定义标签的物品列表
    private boolean includeNBT = true;     // 是否在配方JSON中写入NBT匹配（仅对ITEM类型有效）

    /**
     * 匹配时从物品 NBT 中忽略的 key 列表。
     * <p>仅当 {@code includeNBT=true} 且物品有 NBT 时生效。
     * 非空时会使用 {@code registerhelper:partial_nbt} 而非 {@code forge:nbt}，
     * 从而实现"忽略动态 key（如 lastUsed）"的部分匹配。
     *
     * <p>典型值：{@code ["lastUsed", "AttackCount"]}（适用于拔刀剑类物品）
     */
    private List<String> ignoreNbtKeys = new ArrayList<>();
    
    // 私有构造函数
    private IngredientData(Type type) {
        this.type = type;
        this.customTagItems = new ArrayList<>();
    }

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
        
        return ForgeRegistries.ITEMS.getValues().stream()
                .filter(item -> item.builtInRegistryHolder().is(tagId))
                .findFirst()
                .map(item -> new ItemStack(item, 1))
                .orElse(ItemStack.EMPTY);
    }
    
    /**
     * 获取显示文本。
     * <ul>
     *   <li>紫色 §d(匹配NBT)：forge:nbt 精确匹配</li>
     *   <li>黄色 §e(部分匹配)：registerhelper:partial_nbt，忽略指定 key</li>
     *   <li>灰色 §8(忽略NBT)：不写入 NBT</li>
     * </ul>
     */
    public String getDisplayText() {
        return switch (type) {
            case ITEM -> {
                if (itemStack.isEmpty()) {
                    yield "空";
                }
                String name = itemStack.getItem().getDescription().getString();
                if (itemStack.hasTag()) {
                    if (!includeNBT) {
                        yield name + " §8(忽略NBT)";
                    } else if (!ignoreNbtKeys.isEmpty()) {
                        yield name + " §e(部分匹配)";
                    } else {
                        yield name + " §d(匹配NBT)";
                    }
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
                if (itemStack.hasTag()) {
                    yield itemStack; // 由 RecipeUtil 处理 NBT / partial_nbt 逻辑
                }
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
     * 是否在配方中启用 NBT 匹配
     */
    public boolean isIncludeNBT() {
        return includeNBT;
    }

    /**
     * 设置是否启用 NBT 匹配
     */
    public void setIncludeNBT(boolean value) {
        this.includeNBT = value;
    }

    /**
     * 切换 NBT 匹配开关（仅对带 NBT 的 ITEM 有效）
     *
     * <p>循环顺序：
     * <pre>
     *   精确匹配(forge:nbt)  →  部分匹配(registerhelper:partial_nbt，保留已有 ignoreKeys)
     *                        →  忽略 NBT
     *                        →  精确匹配 ...
     * </pre>
     * 若 ignoreKeys 为空则跳过"部分匹配"状态，直接在精确/忽略之间切换。
     *
     * @return 切换后的新状态描述
     */
    public String cycleNbtMode() {
        if (type != Type.ITEM || !hasNBT()) return "无NBT";

        if (includeNBT && ignoreNbtKeys.isEmpty()) {
            // 精确 → 忽略
            includeNBT = false;
            return "忽略NBT";
        } else if (!includeNBT) {
            // 忽略 → 精确
            includeNBT = true;
            return "匹配NBT";
        }
        // 部分匹配 → 忽略（保留 ignoreKeys，方便再切回来）
        includeNBT = false;
        return "忽略NBT";
    }

    /**
     * 旧接口兼容：切换精确匹配 / 忽略 NBT（不涉及部分匹配状态）。
     */
    public boolean toggleIncludeNBT() {
        if (type == Type.ITEM && hasNBT()) {
            includeNBT = !includeNBT;
        }
        return includeNBT;
    }

    /**
     * 获取 NBT 匹配模式：
     * <ul>
     *   <li>{@code "exact"}    - forge:nbt 精确匹配</li>
     *   <li>{@code "partial"}  - registerhelper:partial_nbt 部分匹配</li>
     *   <li>{@code "none"}     - 忽略 NBT</li>
     * </ul>
     */
    public String getNbtMode() {
        if (!includeNBT) return "none";
        if (!ignoreNbtKeys.isEmpty()) return "partial";
        return "exact";
    }

    // ---- ignoreNbtKeys 相关方法 ----

    /**
     * 获取忽略的 NBT key 列表（不可变副本）
     */
    public List<String> getIgnoreNbtKeys() {
        return List.copyOf(ignoreNbtKeys);
    }

    /**
     * 设置忽略的 NBT key 列表。
     * <p>调用后会自动将 includeNBT 切换为 true（启用部分匹配）。
     *
     * @param keys 要忽略的 key；传空列表则退化为普通 forge:nbt 精确匹配
     */
    public void setIgnoreNbtKeys(List<String> keys) {
        this.ignoreNbtKeys = new ArrayList<>(keys);
        if (!keys.isEmpty()) {
            this.includeNBT = true; // 有忽略 key 时自动启用 NBT 匹配
        }
    }

    /**
     * 追加一个忽略 key（不重复）
     */
    public void addIgnoreNbtKey(String key) {
        if (!ignoreNbtKeys.contains(key)) {
            ignoreNbtKeys.add(key);
            this.includeNBT = true;
        }
    }

    /**
     * 移除一个忽略 key
     */
    public void removeIgnoreNbtKey(String key) {
        ignoreNbtKeys.remove(key);
    }

    /**
     * 是否处于"部分匹配"模式（有 ignoreKeys 且 includeNBT=true）
     */
    public boolean isPartialNbtMode() {
        return includeNBT && !ignoreNbtKeys.isEmpty();
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
        IngredientData result = switch (type) {
            case ITEM -> fromItem(getItemStack());
            case TAG -> fromTag(tagId);
            case CUSTOM_TAG -> fromCustomTag(tagId, customTagItems);
        };
        result.includeNBT = this.includeNBT;
        result.ignoreNbtKeys = new ArrayList<>(this.ignoreNbtKeys);
        return result;
    }
    
    @Override
    public String toString() {
        return "IngredientData{" +
                "type=" + type +
                ", display=" + getDisplayText() +
                ", nbtMode=" + getNbtMode() +
                (ignoreNbtKeys.isEmpty() ? "" : ", ignoreKeys=" + ignoreNbtKeys) +
                '}';
    }
}
