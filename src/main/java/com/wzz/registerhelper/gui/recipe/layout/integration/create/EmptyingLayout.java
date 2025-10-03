package com.wzz.registerhelper.gui.recipe.layout.integration.create;

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
            baseX + 66, baseY + 66,
            "comp_0",
            0
        ));
        components.add(new SlotComponent(
            baseX + 132, baseY + 154,
            "comp_1",
            1
        ));
        components.add(new StringInputComponent(
            baseX + 132, baseY + 66,
            80, "fluid",
            "液体", "create:tea",
            "fluidOutput", false
        ));
        components.add(new NumberInputComponent(
                baseX + 154, baseY + 110,
                60, "fluidAmount",
                "数值", 250,
                0, 10000,
                "value", false
        ));
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 200, 200);
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