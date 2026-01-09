package com.wzz.registerhelper.util;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.init.ModConfig;
import net.minecraft.nbt.*;
import org.slf4j.Logger;

/**
 * NBT数据比较工具类
 * 提供标准匹配和模糊匹配两种模式
 */
public class NbtMatchingUtil {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 检查两个NBT标签是否匹配
     * 根据配置决定使用精确匹配还是模糊匹配
     * 
     * @param actual 实际物品的NBT数据
     * @param required 配方要求的NBT数据
     * @param useFuzzyMatching 是否使用模糊匹配
     * @return 是否匹配
     */
    public static boolean nbtMatches(CompoundTag actual, CompoundTag required, boolean useFuzzyMatching) {
        // 如果配方没有NBT要求，总是匹配
        if (required == null || required.isEmpty()) {
            return true;
        }
        
        // 如果配方有NBT要求，但实际物品没有NBT，不匹配
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        
        // 根据配置选择匹配模式
        if (useFuzzyMatching) {
            boolean matches = fuzzyNbtMatches(actual, required);
            
            if (ModConfig.isDebugLoggingEnabled()) {
                LOGGER.info("NBT模糊匹配检查:");
                LOGGER.info("  实际NBT: {}", actual);
                LOGGER.info("  需要NBT: {}", required);
                LOGGER.info("  匹配结果: {}", matches);
            }
            
            return matches;
        } else {
            boolean matches = actual.equals(required);
            
            if (ModConfig.isDebugLoggingEnabled()) {
                LOGGER.info("NBT精确匹配检查:");
                LOGGER.info("  实际NBT: {}", actual);
                LOGGER.info("  需要NBT: {}", required);
                LOGGER.info("  匹配结果: {}", matches);
            }
            
            return matches;
        }
    }
    
    /**
     * NBT模糊匹配
     * 检查actual是否包含required中的所有键值对
     * actual可以有额外的键值对
     * 
     * @param actual 实际的NBT数据（可以有更多数据）
     * @param required 必需的NBT数据（必须全部包含）
     * @return 如果actual包含required的所有内容则返回true
     */
    public static boolean fuzzyNbtMatches(CompoundTag actual, CompoundTag required) {
        // 遍历required中的所有键
        for (String key : required.getAllKeys()) {
            // 如果actual中不包含这个键，匹配失败
            if (!actual.contains(key)) {
                if (ModConfig.isDebugLoggingEnabled()) {
                    LOGGER.info("  缺少键: {}", key);
                }
                return false;
            }
            
            Tag requiredTag = required.get(key);
            Tag actualTag = actual.get(key);
            
            // 递归检查标签内容
            if (!tagMatches(actualTag, requiredTag)) {
                if (ModConfig.isDebugLoggingEnabled()) {
                    LOGGER.info("  键 {} 的值不匹配", key);
                    LOGGER.info("    实际值: {}", actualTag);
                    LOGGER.info("    需要值: {}", requiredTag);
                }
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 递归比较两个NBT标签
     * 支持所有NBT标签类型的模糊匹配
     */
    private static boolean tagMatches(Tag actual, Tag required) {
        // 类型必须相同
        if (actual.getId() != required.getId()) {
            return false;
        }
        
        // 根据标签类型进行比较
        switch (required.getId()) {
            case Tag.TAG_COMPOUND:
                // CompoundTag递归进行模糊匹配
                return fuzzyNbtMatches((CompoundTag) actual, (CompoundTag) required);
                
            case Tag.TAG_LIST:
                // ListTag需要特殊处理
                return listTagMatches((ListTag) actual, (ListTag) required);
                
            case Tag.TAG_BYTE_ARRAY:
            case Tag.TAG_INT_ARRAY:
            case Tag.TAG_LONG_ARRAY:
                // 数组类型必须完全相同
                return actual.equals(required);
                
            default:
                // 基础类型（Byte, Short, Int, Long, Float, Double, String）直接比较
                return actual.equals(required);
        }
    }
    
    /**
     * 比较两个ListTag
     * required中的所有元素必须在actual中找到对应的匹配元素
     */
    private static boolean listTagMatches(ListTag actual, ListTag required) {
        // 如果required为空，总是匹配
        if (required.isEmpty()) {
            return true;
        }
        
        // actual的元素数量必须大于等于required
        if (actual.size() < required.size()) {
            return false;
        }
        
        // 检查类型是否相同
        if (actual.getElementType() != required.getElementType()) {
            return false;
        }
        
        // 对于列表，我们采用"包含检查"而不是"顺序检查"
        // required中的每个元素都必须在actual中找到匹配的元素
        for (int i = 0; i < required.size(); i++) {
            Tag requiredElement = required.get(i);
            boolean foundMatch = false;
            
            // 在actual中查找匹配的元素
            for (int j = 0; j < actual.size(); j++) {
                Tag actualElement = actual.get(j);
                if (tagMatches(actualElement, requiredElement)) {
                    foundMatch = true;
                    break;
                }
            }
            
            // 如果没有找到匹配的元素，匹配失败
            if (!foundMatch) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 严格的列表匹配（按顺序和大小）
     * 只有当两个列表完全相同时才匹配
     */
    @SuppressWarnings("unused")
    private static boolean listTagMatchesStrict(ListTag actual, ListTag required) {
        // 大小必须相同
        if (actual.size() != required.size()) {
            return false;
        }
        
        // 类型必须相同
        if (actual.getElementType() != required.getElementType()) {
            return false;
        }
        
        // 逐个比较元素
        for (int i = 0; i < required.size(); i++) {
            if (!tagMatches(actual.get(i), required.get(i))) {
                return false;
            }
        }
        
        return true;
    }
}