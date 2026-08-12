package com.wzz.registerhelper.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;

/** Shared visual language for RegisterHelper screens. */
public final class GuiTheme {
    private static final ResourceLocation GENERIC_54_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft", "textures/gui/container/generic_54.png");
    private static final int VANILLA_SLOT_SIZE = 18;
    private static final int VANILLA_SLOT_U = 7;
    private static final int VANILLA_SLOT_V = 17;

    private GuiTheme() {
    }

    private record Palette(
            int backdrop, int panelShadow, int panelEdge, int panel, int panelAlt,
            int header, int headerAccent, int headerAccentWarm, int surface, int surfaceAlt,
            int section, int border, int divider, int text, int textMuted, int textOnHeader,
            int hover, int selected, int selectedEdge, int info, int success, int warning,
            int danger, int dangerSoft, int input, int inputEdge, int scrollTrack,
            int scrollThumb, int scrollThumbHover, int slot, int slotEdge
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

    public static int BACKDROP, PANEL_SHADOW, PANEL_EDGE, PANEL, PANEL_ALT;
    public static int HEADER, HEADER_ACCENT, HEADER_ACCENT_WARM, SURFACE, SURFACE_ALT;
    public static int SECTION, BORDER, DIVIDER, TEXT, TEXT_MUTED, TEXT_ON_HEADER;
    public static int HOVER, SELECTED, SELECTED_EDGE, INFO, SUCCESS, WARNING, DANGER, DANGER_SOFT;
    public static int INPUT, INPUT_EDGE, SCROLL_TRACK, SCROLL_THUMB, SCROLL_THUMB_HOVER;
    public static int SLOT, SLOT_EDGE;

    private static String currentTheme;

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
        if (width == VANILLA_SLOT_SIZE && height == VANILLA_SLOT_SIZE) {
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
        input.setTextColor(TEXT);
        input.setTextColorUneditable(TEXT_MUTED);
    }

    public static void drawInput(GuiGraphics graphics, EditBox input) {
        if (input == null || !input.visible) {
            return;
        }
        int x = input.getX();
        int y = input.getY();
        int right = x + input.getWidth();
        int bottom = y + input.getHeight();
        int edge = input.isFocused() ? SELECTED_EDGE : INPUT_EDGE;
        graphics.fill(x - 1, y - 1, right + 1, bottom + 1, edge);
        graphics.fill(x, y, right, bottom, INPUT);
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
