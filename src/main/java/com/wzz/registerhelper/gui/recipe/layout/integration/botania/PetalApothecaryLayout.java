package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 花瓣药剂师布局（十字形）
 */
public class PetalApothecaryLayout implements GridLayout {
    private final int slotSpacing;
    
    public PetalApothecaryLayout() {
        this(30);
    }
    
    public PetalApothecaryLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();
        
        int centerX = baseX + 2 * slotSpacing;
        int centerY = baseY + 2 * slotSpacing;
        
        // 中心槽位（种子）
        slots.add(new SlotPosition(2, 2, centerX, centerY, 0, 
            SlotPosition.SlotType.INPUT, "seed"));
        
        // 十字形花瓣槽位
        int[][] positions = {
            {2, 1}, // 上
            {3, 2}, // 右
            {2, 3}, // 下
            {1, 2}, // 左
            {1, 1}, // 左上
            {3, 1}, // 右上
            {3, 3}, // 右下
            {1, 3}  // 左下
        };
        
        int maxSlots = Math.min(positions.length, tier * 2 + 4);
        for (int i = 0; i < maxSlots; i++) {
            int x = baseX + positions[i][0] * slotSpacing;
            int y = baseY + positions[i][1] * slotSpacing;
            slots.add(new SlotPosition(positions[i][0], positions[i][1], x, y, i + 1, 
                SlotPosition.SlotType.INPUT, "petal_" + (i + 1)));
        }
        
        return slots;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        int size = 5 * slotSpacing;
        return new Rectangle(0, 0, size, size);
    }
    
    @Override
    public boolean supportsTiers() {
        return true;
    }
    
    @Override
    public String getLayoutName() {
        return "Petal Apothecary (Cross)";
    }
}