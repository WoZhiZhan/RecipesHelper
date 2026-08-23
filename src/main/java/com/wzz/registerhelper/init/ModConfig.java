package com.wzz.registerhelper.init;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod配置管理类
 * 用于管理NBT匹配模式等配置
 */
public class ModConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final CommonConfig COMMON;

    static {
        ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        COMMON = new CommonConfig(commonBuilder);
        COMMON_SPEC = commonBuilder.build();
    }

    /**
     * 注册配置
     */
    @SuppressWarnings("removal")
    public static void register() {
        ModLoadingContext.get().registerConfig(Type.COMMON, COMMON_SPEC);
    }

    /**
     * 通用配置
     */
    public static class CommonConfig {

        public final ForgeConfigSpec.BooleanValue enablePerSlotNBT;
        public final ForgeConfigSpec.BooleanValue enableDebugLogging;
        public final ForgeConfigSpec.BooleanValue defaultIncludeNBT;
        public final ForgeConfigSpec.BooleanValue enableLearnedRecipes;
        public final ForgeConfigSpec.ConfigValue<String> guiTheme;
        public final ForgeConfigSpec.ConfigValue<String> brushFavorite1;
        public final ForgeConfigSpec.ConfigValue<String> brushFavorite2;
        public final ForgeConfigSpec.ConfigValue<String> brushFavorite3;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("nbt_matching");

            // NBT复选框默认状态
            defaultIncludeNBT = builder
                    .comment("配方创建器中NBT复选框的默认状态",
                            "当启用时，创建配方时输入物品默认包含NBT",
                            "当禁用时，创建配方时输入物品默认不包含NBT",
                            "",
                            "Default state of NBT checkbox in recipe creator",
                            "When enabled, input items will include NBT by default",
                            "When disabled, input items will not include NBT by default")
                    .define("defaultIncludeNBT", true);

            // per-slot NBT 控制开关
            enablePerSlotNBT = builder
                    .comment("启用配方创建器中每个槽位独立的NBT匹配控制",
                            "启用后：槽位底部显示颜色条，中键点击可单独开关每个槽位的NBT匹配",
                            "禁用后：所有槽位统一由 defaultIncludeNBT 决定，不显示颜色条",
                            "",
                            "Enable per-slot NBT matching control in recipe creator",
                            "When enabled: color bar shown at slot bottom, middle-click to toggle per slot",
                            "When disabled: all slots follow defaultIncludeNBT, no color bar shown")
                    .define("enablePerSlotNBT", true);

            builder.pop();

            builder.push("debug");

            // 调试日志开关
            enableDebugLogging = builder
                    .comment("启用调试日志输出",
                            "当启用时，会输出NBT匹配的详细信息",
                            "",
                            "Enable debug logging",
                            "When enabled, detailed NBT matching information will be logged")
                    .define("enableDebugLogging", false);

            builder.pop();

            builder.push("recipe_learning");
            enableLearnedRecipes = builder
                    .comment("Automatically discover ordinary square item recipes from loaded mods",
                            "自动学习已加载模组中的普通方形物品配方",
                            "Disabled by default; special recipes are never learned")
                    .define("enableLearnedRecipes", false);
            builder.pop();

            builder.push("client_ui");

            guiTheme = builder
                    .comment("RegisterHelper GUI theme",
                            "Available themes: soft_dark, light",
                            "",
                            "RegisterHelper GUI theme",
                            "Available themes: soft_dark, light")
                    .define("theme", "soft_dark",
                            value -> "soft_dark".equals(value) || "light".equals(value));

            brushFavorite1 = builder
                    .comment("First frequently used brush item id")
                    .define("brushFavorite1", "");
            brushFavorite2 = builder
                    .comment("Second frequently used brush item id")
                    .define("brushFavorite2", "");
            brushFavorite3 = builder
                    .comment("Third frequently used brush item id")
                    .define("brushFavorite3", "");

            builder.pop();
        }
    }

    /**
     * 检查是否启用调试日志
     */
    public static boolean isDebugLoggingEnabled() {
        return COMMON.enableDebugLogging.get();
    }

    /**
     * 检查是否启用了 per-slot NBT 控制
     */
    public static boolean isPerSlotNBTEnabled() {
        return COMMON.enablePerSlotNBT.get();
    }

    /**
     * 获取NBT复选框默认状态
     */
    public static boolean getDefaultIncludeNBT() {
        return COMMON.defaultIncludeNBT.get();
    }

    public static String getGuiTheme() {
        String theme = COMMON.guiTheme.get();
        return "light".equals(theme) ? "light" : "soft_dark";
    }

    public static boolean isLearnedRecipesEnabled() {
        return COMMON.enableLearnedRecipes.get();
    }

    public static String getBrushFavorite(int index) {
        return switch (index) {
            case 0 -> COMMON.brushFavorite1.get();
            case 1 -> COMMON.brushFavorite2.get();
            case 2 -> COMMON.brushFavorite3.get();
            default -> "";
        };
    }

    public static List<String> getBrushFavoriteIds() {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String value = getBrushFavorite(i);
            if (value != null && !value.isBlank() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    public static List<ItemStack> getBrushFavoriteStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (String id : getBrushFavoriteIds()) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) continue;
            var item = ForgeRegistries.ITEMS.getValue(location);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                stacks.add(new ItemStack(item));
            }
        }
        return stacks;
    }

    /** Adds an ordinary item to the front of the three persistent brush slots. */
    public static void rememberBrushItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || id.equals(new ResourceLocation("minecraft", "air"))) return;

        List<String> values = getBrushFavoriteIds();
        values.remove(id.toString());
        values.add(0, id.toString());
        setBrushFavorites(values);
        COMMON_SPEC.save();
    }

    public static void setBrushFavorites(List<String> values) {
        List<String> normalized = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank() || ResourceLocation.tryParse(value) == null) {
                    continue;
                }
                if (!normalized.contains(value)) normalized.add(value);
                if (normalized.size() == 3) break;
            }
        }
        COMMON.brushFavorite1.set(normalized.size() > 0 ? normalized.get(0) : "");
        COMMON.brushFavorite2.set(normalized.size() > 1 ? normalized.get(1) : "");
        COMMON.brushFavorite3.set(normalized.size() > 2 ? normalized.get(2) : "");
    }
}
