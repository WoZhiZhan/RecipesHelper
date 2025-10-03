package com.wzz.registerhelper.gui.recipe.dynamic;

import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.recipe.integration.module.MinecraftRecipeProcessor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态配方类型配置系统
 * 支持运行时注册新的配方类型，与ModRecipeProcessor接口集成
 */
public class DynamicRecipeTypeConfig {

    private static final Map<String, RecipeTypeDefinition> RECIPE_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, ModRecipeProcessor> MOD_PROCESSORS = new ConcurrentHashMap<>();

    static {
        registerBuiltinRecipeTypes();
    }

    /**
     * 配方类型定义
     */
    public static class RecipeTypeDefinition {
        private final String id;
        private final String displayName;
        private final String modId;
        private final int maxInputs;
        private final int maxGridWidth;
        private final int maxGridHeight;
        private final boolean supportsFillMode;
        private final boolean supportsCookingSettings;
        private final boolean displayable;
        private final Map<String, Object> additionalProperties;
        private final ModRecipeProcessor processor;

        public RecipeTypeDefinition(Builder builder) {
            this.id = builder.id;
            this.displayName = builder.displayName;
            this.modId = builder.modId;
            this.maxInputs = builder.maxInputs;
            this.maxGridWidth = builder.maxGridWidth;
            this.maxGridHeight = builder.maxGridHeight;
            this.supportsFillMode = builder.supportsFillMode;
            this.supportsCookingSettings = builder.supportsCookingSettings;
            this.additionalProperties = new HashMap<>(builder.additionalProperties);
            this.processor = builder.processor;
            this.displayable = builder.displayable;
        }

        // Getters
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getModId() { return modId; }
        public int getMaxInputs() { return maxInputs; }
        public int getMaxGridWidth() { return maxGridWidth; }
        public int getMaxGridHeight() { return maxGridHeight; }
        public boolean supportsFillMode() { return supportsFillMode; }
        public boolean supportsCookingSettings() { return supportsCookingSettings; }
        public ModRecipeProcessor getProcessor() { return processor; }
        public boolean getDisplayable() { return displayable; }

        public <T> T getProperty(String key, Class<T> type) {
            Object value = additionalProperties.get(key);
            return type.isInstance(value) ? type.cast(value) : null;
        }

        public boolean isCookingType() {
            return supportsCookingSettings || "cooking".equals(getProperty("category", String.class));
        }

        public boolean isAvaritiaType() {
            return "avaritia".equals(modId) || "avaritia".equals(getProperty("category", String.class));
        }

        public boolean isCraftingType() {
            return "minecraft".equals(modId) && "crafting".equals(getProperty("category", String.class));
        }

        /**
         * 构建器模式
         */
        public static class Builder {
            private String id;
            private String displayName;
            private String modId = "minecraft";
            private int maxInputs = 9;
            private int maxGridWidth = 3;
            private int maxGridHeight = 3;
            private boolean supportsFillMode = true;
            private boolean supportsCookingSettings = false;
            private boolean displayable = true;
            private final Map<String, Object> additionalProperties = new HashMap<>();
            private ModRecipeProcessor processor;

            public Builder(String id, String displayName) {
                this.id = id;
                this.displayName = displayName;
            }

            public Builder modId(String modId) { this.modId = modId; return this; }
            public Builder maxInputs(int maxInputs) { this.maxInputs = maxInputs; return this; }
            public Builder gridSize(int width, int height) {
                this.maxGridWidth = width;
                this.maxGridHeight = height;
                this.maxInputs = width * height;
                return this;
            }
            public Builder supportsFillMode(boolean supports) { this.supportsFillMode = supports; return this; }
            public Builder supportsCookingSettings(boolean supports) { this.supportsCookingSettings = supports; return this; }
            public Builder processor(ModRecipeProcessor processor) { this.processor = processor; return this; }
            public Builder property(String key, Object value) { this.additionalProperties.put(key, value); return this; }
            public Builder displayable(boolean displayable) {
                this.displayable = displayable;
                return this;
            }

            public RecipeTypeDefinition build() {
                return new RecipeTypeDefinition(this);
            }
        }
    }

    /**
     * 注册配方类型
     */
    public static void registerRecipeType(RecipeTypeDefinition definition) {
        RECIPE_TYPES.put(definition.getId(), definition);
        if (definition.getProcessor() != null) {
            MOD_PROCESSORS.put(definition.getModId(), definition.getProcessor());
        }
    }

    /**
     * 通过ModRecipeProcessor自动注册配方类型
     */
    public static void registerModProcessor(String modId, ModRecipeProcessor processor) {
        MOD_PROCESSORS.put(modId, processor);

        if (processor.isModLoaded()) {
            String[] supportedTypes = processor.getSupportedRecipeTypes();
            for (String type : supportedTypes) {
                if (!RECIPE_TYPES.containsKey(type)) {
                    RecipeTypeDefinition definition = createDefinitionFromProcessor(modId, type, processor);
                    registerRecipeType(definition);
                }
            }
        }
    }

    /**
     * 从处理器自动创建配方类型定义
     */
    private static RecipeTypeDefinition createDefinitionFromProcessor(String modId, String type, ModRecipeProcessor processor) {
        RecipeTypeDefinition.Builder builder = new RecipeTypeDefinition.Builder(type, getDisplayName(modId, type))
                .modId(modId)
                .processor(processor);

        // 根据类型名称推断配置
        if (type.contains("cooking") || type.contains("smelting") || type.contains("blasting") || type.contains("smoking")) {
            builder.supportsCookingSettings(true)
                    .supportsFillMode(false)
                    .gridSize(1, 1)
                    .property("category", "cooking");
        } else if (type.contains("avaritia") || type.contains("extreme")) {
            builder.gridSize(9, 9)
                    .property("category", "avaritia")
                    .property("dynamicSize", true);
        } else {
            builder.property("category", "crafting");
        }

        return builder.build();
    }

