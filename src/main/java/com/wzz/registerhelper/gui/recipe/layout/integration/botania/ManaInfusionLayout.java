package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 魔力灌注布局（横向三个：输入 - 催化剂 - 输出）
 */
public class ManaInfusionLayout implements RecipeLayout {
    private final int slotSpacing;
    
    public ManaInfusionLayout() {
        this(30);
    }
    
    public ManaInfusionLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        // 输入物品
        components.add(new SlotComponent(
            baseX, 
            baseY + slotSpacing, 
            "input",
            0
        ));
        
        // 催化剂（可选）
        components.add(new SlotComponent(
            baseX + slotSpacing, 
            baseY + slotSpacing, 
            "catalyst",
            1
        ));
        
        return components;
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
    
    @Override
    public LayoutType getLayoutType() {
        return LayoutType.GRID; // 纯槽位布局
    }
}