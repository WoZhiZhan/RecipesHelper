package com.wzz.registerhelper.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public class RecipeUtil {

    public static JsonObject createShapedTableRecipe(String type, RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        // 添加tier
        Integer tier = (Integer) request.properties.get("tier");
        if (tier != null) {
            recipe.addProperty("tier", tier);
        }

        // 添加pattern
        if (request.pattern != null) {
            JsonArray patternArray = new JsonArray();
            for (String row : request.pattern) {
                patternArray.add(row);
            }
            recipe.add("pattern", patternArray);
        }

        if (request.ingredients != null) {
            JsonObject key = new JsonObject();
            Map<Character, JsonObject> keyMapping = new HashMap<>();

            // 修复：使用自动字符映射，而不是从原料数组中读取符号
            char currentChar = 'A';
            for (Object ingredient : request.ingredients) {
                // 跳过null或空物品
                if (ingredient == null) continue;

                if (ingredient instanceof ItemStack stack && stack.isEmpty()) {
                    continue;
                }

                // 为每个有效原料分配一个字符
                JsonObject ingredientJson = createIngredientJson(ingredient);
                if (ingredientJson != null) {
                    // 找到可用的字符
                    while (currentChar <= 'Z' && keyMapping.containsKey(currentChar)) {
                        currentChar++;
                    }
                    if (currentChar > 'Z') {
                        throw new IllegalArgumentException("合成表原料过多，超过26个字符限制");
                    }

                    keyMapping.put(currentChar, ingredientJson);
                    currentChar++;
                }
            }

            for (Map.Entry<Character, JsonObject> entry : keyMapping.entrySet()) {
                key.add(String.valueOf(entry.getKey()), entry.getValue());
            }
            recipe.add("key", key);
        }

        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }


    public static JsonObject createShapelessTableRecipe(String type, RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        // 添加tier
        Integer tier = (Integer) request.properties.get("tier");
        if (tier != null) {
            recipe.addProperty("tier", tier);
        }

        // 添加ingredients
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

        // 添加结果
        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    /**
     * 创建熔炉配方
     */
    public static JsonObject createSmeltingRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 200); // 默认200 ticks (10秒)
    }

    /**
     * 创建高炉配方
     */
    public static JsonObject createBlastingRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 100); // 默认100 ticks (5秒)
    }

    /**
     * 创建烟熏炉配方
     */
    public static JsonObject createSmokingRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 100); // 默认100 ticks (5秒)
    }

    /**
     * 创建营火配方
     */
    public static JsonObject createCampfireRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 600); // 默认600 ticks (30秒)
    }

    /**
     * 创建烹饪类配方的通用方法
     */
    private static JsonObject createCookingRecipe(String type, RecipeRequest request, int defaultCookingTime) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        // 添加ingredient（只取第一个材料）
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject ingredientJson = createIngredientJson(request.ingredients[0]);
            if (ingredientJson != null) {
                recipe.add("ingredient", ingredientJson);
            }
        }

        // 添加结果（简化版，只包含item和count）
        if (request.result != null) {
            String itemId = getItemResourceLocation(request.result.getItem()).toString();
            recipe.addProperty("result", itemId);
        }

        // 添加经验值（从properties中获取，默认0.1）
        Float experience = (Float) request.properties.get("experience");
        if (experience == null) {
            experience = (Double) request.properties.get("experience") != null
                    ? ((Double) request.properties.get("experience")).floatValue()
                    : 0.1f;
        }
        recipe.addProperty("experience", experience);

        // 添加烹饪时间（从properties中获取，否则使用默认值）
        Integer cookingTime = (Integer) request.properties.get("cookingtime");
        if (cookingTime == null) {
            cookingTime = defaultCookingTime;
        }
        recipe.addProperty("cookingtime", cookingTime);

        return recipe;
    }

    // ========== 多输出配方（通用） ==========

    /**
     * 创建多输出配方（适用于FarmersDelight等模组）
     * @param type 配方类型
     * @param request 配方请求
     * @param resultFieldName 结果字段名（如 "result" 或 "results"）
     * @return JSON配方对象
     */
    public static JsonObject createMultiOutputRecipe(String type, RecipeRequest request, String resultFieldName) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        // 添加 ingredients
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

        // 添加多个输出结果
        JsonArray resultArray = createMultipleResults(request);
        recipe.add(resultFieldName, resultArray);

        return recipe;
    }

    /**
     * 创建多个输出结果的数组
     */
    public static JsonArray createMultipleResults(RecipeRequest request) {
        JsonArray resultArray = new JsonArray();

        // 主要输出
        if (request.result != null) {
            JsonObject mainResult = createResultJson(request.result, request.resultCount);
            resultArray.add(mainResult);
        }

        // 额外输出（从properties中获取）
        Object extraResults = request.properties.get("extraResults");
        if (extraResults instanceof ItemStack[] extraStacks) {
            for (ItemStack stack : extraStacks) {
                if (!stack.isEmpty()) {
                    JsonObject extraResult = createResultJson(stack, stack.getCount());

                    // 添加概率（如果有）
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

    /**
     * 创建材料JSON对象
     */
    @SuppressWarnings("unchecked")
    public static JsonObject createIngredientJson(Object ingredient) {
        JsonObject ingredientJson = new JsonObject();

        if (ingredient instanceof ItemStack stack) {
            String itemId = getItemResourceLocation(stack.getItem()).toString();
            ingredientJson.addProperty("item", itemId);

            if (stack.getCount() > 1) {
                ingredientJson.addProperty("count", stack.getCount());
            }
            if (stack.hasTag()) {
                ingredientJson.addProperty("type", "forge:nbt");
                ingredientJson.addProperty("nbt", stack.getTag().toString());
            }
        } else if (ingredient instanceof Item item) {
            String itemId = getItemResourceLocation(item).toString();
            ingredientJson.addProperty("item", itemId);
        } else if (ingredient instanceof String str) {
            if (str.startsWith("#")) {
                ingredientJson.addProperty("tag", str.substring(1));
            } else {
                ingredientJson.addProperty("item", str);
            }
        } else if (ingredient instanceof Map map) {
            if (map.containsKey("fluid")) {
                String fluidId = (String) map.get("fluid");
                int amount = (int) map.getOrDefault("amount", 250);
                ingredientJson.addProperty("fluid", fluidId);
                ingredientJson.addProperty("amount", amount);

                if (map.containsKey("nbt") && map.get("nbt") instanceof JsonElement json) {
                    ingredientJson.add("nbt", json);
                } else {
                    ingredientJson.add("nbt", new JsonObject());
                }
            }
            else if (map.containsKey("item")) {
                ingredientJson.addProperty("item", (String) map.get("item"));
                if (map.containsKey("nbt") && map.get("nbt") instanceof JsonElement json) {
                    ingredientJson.add("nbt", json);
                }
            }
        } else {
            return null;
        }
        return ingredientJson;
    }


    /**
     * 创建结果JSON对象
     */
    public static JsonObject createResultJson(ItemStack result, int count) {
        JsonObject resultJson = new JsonObject();

        String itemId = getItemResourceLocation(result.getItem()).toString();
        resultJson.addProperty("item", itemId);

        if (count > 1) {
            resultJson.addProperty("count", count);
        }

        if (result.hasTag()) {
            resultJson.addProperty("type", "forge:nbt");
            resultJson.addProperty("nbt", result.getTag().toString());
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
     * 从对象中获取字符
     */
    public static char getCharFromObject(Object obj) {
        if (obj instanceof Character) {
            char c = (Character) obj;
            if (c >= 'A' && c <= 'Z') {
                return c;
            } else {
                throw new IllegalArgumentException("符号必须是大写字母A-Z: " + c);
            }
        } else if (obj instanceof String str) {
            if (str.isBlank()) {
                throw new IllegalArgumentException("符号不能为空");
            }
            char c = str.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return c;
            } else {
                if (c == '#')
                    return c;
                throw new IllegalArgumentException("符号必须是大写字母A-Z: " + c);
            }
        }
        throw new IllegalArgumentException("无效的符号类型: " + obj);
    }

    /**
     * 获取物品的ResourceLocation
     */
    public static ResourceLocation getItemResourceLocation(Item item) {
        ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
        return location != null ? location : new ResourceLocation("minecraft", "air");
    }
}