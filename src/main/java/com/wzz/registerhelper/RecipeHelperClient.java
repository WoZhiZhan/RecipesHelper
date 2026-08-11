package com.wzz.registerhelper;

import com.wzz.registerhelper.gui.ConfigScreen;
import com.wzz.registerhelper.gui.GuiTheme;
import com.wzz.registerhelper.init.ModConfig;
import net.minecraftforge.client.ConfigScreenHandler;

import static com.wzz.registerhelper.RecipeHelper.MODID;

public class RecipeHelperClient {
    public static void init() {
        GuiTheme.applyTheme(ModConfig.getGuiTheme());
        net.minecraftforge.fml.ModList.get().getModContainerById(MODID).ifPresent(container -> container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new ConfigScreen(parent)
                )
        ));
    }
}
