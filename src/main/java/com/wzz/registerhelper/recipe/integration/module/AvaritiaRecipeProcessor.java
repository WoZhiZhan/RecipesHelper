package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;

import java.util.List;

import static com.wzz.registerhelper.util.RecipeUtil.createIngredientJson;

public class AvaritiaRecipeProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        try {
            Class.forName("committee.nova.mods.avaritia.ModApi");
            return true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("committee.nova.mods.avaritia.Avaritia");
                return true;
            } catch (ClassNotFoundException e2) {
                return net.minecraftforge.fml.ModList.get().isLoaded("avaritia");
            }
        }
    }
    
    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        return switch (request.recipeType.toLowerCase()) {
            case "avaritia:shaped_table" -> createShapedTableRecipe(request);
            case "avaritia:shapeless_table" -> createShapelessTableRecipe(request);
            case "avaritia:compressor" -> createCompressorRecipe(request);
            default -> null;
        };
    }
    
    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"shaped_table", "shapeless_table", "compressor"};
    }
    
    private JsonObject createShapedTableRecipe(RecipeRequest request) {
        return RecipeUtil.createShapedTableRecipe("avaritia:shaped_table", request);
    }
    
    private JsonObject createShapelessTableRecipe(RecipeRequest request) {
        return RecipeUtil.createShapelessTableRecipe("avaritia:shapeless_table", request);
    }

    private JsonObject createCompressorRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "avaritia:compressor");

        String name = (String) request.properties.getOrDefault("name", "singularity.avaritia.unknown");
        recipe.addProperty("name", name);

        if (request.properties.containsKey("colors")) {
            @SuppressWarnings("unchecked")
            List<String> colors = (List<String>) request.properties.get("colors");
            JsonArray colorArray = new JsonArray();
            for (String color : colors) {
                colorArray.add(color);
            }
            recipe.add("colors", colorArray);
        }

        Integer timeRequired = (Integer) request.properties.get("timeRequired");
        if (timeRequired != null) {
            recipe.addProperty("timeRequired", timeRequired);
        }

        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject ingredient = createIngredientJson(request.ingredients[0]);
            if (ingredient != null) {
                recipe.add("ingredient", ingredient);
            }
        }

        recipe.addProperty("enabled", request.properties.getOrDefault("enabled", true).toString());
        recipe.addProperty("recipeDisabled", request.properties.getOrDefault("recipeDisabled", false).toString());

        return recipe;
    }
}