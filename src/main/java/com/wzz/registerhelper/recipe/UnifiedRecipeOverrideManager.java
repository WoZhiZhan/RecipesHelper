package com.wzz.registerhelper.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一的配方覆盖管理器 - 在内存中管理配方覆盖规则
 */
public class UnifiedRecipeOverrideManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OVERRIDES_FILE = "config/registerhelper/recipe_overrides.json";
    
    // 内存中的覆盖映射：原配方ID -> 覆盖JSON
    private static final Map<ResourceLocation, JsonObject> recipeOverrides = new ConcurrentHashMap<>();
    private static boolean initialized = false;
    
    /**
     * 覆盖数据类
     */
    public static class OverrideData {
        public final ResourceLocation originalId;
        public final JsonObject overrideJson;
        public final long timestamp;
        public final String description;
        
        public OverrideData(ResourceLocation originalId, JsonObject overrideJson, String description) {
            this.originalId = originalId;
            this.overrideJson = overrideJson;
            this.timestamp = System.currentTimeMillis();
            this.description = description;
        }
    }
    
    /**
     * 初始化覆盖管理器
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            loadOverrides();
            initialized = true;
            LOGGER.info("配方覆盖管理器已初始化，覆盖配方数量: {}", recipeOverrides.size());
        } catch (Exception e) {
            LOGGER.error("初始化配方覆盖管理器失败", e);
            initialized = true;
        }
    }
    
    /**
     * 添加配方覆盖
     */
    public static boolean addOverride(ResourceLocation recipeId, JsonObject overrideJson) {
        try {
            initialize();
            
            recipeOverrides.put(recipeId, overrideJson.deepCopy());
            saveOverrides();
            
            LOGGER.info("配方覆盖已添加: {}", recipeId);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("添加配方覆盖失败: " + recipeId, e);
            return false;
        }
    }
    
    /**
     * 移除配方覆盖
     */
    public static boolean removeOverride(ResourceLocation recipeId) {
        try {
            initialize();
            
            JsonObject removed = recipeOverrides.remove(recipeId);
            if (removed != null) {
                saveOverrides();
                LOGGER.info("配方覆盖已移除: {}", recipeId);
                return true;
            } else {
                LOGGER.warn("配方覆盖不存在: {}", recipeId);
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.error("移除配方覆盖失败: " + recipeId, e);
            return false;
        }
    }
    
    /**
     * 检查配方是否有覆盖
     */
    public static boolean hasOverride(ResourceLocation recipeId) {
        initialize();
        return recipeOverrides.containsKey(recipeId);
    }
    
    /**
     * 获取配方覆盖
     */
    public static JsonObject getOverride(ResourceLocation recipeId) {
        initialize();
        JsonObject override = recipeOverrides.get(recipeId);
        return override != null ? override.deepCopy() : null;
    }
    
    /**
     * 获取所有覆盖配方
     */
    public static Map<ResourceLocation, JsonObject> getAllOverrides() {
        initialize();
        Map<ResourceLocation, JsonObject> result = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : recipeOverrides.entrySet()) {
            result.put(entry.getKey(), entry.getValue().deepCopy());
        }
        return result;
    }
    
    /**
     * 获取覆盖配方ID集合
     */
    public static Set<ResourceLocation> getOverriddenRecipeIds() {
        initialize();
        return Set.copyOf(recipeOverrides.keySet());
    }
    
    /**
     * 清空所有覆盖
     */
    public static boolean clearAllOverrides() {
        try {
            initialize();
            
            int count = recipeOverrides.size();
            recipeOverrides.clear();
            saveOverrides();
            
            LOGGER.info("已清空所有配方覆盖，移除了 {} 个覆盖", count);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("清空配方覆盖失败", e);
            return false;
        }
    }
    
    /**
     * 批量添加覆盖
     */
    public static int addMultipleOverrides(Map<ResourceLocation, JsonObject> overrides) {
        initialize();
        
        int addedCount = 0;
        for (Map.Entry<ResourceLocation, JsonObject> entry : overrides.entrySet()) {
            recipeOverrides.put(entry.getKey(), entry.getValue().deepCopy());
            addedCount++;
        }
        
        if (addedCount > 0) {
            saveOverrides();
            LOGGER.info("批量添加 {} 个配方覆盖", addedCount);
        }
        
        return addedCount;
    }
    
    /**
     * 批量移除覆盖
     */
    public static int removeMultipleOverrides(Set<ResourceLocation> recipeIds) {
        initialize();
        
        int removedCount = 0;
        for (ResourceLocation recipeId : recipeIds) {
            if (recipeOverrides.remove(recipeId) != null) {
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            saveOverrides();
            LOGGER.info("批量移除 {} 个配方覆盖", removedCount);
        }
        
        return removedCount;
    }
    
    /**
     * 获取覆盖统计信息
     */
    public static OverrideStats getStats() {
        initialize();
        
        OverrideStats stats = new OverrideStats();
        stats.totalOverrides = recipeOverrides.size();
        
        // 按命名空间分组统计
        for (ResourceLocation recipeId : recipeOverrides.keySet()) {
            String namespace = recipeId.getNamespace();
            stats.byNamespace.merge(namespace, 1, Integer::sum);
        }
        
        return stats;
    }
    
    /**
     * 应用覆盖到配方映射（供Mixin使用）
     */
    public static void applyOverridesToRecipeMap(Map<ResourceLocation, JsonElement> recipes) {
        initialize();
        
        if (recipeOverrides.isEmpty()) {
            return;
        }
        
        int appliedCount = 0;
        for (Map.Entry<ResourceLocation, JsonObject> entry : recipeOverrides.entrySet()) {
            ResourceLocation recipeId = entry.getKey();
            JsonObject overrideJson = entry.getValue();
            
            if (recipes.containsKey(recipeId)) {
                recipes.put(recipeId, overrideJson.deepCopy());
                appliedCount++;
                LOGGER.debug("应用配方覆盖: {}", recipeId);
            } else {
                LOGGER.debug("原配方不存在，跳过覆盖: {}", recipeId);
            }
        }
        
        if (appliedCount > 0) {
            LOGGER.info("已应用 {} 个配方覆盖", appliedCount);
        }
    }
    
    /**
     * 从文件加载覆盖
     */
    private static void loadOverrides() {
        File overridesFile = new File(OVERRIDES_FILE);
        
        if (!overridesFile.exists()) {
            LOGGER.debug("覆盖文件不存在，创建空覆盖映射: {}", OVERRIDES_FILE);
            recipeOverrides.clear();
            saveOverrides();
            return;
        }
        
        try (FileReader reader = new FileReader(overridesFile, StandardCharsets.UTF_8)) {
            Type mapType = new TypeToken<Map<String, JsonObject>>() {}.getType();
            Map<String, JsonObject> overrideMap = GSON.fromJson(reader, mapType);
            
            recipeOverrides.clear();
            if (overrideMap != null) {
                for (Map.Entry<String, JsonObject> entry : overrideMap.entrySet()) {
                    try {
                        ResourceLocation recipeId = new ResourceLocation(entry.getKey());
                        recipeOverrides.put(recipeId, entry.getValue());
                    } catch (Exception e) {
                        LOGGER.warn("无效的配方ID格式: {}", entry.getKey());
                    }
                }
            }
            
            LOGGER.debug("从文件加载了 {} 个配方覆盖", recipeOverrides.size());
            
        } catch (Exception e) {
            LOGGER.error("加载覆盖文件失败: " + OVERRIDES_FILE, e);
            recipeOverrides.clear();
        }
    }
    
    /**
     * 保存覆盖到文件
     */
    private static void saveOverrides() {
        try {
            File overridesFile = new File(OVERRIDES_FILE);
            overridesFile.getParentFile().mkdirs();
            
            // 转换为字符串键的映射
            Map<String, JsonObject> saveMap = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonObject> entry : recipeOverrides.entrySet()) {
                saveMap.put(entry.getKey().toString(), entry.getValue());
            }
            
            try (FileWriter writer = new FileWriter(overridesFile, StandardCharsets.UTF_8)) {
                GSON.toJson(saveMap, writer);
            }
            
            LOGGER.debug("覆盖已保存到文件: {} (覆盖数量: {})", OVERRIDES_FILE, recipeOverrides.size());
            
        } catch (Exception e) {
            LOGGER.error("保存覆盖文件失败: " + OVERRIDES_FILE, e);
        }
    }
    
    /**
     * 覆盖统计信息
     */
    public static class OverrideStats {
        public int totalOverrides = 0;
        public Map<String, Integer> byNamespace = new HashMap<>();
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("覆盖统计: 总计 ").append(totalOverrides).append(" 个配方\n");
            
            if (!byNamespace.isEmpty()) {
                sb.append("按命名空间分布:\n");
                byNamespace.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> sb.append("  ").append(entry.getKey())
                        .append(": ").append(entry.getValue()).append(" 个\n"));
            }
            
            return sb.toString();
        }
    }
}