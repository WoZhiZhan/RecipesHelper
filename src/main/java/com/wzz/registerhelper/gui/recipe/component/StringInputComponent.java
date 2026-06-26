package com.wzz.registerhelper.gui.recipe.component;

/**
 * 字符串输入框组件
 */
public class StringInputComponent extends RecipeComponent {
    private final String label;
    private final String value;
    private final String propertyKey;
    private final boolean optional; // 是否可选

    public StringInputComponent(int x, int y, int width, String id,
                                String label, String value,
                                String propertyKey, boolean optional) {
        super(x, y, width, 20, id);
        this.label = label;
        this.value = value;
        this.propertyKey = propertyKey;
        this.optional = optional;
    }
    
    @Override
    public ComponentType getType() {
        return ComponentType.NUMBER_INPUT;
    }
    
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public String getPropertyKey() { return propertyKey; }
    public boolean isOptional() { return optional; }
}