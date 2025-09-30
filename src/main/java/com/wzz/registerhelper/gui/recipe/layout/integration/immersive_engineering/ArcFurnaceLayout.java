package com.wzz.registerhelper.gui.recipe.layout.integration.immersive_engineering;

import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 沉浸工程电弧炉布局（4输入+1添加剂+1输出+1渣料）
 */
public class ArcFurnaceLayout implements GridLayout {
    private final int slotSpacing;
    
    public ArcFurnaceLayout() {
        this(22);
    }
    
    public ArcFurnaceLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();
        
        // 4个主要输入槽位（2x2）
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                int pixelX = baseX + x * slotSpacing;
                int pixelY = baseY + y * slotSpacing;
                int index = y * 2 + x;
                
                slots.add(new SlotPosition(x, y, pixelX, pixelY, index, 
                    SlotPosition.SlotType.INPUT, "main_" + (index + 1)));
            }
        }
        
        // 添加剂槽位
        int additiveX = baseX + 3 * slotSpacing;
        int additiveY = baseY;
        slots.add(new SlotPosition(3, 0, additiveX, additiveY, 4, 
            SlotPosition.SlotType.CATALYST, "additive"));
        
        return slots;
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