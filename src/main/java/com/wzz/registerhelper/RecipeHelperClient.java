package com.wzz.registerhelper;

import com.wzz.registerhelper.gui.ConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;

import static com.wzz.registerhelper.RecipeHelper.MODID;

public class RecipeHelperClient {
    public static void init() {
        net.minecraftforge.fml.ModList.get().getModContainerById(MODID).ifPresent(container -> container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new ConfigScreen(parent)
                )
        ));
    }
}
