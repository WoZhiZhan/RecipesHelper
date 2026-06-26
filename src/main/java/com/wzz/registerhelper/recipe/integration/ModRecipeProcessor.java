package com.wzz.registerhelper.recipe.integration;

import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;

/**
 * Mod配方处理器接口
 */
public interface ModRecipeProcessor {
    /**
     * 检查mod是否已加载
     */
    boolean isModLoaded();
    
    /**
     * 创建配方JSON
     */
    JsonObject createRecipeJson(RecipeRequest request);
    
    /**
     * 获取支持的配方类型
     */
    String[] getSupportedRecipeTypes();

    /**
     * 判断配方类型是有序还是无序
     * @param recipeType 配方类型
     * @return true表示有序配方，false表示无序配方
     */
    default boolean isShapedRecipe(String recipeType) {
        return switch (recipeType) {
            case "crafting_shaped", "shaped" -> true;
            default -> false;
        };
    }
}