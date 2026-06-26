package com.wzz.registerhelper.gui.recipe.component;

/**
 * 数值输入框组件
 */
public class NumberInputComponent extends RecipeComponent {
    private final String label;
    private final int defaultValue;
    private final int minValue;
    private final int maxValue;
    private final String propertyKey;
    private final boolean optional; // 是否可选
    
    public NumberInputComponent(int x, int y, int width, String id,
                                String label, int defaultValue,
                                int minValue, int maxValue, 
                                String propertyKey, boolean optional) {
        super(x, y, width, 20, id);
        this.label = label;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.propertyKey = propertyKey;
        this.optional = optional;
    }
    
    @Override
    public ComponentType getType() {
        return ComponentType.NUMBER_INPUT;
    }
    
    public String getLabel() { return label; }
    public int getDefaultValue() { return defaultValue; }
    public int getMinValue() { return minValue; }
    public int getMaxValue() { return maxValue; }
    public String getPropertyKey() { return propertyKey; }
    public boolean isOptional() { return optional; }
}