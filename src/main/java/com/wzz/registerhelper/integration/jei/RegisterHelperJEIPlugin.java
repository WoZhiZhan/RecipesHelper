package com.wzz.registerhelper.integration.jei;

import com.wzz.registerhelper.recipe.CustomRecipeLoader;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * JEI集成插件
 * 显示自定义酿造台和铁砧配方
 */
@JeiPlugin
public class RegisterHelperJEIPlugin implements IModPlugin {
    
    private static final ResourceLocation PLUGIN_ID = new ResourceLocation("registerhelper", "jei_plugin");
    
    @Override
    @NotNull
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }
    
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        // 注册自定义配方分类
        registration.addRecipeCategories(
            new CustomBrewingRecipeCategory(registration.getJeiHelpers().getGuiHelper())
        );
        
        registration.addRecipeCategories(
            new CustomAnvilRecipeCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }
    
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // 注册酿造台配方到JEI
        var brewingRecipes = CustomRecipeLoader.getBrewingRecipes().stream()
            .map(JEIBrewingRecipe::new)
            .toList();
        
        if (!brewingRecipes.isEmpty()) {
            registration.addRecipes(
                CustomBrewingRecipeCategory.RECIPE_TYPE,
                brewingRecipes
            );
        }
        
        // 注册铁砧配方到JEI
        var anvilRecipes = CustomRecipeLoader.getAnvilRecipes().stream()
            .map(JEIAnvilRecipe::new)
            .toList();
        
        if (!anvilRecipes.isEmpty()) {
            registration.addRecipes(
                CustomAnvilRecipeCategory.RECIPE_TYPE,
                anvilRecipes
            );
        }
    }
}