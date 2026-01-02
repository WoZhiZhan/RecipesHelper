package com.wzz.registerhelper.integration.jei;

import com.wzz.registerhelper.recipe.CustomRecipeLoader;
import net.minecraft.world.item.ItemStack;

/**
 * 铁砧配方JEI包装
 */
public class JEIAnvilRecipe {
    private final ItemStack left;
    private final ItemStack right;
    private final ItemStack output;
    private final int cost;
    private final int materialCost;
    
    public JEIAnvilRecipe(CustomRecipeLoader.AnvilRecipeData data) {
        this.left = data.left.copy();
        this.right = data.right.copy();
        this.output = data.output.copy();
        this.cost = data.cost;
        this.materialCost = data.materialCost;
    }
    
    public ItemStack getLeft() {
        return left;
    }
    
    public ItemStack getRight() {
        return right;
    }
    
    public ItemStack getOutput() {
        return output;
    }
    
    public int getCost() {
        return cost;
    }
    
    public int getMaterialCost() {
        return materialCost;
    }
}