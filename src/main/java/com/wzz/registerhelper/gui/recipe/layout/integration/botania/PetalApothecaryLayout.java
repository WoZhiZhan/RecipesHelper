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
        
        int centerX = offsetX + 48;
        int centerY = offsetY + 45;
        int radius = 32;
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16.0 - Math.PI / 2;
            components.add(new SlotComponent(
                    (int) Math.round(centerX + Math.cos(angle) * radius),
                    (int) Math.round(centerY + Math.sin(angle) * radius),
                    "petal_" + i, i));
        }
        // Botania's water/reagent point sits on the upper-left side of the altar.
        components.add(new SlotComponent(offsetX + 39, offsetY + 40,
                "reagent", 16, SlotComponent.SlotRole.REAGENT));
        components.add(new SlotComponent(offsetX + 86, offsetY + 10,
                "output", -1, SlotComponent.SlotRole.OUTPUT));
        
        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 114, 105);
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
