package com.wzz.registerhelper.integration.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.textures.Textures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 自定义酿造台配方分类（JEI）
 */
public class CustomBrewingRecipeCategory extends AbstractRecipeCategory<JEIBrewingRecipe> {

    public static final ResourceLocation UID = new ResourceLocation("registerhelper", "custom_brewing");
    public static final RecipeType<JEIBrewingRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, JEIBrewingRecipe.class);

    public CustomBrewingRecipeCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("registerhelper.jei.brewing"),
                guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, Items.BREWING_STAND.asItem().getDefaultInstance()),
                114,
                61
        );
    }

    @Override
    public void draw(JEIBrewingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Textures textures = Internal.getTextures();

        // 绘制酿造台背景
        textures.getBrewingStandBackground().draw(guiGraphics, 0, 1);

        // 绘制火焰
        textures.getBrewingStandBlazeHeat().draw(guiGraphics, 5, 30);

        // 绘制气泡
        textures.getBrewingStandBubbles().draw(guiGraphics, 9, 1);

        // 绘制箭头
        textures.getBrewingStandArrow().draw(guiGraphics, 43, 3);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, JEIBrewingRecipe recipe, @NotNull IFocusGroup focuses) {
        // 输入药水
        builder.addSlot(RecipeIngredientRole.INPUT,24, 44)
                .addItemStack(recipe.getInput());

        // 酿造材料
        builder.addSlot(RecipeIngredientRole.INPUT,24, 3)
                .addItemStack(recipe.getIngredient());

        // 输出药水
        builder.addSlot(RecipeIngredientRole.OUTPUT,81, 3)
                .addItemStack(recipe.getOutput())
                .setStandardSlotBackground();
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, JEIBrewingRecipe recipe, @NotNull IFocusGroup focuses) {
        // 添加步骤提示（模仿原版）
        Component steps = Component.translatable("registerhelper.jei.brewing.steps", "1");

        builder.addText(steps, 42, 12)
                .setPosition(70, 28)
                .setColor(0xFF808080);
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(JEIBrewingRecipe recipe) {
        return new ResourceLocation("registerhelper", "brewing_" + recipe.hashCode());
    }
}