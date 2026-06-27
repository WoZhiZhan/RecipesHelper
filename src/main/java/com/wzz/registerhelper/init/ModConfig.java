package com.wzz.registerhelper.init;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod配置管理类
 * 用于管理NBT匹配模式等配置
 *
 * NeoForge 1.21.1 迁移要点：
 * - ForgeConfigSpec -> ModConfigSpec
 * - ModLoadingContext.get().registerConfig(...) -> modContainer.registerConfig(...)
 */
public class ModConfig {

    public static final ModConfigSpec COMMON_SPEC;
    public static final CommonConfig COMMON;

    static {
        ModConfigSpec.Builder commonBuilder = new ModConfigSpec.Builder();
        COMMON = new CommonConfig(commonBuilder);
        COMMON_SPEC = commonBuilder.build();
    }

    /**
     * 注册配置（NeoForge：通过 ModContainer）
     */
    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(Type.COMMON, COMMON_SPEC);
    }

    public static class CommonConfig {

        public final ModConfigSpec.BooleanValue enablePerSlotNBT;
        public final ModConfigSpec.BooleanValue enableDebugLogging;
        public final ModConfigSpec.BooleanValue defaultIncludeNBT;

        public CommonConfig(ModConfigSpec.Builder builder) {
            builder.push("nbt_matching");

            defaultIncludeNBT = builder
                    .comment("配方创建器中NBT复选框的默认状态",
                            "当启用时，创建配方时输入物品默认包含NBT",
                            "当禁用时，创建配方时输入物品默认不包含NBT",
                            "",
                            "Default state of NBT checkbox in recipe creator",
                            "When enabled, input items will include NBT by default",
                            "When disabled, input items will not include NBT by default")
                    .define("defaultIncludeNBT", true);

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

    public static boolean isDebugLoggingEnabled() {
        return COMMON.enableDebugLogging.get();
    }

    public static boolean isPerSlotNBTEnabled() {
        return COMMON.enablePerSlotNBT.get();
    }

    public static boolean getDefaultIncludeNBT() {
        return COMMON.defaultIncludeNBT.get();
    }
}
