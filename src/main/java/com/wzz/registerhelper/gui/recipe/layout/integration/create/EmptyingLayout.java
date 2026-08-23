package com.wzz.registerhelper.gui.recipe.layout.integration.create;

import com.wzz.registerhelper.gui.GuiText;
import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class EmptyingLayout implements RecipeLayout {
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(
            baseX + 24, baseY + 44,
            "comp_0",
            0
        ));
        components.add(new SlotComponent(
                baseX + 166, baseY + 44,
                "output", -1, SlotComponent.SlotRole.OUTPUT));
        components.add(new StringInputComponent(
            baseX + 50, baseY + 92,
            80, "fluid",
             GuiText.string("registerhelper.recipe_layout.fluid"), "create:tea",
            "fluidOutput", false
        ));
        components.add(new NumberInputComponent(
                baseX + 126, baseY + 92,
                60, "fluidAmount",
                GuiText.string("registerhelper.recipe_layout.number"), 250,
                0, 10000,
                "value", false
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
        return "EmptyingLayout";
    }
}
