package com.wzz.registerhelper.init;


import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.gui.recipe.layout.integration.mysticalagriculture.AwakeningLayout;
import com.wzz.registerhelper.recipe.integration.module.*;
import com.wzz.registerhelper.recipe.integration.register.BotaniaRecipeRegistration;
import com.wzz.registerhelper.util.RegisterHelper;

public class ModIntegrations {
    public static void registerAll() {
        CreateRecipeProcessor createRecipeProcessor = new CreateRecipeProcessor();
        LayoutManager.registerLayout("awakening", new AwakeningLayout());
        RegisterHelper.registerRecipeType("avaritia", "shaped_table",
                "registerhelper.recipe_type.mod.avaritia_shaped_table", new AvaritiaRecipeProcessor(), 9, true);
        RegisterHelper.registerRecipeType("eternisstarrysky", "vanilla_workbench_s",
                "registerhelper.recipe_type.mod.eternisstarrysky_workbench", new EternisStarrySkyProcessor(), 9, true);
        RegisterHelper.registerRecipeType("forever_love_sword", "starshine_oath_table",
                "registerhelper.recipe_type.mod.forever_love_sword", new ForeverLoveSwordProcessor(), 9, true);
        RegisterHelper.registerRecipeTypeWithLayout("mysticalagriculture", "infusion",
                "registerhelper.recipe_type.mod.mystical_infusion", new MysticalAgricultureProcessor(), "infusion");
        RegisterHelper.registerRecipeTypeWithLayout("mysticalagriculture", "awakening",
                "registerhelper.recipe_type.mod.mystical_awakening", new MysticalAgricultureProcessor(), "awakening");
        RegisterHelper.registerRecipeTypeWithLayout("mysticalagriculture", "reprocessor",
                "registerhelper.recipe_type.mod.mystical_reprocessor", new MysticalAgricultureProcessor(), "reprocessor");
        RegisterHelper.registerRecipeTypeWithLayout("farmersdelight", "cutting",
                "registerhelper.recipe_type.mod.farmers_cutting", new FarmersDelightProcessor(), "cutting");
        RegisterHelper.registerRecipeTypeWithLayout("farmersdelight", "cooking",
                "registerhelper.recipe_type.mod.farmers_cooking", new FarmersDelightProcessor(), "cooking");
        BotaniaRecipeRegistration.registerBotaniaRecipes();
        RegisterHelper.registerRecipeTypeWithLayout("create", "emptying",
                "registerhelper.recipe_type.mod.create_emptying", createRecipeProcessor, "emptying");
        RegisterHelper.registerRecipeTypeWithLayout("create", "cutting",
                "registerhelper.recipe_type.mod.create_cutting", createRecipeProcessor, "create_cutting");
        RegisterHelper.registerRecipeTypeWithLayout("create", "compacting",
                "registerhelper.recipe_type.mod.create_compacting", createRecipeProcessor, "compacting");
        RegisterHelper.registerRecipeTypeWithLayout("create", "pressing",
                "registerhelper.recipe_type.mod.create_pressing", createRecipeProcessor, "pressing");
        RegisterHelper.registerRecipeTypeWithLayout("create", "mixing",
                "registerhelper.recipe_type.mod.create_mixing", createRecipeProcessor, "compacting");
        RegisterHelper.registerRecipeTypeWithLayout("create", "filling",
                "registerhelper.recipe_type.mod.create_filling", createRecipeProcessor, "filling");
        RegisterHelper.registerRecipeType("arcanevortex", "van_sh_workbench_shaped",
                "registerhelper.recipe_type.mod.arcane_vortex", new ArcaneVortexProcessor(), 16, false);
        RegisterHelper.registerRecipeTypeWithLayout("astralrail_cube", "path_ascension",
                "registerhelper.recipe_type.mod.astralrail_ascension", 2,
                new AstralrailCubeProcessor(), "path_ascension");
        RegisterHelper.registerRecipeTypeWithLayout("astralrail_cube", "path_transmuter",
                "registerhelper.recipe_type.mod.astralrail_transmuter",
                new AstralrailCubeProcessor(), "path_transmuter");

        ExtendedCraftingProcessor extendedCraftingProcessor = new ExtendedCraftingProcessor();
        RegisterHelper.registerRecipeType("extendedcrafting", "shaped_table",
                "registerhelper.recipe_type.mod.extended_shaped", extendedCraftingProcessor, 3, true);
        RegisterHelper.registerRecipeType("extendedcrafting", "shaped_table_5x5",
                "registerhelper.recipe_type.mod.extended_shaped_5", extendedCraftingProcessor, 5, true, false);
        RegisterHelper.registerRecipeType("extendedcrafting", "shaped_table_7x7",
                "registerhelper.recipe_type.mod.extended_shaped_7", extendedCraftingProcessor, 7, true, false);
        RegisterHelper.registerRecipeType("extendedcrafting", "shaped_table_9x9",
                "registerhelper.recipe_type.mod.extended_shaped_9", extendedCraftingProcessor, 9, true, false);
        RegisterHelper.registerRecipeType("extendedcrafting", "shapeless_table",
                "registerhelper.recipe_type.mod.extended_shapeless", extendedCraftingProcessor, 3, true, true);
        RegisterHelper.registerRecipeType("extendedcrafting", "shapeless_table_5x5",
                "registerhelper.recipe_type.mod.extended_shapeless_5", extendedCraftingProcessor, 5, true, false);
        RegisterHelper.registerRecipeType("extendedcrafting", "shapeless_table_7x7",
                "registerhelper.recipe_type.mod.extended_shapeless_7", extendedCraftingProcessor, 7, true, false);
        RegisterHelper.registerRecipeType("extendedcrafting", "shapeless_table_9x9",
                "registerhelper.recipe_type.mod.extended_shapeless_9", extendedCraftingProcessor, 9, true, false);
    }
}
