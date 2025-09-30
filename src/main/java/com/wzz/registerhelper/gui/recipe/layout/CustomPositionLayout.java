package com.wzz.registerhelper.gui.recipe.layout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义位置布局（完全自由定义）
 */
public class CustomPositionLayout implements GridLayout {
    private final List<SlotTemplate> slotTemplates;
    private final Rectangle bounds;
    private final String name;
    
    public static class SlotTemplate {
        public final int x, y;
        public final SlotPosition.SlotType type;
        public final String label;
        
        public SlotTemplate(int x, int y, SlotPosition.SlotType type, String label) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.label = label;
        }
    }
    
    public CustomPositionLayout(String name, Rectangle bounds, List<SlotTemplate> templates) {
        this.name = name;
        this.bounds = bounds;
        this.slotTemplates = new ArrayList<>(templates);
    }
    
    @Override
    public List<SlotPosition> generateSlots(int baseX, int baseY, int tier) {
        List<SlotPosition> slots = new ArrayList<>();
        
        for (int i = 0; i < slotTemplates.size(); i++) {
            SlotTemplate template = slotTemplates.get(i);
            int pixelX = baseX + template.x;
            int pixelY = baseY + template.y;
            
            slots.add(new SlotPosition(template.x / 20, template.y / 20, pixelX, pixelY, i, 
                template.type, template.label));
        }
        
        return slots;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return bounds;
    }
    
    @Override
    public boolean supportsTiers() {
        return false;
    }
    
    @Override
    public String getLayoutName() {
        return name;
    }
}