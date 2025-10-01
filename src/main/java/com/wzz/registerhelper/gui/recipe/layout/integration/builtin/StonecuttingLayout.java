package com.wzz.registerhelper.gui.recipe.layout.integration.builtin;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 切石机布局
 */
public class StonecuttingLayout implements RecipeLayout {
    private final int slotSpacing;

    public StonecuttingLayout() {
        this(30);
    }

    public StonecuttingLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }

    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        // 输入槽位
        components.add(new SlotComponent(
            baseX + slotSpacing, 
            baseY + slotSpacing, 
            "input",
            0
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
        return "Stonecutting";
    }
}