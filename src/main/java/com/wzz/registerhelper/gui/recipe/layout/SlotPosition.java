package com.wzz.registerhelper.gui.recipe.layout;

/**
 * 槽位位置信息
 */
public class SlotPosition {
    private final int x, y;           // 相对位置（网格坐标）
    private final int pixelX, pixelY; // 绝对像素位置
    private final int index;          // 槽位索引
    private final SlotType type;      // 槽位类型
    private final String label;       // 槽位标签（可选）
    
    public enum SlotType {
        INPUT,           // 普通输入
        CENTER,          // 中央槽位（如神秘农业的中心）
        CATALYST,        // 催化剂槽位
        TEMPLATE,        // 模板槽位（如压印机的压印版）
        FUEL,            // 燃料槽位
        OUTPUT,          // 输出槽位
        SECONDARY_OUTPUT // 副产品输出
    }
    
    public SlotPosition(int x, int y, int pixelX, int pixelY, int index, SlotType type) {
        this(x, y, pixelX, pixelY, index, type, null);
    }
    
    public SlotPosition(int x, int y, int pixelX, int pixelY, int index, SlotType type, String label) {
        this.x = x;
        this.y = y;
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.index = index;
        this.type = type;
        this.label = label;
    }
    
    // Getters
    public int getX() { return x; }
    public int getY() { return y; }
    public int getPixelX() { return pixelX; }
    public int getPixelY() { return pixelY; }
    public int getIndex() { return index; }
    public SlotType getType() { return type; }
    public String getLabel() { return label; }
}