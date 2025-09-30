package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 神秘农业注魔布局（中央+环形）
 */
public class MysticalInfusionLayout implements GridLayout {
    private final int slotSpacing;
    
    public MysticalInfusionLayout() {
        this(25); // 稍大的间距以适应环形布局
    }
    
    public MysticalInfusionLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();
        
        // 中央槽位（输入物品）
        int centerX = baseX + 2 * slotSpacing;
        int centerY = baseY + 2 * slotSpacing;
        slots.add(new SlotPosition(2, 2, centerX, centerY, 0, SlotPosition.SlotType.CENTER, "center"));
        
        // 8个环形槽位（材料）
        int[][] ringPositions = {
            {1, 1}, {2, 1}, {3, 1},  // 上排
            {3, 2},                   // 右侧
            {3, 3}, {2, 3}, {1, 3},  // 下排
            {1, 2}                    // 左侧
        };
        
        for (int i = 0; i < ringPositions.length; i++) {
            int x = ringPositions[i][0];
            int y = ringPositions[i][1];
            int pixelX = baseX + x * slotSpacing;
            int pixelY = baseY + y * slotSpacing;
            
            slots.add(new SlotPosition(x, y, pixelX, pixelY, i + 1, 
                SlotPosition.SlotType.INPUT, "ingredient_" + (i + 1)));
        }
        
        return slots;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 5 * slotSpacing, 5 * slotSpacing);
    }
    
    @Override
    public boolean supportsTiers() {
        return false; // 神秘农业布局是固定的
    }
    
    @Override
    public String getLayoutName() {
        return "Mystical Infusion (Center + Ring)";
    }
}