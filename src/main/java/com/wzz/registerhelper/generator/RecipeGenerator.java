package com.wzz.registerhelper.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 通用配方生成器 - 支持各种mod和配方格式
 */
public class RecipeGenerator {

    /**
     * 配方模式枚举
     */
    public enum RecipeMode {
        SHAPED_3X3("3x3有序", 3),
        SHAPELESS_3X3("3x3无序", 3),
        SHAPED_5X5("5x5有序", 5),
        SHAPELESS_5X5("5x5无序", 5),
        SHAPED_7X7("7x7有序", 7),
        SHAPELESS_7X7("7x7无序", 7),
        SHAPED_9X9("9x9有序", 9),
        SHAPELESS_9X9("9x9无序", 9),
        SMELTING("熔炉", 1),
        BLASTING("高炉", 1),
        SMOKING("烟熏炉", 1),
        CAMPFIRE("营火", 1),
        STONECUTTING("切石机", 1),
        BREWING("酿造台", 2),
        SMITHING("锻造台", 3);

        private final String displayName;
        private final int gridSize;

        RecipeMode(String displayName, int gridSize) {
            this.displayName = displayName;
            this.gridSize = gridSize;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getGridSize() {
            return gridSize;
        }

        public boolean isShaped() {
            return name().contains("SHAPED");
        }

        public boolean isCooking() {
            return name().matches(".*(SMELTING|BLASTING|SMOKING|CAMPFIRE).*");
        }

        public boolean isSpecialCrafting() {
            return name().matches(".*(STONECUTTING|BREWING|SMITHING).*");
        }

        public boolean isAvaritia() {
            return gridSize > 3;
        }
    }

    /**
     * 配方数据类
     */
    public static class RecipeData {
        public String modId;
        public String recipeId;
        public RecipeMode mode;
        public ItemStack result;
        public int resultCount = 1;

        // 有序配方数据
        public String[] pattern;
        public Map<Character, ItemStack> symbolMapping = new HashMap<>();

        // 无序配方数据
        public List<ItemStack> ingredients = new ArrayList<>();

        // 烹饪配方数据
        public ItemStack cookingIngredient;
        public float experience = 0.0f;
        public int cookingTime = 200;

        // 扩展属性
        public Map<String, Object> properties = new HashMap<>();

        public RecipeData(String modId, String recipeId, RecipeMode mode, ItemStack result) {
            this.modId = modId;
            this.recipeId = recipeId;
            this.mode = mode;
            this.result = result;
            this.resultCount = result.getCount();
        }
    }

    /**
     * 从GUI数据构建配方数据
     */
    public static RecipeData buildFromGrid(String modId, String recipeId, RecipeMode mode,
                                           ItemStack result, List<ItemStack> gridItems) {
        RecipeData data = new RecipeData(modId, recipeId, mode, result);

        if (mode.isCooking()) {
            buildCookingData(data, gridItems);
        } else if (mode.isShaped()) {
            buildShapedData(data, gridItems);
        } else {
            buildShapelessData(data, gridItems);
        }

        return data;
    }

    /**
     * 构建烹饪配方数据
     */
    private static void buildCookingData(RecipeData data, List<ItemStack> gridItems) {
        if (!gridItems.isEmpty() && !gridItems.get(0).isEmpty()) {
            data.cookingIngredient = gridItems.get(0);
        }
    }

