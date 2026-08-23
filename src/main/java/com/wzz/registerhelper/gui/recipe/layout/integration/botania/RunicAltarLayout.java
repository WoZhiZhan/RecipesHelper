package com.wzz.registerhelper.gui.recipe.layout.integration.botania;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.GuiText;
import com.wzz.registerhelper.gui.recipe.component.LabelComponent;
import com.wzz.registerhelper.gui.recipe.component.NumberInputComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 植物魔法符文祭坛布局（环形）
 */
public class RunicAltarLayout implements RecipeLayout {
    private final int slotSpacing;
    
    public RunicAltarLayout() {
        this(30);
    }
    
    public RunicAltarLayout(int slotSpacing) {
        this.slotSpacing = slotSpacing;
    }
    
    @Override
    public List<RecipeComponent> generateComponents(int baseX, int baseY, int tier) {
        List<RecipeComponent> components = new ArrayList<>();
        
        // Coordinates mirror Botania's JEI category and are slot top-lefts.
        int centerX = baseX + 48;
        int centerY = baseY + 45;
        int radius = 32;

        // Runic Altar supports up to sixteen inputs. Do not derive the
        // visible slot count from the editor tier, otherwise valid recipes
        // with more than four inputs are truncated.
        int maxSlots = 16;
        
        for (int i = 0; i < maxSlots; i++) {
            double angle = 2 * Math.PI * i / maxSlots - Math.PI / 2;
            int x = (int) Math.round(centerX + radius * Math.cos(angle));
            int y = (int) Math.round(centerY + radius * Math.sin(angle));
            
            components.add(new SlotComponent(
                x, y,
                "runic_" + (i + 1),
                i
            ));
        }
        components.add(new SlotComponent(baseX + 86, baseY + 10,
                "output", -1, SlotComponent.SlotRole.OUTPUT));

        components.add(new NumberInputComponent(baseX + 6, baseY + 98,
                70, "mana", GuiText.string("registerhelper.recipe_layout.number"),
                5200, 1, 1000001, "value", false));
        components.add(new LabelComponent(baseX + 80, baseY + 98,
                "mana_label", "Mana"));
        
        return components;
    }
    
    @Override
    public Rectangle getBounds(int tier) {
        return new Rectangle(0, 0, 114, 125);
    }
    
    @Override
    public boolean supportsTiers() {
        return true; // 可以调整槽位数量
    }
    
    @Override
    public String getLayoutName() {
        return "Runic Altar (Circular)";
    }
}
