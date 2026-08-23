package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.component.StringInputComponent;
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
        
        for (int i = 0; i < 16; i++) {
            components.add(new SlotComponent(offsetX + (i % 8) * 24,
                    offsetY + (i / 8) * 30, "ingredient_" + i, i));
        }
        components.add(new StringInputComponent(offsetX + 30, offsetY + 72,
                150, "brew", "Brew ID", "botania:speed", "value", false));
        
        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 190, 105);
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
