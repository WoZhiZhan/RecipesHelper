package com.wzz.registerhelper.ingredient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.RecipeHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 支持忽略指定 NBT key 的自定义 Ingredient。
 *
 * <p>匹配逻辑（子集匹配）：
 * <ol>
 *   <li>物品类型必须一致。</li>
 *   <li>将待检测物品的 NBT 复制一份，移除 {@code ignore_keys} 中的所有 key。</li>
 *   <li>检查配方要求的 NBT 是 stripped 之后物品 NBT 的子集
 *       （即物品必须包含配方要求的所有 key=value，多余的 key 无所谓）。</li>
 * </ol>
 *
 * <p>JSON 格式：
 * <pre>{@code
 * {
 *   "type": "registerhelper:partial_nbt",
 *   "item": "slashblade:slashblade",
 *   "nbt": "{quality:5,type:\"xxx\"}",   // 必须匹配的最小 NBT 子集（可选，不写则只匹配物品类型）
 *   "ignore_keys": ["lastUsed", "AttackCount"]  // 匹配时从物品 NBT 中忽略掉的 key
 * }
 * }</pre>
 */
public class PartialNbtIngredient extends AbstractIngredient {

    public static final ResourceLocation ID = new ResourceLocation(RecipeHelper.MODID, "partial_nbt");
    public static final Serializer SERIALIZER = new Serializer();

    /** 要求的物品类型 */
    private final Item item;
    /** 配方要求的最小 NBT 子集，null 表示不限 */
    @Nullable
    private final CompoundTag requiredNbt;
    /** 匹配时从物品实际 NBT 中移除的 key 列表（如 lastUsed、AttackCount 等动态 key） */
    private final List<String> ignoreKeys;

    private PartialNbtIngredient(Item item, @Nullable CompoundTag requiredNbt, List<String> ignoreKeys) {
        super(Stream.of(buildDisplayStack(item, requiredNbt)).map(Ingredient.ItemValue::new));
        this.item = item;
        this.requiredNbt = requiredNbt;
        this.ignoreKeys = ignoreKeys;
    }

    /**
     * 创建实例。
     *
     * @param item        要求的物品类型
     * @param requiredNbt 必须包含的最小 NBT 子集（传 null 则只匹配物品类型）
     * @param ignoreKeys  匹配时需要从物品 NBT 中忽略的 key
     */
    public static PartialNbtIngredient of(Item item, @Nullable CompoundTag requiredNbt, List<String> ignoreKeys) {
        return new PartialNbtIngredient(item, requiredNbt, ignoreKeys);
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null || input.isEmpty()) return false;
        if (!input.is(item)) return false;
        if (requiredNbt == null || requiredNbt.isEmpty()) return true;

        CompoundTag inputTag = input.getTag();
        if (inputTag == null) return false;

        CompoundTag strippedInput    = inputTag.copy();
        CompoundTag strippedRequired = requiredNbt.copy();
        for (String path : ignoreKeys) {
            removeByPath(strippedInput,    path);
            removeByPath(strippedRequired, path);
        }
        return nbtContains(strippedInput, strippedRequired);
    }

    /**
     * 按点路径移除 NBT key，支持：
     *   "lastActionTime"              → 移除顶层 key
     *   "bladeState.lastActionTime"   → 移除 bladeState 下的 key（任意深度）
     */
    private static void removeByPath(CompoundTag tag, String path) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            // 顶层 key
            tag.remove(path);
        } else {
            String head = path.substring(0, dot);
            String tail = path.substring(dot + 1);
            if (tag.get(head) instanceof CompoundTag nested) {
                // 修改的是 nested 的副本的引用，需要替换回去
                CompoundTag nestedCopy = nested.copy();
                removeByPath(nestedCopy, tail);
                tag.put(head, nestedCopy);
            }
            // 若 head 不是 CompoundTag，忽略（路径不存在则无操作）
        }
    }

    /**
     * 检查 {@code container} 是否包含 {@code subset} 的所有条目（深度子集）。
     */
    private static boolean nbtContains(CompoundTag container, CompoundTag subset) {
        for (String key : subset.getAllKeys()) {
            if (!container.contains(key)) return false;
            // 递归处理嵌套 CompoundTag
            if (subset.get(key) instanceof CompoundTag subNested
                    && container.get(key) instanceof CompoundTag conNested) {
                if (!nbtContains(conNested, subNested)) return false;
            } else {
                if (!container.get(key).equals(subset.get(key))) return false;
            }
        }
        return true;
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        json.addProperty("item", ForgeRegistries.ITEMS.getKey(item).toString());
        if (requiredNbt != null && !requiredNbt.isEmpty()) {
            json.addProperty("nbt", requiredNbt.toString());
        }
        if (!ignoreKeys.isEmpty()) {
            JsonArray arr = new JsonArray();
            ignoreKeys.forEach(arr::add);
            json.add("ignore_keys", arr);
        }
        return json;
    }

    private static ItemStack buildDisplayStack(Item item, @Nullable CompoundTag nbt) {
        ItemStack stack = new ItemStack(item);
        if (nbt != null) stack.setTag(nbt.copy());
        return stack;
    }

    public static final class Serializer implements IIngredientSerializer<PartialNbtIngredient> {

        @Override
        public PartialNbtIngredient parse(JsonObject json) {
            // 解析 item
            String itemId = json.get("item").getAsString();
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item == null) {
                throw new IllegalArgumentException("[RegisterHelper] PartialNbtIngredient: 找不到物品 " + itemId);
            }
            // 解析可选的 nbt
            CompoundTag requiredNbt = null;
            if (json.has("nbt")) {
                try {
                    requiredNbt = TagParser.parseTag(json.get("nbt").getAsString());
                } catch (Exception e) {
                    throw new IllegalArgumentException("[RegisterHelper] PartialNbtIngredient: NBT 解析失败 - " + e.getMessage());
                }
            }
            // 解析 ignore_keys
            List<String> ignoreKeys = new ArrayList<>();
            if (json.has("ignore_keys")) {
                JsonArray arr = json.getAsJsonArray("ignore_keys");
                arr.forEach(el -> ignoreKeys.add(el.getAsString()));
            }
            return new PartialNbtIngredient(item, requiredNbt, ignoreKeys);
        }

        @Override
        public PartialNbtIngredient parse(FriendlyByteBuf buffer) {
            String itemId = buffer.readUtf();
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            boolean hasNbt = buffer.readBoolean();
            CompoundTag nbt = hasNbt ? buffer.readNbt() : null;
            int size = buffer.readVarInt();
            List<String> ignoreKeys = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ignoreKeys.add(buffer.readUtf());
            }
            return new PartialNbtIngredient(item, nbt, ignoreKeys);
        }

        @Override
        public void write(FriendlyByteBuf buffer, PartialNbtIngredient ingredient) {
            buffer.writeUtf(ForgeRegistries.ITEMS.getKey(ingredient.item).toString());
            buffer.writeBoolean(ingredient.requiredNbt != null);
            if (ingredient.requiredNbt != null) buffer.writeNbt(ingredient.requiredNbt);
            buffer.writeVarInt(ingredient.ignoreKeys.size());
            ingredient.ignoreKeys.forEach(buffer::writeUtf);
        }
    }
}
