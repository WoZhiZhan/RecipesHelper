package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;

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
            case "shaped", "shaped_table" -> createShapedTableRecipe(request);
            case "shapeless", "shapeless_table" -> createShapelessTableRecipe(request);
            default -> null;
        };
    }
    
    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"shaped", "shapeless", "shaped_table", "shapeless_table"};
    }
    
    private JsonObject createShapedTableRecipe(RecipeRequest request) {
        return RecipeUtil.createShapedTableRecipe("avaritia:shaped_table", request);
    }
    
    private JsonObject createShapelessTableRecipe(RecipeRequest request) {
        return RecipeUtil.createShapelessTableRecipe("avaritia:shapeless_table", request);
    }
}