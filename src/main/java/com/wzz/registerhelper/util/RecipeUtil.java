package com.wzz.registerhelper.util;

import com.google.gson.JsonArray;
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

        // 添加key映射
        if (request.ingredients != null) {
            JsonObject key = new JsonObject();
            Map<Character, JsonObject> keyMapping = new HashMap<>();

            for (int i = 0; i < request.ingredients.length; i += 2) {
                if (i + 1 < request.ingredients.length) {
                    char symbol = getCharFromObject(request.ingredients[i]);
                    Object ingredient = request.ingredients[i + 1];

                    JsonObject ingredientJson = createIngredientJson(ingredient);
                    if (ingredientJson != null) {
                        keyMapping.put(symbol, ingredientJson);
                    }
                }
            }

            for (Map.Entry<Character, JsonObject> entry : keyMapping.entrySet()) {
                key.add(String.valueOf(entry.getKey()), entry.getValue());
            }
            recipe.add("key", key);
        }

        // 添加结果
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
     * 创建材料JSON对象
     */
    public static JsonObject createIngredientJson(Object ingredient) {
        JsonObject ingredientJson = new JsonObject();

        if (ingredient instanceof ItemStack stack) {
            String itemId = getItemResourceLocation(stack.getItem()).toString();
            ingredientJson.addProperty("item", itemId);

            if (stack.getCount() > 1) {
                ingredientJson.addProperty("count", stack.getCount());
            }

            if (stack.hasTag()) {
                ingredientJson.addProperty("nbt", stack.getTag().toString());
            }
        } else if (ingredient instanceof Item item) {
            String itemId = getItemResourceLocation(item).toString();
            ingredientJson.addProperty("item", itemId);
        } else if (ingredient instanceof String str) {
            // 直接是物品ID字符串
            ingredientJson.addProperty("item", str);
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
            resultJson.addProperty("nbt", result.getTag().toString());
        }

        return resultJson;
    }

    /**
     * 从对象中获取字符
     */
    public static char getCharFromObject(Object obj) {
        if (obj instanceof Character) {
            char c = (Character) obj;
            // 检查字符是否有效
            if (c >= 'A' && c <= 'Z') {
                return c;
            } else {
                return 'A';
            }
        } else if (obj instanceof String str) {
            if (str.isEmpty()) {
                return ' ';
            }
            char c = str.charAt(0);
            // 检查字符是否有效
            if (c >= 'A' && c <= 'Z') {
                return c;
            } else {
                return 'A';
            }
        }
        return ' ';
    }

    /**
     * 获取物品的ResourceLocation
     */
    public static ResourceLocation getItemResourceLocation(Item item) {
        ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
        return location != null ? location : new ResourceLocation("minecraft", "air");
    }
}
