package com.wzz.registerhelper.gui.recipe.component;

import java.util.HashMap;
import java.util.Map;

/**
 * 组件数据管理器 - 存储各组件的值
 */
public class ComponentDataManager {
    private final Map<String, Object> data = new HashMap<>();

    /**
     * 设置数值
     */
    public void setNumber(String id, int value) {
        data.put(id, value);
    }

    /**
     * 获取数值
     */
    public int getNumber(String id, int defaultValue) {
        Object value = data.get(id);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return defaultValue;
    }

    /**
     * 设置字符串
     */
    public void setString(String id, String value) {
        data.put(id, value);
    }

    /**
     * 获取字符串
     */
    public String getString(String id, String defaultValue) {
        Object value = data.get(id);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }

    /**
     * 设置布尔值
     */
    public void setBoolean(String id, boolean value) {
        data.put(id, value);
    }

    /**
     * 获取布尔值
     */
    public boolean getBoolean(String id, boolean defaultValue) {
        Object value = data.get(id);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    /**
     * 清空所有数据
     */
    public void clear() {
        data.clear();
    }

    /**
     * 获取所有数据（用于配方构建）
     */
    public Map<String, Object> getAllData() {
        return new HashMap<>(data);
    }
}