package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Recipe type selector with categories, search, responsive bounds and drag scrolling. */
@OnlyIn(Dist.CLIENT)
public class RecipeTypeSelectorScreen extends Screen {
    private final Screen parentScreen;
    private final Consumer<RecipeTypeDefinition> selectionCallback;
    private final List<RecipeTypeDefinition> allRecipeTypes;
    private final RecipeTypeDefinition currentSelection;
    private EditBox searchBox;
    private Button cancelButton;
    private Button categoryAllButton;
    private Button categoryCraftingButton;
    private Button categoryCookingButton;
    private Button categoryModsButton;
    private final PinyinSearchHelper<RecipeTypeDefinition> searchHelper;
    private List<RecipeTypeDefinition> filteredRecipeTypes;
    private String currentCategory = "all";
    private int scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private static final int ITEM_HEIGHT = 18;
    private static final int PREFERRED_WIDTH = 520;
    private static final int PREFERRED_HEIGHT = 460;
    private static final int MIN_WIDTH = 280;
    private static final int MIN_HEIGHT = 220;
    private int leftPos, topPos, contentWidth, contentHeight;
    private GuiLayoutHelper.Bounds listBounds;
    private boolean initialized;

    public RecipeTypeSelectorScreen(Screen parentScreen,
                                    Consumer<RecipeTypeDefinition> selectionCallback,
                                    List<RecipeTypeDefinition> recipeTypes,
                                    RecipeTypeDefinition currentSelection) {
        super(GuiText.component("registerhelper.gui.recipe_type_selector.title"));
        this.parentScreen = parentScreen;
        this.selectionCallback = selectionCallback;
        this.allRecipeTypes = new ArrayList<>(recipeTypes);
        this.currentSelection = currentSelection;
        this.filteredRecipeTypes = new ArrayList<>(recipeTypes);
        searchHelper = new PinyinSearchHelper<>(RecipeTypeDefinition::getDisplayName,
                type -> type.getModId() + ":" + type.getId());
        searchHelper.buildCache(allRecipeTypes);
    }

