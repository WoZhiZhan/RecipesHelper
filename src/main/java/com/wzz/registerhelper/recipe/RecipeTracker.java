package com.wzz.registerhelper.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 配方追踪器
 * 用于追踪由本mod添加或修改的配方
 */
public class RecipeTracker {
    
    /**
     * 存储本mod添加的配方ID
     */
    private static final Set<ResourceLocation> TRACKED_RECIPES = Collections.synchronizedSet(new HashSet<>());
    
    /**
     * 注册一个配方为本mod追踪的配方
     * @param recipeId 配方的ResourceLocation
     */
    public static void trackRecipe(ResourceLocation recipeId) {
        TRACKED_RECIPES.add(recipeId);
    }
    
    /**
     * 批量注册配方
     * @param recipeIds 配方ID集合
     */
    public static void trackRecipes(Set<ResourceLocation> recipeIds) {
        TRACKED_RECIPES.addAll(recipeIds);
    }
    
    /**
     * 检查配方是否由本mod追踪
     * @param recipeId 配方的ResourceLocation
     * @return 如果是本mod的配方返回true
     */
    public static boolean isTrackedRecipe(ResourceLocation recipeId) {
        return TRACKED_RECIPES.contains(recipeId);
    }
    
    /**
     * 获取所有被追踪的配方ID（只读）
     * @return 配方ID集合的只读视图
     */
    public static Set<ResourceLocation> getTrackedRecipes() {
        return Collections.unmodifiableSet(TRACKED_RECIPES);
    }
    
    /**
     * 清空所有追踪的配方（用于重载）
     */
    public static void clearTrackedRecipes() {
        TRACKED_RECIPES.clear();
    }
    
    /**
     * 获取追踪的配方数量
     * @return 配方数量
     */
    public static int getTrackedRecipeCount() {
        return TRACKED_RECIPES.size();
    }
    
    /**
     * 移除指定的配方追踪
     * @param recipeId 配方的ResourceLocation
     * @return 如果配方存在并被移除返回true
     */
    public static boolean untrackRecipe(ResourceLocation recipeId) {
        return TRACKED_RECIPES.remove(recipeId);
    }
}