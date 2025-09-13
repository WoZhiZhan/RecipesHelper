package com.wzz.registerhelper;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.core.SuperRuntimeRecipeManager;
import com.wzz.registerhelper.core.RecipeCommand;
import com.wzz.registerhelper.init.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Registerhelper.MODID)
public class Registerhelper {
    public static final String MODID = "registerhelper";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    private static SuperRuntimeRecipeManager recipeManager;

    @SuppressWarnings("removal")
    public Registerhelper() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        recipeManager = new SuperRuntimeRecipeManager(MODID);
        ModNetwork.register();
    }

    public static SuperRuntimeRecipeManager getRecipeManager() {
        return recipeManager;
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RecipeCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        try {
            recipeManager.reloadFromJson();
            if (recipeManager.getPendingRecipeCount() > 0) {
                recipeManager.registerRecipes();
                LOGGER.info("[RegisterHelper] 服务器启动时自动加载并注册了 {} 个JSON配方", recipeManager.getPendingRecipeCount());
            }
        } catch (Exception e) {
            LOGGER.error("[RegisterHelper] 服务器启动时加载JSON配方失败: {}", e.getMessage());
        }
    }
}
