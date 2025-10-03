package com.wzz.registerhelper.gui.recipe.layout.integration.create;

import com.wzz.registerhelper.gui.recipe.component.*;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CompactingLayout implements RecipeLayout {
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(
            baseX + 54, baseY + 108,
            "compacting",
            0
        ));
        components.add(new SlotComponent(
            baseX + 90, baseY + 108,
            "comp_1",
            1
        ));
        components.add(new SlotComponent(
            baseX + 306, baseY + 108,
            "comp_2",
            2
        ));
        components.add(new StringInputComponent(
            baseX + 200, baseY + 108,
            80, "fluid",
            "文本", "minecraft:lava",
            "value", false
        ));
        components.add(new NumberInputComponent(
            baseX + 198, baseY + 162,
            60, "amount",
            "数值", 100,
            0, 1000,
            "value", false
        ));
        components.add(new LabelComponent(
            baseX + 162, baseY + 108,
            "comp_5", "Amount",
            12, 0x404040
        ));
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 315, 200);
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