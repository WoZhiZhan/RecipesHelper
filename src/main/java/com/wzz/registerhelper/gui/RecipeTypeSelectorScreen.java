package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * 配方类型选择器屏幕
 */
@OnlyIn(Dist.CLIENT)
public class RecipeTypeSelectorScreen extends Screen {

    private final Screen parentScreen;
    private final Consumer<RecipeTypeDefinition> selectionCallback;
    private final List<RecipeTypeDefinition> allRecipeTypes;
    private final RecipeTypeDefinition currentSelection;

    // UI组件
    private EditBox searchBox;
    private Button cancelButton;
    private Button categoryAllButton;
    private Button categoryCraftingButton;
    private Button categoryCookingButton;
    private Button categoryModsButton;

    // 使用拼音搜索助手
    private final PinyinSearchHelper<RecipeTypeDefinition> searchHelper;

    // 状态
    private List<RecipeTypeDefinition> filteredRecipeTypes;
    private String currentCategory = "all";
    private int scrollOffset = 0;
    private final int maxVisibleItems = 15;

    // 布局
    private int leftPos, topPos;
    private int contentWidth = 400;
    private int contentHeight = 400;

    public RecipeTypeSelectorScreen(Screen parentScreen, Consumer<RecipeTypeDefinition> selectionCallback,
                                    List<RecipeTypeDefinition> recipeTypes, RecipeTypeDefinition currentSelection) {
        super(Component.literal("选择配方类型"));
        this.parentScreen = parentScreen;
        this.selectionCallback = selectionCallback;
        this.allRecipeTypes = new ArrayList<>(recipeTypes);
        this.currentSelection = currentSelection;
        this.filteredRecipeTypes = new ArrayList<>(recipeTypes);

        // 初始化搜索助手
        this.searchHelper = new PinyinSearchHelper<>(
                RecipeTypeDefinition::getDisplayName,  // 显示名称
                type -> type.getModId() + ":" + type.getId()  // ID（用于mod过滤）
        );

        // 构建拼音缓存
        searchHelper.buildCache(allRecipeTypes);
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - contentWidth) / 2;
        this.topPos = (this.height - contentHeight) / 2;

        initializeSearchBox();
        initializeCategoryButtons();
        initializeActionButtons();

