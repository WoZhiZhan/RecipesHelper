package com.wzz.registerhelper.gui.recipe.component.renderer;

import com.wzz.registerhelper.gui.recipe.component.ComponentRenderer;
import com.wzz.registerhelper.gui.recipe.component.LabelComponent;
import com.wzz.registerhelper.gui.GuiTheme;
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

        int textWidth = Math.max(1, font.width(component.getText()));
        float scale = Math.min(1.0F, Math.min(
                component.getWidth() / (float) textWidth,
                component.getHeight() / (float) font.lineHeight));
        guiGraphics.pose().pushPose();
        try {
            guiGraphics.pose().translate(component.getX(), component.getY(), 0);
            guiGraphics.pose().scale(scale, scale, 1.0F);
            guiGraphics.drawString(font, component.getText(), 0, 0,
                    GuiTheme.readableLabelColor(component.getColor()), false);
        } finally {
            guiGraphics.pose().popPose();
        }
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
