package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.ModLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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

        // 移除 namespace 前缀
        if (recipeType.contains(":")) {
            recipeType = recipeType.substring(recipeType.indexOf(":") + 1);
        }

        ModLogger.getLogger().info("Processing recipe type: {} (original: {})", recipeType, request.recipeType);

        return switch (recipeType) {
            case "shaped", "crafting_shaped" -> createShapedRecipe(request);
            case "shapeless", "crafting_shapeless" -> createShapelessRecipe(request);
            case "smelting" -> createCookingRecipe(request, "minecraft:smelting");
            case "blasting" -> createCookingRecipe(request, "minecraft:blasting");
            case "smoking" -> createCookingRecipe(request, "minecraft:smoking");
            case "campfire_cooking", "campfire" -> createCookingRecipe(request, "minecraft:campfire_cooking");
            case "stonecutting" -> createStonecuttingRecipe(request);
            case "smithing", "smithing_transform" -> createSmithingRecipe(request);
            case "brew", "brewing" -> createBrewingRecipe(request);
            case "anvil" -> createAnvilRecipe(request);
            default -> {
                ModLogger.getLogger().error("Unsupported recipe type: {} (processed as: {})", request.recipeType, recipeType);
                yield null;
            }
        };
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
                "shaped", "crafting_shaped", "shapeless", "crafting_shapeless",
                "smelting", "blasting", "smoking", "campfire_cooking", "campfire",
                "brew", "brewing", "stonecutting", "smithing", "smithing_transform", "anvil"
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

                    JsonObject ingredientJson = createIngredientJson(ingredient,
                            (Boolean) request.properties.getOrDefault("includeNBT", true));
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

        // 添加结果（1.21 格式）
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
                JsonObject ingredientJson = createIngredientJson(ingredient,
                        (Boolean) request.properties.getOrDefault("includeNBT", true));
                if (ingredientJson != null) {
                    ingredientsArray.add(ingredientJson);
                }
            }
            recipe.add("ingredients", ingredientsArray);
        }

        // 添加结果（1.21 格式）
        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    /**
     * 创建烹饪配方（熔炉、高炉、烟熏炉、营火）
     * 在 1.21+ 中，result 使用对象格式而不是字符串
     */
    private JsonObject createCookingRecipe(RecipeRequest request, String type) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        // 添加ingredient
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject ingredientJson = createIngredientJson(request.ingredients[0],
                    (Boolean) request.properties.getOrDefault("includeNBT", true));
            if (ingredientJson != null) {
                recipe.add("ingredient", ingredientJson);
            }
        }

        // 添加结果（1.21+ 使用对象格式和 Data Components）
        recipe.add("result", createResultJson(request.result, request.resultCount));

        // 添加经验和时间
        Number experience = (Number) request.properties.get("experience");
        Integer cookingTime = (Integer) request.properties.get("cookingTime");

        if (experience != null) {
            recipe.addProperty("experience", experience.floatValue());
        } else {
            // 默认经验值
            recipe.addProperty("experience", 0.1f);
        }

        if (cookingTime != null) {
            recipe.addProperty("cookingtime", cookingTime);
        } else {
            // 默认烹饪时间（根据类型）
            int defaultTime = switch (type) {
                case "minecraft:blasting" -> 100;  // 高炉：5秒
                case "minecraft:smoking" -> 100;   // 烟熏炉：5秒
                case "minecraft:campfire_cooking" -> 600; // 营火：30秒
                default -> 200; // 熔炉：10秒
            };
            recipe.addProperty("cookingtime", defaultTime);
        }

        return recipe;
    }

    /**
     * 创建切石机配方
     * 在 1.21+ 中，result 格式有变化
     */
    private JsonObject createStonecuttingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:stonecutting");

        // 输入材料
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject ingredient = createIngredientJson(request.ingredients[0],
                    (Boolean) request.properties.getOrDefault("includeNBT", true));
            if (ingredient != null) {
                recipe.add("ingredient", ingredient);
            }
        }

        // 1.21.1 keeps count and components inside the ItemStack result.
        recipe.add("result", createResultJson(request.result, Math.max(1, request.resultCount)));

        return recipe;
    }

    /**
     * 创建锻造台配方（smithing_transform）
     */
    private JsonObject createSmithingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:smithing_transform");

        // 模板（第一个材料）
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject template = createIngredientJson(request.ingredients[0],
                    (Boolean) request.properties.getOrDefault("includeNBT", true));
            if (template != null) {
                recipe.add("template", template);
            }
        }

        // 基础物品（第二个材料）
        if (request.ingredients != null && request.ingredients.length > 1) {
            JsonObject base = createIngredientJson(request.ingredients[1],
                    (Boolean) request.properties.getOrDefault("includeNBT", true));
            if (base != null) {
                recipe.add("base", base);
            }
        }

        // 添加材料（第三个材料）
        if (request.ingredients != null && request.ingredients.length > 2) {
            JsonObject addition = createIngredientJson(request.ingredients[2],
                    (Boolean) request.properties.getOrDefault("includeNBT", true));
            if (addition != null) {
                recipe.add("addition", addition);
            }
        }

        // 结果（1.21 格式）
        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    /**
     * Creates the registerhelper brewing recipe used by the built-in brewing layout.
     */
    private JsonObject createBrewingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "registerhelper:brewing");

        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject input = createIngredientJson(request.ingredients[0],
                    (Boolean) request.properties.getOrDefault("includeNBT", true));
            if (input != null) {
                recipe.add("input", input);
            }
        }
        if (request.ingredients != null && request.ingredients.length > 1) {
            JsonObject ingredient = createIngredientJson(request.ingredients[1],
                    (Boolean) request.properties.getOrDefault("includeNBT", true));
            if (ingredient != null) {
                recipe.add("ingredient", ingredient);
            }
        }

        recipe.add("output", createResultJson(request.result, request.resultCount));
        return recipe;
    }

    /**
     * Creates the registerhelper anvil recipe used by the built-in anvil layout.
     */
    private JsonObject createAnvilRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "registerhelper:anvil");

        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject left = createIngredientJson(request.ingredients[0],
                    (Boolean) request.properties.getOrDefault("includeNBT", true));
            if (left != null) {
                recipe.add("left", left);
            }
        }
        if (request.ingredients != null && request.ingredients.length > 1) {
            JsonObject right = createIngredientJson(request.ingredients[1],
                    (Boolean) request.properties.getOrDefault("includeNBT", true));
            if (right != null) {
                recipe.add("right", right);
            }
        }

        recipe.add("output", createResultJson(request.result, request.resultCount));
        Integer cost = (Integer) request.properties.get("cost");
        recipe.addProperty("cost", Objects.requireNonNullElse(cost, 1));
        Integer materialCost = (Integer) request.properties.get("material_cost");
        recipe.addProperty("material_cost", Objects.requireNonNullElse(materialCost, 1));
        return recipe;
    }
}