    /**
     * 构建有序配方数据
     */
    private static void buildShapedData(RecipeData data, List<ItemStack> gridItems) {
        int gridSize = data.mode.getGridSize();

        // 1. 收集唯一物品并分配符号
        Map<ItemStack, Character> itemToSymbol = new HashMap<>();
        char currentSymbol = 'A';

        for (ItemStack item : gridItems) {
            if (!item.isEmpty()) {
                boolean found = false;
                for (ItemStack existing : itemToSymbol.keySet()) {
                    if (ItemStack.isSameItemSameTags(item, existing)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    itemToSymbol.put(item.copy(), currentSymbol++);
                }
            }
        }

        // 2. 构建pattern
        data.pattern = new String[gridSize];
        for (int y = 0; y < gridSize; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < gridSize; x++) {
                int index = y * gridSize + x;
                if (index < gridItems.size() && !gridItems.get(index).isEmpty()) {
                    ItemStack item = gridItems.get(index);
                    Character symbol = findSymbolForItem(item, itemToSymbol);
                    row.append(symbol != null ? symbol : ' ');
                } else {
                    row.append(' ');
                }
            }
            data.pattern[y] = row.toString();
        }

        // 3. 构建符号映射
        data.symbolMapping.clear();
        for (Map.Entry<ItemStack, Character> entry : itemToSymbol.entrySet()) {
            data.symbolMapping.put(entry.getValue(), entry.getKey());
        }
    }

    /**
     * 构建无序配方数据
     */
    private static void buildShapelessData(RecipeData data, List<ItemStack> gridItems) {
        data.ingredients.clear();
        for (ItemStack item : gridItems) {
            if (!item.isEmpty()) {
                data.ingredients.add(item.copy());
            }
        }
    }

    /**
     * 查找物品对应的符号
     */
    private static Character findSymbolForItem(ItemStack target, Map<ItemStack, Character> itemToSymbol) {
        for (Map.Entry<ItemStack, Character> entry : itemToSymbol.entrySet()) {
            if (ItemStack.isSameItemSameTags(target, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 生成Minecraft原版配方JSON
     */
    public static JsonObject generateMinecraftRecipe(RecipeData data) {
        JsonObject recipe = new JsonObject();

        if (data.mode.isCooking()) {
            return generateCookingRecipe(data);
        } else if (data.mode.isShaped()) {
            return generateShapedCraftingRecipe(data);
        } else {
            return generateShapelessCraftingRecipe(data);
        }
    }

    /**
     * 生成Avaritia配方JSON
     */
    public static JsonObject generateAvaritiaRecipe(RecipeData data) {
        JsonObject recipe = new JsonObject();

        if (data.mode.isShaped()) {
            recipe.addProperty("type", "avaritia:shaped_table");

            // 添加tier
            int tier = getTierFromGridSize(data.mode.getGridSize());
            recipe.addProperty("tier", tier);

            // 添加pattern
            JsonArray patternArray = new JsonArray();
            for (String row : data.pattern) {
                patternArray.add(row);
            }
            recipe.add("pattern", patternArray);

            // 添加key
            JsonObject key = new JsonObject();
            for (Map.Entry<Character, ItemStack> entry : data.symbolMapping.entrySet()) {
                JsonObject itemJson = new JsonObject();
                itemJson.addProperty("item", ForgeRegistries.ITEMS.getKey(entry.getValue().getItem()).toString());
                key.add(String.valueOf(entry.getKey()), itemJson);
            }
            recipe.add("key", key);

        } else {
            recipe.addProperty("type", "avaritia:shapeless_table");

            // 添加tier
            int tier = getTierFromIngredientCount(data.ingredients.size());
            recipe.addProperty("tier", tier);

            // 添加ingredients
            JsonArray ingredientsArray = new JsonArray();
            for (ItemStack ingredient : data.ingredients) {
                JsonObject itemJson = new JsonObject();
                itemJson.addProperty("item", ForgeRegistries.ITEMS.getKey(ingredient.getItem()).toString());
                ingredientsArray.add(itemJson);
            }
            recipe.add("ingredients", ingredientsArray);
        }

        // 添加result
        JsonObject resultJson = new JsonObject();
        resultJson.addProperty("item", ForgeRegistries.ITEMS.getKey(data.result.getItem()).toString());
        if (data.resultCount > 1) {
            resultJson.addProperty("count", data.resultCount);
        }
        recipe.add("result", resultJson);

        return recipe;
    }

    /**
     * 生成有序工作台配方
     */
    private static JsonObject generateShapedCraftingRecipe(RecipeData data) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shaped");

        // 添加pattern
        JsonArray patternArray = new JsonArray();
        for (String row : data.pattern) {
            patternArray.add(row);
        }
        recipe.add("pattern", patternArray);

        // 添加key
        JsonObject key = new JsonObject();
        for (Map.Entry<Character, ItemStack> entry : data.symbolMapping.entrySet()) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("item", ForgeRegistries.ITEMS.getKey(entry.getValue().getItem()).toString());
            key.add(String.valueOf(entry.getKey()), itemJson);
        }
        recipe.add("key", key);

        // 添加result
        JsonObject resultJson = new JsonObject();
        resultJson.addProperty("item", ForgeRegistries.ITEMS.getKey(data.result.getItem()).toString());
        if (data.resultCount > 1) {
            resultJson.addProperty("count", data.resultCount);
        }
        recipe.add("result", resultJson);

        return recipe;
    }

    /**
     * 生成无序工作台配方
     */
    private static JsonObject generateShapelessCraftingRecipe(RecipeData data) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "minecraft:crafting_shapeless");

        // 添加ingredients
        JsonArray ingredientsArray = new JsonArray();
        for (ItemStack ingredient : data.ingredients) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("item", ForgeRegistries.ITEMS.getKey(ingredient.getItem()).toString());
            ingredientsArray.add(itemJson);
        }
        recipe.add("ingredients", ingredientsArray);

