package com.wzz.registerhelper.gui.recipe.component.renderer;

import com.wzz.registerhelper.gui.recipe.component.ComponentDataManager;
import com.wzz.registerhelper.gui.recipe.component.ComponentRenderer;
import com.wzz.registerhelper.gui.recipe.component.NumberInputComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.awt.*;

/**
 * 数值输入框渲染器
 */
public class NumberInputRenderer implements ComponentRenderer {
    private final NumberInputComponent component;
    private final EditBox editBox;
    private final ComponentDataManager dataManager;
    private boolean active = true;
    
    public NumberInputRenderer(NumberInputComponent component, Font font, ComponentDataManager dataManager) {
        this.component = component;
        this.dataManager = dataManager;
        if (font == null)
            font = Minecraft.getInstance().font;
        // 创建 EditBox
        this.editBox = new EditBox(font, 
            component.getX(), component.getY(), 
            component.getWidth(), component.getHeight(),
            Component.literal(component.getLabel()));
        // 设置默认值
        this.editBox.setValue(String.valueOf(component.getDefaultValue()));
        
        // 设置过滤器（只允许数字）
        this.editBox.setFilter(text -> {
            if (text.isEmpty()) return true;
            try {
                int value = Integer.parseInt(text);
                return value >= component.getMinValue() && value <= component.getMaxValue();
            } catch (NumberFormatException e) {
                return false;
            }
        });
        
        // 同步数据到管理器
        this.editBox.setResponder(text -> {
            try {
                int value = text.isEmpty() ? component.getDefaultValue() : Integer.parseInt(text);
                dataManager.setNumber(component.getId(), value);
            } catch (NumberFormatException e) {
                dataManager.setNumber(component.getId(), component.getDefaultValue());
            }
        });
        
        // 初始化数据
        dataManager.setNumber(component.getId(), component.getDefaultValue());
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!active) return;
        editBox.render(guiGraphics, mouseX, mouseY, 0);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active) return false;
        return editBox.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active) return false;
        return editBox.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!active) return false;
        return editBox.charTyped(codePoint, modifiers);
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
        editBox.setVisible(active);
    }
    
    public EditBox getEditBox() {
        return editBox;
    }
}