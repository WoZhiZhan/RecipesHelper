package com.wzz.registerhelper.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.gui.recipe.IngredientData;
import com.wzz.registerhelper.recipe.RecipeRequest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方 JSON 生成工具。
 *
 * Recipe JSON generation for NeoForge 1.21.1.
 * Exact component ingredients are emitted through NeoForge's registered
 * neoforge:components ingredient. Partial matching remains this mod's
 * registered ingredient and is limited to CUSTOM_DATA subset matching.
 */
public class RecipeUtil {
    // 符号字符表，优先级：大写字母 → 小写字母 → 特殊符号（共 78 个）
    public static final String SYMBOL_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +           // 大写字母 (26个)
                    "abcdefghijklmnopqrstuvwxyz" +   // 小写字母 (26个)
                    "!@#$%^&*()_{}[];:'/.,`";        // 特殊符号 (26个，总共78个)

    public static JsonObject createShapedTableRecipe(String type, RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        Integer tier = (Integer) request.properties.get("tier");
        if (tier != null) {
            recipe.addProperty("tier", tier);
        }

        if (request.pattern != null) {
            JsonArray patternArray = new JsonArray();
            for (String row : request.pattern) {
                patternArray.add(row);
            }
            recipe.add("pattern", patternArray);
        }

        if (request.ingredients != null) {
            JsonObject key = new JsonObject();

            // request.ingredients 格式为 [符号0, 物品0, 符号1, 物品1, ...]
            // 必须使用原始符号作为 key，不能重新分配，否则与 pattern 中的字母对不上。
            boolean hasPairs = request.ingredients.length >= 2
                    && request.ingredients[0] instanceof Character;

            if (hasPairs) {
                // 新格式：成对迭代，保留原始符号
                for (int i = 0; i + 1 < request.ingredients.length; i += 2) {
                    if (request.ingredients[i] instanceof Character symbol) {
                        JsonObject ingredientJson = createIngredientJson(
                                request.ingredients[i + 1],
                                (Boolean) request.properties.getOrDefault("includeNBT", true));
                        if (ingredientJson != null) {
                            key.add(String.valueOf(symbol), ingredientJson);
                        }
                    }
                }
            } else {
                // 旧格式兼容：纯物品数组，重新分配符号
                Map<Character, JsonObject> keyMapping = new HashMap<>();
                Map<String, Character> ingredientToChar = new HashMap<>();
                int charIndex = 0;
                for (Object ingredient : request.ingredients) {
                    if (ingredient == null) continue;
                    if (ingredient instanceof ItemStack stack && stack.isEmpty()) continue;
                    JsonObject ingredientJson = createIngredientJson(ingredient,
                            (Boolean) request.properties.getOrDefault("includeNBT", true));
                    if (ingredientJson != null) {
                        String ingredientKey = ingredientJson.toString();
                        if (!ingredientToChar.containsKey(ingredientKey)) {
                            if (charIndex >= SYMBOL_CHARS.length()) {
                                throw new IllegalArgumentException(
                                        String.format("合成表原料过多，超过%d个符号限制", SYMBOL_CHARS.length()));
                            }
                            char symbol = SYMBOL_CHARS.charAt(charIndex);
                            ingredientToChar.put(ingredientKey, symbol);
                            keyMapping.put(symbol, ingredientJson);
                            charIndex++;
                        }
                    }
                }
                for (Map.Entry<Character, JsonObject> entry : keyMapping.entrySet()) {
                    key.add(String.valueOf(entry.getKey()), entry.getValue());
                }
            }

            recipe.add("key", key);
        }

        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    public static JsonObject createShapelessTableRecipe(String type, RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        Integer tier = (Integer) request.properties.get("tier");
        if (tier != null) {
            recipe.addProperty("tier", tier);
        }

        if (request.ingredients != null) {
            JsonArray ingredientsArray = new JsonArray();
            for (Object ingredient : request.ingredients) {
                JsonObject ingredientJson = createIngredientJson(ingredient,
                        (Boolean) request.properties.getOrDefault("includeNBT", true));
                if (ingredientJson != null) {
                    ingredientsArray.add(ingredientJson);
                }
            }
            recipe.add("ingredients", ingredientsArray);
        }

        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    public static JsonObject createSmeltingRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 200);
    }