    /**
     * 获取显示名称
     */
    private static String getDisplayName(String modId, String type) {
        return switch (modId) {
            case "minecraft" -> switch (type) {
                case "crafting_shaped" -> "有序合成";
                case "crafting_shapeless" -> "无序合成";
                case "smelting" -> "熔炉";
                case "blasting" -> "高炉";
                case "smoking" -> "烟熏炉";
                case "campfire_cooking" -> "营火";
                default -> type;
            };
            case "avaritia" -> "Avaritia工作台";
            case "thermal" -> "热力系列";
            case "mekanism" -> "通用机械";
            case "immersiveengineering" -> "沉浸工程";
            default -> modId + ":" + type;
        };
    }

    /**
     * 注册内置配方类型
     */
    private static void registerBuiltinRecipeTypes() {
        MinecraftRecipeProcessor minecraftProcessor = new MinecraftRecipeProcessor();
        registerRecipeType(new RecipeTypeDefinition.Builder("crafting_shaped", "原版合成")
                .modId("minecraft")
                .gridSize(3, 3)
                .property("category", "crafting")
                .property("mode", "shaped")
                .build());
        registerRecipeType(new RecipeTypeDefinition.Builder("crafting_shapeless", "原版合成无序")
                .modId("minecraft")
                .gridSize(3, 3)
                .property("category", "crafting")
                .property("mode", "shaped")
                .displayable(false)
                .build());
        registerRecipeType(new RecipeTypeDefinition.Builder("smelting", "熔炉")
                .modId("minecraft")
                .gridSize(1, 1)
                .supportsCookingSettings(true)
                .supportsFillMode(false)
                .property("category", "cooking")
                .property("defaultTime", "200")
                .property("defaultExp", "0.7")
                .build());
        registerRecipeType(new RecipeTypeDefinition.Builder("blasting", "高炉")
                .modId("minecraft")
                .gridSize(1, 1)
                .supportsCookingSettings(true)
                .supportsFillMode(false)
                .property("category", "cooking")
                .property("defaultTime", "100")
                .property("defaultExp", "0.7")
                .build());
        registerRecipeType(new RecipeTypeDefinition.Builder("smoking", "烟熏炉")
                .modId("minecraft")
                .gridSize(1, 1)
                .supportsCookingSettings(true)
                .supportsFillMode(false)
                .property("category", "cooking")
                .property("defaultTime", "100")
                .property("defaultExp", "0.35")
                .build());
        registerRecipeType(new RecipeTypeDefinition.Builder("campfire_cooking", "营火")
                .modId("minecraft")
                .gridSize(1, 1)
                .supportsCookingSettings(true)
                .supportsFillMode(false)
                .property("category", "cooking")
                .property("defaultTime", "600")
                .property("defaultExp", "0.35")
                .build());
        registerRecipeType(new RecipeTypeDefinition.Builder("brew", "酿造台")
                .modId("minecraft")
                .gridSize(2, 3)
                .supportsFillMode(false)
                .property("category", "brew")
                .property("layout", "minecraft_brewing")
                .processor(minecraftProcessor)
                .build());
        registerRecipeType(new RecipeTypeDefinition.Builder("stonecutting", "切石机")
                .modId("minecraft")
                .gridSize(1, 1)
                .supportsFillMode(false)
                .property("category", "stonecutting")
                .property("layout", "stonecutting")
                .processor(minecraftProcessor)
                .build());
    }

    /**
     * 检查mod是否已加载
     */
    private static boolean isModLoaded(String modId) {
        try {
            return net.minecraftforge.fml.ModList.get().isLoaded(modId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取所有可用的配方类型
     */
    public static List<RecipeTypeDefinition> getAvailableRecipeTypes() {
        return RECIPE_TYPES.values().stream()
                .filter(def -> {
                    ModRecipeProcessor processor = def.getProcessor();
                    return processor == null || processor.isModLoaded();
                })
                .sorted(Comparator.comparing(RecipeTypeDefinition::getDisplayName))
                .toList();
    }

    /**
     * 获取所有显示的配方类型
     */
    public static List<RecipeTypeDefinition> getAvailableDisplayRecipeTypes() {
        return RECIPE_TYPES.values().stream()
                .filter(RecipeTypeDefinition::getDisplayable)
                .filter(def -> {
                    ModRecipeProcessor processor = def.getProcessor();
                    return processor == null || processor.isModLoaded();
                })
                .sorted(Comparator.comparing(RecipeTypeDefinition::getDisplayName))
                .toList();
    }

    /**
     * 根据ID获取配方类型定义
     */
    public static RecipeTypeDefinition getRecipeType(String id) {
        return RECIPE_TYPES.get(id);
    }

    /**
     * 根据mod获取配方类型
     */
    public static List<RecipeTypeDefinition> getRecipeTypesByMod(String modId) {
        return RECIPE_TYPES.values().stream()
                .filter(def -> modId.equals(def.getModId()))
                .sorted(Comparator.comparing(RecipeTypeDefinition::getDisplayName))
                .toList();
    }

    /**
     * 根据类别获取配方类型
     */
    public static List<RecipeTypeDefinition> getRecipeTypesByCategory(String category) {
        return RECIPE_TYPES.values().stream()
                .filter(def -> category.equals(def.getProperty("category", String.class)))
                .sorted(Comparator.comparing(RecipeTypeDefinition::getDisplayName))
                .toList();
    }

    /**
     * 获取所有已注册的mod处理器
     */
    public static Map<String, ModRecipeProcessor> getModProcessors() {
        return new HashMap<>(MOD_PROCESSORS);
    }
}