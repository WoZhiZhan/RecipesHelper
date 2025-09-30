package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯洁雏菊布局（单一转换）
 */
public class PureDaisyLayout implements GridLayout {
    private final int slotSpacing;
    
    public PureDaisyLayout() {
        this(30);
    }
    
    public PureDaisyLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();
        
        // 单一输入槽位
        slots.add(new SlotPosition(1, 1, baseX + slotSpacing, baseY + slotSpacing, 0, 
            SlotPosition.SlotType.INPUT, "input_block"));
        
        return slots;
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