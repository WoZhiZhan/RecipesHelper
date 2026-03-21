package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraftforge.fml.ModList;

/**
 * AstralrailCube 模组配方处理器
 * 支持：
 *   path_ascension  —— shaped 合成台（key/pattern/result）
 *   path_transmuter —— 双槽转换台（left_slot / center_slot / output / experience / repair_cost）
 */
public class AstralrailCubeProcessor implements ModRecipeProcessor {

    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("astralrail_cube");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"path_ascension", "path_transmuter"};
    }

    @Override
    public boolean isShapedRecipe(String recipeType) {
        return recipeType.contains("path_ascension");
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String type = request.recipeType.toLowerCase();
        if (type.contains(":")) {
            type = type.substring(type.indexOf(":") + 1);
        }
        return switch (type) {
            case "path_transmuter" -> createPathTransmuterRecipe(request);
            default -> createPathAscensionRecipe(request);
        };
    }

    private JsonObject createPathAscensionRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "astralrail_cube:path_ascension");

        if (request.pattern != null) {
            // 去掉末尾全空行，再裁剪/补足到 2 行
            List<String> rows = new ArrayList<>();
            for (String row : request.pattern) {
                if (row != null && !row.isBlank()) {
                    rows.add(row);
                }
            }
            while (rows.size() < 2) rows.add("");

            JsonArray patternArray = new JsonArray();
            patternArray.add(rows.get(0));
            patternArray.add(rows.get(1));
            recipe.add("pattern", patternArray);
        }

        if (request.ingredients != null) {
            JsonObject key = new JsonObject();
            boolean includeNBT = (Boolean) request.properties.getOrDefault("includeNBT", true);
            for (int i = 0; i + 1 < request.ingredients.length; i += 2) {
                if (request.ingredients[i] instanceof Character symbol) {
                    JsonObject ingredientJson = RecipeUtil.createIngredientJson(
                            request.ingredients[i + 1], includeNBT);
                    if (ingredientJson != null) {
                        key.add(String.valueOf(symbol), ingredientJson);
                    }
                }
            }
            recipe.add("key", key);
        }

        recipe.add("result", RecipeUtil.createResultJson(request.result, request.resultCount));

        return recipe;
    }

    // path_transmuter：双槽转换台
    //   格式：
    //   {
    //     "type": "astralrail_cube:path_transmuter",
    //     "left_slot":   { "item": "..." },   // ingredients[0]
    //     "center_slot": { "item": "..." },   // ingredients[1]
    //     "output":      { "item": "...", "count": N },
    //     "experience":  16,                  // properties["experience"]，默认 0
    //     "repair_cost": 0                    // properties["repair_cost"]，默认 0
    //   }
    // -------------------------------------------------------------------------
    private JsonObject createPathTransmuterRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "astralrail_cube:path_transmuter");

        // left_slot —— 第一个材料
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject leftSlot = RecipeUtil.createIngredientJson(request.ingredients[0]);
            if (leftSlot != null) {
                recipe.add("left_slot", leftSlot);
            }
        }

        // center_slot —— 第二个材料
        if (request.ingredients != null && request.ingredients.length > 1) {
            JsonObject centerSlot = RecipeUtil.createIngredientJson(request.ingredients[1]);
            if (centerSlot != null) {
                recipe.add("center_slot", centerSlot);
            }
        }

        // output
        if (request.result != null) {
            JsonObject output = new JsonObject();
            output.addProperty("item", RecipeUtil.getItemResourceLocation(request.result.getItem()).toString());
            int count = request.resultCount > 1 ? request.resultCount : request.result.getCount();
            if (count > 1) {
                output.addProperty("count", count);
            }
            if (request.result.hasTag()) {
                output.addProperty("nbt", request.result.getTag().toString());
            }
            recipe.add("output", output);
        }

        // experience（整数，默认 0）
        Number exp = (Number) request.properties.get("experience");
        recipe.addProperty("experience", exp != null ? exp.intValue() : 0);

        // repair_cost（整数，默认 0）
        Number repairCost = (Number) request.properties.get("repair_cost");
        recipe.addProperty("repair_cost", repairCost != null ? repairCost.intValue() : 0);

        return recipe;
    }
}