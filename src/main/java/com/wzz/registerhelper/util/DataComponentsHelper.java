package com.wzz.registerhelper.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.jetbrains.annotations.Nullable;

/** Helpers for the 1.21.1 item-stack and ingredient codecs. */
public final class DataComponentsHelper {
    private DataComponentsHelper() {
    }

    /**
     * Returns true when the stack has an explicit component patch. This is the
     * component equivalent of the old ItemStack.hasTag() check and includes
     * component types added by other mods.
     */
    public static boolean hasAnyComponents(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !stack.isComponentsPatchEmpty();
    }

    /**
     * Encodes the stack's explicit component patch. This is useful to callers
     * that need the component object itself, while recipe results should use
     * {@link #createResultWithComponents(ItemStack)} instead.
     */
    public static JsonObject toComponentsJson(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new JsonObject();
        }

        JsonElement encoded = encode(ItemStack.CODEC, stack);
        if (encoded != null && encoded.isJsonObject()) {
            JsonElement components = encoded.getAsJsonObject().get("components");
            if (components != null && components.isJsonObject()) {
                return components.getAsJsonObject().deepCopy();
            }
        }
        return new JsonObject();
    }

    /**
     * Encodes all effective components as a DataComponentPredicate. A strict
     * ingredient needs this rather than the stack's patch: the predicate is
     * compared against the target item's effective component map.
     */
    public static JsonObject toComponentPredicateJson(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new JsonObject();
        }

        JsonElement encoded = encode(
                DataComponentPredicate.CODEC,
                DataComponentPredicate.allOf(stack.getComponents()));
        return encoded != null && encoded.isJsonObject()
                ? encoded.getAsJsonObject().deepCopy()
                : new JsonObject();
    }

    /** Creates a valid NeoForge component ingredient with strict matching. */
    public static JsonObject createIngredientWithComponents(ItemStack stack) {
        JsonObject fallback = createPlainIngredient(stack);
        if (stack == null || stack.isEmpty() || !hasAnyComponents(stack)) {
            return fallback;
        }

        JsonElement encoded = encode(Ingredient.CODEC, DataComponentIngredient.of(true, stack));
        if (encoded == null || !encoded.isJsonObject()) {
            return fallback;
        }

        JsonObject ingredient = encoded.getAsJsonObject().deepCopy();
        if (stack.getCount() > 1) {
            ingredient.addProperty("count", stack.getCount());
        }
        return ingredient;
    }

    /**
     * Creates the registered partial ingredient format. The component
     * predicate keeps every non-ignored component exact; PartialNbtIngredient
     * applies subset semantics only to CUSTOM_DATA.
     */
    public static JsonObject createPartialIngredientWithComponents(ItemStack stack, java.util.List<String> ignoreKeys) {
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("type", "registerhelper:partial_nbt");
        ingredient.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());

        JsonObject predicate = toComponentPredicateJson(stack);
        if (predicate.size() > 0) {
            ingredient.add("components", predicate);
        } else {
            CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null && !customData.isEmpty()) {
                ingredient.addProperty("nbt", customData.copyTag().toString());
            }
        }

        if (ignoreKeys != null && !ignoreKeys.isEmpty()) {
            com.google.gson.JsonArray ignored = new com.google.gson.JsonArray();
            ignoreKeys.forEach(ignored::add);
            ingredient.add("ignore_keys", ignored);
        }
        if (stack.getCount() > 1) {
            ingredient.addProperty("count", stack.getCount());
        }
        return ingredient;
    }

    /** Creates a 1.21.1 result object, preserving every codec-supported component. */
    public static JsonObject createResultWithComponents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new JsonObject();
        }

        JsonElement encoded = encode(ItemStack.CODEC, stack);
        if (encoded != null && encoded.isJsonObject()) {
            return encoded.getAsJsonObject().deepCopy();
        }

        JsonObject fallback = new JsonObject();
        fallback.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (stack.getCount() > 1) {
            fallback.addProperty("count", stack.getCount());
        }
        return fallback;
    }

    /**
     * Parses a standard 1.21.1 item stack object. The legacy "item" key is
     * accepted as an input convenience; output is always written with "id".
     */
    public static ItemStack parseItemStack(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return ItemStack.EMPTY;
        }

        JsonObject object = element.getAsJsonObject().deepCopy();
        if (!object.has("id") && object.has("item")) {
            object.add("id", object.remove("item"));
        } else {
            object.remove("item");
        }

        HolderLookup.Provider registries = OldUtils.getRegistryAccess();
        DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, object).result().orElse(ItemStack.EMPTY);
    }

    /** Parses a legacy SNBT value for custom-data based config files. */
    @Nullable
    public static CompoundTag parseSnbt(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return TagParser.parseTag(element.isJsonPrimitive()
                    ? element.getAsString()
                    : element.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JsonObject createPlainIngredient(ItemStack stack) {
        JsonObject ingredient = new JsonObject();
        if (stack == null || stack.isEmpty()) {
            return ingredient;
        }
        ingredient.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (stack.getCount() > 1) {
            ingredient.addProperty("count", stack.getCount());
        }
        return ingredient;
    }

    @Nullable
    private static <T> JsonElement encode(Codec<T> codec, T value) {
        try {
            HolderLookup.Provider registries = OldUtils.getRegistryAccess();
            DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
            return codec.encodeStart(ops, value).resultOrPartial().orElse(null);
        } catch (RuntimeException ignored) {
            // A client without a server-side dynamic registry can not encode
            // some holder-backed components. Keep the generated JSON valid.
            return null;
        }
    }
}
