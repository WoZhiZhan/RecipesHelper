package com.wzz.registerhelper.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/** Popup menu for choosing an ingredient input mode. */
@OnlyIn(Dist.CLIENT)
public class IngredientTypeSelector extends Screen {
    private static final int PREFERRED_BUTTON_HEIGHT = 25;
    private static final int MIN_BUTTON_HEIGHT = 18;
    private static final int PREFERRED_BUTTON_SPACING = 5;
    private static final int TITLE_AREA_HEIGHT = 30;

    private final Screen parentScreen;
    private final Consumer<SelectionType> onSelect;
    private final int slotIndex;

    private int menuX, menuY;
    private int menuWidth, menuHeight;
    private int buttonHeight, buttonSpacing;

    public enum SelectionType {
        ALL_ITEMS("registerhelper.gui.ingredient_type.all_items"),
        INVENTORY("registerhelper.gui.ingredient_type.inventory_nbt"),
        TAG("registerhelper.gui.ingredient_type.tag"),
        CUSTOM_TAG("registerhelper.gui.ingredient_type.custom_tag");

        private final String translationKey;

        SelectionType(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getTranslationKey() {
            return translationKey;
        }
    }

    public IngredientTypeSelector(Screen parentScreen, int slotIndex,
                                  Consumer<SelectionType> onSelect) {
        super(GuiText.component("registerhelper.gui.ingredient_type.title"));
        this.parentScreen = parentScreen;
        this.slotIndex = slotIndex;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        int widestLabel = 0;
        for (SelectionType type : SelectionType.values()) {
            widestLabel = Math.max(widestLabel,
                    this.font.width(GuiText.string(type.getTranslationKey())));
        }
        menuWidth = GuiLayoutHelper.fit(Math.max(180, widestLabel + 28),
                widestLabel + 16, this.width - 16);

        int itemCount = SelectionType.values().length;
        int availableButtonArea = Math.max(itemCount * MIN_BUTTON_HEIGHT,
                this.height - 16 - TITLE_AREA_HEIGHT - 10);
        buttonSpacing = availableButtonArea >= itemCount * PREFERRED_BUTTON_HEIGHT
                + (itemCount - 1) * PREFERRED_BUTTON_SPACING
                ? PREFERRED_BUTTON_SPACING : 2;
        buttonHeight = GuiLayoutHelper.clamp(
                (availableButtonArea - buttonSpacing * (itemCount - 1)) / itemCount,
                MIN_BUTTON_HEIGHT, PREFERRED_BUTTON_HEIGHT);
        menuHeight = TITLE_AREA_HEIGHT + itemCount * buttonHeight
                + (itemCount - 1) * buttonSpacing + 10;
        menuX = (this.width - menuWidth) / 2;
        menuY = (this.height - menuHeight) / 2;

        int buttonY = menuY + TITLE_AREA_HEIGHT;
        for (SelectionType type : SelectionType.values()) {
            SelectionType selectedType = type;
            addRenderableWidget(Button.builder(GuiText.component(type.getTranslationKey()),
                            button -> handleSelection(selectedType))
                    .bounds(menuX + 10, buttonY, menuWidth - 20, buttonHeight).build());
            buttonY += buttonHeight + buttonSpacing;
        }
    }

    private void handleSelection(SelectionType type) {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
        if (onSelect != null) {
            onSelect.accept(type);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.drawBackdrop(g, this.width, this.height);
        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(menuX, menuY, menuWidth, menuHeight);
        GuiTheme.drawPanel(g, panel, 28, GuiTheme.HEADER_ACCENT);
        g.drawCenteredString(this.font, this.title, menuX + menuWidth / 2,
                menuY + 10, GuiTheme.TEXT_ON_HEADER);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
