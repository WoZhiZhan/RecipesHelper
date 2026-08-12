package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.neoforged.fml.ModList;

public class ExtraBotanyProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("extrabotany");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"extrabotany:pedestal_smash"};
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String type = request.recipeType.toLowerCase();
        if (type.contains(":")) {
            type = type.substring(type.indexOf(":") + 1);
        }
        if (!"pedestal_smash".equals(type)) {
            return null;
        }

        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "extrabotany:pedestal_smash");

        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject input = RecipeUtil.createIngredientJson(request.ingredients[0]);
            if (input != null) {
                recipe.add("input", input);
            }
        }

        if (request.result != null) {
            recipe.add("output", RecipeUtil.createResultJson(request.result, request.resultCount));
        }

        Object smashTools = request.properties.get("smash_tools");
        if (smashTools != null) {
            JsonObject tools = RecipeUtil.createIngredientJson(smashTools);
            if (tools != null) {
                recipe.add("smash_tools", tools);
            }
        }

        Number strike = (Number) request.properties.get("strike");
        recipe.addProperty("strike", strike != null ? strike.intValue() : 10);

        Number experience = (Number) request.properties.get("exp");
        if (experience != null && experience.intValue() > 0) {
            recipe.addProperty("exp", experience.intValue());
        }
        return recipe;
    }
}
