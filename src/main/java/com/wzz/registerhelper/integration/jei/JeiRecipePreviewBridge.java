package com.wzz.registerhelper.integration.jei;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional JEI integration used by the editor. JEI itself loads this class
 * only when the JEI plugin is present; the main editor uses it reflectively so
 * RegisterHelper still runs without JEI.
 */
public final class JeiRecipePreviewBridge {
    private static final Map<ResourceLocation, Object> LAYOUTS = new ConcurrentHashMap<>();
    private static volatile IJeiRuntime runtime;

    private JeiRecipePreviewBridge() {
    }

    public static void setRuntime(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        LAYOUTS.clear();
    }

    public static void clearRuntime() {
        runtime = null;
        LAYOUTS.clear();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object create(ResourceLocation recipeId) {
        return create(recipeId, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object create(ResourceLocation recipeId, Object runtimeRecipe) {
        IJeiRuntime jeiRuntime = runtime;
        if (jeiRuntime == null) return null;
        Object cached = LAYOUTS.get(recipeId);
        if (cached != null) return cached;

        try {
            IRecipeManager manager = jeiRuntime.getRecipeManager();
            for (IRecipeCategory<?> category : manager.createRecipeCategoryLookup()
                    .includeHidden().get().toList()) {
                RecipeType type = category.getRecipeType();
                Object recipe = manager.createRecipeLookup(type).includeHidden().get()
                        .filter(candidate -> matchesRecipe((IRecipeCategory) category,
                                recipeId, runtimeRecipe, candidate))
                        .findFirst().orElse(null);
                if (recipe == null) continue;

                IFocusGroup focusGroup = jeiRuntime.getJeiHelpers()
                        .getFocusFactory().getEmptyFocusGroup();
                IRecipeLayoutDrawable layout = (IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(
                        (IRecipeCategory) category, recipe, focusGroup).orElse(null);
                if (layout != null) {
                    LAYOUTS.put(recipeId, layout);
                    return layout;
                }
            }
        } catch (Exception ignored) {
            // The editor will use its built-in adapter when JEI rejects a layout.
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean matchesRecipe(IRecipeCategory category, ResourceLocation id,
                                         Object runtimeRecipe, Object candidate) {
        if (runtimeRecipe != null && candidate == runtimeRecipe) return true;
        try {
            if (id.equals(category.getRegistryName(candidate))) return true;
        } catch (Exception ignored) {
            // A category may use a private wrapper type; try the next matcher.
        }
        if (runtimeRecipe != null) {
            try {
                if (runtimeRecipe instanceof Recipe<?> runtime && candidate instanceof Recipe<?> candidateRecipe
                        && id.equals(candidateRecipe.getId())) {
                    return category.isHandled(candidate);
                }
                return runtimeRecipe.getClass().isInstance(candidate)
                        && category.isHandled(candidate)
                        && runtimeRecipe.getClass().equals(candidate.getClass());
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static void setPosition(Object layout, int x, int y) {
        ((IRecipeLayoutDrawable<Object>) layout).setPosition(x, y);
    }

    @SuppressWarnings("unchecked")
    public static void tick(Object layout) {
        ((IRecipeLayoutDrawable<Object>) layout).tick();
    }

    @SuppressWarnings("unchecked")
    public static void draw(Object layout, GuiGraphics graphics, int mouseX, int mouseY) {
        ((IRecipeLayoutDrawable<Object>) layout).drawRecipe(graphics, mouseX, mouseY);
    }

    @SuppressWarnings("unchecked")
    public static void drawOverlays(Object layout, GuiGraphics graphics, int mouseX, int mouseY) {
        ((IRecipeLayoutDrawable<Object>) layout).drawOverlays(graphics, mouseX, mouseY);
    }

    @SuppressWarnings("unchecked")
    public static int width(Object layout) {
        return ((IRecipeLayoutDrawable<Object>) layout).getRect().getWidth();
    }

    @SuppressWarnings("unchecked")
    public static int height(Object layout) {
        return ((IRecipeLayoutDrawable<Object>) layout).getRect().getHeight();
    }

    @SuppressWarnings("unchecked")
    public static Rect2i rect(Object layout) {
        return ((IRecipeLayoutDrawable<Object>) layout).getRect();
    }

    @SuppressWarnings("unchecked")
    public static int hoveredSlotIndex(Object layout, double mouseX, double mouseY) {
        IRecipeLayoutDrawable<Object> drawable = (IRecipeLayoutDrawable<Object>) layout;
        RecipeSlotUnderMouse underMouse = drawable.getSlotUnderMouse(mouseX, mouseY).orElse(null);
        if (underMouse == null) return -1;
        IRecipeSlotsView slots = drawable.getRecipeSlotsView();
        IRecipeSlotDrawable slot = underMouse.slot();
        for (int i = 0; i < slots.getSlotViews().size(); i++) {
            if (slots.getSlotViews().get(i) == slot) return i;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public static String hoveredRole(Object layout, double mouseX, double mouseY) {
        IRecipeLayoutDrawable<Object> drawable = (IRecipeLayoutDrawable<Object>) layout;
        RecipeSlotUnderMouse underMouse = drawable.getSlotUnderMouse(mouseX, mouseY).orElse(null);
        if (underMouse == null) return "";
        return underMouse.slot().getRole().name();
    }

    @SuppressWarnings("unchecked")
    public static int hoveredRoleIndex(Object layout, double mouseX, double mouseY, String role) {
        IRecipeLayoutDrawable<Object> drawable = (IRecipeLayoutDrawable<Object>) layout;
        RecipeSlotUnderMouse underMouse = drawable.getSlotUnderMouse(mouseX, mouseY).orElse(null);
        if (underMouse == null || underMouse.slot().getRole().name().equals(role) == false) return -1;
        int ordinal = 0;
        for (IRecipeSlotView slot : drawable.getRecipeSlotsView().getSlotViews()) {
            if (!slot.getRole().name().equals(role)) continue;
            if (slot == underMouse.slot()) return ordinal;
            ordinal++;
        }
        return -1;
    }
}
