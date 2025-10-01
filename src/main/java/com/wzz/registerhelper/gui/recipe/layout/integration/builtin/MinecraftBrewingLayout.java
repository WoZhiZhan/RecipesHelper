package com.wzz.registerhelper.gui.recipe.layout.integration.builtin;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 原版酿造台布局
 */
public class MinecraftBrewingLayout implements RecipeLayout {
    private final int slotSpacing;

    public MinecraftBrewingLayout() {
        this(30);
    }

    public MinecraftBrewingLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }

    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();

        // 基础药水（底部中心）
        components.add(new SlotComponent(
            baseX + slotSpacing, 
            baseY + 2 * slotSpacing, 
            "base_potion",
            0
        ));

        // 酿造材料（顶部中心）
        components.add(new SlotComponent(
            baseX + slotSpacing, 
            baseY, 
            "reagent",
            1
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
        return "Brewing Stand";
    }
}