    public static JsonObject createBlastingRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 100);
    }

    public static JsonObject createSmokingRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 100);
    }

    public static JsonObject createCampfireRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 600);
    }

    private static JsonObject createCookingRecipe(String type, RecipeRequest request, int defaultCookingTime) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject ingredientJson = createIngredientJson(request.ingredients[0]);
            if (ingredientJson != null) {
                recipe.add("ingredient", ingredientJson);
            }
        }

        if (request.result != null) {
            recipe.add("result", createResultJson(request.result, request.resultCount));
        }

        Number experienceValue = (Number) request.properties.get("experience");
        float experience = experienceValue != null ? experienceValue.floatValue() : 0.1f;
        recipe.addProperty("experience", experience);

        Number cookingTimeValue = (Number) request.properties.get("cookingtime");
        if (cookingTimeValue == null) {
            cookingTimeValue = (Number) request.properties.get("cookingTime");
        }
        int cookingTime = cookingTimeValue != null ? cookingTimeValue.intValue() : defaultCookingTime;
        recipe.addProperty("cookingtime", cookingTime);

        return recipe;
    }

    public static JsonObject createMultiOutputRecipe(String type, RecipeRequest request, String resultFieldName) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        if (request.ingredients != null) {
            JsonArray ingredientsArray = new JsonArray();
            for (Object ingredient : request.ingredients) {
                JsonObject ingredientJson = createIngredientJson(ingredient);
                if (ingredientJson != null) {
                    ingredientsArray.add(ingredientJson);
                }
            }
            recipe.add("ingredients", ingredientsArray);
        }

        JsonArray resultArray = createMultipleResults(request);
        recipe.add(resultFieldName, resultArray);

        return recipe;
    }

    public static JsonArray createMultipleResults(RecipeRequest request) {
        JsonArray resultArray = new JsonArray();

        if (request.result != null) {
            JsonObject mainResult = createResultJson(request.result, request.resultCount);
            resultArray.add(mainResult);
        }

        Object extraResults = request.properties.get("extraResults");
        if (extraResults instanceof ItemStack[] extraStacks) {
            for (ItemStack stack : extraStacks) {
                if (!stack.isEmpty()) {
                    JsonObject extraResult = createResultJson(stack, stack.getCount());

                    Number chance = (Number) request.properties.get("chance_" + stack.getItem());
                    if (chance != null && chance.floatValue() < 1.0f) {
                        extraResult.addProperty("chance", chance.floatValue());
                    }

                    resultArray.add(extraResult);
                }
            }
        }

        return resultArray;
    }

    // ======================================================================
    // createIngredientJson 重载族
    // ======================================================================

    /**
     * Creates an ingredient with component matching enabled.
     */
    public static JsonObject createIngredientJson(Object ingredient) {
        return createIngredientJson(ingredient, true, List.of());
    }

    /**
     * Creates an ingredient while controlling component matching.
     */
    public static JsonObject createIngredientJson(Object ingredient, boolean includeNBT) {
        return createIngredientJson(ingredient, includeNBT, List.of());
    }

    /**
     * Creates an ingredient from GUI data, including its ignore-key policy.
     */
    public static JsonObject createIngredientJson(IngredientData data) {
        if (data == null || data.isEmpty()) return null;

        return switch (data.getType()) {
            case TAG, CUSTOM_TAG -> {
                JsonObject json = new JsonObject();
                json.addProperty("tag", data.getTagId().toString());
                yield json;
            }
            case ITEM -> createIngredientJson(
                    data.getItemStack(),
                    data.isIncludeNBT(),
                    data.getIgnoreNbtKeys()
            );
        };
    }

    /**
     * Core item/tag/map implementation.
     *
     * @param ingredient ItemStack, Item, String, Map, or IngredientData
     * @param includeNBT whether component matching is enabled
     * @param ignoreKeys custom-data paths to ignore for partial matching
     */
    @SuppressWarnings("unchecked")
    public static JsonObject createIngredientJson(Object ingredient, boolean includeNBT, List<String> ignoreKeys) {
        if (ingredient instanceof IngredientData data) {
            return createIngredientJson(data); // 走专用重载
        }

        if (ingredient instanceof ItemStack stack) {
            boolean hasIgnore = ignoreKeys != null && !ignoreKeys.isEmpty();

            if (includeNBT && hasIgnore && OldUtils.hasTag(stack)) {
                return DataComponentsHelper.createPartialIngredientWithComponents(stack, ignoreKeys);
            }

            if (includeNBT) {
                return DataComponentsHelper.createIngredientWithComponents(stack);
            }

            JsonObject ingredientJson = new JsonObject();
            ingredientJson.addProperty("item", getItemResourceLocation(stack.getItem()).toString());
            if (stack.getCount() > 1) {
                ingredientJson.addProperty("count", stack.getCount());
            }
            return ingredientJson;

        } else if (ingredient instanceof Item item) {
            JsonObject ingredientJson = new JsonObject();
            ingredientJson.addProperty("item", getItemResourceLocation(item).toString());
            return ingredientJson;

        } else if (ingredient instanceof String str) {
            JsonObject ingredientJson = new JsonObject();
            if (str.startsWith("#")) {
                ingredientJson.addProperty("tag", str.substring(1));
            } else {
                ingredientJson.addProperty("item", str);
            }
            return ingredientJson;

        } else if (ingredient instanceof Map map) {
            JsonObject ingredientJson = new JsonObject();

            if (map.containsKey("fluid")) {
                String fluidId = (String) map.get("fluid");
                int amount = ((Number) map.getOrDefault("amount", 250)).intValue();
                ingredientJson.addProperty("fluid", fluidId);
                ingredientJson.addProperty("amount", amount);
                if (includeNBT && map.containsKey("nbt") && map.get("nbt") instanceof JsonElement json) {
                    ingredientJson.add("nbt", json);
                }
                return ingredientJson;
            }

            String id = null;
            if (map.containsKey("id")) {
                id = (String) map.get("id");
            } else if (map.containsKey("item")) {
                id = (String) map.get("item");
            } else if (map.containsKey("block")) {
                id = (String) map.get("block");
            }

            if (id != null) {
                int count = map.containsKey("count")
                        ? ((Number) map.get("count")).intValue() : 1;
                if (includeNBT && map.containsKey("nbt")) {
                    CompoundTag nbt = parseMapNbt(map.get("nbt"));
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
                    if (nbt != null && item != Items.AIR) {
                        ItemStack stack = new ItemStack(item, Math.max(1, count));
                        OldUtils.setTag(stack, nbt);
                        return DataComponentsHelper.createIngredientWithComponents(stack);
                    }
                }
                if (includeNBT && map.get("components") instanceof JsonElement components) {
                    JsonObject stackJson = new JsonObject();
                    stackJson.addProperty("id", id);
                    stackJson.add("components", components.deepCopy());
                    if (count > 1) {
                        stackJson.addProperty("count", count);
                    }
                    ItemStack stack = DataComponentsHelper.parseItemStack(stackJson);
                    if (!stack.isEmpty()) {
                        return DataComponentsHelper.createIngredientWithComponents(stack);
                    }
                }
                ingredientJson.addProperty("item", id);
                if (count > 1) {
                    ingredientJson.addProperty("count", count);
                }
                return ingredientJson;
            }
            return null;
        }

        return null;
    }

    /**
     * 创建结果JSON对象（走 DataComponents 形式）
     */
    public static JsonObject createResultJson(ItemStack result, int count) {
        if (result == null || result.isEmpty()) {
            return new JsonObject();
        }
        JsonObject resultJson = DataComponentsHelper.createResultWithComponents(result);
        resultJson.remove("count");
        if (count > 1) {
            resultJson.addProperty("count", count);
        }
        return resultJson;
    }

    private static CompoundTag parseMapNbt(Object value) {
        if (value instanceof JsonElement json) {
            return DataComponentsHelper.parseSnbt(json);
        }
        if (value instanceof String string) {
            try {
                return TagParser.parseTag(string);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 创建工具要求JSON对象
     */
    public static JsonObject createToolJson(String toolTag) {
        JsonObject tool = new JsonObject();
        if (toolTag.startsWith("#")) {
            tool.addProperty("tag", toolTag.substring(1));
        } else {
            tool.addProperty("tag", toolTag);
        }
        return tool;
    }

    /**
     * 从对象中获取符号字符（支持 SYMBOL_CHARS 全集 + '#'）
     */
    public static char getCharFromObject(Object obj) {
        if (obj instanceof Character c) {
            if (SYMBOL_CHARS.indexOf(c) >= 0) {
                return c;
            } else {
                throw new IllegalArgumentException("符号必须是允许的字符: " + c);
            }
        } else if (obj instanceof String str) {
            if (str.isBlank()) {
                throw new IllegalArgumentException("符号不能为空");
            }
            char c = str.charAt(0);
            if (c == '#') {
                return c;
            }
            if (SYMBOL_CHARS.indexOf(c) >= 0) {
                return c;
            } else {
                throw new IllegalArgumentException("符号必须是允许的字符: " + c);
            }
        }
        throw new IllegalArgumentException("无效的符号类型: " + obj);
    }

    /**
     * 获取物品的ResourceLocation
     */
    public static ResourceLocation getItemResourceLocation(Item item) {
        ResourceLocation location = BuiltInRegistries.ITEM.getKey(item);
        return location != null ? location : ResourceLocation.fromNamespaceAndPath("minecraft", "air");
    }
}
