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
 * 魔力灌注布局（横向三个：输入 - 催化剂 - 输出）
 */
public class ManaInfusionLayout implements RecipeLayout {
    private final int slotSpacing;
    
    public ManaInfusionLayout() {
        this(30);
    }
    
    public ManaInfusionLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(baseX + 32, baseY + 12, "input", 0));
        
        // 催化剂（可选）
        components.add(new SlotComponent(baseX + 12, baseY + 12,
                "catalyst", 1, SlotComponent.SlotRole.CATALYST));
        components.add(new SlotComponent(baseX + 93, baseY + 12,
                "output", -1, SlotComponent.SlotRole.OUTPUT));

        components.add(new NumberInputComponent(baseX + 20, baseY + 58,
                70, "mana", GuiText.string("registerhelper.recipe_layout.number"),
                1000, 1, 1000001, "value", false));
        components.add(new LabelComponent(baseX + 94, baseY + 58,
                "mana_label", "Mana"));
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 142, 86);
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return "Mana Infusion (Horizontal)";
    }
    
    @Override
    public LayoutType getLayoutType() {
        return LayoutType.GRID; // 纯槽位布局
    }
}
