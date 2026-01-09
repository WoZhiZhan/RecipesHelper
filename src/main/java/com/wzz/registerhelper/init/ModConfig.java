package com.wzz.registerhelper.init;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;

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

        public final ForgeConfigSpec.BooleanValue enableFuzzyNbtMatching;
        public final ForgeConfigSpec.BooleanValue enableDebugLogging;
        public final ForgeConfigSpec.BooleanValue defaultIncludeNBT;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("nbt_matching");

            // NBT模糊匹配总开关
            enableFuzzyNbtMatching = builder
                    .comment("启用NBT模糊匹配模式",
                            "当启用时，本mod添加的所有配方在匹配物品NBT时，只需物品NBT包含配方所需的NBT即可",
                            "例如：配方需要{display:{Name:\"test\"}}，物品有{display:{Name:\"test\",Lore:[\"额外信息\"]}}也会匹配",
                            "这适用于本mod创建/修改的所有配方类型（工作台、熔炉、铁砧、酿造台等）",
                            "",
                            "Enable fuzzy NBT matching mode",
                            "When enabled, all recipes added by this mod will use fuzzy NBT matching",
                            "Items only need to contain the required NBT data to match",
                            "Example: Recipe requires {display:{Name:\"test\"}}, item with {display:{Name:\"test\",Lore:[\"extra\"]}} will also match",
                            "This applies to all recipe types created/modified by this mod")
                    .define("enableFuzzyNbtMatching", true);

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
        }
    }

    /**
     * 检查是否启用了NBT模糊匹配
     */
    public static boolean isFuzzyNbtMatchingEnabled() {
        return COMMON.enableFuzzyNbtMatching.get();
    }

    /**
     * 检查是否启用调试日志
     */
    public static boolean isDebugLoggingEnabled() {
        return COMMON.enableDebugLogging.get();
    }

    /**
     * 获取NBT复选框默认状态
     */
    public static boolean getDefaultIncludeNBT() {
        return COMMON.defaultIncludeNBT.get();
    }
}