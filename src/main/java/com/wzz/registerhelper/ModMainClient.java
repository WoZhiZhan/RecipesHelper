package com.wzz.registerhelper;

import com.wzz.registerhelper.gui.ConfigScreen;
import com.wzz.registerhelper.gui.GuiTheme;
import com.wzz.registerhelper.init.ModConfig;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public class ModMainClient {
    public static void init() {
        GuiTheme.applyTheme(ModConfig.getGuiTheme());
        ModList.get().getModContainerById(ModMain.MODID)
                .ifPresent(container ->
                        container.registerExtensionPoint(
                                IConfigScreenFactory.class,
                                (mc, parent) -> new ConfigScreen(parent)
                        )
                );
    }
}
