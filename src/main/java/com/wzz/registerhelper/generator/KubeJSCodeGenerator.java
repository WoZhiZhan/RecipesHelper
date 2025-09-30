package com.wzz.registerhelper.generator;

import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.RecipeTypeDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * KubeJS代码生成器
 * 负责将配方转换为KubeJS格式的代码
 */
public class KubeJSCodeGenerator {
    
    /**
     * 生成KubeJS代码
     */
    public static GenerationResult generateCode(RecipeTypeDefinition recipeType, String craftingMode, 
                                               ItemStack resultItem, int resultCount, List<ItemStack> ingredients) {
        try {
            if (recipeType == null) {
                return new GenerationResult(false, "配方类型为空", null);
            }
            
            if (resultItem.isEmpty()) {
                return new GenerationResult(false, "结果物品为空", null);
            }
            
            if (ingredients.isEmpty() || ingredients.stream().allMatch(ItemStack::isEmpty)) {
                return new GenerationResult(false, "合成材料为空", null);
            }
            
            String category = recipeType.getProperty("category", String.class);
            if (!"crafting".equals(category)) {
                return new GenerationResult(false, "目前只支持合成配方导出为KubeJS", null);
            }
            
            String resultItemId = getItemId(resultItem);
            String code = null;
            
            if ("shaped".equals(craftingMode)) {
                code = generateShapedCode(recipeType, resultItemId, resultCount, ingredients);
            } else if ("shapeless".equals(craftingMode)) {
                code = generateShapelessCode(resultItemId, resultCount, ingredients);
            } else {
                return new GenerationResult(false, "不支持的合成模式: " + craftingMode, null);
            }
            
            if (code != null && !code.isEmpty()) {
                return new GenerationResult(true, "KubeJS代码生成成功", code);
            } else {
                return new GenerationResult(false, "生成的代码为空", null);
            }
            
        } catch (Exception e) {
            return new GenerationResult(false, "生成失败: " + e.getMessage(), null);
        }
    }
    
    /**
     * 生成有序配方代码
     */
    private static String generateShapedCode(RecipeTypeDefinition recipeType, String resultItemId, 
                                           int resultCount, List<ItemStack> ingredients) {
        StringBuilder code = new StringBuilder();
        
        // 获取网格尺寸
        int gridWidth = recipeType.getMaxGridWidth();
        int gridHeight = recipeType.getMaxGridHeight();
        
        // 生成模式和键值映射
        PatternResult patternResult = generatePattern(ingredients, gridWidth, gridHeight);
        
        if (patternResult.keyMap.isEmpty()) {
            return null; // 没有有效材料
        }
        
        code.append("event.shaped(\n");
        code.append("  Item.of('").append(resultItemId).append("', ").append(resultCount).append("),\n");
        code.append("  [\n");
        
        for (int i = 0; i < patternResult.pattern.length; i++) {
            code.append("    '").append(patternResult.pattern[i]).append("'");
            if (i < patternResult.pattern.length - 1) {
                code.append(",");
            }
            code.append("\n");
        }
        
        code.append("  ],\n");
        code.append("  {\n");
        
        boolean first = true;
        for (Map.Entry<Character, String> entry : patternResult.keyMap.entrySet()) {
            if (!first) {
                code.append(",\n");
            }
            code.append("    ").append(entry.getKey()).append(": '").append(entry.getValue()).append("'");
            first = false;
        }
        
        code.append("\n  }\n");
        code.append(")");
        
        return code.toString();
    }
    
    /**
     * 生成无序配方代码
     */
    private static String generateShapelessCode(String resultItemId, int resultCount, List<ItemStack> ingredients) {
        StringBuilder code = new StringBuilder();
        
        code.append("event.shapeless(\n");
        code.append("  Item.of('").append(resultItemId).append("', ").append(resultCount).append("),\n");
        code.append("  [\n");
        
        boolean first = true;
        for (ItemStack ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                if (!first) {
                    code.append(",\n");
                }
                code.append("    '").append(getItemId(ingredient)).append("'");
                first = false;
            }
        }
        
        if (first) {
            return null; // 没有有效材料
        }
        
        code.append("\n  ]\n");
        code.append(")");
        
        return code.toString();
    }
    
    /**
     * 生成配方模式和键值映射
     */
    private static PatternResult generatePattern(List<ItemStack> ingredients, int gridWidth, int gridHeight) {
        String[] pattern = new String[gridHeight];
        Map<Character, String> keyMap = new LinkedHashMap<>(); // 保持顺序
        char currentChar = 'A';
        
        // 首先找到实际使用的边界，去除多余的空行/空列
        int minRow = gridHeight, maxRow = -1;
        int minCol = gridWidth, maxCol = -1;
        
        for (int row = 0; row < gridHeight; row++) {
            for (int col = 0; col < gridWidth; col++) {
                int index = row * gridWidth + col;
                if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minCol = Math.min(minCol, col);
                    maxCol = Math.max(maxCol, col);
                }
            }
        }
        
        // 如果没有找到任何物品，返回空模式
        if (minRow > maxRow || minCol > maxCol) {
            return new PatternResult(new String[0], new HashMap<>());
        }
        
        // 重新计算实际尺寸
        int actualHeight = maxRow - minRow + 1;
        int actualWidth = maxCol - minCol + 1;
        pattern = new String[actualHeight];
        
        for (int row = 0; row < actualHeight; row++) {
            StringBuilder rowPattern = new StringBuilder();
            
            for (int col = 0; col < actualWidth; col++) {
                int originalRow = minRow + row;
                int originalCol = minCol + col;
                int index = originalRow * gridWidth + originalCol;
                
                if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                    ItemStack ingredient = ingredients.get(index);
                    String itemId = getItemId(ingredient);
                    
                    // 查找是否已经有对应的字符
                    char symbol = findSymbolForItem(keyMap, itemId);
                    if (symbol == 0) {
                        // 没找到，创建新符号
                        symbol = currentChar++;
                        keyMap.put(symbol, itemId);
                    }
                    
                    rowPattern.append(symbol);
                } else {
                    rowPattern.append(' ');
                }
            }
            
            pattern[row] = rowPattern.toString();
        }
        
        return new PatternResult(pattern, keyMap);
    }
    
    /**
     * 查找物品对应的符号
     */
    private static char findSymbolForItem(Map<Character, String> keyMap, String itemId) {
        for (Map.Entry<Character, String> entry : keyMap.entrySet()) {
            if (entry.getValue().equals(itemId)) {
                return entry.getKey();
            }
        }
        return 0; // 未找到
    }
    
    /**
     * 获取物品ID
     */
    private static String getItemId(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return "";
        }
        
        var itemLocation = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        if (itemLocation != null) {
            return itemLocation.toString();
        }
        
        // 后备方案
        String itemId = itemStack.getItem().toString();
        if (!itemId.contains(":")) {
            itemId = "minecraft:" + itemId;
        }
        return itemId;
    }
    
    /**
     * 生成结果类
     */
    public static class GenerationResult {
        public final boolean success;
        public final String message;
        public final String code;
        
        public GenerationResult(boolean success, String message, String code) {
            this.success = success;
            this.message = message;
            this.code = code;
        }
    }
    
    /**
     * 模式结果类
     */
    private static class PatternResult {
        final String[] pattern;
        final Map<Character, String> keyMap;
        
        PatternResult(String[] pattern, Map<Character, String> keyMap) {
            this.pattern = pattern;
            this.keyMap = keyMap;
        }
    }
}