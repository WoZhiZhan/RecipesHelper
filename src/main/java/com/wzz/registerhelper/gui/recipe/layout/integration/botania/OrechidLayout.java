package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.GuiText;
import com.wzz.registerhelper.gui.recipe.component.LabelComponent;
import com.wzz.registerhelper.gui.recipe.component.NumberInputComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.component.StringInputComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** Layout for Botania block conversion recipes such as Orechid. */
public class OrechidLayout implements RecipeLayout {
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        components.add(new SlotComponent(baseX + 30, baseY + 20, "input_block", 0));
        components.add(new SlotComponent(baseX + 86, baseY + 20,
                "output", -1, SlotComponent.SlotRole.OUTPUT));
        components.add(new NumberInputComponent(baseX + 30, baseY + 60, 70,
                "weight", GuiText.string("registerhelper.recipe_layout.number"),
                67415, 1, 1000000, "value", false));
        components.add(new LabelComponent(baseX + 104, baseY + 60,
                "weight_label", "Weight"));
        components.add(new StringInputComponent(baseX + 30, baseY + 86, 170,
                "biome_bonus_tag", GuiText.string("registerhelper.recipe_layout.text"),
                "botania:marimorphosis_desert_bonus", "value", true));
        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 205, 125);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "Orechid";
    }
}
