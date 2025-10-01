package com.wzz.registerhelper.gui.recipe.layout.integration.farmersdelight;

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
            "comp_0",
            0
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
        return "CuttingLayout";
    }
}