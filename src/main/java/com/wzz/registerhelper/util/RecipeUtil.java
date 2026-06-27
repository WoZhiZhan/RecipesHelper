package com.wzz.registerhelper.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.gui.recipe.IngredientData;
import com.wzz.registerhelper.recipe.RecipeRequest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方 JSON 生成工具。
 *
 * NeoForge 1.21.1 迁移要点：
 * - forge:nbt -> neoforge:nbt（精确 NBT 匹配的 ingredient 类型）
 * - stack.hasTag()/getTag() -> OldUtils.hasTag()/getTag()（DataComponents 兼容层）
 * - ForgeRegistries.ITEMS -> BuiltInRegistries.ITEM
 * - ItemStack 材料/结果默认走 DataComponentsHelper（生成 components 形式），
 *   仅在需要旧式 nbt 字符串匹配（精确 / partial）时回退到 OldUtils 拼 NBT
 * - registerhelper:partial_nbt 为本 mod 自定义 ingredient 类型，名称不变
 *
 * 依赖说明：createIngredientJson(IngredientData) 与 ignoreKeys 重载依赖
 * IngredientData 的 isIncludeNBT() / getIgnoreNbtKeys() 等方法（属 GUI 包，
 * 移植 IngredientData 时需一并恢复其 NBT 控制字段）。
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

        // 1.21+ 结果使用对象格式（id + 可选 count/nbt）
        if (request.result != null) {
            JsonObject resultObj = new JsonObject();
            String itemId = getItemResourceLocation(request.result.getItem()).toString();
            resultObj.addProperty("id", itemId);

            if (request.resultCount > 1) {
                resultObj.addProperty("count", request.resultCount);
            }

            if (OldUtils.hasTag(request.result)) {
                resultObj.addProperty("type", "neoforge:nbt");
                resultObj.addProperty("nbt", OldUtils.getTag(request.result).toString());
            }

            recipe.add("result", resultObj);
        }

        Float experience = (Float) request.properties.get("experience");
        if (experience == null) {
            experience = (Double) request.properties.get("experience") != null
                    ? ((Double) request.properties.get("experience")).floatValue()
                    : 0.1f;
        }
        recipe.addProperty("experience", experience);

        Integer cookingTime = (Integer) request.properties.get("cookingtime");
        if (cookingTime == null) {
            cookingTime = defaultCookingTime;
        }
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

                    Float chance = (Float) request.properties.get("chance_" + stack.getItem());
                    if (chance != null && chance < 1.0f) {
                        extraResult.addProperty("chance", chance);
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
     * 创建材料 JSON（默认包含 NBT，精确匹配）
     */
    public static JsonObject createIngredientJson(Object ingredient) {
        return createIngredientJson(ingredient, true, List.of());
    }

    /**
     * 创建材料 JSON（仅控制是否包含 NBT）
     */
    public static JsonObject createIngredientJson(Object ingredient, boolean includeNBT) {
        return createIngredientJson(ingredient, includeNBT, List.of());
    }

    /**
     * 从 {@link IngredientData} 创建材料 JSON（自动读取 ignoreNbtKeys）。
     * GUI 层推荐入口。
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
     * 核心实现：创建材料 JSON。
     *
     * <p>NBT 处理策略（1.21）：
     * <ul>
     *   <li>includeNBT=false：仅匹配物品 ID（走 DataComponentsHelper 但忽略组件，
     *       这里直接只写 item）</li>
     *   <li>includeNBT=true 且无 ignoreKeys：精确匹配。优先使用 DataComponents 形式
     *       （DataComponentsHelper），保证 1.21 原生兼容</li>
     *   <li>includeNBT=true 且有 ignoreKeys：使用 registerhelper:partial_nbt，
     *       NBT 通过 OldUtils 还原成旧式字符串写入</li>
     * </ul>
     *
     * @param ingredient  物品对象（ItemStack / Item / String / Map / IngredientData）
     * @param includeNBT  是否写入 NBT 匹配字段
     * @param ignoreKeys  若非空且 includeNBT=true，则改用 registerhelper:partial_nbt
     */
    @SuppressWarnings("unchecked")
    public static JsonObject createIngredientJson(Object ingredient, boolean includeNBT, List<String> ignoreKeys) {
        if (ingredient instanceof IngredientData data) {
            return createIngredientJson(data); // 走专用重载
        }

        if (ingredient instanceof ItemStack stack) {
            boolean hasIgnore = ignoreKeys != null && !ignoreKeys.isEmpty();

            // 部分匹配：registerhelper:partial_nbt
            if (includeNBT && hasIgnore && OldUtils.hasTag(stack)) {
                JsonObject ingredientJson = new JsonObject();
                ingredientJson.addProperty("type", "registerhelper:partial_nbt");
                ingredientJson.addProperty("item", getItemResourceLocation(stack.getItem()).toString());
                ingredientJson.addProperty("nbt", OldUtils.getTag(stack).toString());
                JsonArray arr = new JsonArray();
                ignoreKeys.forEach(arr::add);
                ingredientJson.add("ignore_keys", arr);
                if (stack.getCount() > 1) {
                    ingredientJson.addProperty("count", stack.getCount());
                }
                return ingredientJson;
            }

            // 精确匹配（含 NBT）：使用 DataComponents 形式
            if (includeNBT) {
                JsonObject ingredientJson = DataComponentsHelper.createIngredientWithComponents(stack);
                if (stack.getCount() > 1 && !ingredientJson.has("count")) {
                    ingredientJson.addProperty("count", stack.getCount());
                }
                return ingredientJson;
            }

            // 不含 NBT：仅物品 ID
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

            // ---------- Fluid ----------
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

            // ---------- Item / Block（统一为 item id） ----------
            String id = null;
            if (map.containsKey("id")) {
                id = (String) map.get("id");
            } else if (map.containsKey("item")) {
                id = (String) map.get("item");
            } else if (map.containsKey("block")) {
                id = (String) map.get("block");
            }

            if (id != null) {
                ingredientJson.addProperty("item", id);
                if (map.containsKey("count")) {
                    ingredientJson.addProperty("count", ((Number) map.get("count")).intValue());
                }
                if (includeNBT && map.containsKey("nbt") && map.get("nbt") instanceof JsonElement json) {
                    ingredientJson.addProperty("type", "neoforge:nbt");
                    ingredientJson.add("nbt", json);
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
        JsonObject resultJson = DataComponentsHelper.createResultWithComponents(result);
        if (count > 1) {
            resultJson.addProperty("count", count);
        }
        return resultJson;
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
