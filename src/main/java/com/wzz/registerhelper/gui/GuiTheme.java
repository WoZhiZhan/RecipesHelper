package com.wzz.registerhelper.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Shared visual language for RegisterHelper screens. */
public final class GuiTheme {
    private static final ResourceLocation GENERIC_54_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    private static final ResourceLocation BOTANIA_PETAL_OVERLAY =
            new ResourceLocation("botania", "textures/gui/petal_overlay.png");
    private static final ResourceLocation BOTANIA_PURE_DAISY_OVERLAY =
            new ResourceLocation("botania", "textures/gui/pure_daisy_overlay.png");
    private static final ResourceLocation BOTANIA_TERRA_OVERLAY =
            new ResourceLocation("botania", "textures/gui/terrasteel_jei_overlay.png");
    private static final ResourceLocation BOTANIA_ELVEN_OVERLAY =
            new ResourceLocation("botania", "textures/gui/elven_trade_overlay.png");
    private static final ResourceLocation CREATE_JEI_WIDGETS =
            new ResourceLocation("create", "textures/gui/jei/widgets.png");
    public static final ResourceLocation EDITOR_FONT =
            new ResourceLocation("minecraft", "uniform");
    private static final int VANILLA_SLOT_SIZE = 18;
    private static final int VANILLA_SLOT_U = 7;
    private static final int VANILLA_SLOT_V = 17;

    private GuiTheme() {
    }

    private record Palette(
            int backdrop,
            int panelShadow,
            int panelEdge,
            int panel,
            int panelAlt,
            int header,
            int headerAccent,
            int headerAccentWarm,
            int surface,
            int surfaceAlt,
            int section,
            int border,
            int divider,
            int text,
            int textMuted,
            int textOnHeader,
            int hover,
            int selected,
            int selectedEdge,
            int info,
            int success,
            int warning,
            int danger,
            int dangerSoft,
            int input,
            int inputEdge,
            int scrollTrack,
            int scrollThumb,
            int scrollThumbHover,
            int slot,
            int slotEdge
    ) {
    }

    private static final Palette SOFT_DARK = new Palette(
            0x8A101820, 0x44050A0F, 0xFF41505A, 0xFF263139, 0xFF2D3942,
            0xFF1D313D, 0xFF4FA99B, 0xFFD49B57, 0xFF303D46, 0xFF35434D,
            0xFF3A4A55, 0xFF52636F, 0xFF46545E, 0xFFE6ECEF, 0xFFA6B4BC,
            0xFFF6FAFB, 0xFF3A565B, 0xFF2F625C, 0xFF64C8B3, 0xFF7AAED2,
            0xFF72C99A, 0xFFE4B267, 0xFFE07A7A, 0xFF5A3D42, 0xFF202A31,
            0xFF647883, 0xFF3D4A53, 0xFF718A96, 0xFF9AB7C0, 0xFF29363F,
            0xFF6B7F8B);

    private static final Palette LIGHT = new Palette(
            0x8A52656B, 0x330A1724, 0xFFB9C7D5, 0xFFF7FAFC, 0xFFEAF1F5,
            0xFF2D5264, 0xFF74C4B5, 0xFFE5AA62, 0xFFFFFFFF, 0xFFF1F5F8,
            0xFFE4EDF2, 0xFFB8C8D4, 0xFFD7E1E8, 0xFF243746, 0xFF62788A,
            0xFFFFFFFF, 0xFFDDF3EF, 0xFFC9E9E3, 0xFF299B8A, 0xFF4D8BB7,
            0xFF3D9B76, 0xFFE0A04A, 0xFFD65D62, 0xFFFBE8E8, 0xFFFFFFFF,
            0xFF9FB4C4, 0xFFDCE6EC, 0xFF8EA6B5, 0xFF5E8797, 0xFFE8EEF2,
            0xFFA9BBC8);

