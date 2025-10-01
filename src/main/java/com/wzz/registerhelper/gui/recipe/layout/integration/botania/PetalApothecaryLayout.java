package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * 花瓣坛布局
 */
public class PetalApothecaryLayout implements RecipeLayout {
    private final int slotSpacing;

    public PetalApothecaryLayout() {
        this(10);
    }

    public PetalApothecaryLayout(int spacing) {
        this.slotSpacing = spacing;
    }

    @Override
    public List<RecipeComponent> generateComponents(int offsetX, int offsetY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(offsetX + 150, offsetY + 80, "petal_0", 0));
        components.add(new SlotComponent(offsetX + 180, offsetY + 100, "petal_1", 1));
        components.add(new SlotComponent(offsetX + 200, offsetY + 140, "petal_2", 2));
        components.add(new SlotComponent(offsetX + 190, offsetY + 180, "petal_3", 3));
        components.add(new SlotComponent(offsetX + 170, offsetY + 210, "petal_4", 4));
        components.add(new SlotComponent(offsetX + 140, offsetY + 210, "petal_5", 5));
        components.add(new SlotComponent(offsetX + 120, offsetY + 180, "petal_6", 6));
        components.add(new SlotComponent(offsetX + 100, offsetY + 140, "petal_7", 7));
        components.add(new SlotComponent(offsetX + 120, offsetY + 100, "petal_8", 8));
        
        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 210, 220);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "Petal Apothecary";
    }
}