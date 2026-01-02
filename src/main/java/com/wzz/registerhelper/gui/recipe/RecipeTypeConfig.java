package com.wzz.registerhelper.gui.recipe;

/**
 * 配方类型配置类
 * 定义不同配方类型的属性和行为
 */
public class RecipeTypeConfig {
    
    public enum RecipeType {
        CRAFTING("工作台合成", 9, 3, 3, true, false),
        COOKING("烹饪配方", 1, 1, 1, false, true),
        AVARITIA("Avaritia工作台", 81, 9, 9, true, false);

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

        public String getDisplayName() { return displayName; }
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
        SHAPELESS("无序"),
        SHAPED("有序");

        private final String displayName;
        CraftingMode(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public enum CookingType {
        SMELTING("熔炉", "200", "0.7"),
        BLASTING("高炉", "100", "0.7"),
        SMOKING("烟熏炉", "100", "0.35"),
        CAMPFIRE("营火", "600", "0.35");

        private final String displayName;
        private final String defaultTime;
        private final String defaultExp;

        CookingType(String displayName, String defaultTime, String defaultExp) {
            this.displayName = displayName;
            this.defaultTime = defaultTime;
            this.defaultExp = defaultExp;
        }

        public String getDisplayName() { return displayName; }
        public String getDefaultTime() { return defaultTime; }
        public String getDefaultExp() { return defaultExp; }
    }
}