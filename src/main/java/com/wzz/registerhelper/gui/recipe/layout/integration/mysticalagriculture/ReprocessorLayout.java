package com.wzz.registerhelper.gui.recipe.layout.integration.mysticalagriculture;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * 神秘农业再处理器布局
 */
public class ReprocessorLayout implements RecipeLayout {
    private final int slotSpacing;

    public ReprocessorLayout() {
        this(30);
    }

    public ReprocessorLayout(int spacing) {
        this.slotSpacing = spacing;
    }

    @Override
    public List<RecipeComponent> generateComponents(int offsetX, int offsetY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(
            offsetX + 60, 
            offsetY + 120, 
            "input",
            0
        ));
        
        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 90, 150);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "Reprocessor";
    }
}