package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.ModLogger;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * FarmersDelight 模组配方处理器
 * 支持：cutting（切割配方）
 */
public class FarmersDelightProcessor implements ModRecipeProcessor {

    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("farmersdelight");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"cutting", "cooking"};
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String recipeType = request.recipeType;
        ModLogger.getLogger().info("Type {}", recipeType);
        return switch (recipeType) {
            case "cooking" -> createCookingRecipe(request);
            default -> createCuttingRecipe(request);
        };
    }

    /**
     * 创建切割配方
     * 格式：
     * {
     *   "type": "farmersdelight:cutting",
     *   "ingredients": [...],
     *   "result": [...],
     *   "tool": { "tag": "forge:tools/knives" }
     * }
     */
    private JsonObject createCuttingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "farmersdelight:cutting");

        // 添加 ingredients（输入材料数组）
        JsonArray ingredientsArray = new JsonArray();
        if (request.ingredients != null && request.ingredients.length > 0) {
            // 切割配方通常只有一个输入
            JsonObject ingredientJson = RecipeUtil.createIngredientJson(request.ingredients[0]);
            if (ingredientJson != null) {
                ingredientsArray.add(ingredientJson);
            }
        }
        recipe.add("ingredients", ingredientsArray);

        // 添加 result（输出结果数组，可以有多个）
        JsonArray resultArray = new JsonArray();

        // 主要输出
        if (request.result != null) {
            JsonObject mainResult = new JsonObject();
            String itemId = RecipeUtil.getItemResourceLocation(request.result.getItem()).toString();
            mainResult.addProperty("item", itemId);

            int count = request.resultCount > 0 ? request.resultCount : request.result.getCount();
            if (count > 1) {
                mainResult.addProperty("count", count);
            }

            resultArray.add(mainResult);
        }

        // 额外输出（从properties中获取）
        Object extraResults = request.properties.get("extraResults");
        if (extraResults instanceof ItemStack[] extraStacks) {
            for (ItemStack stack : extraStacks) {
                if (!stack.isEmpty()) {
                    JsonObject extraResult = new JsonObject();
                    String itemId = RecipeUtil.getItemResourceLocation(stack.getItem()).toString();
                    extraResult.addProperty("item", itemId);

                    if (stack.getCount() > 1) {
                        extraResult.addProperty("count", stack.getCount());
                    }

                    // 可以添加概率（可选）
                    // extraResult.addProperty("chance", 0.5f);

                    resultArray.add(extraResult);
                }
            }
        }

        recipe.add("result", resultArray);

        // 添加 tool（工具标签，默认为刀具）
        JsonObject tool = new JsonObject();
        String toolTag = (String) request.properties.getOrDefault("toolTag", "forge:tools/knives");
        tool.addProperty("tag", toolTag);
        recipe.add("tool", tool);

        // 添加 sound（可选的切割音效）
        String sound = (String) request.properties.get("sound");
        if (sound != null) {
            recipe.addProperty("sound", sound);
        }

        return recipe;
    }

    /**
     * 创建烹饪配方
     * 格式：
     * {
     *   "type": "farmersdelight:cooking",
     *   "ingredients": [...],
     *   "result": {...},
     *   "cookingtime": 200,
     *   "experience": 1.0,
     *   "recipe_book_tab": "drinks"
     * }
     */
    private JsonObject createCookingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "farmersdelight:cooking");

        // 添加 ingredients（多个输入材料）
        JsonArray ingredientsArray = new JsonArray();
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                JsonObject ingredientJson = RecipeUtil.createIngredientJson(ingredient);
                if (ingredientJson != null) {
                    ingredientsArray.add(ingredientJson);
                }
            }
        }
        recipe.add("ingredients", ingredientsArray);

        // 添加 result（单个输出）
        if (request.result != null) {
            JsonObject resultJson = new JsonObject();
            String itemId = RecipeUtil.getItemResourceLocation(request.result.getItem()).toString();
            resultJson.addProperty("item", itemId);

            int count = request.resultCount > 0 ? request.resultCount : request.result.getCount();
            if (count > 1) {
                resultJson.addProperty("count", count);
            }

            recipe.add("result", resultJson);
        }

        // 添加 cookingtime（烹饪时间，默认200 ticks = 10秒）
        Number vv = (Number) request.properties.get("cookingtime");
        if (vv == null) {
            vv = 200;
        }
        int cookingTime = vv.intValue();
        recipe.addProperty("cookingtime", cookingTime);

        // 添加 experience（经验值，默认1.0）
        Number v = (Number) request.properties.get("experience");
        if (v == null) {
            Double expDouble = (Double) request.properties.get("experience");
            v = expDouble != null ? expDouble.floatValue() : 1.0f;
        }
        float experience = v.floatValue();
        recipe.addProperty("experience", experience);

        // 添加 recipe_book_tab（配方书标签页，可选）
        String recipeBookTab = (String) request.properties.get("recipe_book_tab");
        if (recipeBookTab != null && !recipeBookTab.isEmpty()) {
            recipe.addProperty("recipe_book_tab", recipeBookTab);
        }

        // 添加 container（容器物品，可选 - 如碗）
        Object container = request.properties.get("container");
        if (container != null) {
            JsonObject containerJson = RecipeUtil.createIngredientJson(container);
            if (containerJson != null) {
                recipe.add("container", containerJson);
            }
        }

        return recipe;
    }
}