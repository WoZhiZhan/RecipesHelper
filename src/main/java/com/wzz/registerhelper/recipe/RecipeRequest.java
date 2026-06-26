package com.wzz.registerhelper.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 配方请求数据类
 */
public class RecipeRequest {
    public String modId;           // mod ID
    public String recipeType;      // 配方类型
    public String recipeId;        // 配方ID
    public ItemStack result;       // 结果物品
    public int resultCount = 1;    // 结果数量
    public String[] pattern;       // 有形状配方的模式
    public Object[] ingredients;   // 材料列表
    public Map<String, Object> properties = new HashMap<>(); // 额外属性
    
    // 便捷构造方法
    public static RecipeRequest shaped(String modId, String recipeId, ItemStack result, String[] pattern, Object... ingredients) {
        RecipeRequest request = new RecipeRequest();
        request.modId = modId;
        request.recipeType = "shaped";
        request.recipeId = recipeId;
        request.result = result;
        request.pattern = pattern;
        request.ingredients = ingredients;
        return request;
    }
    
    public static RecipeRequest shapeless(String modId, String recipeId, ItemStack result, Object... ingredients) {
        RecipeRequest request = new RecipeRequest();
        request.modId = modId;
        request.recipeType = "shapeless";
        request.recipeId = recipeId;
        request.result = result;
        request.ingredients = ingredients;
        return request;
    }
    
    public static RecipeRequest cooking(String modId, String cookingType, String recipeId, ItemStack result, Object ingredient, float experience, int cookingTime) {
        RecipeRequest request = new RecipeRequest();
        request.modId = modId;
        request.recipeType = cookingType;
        request.recipeId = recipeId;
        request.result = result;
        request.ingredients = new Object[]{ingredient};
        request.properties.put("experience", experience);
        request.properties.put("cookingTime", cookingTime);
        return request;
    }
    
    public RecipeRequest withProperty(String key, Object value) {
        this.properties.put(key, value);
        return this;
    }
}