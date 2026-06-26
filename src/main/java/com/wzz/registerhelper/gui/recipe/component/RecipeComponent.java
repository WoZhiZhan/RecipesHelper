package com.wzz.registerhelper.gui.recipe.component;

import java.awt.*;

/**
 * 配方组件基类
 */
public abstract class RecipeComponent {
    protected int x, y, width, height;
    protected String id;
    
    public RecipeComponent(int x, int y, int width, int height, String id) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.id = id;
    }
    
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }
    
    public abstract ComponentType getType();
    
    // Getters
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getId() { return id; }
    
    // Setters (用于坐标更新)
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public enum ComponentType {
        SLOT,           // 物品槽位
        NUMBER_INPUT,   // 数值输入框
        STRING_INPUT,     // 文本输入框
        LABEL          // 文本标签
    }
}