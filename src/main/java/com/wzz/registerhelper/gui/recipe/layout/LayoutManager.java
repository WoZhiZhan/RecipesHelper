package com.wzz.registerhelper.gui.recipe.layout;

import com.wzz.registerhelper.gui.recipe.layout.integration.botania.*;
import com.wzz.registerhelper.gui.recipe.layout.integration.builtin.*;
import com.wzz.registerhelper.gui.recipe.layout.integration.farmersdelight.CookingLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.farmersdelight.CuttingLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.immersive_engineering.ArcFurnaceLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.mysticalagriculture.InfusionLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.mysticalagriculture.ReprocessorLayout;

import java.util.*;

/**
 * 布局管理器
 */
public class LayoutManager {
    private static final Map<String, RecipeLayout> layouts = new HashMap<>();
    
    static {
        // 基础矩形布局
        registerLayout("rectangular_3x3", new RectangularLayout(3, 3));
        registerLayout("rectangular_9x9", new RectangularLayout(9, 9));
        registerLayout("rectangular_4x1", new RectangularLayout(4, 1));
        registerLayout("rectangular_1x1", new RectangularLayout(1, 1));
        registerLayout("rectangular_2x2", new RectangularLayout(2, 2));
        registerLayout("rectangular_5x5", new RectangularLayout(5, 5));
        
        // Minecraft 内置布局
        registerLayout("minecraft_brewing", new MinecraftBrewingLayout());
        registerLayout("stonecutting", new StonecuttingLayout());
        registerLayout("smithing", new SmithingLayout());
        
        // Mod 集成布局
        registerLayout("runic_altar", new RunicAltarLayout());
        registerLayout("arc_furnace", new ArcFurnaceLayout());
        registerLayout("infusion", new InfusionLayout());
        registerLayout("reprocessor", new ReprocessorLayout());
        registerLayout("cutting", new CuttingLayout());
        registerLayout("cooking", new CookingLayout());
    }
    
    /**
     * 注册布局
     */
    public static void registerLayout(String id, RecipeLayout layout) {
        layouts.put(id, layout);
    }

    /**
     * 获取布局
     */
    public static RecipeLayout getLayout(String id) {
        return layouts.get(id);
    }
    
    /**
     * 获取所有布局ID
     */
    public static Set<String> getAllLayoutIds() {
        return layouts.keySet();
    }
    
    /**
     * 获取所有布局
     */
    public static List<RecipeLayout> getAllLayouts() {
        return new ArrayList<>(layouts.values());
    }
}