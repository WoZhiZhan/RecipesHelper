package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;

public class ExtraBotanyProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("extrabotany");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
                "extrabotany:pedestal_smash"
        };
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", request.recipeType);

        if (request.recipeType.equals("extrabotany:pedestal_smash")) {
            createPedestalSmashRecipe(recipe, request);
        }

        return recipe;
    }

    private void createPedestalSmashRecipe(JsonObject recipe, RecipeRequest request) {
        // 处理输入（单个物品）
        if (request.ingredients != null && request.ingredients.length > 0) {
            Object firstIngredient = request.ingredients[0];
            JsonObject input = createIngredientObject(firstIngredient);
            recipe.add("input", input);
        }

        // 处理输出
        JsonObject output = new JsonObject();
        output.addProperty("item", ensureValidItemId(request.result.getItem().toString()));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);

        // 处理 smash_tools（锤子工具标签）
        Object smashTools = request.properties.get("smash_tools");
        if (smashTools != null) {
            JsonObject toolsObj = createIngredientObject(smashTools);
            recipe.add("smash_tools", toolsObj);
        }

        // 处理 strike（敲击次数）
        Integer strike = (Integer) request.properties.get("strike");
        recipe.addProperty("strike", strike != null ? strike : 10);

        // 处理 exp（经验值）
        Integer exp = (Integer) request.properties.get("exp");
        if (exp != null && exp > 0) {
            recipe.addProperty("exp", exp);
        }
    }

    /**
     * 创建材料对象，支持tag和item
     */
    private JsonObject createIngredientObject(Object ingredient) {
        JsonObject obj = new JsonObject();
        String str = ingredient.toString();

        if (str.startsWith("#")) {
            // Tag格式
            obj.addProperty("tag", str.substring(1));
        } else {
            // Item格式
            obj.addProperty("item", ensureValidItemId(str));
        }

        return obj;
    }

    /**
     * 确保物品ID包含命名空间
     */
    private String ensureValidItemId(String itemId) {
        if (itemId == null) {
            return "minecraft:air";
        }

        if (itemId.contains(":")) {
            return itemId;
        }

        return "minecraft:" + itemId;
    }
}