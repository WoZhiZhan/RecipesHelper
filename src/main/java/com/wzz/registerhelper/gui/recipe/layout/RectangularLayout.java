package com.wzz.registerhelper.gui.recipe.layout;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeBuilder;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 矩形网格布局
 */
public class RectangularLayout implements RecipeLayout {
    private final int maxWidth, maxHeight;
    private final int slotSpacing;
    
    public RectangularLayout(int maxWidth, int maxHeight) {
        this(maxWidth, maxHeight, 20);
    }
    
    public RectangularLayout(int maxWidth, int maxHeight, int slotSpacing) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        int width = supportsTiers() ? DynamicRecipeBuilder.getGridSizeForTier(tier) : maxWidth;
        int height = supportsTiers() ? DynamicRecipeBuilder.getGridSizeForTier(tier) : maxHeight;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelX = baseX + x * slotSpacing;
                int pixelY = baseY + y * slotSpacing;
                int index = y * width + x;
                
                components.add(new SlotComponent(
                    pixelX, pixelY,
                    "slot_" + index,
                    index
                ));
            }
        }
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        int size = supportsTiers() ? DynamicRecipeBuilder.getGridSizeForTier(tier) : Math.max(maxWidth, maxHeight);
        return new Rectangle(0, 0, size * slotSpacing, size * slotSpacing);
    }
    
    @Override
    public boolean supportsTiers() {
        return maxWidth == maxHeight;
    }
    
    @Override
    public String getLayoutName() {
        return "Rectangular " + maxWidth + "x" + maxHeight;
    }
}