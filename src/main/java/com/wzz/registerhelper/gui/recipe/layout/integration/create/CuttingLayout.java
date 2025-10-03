package com.wzz.registerhelper.gui.recipe.layout.integration.create;

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
                baseX + 66, baseY + 66,
                "create_cutting",
                0
        ));
        components.add(new NumberInputComponent(
                baseX + 198, baseY + 132,
                60, "processingTime",
                "数值", 100,
                10, 10000,
                "value", false
        ));
        components.add(new LabelComponent(
                baseX + 132, baseY + 132,
                "comp_2", "处理事件(tick)",
                12, 0x404040
        ));

        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 228, 200);
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