package com.wzz.registerhelper.gui.recipe.layout.integration.mysticalagriculture;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Awakening altar layout: one altar input, four pedestal inputs, and four essence slots.
 */
public class AwakeningLayout implements RecipeLayout {
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();

        components.add(new SlotComponent(baseX + 108, baseY + 72, "awakening_input", 0));
        components.add(new SlotComponent(baseX + 72, baseY + 72, "awakening_ingredient_0", 1));
        components.add(new SlotComponent(baseX + 108, baseY + 36, "awakening_ingredient_1", 2));
        components.add(new SlotComponent(baseX + 144, baseY + 72, "awakening_ingredient_2", 3));
        components.add(new SlotComponent(baseX + 108, baseY + 108, "awakening_ingredient_3", 4));

        components.add(new SlotComponent(baseX + 48, baseY + 156, "awakening_essence_0", 5));
        components.add(new SlotComponent(baseX + 84, baseY + 156, "awakening_essence_1", 6));
        components.add(new SlotComponent(baseX + 120, baseY + 156, "awakening_essence_2", 7));
        components.add(new SlotComponent(baseX + 156, baseY + 156, "awakening_essence_3", 8));
        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 222, 192);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "Awakening Altar";
    }
}
