package com.wzz.registerhelper.init;


import com.wzz.registerhelper.recipe.integration.module.*;
import com.wzz.registerhelper.recipe.UniversalRecipeManager;
import com.wzz.registerhelper.recipe.integration.register.BotaniaRecipeRegistration;
import com.wzz.registerhelper.util.RegisterHelper;

public class ModIntegrations {
    public static void registerAll() {
        UniversalRecipeManager.registerProcessor("minecraft", new MinecraftRecipeProcessor());
        UniversalRecipeManager.registerProcessor("avaritia", new AvaritiaRecipeProcessor());
        RegisterHelper.registerRecipeType("eternisstarrysky", "shaped_crafting",
                "永恒星空有序合成", new EternisStarrySkyProcessor(), 9, true);
        RegisterHelper.registerRecipeTypeWithLayout("mysticalagriculture", "infusion",
                "神秘农业注魔", new MysticalAgricultureProcessor(), "mystical_infusion");
        BotaniaRecipeRegistration.registerBotaniaRecipes();
    }
}