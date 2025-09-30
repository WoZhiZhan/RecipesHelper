package com.wzz.registerhelper.gui.recipe.layout;

import com.wzz.registerhelper.gui.recipe.layout.integration.immersive_engineering.ArcFurnaceLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.botania.MysticalInfusionLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.botania.RunicAltarLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.builtin.*;

import java.util.*;

/**
 * 布局管理器
 */
public class LayoutManager {
    private static final Map<String, GridLayout> layouts = new HashMap<>();
    
    static {
        registerLayout("rectangular_3x3", new RectangularLayout(3, 3));
        registerLayout("rectangular_9x9", new RectangularLayout(9, 9));
        registerLayout("rectangular_4x1", new RectangularLayout(4, 1));
        registerLayout("rectangular_1x1", new RectangularLayout(1, 1));
        registerLayout("rectangular_2x2", new RectangularLayout(2, 2));
        registerLayout("rectangular_5x5", new RectangularLayout(5, 5));

        registerLayout("minecraft_brewing", new MinecraftBrewingLayout());
        registerLayout("stonecutting", new StonecuttingLayout());
        registerLayout("smithing", new SmithingLayout());

        registerLayout("mystical_infusion", new MysticalInfusionLayout());
        registerLayout("runic_altar", new RunicAltarLayout());
        registerLayout("arc_furnace", new ArcFurnaceLayout());
    }
    
    public static void registerLayout(String id, GridLayout layout) {
        layouts.put(id, layout);
    }
    
    public static GridLayout getLayout(String id) {
        return layouts.get(id);
    }
    
    public static Set<String> getAllLayoutIds() {
        return layouts.keySet();
    }
    
    public static List<GridLayout> getAllLayouts() {
        return new ArrayList<>(layouts.values());
    }
}