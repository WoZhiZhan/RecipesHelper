package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 酿造台布局（容器+材料）
 */
public class BrewingLayout implements GridLayout {
    private final int slotSpacing;

    public BrewingLayout() {
        this(30);
    }

    public BrewingLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }

    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();

        // 容器槽位（中心）
        int centerX = baseX + 2 * slotSpacing;
        int centerY = baseY + 2 * slotSpacing;
        slots.add(new SlotPosition(2, 2, centerX, centerY, 0,
            SlotPosition.SlotType.INPUT, "container"));

        // 材料槽位（围绕容器）
        int[][] positions = {
            {2, 1}, // 上
            {3, 2}, // 右
            {2, 3}, // 下
            {1, 2}  // 左
        };

        for (int i = 0; i < positions.length; i++) {
            int x = baseX + positions[i][0] * slotSpacing;
            int y = baseY + positions[i][1] * slotSpacing;
            slots.add(new SlotPosition(positions[i][0], positions[i][1], x, y, i + 1,
                SlotPosition.SlotType.INPUT, "ingredient_" + (i + 1)));
        }

        return slots;
    }

    @Override
    public Rectangle getBounds(int tier) {
        int size = 5 * slotSpacing;
        return new Rectangle(0, 0, size, size);
    }

    @Override
    public boolean supportsTiers() {
        return false;
    }

    @Override
    public String getLayoutName() {
        return "Brewing (Container + Ingredients)";
    }
}