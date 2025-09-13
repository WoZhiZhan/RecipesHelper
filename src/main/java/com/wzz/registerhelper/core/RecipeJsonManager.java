package com.wzz.registerhelper.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.Registerhelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RecipeJsonManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String RECIPES_DIR = "config/recipes";

    public static class RecipeData {
        public String id;
        public String type;
        public String result;
        public int count = 1;
        public String[] pattern;
        public String[] ingredients;
        public Object[] materialMapping;
        public int tier = 0;
        public float experience = 0.0f;
        public int cookingTime = 200;
    }

    static {
        File dir = new File(RECIPES_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            LOGGER.info("创建配方目录: {} - {}", RECIPES_DIR, created ? "成功" : "失败");
        }
    }

    /**
     * 保存配方到JSON文件
     */
    public static void saveRecipe(String recipeId, RecipeData data) {
        try {
            File file = new File(RECIPES_DIR, sanitizeFileName(recipeId) + ".json");
            data.id = recipeId;

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("id", data.id);
            jsonObject.addProperty("type", data.type);
            jsonObject.addProperty("result", data.result);
            jsonObject.addProperty("count", data.count);

            if (data.pattern != null) {
                jsonObject.add("pattern", GSON.toJsonTree(data.pattern));
            }

            if (data.ingredients != null) {
                jsonObject.add("ingredients", GSON.toJsonTree(data.ingredients));
            }

            if (data.materialMapping != null) {
                jsonObject.add("materialMapping", GSON.toJsonTree(data.materialMapping));
            }

            if (data.tier > 0) {
                jsonObject.addProperty("tier", data.tier);
            }

            if (data.experience != 0.0f) {
                jsonObject.addProperty("experience", data.experience);
            }

            if (data.cookingTime != 200) {
                jsonObject.addProperty("cookingTime", data.cookingTime);
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(jsonObject, writer);
            }

            LOGGER.info("配方已保存到JSON: {} -> {}", recipeId, file.getName());

        } catch (IOException e) {
            LOGGER.error("保存配方JSON失败: " + recipeId, e);
        }
    }

    /**
     * 从JSON文件加载配方
     */
    public static RecipeData loadRecipe(String recipeId) {
        try {
            File file = new File(RECIPES_DIR, sanitizeFileName(recipeId) + ".json");
            if (!file.exists()) {
                return null;
            }

            try (FileReader reader = new FileReader(file)) {
                JsonObject jsonObject = GSON.fromJson(reader, JsonObject.class);

                RecipeData data = new RecipeData();
                data.id = jsonObject.has("id") ? jsonObject.get("id").getAsString() : recipeId;
                data.type = jsonObject.get("type").getAsString();
                data.result = jsonObject.get("result").getAsString();
                data.count = jsonObject.has("count") ? jsonObject.get("count").getAsInt() : 1;

                if (jsonObject.has("pattern")) {
                    data.pattern = GSON.fromJson(jsonObject.get("pattern"), String[].class);
                }

                if (jsonObject.has("ingredients")) {
                    data.ingredients = GSON.fromJson(jsonObject.get("ingredients"), String[].class);
                }

                if (jsonObject.has("materialMapping")) {
                    data.materialMapping = GSON.fromJson(jsonObject.get("materialMapping"), Object[].class);
                }

                if (jsonObject.has("tier")) {
                    data.tier = jsonObject.get("tier").getAsInt();
                }

                if (jsonObject.has("experience")) {
                    data.experience = jsonObject.get("experience").getAsFloat();
                }

                if (jsonObject.has("cookingTime")) {
                    data.cookingTime = jsonObject.get("cookingTime").getAsInt();
                }

                return data;
            }

        } catch (Exception e) {
            LOGGER.error("加载配方JSON失败: " + recipeId, e);
            return null;
        }
    }

    /**
     * 删除配方JSON文件
     */
    public static boolean deleteRecipe(String recipeId) {
        try {
            File file = new File(RECIPES_DIR, sanitizeFileName(recipeId) + ".json");
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    LOGGER.info("删除配方JSON文件: {}", recipeId);
                }
                return deleted;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("删除配方JSON文件失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 获取所有保存的配方ID
     */
    public static List<String> getAllSavedRecipeIds() {
        List<String> recipeIds = new ArrayList<>();
        File dir = new File(RECIPES_DIR);

        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    String recipeId = fileName.substring(0, fileName.lastIndexOf('.'));
                    recipeIds.add(recipeId);
                }
            }
        }

        LOGGER.info("找到 {} 个保存的配方文件", recipeIds.size());
        return recipeIds;
    }

    /**
     * 重新加载所有保存的配方到游戏中
     */
    public static void reloadAllSavedRecipes() {
        List<String> recipeIds = getAllSavedRecipeIds();
        int loadedCount = 0;
        int errorCount = 0;

        LOGGER.info("开始重新加载 {} 个配方文件...", recipeIds.size());

        for (String recipeId : recipeIds) {
            try {
                RecipeData data = loadRecipe(recipeId);
                if (data != null) {
                    if (applyRecipeToGame(data)) {
                        loadedCount++;
                        LOGGER.debug("成功重新加载配方: {}", recipeId);
                    } else {
                        errorCount++;
                        LOGGER.warn("重新加载配方失败: {}", recipeId);
                    }
                }
            } catch (Exception e) {
                errorCount++;
                LOGGER.error("重新加载配方时出错: " + recipeId, e);
            }
        }

        if (loadedCount > 0) {
            Registerhelper.getRecipeManager().registerRecipes();
            LOGGER.info("重新加载完成: {} 成功, {} 失败", loadedCount, errorCount);
        } else {
            LOGGER.info("没有找到可加载的配方文件");
        }
    }

    /**
     * 将配方数据应用到游戏中
     */
    private static boolean applyRecipeToGame(RecipeData data) {
        try {
            ResourceLocation resultId = new ResourceLocation(data.result);
            Item resultItem = ForgeRegistries.ITEMS.getValue(resultId);
            if (resultItem == null) {
                LOGGER.error("结果物品不存在: {}", data.result);
                return false;
            }

            ItemStack result = new ItemStack(resultItem, data.count);

            switch (data.type.toLowerCase()) {
                case "shaped":
                    if (data.pattern == null || data.materialMapping == null) {
                        LOGGER.error("有形状配方缺少必需的数据: {}", data.id);
                        return false;
                    }
                    Object[] ingredients = convertStringArrayToItems(data.materialMapping);
                    Registerhelper.getRecipeManager().addShapedRecipe(result, data.pattern, ingredients);
                    break;

                case "shapeless":
                    if (data.ingredients == null) {
                        LOGGER.error("无形状配方缺少材料数据: {}", data.id);
                        return false;
                    }

                    Object[] shapelessIngredients = convertStringArrayToItems(data.ingredients);
                    Registerhelper.getRecipeManager().addShapelessRecipe(result, shapelessIngredients);
                    break;

                case "smelting":
                    if (data.ingredients == null || data.ingredients.length == 0) {
                        LOGGER.error("熔炼配方缺少材料数据: {}", data.id);
                        return false;
                    }

                    ResourceLocation ingredientId = new ResourceLocation(data.ingredients[0]);
                    Item ingredient = ForgeRegistries.ITEMS.getValue(ingredientId);
                    if (ingredient == null) {
                        LOGGER.error("熔炼配方材料不存在: {}", data.ingredients[0]);
                        return false;
                    }

                    Registerhelper.getRecipeManager().addSmeltingRecipe(
                            ingredient, resultItem, data.experience, data.cookingTime);
                    break;

                case "avaritia_shaped":
                    if (data.pattern == null || data.materialMapping == null) {
                        LOGGER.error("Avaritia有形状配方缺少必需的数据: {}", data.id);
                        return false;
                    }

                    Object[] avaritiaIngredients = convertStringArrayToItems(data.materialMapping);
                    Registerhelper.getRecipeManager().addAvaritiaTableRecipe(
                            result, data.tier, data.pattern, avaritiaIngredients);
                    break;

                case "avaritia_shapeless":
                    if (data.ingredients == null) {
                        LOGGER.error("Avaritia无形状配方缺少材料数据: {}", data.id);
                        return false;
                    }

                    List<ItemStack> avaritiaShapelessItems = new ArrayList<>();
                    for (String ingredientStr : data.ingredients) {
                        ResourceLocation ingId = new ResourceLocation(ingredientStr);
                        Item ingItem = ForgeRegistries.ITEMS.getValue(ingId);
                        if (ingItem != null) {
                            avaritiaShapelessItems.add(new ItemStack(ingItem));
                        }
                    }

                    ResourceLocation recipeId = new ResourceLocation(data.id);
                    Registerhelper.getRecipeManager().addAvaritiaShapelessRecipe(
                            recipeId, result, data.tier, avaritiaShapelessItems);
                    break;

                default:
                    LOGGER.error("未知的配方类型: {} (配方: {})", data.type, data.id);
                    return false;
            }

            return true;

        } catch (Exception e) {
            LOGGER.error("应用配方到游戏失败: " + data.id, e);
            return false;
        }
    }

    /**
     * 将字符串数组转换为物品对象数组
     */
    private static Object[] convertStringArrayToItems(Object[] stringArray) {
        if (stringArray == null) return new Object[0];

        Object[] result = new Object[stringArray.length];
        for (int i = 0; i < stringArray.length; i++) {
            if (stringArray[i] instanceof String str) {
                try {
                    ResourceLocation itemId = new ResourceLocation(str);
                    Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    result[i] = item != null ? item : net.minecraft.world.item.Items.AIR;
                } catch (Exception e) {
                    if (str.length() == 1) {
                        result[i] = str.charAt(0); // 字符
                    } else {
                        result[i] = net.minecraft.world.item.Items.AIR;
                    }
                }
            } else {
                result[i] = stringArray[i];
            }
        }
        return result;
    }

    /**
     * 清理文件名中的非法字符
     */
    private static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    /**
     * 检查配方文件是否存在
     */
    public static boolean recipeFileExists(String recipeId) {
        File file = new File(RECIPES_DIR, sanitizeFileName(recipeId) + ".json");
        return file.exists();
    }

    /**
     * 获取配方目录路径
     */
    public static String getRecipesDirectory() {
        return RECIPES_DIR;
    }
}