package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.GuiText;
import com.wzz.registerhelper.gui.recipe.component.LabelComponent;
import com.wzz.registerhelper.gui.recipe.component.NumberInputComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 泰拉凝聚板布局（十字形）
 */
public class TerraPlateLayout implements RecipeLayout {
    private final int slotSpacing;

    public TerraPlateLayout() {
        this(30);
    }

    public TerraPlateLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }

    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();

        // Coordinates mirror Botania's JEI category and are slot top-lefts.
        int centerX = baseX + 48;
        int centerY = baseY + 37;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8.0 - Math.PI / 2;
            components.add(new SlotComponent(
                    (int) Math.round(centerX + Math.cos(angle) * 32),
                    (int) Math.round(centerY + Math.sin(angle) * 32),
                    "material_" + (i + 1), i));
        }
        components.add(new SlotComponent(centerX, centerY,
                "output", -1, SlotComponent.SlotRole.OUTPUT));
        components.add(new NumberInputComponent(baseX + 6, baseY + 126,
                70, "mana", GuiText.string("registerhelper.recipe_layout.number"),
                500000, 1, 1000001, "value", false));
        components.add(new LabelComponent(baseX + 80, baseY + 126,
                "mana_label", "Mana"));

        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 114, 140);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "Terra Plate (Cross Grid)";
    }
}
