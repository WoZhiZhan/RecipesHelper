package com.wzz.registerhelper.gui.recipe.layout.integration.create;

import com.wzz.registerhelper.gui.GuiText;
import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CuttingLayout implements RecipeLayout {

    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();

        components.add(new SlotComponent(
                baseX + 24, baseY + 44,
                "create_cutting",
                0
        ));
        components.add(new SlotComponent(
                baseX + 166, baseY + 44,
                "output", -1, SlotComponent.SlotRole.OUTPUT));
        components.add(new NumberInputComponent(
                baseX + 198, baseY + 132,
                60, "processingTime",
                GuiText.string("registerhelper.recipe_layout.number"), 100,
                10, 10000,
                "value", false
        ));
        components.add(new LabelComponent(
                baseX + 132, baseY + 132,
                "comp_2", GuiText.string("registerhelper.recipe_layout.processing_time_ticks"),
                12, 0x404040
        ));

        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 210, 165);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "CuttingLayout";
    }
}
