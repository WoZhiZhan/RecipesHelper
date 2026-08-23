package com.wzz.registerhelper.gui.recipe.layout.integration.create;

import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PressingLayout implements RecipeLayout {
    
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
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 210, 180);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "PressingLayout";
    }
}
