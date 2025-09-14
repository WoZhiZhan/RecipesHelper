package com.wzz.registerhelper.core;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * RecipeJsonManager的扩展，专门处理配方覆盖相关功能
 * 与现有的RecipeJsonManager类完美集成
 */
public class RecipeJsonManagerExtension {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 为现有配方创建覆盖模板
     * @param recipeId 配方ID
     * @param originalRecipe 原始配方
     * @return 是否创建成功
     */
    public static boolean createOverrideTemplate(String recipeId, Recipe<?> originalRecipe) {
        try {
            RecipeJsonManager.RecipeData data = new RecipeJsonManager.RecipeData();
            data.id = recipeId;

            // 获取输出物品
            ItemStack resultItem = ItemStack.EMPTY;
            try {
                resultItem = originalRecipe.getResultItem(null);
            } catch (Exception e) {
                LOGGER.warn("获取配方输出失败: {}", recipeId);
            }

            String recipeTypeName = originalRecipe.getType().toString().toLowerCase();

            if (recipeTypeName.contains("crafting_shaped")) {
                return createShapedOverrideTemplate(data, originalRecipe, resultItem);
            } else if (recipeTypeName.contains("crafting_shapeless")) {
                return createShapelessOverrideTemplate(data, originalRecipe, resultItem);
            } else if (recipeTypeName.contains("smelting") || recipeTypeName.contains("blasting") ||
                    recipeTypeName.contains("smoking") || recipeTypeName.contains("campfire")) {
                return createSmeltingOverrideTemplate(data, originalRecipe, resultItem);
            } else if (recipeTypeName.contains("avaritia")) {
                if (recipeTypeName.contains("shaped")) {
                    return createAvaritiaShapedOverrideTemplate(data, originalRecipe, resultItem);
                } else {
                    return createAvaritiaShapelessOverrideTemplate(data, originalRecipe, resultItem);
                }
            } else {
                // 未知类型，创建基础模板
                return createGenericOverrideTemplate(data, originalRecipe, resultItem);
            }

        } catch (Exception e) {
            LOGGER.error("创建配方覆盖模板失败: " + recipeId, e);
            return false;
        }
    }

