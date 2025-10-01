package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯洁雏菊布局（单一转换）
 */
public class PureDaisyLayout implements RecipeLayout {
    private final int slotSpacing;
    
    public PureDaisyLayout() {
        this(30);
    }
    
    public PureDaisyLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        // 单一输入槽位
        components.add(new SlotComponent(
            baseX + slotSpacing, 
            baseY + slotSpacing, 
            "input_block",
            0
        ));
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        int size = 3 * slotSpacing;
        return new Rectangle(0, 0, size, size);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "Pure Daisy (Single)";
    }
}