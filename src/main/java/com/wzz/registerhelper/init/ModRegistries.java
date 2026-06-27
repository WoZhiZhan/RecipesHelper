package com.wzz.registerhelper.init;

import com.wzz.registerhelper.ModMain;
import com.wzz.registerhelper.ingredient.PartialNbtIngredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * NeoForge 1.21.1 注册中心
 * 目前用于注册自定义 IngredientType（PartialNbtIngredient）
 */
public class ModRegistries {

    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, ModMain.MODID);

    public static final Supplier<IngredientType<PartialNbtIngredient>> PARTIAL_NBT_INGREDIENT_TYPE =
            INGREDIENT_TYPES.register("partial_nbt",
                    () -> new IngredientType<>(PartialNbtIngredient.CODEC, PartialNbtIngredient.STREAM_CODEC));

    /**
     * 在 mod 构造函数中调用，将所有 DeferredRegister 绑定到 mod 事件总线
     */
    public static void register(IEventBus modEventBus) {
        INGREDIENT_TYPES.register(modEventBus);
    }
}
