package com.wzz.registerhelper.gui.recipe.layout.integration.mysticalagriculture;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * 神秘农业注入布局
 */
public class InfusionLayout implements RecipeLayout {
    private final int slotSpacing;

    public InfusionLayout() {
        this(30);
    }

    public InfusionLayout(int spacing) {
        this.slotSpacing = spacing;
    }

    @Override
    public List<RecipeComponent> generateComponents(int offsetX, int offsetY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        components.add(new SlotComponent(offsetX + 120, offsetY + 90, "slot_0", 0));
        components.add(new SlotComponent(offsetX + 150, offsetY + 60, "slot_1", 1));
        components.add(new SlotComponent(offsetX + 210, offsetY + 120, "slot_2", 2));
        components.add(new SlotComponent(offsetX + 90, offsetY + 120, "slot_3",3));
        components.add(new SlotComponent(offsetX + 120, offsetY + 150, "slot_4", 4));
        components.add(new SlotComponent(offsetX + 150, offsetY + 180, "slot_5", 5));
        components.add(new SlotComponent(offsetX + 180, offsetY + 150, "slot_6", 6));
        components.add(new SlotComponent(offsetX + 180, offsetY + 90, "slot_7",7));
        components.add(new SlotComponent(offsetX + 150, offsetY + 120, "slot_8", 8));
        
        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 240, 210);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "Infusion Altar";
    }
}