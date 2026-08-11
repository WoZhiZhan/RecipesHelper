package com.wzz.registerhelper.recipe.integration.register;

import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.gui.recipe.layout.integration.botania.*;
import com.wzz.registerhelper.gui.recipe.layout.integration.botania.BrewingLayout;
import com.wzz.registerhelper.gui.recipe.layout.integration.botania.RunicAltarLayout;
import com.wzz.registerhelper.recipe.integration.module.BotaniaProcessor;

import static com.wzz.registerhelper.util.RegisterHelper.registerRecipeTypeWithLayout;

/**
 * 植物魔法配方注册类
 */
public class BotaniaRecipeRegistration {
    
    public static void registerBotaniaRecipes() {
        BotaniaProcessor processor = new BotaniaProcessor();
        
        if (!processor.isModLoaded()) {
            return;
        }

        registerBotaniaLayouts();

        registerRecipeTypeWithLayout("botania", "runic_altar", "registerhelper.recipe_type.botania.runic_altar",
            processor, "runic_altar");

        registerRecipeTypeWithLayout("botania", "mana_infusion", "registerhelper.recipe_type.botania.mana_infusion",
            processor, "mana_infusion");

        registerRecipeTypeWithLayout("botania", "elven_trade", "registerhelper.recipe_type.botania.elven_trade",
            processor, "elven_trade");

        registerRecipeTypeWithLayout("botania", "terra_plate", "registerhelper.recipe_type.botania.terra_plate",
            processor, "terra_plate");

        registerRecipeTypeWithLayout("botania", "petal_apothecary", "registerhelper.recipe_type.botania.petal_apothecary",
            processor, "petal_apothecary");

        registerRecipeTypeWithLayout("botania", "pure_daisy", "registerhelper.recipe_type.botania.pure_daisy",
            processor, "pure_daisy");

        registerRecipeTypeWithLayout("botania", "brew", "registerhelper.recipe_type.botania.brew",
            processor, "brew");

        registerRecipeTypeWithLayout("botania", "orechid", "registerhelper.recipe_type.botania.orechid",
            processor, "pure_daisy");

        registerRecipeTypeWithLayout("botania", "marimorphosis", "registerhelper.recipe_type.botania.marimorphosis",
            processor, "pure_daisy");
    }
    
    private static void registerBotaniaLayouts() {
        LayoutManager.registerLayout("runic_altar", new RunicAltarLayout());
        LayoutManager.registerLayout("petal_apothecary", new PetalApothecaryLayout());
        LayoutManager.registerLayout("pure_daisy", new PureDaisyLayout());
        LayoutManager.registerLayout("brew", new BrewingLayout());
        LayoutManager.registerLayout("mana_infusion", new ManaInfusionLayout());
        LayoutManager.registerLayout("terra_plate", new TerraPlateLayout());
        LayoutManager.registerLayout("elven_trade", new ElvenTradeLayout());
    }
}