        updateFilteredList("");
    }

    private void initializeSearchBox() {
        searchBox = new EditBox(this.font, leftPos + 20, topPos + 30, contentWidth - 40, 20,
                Component.literal("搜索配方类型"));
        searchBox.setHint(Component.literal("输入配方名称/拼音/mod名称..."));
        searchBox.setResponder(this::updateFilteredList);
        addRenderableWidget(searchBox);
    }

    private void initializeCategoryButtons() {
        int buttonY = topPos + 60;
        int buttonWidth = 70;
        int buttonSpacing = 5;
        int currentX = leftPos + 20;

        categoryAllButton = addRenderableWidget(Button.builder(
                        Component.literal("全部"),
                        button -> selectCategory("all"))
                .bounds(currentX, buttonY, buttonWidth, 20)
                .build());
        currentX += buttonWidth + buttonSpacing;

        categoryCraftingButton = addRenderableWidget(Button.builder(
                        Component.literal("原版合成"),
                        button -> selectCategory("crafting"))
                .bounds(currentX, buttonY, buttonWidth, 20)
                .build());
        currentX += buttonWidth + buttonSpacing;

        categoryCookingButton = addRenderableWidget(Button.builder(
                        Component.literal("烹饪"),
                        button -> selectCategory("cooking"))
                .bounds(currentX, buttonY, buttonWidth, 20)
                .build());
        currentX += buttonWidth + buttonSpacing;

        categoryModsButton = addRenderableWidget(Button.builder(
                        Component.literal("Mods"),
                        button -> selectCategory("mods"))
                .bounds(currentX, buttonY, buttonWidth, 20)
                .build());

        updateCategoryButtonStates();
    }

    private void initializeActionButtons() {
        int buttonY = topPos + contentHeight - 30;

        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(leftPos + contentWidth - 60, buttonY, 50, 20)
                .build());
    }

    private void selectCategory(String category) {
        this.currentCategory = category;
        this.scrollOffset = 0;
        updateCategoryButtonStates();
        updateFilteredList(searchBox != null ? searchBox.getValue() : "");
    }

    private void updateCategoryButtonStates() {
        categoryAllButton.setMessage(Component.literal(currentCategory.equals("all") ? "§6全部" : "全部"));
        categoryCraftingButton.setMessage(Component.literal(currentCategory.equals("crafting") ? "§6原版合成" : "原版合成"));
        categoryCookingButton.setMessage(Component.literal(currentCategory.equals("cooking") ? "§6烹饪" : "烹饪"));
        categoryModsButton.setMessage(Component.literal(currentCategory.equals("mods") ? "§6Mods" : "Mods"));
    }

    private void updateFilteredList(String searchText) {
        this.scrollOffset = 0;

        // 先用搜索助手过滤
        List<RecipeTypeDefinition> searchResults = searchHelper.filter(allRecipeTypes, searchText);

        // 再用分类过滤
        filteredRecipeTypes.clear();
        for (RecipeTypeDefinition type : searchResults) {
            if (matchesCategory(type)) {
                filteredRecipeTypes.add(type);
            }
        }

        // 排序
        filteredRecipeTypes.sort(Comparator.comparing(RecipeTypeDefinition::getDisplayName));
    }

    private boolean matchesCategory(RecipeTypeDefinition type) {
        return switch (currentCategory) {
            case "crafting" -> type.getModId().equals("minecraft");
            case "cooking" -> type.supportsCookingSettings() ||
                    "cooking".equals(type.getProperty("category", String.class));
            case "mods" -> !type.getModId().equals("minecraft");
            default -> true;
        };
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // 主背景
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + contentHeight, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + contentWidth - 1, topPos + contentHeight - 1, 0xFF8B8B8B);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, leftPos + contentWidth / 2, topPos + 10, 0x404040);

        // 列表背景
        int listTop = topPos + 90;
        int listHeight = contentHeight - 130;
        guiGraphics.fill(leftPos + 20, listTop, leftPos + contentWidth - 20, listTop + listHeight, 0xFF000000);
        guiGraphics.fill(leftPos + 21, listTop + 1, leftPos + contentWidth - 21, listTop + listHeight - 1, 0xFFFFFFFF);

        renderRecipeTypeList(guiGraphics, mouseX, mouseY, listTop, listHeight);

        if (filteredRecipeTypes.size() > maxVisibleItems) {
            renderScrollbar(guiGraphics, listTop, listHeight);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltips(guiGraphics, mouseX, mouseY, listTop, listHeight);
    }

    private void renderRecipeTypeList(GuiGraphics guiGraphics, int mouseX, int mouseY, int listTop, int listHeight) {
        int itemHeight = 18;
        guiGraphics.enableScissor(leftPos + 21, listTop + 1, leftPos + contentWidth - 21, listTop + listHeight - 1);

        int visibleItems = Math.min(maxVisibleItems, listHeight / itemHeight);

        for (int i = 0; i < visibleItems; i++) {
            int typeIndex = scrollOffset + i;
            if (typeIndex >= filteredRecipeTypes.size()) break;

            RecipeTypeDefinition type = filteredRecipeTypes.get(typeIndex);
            int itemY = listTop + 2 + i * itemHeight;
            int itemX = leftPos + 25;

            boolean isHovered = mouseX >= itemX && mouseX <= leftPos + contentWidth - 25 &&
                    mouseY >= itemY && mouseY < itemY + itemHeight;
            boolean isSelected = type.equals(currentSelection);

            if (isSelected) {
                guiGraphics.fill(itemX, itemY, leftPos + contentWidth - 25, itemY + itemHeight, 0xFF4A90E2);
            } else if (isHovered) {
                guiGraphics.fill(itemX, itemY, leftPos + contentWidth - 25, itemY + itemHeight, 0xFFE0E0E0);
            }

            String displayText = type.getDisplayName();
            String modText = "[" + type.getModId() + "]";

            int textColor = isSelected ? 0xFFFFFF : 0x000000;
            int modColor = isSelected ? 0xCCCCCC : 0x666666;

            int maxTextWidth = contentWidth - 80;
            if (this.font.width(displayText) > maxTextWidth) {
                displayText = this.font.plainSubstrByWidth(displayText, maxTextWidth - 10) + "...";
            }

            guiGraphics.drawString(this.font, displayText, itemX + 5, itemY + 5, textColor, false);

            int modWidth = this.font.width(modText);
            guiGraphics.drawString(this.font, modText, leftPos + contentWidth - 30 - modWidth, itemY + 5, modColor, false);
        }

        guiGraphics.disableScissor();
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int listTop, int listHeight) {
        int scrollbarX = leftPos + contentWidth - 15;
        int scrollbarHeight = listHeight - 4;

        guiGraphics.fill(scrollbarX, listTop + 2, scrollbarX + 10, listTop + listHeight - 2, 0xFF666666);

        float scrollPercentage = (float) scrollOffset / (filteredRecipeTypes.size() - maxVisibleItems);
        int sliderHeight = Math.max(20, scrollbarHeight * maxVisibleItems / filteredRecipeTypes.size());
        int sliderY = listTop + 2 + (int) ((scrollbarHeight - sliderHeight) * scrollPercentage);

        guiGraphics.fill(scrollbarX + 1, sliderY, scrollbarX + 9, sliderY + sliderHeight, 0xFFCCCCCC);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, int listTop, int listHeight) {
        int itemHeight = 18;

        if (mouseX >= leftPos + 25 && mouseX <= leftPos + contentWidth - 25 &&
                mouseY >= listTop + 1 && mouseY < listTop + listHeight - 1) {

            int relativeY = (int)(mouseY - (listTop + 2));
            int hoveredRow = relativeY / itemHeight;
            int typeIndex = scrollOffset + hoveredRow;

            if (hoveredRow >= 0 && typeIndex < filteredRecipeTypes.size()) {
                RecipeTypeDefinition type = filteredRecipeTypes.get(typeIndex);

                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("§6" + type.getDisplayName()));
                tooltip.add(Component.literal("§7ID: " + type.getId()));
                tooltip.add(Component.literal("§7Mod: " + type.getModId()));
                tooltip.add(Component.literal("§7网格: " + type.getMaxGridWidth() + "×" + type.getMaxGridHeight()));

                if (type.supportsFillMode()) {
                    tooltip.add(Component.literal("§a支持填充模式"));
                }
                if (type.supportsCookingSettings()) {
                    tooltip.add(Component.literal("§e支持烹饪设置"));
                }

                // 使用搜索助手获取拼音信息
                PinyinSearchHelper.PinyinInfo pinyinInfo = searchHelper.getPinyinInfo(type);
                if (pinyinInfo != null && !pinyinInfo.fullPinyin.trim().isEmpty()) {
                    String displayName = type.getDisplayName();
                    if (PinyinSearchHelper.containsChinese(displayName)) {
                        tooltip.add(Component.literal("§8拼音: " + pinyinInfo.fullPinyin));
                        tooltip.add(Component.literal("§8简写: " + pinyinInfo.initials));
                    }
                }

                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = topPos + 90;
        int listHeight = contentHeight - 130;
        int itemHeight = 18;

        if (mouseX >= leftPos + 25 && mouseX <= leftPos + contentWidth - 25 &&
                mouseY >= listTop + 1 && mouseY < listTop + listHeight - 1) {

            int relativeY = (int)(mouseY - (listTop + 2));
            int clickedRow = relativeY / itemHeight;
            int typeIndex = scrollOffset + clickedRow;

            if (clickedRow >= 0 && typeIndex < filteredRecipeTypes.size()) {
                RecipeTypeDefinition selectedType = filteredRecipeTypes.get(typeIndex);
                selectionCallback.accept(selectedType);
                onClose();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (filteredRecipeTypes.size() > maxVisibleItems) {
            int maxScrollOffset = Math.max(0, filteredRecipeTypes.size() - maxVisibleItems);
            scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset - (int) delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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