package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 精灵贸易布局（横向5个输入格子）
 */
public class ElvenTradeLayout implements RecipeLayout {
    private final int slotSpacing;
    
    public ElvenTradeLayout() {
        this(30);
    }
    
    public ElvenTradeLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        // 横向5个输入槽位
        for (int i = 0; i < 5; i++) {
            int x = baseX + i * slotSpacing;
            int y = baseY + slotSpacing;
            components.add(new SlotComponent(
                x, y,
                "input_" + (i + 1),
                i
            ));
        }
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 5 * slotSpacing, 3 * slotSpacing);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "Elven Trade (5 Horizontal Inputs)";
    }
}