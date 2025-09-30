package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 泰拉凝聚板布局（网格形）
 */
public class TerraPlateLayout implements GridLayout {
    private final int slotSpacing;

    public TerraPlateLayout() {
        this(30);
    }

    public TerraPlateLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }

    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();

        // 3x3网格，但只使用特定位置
        int[][] positions = {
                {1, 0}, // 上
                {0, 1}, // 左
                {1, 1}, // 中
                {2, 1}, // 右
                {1, 2}  // 下
        };

        for (int i = 0; i < positions.length; i++) {
            int x = baseX + positions[i][0] * slotSpacing;
            int y = baseY + positions[i][1] * slotSpacing;
            slots.add(new SlotPosition(positions[i][0], positions[i][1], x, y, i,
                    SlotPosition.SlotType.INPUT, "material_" + (i + 1)));
        }

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
        return "Terra Plate (Cross Grid)";
    }
}