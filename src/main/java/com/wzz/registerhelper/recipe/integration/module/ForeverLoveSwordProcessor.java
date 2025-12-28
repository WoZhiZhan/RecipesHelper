package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraftforge.fml.ModList;

public class ForeverLoveSwordProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("forever_love_sword");
    }
    
    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"starshine_oath_table"};
    }

    @Override
    public boolean isShapedRecipe(String recipeType) {
        return true;
    }
    
    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        return createShapedTableRecipe(request);
    }

    private JsonObject createShapedTableRecipe(RecipeRequest request) {
        return RecipeUtil.createShapedTableRecipe("forever_love_sword:starshine_oath_table", request);
    }
}