    @Override
    protected void init() {
        String currentSearch = searchBox != null ? searchBox.getValue() : "";
        int previousScroll = scrollOffset;
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(width, height,
                PREFERRED_WIDTH, PREFERRED_HEIGHT, MIN_WIDTH, MIN_HEIGHT, 8, 8);
        leftPos = panel.x(); topPos = panel.y();
        contentWidth = panel.width(); contentHeight = panel.height();
        listBounds = new GuiLayoutHelper.Bounds(leftPos + 20, topPos + 90,
                contentWidth - 40, Math.max(ITEM_HEIGHT, contentHeight - 130));
        searchBox = new EditBox(font, leftPos + 20, topPos + 30,
                contentWidth - 40, 20, GuiText.component("registerhelper.gui.common.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setHint(GuiText.component("registerhelper.gui.recipe_type_selector.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::updateFilteredList);
        addRenderableWidget(searchBox);
        initializeCategoryButtons();
        initializeActionButtons();
        updateFilteredList(currentSearch);
        if (initialized) {
            scrollOffset = GuiLayoutHelper.clamp(previousScroll, 0,
                    Math.max(0, filteredRecipeTypes.size() - currentVisibleItems()));
        }
        initialized = true;
    }

    private void initializeCategoryButtons() {
        int buttonY = topPos + 60;
        int spacing = 5;
        int buttonWidth = Math.max(1, (contentWidth - 40 - spacing * 3) / 4);
        int x = leftPos + 20;
        categoryAllButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.recipe_type_selector.category.all"),
                        b -> selectCategory("all"))
                .bounds(x, buttonY, buttonWidth, 20).build()); x += buttonWidth + spacing;
        categoryCraftingButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.recipe_type_selector.category.crafting"),
                        b -> selectCategory("crafting"))
                .bounds(x, buttonY, buttonWidth, 20).build()); x += buttonWidth + spacing;
        categoryCookingButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.recipe_type_selector.category.cooking"),
                        b -> selectCategory("cooking"))
                .bounds(x, buttonY, buttonWidth, 20).build()); x += buttonWidth + spacing;
        categoryModsButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.recipe_type_selector.category.mods"),
                        b -> selectCategory("mods"))
                .bounds(x, buttonY, buttonWidth, 20).build());
        updateCategoryButtonStates();
    }

    private void initializeActionButtons() {
        int buttonWidth = Math.max(45,
                font.width(GuiText.string("registerhelper.gui.common.cancel")) + 14);
        cancelButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.cancel"), b -> onClose())
                .bounds(leftPos + contentWidth - buttonWidth - 10,
                        topPos + contentHeight - 30, buttonWidth, 20).build());
    }

    private void selectCategory(String category) {
        currentCategory = category;
        scrollOffset = 0;
        updateCategoryButtonStates();
        updateFilteredList(searchBox == null ? "" : searchBox.getValue());
    }

    private void updateCategoryButtonStates() {
        categoryAllButton.setMessage(categoryComponent("all"));
        categoryCraftingButton.setMessage(categoryComponent("crafting"));
        categoryCookingButton.setMessage(categoryComponent("cooking"));
        categoryModsButton.setMessage(categoryComponent("mods"));
    }

    private Component categoryComponent(String category) {
        String key = "registerhelper.gui.recipe_type_selector.category." + category;
        return GuiText.component(key).withStyle(currentCategory.equals(category)
                ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.WHITE);
    }

    private void updateFilteredList(String searchText) {
        scrollOffset = 0;
        List<RecipeTypeDefinition> searchResults = searchHelper.filter(allRecipeTypes, searchText);
        filteredRecipeTypes.clear();
        for (RecipeTypeDefinition type : searchResults) if (matchesCategory(type)) filteredRecipeTypes.add(type);
        filteredRecipeTypes.sort(Comparator.comparing(RecipeTypeDefinition::getDisplayName));
    }

    private boolean matchesCategory(RecipeTypeDefinition type) {
        return switch (currentCategory) {
            case "crafting" -> type.getModId().equals("minecraft");
            case "cooking" -> type.supportsCookingSettings()
                    || "cooking".equals(type.getProperty("category", String.class));
            case "mods" -> !type.getModId().equals("minecraft");
            default -> true;
        };
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.drawBackdrop(g, width, height);
        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(leftPos, topPos,
                contentWidth, contentHeight);
        GuiTheme.drawPanel(g, panel, 28, GuiTheme.HEADER_ACCENT);
        g.drawCenteredString(this.font, this.title, leftPos + contentWidth / 2,
                topPos + 10, GuiTheme.TEXT_ON_HEADER);
        GuiTheme.drawSurface(g, listBounds, false);
        renderRecipeTypeList(g, mouseX, mouseY);
        if (filteredRecipeTypes.size() > currentVisibleItems()) renderScrollbar(g, mouseX, mouseY);
        GuiTheme.drawInput(g, searchBox);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltips(g, mouseX, mouseY);
    }

    private void renderRecipeTypeList(GuiGraphics g, int mouseX, int mouseY) {
        int itemRight = listBounds.right() - 14;
        g.enableScissor(listBounds.x() + 1, listBounds.y() + 1,
                listBounds.right() - 1, listBounds.bottom() - 1);
        for (int i = 0; i < currentVisibleItems(); i++) {
            int index = scrollOffset + i;
            if (index >= filteredRecipeTypes.size()) break;
            RecipeTypeDefinition type = filteredRecipeTypes.get(index);
            int rowY = listBounds.y() + 2 + i * ITEM_HEIGHT;
            int rowX = listBounds.x() + 5;
            boolean hovered = mouseX >= rowX && mouseX <= itemRight
                    && mouseY >= rowY && mouseY < rowY + ITEM_HEIGHT;
            boolean selected = type.equals(currentSelection);
            GuiTheme.drawRow(g, rowX, rowY, itemRight - rowX, ITEM_HEIGHT, i, hovered, selected);
            String modText = "[" + type.getModId() + "]";
            int modWidth = font.width(modText);
            String displayText = GuiLayoutHelper.ellipsis(font, type.getDisplayName(),
                    Math.max(1, itemRight - rowX - modWidth - 16));
            g.drawString(font, displayText, rowX + 5, rowY + 5, GuiTheme.TEXT, false);
            g.drawString(font, modText, itemRight - 5 - modWidth, rowY + 5,
                    selected ? GuiTheme.SELECTED_EDGE : GuiTheme.TEXT_MUTED, false);
        }
        g.disableScissor();
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        GuiTheme.drawScrollbar(g, currentScrollbar(), mouseX, mouseY);
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX < listBounds.x() + 5 || mouseX > listBounds.right() - 14
                || mouseY < listBounds.y() + 1 || mouseY >= listBounds.bottom() - 1) return;
        int row = (mouseY - (listBounds.y() + 2)) / ITEM_HEIGHT;
        int index = scrollOffset + row;
        if (row < 0 || index >= filteredRecipeTypes.size()) return;
        RecipeTypeDefinition type = filteredRecipeTypes.get(index);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(type.getDisplayName()).withStyle(net.minecraft.ChatFormatting.GOLD));
        tooltip.add(GuiText.component("registerhelper.tooltip.recipe_type.id", type.getId()));
        tooltip.add(GuiText.component("registerhelper.tooltip.recipe_type.mod", type.getModId()));
        tooltip.add(GuiText.component("registerhelper.tooltip.recipe_type.grid",
                type.getMaxGridWidth(), type.getMaxGridHeight()));
        if (type.supportsFillMode()) tooltip.add(GuiText.component("registerhelper.tooltip.recipe_type.supports_fill"));
        if (type.supportsCookingSettings()) tooltip.add(GuiText.component("registerhelper.tooltip.recipe_type.supports_cooking"));
        PinyinSearchHelper.PinyinInfo info = searchHelper.getPinyinInfo(type);
        if (info != null && !info.fullPinyin.trim().isEmpty()
                && PinyinSearchHelper.containsChinese(type.getDisplayName())) {
            tooltip.add(GuiText.component("registerhelper.tooltip.search.pinyin", info.fullPinyin));
            tooltip.add(GuiText.component("registerhelper.tooltip.search.initials", info.initials));
        }
        g.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private GuiLayoutHelper.Scrollbar currentScrollbar() {
        return GuiLayoutHelper.scrollbar(new GuiLayoutHelper.Bounds(
                        listBounds.right() - 11, listBounds.y() + 2, 10,
                        Math.max(1, listBounds.height() - 4)),
                filteredRecipeTypes.size(), currentVisibleItems(), scrollOffset, 20);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            GuiLayoutHelper.Scrollbar scrollbar = currentScrollbar();
            if (scrollbar.contains(mouseX, mouseY)) {
                draggingScrollbar = true;
                scrollbarGrabOffset = scrollbar.grabOffset(mouseY);
                scrollOffset = scrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
                return true;
            }
        }
        if (mouseX >= listBounds.x() + 5 && mouseX <= listBounds.right() - 14
                && mouseY >= listBounds.y() + 1 && mouseY < listBounds.bottom() - 1) {
            int row = (int) (mouseY - (listBounds.y() + 2)) / ITEM_HEIGHT;
            int index = scrollOffset + row;
            if (row >= 0 && index < filteredRecipeTypes.size()) {
                selectionCallback.accept(filteredRecipeTypes.get(index));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            scrollOffset = currentScrollbar().offsetForPointer(mouseY, scrollbarGrabOffset);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visible = currentVisibleItems();
        if (filteredRecipeTypes.size() > visible) {
            scrollOffset = GuiLayoutHelper.clamp(scrollOffset - (int) scrollY, 0,
                    filteredRecipeTypes.size() - visible);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int currentVisibleItems() {
        return Math.max(1, (listBounds.height() - 2) / ITEM_HEIGHT);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
