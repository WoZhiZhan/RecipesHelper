package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import net.minecraftforge.fml.ModList;

import java.util.HashMap;
import java.util.Map;

import static com.wzz.registerhelper.util.RecipeUtil.*;

/**
 * ExtendedCrafting配方处理器
 * 支持3x3, 5x5, 7x7, 9x9的Table配方
 */
public class ExtendedCraftingProcessor implements ModRecipeProcessor {

    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("extendedcrafting");
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String recipeType = request.recipeType.toLowerCase();
        
        // 移除modid前缀
        if (recipeType.contains(":")) {
            recipeType = recipeType.substring(recipeType.indexOf(":") + 1);
        }
        
        // 判断是shaped还是shapeless
        if (recipeType.contains("shaped") && !recipeType.contains("shapeless")) {
            return createShapedTableRecipe(request);
        } else if (recipeType.contains("shapeless")) {
            return createShapelessTableRecipe(request);
        }
        
        return null;
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
            "shaped_table", "shapeless_table"
        };
    }

    @Override
    public boolean isShapedRecipe(String recipeType) {
        return recipeType.contains("shaped") && !recipeType.contains("shapeless");
    }

    /**
     * 创建有序Table配方
     */
    private JsonObject createShapedTableRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "extendedcrafting:shaped_table");

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

        // 添加tier（如果有）
        Integer tier = (Integer) request.properties.get("tier");
        if (tier != null && tier > 0) {
            recipe.addProperty("tier", tier);
        }

        return recipe;
    }

    /**
     * 创建无序Table配方
     */
    private JsonObject createShapelessTableRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "extendedcrafting:shapeless_table");

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

        // 添加tier（如果有）
        Integer tier = (Integer) request.properties.get("tier");
        if (tier != null && tier > 0) {
            recipe.addProperty("tier", tier);
        }

        return recipe;
    }
}