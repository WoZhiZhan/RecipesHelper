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

        registerRecipeTypeWithLayout("botania", "runic_altar", "符文祭坛", 
            processor, "runic_altar");

        registerRecipeTypeWithLayout("botania", "mana_infusion", "魔力灌注", 
            processor, "mana_infusion");

        registerRecipeTypeWithLayout("botania", "elven_trade", "精灵贸易", 
            processor, "elven_trade");

        registerRecipeTypeWithLayout("botania", "terra_plate", "泰拉凝聚板", 
            processor, "terra_plate");

        registerRecipeTypeWithLayout("botania", "petal_apothecary", "花瓣炼制",
            processor, "petal_apothecary");

        registerRecipeTypeWithLayout("botania", "pure_daisy", "白雏菊",
            processor, "pure_daisy");

        registerRecipeTypeWithLayout("botania", "brew", "植物酿造",
            processor, "brew");

        registerRecipeTypeWithLayout("botania", "orechid", "矿石兰", 
            processor, "pure_daisy");

        registerRecipeTypeWithLayout("botania", "marimorphosis", "石之变换", 
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