    public static int BACKDROP;
    public static int PANEL_SHADOW;
    public static int PANEL_EDGE;
    public static int PANEL;
    public static int PANEL_ALT;
    public static int HEADER;
    public static int HEADER_ACCENT;
    public static int HEADER_ACCENT_WARM;
    public static int SURFACE;
    public static int SURFACE_ALT;
    public static int SECTION;
    public static int BORDER;
    public static int DIVIDER;
    public static int TEXT;
    public static int TEXT_MUTED;
    public static int TEXT_ON_HEADER;
    public static int HOVER;
    public static int SELECTED;
    public static int SELECTED_EDGE;
    public static int INFO;
    public static int SUCCESS;
    public static int WARNING;
    public static int DANGER;
    public static int DANGER_SOFT;
    public static int INPUT;
    public static int INPUT_EDGE;
    public static int SCROLL_TRACK;
    public static int SCROLL_THUMB;
    public static int SCROLL_THUMB_HOVER;
    public static int SLOT;
    public static int SLOT_EDGE;

    private static String currentTheme;
    private static boolean recipeCanvas;
    private static String recipeCanvasLayout;

    static {
        applyTheme("soft_dark");
    }

    public static void applyTheme(String theme) {
        String normalized = normalizeTheme(theme);
        Palette palette = "light".equals(normalized) ? LIGHT : SOFT_DARK;
        BACKDROP = palette.backdrop();
        PANEL_SHADOW = palette.panelShadow();
        PANEL_EDGE = palette.panelEdge();
        PANEL = palette.panel();
        PANEL_ALT = palette.panelAlt();
        HEADER = palette.header();
        HEADER_ACCENT = palette.headerAccent();
        HEADER_ACCENT_WARM = palette.headerAccentWarm();
        SURFACE = palette.surface();
        SURFACE_ALT = palette.surfaceAlt();
        SECTION = palette.section();
        BORDER = palette.border();
        DIVIDER = palette.divider();
        TEXT = palette.text();
        TEXT_MUTED = palette.textMuted();
        TEXT_ON_HEADER = palette.textOnHeader();
        HOVER = palette.hover();
        SELECTED = palette.selected();
        SELECTED_EDGE = palette.selectedEdge();
        INFO = palette.info();
        SUCCESS = palette.success();
        WARNING = palette.warning();
        DANGER = palette.danger();
        DANGER_SOFT = palette.dangerSoft();
        INPUT = palette.input();
        INPUT_EDGE = palette.inputEdge();
        SCROLL_TRACK = palette.scrollTrack();
        SCROLL_THUMB = palette.scrollThumb();
        SCROLL_THUMB_HOVER = palette.scrollThumbHover();
        SLOT = palette.slot();
        SLOT_EDGE = palette.slotEdge();
        currentTheme = normalized;
    }

    public static String normalizeTheme(String theme) {
        return "light".equals(theme) ? "light" : "soft_dark";
    }

    public static String currentTheme() {
        return currentTheme;
    }

    public static String nextTheme(String theme) {
        return "light".equals(normalizeTheme(theme)) ? "soft_dark" : "light";
    }

    public static String themeLabelKey(String theme) {
        return "light".equals(normalizeTheme(theme))
                ? "registerhelper.gui.config.theme.value.light"
                : "registerhelper.gui.config.theme.value.soft_dark";
    }

