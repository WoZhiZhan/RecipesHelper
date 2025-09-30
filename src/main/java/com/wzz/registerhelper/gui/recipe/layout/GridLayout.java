package com.wzz.registerhelper.gui.recipe.layout;

import java.awt.*;
import java.util.List;

/**
 * 槽位布局接口
 */
public interface GridLayout {
    /**
     * 生成槽位位置列表
     * @param baseX 基础X坐标
     * @param baseY 基础Y坐标 
     * @param tier 等级（如果支持）
     * @return 槽位位置列表
     */
    List<SlotPosition> generateSlots(int baseX, int baseY, int tier);
    
    /**
     * 获取布局的边界框
     */
    Rectangle getBounds(int tier);
    
    /**
     * 是否支持等级缩放
     */
    boolean supportsTiers();
    
    /**
     * 获取布局名称
     */
    String getLayoutName();
}