package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Botania 酿造布局（5个横向槽位）
 */
public class BrewingLayout implements RecipeLayout {
    private final int slotSpacing;

    public BrewingLayout() {
        this(30);
    }

    public BrewingLayout(int spacing) {
        this.slotSpacing = spacing;
    }

    @Override
    public List<RecipeComponent> generateComponents(int offsetX, int offsetY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(offsetX + 60, offsetY + 90, "ingredient_0", 0));
        components.add(new SlotComponent(offsetX + 90, offsetY + 90, "ingredient_1", 1));
        components.add(new SlotComponent(offsetX + 120, offsetY + 90, "ingredient_2", 2));
        components.add(new SlotComponent(offsetX + 150, offsetY + 90, "ingredient_3", 3));
        components.add(new SlotComponent(offsetX + 180, offsetY + 90, "ingredient_4", 4));
        
        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 210, 120);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "Botania Brewing";
    }
}