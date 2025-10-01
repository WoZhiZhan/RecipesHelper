package com.wzz.registerhelper.gui.recipe.layout.integration.farmersdelight;

import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CookingLayout implements RecipeLayout {
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();

        components.add(new SlotComponent(
            baseX + 44, baseY + 44,
            "comp_0",
            0
        ));
        components.add(new SlotComponent(
            baseX + 66, baseY + 44,
            "comp_1",
            1
        ));
        components.add(new SlotComponent(
            baseX + 88, baseY + 44,
            "comp_2",
            2
        ));
        components.add(new SlotComponent(
            baseX + 44, baseY + 66,
            "comp_3",
            3
        ));
        components.add(new SlotComponent(
            baseX + 66, baseY + 66,
            "comp_4",
            4
        ));
        components.add(new SlotComponent(
            baseX + 88, baseY + 66,
            "comp_5",
            5
        ));
        components.add(new NumberInputComponent(
            baseX + 66, baseY + 154,
            60, "experience",
            "experience", 1,
            1, 1000,
            "value", false
        ));
        components.add(new NumberInputComponent(
            baseX + 154, baseY + 154,
            60, "cookingtime",
            "cookingtime", 100,
            10, 10000,
            "value", false
        ));
        components.add(new LabelComponent(
            baseX + 66, baseY + 198,
            "comp_10", "经验",
            12, 0x404040
        ));
        components.add(new LabelComponent(
            baseX + 154, baseY + 198,
            "comp_11", "时间",
            12, 0x404040
        ));
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 200, 206);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "CookingLayout";
    }
}