package com.wzz.registerhelper.gui.recipe.layout.integration.builtin;

import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 切石机布局
 */
public class StonecuttingLayout implements GridLayout {
    private final int slotSpacing;

    public StonecuttingLayout() {
        this(30);
    }

    public StonecuttingLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }

    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();
        slots.add(new SlotPosition(1, 1, baseX + slotSpacing, baseY + slotSpacing, 0,
                SlotPosition.SlotType.INPUT, "input"));
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
        return "Stonecutting";
    }
}