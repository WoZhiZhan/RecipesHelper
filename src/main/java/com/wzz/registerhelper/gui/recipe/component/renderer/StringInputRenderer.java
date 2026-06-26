package com.wzz.registerhelper.gui.recipe.component.renderer;

import com.wzz.registerhelper.gui.recipe.component.ComponentDataManager;
import com.wzz.registerhelper.gui.recipe.component.ComponentRenderer;
import com.wzz.registerhelper.gui.recipe.component.StringInputComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.awt.*;

/**
 * 字符串输入框渲染器
 */
public class StringInputRenderer implements ComponentRenderer {
    private final StringInputComponent component;
    private final EditBox editBox;
    private final ComponentDataManager dataManager;
    private boolean active = true;
    
    public StringInputRenderer(StringInputComponent component, Font font, ComponentDataManager dataManager) {
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
        this.editBox.setValue(component.getValue());
        
        // 设置最大长度
        this.editBox.setMaxLength(256);
        
        // 同步数据到管理器
        this.editBox.setResponder(text -> {
            dataManager.setString(component.getId(), text);
        });
        
        // 初始化数据
        dataManager.setString(component.getId(), component.getValue());
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