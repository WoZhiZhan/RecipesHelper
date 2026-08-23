package com.wzz.registerhelper.gui.recipe.layout.integration.create;

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

/** Basic editor for Create sequenced assembly recipes. */
public class SequencedAssemblyLayout implements RecipeLayout {
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        components.add(new SlotComponent(baseX + 18, baseY + 18,
                "input", 0, SlotComponent.SlotRole.INPUT));
        components.add(new SlotComponent(baseX + 72, baseY + 18,
                "transitionalItem", 1, SlotComponent.SlotRole.TRANSITIONAL));
        components.add(new SlotComponent(baseX + 158, baseY + 18,
                "output", -1, SlotComponent.SlotRole.OUTPUT));
        components.add(new LabelComponent(baseX + 4, baseY + 44, "sequence_label", "Sequence"));
        components.add(new StringInputComponent(baseX + 72, baseY + 42, 130,
                "sequenceType", GuiText.string("registerhelper.recipe_layout.text"),
                "create:pressing", "value", false));
        components.add(new NumberInputComponent(baseX + 72, baseY + 70, 60,
                "loops", GuiText.string("registerhelper.recipe_layout.number"),
                5, 1, 1000, "value", false));
        components.add(new LabelComponent(baseX + 136, baseY + 70, "loops_label", "Loops"));
        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 215, 105);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "Sequenced Assembly";
    }
}
