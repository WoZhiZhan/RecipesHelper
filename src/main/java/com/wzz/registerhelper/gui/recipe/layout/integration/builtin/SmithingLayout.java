package com.wzz.registerhelper.gui.recipe.layout.integration.builtin;

import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 锻造台布局
 */
public class SmithingLayout implements GridLayout {
    private final int slotSpacing;

    public SmithingLayout() {
        this(30);
    }

    public SmithingLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }

    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();

        // 模板
        slots.add(new SlotPosition(0, 1, baseX, baseY + slotSpacing, 0,
                SlotPosition.SlotType.INPUT, "template"));

        // 基础物品
        slots.add(new SlotPosition(1, 1, baseX + slotSpacing, baseY + slotSpacing, 1,
                SlotPosition.SlotType.INPUT, "base"));

        // 添加材料
        slots.add(new SlotPosition(2, 1, baseX + 2 * slotSpacing, baseY + slotSpacing, 2,
                SlotPosition.SlotType.INPUT, "addition"));

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
        return "Smithing Table";
    }
}