package com.wzz.registerhelper.mixinaccess;

import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Map;

public interface IRecipeManager {

    /**
     * 获取按类型分组的配方（原 recipes 字段）
     */
    Multimap<RecipeType<?>, RecipeHolder<?>> getByType();

    /**
     * 获取按名称索引的配方
     */
    Map<ResourceLocation, RecipeHolder<?>> getByName();

    /**
     * 设置按名称索引的配方
     */
    void setByName(Map<ResourceLocation, RecipeHolder<?>> byName);

    /**
     * 设置按类型分组的配方
     */
    void setByType(Multimap<RecipeType<?>, RecipeHolder<?>> byType);

    /**
     * 安全地设置配方（创建不可变副本）
     */
    void safeSetRecipes(Multimap<RecipeType<?>, RecipeHolder<?>> byType,
                        Map<ResourceLocation, RecipeHolder<?>> byName);
}