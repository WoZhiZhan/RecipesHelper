package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraftforge.fml.ModList;

public class EternisStarrySkyProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("eternisstarrysky");
    }
    
    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"vanilla_workbench_s"};
    }
    
    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        return createShapedTableRecipe(request);
    }

    private JsonObject createShapedTableRecipe(RecipeRequest request) {
        return RecipeUtil.createShapedTableRecipe("eternisstarrysky:vanilla_workbench_s", request);
    }
}