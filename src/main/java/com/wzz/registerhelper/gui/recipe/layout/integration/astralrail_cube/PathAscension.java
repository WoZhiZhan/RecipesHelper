package com.wzz.registerhelper.gui.recipe.layout.integration.astralrail_cube;

import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PathAscension implements RecipeLayout {
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(
            baseX + 88, baseY + 66,
            "s1",
            0
        ));
        components.add(new SlotComponent(
            baseX + 88, baseY + 110,
            "s2",
            1
        ));
        components.add(new SlotComponent(
            baseX + 154, baseY + 66,
            "s3",
            2
        ));
        components.add(new SlotComponent(
            baseX + 154, baseY + 110,
            "s4",
            3
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
        return "PathAscension";
    }
}