    public static void drawBackdrop(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, BACKDROP);
    }

    public static void drawPanel(GuiGraphics graphics, GuiLayoutHelper.Bounds bounds,
                                 int headerHeight, int accentColor) {
        graphics.fill(bounds.x() - 3, bounds.y() - 2,
                bounds.right() + 3, bounds.bottom() + 3, PANEL_SHADOW);
        graphics.fill(bounds.x() - 1, bounds.y() - 1,
                bounds.right() + 1, bounds.bottom() + 1, PANEL_EDGE);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), PANEL);
        if (headerHeight > 0) {
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + headerHeight, HEADER);
            graphics.fill(bounds.x(), bounds.y() + headerHeight - 2,
                    bounds.right(), bounds.y() + headerHeight, accentColor);
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, HEADER_ACCENT);
        }
    }

    public static void drawSurface(GuiGraphics graphics, GuiLayoutHelper.Bounds bounds,
                                   boolean alternate) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), BORDER);
        graphics.fill(bounds.x() + 1, bounds.y() + 1,
                bounds.right() - 1, bounds.bottom() - 1,
                alternate ? PANEL_ALT : SURFACE);
    }

    public static void drawRow(GuiGraphics graphics, int x, int y, int width, int height,
                               int index, boolean hovered, boolean selected) {
        int color = selected ? SELECTED : hovered ? HOVER
                : (index & 1) == 0 ? SURFACE : SURFACE_ALT;
        graphics.fill(x, y, x + width, y + height, color);
        graphics.fill(x, y + height - 1, x + width, y + height, DIVIDER);
        if (selected) {
            graphics.fill(x, y, x + 3, y + height, SELECTED_EDGE);
        }
    }

    public static void drawSlot(GuiGraphics graphics, int x, int y, int width, int height,
                                 boolean hovered) {
        if (recipeCanvas && width == VANILLA_SLOT_SIZE && height == VANILLA_SLOT_SIZE) {
            graphics.fill(x, y, x + width, y + height, 0xFF5B5B5B);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFB8B8B8);
        }
        if (recipeCanvas && isCreateRecipeLayout(recipeCanvasLayout)
                && width == VANILLA_SLOT_SIZE && height == VANILLA_SLOT_SIZE) {
            graphics.blit(CREATE_JEI_WIDGETS, x, y, 18, 18,
                    VANILLA_SLOT_SIZE, VANILLA_SLOT_SIZE, 256, 256);
        } else if (width == VANILLA_SLOT_SIZE && height == VANILLA_SLOT_SIZE) {
            graphics.blit(GENERIC_54_TEXTURE, x, y,
                    VANILLA_SLOT_U, VANILLA_SLOT_V,
                    VANILLA_SLOT_SIZE, VANILLA_SLOT_SIZE);
        } else {
            // Large grids can shrink below 18px. Keep the vanilla recessed-slot
            // lighting instead of stretching and blurring the source texture.
            graphics.fill(x, y, x + width, y + height, 0xFF8B8B8B);
            if (width > 2 && height > 2) {
                graphics.fill(x, y, x + width, y + 1, 0xFF373737);
                graphics.fill(x, y, x + 1, y + height, 0xFF373737);
                graphics.fill(x, y + height - 1, x + width, y + height, 0xFFFFFFFF);
                graphics.fill(x + width - 1, y, x + width, y + height, 0xFFFFFFFF);
            }
        }
        if (hovered && width > 2 && height > 2) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x60FFFFFF);
        }
    }

    public static void styleInput(EditBox input) {
        input.setBordered(false);
        input.setTextColor(recipeCanvas ? 0xFF202020 : TEXT);
        input.setTextColorUneditable(recipeCanvas ? 0xFF505050 : TEXT_MUTED);
    }

    public static void drawInput(GuiGraphics graphics, EditBox input) {
        if (input == null || !input.visible) {
            return;
        }
        int x = input.getX();
        int y = input.getY();
        int right = x + input.getWidth();
        int bottom = y + input.getHeight();
        input.setTextColor(recipeCanvas ? 0xFF202020 : TEXT);
        input.setTextColorUneditable(recipeCanvas ? 0xFF505050 : TEXT_MUTED);
        int edge = recipeCanvas
                ? (input.isFocused() ? 0xFF426A86 : 0xFF77838B)
                : (input.isFocused() ? SELECTED_EDGE : INPUT_EDGE);
        int fill = recipeCanvas ? 0xFFE8ECEF : INPUT;
        graphics.fill(x - 1, y - 1, right + 1, bottom + 1, edge);
        graphics.fill(x, y, right, bottom, fill);
    }

    /** Returns a readable color for labels placed on the current theme. */
    public static int readableLabelColor(int requested) {
        int red = requested >> 16 & 0xFF;
        int green = requested >> 8 & 0xFF;
        int blue = requested & 0xFF;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        if (recipeCanvas) {
            return luminance < 150 ? 0xFF202020 : requested;
        }
        if (!"light".equals(currentTheme) && luminance < 150) {
            return TEXT;
        }
        return requested;
    }

    public static void beginRecipeCanvas() {
        recipeCanvas = true;
        recipeCanvasLayout = null;
    }

    public static void beginRecipeCanvas(String layout) {
        recipeCanvas = true;
        recipeCanvasLayout = layout;
    }

    private static boolean isCreateRecipeLayout(String layout) {
        return layout != null && switch (layout) {
            case "create_cutting", "pressing", "filling", "emptying",
                    "compacting", "sequenced_assembly" -> true;
            default -> false;
        };
    }

    public static void endRecipeCanvas() {
        recipeCanvas = false;
        recipeCanvasLayout = null;
    }

    /** Draws a special-recipe overlay using the layout's logical top-left origin. */
    public static void drawRecipeOverlayAt(GuiGraphics graphics, String layout,
                                           int originX, int originY, float scale) {
        float safeScale = Math.max(0.1F, Math.min(1.0F, scale));
        ResourceLocation texture;
        int u;
        int v;
        int width;
        int height;
        int offsetX;
        int offsetY;
        switch (layout) {
            case "mana_infusion" -> {
                // Botania's JEI category uses pure_daisy_overlay for the mana pool.
                texture = BOTANIA_PURE_DAISY_OVERLAY;
                u = 0;
                v = 0;
                width = 64;
                height = 46;
                offsetX = 40;
                offsetY = 0;
            }
            case "terra_plate" -> {
                texture = BOTANIA_TERRA_OVERLAY;
                u = 42;
                v = 29;
                width = 64;
                height = 64;
                offsetX = 25;
                offsetY = 14;
            }
            case "petal_apothecary", "runic_altar" -> {
                texture = BOTANIA_PETAL_OVERLAY;
                u = 17;
                v = 11;
                width = 114;
                height = 82;
                offsetX = 0;
                offsetY = 4;
            }
            case "pure_daisy" -> {
                texture = BOTANIA_PURE_DAISY_OVERLAY;
                u = 0;
                v = 0;
                width = 64;
                height = 44;
                offsetX = 17;
                offsetY = 0;
            }
            case "elven_trade" -> {
                texture = BOTANIA_ELVEN_OVERLAY;
                u = 0;
                v = 15;
                width = 140;
                height = 90;
                offsetX = 0;
                offsetY = 4;
            }
            default -> {
                return;
            }
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(originX, originY, 0);
            graphics.pose().scale(safeScale, safeScale, 1.0F);
            graphics.blit(texture, offsetX, offsetY, u, v, width, height, 256, 256);
        } finally {
            graphics.pose().popPose();
        }
    }

    public static void drawRecipeCanvas(GuiGraphics graphics, GuiLayoutHelper.Bounds bounds) {
        graphics.fill(bounds.x() - 2, bounds.y() - 2,
                bounds.right() + 2, bounds.bottom() + 2, 0xFF20262B);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFFC7C7C7);
        graphics.fill(bounds.x() + 1, bounds.y() + 1,
                bounds.right() - 1, bounds.bottom() - 1, 0xFFD6D6D6);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, 0xFFF4F4F4);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), 0xFFF4F4F4);
        graphics.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), 0xFF777777);
        graphics.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), 0xFF777777);
    }

    public static void drawManaBar(GuiGraphics graphics, int x, int y, int width,
                                   int value, int maximum) {
        int safeMaximum = Math.max(1, maximum);
        int fill = Math.max(0, Math.min(width - 4, (width - 4) * value / safeMaximum));
        graphics.fill(x, y, x + width, y + 8, 0xFF343434);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 7, 0xFF777777);
        graphics.fill(x + 2, y + 2, x + 2 + fill, y + 6, 0xFF2838D0);
        graphics.fill(x + 2, y + 2, x + 2 + fill, y + 3, 0xFF6C7BFF);
    }

    public static void drawCreateArrow(GuiGraphics graphics, int x, int y) {
        drawScaledTexture(graphics, CREATE_JEI_WIDGETS, x, y,
                19, 10, 42, 10, 1.0F);
    }

    public static void drawCreateDownArrow(GuiGraphics graphics, int x, int y) {
        drawScaledTexture(graphics, CREATE_JEI_WIDGETS, x, y,
                0, 21, 18, 14, 1.0F);
    }

    public static void drawCreateArrow(GuiGraphics graphics, int x, int y, float scale) {
        drawScaledTexture(graphics, CREATE_JEI_WIDGETS, x, y,
                19, 10, 42, 10, scale);
    }

    public static void drawCreateDownArrow(GuiGraphics graphics, int x, int y, float scale) {
        drawScaledTexture(graphics, CREATE_JEI_WIDGETS, x, y,
                0, 21, 18, 14, scale);
    }

    private static void drawScaledTexture(GuiGraphics graphics, ResourceLocation texture,
                                          int x, int y, int u, int v,
                                          int width, int height, float scale) {
        float safeScale = Math.max(0.1F, Math.min(1.0F, scale));
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(safeScale, safeScale, 1.0F);
            graphics.blit(texture, 0, 0, u, v, width, height, 256, 256);
        } finally {
            graphics.pose().popPose();
        }
    }

    public static void drawCreateHeatBar(GuiGraphics graphics, int x, int y, boolean heated) {
        graphics.blit(CREATE_JEI_WIDGETS, x, y, 0, heated ? 201 : 221,
                169, 19, 256, 256);
    }

    public static void drawCenteredItem(GuiGraphics graphics, ItemStack stack, int centerX, int centerY) {
        drawCenteredItem(graphics, stack, centerX, centerY, 1.0F);
    }

    public static void drawCenteredItem(GuiGraphics graphics, ItemStack stack,
                                        int centerX, int centerY, float scale) {
        if (stack == null || stack.isEmpty()) return;
        float safeScale = Math.max(0.25F, Math.min(1.0F, scale));
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(centerX - 8 * safeScale,
                    centerY - 8 * safeScale, 50);
            graphics.pose().scale(safeScale, safeScale, 1.0F);
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    public static void drawScrollbar(GuiGraphics graphics, GuiLayoutHelper.Scrollbar scrollbar,
                                     int mouseX, int mouseY) {
        if (!scrollbar.visible()) {
            return;
        }
        GuiLayoutHelper.Bounds track = scrollbar.track();
        graphics.fill(track.x(), track.y(), track.right(), track.bottom(), SCROLL_TRACK);
        int thumbColor = scrollbar.thumbContains(mouseX, mouseY)
                ? SCROLL_THUMB_HOVER : SCROLL_THUMB;
        graphics.fill(track.x() + 1, scrollbar.thumbY(), track.right() - 1,
                scrollbar.thumbY() + scrollbar.thumbHeight(), thumbColor);
    }

    public static int namespaceColor(String namespace) {
        return switch (namespace) {
            case "minecraft" -> currentTheme.equals("light") ? 0xFF3E8E6B : 0xFF72C99A;
            case "registerhelper" -> DANGER;
            case "avaritia" -> currentTheme.equals("light") ? 0xFF7A63B8 : 0xFFB99AE8;
            default -> currentTheme.equals("light") ? 0xFFB4773C : 0xFFE0AD70;
        };
    }

    public static int categoryColor(String category) {
        return switch (category) {
            case "BLOCKS" -> currentTheme.equals("light") ? 0xFF3E8E6B : 0xFF72C99A;
            case "TOOLS" -> currentTheme.equals("light") ? 0xFFB4773C : 0xFFE0AD70;
            case "COMBAT" -> DANGER;
            case "FOOD" -> currentTheme.equals("light") ? 0xFF9B7A22 : 0xFFE4B267;
            case "MISC" -> TEXT_MUTED;
            case "MODDED" -> currentTheme.equals("light") ? 0xFF7A63B8 : 0xFFB99AE8;
            default -> TEXT;
        };
    }
}
