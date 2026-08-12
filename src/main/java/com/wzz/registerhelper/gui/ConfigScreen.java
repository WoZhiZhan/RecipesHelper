package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.init.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Mod configuration screen. */
@OnlyIn(Dist.CLIENT)
public class ConfigScreen extends Screen {
    private final Screen parent;

    private boolean perSlotNBT;
    private boolean defaultIncludeNBT;
    private boolean debugLogging;
    private String guiTheme;
    private String savedTheme;

    private final List<Button> toggleButtons = new ArrayList<>();

    private static final String[] LABELS = {
            "registerhelper.gui.config.per_slot_nbt",
            "registerhelper.gui.config.default_include_nbt",
            "registerhelper.gui.config.debug_logging",
            "registerhelper.gui.config.theme"
    };
    private static final String[] DESCS = {
            "registerhelper.gui.config.per_slot_nbt.desc",
            "registerhelper.gui.config.default_include_nbt.desc",
            "registerhelper.gui.config.debug_logging.desc",
            "registerhelper.gui.config.theme.desc"
    };

    private static final int PREFERRED_PANEL_WIDTH = 380;
    private static final int MIN_PANEL_WIDTH = 240;
    private static final int PREFERRED_ROW_HEIGHT = 32;
    private static final int TITLE_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 40;
    private static final int ROWS = LABELS.length;

    private GuiLayoutHelper.Bounds panelBounds;
    private int rowHeight;
    private boolean showDescriptions;
    private boolean initialized;

