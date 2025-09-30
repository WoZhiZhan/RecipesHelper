package com.wzz.registerhelper.mixinaccess;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Map;

public interface IRecipeManager {
    void setByName(Map<ResourceLocation, Recipe<?>> byName);

    void setRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes);

    void safeSetRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes);

    Map<ResourceLocation, Recipe<?>> getByName0();

    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> getRecipes0();
}
