package com.wzz.registerhelper.integration.jei;

import com.wzz.registerhelper.recipe.CustomRecipeLoader;
import net.minecraft.world.item.ItemStack;

/**
 * 酿造台配方JEI包装
 */
public class JEIBrewingRecipe {
    private final ItemStack input;
    private final ItemStack ingredient;
    private final ItemStack output;
    
    public JEIBrewingRecipe(CustomRecipeLoader.BrewingRecipeData data) {
        this.input = data.input.copy();
        this.ingredient = data.ingredient.copy();
        this.output = data.output.copy();
    }
    
    public ItemStack getInput() {
        return input;
    }
    
    public ItemStack getIngredient() {
        return ingredient;
    }
    
    public ItemStack getOutput() {
        return output;
    }
}