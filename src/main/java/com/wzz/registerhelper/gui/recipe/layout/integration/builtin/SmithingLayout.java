package com.wzz.registerhelper.gui.recipe.layout.integration.builtin;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 锻造台布局
 */
public class SmithingLayout implements RecipeLayout {
    private final int slotSpacing;

    public SmithingLayout() {
        this(30);
    }

    public SmithingLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }

    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();

        // 模板
        components.add(new SlotComponent(
            baseX, 
            baseY + slotSpacing, 
            "template",
            0
        ));

        // 基础物品
        components.add(new SlotComponent(
            baseX + slotSpacing, 
            baseY + slotSpacing, 
            "base",
            1
        ));

        // 添加材料
        components.add(new SlotComponent(
            baseX + 2 * slotSpacing, 
            baseY + slotSpacing, 
            "addition",
            2
        ));

        return components;
    }

    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 3 * slotSpacing, 3 * slotSpacing);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "Smithing Table";
    }
}