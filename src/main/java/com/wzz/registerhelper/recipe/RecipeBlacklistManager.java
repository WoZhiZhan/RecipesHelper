package com.wzz.registerhelper.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配方黑名单管理器 - 管理被禁用的配方
 */
public class RecipeBlacklistManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BLACKLIST_FILE = FMLPaths.CONFIGDIR.get()
            .resolve("registerhelper/recipe_blacklist.json").toAbsolutePath().normalize().toString();

    private static final Set<ResourceLocation> blacklistedRecipes = ConcurrentHashMap.newKeySet();
    private static boolean initialized = false;
    
    /**
     * 初始化黑名单管理器
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        try {
            loadBlacklist();
            initialized = true;
            LOGGER.info("配方黑名单管理器已初始化，黑名单配方数量: {}", blacklistedRecipes.size());
        } catch (Exception e) {
            LOGGER.error("初始化配方黑名单管理器失败", e);
            initialized = true; // 即使失败也标记为已初始化，避免重复尝试
        }
    }
    
    /**
     * 添加配方到黑名单
     */
    public static boolean addToBlacklist(ResourceLocation recipeId) {
        try {
            initialize();
            
            boolean added = blacklistedRecipes.add(recipeId);
            if (added) {
                saveBlacklist();
                LOGGER.info("配方已添加到黑名单: {}", recipeId);
                return true;
            } else {
                LOGGER.warn("配方已在黑名单中: {}", recipeId);
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.error("添加配方到黑名单失败: " + recipeId, e);
            return false;
        }
    }
    
    /**
     * 从黑名单移除配方
     */
    public static boolean removeFromBlacklist(ResourceLocation recipeId) {
        try {
            initialize();
            
            boolean removed = blacklistedRecipes.remove(recipeId);
            if (removed) {
                saveBlacklist();
                LOGGER.info("配方已从黑名单移除: {}", recipeId);
                return true;
            } else {
                LOGGER.warn("配方不在黑名单中: {}", recipeId);
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.error("从黑名单移除配方失败: " + recipeId, e);
            return false;
        }
    }
    
    /**
     * 检查配方是否在黑名单中
     */
    public static boolean isBlacklisted(ResourceLocation recipeId) {
        initialize();
        return blacklistedRecipes.contains(recipeId);
    }
    
    /**
     * 获取所有黑名单配方
     */
    public static Set<ResourceLocation> getBlacklistedRecipes() {
        initialize();
        return new HashSet<>(blacklistedRecipes);
    }
    
    /**
     * 清空黑名单
     */
    public static boolean clearBlacklist() {
        try {
            initialize();
            
            int count = blacklistedRecipes.size();
            blacklistedRecipes.clear();
            saveBlacklist();
            
            LOGGER.info("已清空黑名单，移除了 {} 个配方", count);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("清空黑名单失败", e);
            return false;
        }
    }
    
    /**
     * 获取黑名单统计信息
     */
    public static BlacklistStats getStats() {
        initialize();
        
        BlacklistStats stats = new BlacklistStats();
        stats.totalBlacklisted = blacklistedRecipes.size();
        
        // 按命名空间分组统计
        for (ResourceLocation recipeId : blacklistedRecipes) {
            String namespace = recipeId.getNamespace();
            stats.byNamespace.merge(namespace, 1, Integer::sum);
        }
        
        return stats;
    }
    
    /**
     * 批量操作：添加多个配方到黑名单
     */
    public static int addMultipleToBlacklist(Set<ResourceLocation> recipeIds) {
        initialize();
        
        int addedCount = 0;
        for (ResourceLocation recipeId : recipeIds) {
            if (blacklistedRecipes.add(recipeId)) {
                addedCount++;
            }
        }
        
        if (addedCount > 0) {
            saveBlacklist();
            LOGGER.info("批量添加 {} 个配方到黑名单", addedCount);
        }
        
        return addedCount;
    }
    
    /**
     * 批量操作：从黑名单移除多个配方
     */
    public static int removeMultipleFromBlacklist(Set<ResourceLocation> recipeIds) {
        initialize();
        
        int removedCount = 0;
        for (ResourceLocation recipeId : recipeIds) {
            if (blacklistedRecipes.remove(recipeId)) {
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            saveBlacklist();
            LOGGER.info("批量从黑名单移除 {} 个配方", removedCount);
        }
        
        return removedCount;
    }
    
    /**
     * 从文件加载黑名单
     */
    private static void loadBlacklist() {
        File blacklistFile = new File(BLACKLIST_FILE);
        
        if (!blacklistFile.exists()) {
            LOGGER.debug("黑名单文件不存在，创建空黑名单: {}", BLACKLIST_FILE);
            blacklistedRecipes.clear();
            saveBlacklist(); // 创建空文件
            return;
        }
        
        try (FileReader reader = new FileReader(blacklistFile, StandardCharsets.UTF_8)) {
            Type setType = new TypeToken<Set<String>>() {}.getType();
            Set<String> recipeStrings = GSON.fromJson(reader, setType);
            
            blacklistedRecipes.clear();
            if (recipeStrings != null) {
                for (String recipeString : recipeStrings) {
                    try {
                        ResourceLocation recipeId = new ResourceLocation(recipeString);
                        blacklistedRecipes.add(recipeId);
                    } catch (Exception e) {
                        LOGGER.warn("无效的配方ID格式: {}", recipeString);
                    }
                }
            }
            
            LOGGER.debug("从文件加载了 {} 个黑名单配方", blacklistedRecipes.size());
            
        } catch (Exception e) {
            LOGGER.error("加载黑名单文件失败: " + BLACKLIST_FILE, e);
            blacklistedRecipes.clear(); // 出错时清空，避免使用损坏的数据
        }
    }
    
    /**
     * 保存黑名单到文件
     */
    private static void saveBlacklist() {
        try {
            File blacklistFile = new File(BLACKLIST_FILE);
            blacklistFile.getParentFile().mkdirs();
            
            // 转换为字符串集合
            Set<String> recipeStrings = new HashSet<>();
            for (ResourceLocation recipeId : blacklistedRecipes) {
                recipeStrings.add(recipeId.toString());
            }
            
            try (FileWriter writer = new FileWriter(blacklistFile, StandardCharsets.UTF_8)) {
                GSON.toJson(recipeStrings, writer);
            }
            
            LOGGER.debug("黑名单已保存到文件: {} (配方数量: {})", BLACKLIST_FILE, blacklistedRecipes.size());
            
        } catch (Exception e) {
            LOGGER.error("保存黑名单文件失败: " + BLACKLIST_FILE, e);
        }
    }
    
    /**
     * 重新加载黑名单
     */
    public static void reload() {
        try {
            LOGGER.info("重新加载配方黑名单...");
            loadBlacklist();
            LOGGER.info("配方黑名单重新加载完成，当前黑名单配方数量: {}", blacklistedRecipes.size());
        } catch (Exception e) {
            LOGGER.error("重新加载配方黑名单失败", e);
        }
    }
    
    /**
     * 触发配方重载
     */
    public static boolean triggerRecipeReload() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.warn("服务器未运行，无法触发配方重载");
                return false;
            }
            
            server.execute(() -> {
                try {
                    Collection<String> selectedPackIds = server.getPackRepository()
                            .getSelectedPacks()
                            .stream()
                            .map(Pack::getId)
                            .toList();
                    server.reloadResources(selectedPackIds);
                    LOGGER.info("已触发配方重载");
                } catch (Exception e) {
                    LOGGER.error("触发配方重载失败", e);
                }
            });
            
            return true;
            
        } catch (Exception e) {
            LOGGER.error("触发配方重载时发生错误", e);
            return false;
        }
    }
    
    /**
     * 黑名单统计信息
     */
    public static class BlacklistStats {
        public int totalBlacklisted = 0;
        public java.util.Map<String, Integer> byNamespace = new java.util.HashMap<>();
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("黑名单统计: 总计 ").append(totalBlacklisted).append(" 个配方\n");
            
            if (!byNamespace.isEmpty()) {
                sb.append("按命名空间分布:\n");
                byNamespace.entrySet().stream()
                    .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> sb.append("  ").append(entry.getKey())
                        .append(": ").append(entry.getValue()).append(" 个\n"));
            }
            
            return sb.toString();
        }
    }
}