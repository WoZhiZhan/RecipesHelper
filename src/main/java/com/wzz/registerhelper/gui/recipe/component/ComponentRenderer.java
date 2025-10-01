package com.wzz.registerhelper.gui.recipe.component;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

/**
 * 组件渲染器接口
 */
public interface ComponentRenderer {
    /**
     * 渲染组件
     */
    void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY);
    
    /**
     * 处理鼠标点击
     */
    boolean mouseClicked(double mouseX, double mouseY, int button);
    
    /**
     * 处理键盘输入
     */
    boolean keyPressed(int keyCode, int scanCode, int modifiers);
    
    /**
     * 处理字符输入
     */
    boolean charTyped(char codePoint, int modifiers);
    
    /**
     * 获取组件边界
     */
    java.awt.Rectangle getBounds();
    
    /**
     * 是否激活（可交互）
     */
    boolean isActive();
    
    /**
     * 设置激活状态
     */
    void setActive(boolean active);
}