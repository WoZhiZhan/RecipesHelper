package com.wzz.registerhelper.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.gui.recipe.IngredientData;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wzz.registerhelper.gui.recipe.RecipeTypeConfig.AvaritiaConfig.getGridSizeForTier;

/**
 * Recipe JSON builder for the built-in and Avaritia recipe layouts.
 */
public class RecipeJsonBuilder {

    /**
     * 创建有序合成配方JSON
     */
    public static JsonObject createShapedRecipe(List<IngredientData> ingredients,
                                                ItemStack result,
                                                int gridWidth,
                                                int gridHeight) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");

        // 生成配方模式
        PatternResult patternResult = generatePattern(ingredients, gridWidth, gridHeight);

        // 添加模式
        JsonArray patternArray = new JsonArray();
        for (String row : patternResult.pattern) {
            patternArray.add(row);
        }
        recipe.add("pattern", patternArray);

        // 添加键值映射
        JsonObject keyObj = new JsonObject();
        for (Map.Entry<Character, IngredientData> entry : patternResult.keyMapping.entrySet()) {
            JsonObject ingredientObj = createIngredientJson(entry.getValue());
            keyObj.add(String.valueOf(entry.getKey()), ingredientObj);
        }
        recipe.add("key", keyObj);

        // 添加结果
        recipe.add("result", createResultJson(result));

        return recipe;
    }

    /**
     * 创建无序合成配方JSON
     */
    public static JsonObject createShapelessRecipe(List<IngredientData> ingredients, ItemStack result) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");

        // 添加材料列表
        JsonArray ingredientsArray = new JsonArray();
        for (IngredientData data : ingredients) {
            if (!data.isEmpty()) {
                ingredientsArray.add(createIngredientJson(data));
            }
        }
        recipe.add("ingredients", ingredientsArray);

        // 添加结果
        recipe.add("result", createResultJson(result));

        return recipe;
    }

    /**
     * 创建烹饪配方JSON（1.21+ 格式）
     */
    public static JsonObject createCookingRecipe(String cookingType,
                                                 IngredientData ingredient,
                                                 ItemStack result,
                                                 float experience,
                                                 int cookingTime) {
        JsonObject recipe = new JsonObject();

        // 设置配方类型
        String type = switch (cookingType) {
            case "blasting" -> "minecraft:blasting";
            case "smoking" -> "minecraft:smoking";
            case "campfire_cooking", "campfire" -> "minecraft:campfire_cooking";
            default -> "minecraft:smelting";
        };
        recipe.addProperty("type", type);

        // 添加材料
        recipe.add("ingredient", createIngredientJson(ingredient));

        JsonObject resultObj = RecipeUtil.createResultJson(result, result.getCount());
        recipe.add("result", resultObj);

        // 添加经验和时间
        recipe.addProperty("experience", experience);
        recipe.addProperty("cookingtime", cookingTime);

        return recipe;
    }

    /**
     * Creates an ingredient using the shared 1.21.1 component policy.
     */
    private static JsonObject createIngredientJson(IngredientData data) {
        return RecipeUtil.createIngredientJson(data);
    }

    /**
     * 创建结果JSON对象
     */
    private static JsonObject createResultJson(ItemStack result) {
        return RecipeUtil.createResultJson(result, result.getCount());
    }

    /**
     * 生成配方模式和键值映射
     */
    private static PatternResult generatePattern(List<IngredientData> ingredients,
                                                 int gridWidth,
                                                 int gridHeight) {
        String[] pattern = new String[gridHeight];
        Map<Character, IngredientData> keyMapping = new HashMap<>();
        Map<String, Character> ingredientToChar = new HashMap<>();
        char currentChar = 'A';

        for (int row = 0; row < gridHeight; row++) {
            StringBuilder rowPattern = new StringBuilder();

            for (int col = 0; col < gridWidth; col++) {
                int index = row * gridWidth + col;

                if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                    IngredientData data = ingredients.get(index);
                    String key = getIngredientKey(data);

                    // 获取或创建该材料的符号
                    Character symbol = ingredientToChar.get(key);
                    if (symbol == null) {
                        symbol = currentChar++;
                        ingredientToChar.put(key, symbol);
                        keyMapping.put(symbol, data);
                    }

                    rowPattern.append(symbol);
                } else {
                    rowPattern.append(' ');
                }
            }

            pattern[row] = rowPattern.toString();
        }

        return new PatternResult(pattern, keyMapping);
    }

    /**
     * 获取材料的唯一键
     * 用于识别相同的材料
     */
    private static String getIngredientKey(IngredientData data) {
        return switch (data.getType()) {
            case ITEM -> RecipeUtil.createIngredientJson(data).toString();
            case TAG, CUSTOM_TAG -> "tag_" + data.getTagId().toString();
        };
    }

    /**
     * 模式生成结果
     */
    private static class PatternResult {
        final String[] pattern;
        final Map<Character, IngredientData> keyMapping;

        PatternResult(String[] pattern, Map<Character, IngredientData> keyMapping) {
            this.pattern = pattern;
            this.keyMapping = keyMapping;
        }
    }

    /**
     * 为Avaritia创建配方JSON
     */
    public static JsonObject createAvaritiaRecipe(String mode,
                                                  List<IngredientData> ingredients,
                                                  ItemStack result,
                                                  int tier) {
        JsonObject recipe = new JsonObject();

        // Avaritia配方类型
        String recipeType = mode.equals("shaped") ?
                "avaritia:shaped_table" : "avaritia:shapeless_table";
        recipe.addProperty("type", recipeType);

        if (mode.equals("shaped")) {
            // 有序配方
            int gridSize = getAvaritiaGridSize(tier);
            PatternResult patternResult = generatePattern(ingredients, gridSize, gridSize);

            JsonArray patternArray = new JsonArray();
            for (String row : patternResult.pattern) {
                patternArray.add(row);
            }
            recipe.add("pattern", patternArray);

            JsonObject keyObj = new JsonObject();
            for (Map.Entry<Character, IngredientData> entry : patternResult.keyMapping.entrySet()) {
                keyObj.add(String.valueOf(entry.getKey()), createIngredientJson(entry.getValue()));
            }
            recipe.add("key", keyObj);
        } else {
            // 无序配方
            JsonArray ingredientsArray = new JsonArray();
            for (IngredientData data : ingredients) {
                if (!data.isEmpty()) {
                    ingredientsArray.add(createIngredientJson(data));
                }
            }
            recipe.add("ingredients", ingredientsArray);
        }

        // 结果
        recipe.add("result", createResultJson(result));

        return recipe;
    }

    /**
     * 获取Avaritia网格大小
     */
    private static int getAvaritiaGridSize(int tier) {
        return getGridSizeForTier(tier);
    }
}
