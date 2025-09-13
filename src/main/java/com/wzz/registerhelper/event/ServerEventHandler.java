package com.wzz.registerhelper.event;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.core.RecipeOverrideResolver;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber
public class ServerEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onServerStart(ServerStartedEvent event) {
        int i = RecipeOverrideResolver.resolveConflictsPreferJson();
        LOGGER.info("删除 {} 个配方", i);
        RecipeOverrideResolver.OverrideReport afterReport = RecipeOverrideResolver.generateOverrideReport();
        if (!afterReport.overriddenRecipes.isEmpty()) {
            LOGGER.info("配方覆盖成功! 以下配方现在使用JSON修改版本:");
            for (ResourceLocation overriddenId : afterReport.overriddenRecipes) {
                LOGGER.info(" {} (已覆盖)", overriddenId);
            }
        }
    }
}
