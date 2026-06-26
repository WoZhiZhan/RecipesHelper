package com.wzz.registerhelper;

import com.wzz.registerhelper.command.RecipeCommand;
import com.wzz.registerhelper.init.ModIntegrations;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.init.ProcessorLoader;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ModMain.MODID)
public class ModMain {
    public static final String MODID = "registerhelper";

    public ModMain(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        modEventBus.register(ModNetwork.class);
        ModIntegrations.registerAll();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ProcessorLoader.loadProcessors(event);
    }

    @SubscribeEvent
    public void onRegisterCommandsEvent(RegisterCommandsEvent event) {
        RecipeCommand.register(event.getDispatcher());
    }
}
