package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Rebuilds ordinary learned shaped/shapeless recipes from a JSON template. */
public final class LearnedJsonRecipeProcessor implements ModRecipeProcessor {
    private final ResourceLocation typeId;
    private final JsonObject template;
    private final boolean shaped;
    private final int gridSize;
    private final String outputKey;

    public LearnedJsonRecipeProcessor(ResourceLocation typeId, JsonObject template,
                                      boolean shaped, int gridSize, String outputKey) {
        this.typeId = typeId;
        this.template = template.deepCopy();
        this.shaped = shaped;
        this.gridSize = gridSize;
        this.outputKey = outputKey;
    }

    public boolean isShaped() {
        return shaped;
    }

    public int getGridSize() {
        return gridSize;
    }

    @Override
    public boolean isModLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded(typeId.getNamespace());
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{typeId.toString()};
    }

    @Override
    public boolean isShapedRecipe(String recipeType) {
        return shaped;
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        JsonObject recipe = template.deepCopy();
        recipe.addProperty("type", typeId.toString());
        if (shaped) {
            writeShaped(recipe, request);
        } else {
            writeShapeless(recipe, request);
        }
        writeResult(recipe, request);
        return recipe;
    }

    private void writeShaped(JsonObject recipe, RecipeRequest request) {
        JsonArray pattern = new JsonArray();
        if (request.pattern != null) {
            for (String row : request.pattern) pattern.add(row);
        }
        recipe.add("pattern", pattern);

        JsonObject key = new JsonObject();
        if (request.ingredients != null) {
            for (int i = 0; i + 1 < request.ingredients.length; i += 2) {
                Object symbol = request.ingredients[i];
                JsonObject ingredient = RecipeUtil.createIngredientJson(request.ingredients[i + 1]);
                if (ingredient != null) key.add(String.valueOf(symbol), ingredient);
            }
        }
        recipe.add("key", key);
    }

    private void writeShapeless(JsonObject recipe, RecipeRequest request) {
        JsonArray ingredients = new JsonArray();
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                JsonObject json = RecipeUtil.createIngredientJson(ingredient);
                if (json != null) ingredients.add(json);
            }
        }
        recipe.add("ingredients", ingredients);
    }

    private void writeResult(JsonObject recipe, RecipeRequest request) {
        if (request.result == null || request.result.isEmpty()) return;
        JsonObject result = RecipeUtil.createResultJson(request.result, request.resultCount);
        if ("output".equals(outputKey)) {
            recipe.add("output", result);
        } else {
            recipe.add("result", result);
        }
    }
}
