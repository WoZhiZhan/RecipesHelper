package com.wzz.registerhelper.gui.recipe.component.renderer;

import com.wzz.registerhelper.gui.recipe.component.ComponentRenderer;
import com.wzz.registerhelper.gui.recipe.component.LabelComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.*;

/**
 * 标签渲染器
 */
public class LabelRenderer implements ComponentRenderer {
    private final LabelComponent component;
    private boolean active = true;
    
    public LabelRenderer(LabelComponent component) {
        this.component = component;
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!active) return;
        
        guiGraphics.drawString(font, component.getText(), 
            component.getX(), component.getY(), 
            component.getColor(), false);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false; // 标签不处理点击
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
    
    @Override
    public Rectangle getBounds() {
        return component.getBounds();
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
}