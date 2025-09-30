package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;

public class MysticalAgricultureProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("mysticalagriculture");
    }
    
    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
            "mysticalagriculture:infusion",
            "mysticalagriculture:reprocessor",
            "mysticalagriculture:soul_extraction"
        };
    }
    
    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", request.recipeType);
        
        switch (request.recipeType) {
            case "mysticalagriculture:infusion" -> createInfusionRecipe(recipe, request);
            case "mysticalagriculture:reprocessor" -> createReprocessorRecipe(recipe, request);
            case "mysticalagriculture:soul_extraction" -> createSoulExtractionRecipe(recipe, request);
        }
        
        return recipe;
    }
    
    private void createInfusionRecipe(JsonObject recipe, RecipeRequest request) {
        // 注魔祭坛：中央物品 + 8个外围材料 + 精华 -> 输出
        JsonObject input = new JsonObject();
        input.addProperty("item", request.ingredients[0].toString());
        recipe.add("input", input);
        
        JsonArray ingredients = new JsonArray();
        for (int i = 1; i < Math.min(9, request.ingredients.length); i++) {
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", request.ingredients[i].toString());
            ingredients.add(ingredient);
        }
        recipe.add("ingredients", ingredients);
        
        JsonObject result = new JsonObject();
        result.addProperty("item", request.result.getItem().toString());
        result.addProperty("count", request.resultCount);
        recipe.add("result", result);
        
        String essenceType = (String) request.properties.get("essenceType");
        Integer essenceAmount = (Integer) request.properties.get("essenceAmount");
        
        JsonObject essence = new JsonObject();
        essence.addProperty("item", essenceType != null ? essenceType : "mysticalagriculture:inferium_essence");
        essence.addProperty("count", essenceAmount != null ? essenceAmount : 4);
        recipe.add("essence", essence);
    }
    
    private void createReprocessorRecipe(JsonObject recipe, RecipeRequest request) {
        // 种子重处理器
        JsonObject input = new JsonObject();
        input.addProperty("item", request.ingredients[0].toString());
        recipe.add("input", input);
        
        JsonObject result = new JsonObject();
        result.addProperty("item", request.result.getItem().toString());
        result.addProperty("count", request.resultCount);
        recipe.add("result", result);
    }
    
    private void createSoulExtractionRecipe(JsonObject recipe, RecipeRequest request) {
        // 灵魂提取器
        JsonObject input = new JsonObject();
        input.addProperty("item", request.ingredients[0].toString());
        recipe.add("input", input);
        
        JsonObject result = new JsonObject();
        result.addProperty("item", request.result.getItem().toString());
        result.addProperty("count", request.resultCount);
        recipe.add("result", result);
        
        Integer souls = (Integer) request.properties.get("souls");
        recipe.addProperty("souls", souls != null ? souls : 1);
    }
}