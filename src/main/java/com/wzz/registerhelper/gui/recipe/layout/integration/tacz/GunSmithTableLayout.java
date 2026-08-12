package com.wzz.registerhelper.gui.recipe.layout.integration.tacz;

import com.wzz.registerhelper.gui.GuiText;
import com.wzz.registerhelper.gui.recipe.component.LabelComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** TACZ gun smith table layout: a labelled 3x3 ingredient grid. */
public class GunSmithTableLayout implements RecipeLayout {
    private static final int SLOT_SPACING = 20;
    private static final int GRID_SIZE = 3;

    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        components.add(new LabelComponent(baseX, baseY - 25, "label_title",
                GuiText.string("registerhelper.recipe_layout.tacz.title")));
        components.add(new LabelComponent(baseX, baseY - 12, "label_info",
                GuiText.string("registerhelper.recipe_layout.tacz.hint")));

        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                int index = y * GRID_SIZE + x;
                components.add(new SlotComponent(
                        baseX + x * SLOT_SPACING,
                        baseY + y * SLOT_SPACING,
                        "ingredient_" + index,
                        index));
            }
        }
        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, -30, GRID_SIZE * SLOT_SPACING,
                GRID_SIZE * SLOT_SPACING + 30);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "TACZ Gun Smith Table";
    }

    @Override
    public LayoutType getLayoutType() {
        return LayoutType.MIXED;
    }
}