        // 添加result
        JsonObject resultJson = new JsonObject();
        resultJson.addProperty("item", ForgeRegistries.ITEMS.getKey(data.result.getItem()).toString());
        if (data.resultCount > 1) {
            resultJson.addProperty("count", data.resultCount);
        }
        recipe.add("result", resultJson);

        return recipe;
    }

    /**
     * 生成烹饪配方
     */
    private static JsonObject generateCookingRecipe(RecipeData data) {
        JsonObject recipe = new JsonObject();

        String type = switch (data.mode) {
            case SMELTING -> "minecraft:smelting";
            case BLASTING -> "minecraft:blasting";
            case SMOKING -> "minecraft:smoking";
            case CAMPFIRE -> "minecraft:campfire_cooking";
            default -> "minecraft:smelting";
        };
        recipe.addProperty("type", type);

        // 添加ingredient
        JsonObject ingredientJson = new JsonObject();
        ingredientJson.addProperty("item", ForgeRegistries.ITEMS.getKey(data.cookingIngredient.getItem()).toString());
        recipe.add("ingredient", ingredientJson);

        // 添加result
        recipe.addProperty("result", ForgeRegistries.ITEMS.getKey(data.result.getItem()).toString());

        // 添加experience和cookingtime
        recipe.addProperty("experience", data.experience);
        recipe.addProperty("cookingtime", data.cookingTime);

        return recipe;
    }

    /**
     * 根据网格大小获取tier
     */
    public static int getTierFromGridSize(int gridSize) {
        return switch (gridSize) {
            case 3 -> 1;
            case 5 -> 2;
            case 7 -> 3;
            case 9 -> 4;
            default -> 4;
        };
    }

    /**
     * 根据材料数量获取tier
     */
    public static int getTierFromIngredientCount(int count) {
        if (count <= 9) return 1;
        if (count <= 25) return 2;
        if (count <= 49) return 3;
        return 4;
    }

    /**
     * 生成配方ID
     */
    public static String generateRecipeId(String modId, RecipeMode mode, ItemStack result) {
        String itemName = ForgeRegistries.ITEMS.getKey(result.getItem()).getPath();
        String modeString = mode.name().toLowerCase();
        long timestamp = System.currentTimeMillis();
        return modId + ":custom_" + modeString + "_" + itemName + "_" + timestamp;
    }

    /**
     * 验证配方数据
     */
    public static boolean validateRecipeData(RecipeData data) {
        if (data.result == null || data.result.isEmpty()) {
            return false;
        }

        if (data.mode.isCooking()) {
            return data.cookingIngredient != null && !data.cookingIngredient.isEmpty();
        } else if (data.mode.isShaped()) {
            return data.pattern != null && data.symbolMapping != null && !data.symbolMapping.isEmpty();
        } else {
            return data.ingredients != null && !data.ingredients.isEmpty();
        }
    }
}