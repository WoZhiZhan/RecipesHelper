package com.wzz.registerhelper.gui.recipe.layout.integration.create;

import com.wzz.registerhelper.gui.GuiText;
import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CompactingLayout implements RecipeLayout {
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        // Basin inputs on the left, mixer/output on the right. The JEI
        // category uses the same semantic arrangement for existing recipes.
        for (int i = 0; i < 9; i++) {
            components.add(new SlotComponent(baseX + 10 + (i % 3) * 24,
                    baseY + 20 + (i / 3) * 24, "ingredient_" + i, i));
        }
        components.add(new SlotComponent(baseX + 166, baseY + 44,
                "output", -1, SlotComponent.SlotRole.OUTPUT));
        components.add(new StringInputComponent(
            baseX + 84, baseY + 116,
            80, "fluid",
            GuiText.string("registerhelper.recipe_layout.text"), "",
            "value", true
        ));
        components.add(new NumberInputComponent(
            baseX + 168, baseY + 116,
            60, "amount",
            GuiText.string("registerhelper.recipe_layout.number"), 100,
            0, 1000,
            "value", false
        ));
        components.add(new StringInputComponent(
                baseX + 84, baseY + 146, 80, "fluidOut",
                GuiText.string("registerhelper.recipe_layout.text"), "", "value", true));
        components.add(new NumberInputComponent(
                baseX + 168, baseY + 146, 60, "fluidOutAmount",
                GuiText.string("registerhelper.recipe_layout.number"), 250,
                1, 10000, "value", true));
        components.add(new LabelComponent(
            baseX + 4, baseY + 176,
            "heat_label", "Heat",
            12, 0x404040
        ));
        components.add(new StringInputComponent(
                baseX + 84, baseY + 176, 100, "heatRequirement",
                GuiText.string("registerhelper.recipe_layout.text"), "",
                "value", true));
        components.add(new NumberInputComponent(
                baseX + 84, baseY + 206, 70, "processingTime",
                GuiText.string("registerhelper.recipe_layout.number"), 100,
                1, 100000, "value", true));
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 230, 240);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "CompactingLayout";
    }
}
