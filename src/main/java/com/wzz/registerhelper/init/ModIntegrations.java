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
                "星空永恒有序合成", new EternisStarrySkyProcessor(), 9, true);
        RegisterHelper.registerRecipeTypeWithLayout("mysticalagriculture", "infusion",
                "神秘农业注魔", new MysticalAgricultureProcessor(), "infusion");
        RegisterHelper.registerRecipeTypeWithLayout("mysticalagriculture", "awakening",
                "神秘农业觉醒", new MysticalAgricultureProcessor(), "infusion");
        RegisterHelper.registerRecipeTypeWithLayout("mysticalagriculture", "reprocessor",
                "神秘农业种子再处理器", new MysticalAgricultureProcessor(), "reprocessor");
        RegisterHelper.registerRecipeTypeWithLayout("farmersdelight", "cutting",
                "农夫乐事砧板", new FarmersDelightProcessor(), "cutting");
        RegisterHelper.registerRecipeTypeWithLayout("farmersdelight", "cooking",
                "农夫乐事烹饪", new FarmersDelightProcessor(), "cooking");
        BotaniaRecipeRegistration.registerBotaniaRecipes();
    }
}