package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 植物魔法符文祭坛布局（环形）
 */
public class RunicAltarLayout implements RecipeLayout {
    private final int slotSpacing;
    
    public RunicAltarLayout() {
        this(30);
    }
    
    public RunicAltarLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        // 计算圆形布局
        int centerX = baseX + 3 * slotSpacing;
        int centerY = baseY + 3 * slotSpacing;
        int radius = 2 * slotSpacing;
        
        // 最多16个槽位围成圆圈
        int maxSlots = Math.min(16, tier * 4); // 根据tier调整槽位数量
        
        for (int i = 0; i < maxSlots; i++) {
            double angle = 2 * Math.PI * i / maxSlots;
            int x = (int) (centerX + radius * Math.cos(angle));
            int y = (int) (centerY + radius * Math.sin(angle));
            
            components.add(new SlotComponent(
                x, y,
                "runic_" + (i + 1),
                i
            ));
        }
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        int size = 6 * slotSpacing;
        return new Rectangle(0, 0, size, size);
    }
    
    @Override
    public boolean supportsTiers() {
        return true; // 可以调整槽位数量
    }
    
    @Override
    public String getLayoutName() {
        return "Runic Altar (Circular)";
    }
}