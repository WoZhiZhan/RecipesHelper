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
        
        // Leave room for the full ingredient list exposed by Botania's API.
        for (int i = 0; i < 16; i++) {
            int x = baseX + 42 + (i % 5) * 18;
            int y = baseY + (i / 5) * 20;
            components.add(new SlotComponent(
                x, y,
                "input_" + (i + 1),
                i
            ));
        }
        components.add(new SlotComponent(baseX + 93, baseY + 41,
                "output", -1, SlotComponent.SlotRole.OUTPUT));
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 8 * slotSpacing, 3 * slotSpacing);
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
