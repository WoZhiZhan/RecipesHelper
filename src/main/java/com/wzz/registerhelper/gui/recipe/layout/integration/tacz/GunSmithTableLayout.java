package com.wzz.registerhelper.gui.recipe.layout.integration.tacz;

import com.wzz.registerhelper.gui.recipe.component.LabelComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * TACZ枪械工作台布局
 * 3x3材料槽位 + 1个输出槽
 */
public class GunSmithTableLayout implements RecipeLayout {
    private static final int SLOT_SPACING = 20;
    private static final int GRID_SIZE = 3;
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        // 添加说明标签
        components.add(new LabelComponent(
            baseX, baseY - 25,
            "label_title",
            "§6TACZ枪械工作台"
        ));
        
        components.add(new LabelComponent(
            baseX, baseY - 12,
            "label_info",
            "§7添加材料和输出物品"
        ));
        
        // 材料槽位 (3x3网格)
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                int pixelX = baseX + x * SLOT_SPACING;
                int pixelY = baseY + y * SLOT_SPACING;
                int index = y * GRID_SIZE + x;
                
                components.add(new SlotComponent(
                    pixelX, pixelY,
                    "ingredient_" + index,
                    index
                ));
            }
        }
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        // 3x3网格的边界
        int width = GRID_SIZE * SLOT_SPACING;
        int height = GRID_SIZE * SLOT_SPACING + 30; // 额外空间给标签
        return new Rectangle(0, -30, width, height);
    }
    
    @Override
    public boolean supportsTiers() {
        return false; // TACZ不支持tier
    }
    
    @Override
    public String getLayoutName() {
        return "TACZ Gun Smith Table";
    }
    
    @Override
    public LayoutType getLayoutType() {
        return LayoutType.MIXED; // 混合布局（槽位+标签）
    }
}