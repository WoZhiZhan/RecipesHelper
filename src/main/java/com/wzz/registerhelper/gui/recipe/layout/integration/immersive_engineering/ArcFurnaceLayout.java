package com.wzz.registerhelper.gui.recipe.layout.integration.immersive_engineering;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 沉浸工程电弧炉布局（4输入+1添加剂）
 */
public class ArcFurnaceLayout implements RecipeLayout {
    private final int slotSpacing;
    
    public ArcFurnaceLayout() {
        this(22);
    }
    
    public ArcFurnaceLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        // 4个主要输入槽位（2x2）
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                int pixelX = baseX + x * slotSpacing;
                int pixelY = baseY + y * slotSpacing;
                int index = y * 2 + x;
                
                components.add(new SlotComponent(
                    pixelX, pixelY,
                    "main_" + (index + 1),
                    index
                ));
            }
        }
        
        // 添加剂槽位（催化剂）
        int additiveX = baseX + 3 * slotSpacing;
        int additiveY = baseY;
        components.add(new SlotComponent(
            additiveX, additiveY,
            "additive",
            4
        ));
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 4 * slotSpacing, 2 * slotSpacing);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "Arc Furnace (4 Inputs + Additive)";
    }
}