    private static boolean createShapedOverrideTemplate(RecipeJsonManager.RecipeData data, Recipe<?> originalRecipe, ItemStack resultItem) {
        try {
            data.type = "shaped";

            // 尝试从原配方获取模式和材料映射
            List<Ingredient> ingredients = originalRecipe.getIngredients();

            // 创建3x3模式模板
            data.pattern = new String[]{
                    "ABC",
                    "DEF",
                    "GHI"
            };

            // 创建材料映射模板
            List<Object> materialMappingList = new ArrayList<>();
            char currentChar = 'A';

            for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
                Ingredient ingredient = ingredients.get(i);
                if (!ingredient.isEmpty()) {
                    ItemStack[] items = ingredient.getItems();
                    if (items.length > 0) {
                        materialMappingList.add(currentChar);
                        materialMappingList.add(items[0].getItem());
                        currentChar++;
                    }
                }
            }

            data.materialMapping = materialMappingList.toArray();

            return RecipeJsonManager.saveRecipe(data.id, data, resultItem, null);

        } catch (Exception e) {
            LOGGER.error("创建有序配方覆盖模板失败", e);
            return false;
        }
    }

    private static boolean createShapelessOverrideTemplate(RecipeJsonManager.RecipeData data, Recipe<?> originalRecipe, ItemStack resultItem) {
        try {
            data.type = "shapeless";

            List<Ingredient> ingredients = originalRecipe.getIngredients();
            List<String> materialStrings = new ArrayList<>();

            for (Ingredient ingredient : ingredients) {
                if (!ingredient.isEmpty()) {
                    ItemStack[] items = ingredient.getItems();
                    if (items.length > 0) {
                        materialStrings.add(items[0].getItem().toString());
                    }
                }
            }

            data.ingredients = materialStrings.toArray(new String[0]);

            return RecipeJsonManager.saveRecipe(data.id, data, resultItem, null);

        } catch (Exception e) {
            LOGGER.error("创建无序配方覆盖模板失败", e);
            return false;
        }
    }

    private static boolean createSmeltingOverrideTemplate(RecipeJsonManager.RecipeData data, Recipe<?> originalRecipe, ItemStack resultItem) {
        try {
            data.type = "smelting";
            data.experience = 0.7f;
            data.cookingTime = 200;

            List<Ingredient> ingredients = originalRecipe.getIngredients();
            if (!ingredients.isEmpty() && !ingredients.get(0).isEmpty()) {
                ItemStack[] items = ingredients.get(0).getItems();
                if (items.length > 0) {
                    data.ingredients = new String[]{items[0].getItem().toString()};
                }
            }

            return RecipeJsonManager.saveRecipe(data.id, data, resultItem, null);

        } catch (Exception e) {
            LOGGER.error("创建熔炼配方覆盖模板失败", e);
            return false;
        }
    }

    private static boolean createAvaritiaShapedOverrideTemplate(RecipeJsonManager.RecipeData data, Recipe<?> originalRecipe, ItemStack resultItem) {
        try {
            data.type = "avaritia_shaped";

            List<Ingredient> ingredients = originalRecipe.getIngredients();
            int gridSize = getGridSizeFromIngredientCount(ingredients.size());
            data.tier = getAvaritiaTeierFromGridSize(gridSize);

            // 创建动态大小的模式
            String[] pattern = new String[gridSize];
            char currentChar = 'A';

            for (int y = 0; y < gridSize; y++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < gridSize; x++) {
                    row.append(currentChar++);
                }
                pattern[y] = row.toString();
            }

            data.pattern = pattern;

            // 创建材料映射
            List<Object> materialMappingList = new ArrayList<>();
            currentChar = 'A';

            for (Ingredient ingredient : ingredients) {
                if (!ingredient.isEmpty()) {
                    ItemStack[] items = ingredient.getItems();
                    if (items.length > 0) {
                        materialMappingList.add(currentChar);
                        materialMappingList.add(items[0].getItem());
                        currentChar++;
                    }
                }
            }

            data.materialMapping = materialMappingList.toArray();

            return RecipeJsonManager.saveRecipe(data.id, data, resultItem, null);

        } catch (Exception e) {
            LOGGER.error("创建Avaritia有序配方覆盖模板失败", e);
            return false;
        }
    }

    private static boolean createAvaritiaShapelessOverrideTemplate(RecipeJsonManager.RecipeData data, Recipe<?> originalRecipe, ItemStack resultItem) {
        try {
            data.type = "avaritia_shapeless";

            List<Ingredient> ingredients = originalRecipe.getIngredients();
            data.tier = getAvaritiaTeierFromIngredientCount(ingredients.size());

            List<String> materialStrings = new ArrayList<>();
            for (Ingredient ingredient : ingredients) {
                if (!ingredient.isEmpty()) {
                    ItemStack[] items = ingredient.getItems();
                    if (items.length > 0) {
                        materialStrings.add(items[0].getItem().toString());
                    }
                }
            }

            data.ingredients = materialStrings.toArray(new String[0]);

            return RecipeJsonManager.saveRecipe(data.id, data, resultItem, null);

        } catch (Exception e) {
            LOGGER.error("创建Avaritia无序配方覆盖模板失败", e);
            return false;
        }
    }

    private static boolean createGenericOverrideTemplate(RecipeJsonManager.RecipeData data, Recipe<?> originalRecipe, ItemStack resultItem) {
        try {
            data.type = "unknown";

            return RecipeJsonManager.saveRecipe(data.id, data, resultItem, null);

        } catch (Exception e) {
            LOGGER.error("创建通用配方覆盖模板失败", e);
            return false;
        }
    }

    private static int getGridSizeFromIngredientCount(int ingredientCount) {
        if (ingredientCount <= 9) return 3;
        if (ingredientCount <= 25) return 5;
        if (ingredientCount <= 49) return 7;
        return 9;
    }

    private static int getAvaritiaTeierFromGridSize(int gridSize) {
        return switch (gridSize) {
            case 3 -> 1;
            case 5 -> 2;
            case 7 -> 3;
            case 9 -> 4;
            default -> 1;
        };
    }

    private static int getAvaritiaTeierFromIngredientCount(int ingredientCount) {
        if (ingredientCount <= 9) return 1;
        if (ingredientCount <= 25) return 2;
        if (ingredientCount <= 49) return 3;
        return 4;
    }

    /**
     * 检查是否存在配方覆盖文件
     * @param recipeId 配方ID
     * @return 是否存在覆盖文件
     */
    public static boolean hasOverrideFile(String recipeId) {
        return RecipeJsonManager.recipeFileExists(recipeId);
    }

    /**
     * 获取配方覆盖文件的路径
     * @param recipeId 配方ID
     * @return 文件路径字符串
     */
    public static String getOverrideFilePath(String recipeId) {
        ResourceLocation id = new ResourceLocation(recipeId);
        return RecipeJsonManager.getRecipesDirectory() + "/" + sanitizeFileName(recipeId) + ".json";
    }

    /**
     * 清理文件名中的非法字符（与RecipeJsonManager保持一致）
     */
    private static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    /**
     * 批量创建覆盖模板
     * @param recipeIds 配方ID列表
     * @param originalRecipes 对应的原始配方列表
     * @return 成功创建的数量
     */
    public static int createBatchOverrideTemplates(List<String> recipeIds, List<Recipe<?>> originalRecipes) {
        if (recipeIds.size() != originalRecipes.size()) {
            LOGGER.error("配方ID列表和原始配方列表大小不匹配");
            return 0;
        }

        int successCount = 0;
        for (int i = 0; i < recipeIds.size(); i++) {
            try {
                if (createOverrideTemplate(recipeIds.get(i), originalRecipes.get(i))) {
                    successCount++;
                }
            } catch (Exception e) {
                LOGGER.error("批量创建覆盖模板时出错: " + recipeIds.get(i), e);
            }
        }

        LOGGER.info("批量创建覆盖模板完成: {}/{} 成功", successCount, recipeIds.size());
        return successCount;
    }
}