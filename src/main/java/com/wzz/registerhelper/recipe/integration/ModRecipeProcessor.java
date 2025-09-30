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
}