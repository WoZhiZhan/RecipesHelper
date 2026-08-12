package com.wzz.registerhelper.ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wzz.registerhelper.init.ModRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Component-aware partial ingredient used for ignored custom-data paths.
 *
 * <p>Non-custom components are compared exactly. The value of
 * {@code minecraft:custom_data} is compared as an NBT subset after the
 * configured paths are removed. The legacy {@code nbt} field is retained as
 * an input format and is mapped to CUSTOM_DATA, rather than to a synthetic
 * legacy item tag.
 */
public class PartialNbtIngredient implements ICustomIngredient {
    private final Item item;
    private final DataComponentPredicate requiredPredicate;
    private final DataComponentPatch requiredComponents;
    private final DataComponentMap expectedComponents;
    private final List<String> ignoreKeys;
    private final int count;

    /** Compatibility constructor for existing callers and old config files. */
    public PartialNbtIngredient(Item item, @Nullable CompoundTag requiredNbt, List<String> ignoreKeys) {
        this(item, predicateForCustomData(requiredNbt), ignoreKeys, 1);
    }

    public PartialNbtIngredient(Item item,
                                DataComponentPredicate requiredPredicate,
                                List<String> ignoreKeys) {
        this(item, requiredPredicate, ignoreKeys, 1);
    }

    public PartialNbtIngredient(Item item,
                                DataComponentPredicate requiredPredicate,
                                List<String> ignoreKeys,
                                int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Partial ingredient count must be positive");
        }
        this.item = item;
        this.requiredPredicate = requiredPredicate;
        this.requiredComponents = requiredPredicate.asPatch();
        ItemStack expectedStack = new ItemStack(item);
        expectedStack.applyComponents(requiredComponents);
        this.expectedComponents = expectedStack.getComponents();
        this.ignoreKeys = List.copyOf(ignoreKeys);
        this.count = count;
    }

    public static PartialNbtIngredient of(Item item, @Nullable CompoundTag requiredNbt, List<String> ignoreKeys) {
        return new PartialNbtIngredient(item, requiredNbt, ignoreKeys);
    }

    public Item getItem() {
        return item;
    }

    @Nullable
    public CompoundTag getRequiredNbt() {
        Optional<? extends CustomData> customData = requiredComponents.get(DataComponents.CUSTOM_DATA);
        return customData == null || customData.isEmpty() ? null : customData.get().copyTag();
    }

    public DataComponentPredicate getRequiredPredicate() {
        return requiredPredicate;
    }

    public List<String> getIgnoreKeys() {
        return ignoreKeys;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean test(@Nullable ItemStack input) {
        if (input == null || input.isEmpty() || !input.is(item) || input.getCount() < count) {
            return false;
        }

        DataComponentMap actual = input.getComponents();
        for (DataComponentType<?> type : expectedComponents.keySet()) {
            if (isIgnoredComponent(type)) {
                continue;
            }

            Object expectedValue = expectedComponents.get(type);
            Object actualValue = actual.get(type);
            if (type == DataComponents.CUSTOM_DATA) {
                if (!matchesCustomData((CustomData) expectedValue, actualValue instanceof CustomData custom ? custom : null)) {
                    return false;
                }
            } else if (!Objects.equals(expectedValue, actualValue)) {
                return false;
            }
        }

        for (DataComponentType<?> type : actual.keySet()) {
            if (!isIgnoredComponent(type) && !expectedComponents.has(type)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCustomData(@Nullable CustomData expected, @Nullable CustomData actual) {
        if (ignoresWholeCustomData()) {
            return true;
        }
        if (expected == null || actual == null) {
            return expected == null;
        }

        CompoundTag expectedTag = expected.copyTag();
        CompoundTag actualTag = actual.copyTag();
        for (String path : ignoreKeys) {
            removeCustomDataPath(expectedTag, path);
            removeCustomDataPath(actualTag, path);
        }
        return NbtUtils.compareNbt(expectedTag, actualTag, true);
    }

    private boolean ignoresWholeCustomData() {
        return ignoreKeys.stream().anyMatch(key ->
                key.equals("custom_data")
                        || key.equals("minecraft:custom_data"));
    }

    private boolean isIgnoredComponent(DataComponentType<?> type) {
        for (String key : ignoreKeys) {
            if ((key.equals("custom_data") || key.equals("minecraft:custom_data"))
                    && type == DataComponents.CUSTOM_DATA
                    || key.equals("Damage") && type == DataComponents.DAMAGE
                    || key.equals("RepairCost") && type == DataComponents.REPAIR_COST
                    || key.equals("CustomModelData") && type == DataComponents.CUSTOM_MODEL_DATA
                    || key.equals("Unbreakable") && type == DataComponents.UNBREAKABLE
                    || key.equals("Enchantments") && type == DataComponents.ENCHANTMENTS
                    || key.equals("StoredEnchantments") && type == DataComponents.STORED_ENCHANTMENTS
                    || key.equals("display") && (type == DataComponents.CUSTOM_NAME
                    || type == DataComponents.LORE || type == DataComponents.DYED_COLOR)
                    || key.equals("display.Name") && type == DataComponents.CUSTOM_NAME
                    || key.equals("display.Lore") && type == DataComponents.LORE
                    || key.equals("display.color") && type == DataComponents.DYED_COLOR
                    || key.equals("minecraft:damage") && type == DataComponents.DAMAGE
                    || key.equals("minecraft:custom_model_data") && type == DataComponents.CUSTOM_MODEL_DATA) {
                return true;
            }
        }
        return false;
    }

    private static void removeCustomDataPath(CompoundTag tag, String path) {
        if (path.equals("custom_data") || path.equals("minecraft:custom_data")) {
            new ArrayList<>(tag.getAllKeys()).forEach(tag::remove);
            return;
        }
        if (path.startsWith("custom_data.")) {
            path = path.substring("custom_data.".length());
        } else if (path.startsWith("minecraft:custom_data.")) {
            path = path.substring("minecraft:custom_data.".length());
        }
        if (!path.startsWith("components.")) {
            removeByPath(tag, path);
        }
    }

    private static void removeByPath(CompoundTag tag, String path) {
        if (path.isEmpty()) {
            return;
        }
        int dot = path.indexOf('.');
        if (dot < 0) {
            tag.remove(path);
            return;
        }

        String head = path.substring(0, dot);
        String tail = path.substring(dot + 1);
        if (tag.get(head) instanceof CompoundTag nested) {
            CompoundTag nestedCopy = nested.copy();
            removeByPath(nestedCopy, tail);
            tag.put(head, nestedCopy);
        }
    }

    private static DataComponentPredicate predicateForCustomData(@Nullable CompoundTag requiredNbt) {
        DataComponentMap.Builder map = DataComponentMap.builder();
        if (requiredNbt != null && !requiredNbt.isEmpty()) {
            map.set(DataComponents.CUSTOM_DATA, CustomData.of(requiredNbt));
        }
        return DataComponentPredicate.allOf(map.build());
    }

    private static DataComponentPredicate predicateFromPatch(DataComponentPatch patch) {
        DataComponentMap.Builder map = DataComponentMap.builder();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            if (entry.getValue().isPresent()) {
                setUnchecked(map, entry.getKey(), entry.getValue().get());
            }
        }
        return DataComponentPredicate.allOf(map.build());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setUnchecked(DataComponentMap.Builder map,
                                     DataComponentType<?> type,
                                     Object value) {
        map.set((DataComponentType) type, value);
    }

    @Override
    public Stream<ItemStack> getItems() {
        ItemStack display = new ItemStack(item, count);
        display.applyComponents(requiredComponents);
        return Stream.of(display);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return ModRegistries.PARTIAL_NBT_INGREDIENT_TYPE.get();
    }

    public static final MapCodec<PartialNbtIngredient> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(PartialNbtIngredient::getItem),
            DataComponentPredicate.CODEC.optionalFieldOf("components", DataComponentPredicate.EMPTY)
                    .forGetter(PartialNbtIngredient::getRequiredPredicate),
            Codec.STRING.optionalFieldOf("nbt").forGetter(ingredient -> {
                CompoundTag tag = ingredient.getRequiredNbt();
                return tag == null || tag.isEmpty() ? Optional.empty() : Optional.of(tag.toString());
            }),
            Codec.STRING.listOf().optionalFieldOf("ignore_keys", List.of())
                    .forGetter(PartialNbtIngredient::getIgnoreKeys),
            Codec.INT.optionalFieldOf("count", 1).forGetter(PartialNbtIngredient::getCount)
    ).apply(inst, (item, predicate, nbtString, ignoreKeys, count) -> {
        DataComponentPatch patch = predicate.asPatch();
        if (nbtString.isPresent()) {
            CompoundTag legacyNbt;
            try {
                legacyNbt = TagParser.parseTag(nbtString.get());
            } catch (Exception e) {
                throw new IllegalArgumentException("[RegisterHelper] PartialNbtIngredient NBT parse failed", e);
            }
            DataComponentMap.Builder map = DataComponentMap.builder();
            map.set(DataComponents.CUSTOM_DATA, CustomData.of(legacyNbt));
            for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
                if (entry.getKey() != DataComponents.CUSTOM_DATA && entry.getValue().isPresent()) {
                    setUnchecked(map, entry.getKey(), entry.getValue().get());
                }
            }
            patch = DataComponentPredicate.allOf(map.build()).asPatch();
        }
        return new PartialNbtIngredient(item, predicateFromPatch(patch), new ArrayList<>(ignoreKeys), count);
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartialNbtIngredient> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PartialNbtIngredient decode(RegistryFriendlyByteBuf buffer) {
                    String itemId = ByteBufCodecs.STRING_UTF8.decode(buffer);
                    Item item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(itemId));
                    DataComponentPatch components = DataComponentPatch.STREAM_CODEC.decode(buffer);
                    int size = ByteBufCodecs.VAR_INT.decode(buffer);
                    List<String> ignoreKeys = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        ignoreKeys.add(ByteBufCodecs.STRING_UTF8.decode(buffer));
                    }
                    int count = ByteBufCodecs.VAR_INT.decode(buffer);
                    return new PartialNbtIngredient(item, predicateFromPatch(components), ignoreKeys, count);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, PartialNbtIngredient ingredient) {
                    ByteBufCodecs.STRING_UTF8.encode(buffer,
                            BuiltInRegistries.ITEM.getKey(ingredient.item).toString());
                    DataComponentPatch.STREAM_CODEC.encode(buffer, ingredient.requiredComponents);
                    ByteBufCodecs.VAR_INT.encode(buffer, ingredient.ignoreKeys.size());
                    for (String key : ingredient.ignoreKeys) {
                        ByteBufCodecs.STRING_UTF8.encode(buffer, key);
                    }
                    ByteBufCodecs.VAR_INT.encode(buffer, ingredient.count);
                }
            };
}
