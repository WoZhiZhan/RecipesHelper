package com.wzz.registerhelper;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.command.RecipeCommand;
import com.wzz.registerhelper.gui.ConfigScreen;
import com.wzz.registerhelper.ingredient.PartialNbtIngredient;
import com.wzz.registerhelper.init.ModConfig;
import com.wzz.registerhelper.init.ModIntegrations;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.init.ProcessorLoader;
import com.wzz.registerhelper.recipe.CustomRecipeLoader;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
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
        modEventBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModNetwork.register();
        ModIntegrations.registerAll();
        ModConfig.register();
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        net.minecraftforge.fml.ModList.get().getModContainerById(MODID).ifPresent(container -> container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new ConfigScreen(parent)
                )
        ));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CraftingHelper.register(
                    PartialNbtIngredient.ID,
                    PartialNbtIngredient.SERIALIZER
            );
        });
        ProcessorLoader.loadProcessors(event);
        CustomRecipeLoader.loadCustomRecipes();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RecipeCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
