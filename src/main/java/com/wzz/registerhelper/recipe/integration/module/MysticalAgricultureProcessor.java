package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MysticalAgricultureProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("mysticalagriculture");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
                "mysticalagriculture:infusion",
                "mysticalagriculture:awakening",
                "mysticalagriculture:reprocessor"
        };
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", request.recipeType);

        switch (request.recipeType) {
            case "mysticalagriculture:infusion" -> createInfusionRecipe(recipe, request);
            case "mysticalagriculture:awakening" -> createAwakeningRecipe(recipe, request);
            case "mysticalagriculture:reprocessor" -> createReprocessorRecipe(recipe, request);
        }

        return recipe;
    }

    /**
     * 注魔祭坛配方：中央物品 + 8个外围材料 + 精华 -> 输出
     */
    private void createInfusionRecipe(JsonObject recipe, RecipeRequest request) {
        // 中央输入物品
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", createIngredient(request.ingredients[0]));
        }

        // 外围材料（最多8个）
        JsonArray ingredients = new JsonArray();
        for (int i = 1; i < Math.min(9, request.ingredients.length); i++) {
            ingredients.add(createIngredient(request.ingredients[i]));
        }
        recipe.add("ingredients", ingredients);

        // 输出结果
        recipe.add("result", createResult(request.result, request.resultCount));

        // 精华配置
        String essenceType = (String) request.properties.get("essenceType");
        Integer essenceAmount = (Integer) request.properties.get("essenceAmount");

        JsonObject essence = new JsonObject();
        essence.addProperty("item", essenceType != null ? essenceType : "mysticalagriculture:inferium_essence");
        essence.addProperty("count", essenceAmount != null ? essenceAmount : 4);
        recipe.add("essence", essence);
    }

    /**
     * 觉醒祭坛配方：中央物品 + 4个外围材料 + 4种元素精华 -> 输出
     */
    private void createAwakeningRecipe(JsonObject recipe, RecipeRequest request) {
        // 中央输入物品
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", createIngredient(request.ingredients[0]));
        }

        // 外围材料（通常是4个）
        JsonArray ingredients = new JsonArray();
        for (int i = 1; i < Math.min(5, request.ingredients.length); i++) {
            ingredients.add(createIngredient(request.ingredients[i]));
        }
        recipe.add("ingredients", ingredients);

        // 输出结果
        recipe.add("result", createResult(request.result, request.resultCount));

        // 4种元素精华
        JsonArray essences = new JsonArray();

        // 从 properties 获取精华配置，如果没有则使用默认值
        Integer airCount = (Integer) request.properties.get("airEssenceCount");
        Integer earthCount = (Integer) request.properties.get("earthEssenceCount");
        Integer waterCount = (Integer) request.properties.get("waterEssenceCount");
        Integer fireCount = (Integer) request.properties.get("fireEssenceCount");

        // 默认每种40个
        int defaultCount = 40;

        // 风元素精华
        JsonObject airEssence = new JsonObject();
        airEssence.addProperty("item", "mysticalagriculture:air_essence");
        airEssence.addProperty("count", airCount != null ? airCount : defaultCount);
        essences.add(airEssence);

        // 土元素精华
        JsonObject earthEssence = new JsonObject();
        earthEssence.addProperty("item", "mysticalagriculture:earth_essence");
        earthEssence.addProperty("count", earthCount != null ? earthCount : defaultCount);
        essences.add(earthEssence);

        // 水元素精华
        JsonObject waterEssence = new JsonObject();
        waterEssence.addProperty("item", "mysticalagriculture:water_essence");
        waterEssence.addProperty("count", waterCount != null ? waterCount : defaultCount);
        essences.add(waterEssence);

        // 火元素精华
        JsonObject fireEssence = new JsonObject();
        fireEssence.addProperty("item", "mysticalagriculture:fire_essence");
        fireEssence.addProperty("count", fireCount != null ? fireCount : defaultCount);
        essences.add(fireEssence);

        recipe.add("essences", essences);
    }

    /**
     * 种子重处理器配方
     */
    private void createReprocessorRecipe(JsonObject recipe, RecipeRequest request) {
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", createIngredient(request.ingredients[0]));
        }

        recipe.add("result", createResult(request.result, request.resultCount));
    }

    /**
     * 创建材料JSON对象
     */
    private JsonObject createIngredient(Object ingredient) {
        JsonObject ingredientJson = new JsonObject();

        if (ingredient instanceof ItemStack stack) {
            String itemId = RecipeUtil.getItemResourceLocation(stack.getItem()).toString();
            ingredientJson.addProperty("item", itemId);

            if (stack.getCount() > 1) {
                ingredientJson.addProperty("count", stack.getCount());
            }
        } else if (ingredient instanceof Item item) {
            String itemId = RecipeUtil.getItemResourceLocation(item).toString();
            ingredientJson.addProperty("item", itemId);
        } else if (ingredient instanceof String str) {
            ingredientJson.addProperty("item", str);
        }

        return ingredientJson;
    }

    /**
     * 创建结果JSON对象
     */
    private JsonObject createResult(ItemStack result, int count) {
        JsonObject resultJson = new JsonObject();

        String itemId = RecipeUtil.getItemResourceLocation(result.getItem()).toString();
        resultJson.addProperty("item", itemId);

        if (count > 1) {
            resultJson.addProperty("count", count);
        }

        return resultJson;
    }
}