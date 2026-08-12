package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

public class AstralrailCubeProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("astralrail_cube");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"path_ascension", "path_transmuter"};
    }

    @Override
    public boolean isShapedRecipe(String recipeType) {
        return recipeType.contains("path_ascension");
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String type = request.recipeType.toLowerCase();
        if (type.contains(":")) {
            type = type.substring(type.indexOf(":") + 1);
        }
        return switch (type) {
            case "path_ascension" -> createPathAscensionRecipe(request);
            case "path_transmuter" -> createPathTransmuterRecipe(request);
            default -> null;
        };
    }

    private JsonObject createPathAscensionRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "astralrail_cube:path_ascension");

        if (request.pattern != null) {
            List<String> rows = new ArrayList<>();
            for (String row : request.pattern) {
                if (row != null && !row.isBlank()) {
                    rows.add(row);
                }
            }
            while (rows.size() < 2) {
                rows.add("");
            }

            JsonArray pattern = new JsonArray();
            pattern.add(rows.get(0));
            pattern.add(rows.get(1));
            recipe.add("pattern", pattern);
        }

        if (request.ingredients != null) {
            JsonObject key = new JsonObject();
            boolean includeNbt = (Boolean) request.properties.getOrDefault("includeNBT", true);
            for (int i = 0; i + 1 < request.ingredients.length; i += 2) {
                if (request.ingredients[i] instanceof Character symbol) {
                    JsonObject ingredient = RecipeUtil.createIngredientJson(
                            request.ingredients[i + 1], includeNbt);
                    if (ingredient != null) {
                        key.add(String.valueOf(symbol), ingredient);
                    }
                }
            }
            recipe.add("key", key);
        }

        recipe.add("result", RecipeUtil.createResultJson(request.result, request.resultCount));
        return recipe;
    }

    private JsonObject createPathTransmuterRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "astralrail_cube:path_transmuter");

        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject leftSlot = RecipeUtil.createIngredientJson(request.ingredients[0]);
            if (leftSlot != null) {
                recipe.add("left_slot", leftSlot);
            }
        }
        if (request.ingredients != null && request.ingredients.length > 1) {
            JsonObject centerSlot = RecipeUtil.createIngredientJson(request.ingredients[1]);
            if (centerSlot != null) {
                recipe.add("center_slot", centerSlot);
            }
        }

        if (request.result != null) {
            int count = request.resultCount > 1 ? request.resultCount : request.result.getCount();
            recipe.add("output", RecipeUtil.createResultJson(request.result, count));
        }

        Number experience = (Number) request.properties.get("experience");
        recipe.addProperty("experience", experience != null ? experience.intValue() : 0);

        Number repairCost = (Number) request.properties.get("repair_cost");
        recipe.addProperty("repair_cost", repairCost != null ? repairCost.intValue() : 0);
        return recipe;
    }
}
