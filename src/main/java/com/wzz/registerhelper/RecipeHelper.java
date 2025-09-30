package com.wzz.registerhelper;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.command.RecipeCommand;
import com.wzz.registerhelper.init.ModIntegrations;
import com.wzz.registerhelper.init.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RecipeHelper.MODID)
public class RecipeHelper {
    public static final String MODID = "registerhelper";
    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public RecipeHelper() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModNetwork.register();
        ModIntegrations.registerAll();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RecipeCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
