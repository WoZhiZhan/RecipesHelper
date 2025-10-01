package com.wzz.registerhelper.gui.recipe.component;

/**
 * 文本标签组件
 */
public class LabelComponent extends RecipeComponent {
    private final String text;
    private final int fontSize;
    private final int color;
    
    public LabelComponent(int x, int y, String id, String text, int fontSize, int color) {
        super(x, y, 100, 20, id);
        this.text = text;
        this.fontSize = fontSize;
        this.color = color;
    }
    
    public LabelComponent(int x, int y, String id, String text) {
        this(x, y, id, text, 12, 0x404040);
    }
    
    @Override
    public ComponentType getType() {
        return ComponentType.LABEL;
    }
    
    public String getText() { return text; }
    public int getFontSize() { return fontSize; }
    public int getColor() { return color; }
}