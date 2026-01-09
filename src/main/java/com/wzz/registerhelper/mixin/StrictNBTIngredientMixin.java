package com.wzz.registerhelper.mixin;

import com.wzz.registerhelper.init.ModConfig;
import com.wzz.registerhelper.util.NbtMatchingUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * StrictNBTIngredient Mixin
 * 用于为本mod的配方提供NBT模糊匹配功能
 */
@Mixin(value = StrictNBTIngredient.class, remap = false)
public abstract class StrictNBTIngredientMixin {
    
    @Shadow
    @Final
    private ItemStack stack;
    
    /**
     * 注入到test方法，实现NBT模糊匹配
     * 
     * @param input 待测试的物品
     * @param cir 回调信息
     */
    @Inject(
            method = "test(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onTest(ItemStack input, CallbackInfoReturnable<Boolean> cir) {
        if (!ModConfig.isFuzzyNbtMatchingEnabled()) {
            return;
        }
        if (!input.is(stack.getItem())) {
            cir.setReturnValue(false);
            return;
        }
        CompoundTag requiredNbt = stack.getTag();
        CompoundTag actualNbt = input.getTag();

        if (requiredNbt == null || requiredNbt.isEmpty()) {
            cir.setReturnValue(true);
            return;
        }

        if (actualNbt == null || actualNbt.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }

        boolean matches = NbtMatchingUtil.fuzzyNbtMatches(actualNbt, requiredNbt);
        
        if (ModConfig.isDebugLoggingEnabled()) {
            if (matches) {
                org.slf4j.LoggerFactory.getLogger("RegisterHelper").info(
                    "NBT模糊匹配成功: {} 匹配配方需求", input.getDisplayName().getString()
                );
            }
        }
        
        cir.setReturnValue(matches);
    }
}