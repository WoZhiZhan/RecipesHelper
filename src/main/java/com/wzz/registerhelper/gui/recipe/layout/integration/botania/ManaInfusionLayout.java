package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 魔力灌注布局（横向三个）
 */
public class ManaInfusionLayout implements GridLayout {
    private final int slotSpacing;
    
    public ManaInfusionLayout() {
        this(30);
    }
    
    public ManaInfusionLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();
        
        // 输入物品
        slots.add(new SlotPosition(0, 1, baseX, baseY + slotSpacing, 0, 
            SlotPosition.SlotType.INPUT, "input"));
        
        // 催化剂（可选）
        slots.add(new SlotPosition(1, 1, baseX + slotSpacing, baseY + slotSpacing, 1, 
            SlotPosition.SlotType.CATALYST, "catalyst"));
        
        // 输出
        slots.add(new SlotPosition(2, 1, baseX + 2 * slotSpacing, baseY + slotSpacing, 2, 
            SlotPosition.SlotType.OUTPUT, "output"));
        
        return slots;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 3 * slotSpacing, 3 * slotSpacing);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "Mana Infusion (Horizontal)";
    }
}