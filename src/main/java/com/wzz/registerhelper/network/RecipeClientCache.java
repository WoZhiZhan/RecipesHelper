package com.wzz.registerhelper.network;

import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 客户端配方缓存
 * 用于存储从服务器同步过来的配方列表
 */
public class RecipeClientCache {
    
    // 缓存的配方列表
    private static final List<UnifiedRecipeInfo> cachedRecipes = new CopyOnWriteArrayList<>();
    
    // 是否正在加载
    private static volatile boolean loading = false;
    
    // 是否已加载完成
    private static volatile boolean loaded = false;
    
    // 加载完成的回调
    private static final List<Consumer<List<UnifiedRecipeInfo>>> callbacks = new CopyOnWriteArrayList<>();
    
    // 错误信息
    private static volatile String errorMessage = null;
    
    /**
     * 请求从服务器加载配方列表
     */
    public static void requestRecipeList() {
        if (loading) {
            return; // 已经在加载中
        }
        
        loading = true;
        loaded = false;
        errorMessage = null;
        cachedRecipes.clear();
        
        // 发送请求到服务器
        RequestRecipeListPacket.sendToServer();
    }
    
    /**
     * 服务器响应后调用，设置配方列表
     */
    public static void setRecipes(List<UnifiedRecipeInfo> recipes) {
        cachedRecipes.clear();
        cachedRecipes.addAll(recipes);
        loading = false;
        loaded = true;
        errorMessage = null;
        
        // 触发所有回调
        for (Consumer<List<UnifiedRecipeInfo>> callback : callbacks) {
            try {
                callback.accept(new ArrayList<>(cachedRecipes));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        callbacks.clear();
    }
    
    /**
     * 设置错误信息
     */
    public static void setError(String error) {
        loading = false;
        loaded = false;
        errorMessage = error;
        
        // 触发回调（传入空列表）
        for (Consumer<List<UnifiedRecipeInfo>> callback : callbacks) {
            try {
                callback.accept(new ArrayList<>());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        callbacks.clear();
    }
    
    /**
     * 获取缓存的配方列表
     */
    public static List<UnifiedRecipeInfo> getCachedRecipes() {
        return new ArrayList<>(cachedRecipes);
    }
    
    /**
     * 是否正在加载
     */
    public static boolean isLoading() {
        return loading;
    }
    
    /**
     * 是否已加载完成
     */
    public static boolean isLoaded() {
        return loaded;
    }
    
    /**
     * 获取错误信息
     */
    public static String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 添加加载完成回调
     */
    public static void addLoadCallback(Consumer<List<UnifiedRecipeInfo>> callback) {
        if (loaded) {
            // 已经加载完成，直接调用
            callback.accept(new ArrayList<>(cachedRecipes));
        } else {
            callbacks.add(callback);
        }
    }
    
    /**
     * 清除缓存
     */
    public static void clearCache() {
        cachedRecipes.clear();
        loading = false;
        loaded = false;
        errorMessage = null;
        callbacks.clear();
    }
    
    /**
     * 根据ID查找配方信息
     */
    public static UnifiedRecipeInfo findRecipeById(ResourceLocation id) {
        for (UnifiedRecipeInfo info : cachedRecipes) {
            if (info.id.equals(id)) {
                return info;
            }
        }
        return null;
    }
}