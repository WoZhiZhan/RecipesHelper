package com.wzz.registerhelper.mixin;

import com.wzz.registerhelper.recipe.CustomRecipeLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 酿造台Mixin - 支持自定义酿造配方
 * NeoForge 1.21.1 迁移要点：
 * 1.20.5+ 起 PotionBrewing 的 hasMix / mix 由静态方法改为实例方法
 * （PotionBrewing 现在是一个持有配方表的实例，挂在 server 上）。
 * 因此注入方法去掉 static。方法签名 (ItemStack input, ItemStack ingredient) 不变。
 */
@Mixin(PotionBrewing.class)
public class PotionBrewingMixin {

    /**
     * 在检查是否可以酿造时，添加自定义配方检查
     */
    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private void onHasMix(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (CustomRecipeLoader.hasBrewingRecipe(input, ingredient)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 在执行酿造时，使用自定义配方
     */
    @Inject(method = "mix", at = @At("HEAD"), cancellable = true)
    private void onMix(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = CustomRecipeLoader.getBrewingResult(input, ingredient);
        if (!result.isEmpty()) {
            cir.setReturnValue(result);
        }
    }
}
