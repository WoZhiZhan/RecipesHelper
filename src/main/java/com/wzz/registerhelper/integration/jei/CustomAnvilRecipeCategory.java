package com.wzz.registerhelper.integration.jei;

import com.wzz.registerhelper.util.ResourceUtil;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 自定义铁砧配方分类（JEI）
 */
public class CustomAnvilRecipeCategory extends AbstractRecipeCategory<JEIAnvilRecipe> {

    public static final ResourceLocation UID = ResourceUtil.createInstance("registerhelper", "custom_anvil");
    public static final RecipeType<JEIAnvilRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, JEIAnvilRecipe.class);

    public CustomAnvilRecipeCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("registerhelper.jei.anvil"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, Items.ANVIL.asItem().getDefaultInstance()),
                125,  // 宽度
                38    // 高度
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, JEIAnvilRecipe recipe, @NotNull IFocusGroup focuses) {
        // 左侧物品
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
                .addItemStack(recipe.getLeft())
                .setStandardSlotBackground();

        // 右侧物品/材料
        builder.addSlot(RecipeIngredientRole.INPUT,50, 1)
                .addItemStack(recipe.getRight())
                .setStandardSlotBackground();

        // 输出
        builder.addSlot(RecipeIngredientRole.OUTPUT,108, 1)
                .addItemStack(recipe.getOutput())
                .setStandardSlotBackground();
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, JEIAnvilRecipe recipe, @NotNull IFocusGroup focuses) {
        // 添加加号
        builder.addRecipePlusSign()
                .setPosition(27, 3);

        // 添加箭头
        builder.addRecipeArrow()
                .setPosition(76, 1);

        // 显示经验消耗
        int cost = recipe.getCost();
        Component costText = Component.translatable("registerhelper.jei.anvil.cost",(Object) cost);

        builder.addText(costText, getWidth() - 4, 10)
                .setPosition(2, 27)
                .setColor(0xFF80FF20)  // 绿色
                .setShadow(true)
                .setTextAlignment(HorizontalAlignment.RIGHT);

        // 如果材料消耗大于1，显示在第二行
        if (recipe.getMaterialCost() > 1) {
            Component materialText = Component.translatable("registerhelper.jei.anvil.material", recipe.getMaterialCost());
            builder.addText(materialText, getWidth() - 4, 10)
                    .setPosition(2, 27 + 11)  // 第二行
                    .setColor(0xFFFFFFFF)
                    .setShadow(true)
                    .setTextAlignment(HorizontalAlignment.RIGHT);
        }
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(JEIAnvilRecipe recipe) {
        return ResourceUtil.createInstance("registerhelper", "anvil_" + recipe.hashCode());
    }
}