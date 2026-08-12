package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

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
        String type = request.recipeType.toLowerCase();
        if (type.contains(":")) {
            type = type.substring(type.indexOf(":") + 1);
        }
        return switch (type) {
            case "cutting" -> createCuttingRecipe(request);
            case "cooking" -> createCookingRecipe(request);
            default -> null;
        };
    }

    /**
     * 创建切割配方
     * 格式：
     * {
     *   "type": "farmersdelight:cutting",
     *   "ingredients": [...],
     *   "result": [...],
     *   "tool": { "tag": "c:tools/knife" }
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

        // Farmer's Delight wraps every cutting output in ChanceResult.
        if (request.result != null && !request.result.isEmpty()) {
            int count = request.resultCount > 0 ? request.resultCount : request.result.getCount();
            resultArray.add(createChanceResult(request.result, count,
                    request.properties.get("resultChance")));
        }

        // 额外输出（从properties中获取）
        Object extraResults = request.properties.get("extraResults");
        if (extraResults instanceof ItemStack[] extraStacks) {
            for (ItemStack stack : extraStacks) {
                if (!stack.isEmpty()) {
                    resultArray.add(createChanceResult(stack, stack.getCount(),
                            request.properties.get("chance_" + stack.getItem())));
                }
            }
        } else if (extraResults instanceof Object[] values) {
            for (Object value : values) {
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    resultArray.add(createChanceResult(stack, stack.getCount(),
                            request.properties.get("chance_" + stack.getItem())));
                }
            }
        }

        recipe.add("result", resultArray);

        // The 1.21 tag is the common knife tag, not the old Forge tag.
        JsonObject tool = new JsonObject();
        String toolTag = (String) request.properties.getOrDefault("toolTag", "c:tools/knife");
        if (toolTag.startsWith("#")) {
            toolTag = toolTag.substring(1);
        }
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
            int count = request.resultCount > 0 ? request.resultCount : request.result.getCount();
            recipe.add("result", RecipeUtil.createResultJson(request.result, count));
        }

        // 添加 cookingtime（烹饪时间，默认200 ticks = 10秒）
        Number vv = (Number) request.properties.get("cookingtime");
        if (vv == null) {
            vv = 200;
        }
        int cookingTime = vv.intValue();
        recipe.addProperty("cookingtime", cookingTime);

        // 添加 experience（经验值，默认1.0）
        Number value = (Number) request.properties.get("experience");
        float experience = value != null ? value.floatValue() : 1.0f;
        recipe.addProperty("experience", experience);

        // 添加 recipe_book_tab（配方书标签页，可选）
        String recipeBookTab = (String) request.properties.get("recipe_book_tab");
        if (recipeBookTab != null && !recipeBookTab.isEmpty()) {
            recipe.addProperty("recipe_book_tab", recipeBookTab);
        }

        // Container is an ItemStack, rather than an Ingredient.
        Object container = request.properties.get("container");
        if (container != null) {
            JsonObject containerJson = createStackJson(container);
            if (containerJson != null) {
                recipe.add("container", containerJson);
            }
        }

        return recipe;
    }

    private JsonObject createChanceResult(ItemStack stack, int count, Object chanceValue) {
        JsonObject chanceResult = new JsonObject();
        chanceResult.add("item", RecipeUtil.createResultJson(stack, count));
        if (chanceValue instanceof Number chance && chance.floatValue() < 1.0f) {
            chanceResult.addProperty("chance", chance.floatValue());
        }
        return chanceResult;
    }

    private JsonObject createStackJson(Object value) {
        if (value instanceof ItemStack stack && !stack.isEmpty()) {
            return RecipeUtil.createResultJson(stack, stack.getCount());
        }
        if (value instanceof String id && !id.isBlank() && !id.startsWith("#")) {
            JsonObject stack = new JsonObject();
            stack.addProperty("id", id);
            return stack;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            Object id = map.containsKey("id") ? map.get("id") : map.get("item");
            if (id instanceof String itemId && !itemId.isBlank()) {
                JsonObject stack = new JsonObject();
                stack.addProperty("id", itemId);
                if (map.get("count") instanceof Number count && count.intValue() > 1) {
                    stack.addProperty("count", count.intValue());
                }
                if (map.get("components") instanceof JsonElement components
                        && components.isJsonObject()) {
                    stack.add("components", components.deepCopy());
                }
                return stack;
            }
        }
        return null;
    }
}
