package com.wzz.registerhelper.integration.jei;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;

/** Reflection-only facade so the main GUI remains optional-JEI safe. */
public final class JeiRecipePreviewFacade {
    private static final String BRIDGE =
            "com.wzz.registerhelper.integration.jei.JeiRecipePreviewBridge";
    private static boolean checked;
    private static Class<?> bridgeClass;
    private static Method create;
    private static Method setPosition;
    private static Method draw;
    private static Method overlays;
    private static Method tick;
    private static Method roleIndex;

    private JeiRecipePreviewFacade() {
    }

    public static Object create(ResourceLocation id) {
        if (!load()) return null;
        try {
            return create.invoke(null, id);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Object create(ResourceLocation id, Object runtimeRecipe) {
        if (!load()) return null;
        try {
            Method method = bridgeClass.getMethod("create", ResourceLocation.class, Object.class);
            return method.invoke(null, id, runtimeRecipe);
        } catch (Exception ignored) {
            return create(id);
        }
    }

    public static void setPosition(Object layout, int x, int y) {
        invoke(setPosition, layout, x, y);
    }

    public static void draw(Object layout, GuiGraphics graphics, int mouseX, int mouseY) {
        invoke(draw, layout, graphics, mouseX, mouseY);
    }

    public static void drawOverlays(Object layout, GuiGraphics graphics, int mouseX, int mouseY) {
        invoke(overlays, layout, graphics, mouseX, mouseY);
    }

    public static int width(Object layout) {
        return invokeInt("width", layout);
    }

    public static int height(Object layout) {
        return invokeInt("height", layout);
    }

    public static void tick(Object layout) {
        invoke(tick, layout);
    }

    public static int hoveredRoleIndex(Object layout, double mouseX, double mouseY, String role) {
        if (!load()) return -1;
        try {
            return (int) roleIndex.invoke(null, layout, mouseX, mouseY, role);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static void invoke(Method method, Object... args) {
        if (!load() || method == null) return;
        try {
            method.invoke(null, args);
        } catch (Exception ignored) {
        }
    }

    private static int invokeInt(String name, Object layout) {
        if (!load()) return 0;
        try {
            return (int) bridgeClass.getMethod(name, Object.class).invoke(null, layout);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static boolean load() {
        if (checked) return bridgeClass != null;
        checked = true;
        try {
            bridgeClass = Class.forName(BRIDGE);
            create = bridgeClass.getMethod("create", ResourceLocation.class);
            setPosition = bridgeClass.getMethod("setPosition", Object.class, int.class, int.class);
            draw = bridgeClass.getMethod("draw", Object.class, GuiGraphics.class, int.class, int.class);
            overlays = bridgeClass.getMethod("drawOverlays", Object.class, GuiGraphics.class,
                    int.class, int.class);
            tick = bridgeClass.getMethod("tick", Object.class);
            roleIndex = bridgeClass.getMethod("hoveredRoleIndex", Object.class,
                    double.class, double.class, String.class);
            return true;
        } catch (Exception ignored) {
            bridgeClass = null;
            return false;
        }
    }
}
