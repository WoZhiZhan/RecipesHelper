package com.wzz.registerhelper.gui.recipe;

import com.wzz.registerhelper.gui.GuiText;

/**
 * 配方类型配置类
 * 定义不同配方类型的属性和行为
 */
public class RecipeTypeConfig {
    
    public enum RecipeType {
        CRAFTING("registerhelper.recipe_type.legacy.crafting", 9, 3, 3, true, false),
        COOKING("registerhelper.recipe_type.legacy.cooking", 1, 1, 1, false, true),
        AVARITIA("registerhelper.recipe_type.legacy.avaritia", 81, 9, 9, true, false);

        private final String displayName;
        private final int maxInputs;
        private final int maxGridWidth;
        private final int maxGridHeight;
        private final boolean supportsFillMode;
        private final boolean supportsCookingSettings;

        RecipeType(String displayName, int maxInputs, int maxGridWidth, int maxGridHeight, 
                  boolean supportsFillMode, boolean supportsCookingSettings) {
            this.displayName = displayName;
            this.maxInputs = maxInputs;
            this.maxGridWidth = maxGridWidth;
            this.maxGridHeight = maxGridHeight;
            this.supportsFillMode = supportsFillMode;
            this.supportsCookingSettings = supportsCookingSettings;
        }

        public String getDisplayName() { return GuiText.string(displayName); }
        public int getMaxInputs() { return maxInputs; }
        public int getMaxGridWidth() { return maxGridWidth; }
        public int getMaxGridHeight() { return maxGridHeight; }
        public boolean supportsFillMode() { return supportsFillMode; }
        public boolean supportsCookingSettings() { return supportsCookingSettings; }
        
        public boolean isCookingType() { return this == COOKING; }
        public boolean isAvaritiaType() { return this == AVARITIA; }
        public boolean isCraftingType() { return this == CRAFTING; }
    }

    public enum CraftingMode {
        SHAPELESS("registerhelper.recipe_type.crafting_shapeless_short"),
        SHAPED("registerhelper.recipe_type.crafting_shaped_short");

        private final String displayName;
        CraftingMode(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return GuiText.string(displayName); }
    }

    public enum CookingType {
        SMELTING("registerhelper.recipe_type.minecraft.smelting", "200", "0.7"),
        BLASTING("registerhelper.recipe_type.minecraft.blasting", "100", "0.7"),
        SMOKING("registerhelper.recipe_type.minecraft.smoking", "100", "0.35"),
        CAMPFIRE("registerhelper.recipe_type.minecraft.campfire", "600", "0.35");

        private final String displayName;
        private final String defaultTime;
        private final String defaultExp;

        CookingType(String displayName, String defaultTime, String defaultExp) {
            this.displayName = displayName;
            this.defaultTime = defaultTime;
            this.defaultExp = defaultExp;
        }

        public String getDisplayName() { return GuiText.string(displayName); }
        public String getDefaultTime() { return defaultTime; }
        public String getDefaultExp() { return defaultExp; }
    }

    /**
     * Avaritia等级配置
     */
    public static class AvaritiaConfig {
        public static int getGridSizeForTier(int tier) {
            return switch (tier) {
                case 1 -> 3;
                case 2 -> 5;
                case 3 -> 7;
                case 4 -> 9;
                case 5 -> 11;
                case 6 -> 16;
                default -> 21;
            };
        }

        public static int getTierFromGridSize(int gridSize) {
            return switch (gridSize) {
                case 3 -> 1;
                case 5 -> 2;
                case 7 -> 3;
                case 9 -> 4;
                case 11 -> 5;
                case 16 -> 6;
                default -> 7;
            };
        }

        public static int getTierFromIngredientCount(int count) {
            if (count <= 9) return 1;
            if (count <= 25) return 2;
            if (count <= 49) return 3;
            if (count <= 81) return 4;
            if (count <= 121) return 5;
            if (count <= 256) return 6;
            return 7;
        }
    }
}
