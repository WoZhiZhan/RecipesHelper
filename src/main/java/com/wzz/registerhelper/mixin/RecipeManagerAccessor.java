package com.wzz.registerhelper.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 访问 RecipeManager 的内部字段，用于在配方加载完成后（apply 的 TAIL）
 * 移除黑名单配方 —— 包括其它 mod 用代码注册（非 json）的配方。
 *
 * 1.20.1 official mappings:
 *   recipes : Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>
 *   byName  : Map<ResourceLocation, Recipe<?>>
 */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {

    @Accessor("recipes")
    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> registerhelper$getRecipes();

    @Mutable
    @Accessor("recipes")
    void registerhelper$setRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes);

    @Accessor("byName")
    Map<ResourceLocation, Recipe<?>> registerhelper$getByName();

    @Mutable
    @Accessor("byName")
    void registerhelper$setByName(Map<ResourceLocation, Recipe<?>> byName);
}
