package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;

import static com.wzz.registerhelper.util.RecipeUtil.createIngredientJson;
import static com.wzz.registerhelper.util.RecipeUtil.createResultJson;
import static com.wzz.registerhelper.util.RecipeUtil.getCharFromObject;

/**
 * Extended Crafting table recipes for the 3x3 through 9x9 table tiers.
 */
public class ExtendedCraftingProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("extendedcrafting");
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String recipeType = request.recipeType.toLowerCase();
        if (recipeType.contains(":")) {
            recipeType = recipeType.substring(recipeType.indexOf(":") + 1);
        }

        if (recipeType.contains("shaped") && !recipeType.contains("shapeless")) {
            return createShapedTableRecipe(request);
        }
        if (recipeType.contains("shapeless")) {
            return createShapelessTableRecipe(request);
        }
        return null;
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
                "shaped_table", "shaped_table_5x5", "shaped_table_7x7", "shaped_table_9x9",
                "shapeless_table", "shapeless_table_5x5", "shapeless_table_7x7", "shapeless_table_9x9"
        };
    }

    @Override
    public boolean isShapedRecipe(String recipeType) {
        return recipeType.contains("shaped") && !recipeType.contains("shapeless");
    }

    private JsonObject createShapedTableRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "extendedcrafting:shaped_table");

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
            for (int i = 0; i + 1 < request.ingredients.length; i += 2) {
                char symbol = getCharFromObject(request.ingredients[i]);
                JsonObject ingredient = createIngredientJson(request.ingredients[i + 1]);
                if (ingredient != null) {
                    keyMapping.put(symbol, ingredient);
                }
            }
            for (Map.Entry<Character, JsonObject> entry : keyMapping.entrySet()) {
                key.add(String.valueOf(entry.getKey()), entry.getValue());
            }
            recipe.add("key", key);
        }

        recipe.add("result", createResultJson(request.result, request.resultCount));

        Integer tier = getTier(request);
        if (tier != null && tier > 0) {
            recipe.addProperty("tier", tier);
        }
        return recipe;
    }

    private JsonObject createShapelessTableRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "extendedcrafting:shapeless_table");

        if (request.ingredients != null) {
            JsonArray ingredients = new JsonArray();
            for (Object ingredient : request.ingredients) {
                JsonObject ingredientJson = createIngredientJson(ingredient);
                if (ingredientJson != null) {
                    ingredients.add(ingredientJson);
                }
            }
            recipe.add("ingredients", ingredients);
        }

        recipe.add("result", createResultJson(request.result, request.resultCount));

        Integer tier = getTier(request);
        if (tier != null && tier > 0) {
            recipe.addProperty("tier", tier);
        }
        return recipe;
    }

    private Integer getTier(RecipeRequest request) {
        Object tier = request.properties.get("tier");
        if (!(tier instanceof Number)) {
            tier = request.properties.get("customTier");
        }
        return tier instanceof Number number ? number.intValue() : null;
    }
}
