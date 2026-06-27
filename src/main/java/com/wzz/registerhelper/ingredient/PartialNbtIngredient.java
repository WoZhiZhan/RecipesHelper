package com.wzz.registerhelper.ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wzz.registerhelper.init.ModRegistries;
import com.wzz.registerhelper.util.OldUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 支持忽略指定 NBT key 的自定义 Ingredient。
 *
 * <p>NeoForge 1.21.1 版本：基于 ICustomIngredient + IngredientType。
 *
 * <p>匹配逻辑（子集匹配）：
 * <ol>
 *   <li>物品类型必须一致。</li>
 *   <li>将待检测物品的 NBT（通过 OldUtils 兼容层从 DataComponents 还原）复制一份，移除 {@code ignore_keys} 中的所有 key。</li>
 *   <li>检查配方要求的 NBT 是 stripped 之后物品 NBT 的子集。</li>
 * </ol>
 *
 * <p>JSON 格式：
 * <pre>{@code
 * {
 *   "type": "registerhelper:partial_nbt",
 *   "item": "slashblade:slashblade",
 *   "nbt": "{quality:5,type:\"xxx\"}",
 *   "ignore_keys": ["lastUsed", "AttackCount"]
 * }
 * }</pre>
 */
public class PartialNbtIngredient implements ICustomIngredient {

    /** 要求的物品类型 */
    private final Item item;
    /** 配方要求的最小 NBT 子集，null 表示不限 */
    @Nullable
    private final CompoundTag requiredNbt;
    /** 匹配时从物品实际 NBT 中移除的 key 列表 */
    private final List<String> ignoreKeys;

    public PartialNbtIngredient(Item item, @Nullable CompoundTag requiredNbt, List<String> ignoreKeys) {
        this.item = item;
        this.requiredNbt = requiredNbt;
        this.ignoreKeys = ignoreKeys;
    }

    /**
     * 创建实例。
     */
    public static PartialNbtIngredient of(Item item, @Nullable CompoundTag requiredNbt, List<String> ignoreKeys) {
        return new PartialNbtIngredient(item, requiredNbt, ignoreKeys);
    }

    public Item getItem() {
        return item;
    }

    @Nullable
    public CompoundTag getRequiredNbt() {
        return requiredNbt;
    }

    public List<String> getIgnoreKeys() {
        return ignoreKeys;
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null || input.isEmpty()) return false;
        if (!input.is(item)) return false;
        if (requiredNbt == null || requiredNbt.isEmpty()) return true;

        // 通过兼容层从 DataComponents 还原出 1.20 风格的 NBT
        CompoundTag inputTag = OldUtils.getTag(input);
        if (inputTag == null) return false;

        CompoundTag strippedInput = inputTag.copy();
        CompoundTag strippedRequired = requiredNbt.copy();
        for (String path : ignoreKeys) {
            removeByPath(strippedInput, path);
            removeByPath(strippedRequired, path);
        }
        return nbtContains(strippedInput, strippedRequired);
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(item.getDefaultInstance());
    }

    /**
     * 按点路径移除 NBT key。
     */
    private static void removeByPath(CompoundTag tag, String path) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            tag.remove(path);
        } else {
            String head = path.substring(0, dot);
            String tail = path.substring(dot + 1);
            if (tag.get(head) instanceof CompoundTag nested) {
                CompoundTag nestedCopy = nested.copy();
                removeByPath(nestedCopy, tail);
                tag.put(head, nestedCopy);
            }
        }
    }

    /**
     * 检查 {@code container} 是否包含 {@code subset} 的所有条目（深度子集）。
     */
    private static boolean nbtContains(CompoundTag container, CompoundTag subset) {
        for (String key : subset.getAllKeys()) {
            if (!container.contains(key)) return false;
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
        // 需要检查 NBT/数据组件，因此不是 simple
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return ModRegistries.PARTIAL_NBT_INGREDIENT_TYPE.get();
    }

    // ==================== 序列化 ====================

    public static final MapCodec<PartialNbtIngredient> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(PartialNbtIngredient::getItem),
            Codec.STRING.optionalFieldOf("nbt").forGetter(i -> {
                CompoundTag tag = i.getRequiredNbt();
                return tag == null || tag.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(tag.toString());
            }),
            Codec.STRING.listOf().optionalFieldOf("ignore_keys", List.of()).forGetter(PartialNbtIngredient::getIgnoreKeys)
    ).apply(inst, (item, nbtStr, ignoreKeys) -> {
        CompoundTag nbt = null;
        if (nbtStr.isPresent()) {
            try {
                nbt = TagParser.parseTag(nbtStr.get());
            } catch (Exception e) {
                throw new IllegalArgumentException("[RegisterHelper] PartialNbtIngredient: NBT 解析失败 - " + e.getMessage());
            }
        }
        return new PartialNbtIngredient(item, nbt, new ArrayList<>(ignoreKeys));
    }));

    public static final StreamCodec<ByteBuf, PartialNbtIngredient> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PartialNbtIngredient decode(ByteBuf buf) {
                    String itemId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    Item item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(itemId));
                    boolean hasNbt = ByteBufCodecs.BOOL.decode(buf);
                    CompoundTag nbt = null;
                    if (hasNbt) {
                        nbt = ByteBufCodecs.COMPOUND_TAG.decode(buf);
                    }
                    int size = ByteBufCodecs.VAR_INT.decode(buf);
                    List<String> ignoreKeys = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        ignoreKeys.add(ByteBufCodecs.STRING_UTF8.decode(buf));
                    }
                    return new PartialNbtIngredient(item, nbt, ignoreKeys);
                }

                @Override
                public void encode(ByteBuf buf, PartialNbtIngredient ingredient) {
                    ByteBufCodecs.STRING_UTF8.encode(buf,
                            BuiltInRegistries.ITEM.getKey(ingredient.item).toString());
                    boolean hasNbt = ingredient.requiredNbt != null && !ingredient.requiredNbt.isEmpty();
                    ByteBufCodecs.BOOL.encode(buf, hasNbt);
                    if (hasNbt) {
                        ByteBufCodecs.COMPOUND_TAG.encode(buf, ingredient.requiredNbt);
                    }
                    ByteBufCodecs.VAR_INT.encode(buf, ingredient.ignoreKeys.size());
                    for (String key : ignoreKeys(ingredient)) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, key);
                    }
                }

                private List<String> ignoreKeys(PartialNbtIngredient ing) {
                    return ing.ignoreKeys;
                }
            };
}
