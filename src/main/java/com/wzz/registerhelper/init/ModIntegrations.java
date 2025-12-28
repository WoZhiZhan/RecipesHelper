package com.wzz.registerhelper.init;


import com.wzz.registerhelper.recipe.integration.module.*;
import com.wzz.registerhelper.recipe.integration.register.BotaniaRecipeRegistration;
import com.wzz.registerhelper.util.RegisterHelper;

public class ModIntegrations {
    public static void registerAll() {
        CreateRecipeProcessor createRecipeProcessor = new CreateRecipeProcessor();
        RegisterHelper.registerRecipeType("avaritia", "shaped_table",
                "无尽贪婪:合成", new AvaritiaRecipeProcessor(), 9, true);
        RegisterHelper.registerRecipeType("eternisstarrysky", "vanilla_workbench_s",
                "星空永恒:有序合成", new EternisStarrySkyProcessor(), 9, true);
        RegisterHelper.registerRecipeType("forever_love_sword", "starshine_oath_table",
                "永爱之刃:星辉誓约台", new ForeverLoveSwordProcessor(), 9, true);
        RegisterHelper.registerRecipeTypeWithLayout("mysticalagriculture", "infusion",
                "神秘农业:注魔", new MysticalAgricultureProcessor(), "infusion");
        RegisterHelper.registerRecipeTypeWithLayout("mysticalagriculture", "awakening",
                "神秘农业:觉醒", new MysticalAgricultureProcessor(), "infusion");
        RegisterHelper.registerRecipeTypeWithLayout("mysticalagriculture", "reprocessor",
                "神秘农业:种子再处理器", new MysticalAgricultureProcessor(), "reprocessor");
        RegisterHelper.registerRecipeTypeWithLayout("farmersdelight", "cutting",
                "农夫乐事:砧板", new FarmersDelightProcessor(), "cutting");
        RegisterHelper.registerRecipeTypeWithLayout("farmersdelight", "cooking",
                "农夫乐事:烹饪", new FarmersDelightProcessor(), "cooking");
        BotaniaRecipeRegistration.registerBotaniaRecipes();
        RegisterHelper.registerRecipeTypeWithLayout("create", "emptying",
                "机械动力:分液", createRecipeProcessor, "emptying");
        RegisterHelper.registerRecipeTypeWithLayout("create", "cutting",
                "机械动力:切削", createRecipeProcessor, "create_cutting");
        RegisterHelper.registerRecipeTypeWithLayout("create", "compacting",
                "机械动力:塑形", createRecipeProcessor, "compacting");
        RegisterHelper.registerRecipeTypeWithLayout("create", "pressing",
                "机械动力:冲压", createRecipeProcessor, "pressing");
        RegisterHelper.registerRecipeTypeWithLayout("create", "mixing",
                "机械动力:混合搅拌", createRecipeProcessor, "compacting");
        RegisterHelper.registerRecipeTypeWithLayout("create", "filling",
                "机械动力:注液", createRecipeProcessor, "filling");
    }
}