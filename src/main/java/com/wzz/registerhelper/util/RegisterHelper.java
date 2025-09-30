package com.wzz.registerhelper.util;

import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.recipe.UniversalRecipeManager;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;

import java.util.HashSet;
import java.util.Set;

public class RegisterHelper {
    private static final Set<String> registeredBackendProcessors = new HashSet<>();

    /**
     * 简单注册：自动发现配方类型
     */
    public static void registerProcessor(String modID, ModRecipeProcessor processor) {
        DynamicRecipeTypeConfig.registerModProcessor(modID, processor);
        registerBackendProcessor(modID, processor);
    }

    /**
     * 详细注册：指定配方类型和网格大小
     */
    public static void registerRecipeType(String modID, String type, String displayName,
                                          ModRecipeProcessor processor, int gridSize, boolean supportsTiers) {
        registerRecipeType(modID, type, displayName, processor, gridSize, gridSize, supportsTiers);
    }

    /**
     * 完整注册：支持不同宽高的网格
     */
    public static void registerRecipeType(String modID, String type, String displayName,
                                          ModRecipeProcessor processor, int gridWidth, int gridHeight, boolean supportsTiers) {
        String recipeId = modID + ":" + type;
        String mode = extractMode(type);
        String category = extractCategory(type, modID);

        DynamicRecipeTypeConfig.registerRecipeType(
                new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder(recipeId, displayName)
                        .modId(modID)
                        .gridSize(gridWidth, gridHeight)
                        .supportsFillMode(gridWidth > 1 && gridHeight > 1)
                        .property("category", category)
                        .property("mode", mode)
                        .property("supportsTiers", supportsTiers)
                        .processor(processor)
                        .build()
        );

        registerBackendProcessor(modID, processor);
    }

    /**
     * 注册带自定义布局的配方类型
     */
    public static void registerRecipeTypeWithLayout(String modID, String type, String displayName,
                                                    ModRecipeProcessor processor, String layoutId) {
        DynamicRecipeTypeConfig.registerRecipeType(
                new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder(modID + ":" + type, displayName)
                        .modId(modID)
                        .gridSize(9, 9)
                        .supportsFillMode(!"mystical_infusion".equals(layoutId))
                        .property("category", modID)
                        .property("mode", type.replace("crafting", ""))
                        .property("layout", layoutId)
                        .processor(processor)
                        .build()
        );
        registerBackendProcessor(modID, processor);
    }

    /**
     * 避免重复注册后端处理器
     */
    private static void registerBackendProcessor(String modID, ModRecipeProcessor processor) {
        if (!registeredBackendProcessors.contains(modID)) {
            UniversalRecipeManager.registerProcessor(modID, processor);
            registeredBackendProcessors.add(modID);
        }
    }

    /**
     * 从类型名称提取模式
     */
    private static String extractMode(String type) {
        if (type.contains("shaped")) return "shaped";
        if (type.contains("shapeless")) return "shapeless";
        if (type.contains("smelting")) return "smelting";
        if (type.contains("blasting")) return "blasting";
        if (type.contains("smoking")) return "smoking";
        return "custom"; // 默认为自定义模式
    }

    /**
     * 从类型名称提取分类
     */
    private static String extractCategory(String type, String modID) {
        if (modID.equals("minecraft")) {
            if (type.contains("crafting")) return "crafting";
            if (type.contains("smelting") || type.contains("blasting") ||
                    type.contains("smoking") || type.contains("campfire")) return "cooking";
        } else if (modID.equals("avaritia")) {
            return "avaritia";
        }
        return modID;
    }
}