    public ConfigScreen(Screen parent) {
        super(GuiText.component("registerhelper.gui.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (!initialized) {
            perSlotNBT = ModConfig.isPerSlotNBTEnabled();
            defaultIncludeNBT = ModConfig.getDefaultIncludeNBT();
            debugLogging = ModConfig.isDebugLoggingEnabled();
            guiTheme = ModConfig.getGuiTheme();
            savedTheme = guiTheme;
            initialized = true;
        }
        GuiTheme.applyTheme(guiTheme);
        toggleButtons.clear();

        int longestDescription = 0;
        for (String description : DESCS) {
            longestDescription = Math.max(longestDescription, this.font.width(GuiText.string(description)));
        }
        int preferredWidth = Math.min(480, Math.max(PREFERRED_PANEL_WIDTH, longestDescription + 120));
        int preferredHeight = TITLE_HEIGHT + PREFERRED_ROW_HEIGHT * ROWS + FOOTER_HEIGHT;
        panelBounds = GuiLayoutHelper.centered(this.width, this.height,
                preferredWidth, preferredHeight, MIN_PANEL_WIDTH,
                TITLE_HEIGHT + ROWS * 22 + FOOTER_HEIGHT, 8, 8);
        rowHeight = Math.max(22,
                (panelBounds.height() - TITLE_HEIGHT - FOOTER_HEIGHT) / ROWS);
        showDescriptions = rowHeight >= 28 && panelBounds.width() >= 280;

        int rowY = panelBounds.y() + TITLE_HEIGHT;
        int toggleWidth = Math.min(96, Math.max(64, panelBounds.width() / 4));
        for (int i = 0; i < ROWS; i++) {
            final int idx = i;
            Button button;
            if (idx == 3) {
                button = addRenderableWidget(Button.builder(
                                GuiText.component(GuiTheme.themeLabelKey(guiTheme)), b -> {
                                    guiTheme = GuiTheme.nextTheme(guiTheme);
                                    GuiTheme.applyTheme(guiTheme);
                                    b.setMessage(GuiText.component(GuiTheme.themeLabelKey(guiTheme)));
                                })
                        .bounds(panelBounds.right() - toggleWidth - 10,
                                rowY + (rowHeight - 18) / 2, toggleWidth, 18).build());
            } else {
                button = addRenderableWidget(Button.builder(
                                GuiText.component(toggleLabelKey(getVal(idx))), b -> {
                                    setVal(idx, !getVal(idx));
                                    b.setMessage(GuiText.component(toggleLabelKey(getVal(idx))));
                                })
                        .bounds(panelBounds.right() - toggleWidth - 10,
                                rowY + (rowHeight - 18) / 2, toggleWidth, 18).build());
            }
            toggleButtons.add(button);
            rowY += rowHeight;
        }

        int footerY = panelBounds.bottom() - FOOTER_HEIGHT;
        int footY = footerY + (FOOTER_HEIGHT - 20) / 2;
        int buttonGap = 8;
        int buttonWidth = Math.min(96, Math.max(64,
                (panelBounds.width() - 30 - buttonGap) / 2));
        int buttonStartX = panelBounds.centerX() - (buttonWidth * 2 + buttonGap) / 2;
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.save"),
                        b -> saveAndClose())
                .bounds(buttonStartX, footY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.cancel"),
                        b -> onClose())
                .bounds(buttonStartX + buttonWidth + buttonGap, footY, buttonWidth, 20).build());
    }

    private boolean getVal(int idx) {
        return switch (idx) {
            case 0 -> perSlotNBT;
            case 1 -> defaultIncludeNBT;
            case 2 -> debugLogging;
            default -> false;
        };
    }

    private void setVal(int idx, boolean value) {
        switch (idx) {
            case 0 -> perSlotNBT = value;
            case 1 -> defaultIncludeNBT = value;
            case 2 -> debugLogging = value;
        }
    }

    private static String toggleLabelKey(boolean on) {
        return on ? "registerhelper.gui.config.enabled" : "registerhelper.gui.config.disabled";
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.drawBackdrop(g, this.width, this.height);

        int cx = panelBounds.x();
        int cy = panelBounds.y();
        int panelWidth = panelBounds.width();
        GuiTheme.drawPanel(g, panelBounds, TITLE_HEIGHT, GuiTheme.HEADER_ACCENT);
        g.drawCenteredString(this.font, GuiText.component("registerhelper.gui.config.header"),
                panelBounds.centerX(), cy + (TITLE_HEIGHT - 8) / 2, GuiTheme.TEXT_ON_HEADER);

        int rowY = cy + TITLE_HEIGHT;
        int textWidth = Math.max(1, panelWidth - Math.min(96, Math.max(64, panelWidth / 4)) - 30);
        for (int i = 0; i < ROWS; i++) {
            int background = (i & 1) == 0 ? GuiTheme.SURFACE : GuiTheme.SURFACE_ALT;
            g.fill(cx, rowY, cx + panelWidth, rowY + rowHeight, background);
            g.fill(cx, rowY + rowHeight - 1, cx + panelWidth, rowY + rowHeight, GuiTheme.DIVIDER);
            int barColor = i == 3 ? GuiTheme.INFO
                    : getVal(i) ? GuiTheme.SUCCESS : GuiTheme.DANGER;
            g.fill(cx, rowY, cx + 3, rowY + rowHeight - 1, barColor);

            String label = GuiLayoutHelper.ellipsis(this.font, GuiText.string(LABELS[i]), textWidth);
            g.drawString(this.font, label, cx + 10,
                    rowY + (showDescriptions ? 4 : (rowHeight - 8) / 2), GuiTheme.TEXT, false);
            if (showDescriptions) {
                String description = GuiLayoutHelper.ellipsis(this.font,
                        GuiText.string(DESCS[i]), textWidth);
                g.drawString(this.font, description, cx + 10, rowY + 16,
                        GuiTheme.TEXT_MUTED, false);
            }
            rowY += rowHeight;
        }

        int footerY = panelBounds.bottom() - FOOTER_HEIGHT;
        g.fill(cx, footerY, cx + panelWidth, panelBounds.bottom(), GuiTheme.PANEL_ALT);
        g.fill(cx, footerY, cx + panelWidth, footerY + 1, GuiTheme.DIVIDER);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void saveAndClose() {
        ModConfig.COMMON.enablePerSlotNBT.set(perSlotNBT);
        ModConfig.COMMON.defaultIncludeNBT.set(defaultIncludeNBT);
        ModConfig.COMMON.enableDebugLogging.set(debugLogging);
        ModConfig.COMMON.guiTheme.set(GuiTheme.normalizeTheme(guiTheme));
        GuiTheme.applyTheme(guiTheme);
        savedTheme = guiTheme;
        ModConfig.COMMON_SPEC.save();
        onClose();
    }

    @Override
    public void onClose() {
        if (savedTheme != null) {
            GuiTheme.applyTheme(savedTheme);
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
