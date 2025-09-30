package com.wzz.registerhelper.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用配方管理器 - 负责所有mod的配方JSON生成和管理
 */
public class UniversalRecipeManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String RECIPES_DIR = "config/registerhelper/recipes";
    
    private static final Map<String, ModRecipeProcessor> processors = new HashMap<>();
    private static UniversalRecipeManager instance;
    
    static {
        File dir = new File(RECIPES_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            LOGGER.info("创建配方目录: {} - {}", RECIPES_DIR, created ? "成功" : "失败");
        }
    }
    
    public static UniversalRecipeManager getInstance() {
        if (instance == null) {
            instance = new UniversalRecipeManager();
        }
        return instance;
    }
    
    /**
     * 注册mod配方处理器
     */
    public static void registerProcessor(String modId, ModRecipeProcessor processor) {
        processors.put(modId, processor);
    }
    
    /**
     * 创建配方JSON文件
     */
    public boolean createRecipe(RecipeRequest request) {
        try {
            // 验证请求
            if (!validateRequest(request)) {
                return false;
            }
            
            // 获取对应的处理器
            ModRecipeProcessor processor = processors.get(request.modId);
            if (processor == null) {
                LOGGER.error("未找到mod处理器: {}", request.modId);
                return false;
            }
            
            // 检查mod是否加载
            if (!processor.isModLoaded()) {
                LOGGER.warn("Mod {} 未加载，跳过配方创建", request.modId);
                return false;
            }
            
            // 生成JSON
            JsonObject recipeJson = processor.createRecipeJson(request);
            if (recipeJson == null) {
                LOGGER.error("处理器无法创建配方JSON: {}", request.recipeId);
                return false;
            }
            
            // 保存文件
            return saveRecipeFile(request.modId, request.recipeId, recipeJson);
            
        } catch (Exception e) {
            LOGGER.error("创建配方失败: " + request.recipeId, e);
            return false;
        }
    }
    
    /**
     * 删除配方文件
     */
    public boolean deleteRecipe(String modId, String recipeId) {
        try {
            File recipeFile = getRecipeFile(modId, recipeId);
            if (recipeFile.exists()) {
                boolean deleted = recipeFile.delete();
                if (deleted) {
                    LOGGER.info("删除配方: {} / {}", modId, recipeId);
                }
                return deleted;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("删除配方失败: " + modId + "/" + recipeId, e);
            return false;
        }
    }
    
    /**
     * 检查配方文件是否存在
     */
    public boolean recipeExists(String modId, String recipeId) {
        return getRecipeFile(modId, recipeId).exists();
    }

    /**
     * 验证配方请求
     */
    private boolean validateRequest(RecipeRequest request) {
        if (request == null) {
            LOGGER.error("配方请求为空");
            return false;
        }
        
        if (request.modId == null || request.modId.isEmpty()) {
            LOGGER.error("Mod ID不能为空");
            return false;
        }
        
        if (request.recipeType == null || request.recipeType.isEmpty()) {
            LOGGER.error("配方类型不能为空");
            return false;
        }
        
        if (request.result == null || request.result.isEmpty()) {
            LOGGER.error("结果物品不能为空");
            return false;
        }
        
        return true;
    }
    
    /**
     * 保存配方文件
     */
    private boolean saveRecipeFile(String modId, String recipeId, JsonObject recipeJson) {
        try {
            File recipeFile = getRecipeFile(modId, recipeId);
            
            // 确保父目录存在
            File parentDir = recipeFile.getParentFile();
            if (!parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                LOGGER.debug("创建目录: {} - {}", parentDir.getPath(), created ? "成功" : "失败");
            }
            
            // 写入文件
            try (FileWriter writer = new FileWriter(recipeFile)) {
                GSON.toJson(recipeJson, writer);
            }
            
            LOGGER.info("配方已保存: {} -> {}", recipeId, recipeFile.getPath());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("保存配方文件失败: " + modId + "/" + recipeId, e);
            return false;
        }
    }
    
    /**
     * 获取配方文件路径
     */
    private File getRecipeFile(String modId, String recipeId) {
        String fileName = sanitizeFileName(recipeId) + ".json";
        return new File(RECIPES_DIR, modId + File.separator + fileName);
    }

    /**
     * 清理文件名
     */
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[<>:\"/\\\\|?*]", "_");
    }
}