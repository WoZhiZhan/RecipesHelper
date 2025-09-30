package com.wzz.registerhelper.gui.recipe.layout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 矩形网格布局（现有的标准布局）
 */
public class RectangularLayout implements GridLayout {
    private final int maxWidth, maxHeight;
    private final int slotSpacing;
    
    public RectangularLayout(int maxWidth, int maxHeight) {
        this(maxWidth, maxHeight, 20);
    }
    
    public RectangularLayout(int maxWidth, int maxHeight, int slotSpacing) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();
        
        // 根据tier调整网格大小
        int width = supportsTiers() ? getTierSize(tier) : maxWidth;
        int height = supportsTiers() ? getTierSize(tier) : maxHeight;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelX = baseX + x * slotSpacing;
                int pixelY = baseY + y * slotSpacing;
                int index = y * width + x;
                
                slots.add(new SlotPosition(x, y, pixelX, pixelY, index, SlotPosition.SlotType.INPUT));
            }
        }
        
        return slots;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        int size = supportsTiers() ? getTierSize(tier) : Math.max(maxWidth, maxHeight);
        return new Rectangle(0, 0, size * slotSpacing, size * slotSpacing);
    }
    
    @Override
    public boolean supportsTiers() {
        return maxWidth == maxHeight; // 只有方形支持tier缩放
    }
    
    @Override
    public String getLayoutName() {
        return "Rectangular " + maxWidth + "x" + maxHeight;
    }
    
    private int getTierSize(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 5; 
            case 3 -> 7;
            case 4 -> 9;
            default -> 3;
        };
    }
}