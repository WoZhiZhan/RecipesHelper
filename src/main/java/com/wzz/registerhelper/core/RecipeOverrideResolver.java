package com.wzz.registerhelper.core;

import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.*;

public class RecipeOverrideResolver {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> OVERRIDDEN_RECIPES = new HashSet<>();

    /**
     * 检测JSON中的配方与内存中配方的冲突，删除内存中的原始配方
     * @return 删除的内存配方数量
     */
    public static int resolveConflictsPreferJson() {
        try {
            Set<ResourceLocation> jsonRecipeIds = getJsonRecipeIds();
            if (jsonRecipeIds.isEmpty()) {
                LOGGER.info("没有找到JSON配方文件");
                return 0;
            }
            LOGGER.info("找到 {} 个JSON配方", jsonRecipeIds.size());
            Set<ResourceLocation> conflictingIds = findConflictingRecipeIds(jsonRecipeIds);
            if (conflictingIds.isEmpty()) {
                LOGGER.info("没有发现配方冲突");
                return 0;
            }
            int deletedCount = deleteOriginalRecipesFromMemory(conflictingIds);
            OVERRIDDEN_RECIPES.addAll(conflictingIds);
            LOGGER.info("成功从内存中删除 {} 个原始配方", deletedCount);
            return deletedCount;
        } catch (Exception e) {
            LOGGER.error("解决配方冲突时发生错误", e);
            return 0;
        }
    }

    /**
     * 获取所有JSON配方的ID
     */
    private static Set<ResourceLocation> getJsonRecipeIds() {
        Set<ResourceLocation> jsonIds = new HashSet<>();
        
        try {
            List<String> recipeIdStrings = RecipeJsonManager.getAllSavedRecipeIds();
            
            for (String idString : recipeIdStrings) {
                try {
                    RecipeJsonManager.RecipeData data = RecipeJsonManager.loadRecipe(idString);
                    if (data != null && data.id != null && !data.id.trim().isEmpty()) {
                        ResourceLocation actualId = new ResourceLocation(data.id);
                        jsonIds.add(actualId);
                        LOGGER.debug("JSON配方: {} (文件: {})", actualId, idString);
                    } else {
                        ResourceLocation fileId = new ResourceLocation(idString);
                        jsonIds.add(fileId);
                        LOGGER.debug("JSON配方(从文件名): {}", fileId);
                    }
                } catch (Exception e) {
                    LOGGER.warn("解析JSON配方ID失败: {} - {}", idString, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("获取JSON配方ID失败", e);
        }
        
        return jsonIds;
    }

    /**
     * 找出与JSON配方冲突的内存配方ID
     */
    private static Set<ResourceLocation> findConflictingRecipeIds(Set<ResourceLocation> jsonRecipeIds) {
        Set<ResourceLocation> conflictingIds = new HashSet<>();
        
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.error("服务器实例为空");
                return conflictingIds;
            }

            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();

            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                ResourceLocation recipeId = recipe.getId();
                
                if (jsonRecipeIds.contains(recipeId)) {
                    conflictingIds.add(recipeId);
                }
            }

        } catch (Exception e) {
            LOGGER.error("查找冲突配方ID失败", e);
        }
        
        return conflictingIds;
    }

    /**
     * 从内存中删除指定的原始配方
     */
    private static int deleteOriginalRecipesFromMemory(Set<ResourceLocation> recipeIds) {
        int deletedCount = 0;
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.error("服务器实例为空，无法删除配方");
                return 0;
            }
            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();
            Field recipesField;
            try {
                recipesField = RecipeManager.class.getDeclaredField("f_44007_");
            } catch (NoSuchFieldException e) {
                recipesField = RecipeManager.class.getDeclaredField("recipes");
            }
            recipesField.setAccessible(true);
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> currentRecipes =
                    (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) recipesField.get(recipeManager);
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> newRecipes = new HashMap<>();
            for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> typeEntry : currentRecipes.entrySet()) {
                Map<ResourceLocation, Recipe<?>> typeRecipes = new HashMap<>();
                
                for (Map.Entry<ResourceLocation, Recipe<?>> recipeEntry : typeEntry.getValue().entrySet()) {
                    ResourceLocation id = recipeEntry.getKey();
                    if (recipeIds.contains(id)) {
                        deletedCount++;
                    } else {
                        typeRecipes.put(id, recipeEntry.getValue());
                    }
                }
                
                newRecipes.put(typeEntry.getKey(), typeRecipes);
            }
            recipesField.set(recipeManager, Collections.unmodifiableMap(newRecipes));
            syncRecipesToClients(server, recipeManager);
        } catch (Exception e) {
            LOGGER.error("删除内存中的原始配方失败", e);
        }
        return deletedCount;
    }

    /**
     * 同步配方到所有客户端
     */
    private static void syncRecipesToClients(MinecraftServer server, RecipeManager recipeManager) {
        try {
            Collection<Recipe<?>> allRecipes = recipeManager.getRecipes();
            ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(allRecipes);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(packet);
            }
        } catch (Exception e) {
            LOGGER.error("同步配方到客户端失败", e);
        }
    }

    /**
     * 生成配方覆盖报告
     */
    public static OverrideReport generateOverrideReport() {
        OverrideReport report = new OverrideReport();
        try {
            Set<ResourceLocation> jsonRecipeIds = getJsonRecipeIds();
            report.jsonRecipeCount = jsonRecipeIds.size();
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerLevel serverLevel = server.overworld();
                RecipeManager recipeManager = serverLevel.getRecipeManager();
                report.memoryRecipeCount = recipeManager.getRecipes().size();
                for (Recipe<?> recipe : recipeManager.getRecipes()) {
                    ResourceLocation id = recipe.getId();
                    if (jsonRecipeIds.contains(id)) {
                        report.conflictingRecipes.add(id);
                    }
                }
            }
            report.overriddenRecipes.addAll(OVERRIDDEN_RECIPES);
        } catch (Exception e) {
            LOGGER.error("生成覆盖报告失败", e);
        }
        
        return report;
    }

    /**
     * 检查指定配方是否已被JSON覆盖
     */
    public static boolean isRecipeOverridden(ResourceLocation recipeId) {
        return OVERRIDDEN_RECIPES.contains(recipeId);
    }

    /**
     * 获取所有被覆盖的配方ID
     */
    public static Set<ResourceLocation> getOverriddenRecipes() {
        return new HashSet<>(OVERRIDDEN_RECIPES);
    }

    /**
     * 配方覆盖报告类
     */
    public static class OverrideReport {
        public int jsonRecipeCount = 0;
        public int memoryRecipeCount = 0;
        public List<ResourceLocation> conflictingRecipes = new ArrayList<>();
        public Set<ResourceLocation> overriddenRecipes = new HashSet<>();

        public boolean hasConflicts() {
            return !conflictingRecipes.isEmpty();
        }
    }
}