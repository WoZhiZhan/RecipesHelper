package com.wzz.registerhelper.gui.recipe.layout.integration.builtin;

import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 原版酿造台布局
 */
public class MinecraftBrewingLayout implements GridLayout {
    private final int slotSpacing;

    public MinecraftBrewingLayout() {
        this(30);
    }

    public MinecraftBrewingLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }

    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();

        // 基础药水（底部中心）
        slots.add(new SlotPosition(1, 2, baseX + slotSpacing, baseY + 2 * slotSpacing, 0,
                SlotPosition.SlotType.INPUT, "base_potion"));

        // 酿造材料（顶部中心）
        slots.add(new SlotPosition(1, 0, baseX + slotSpacing, baseY, 1,
                SlotPosition.SlotType.INPUT, "reagent"));

        return slots;
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