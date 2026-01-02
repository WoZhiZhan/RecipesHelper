package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.ModLogger;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import static com.wzz.registerhelper.util.RecipeUtil.*;

/**
 * Minecraft原版配方处理器
 */
public class MinecraftRecipeProcessor implements ModRecipeProcessor {

    @Override
    public boolean isModLoaded() {
        return true; // 原版始终存在
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String recipeType = request.recipeType.toLowerCase();
        if (recipeType.contains(":")) {
            recipeType = recipeType.substring(recipeType.indexOf(":") + 1);
        }
        return switch (recipeType) {
            case "shaped", "crafting_shaped" -> createShapedRecipe(request);
            case "shapeless", "crafting_shapeless" -> createShapelessRecipe(request);
            case "smelting" -> createCookingRecipe(request, "minecraft:smelting");
            case "blasting" -> createCookingRecipe(request, "minecraft:blasting");
            case "smoking" -> createCookingRecipe(request, "minecraft:smoking");
            case "campfire_cooking" -> createCookingRecipe(request, "minecraft:campfire_cooking");
            case "stonecutting" -> createStonecuttingRecipe(request);
            case "smithing", "smithing_transform" -> createSmithingRecipe(request);
            case "brew", "brewing" -> createBrewingRecipe(request);
            case "anvil" -> createAnvilRecipe(request);
            default -> {
                ModLogger.getLogger().error("Unsupported recipe type: {}", request.recipeType);
                yield null;
            }
        };
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
                "shaped", "shapeless",
                "smelting", "blasting", "smoking", "campfire_cooking",
                "brew", "stonecutting", "smithing", "anvil"
        };
    }

    private JsonObject createShapedRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");

        // 添加pattern
        if (request.pattern != null) {
            JsonArray patternArray = new JsonArray();
            for (String row : request.pattern) {
                patternArray.add(row);
            }
            recipe.add("pattern", patternArray);
        }

        // 添加key映射
        if (request.ingredients != null) {
            JsonObject key = new JsonObject();
            Map<Character, JsonObject> keyMapping = new HashMap<>();

            for (int i = 0; i < request.ingredients.length; i += 2) {
                if (i + 1 < request.ingredients.length) {
                    char symbol = getCharFromObject(request.ingredients[i]);
                    Object ingredient = request.ingredients[i + 1];

                    JsonObject ingredientJson = createIngredientJson(ingredient);
                    if (ingredientJson != null) {
                        keyMapping.put(symbol, ingredientJson);
                    }
                }
            }

            for (Map.Entry<Character, JsonObject> entry : keyMapping.entrySet()) {
                key.add(String.valueOf(entry.getKey()), entry.getValue());
            }
            recipe.add("key", key);
        }

        // 添加结果
        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    private JsonObject createShapelessRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");

        // 添加ingredients
        if (request.ingredients != null) {
            JsonArray ingredientsArray = new JsonArray();
            for (Object ingredient : request.ingredients) {
                JsonObject ingredientJson = createIngredientJson(ingredient);
                if (ingredientJson != null) {
                    ingredientsArray.add(ingredientJson);
                }
            }
            recipe.add("ingredients", ingredientsArray);
        }

        // 添加结果
        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    private JsonObject createCookingRecipe(RecipeRequest request, String type) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        // 添加ingredient
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject ingredientJson = createIngredientJson(request.ingredients[0]);
            if (ingredientJson != null) {
                recipe.add("ingredient", ingredientJson);
            }
        }

        // 添加结果（使用对象格式以支持count）
        recipe.add("result", createResultJson(request.result, request.resultCount));

        // 添加经验和时间
        Float experience = (Float) request.properties.get("experience");
        Integer cookingTime = (Integer) request.properties.get("cookingTime");

        if (experience != null) {
            recipe.addProperty("experience", experience);
        }
        if (cookingTime != null) {
            recipe.addProperty("cookingtime", cookingTime);
        }

        return recipe;
    }

    private JsonObject createStonecuttingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:stonecutting");

        // 输入材料
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject ingredient = createIngredientJson(request.ingredients[0]);
            if (ingredient != null) {
                recipe.add("ingredient", ingredient);
            }
        }

        // 结果（使用对象格式以支持count）
        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    private JsonObject createSmithingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:smithing_transform");

        // 模板（第一个材料）
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject template = createIngredientJson(request.ingredients[0]);
            if (template != null) {
                recipe.add("template", template);
            }
        }

        // 基础物品（第二个材料）
        if (request.ingredients != null && request.ingredients.length > 1) {
            JsonObject base = createIngredientJson(request.ingredients[1]);
            if (base != null) {
                recipe.add("base", base);
            }
        }

        // 添加材料（第三个材料）
        if (request.ingredients != null && request.ingredients.length > 2) {
            JsonObject addition = createIngredientJson(request.ingredients[2]);
            if (addition != null) {
                recipe.add("addition", addition);
            }
        }

        // 结果
        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    /**
     * 创建酿造台配方（自定义JSON）
     */
    private JsonObject createBrewingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "registerhelper:brewing");

        // 输入（通常是药水）
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject input = createIngredientJson(request.ingredients[0]);
            if (input != null) {
                recipe.add("input", input);
            }
        }

        // 酿造材料（第二个材料）
        if (request.ingredients != null && request.ingredients.length > 1) {
            JsonObject ingredient = createIngredientJson(request.ingredients[1]);
            if (ingredient != null) {
                recipe.add("ingredient", ingredient);
            }
        }

        // 输出
        recipe.add("output", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    /**
     * 创建铁砧配方（自定义JSON）
     */
    private JsonObject createAnvilRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "registerhelper:anvil");

        // 左侧物品
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject left = createIngredientJson(request.ingredients[0]);
            if (left != null) {
                recipe.add("left", left);
            }
        }

        // 右侧物品（材料）
        if (request.ingredients != null && request.ingredients.length > 1) {
            JsonObject right = createIngredientJson(request.ingredients[1]);
            if (right != null) {
                recipe.add("right", right);
            }
        }

        // 输出
        recipe.add("output", createResultJson(request.result, request.resultCount));

        // 经验消耗（从properties中获取）
        Integer cost = (Integer) request.properties.get("cost");
        if (cost != null) {
            recipe.addProperty("cost", cost);
        } else {
            recipe.addProperty("cost", 1);  // 默认1级
        }

        // 材料消耗数量
        Integer materialCost = (Integer) request.properties.get("material_cost");
        if (materialCost != null) {
            recipe.addProperty("material_cost", materialCost);
        } else {
            recipe.addProperty("material_cost", 1);  // 默认消耗1个
        }

        return recipe;
    }
}