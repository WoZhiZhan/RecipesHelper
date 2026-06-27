package com.wzz.registerhelper;

import com.wzz.registerhelper.command.RecipeCommand;
import com.wzz.registerhelper.init.ModConfig;
import com.wzz.registerhelper.init.ModIntegrations;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.init.ModRegistries;
import com.wzz.registerhelper.init.ProcessorLoader;
import com.wzz.registerhelper.recipe.CustomRecipeLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
        }
        NeoForge.EVENT_BUS.register(this);
        modEventBus.register(ModNetwork.class);

        // 注册自定义 IngredientType (PartialNbtIngredient)
        ModRegistries.register(modEventBus);

        // 注册配置 (NeoForge: 通过 ModContainer)
        ModConfig.register(modContainer);

        ModIntegrations.registerAll();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        ModMainClient.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ProcessorLoader.loadProcessors(event);
        // 加载自定义配方 (config/registerhelper/recipes 下的 json)
        event.enqueueWork(CustomRecipeLoader::loadCustomRecipes);
    }

    @SubscribeEvent
    public void onRegisterCommandsEvent(RegisterCommandsEvent event) {
        RecipeCommand.register(event.getDispatcher());
    }
}
