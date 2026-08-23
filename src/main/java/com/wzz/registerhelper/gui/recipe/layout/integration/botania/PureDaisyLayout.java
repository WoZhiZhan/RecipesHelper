package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.GuiText;
import com.wzz.registerhelper.gui.recipe.component.LabelComponent;
import com.wzz.registerhelper.gui.recipe.component.NumberInputComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯洁雏菊布局（单一转换）
 */
public class PureDaisyLayout implements RecipeLayout {
    private final int slotSpacing;
    
    public PureDaisyLayout() {
        this(30);
    }
    
    public PureDaisyLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        // 单一输入槽位
        components.add(new SlotComponent(baseX + 9, baseY + 12, "input_block", 0));
        components.add(new SlotComponent(baseX + 68, baseY + 12,
                "output", -1, SlotComponent.SlotRole.OUTPUT));
        components.add(new NumberInputComponent(baseX + 6, baseY + 58,
                70, "time", GuiText.string("registerhelper.recipe_layout.number"),
                150, 0, 100000, "value", false));
        components.add(new LabelComponent(baseX + 80, baseY + 58,
                "time_label", "Time"));
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 114, 90);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "Pure Daisy (Single)";
    }
}
