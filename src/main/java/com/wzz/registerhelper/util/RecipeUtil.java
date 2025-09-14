package com.wzz.registerhelper.util;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.core.RecipeJsonManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.*;

public class RecipeUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 检查配方是否存在（检查内存）
     */
    public static boolean recipeExistsInMemory(ResourceLocation recipeId) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return false;

            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();

            return recipeManager.byKey(recipeId).isPresent();

        } catch (Exception e) {
            LOGGER.error("检查配方存在性失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 检查配方是否存在（检查JSON文件）
     */
    public static boolean recipeExistsInJson(ResourceLocation recipeId) {
        return RecipeJsonManager.recipeFileExists(recipeId.toString());
    }

    /**
     * 全面检查配方是否存在（内存或JSON文件）
     */
    public static boolean recipeExists(ResourceLocation recipeId) {
        return recipeExistsInMemory(recipeId) || recipeExistsInJson(recipeId);
    }

    /**
     * 获取配方存在状态的详细信息
     */
    public static RecipeExistenceStatus getRecipeExistenceStatus(ResourceLocation recipeId) {
        boolean inMemory = recipeExistsInMemory(recipeId);
        boolean inJson = recipeExistsInJson(recipeId);

        return new RecipeExistenceStatus(recipeId, inMemory, inJson);
    }

    /**
     * 配方存在状态类
     */
    public static class RecipeExistenceStatus {
        public final ResourceLocation recipeId;
        public final boolean existsInMemory;
        public final boolean existsInJson;

        public RecipeExistenceStatus(ResourceLocation recipeId, boolean existsInMemory, boolean existsInJson) {
            this.recipeId = recipeId;
            this.existsInMemory = existsInMemory;
            this.existsInJson = existsInJson;
        }

        public boolean exists() {
            return existsInMemory || existsInJson;
        }

        public String getStatusDescription() {
            if (existsInMemory && existsInJson) {
                return "内存和JSON文件中都存在";
            } else if (existsInMemory) {
                return "仅在内存中存在";
            } else if (existsInJson) {
                return "仅在JSON文件中存在";
            } else {
                return "不存在";
            }
        }

        @Override
        public String toString() {
            return String.format("RecipeStatus[%s: %s]", recipeId, getStatusDescription());
        }
    }

    /**
     * 获取所有配方ID（从内存中）
     */
    public static List<ResourceLocation> getAllRecipeIds() {
        List<ResourceLocation> recipeIds = new ArrayList<>();

        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerLevel serverLevel = server.overworld();
                RecipeManager recipeManager = serverLevel.getRecipeManager();

                for (Recipe<?> recipe : recipeManager.getRecipes()) {
                    recipeIds.add(recipe.getId());
                }
            }
        } catch (Exception e) {
            LOGGER.error("获取配方列表失败", e);
        }

        return recipeIds;
    }
}