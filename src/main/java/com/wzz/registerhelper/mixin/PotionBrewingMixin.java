package com.wzz.registerhelper.mixin;

import com.wzz.registerhelper.recipe.CustomRecipeLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 酿造台Mixin - 支持自定义酿造配方
 */
@Mixin(PotionBrewing.class)
public class PotionBrewingMixin {
    
    /**
     * 在检查是否可以酿造时，添加自定义配方检查
     */
    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private static void onHasMix(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        // 检查自定义配方
        if (CustomRecipeLoader.hasBrewingRecipe(input, ingredient)) {
            cir.setReturnValue(true);
        }
    }
    
    /**
     * 在执行酿造时，使用自定义配方
     */
    @Inject(method = "mix", at = @At("HEAD"), cancellable = true)
    private static void onMix(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<ItemStack> cir) {
        // 尝试使用自定义配方
        ItemStack result = CustomRecipeLoader.getBrewingResult(input, ingredient);
        if (!result.isEmpty()) {
            cir.setReturnValue(result);
        }
    }
}