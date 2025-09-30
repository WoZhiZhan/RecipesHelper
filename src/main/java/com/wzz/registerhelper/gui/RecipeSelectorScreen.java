package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class RecipeSelectorScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MIN_GUI_WIDTH = 650;
    private static final int MIN_GUI_HEIGHT = 350;
    private static final int RECIPE_DETAIL_WIDTH = 250; // 增加详情区域宽度
    private static final int RECIPE_ITEM_HEIGHT = 22;
    private static final int MAX_VISIBLE_RECIPES = 11;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_SPACING = 20;

    private final Screen parentScreen;
    private final Consumer<ResourceLocation> onRecipeSelected;

    // 动态尺寸变量
    private int contentWidth;
    private int contentHeight;
    private int leftPos, topPos;

    private final List<RecipeEntry> allRecipes = new ArrayList<>();
    private List<RecipeEntry> filteredRecipes = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedRecipeIndex = -1;
    private String loadError = null;

    private final List<SlotInfo> currentRecipeSlots = new ArrayList<>();
    private SlotInfo currentResultSlot = null;
    private String currentRecipeTypeDisplay = "";

    private EditBox searchBox;
    private Button selectButton;
    private Button cancelButton;
    private Button scrollUpButton;
    private Button scrollDownButton;
    private Button refreshButton;
    private final Set<ResourceLocation> allowedRecipeIds = new HashSet<>();
    private final boolean useRecipeFilter;

    public RecipeSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onRecipeSelected) {
        super(Component.literal("选择配方"));
        this.minecraft = Minecraft.getInstance();
        this.parentScreen = parentScreen;
        this.onRecipeSelected = onRecipeSelected;
        this.useRecipeFilter = false;
        loadRecipes();
    }

    public RecipeSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onRecipeSelected,
                                List<UnifiedRecipeInfo> allowedRecipes, String title) {
        super(Component.literal(title));
        this.minecraft = Minecraft.getInstance();
        this.parentScreen = parentScreen;
        this.onRecipeSelected = onRecipeSelected;
        this.useRecipeFilter = true;
        for (UnifiedRecipeInfo recipe : allowedRecipes) {
            allowedRecipeIds.add(recipe.getRecipeId());
        }
        loadRecipes();
    }

    private void calculateDynamicSize() {
        // 计算所需的最小尺寸
        this.contentWidth = Math.max(MIN_GUI_WIDTH, this.width - 100);
        this.contentHeight = Math.max(MIN_GUI_HEIGHT, this.height - 100);

        // 确保不超过屏幕尺寸
        this.contentWidth = Math.min(this.contentWidth, this.width - 40);
        this.contentHeight = Math.min(this.contentHeight, this.height - 40);

        // 计算位置
        this.leftPos = (this.width - contentWidth) / 2;
        this.topPos = (this.height - contentHeight) / 2;
    }

    private void loadRecipes() {
        allRecipes.clear();
        loadError = null;
        try {
            if (minecraft == null) {
                loadError = "Minecraft实例为空";
                LOGGER.error("Minecraft instance is null");
                return;
            }
            if (minecraft.level == null) {
                loadError = "游戏世界未加载";
                LOGGER.error("Game level is null");
                return;
            }
            RecipeManager recipeManager = minecraft.level.getRecipeManager();
            if (recipeManager == null) {
                loadError = "配方管理器为空";
                LOGGER.error("Recipe manager is null");
                return;
            }
            Collection<Recipe<?>> recipes = recipeManager.getRecipes();
            if (recipes.isEmpty()) {
                loadError = "未找到任何配方，可能需要等待世界完全加载";
                LOGGER.warn("No recipes found in recipe manager");
                return;
            }
            int validRecipeCount = 0;
            for (Recipe<?> recipe : recipes) {
                try {
                    ResourceLocation id = recipe.getId();
                    if (id == null) {
                        LOGGER.warn("配方ID为空，跳过");
                        continue;
                    }
                    ItemStack resultItem = ItemStack.EMPTY;
                    try {
                        resultItem = recipe.getResultItem(minecraft.level.registryAccess());
                    } catch (Exception e) {
                        LOGGER.warn("获取配方 {} 的结果物品失败: {}", id, e.getMessage());
                        // 即使获取结果物品失败也添加配方，使用空物品栈
                    }
                    String recipeType = classifyRecipeType(recipe);
                    allRecipes.add(new RecipeEntry(id, resultItem, recipeType, recipe));
                    validRecipeCount++;
                } catch (Exception e) {
                    LOGGER.warn("处理配方时出错: {}", e.getMessage());
                }
            }
            if (validRecipeCount == 0) {
                loadError = "没有可用的配方数据";
            }
            if (useRecipeFilter && !allowedRecipeIds.isEmpty()) {
                allRecipes.removeIf(entry -> !allowedRecipeIds.contains(entry.recipeId));
            }
        } catch (Exception e) {
            loadError = "加载配方时出错: " + e.getMessage();
            LOGGER.error("Error loading recipes", e);
        }
        allRecipes.sort(Comparator.comparing(entry -> entry.recipeId.toString()));
        filteredRecipes = new ArrayList<>(allRecipes);
    }

    private String classifyRecipeType(Recipe<?> recipe) {
        try {
            String typeName = recipe.getType().toString().toLowerCase();
            if (typeName.contains("minecraft:crafting_shaped") || typeName.contains("shaped")) {
                return "有形状配方";
            } else if (typeName.contains("minecraft:crafting_shapeless") || typeName.contains("shapeless")) {
                return "无形状配方";
            } else if (typeName.contains("minecraft:smelting") || typeName.contains("smelting")) {
                return "熔炼配方";
            } else if (typeName.contains("minecraft:blasting") || typeName.contains("blasting")) {
                return "高炉配方";
            } else if (typeName.contains("minecraft:smoking") || typeName.contains("smoking")) {
                return "烟熏配方";
            } else if (typeName.contains("minecraft:campfire_cooking") || typeName.contains("campfire")) {
                return "营火烹饪";
            } else if (typeName.contains("avaritia")) {
                if (typeName.contains("shaped")) {
                    return "Avaritia有形状配方";
                } else if (typeName.contains("shapeless")) {
                    return "Avaritia无形状配方";
                } else {
                    return "Avaritia配方";
                }
            } else if (typeName.contains("crafting")) {
                return "工作台配方";
            } else {
                return typeName;
            }
        } catch (Exception e) {
            LOGGER.warn("分类配方类型失败: {}", e.getMessage());
            return "未知类型";
        }
    }

    @Override
    protected void init() {
        calculateDynamicSize();

        // 搜索框位置调整，为右侧列表区域
        int listAreaX = leftPos + RECIPE_DETAIL_WIDTH + 20;
        int listAreaWidth = contentWidth - RECIPE_DETAIL_WIDTH - 40;

        searchBox = new EditBox(this.font, listAreaX, topPos + 45, listAreaWidth, 20,
                Component.literal("搜索配方"));
        searchBox.setHint(Component.literal("输入配方ID或物品名称进行搜索..."));
        searchBox.setResponder(this::onSearchTextChanged);
        addRenderableWidget(searchBox);

        selectButton = addRenderableWidget(Button.builder(
                        Component.literal("选择配方"),
                        button -> selectRecipe())
                .bounds(listAreaX, topPos + 75, 80, 20)
                .build());
        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(listAreaX + 90, topPos + 75, 60, 20)
                .build());
        refreshButton = addRenderableWidget(Button.builder(
                        Component.literal("刷新"),
                        button -> refreshRecipes())
                .bounds(listAreaX + 160, topPos + 75, 50, 20)
                .build());
        scrollUpButton = addRenderableWidget(Button.builder(
                        Component.literal("▲"),
                        button -> scrollUp())
                .bounds(leftPos + contentWidth - 40, topPos + 105, 30, 20)
                .build());

        scrollDownButton = addRenderableWidget(Button.builder(
                        Component.literal("▼"),
                        button -> scrollDown())
                .bounds(leftPos + contentWidth - 40, topPos + contentHeight - 50, 30, 20)
                .build());
        updateButtons();
    }

    private void refreshRecipes() {
        loadRecipes();
        scrollOffset = 0;
        selectedRecipeIndex = -1;
        if (searchBox != null && !searchBox.getValue().isEmpty()) {
            onSearchTextChanged(searchBox.getValue());
        }
        updateButtons();
    }

    private void onSearchTextChanged(String searchText) {
        if (searchText.isEmpty()) {
            filteredRecipes = new ArrayList<>(allRecipes);
        } else {
            String lowerSearch = searchText.toLowerCase();
            filteredRecipes = allRecipes.stream()
                    .filter(entry -> {
                        if (entry.recipeId.toString().toLowerCase().contains(lowerSearch)) {
                            return true;
                        }
                        try {
                            if (!entry.resultItem.isEmpty()) {
                                String itemName = entry.resultItem.getHoverName().getString().toLowerCase();
                                if (itemName.contains(lowerSearch)) {
                                    return true;
                                }
                            }
                        } catch (Exception e) {
                        }
                        return entry.recipeType.toLowerCase().contains(lowerSearch);
                    })
                    .collect(Collectors.toList());
        }

        scrollOffset = 0;
        selectedRecipeIndex = -1;
        updateButtons();
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            updateButtons();
        }
    }

    private void scrollDown() {
        int maxScroll = Math.max(0, filteredRecipes.size() - MAX_VISIBLE_RECIPES);
        if (scrollOffset < maxScroll) {
            scrollOffset++;
            updateButtons();
        }
    }

    private void updateButtons() {
        if (scrollUpButton != null) {
            scrollUpButton.active = scrollOffset > 0;
        }
        if (scrollDownButton != null) {
            scrollDownButton.active = scrollOffset < Math.max(0, filteredRecipes.size() - MAX_VISIBLE_RECIPES);
        }
        if (selectButton != null) {
            selectButton.active = selectedRecipeIndex >= 0;
        }
    }

    private void selectRecipe() {
        if (selectedRecipeIndex >= 0 && selectedRecipeIndex < filteredRecipes.size()) {
            RecipeEntry selected = filteredRecipes.get(selectedRecipeIndex);
            onRecipeSelected.accept(selected.recipeId);
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parentScreen);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // 绘制主界面背景
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + contentHeight, 0xFF404040);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + contentWidth - 1, topPos + contentHeight - 1, 0xFF606060);

        // 绘制标题
        guiGraphics.drawCenteredString(this.font, this.title, leftPos + contentWidth / 2, topPos + 15, 0xFFFFFF);

        // 渲染配方详情区域
        renderRecipeDetail(guiGraphics);

        // 分割线
        guiGraphics.fill(leftPos + RECIPE_DETAIL_WIDTH + 5, topPos + 30, leftPos + RECIPE_DETAIL_WIDTH + 7, topPos + contentHeight - 10, 0xFF808080);

        // 右侧列表区域
        int listAreaX = leftPos + RECIPE_DETAIL_WIDTH + 20;

        if (loadError != null) {
            guiGraphics.drawCenteredString(this.font, "§c错误: " + loadError,
                    listAreaX + (contentWidth - RECIPE_DETAIL_WIDTH - 40) / 2, topPos + 30, 0xFFAAAA);
            guiGraphics.drawCenteredString(this.font, "§e点击刷新按钮重试",
                    listAreaX + (contentWidth - RECIPE_DETAIL_WIDTH - 40) / 2, topPos + 105, 0xFFCC66);
        } else {
            String countText = String.format("显示 %d/%d 个配方", filteredRecipes.size(), allRecipes.size());
            guiGraphics.drawString(this.font, countText, listAreaX, topPos + 30, 0xCCCCCC, false);
        }

        int listTop = topPos + 105;
        int listBottom = listTop + MAX_VISIBLE_RECIPES * RECIPE_ITEM_HEIGHT;
        int listRight = leftPos + contentWidth - 50;

        // 配方列表背景
        guiGraphics.fill(listAreaX - 10, listTop, listRight, listBottom, 0xFF000000);
        guiGraphics.fill(listAreaX - 9, listTop + 1, listRight - 1, listBottom - 1, 0xFF808080);

        if (loadError == null) {
            renderRecipeList(guiGraphics, mouseX, mouseY, listTop, listAreaX, listRight);
        } else {
            guiGraphics.drawCenteredString(this.font, "无法加载配方列表",
                    listAreaX + (listRight - listAreaX) / 2, listTop + 50, 0xCCCCCC);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderRecipeDetail(GuiGraphics guiGraphics) {
        int detailX = leftPos + 10;
        int detailY = topPos + 30;
        int detailWidth = RECIPE_DETAIL_WIDTH - 10;
        int detailHeight = contentHeight - 40;

        guiGraphics.fill(detailX, detailY, detailX + detailWidth, detailY + detailHeight, 0xFF000000);
        guiGraphics.fill(detailX + 1, detailY + 1, detailX + detailWidth - 1, detailY + detailHeight - 1, 0xFF505050);

        guiGraphics.drawCenteredString(this.font, "配方预览", detailX + detailWidth / 2, detailY + 10, 0xFFFFFF);

        if (selectedRecipeIndex >= 0 && selectedRecipeIndex < filteredRecipes.size()) {
            RecipeEntry selected = filteredRecipes.get(selectedRecipeIndex);

            int typeColor = getTypeColor(currentRecipeTypeDisplay);
            guiGraphics.drawCenteredString(this.font, currentRecipeTypeDisplay,
                    detailX + detailWidth / 2, detailY + 25, typeColor);

            if (currentResultSlot != null) {
                guiGraphics.drawString(this.font, "输出:", detailX + 10, detailY + 45, 0xCCCCCC, false);
                renderRecipeSlot(guiGraphics, currentResultSlot, true);
            }

            if (!currentRecipeSlots.isEmpty()) {
                guiGraphics.drawString(this.font, "材料:", detailX + 10, detailY + 85, 0xCCCCCC, false);

                for (SlotInfo slot : currentRecipeSlots) {
                    renderRecipeSlot(guiGraphics, slot, false);
                }

                if (currentRecipeTypeDisplay.contains("熔炼") || currentRecipeTypeDisplay.contains("高炉") ||
                        currentRecipeTypeDisplay.contains("烟熏") || currentRecipeTypeDisplay.contains("营火")) {
                    if (currentResultSlot != null && !currentRecipeSlots.isEmpty()) {
                        SlotInfo inputSlot = currentRecipeSlots.get(0);
                        int arrowStartX = inputSlot.x + SLOT_SIZE + 5;
                        int arrowEndX = currentResultSlot.x - 5;
                        int arrowY = inputSlot.y + SLOT_SIZE / 2;

                        guiGraphics.drawString(this.font, "→", arrowStartX + 5, arrowY - 4, 0xFFFFFF, false);
                    }
                }
            }

            String shortId = selected.recipeId.toString();
            if (shortId.length() > 30) {
                shortId = shortId.substring(0, 27) + "...";
            }
            guiGraphics.drawString(this.font, "ID:", detailX + 10, detailY + detailHeight - 40, 0xCCCCCC, false);

            // 分行显示长ID
            int maxLineWidth = detailWidth - 20;
            String[] idLines = wrapText(shortId, maxLineWidth);
            for (int i = 0; i < Math.min(idLines.length, 2); i++) {
                guiGraphics.drawString(this.font, idLines[i], detailX + 10, detailY + detailHeight - 30 + i * 10, 0xFFFFFF, false);
            }

        } else {
            guiGraphics.drawCenteredString(this.font, "选择配方以查看详情",
                    detailX + detailWidth / 2, detailY + detailHeight / 2, 0xFFAAAA);
        }
    }

    private String[] wrapText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return new String[]{text};
        }

        List<String> lines = new ArrayList<>();
        String remaining = text;

        while (!remaining.isEmpty() && this.font.width(remaining) > maxWidth) {
            int breakPoint = remaining.length();
            while (breakPoint > 0 && this.font.width(remaining.substring(0, breakPoint)) > maxWidth) {
                breakPoint--;
            }
            if (breakPoint == 0) breakPoint = 1; // 防止无限循环

            lines.add(remaining.substring(0, breakPoint));
            remaining = remaining.substring(breakPoint);
        }

        if (!remaining.isEmpty()) {
            lines.add(remaining);
        }

        return lines.toArray(new String[0]);
    }

    private void renderRecipeSlot(GuiGraphics guiGraphics, SlotInfo slot, boolean isResultSlot) {
        int bgColor = isResultSlot ? 0xFF4A4A4A : 0xFF373737;
        guiGraphics.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, bgColor);

        int borderColor = isResultSlot ? 0xFF7777FF : 0xFF888888;
        guiGraphics.fill(slot.x - 1, slot.y - 1, slot.x + SLOT_SIZE + 1, slot.y, borderColor);
        guiGraphics.fill(slot.x - 1, slot.y + SLOT_SIZE, slot.x + SLOT_SIZE + 1, slot.y + SLOT_SIZE + 1, borderColor);
        guiGraphics.fill(slot.x - 1, slot.y, slot.x, slot.y + SLOT_SIZE, borderColor);
        guiGraphics.fill(slot.x + SLOT_SIZE, slot.y, slot.x + SLOT_SIZE + 1, slot.y + SLOT_SIZE, borderColor);

        if (!slot.item.isEmpty()) {
            try {
                RenderSystem.enableDepthTest();
                guiGraphics.renderItem(slot.item, slot.x + 1, slot.y + 1);

                if (slot.item.getCount() > 1) {
                    String countText = String.valueOf(slot.item.getCount());
                    guiGraphics.drawString(this.font, countText,
                            slot.x + SLOT_SIZE - this.font.width(countText),
                            slot.y + SLOT_SIZE - 8, 0xFFFFFF, true);
                }

                RenderSystem.disableDepthTest();
            } catch (Exception e) {
                guiGraphics.fill(slot.x + 1, slot.y + 1, slot.x + SLOT_SIZE - 1, slot.y + SLOT_SIZE - 1, 0xFFAA3333);
            }
        }
    }

    private void renderRecipeList(GuiGraphics guiGraphics, int mouseX, int mouseY, int listTop, int listAreaX, int listRight) {
        if (filteredRecipes.isEmpty()) {
            String emptyMessage = allRecipes.isEmpty() ? "没有找到任何配方" : "没有匹配的配方";
            guiGraphics.drawCenteredString(this.font, emptyMessage,
                    listAreaX + (listRight - listAreaX) / 2, listTop + 50, 0xCCCCCC);
            return;
        }

        for (int i = 0; i < MAX_VISIBLE_RECIPES && i + scrollOffset < filteredRecipes.size(); i++) {
            int recipeIndex = i + scrollOffset;
            RecipeEntry recipe = filteredRecipes.get(recipeIndex);

            int itemY = listTop + i * RECIPE_ITEM_HEIGHT;
            int itemX = listAreaX - 5;

            boolean isHovered = mouseX >= itemX && mouseX < listRight &&
                    mouseY >= itemY && mouseY < itemY + RECIPE_ITEM_HEIGHT;
            boolean isSelected = recipeIndex == selectedRecipeIndex;

            if (isSelected) {
                guiGraphics.fill(itemX, itemY, listRight, itemY + RECIPE_ITEM_HEIGHT, 0xFF4488CC);
            } else if (isHovered) {
                guiGraphics.fill(itemX, itemY, listRight, itemY + RECIPE_ITEM_HEIGHT, 0xFF666699);
            }

            try {
                if (!recipe.resultItem.isEmpty()) {
                    RenderSystem.enableDepthTest();
                    guiGraphics.renderItem(recipe.resultItem, itemX + 2, itemY + 1);
                    RenderSystem.disableDepthTest();
                } else {
                    guiGraphics.fill(itemX + 2, itemY + 1, itemX + 18, itemY + 17, 0xFF333333);
                }
            } catch (Exception e) {
                guiGraphics.fill(itemX + 2, itemY + 1, itemX + 18, itemY + 17, 0xFFAA3333);
            }

            String displayText = recipe.recipeId.toString();
            int maxTextWidth = listRight - itemX - 80;
            if (this.font.width(displayText) > maxTextWidth) {
                while (this.font.width(displayText + "...") > maxTextWidth && displayText.length() > 0) {
                    displayText = displayText.substring(0, displayText.length() - 1);
                }
                displayText += "...";
            }

            int textColor = isSelected ? 0xFFFFFF : 0xFFFFFF;
            guiGraphics.drawString(this.font, displayText, itemX + 22, itemY + 2, textColor, false);

            String typeText = "[" + recipe.recipeType + "]";
            int typeColor = getTypeColor(recipe.recipeType);
            if (isSelected) typeColor = 0xFFFFFF;
            guiGraphics.drawString(this.font, typeText, itemX + 22, itemY + 12, typeColor, false);
        }

        if (filteredRecipes.size() > MAX_VISIBLE_RECIPES) {
            int scrollBarX = leftPos + contentWidth - 45;
            int scrollBarTop = listTop + 5;
            int scrollBarHeight = MAX_VISIBLE_RECIPES * RECIPE_ITEM_HEIGHT - 10;
            guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 3, scrollBarTop + scrollBarHeight, 0xFF333333);
            int thumbHeight = Math.max(10, scrollBarHeight * MAX_VISIBLE_RECIPES / filteredRecipes.size());
            int thumbY = scrollBarTop + (scrollBarHeight - thumbHeight) * scrollOffset /
                    Math.max(1, filteredRecipes.size() - MAX_VISIBLE_RECIPES);
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbHeight, 0xFFAAAAAA);
        }
    }

    private int getTypeColor(String recipeType) {
        return switch (recipeType) {
            case "有形状配方" -> 0x66FF66;
            case "无形状配方" -> 0x6699FF;
            case "熔炼配方" -> 0xFFAA66;
            case "高炉配方" -> 0xFF9966;
            case "烟熏配方" -> 0xFFFF66;
            case "营火烹饪" -> 0xFF6666;
            case "Avaritia配方" -> 0xFF66AA;
            case "Avaritia有形状配方" -> 0xFF99AA;
            case "Avaritia无形状配方" -> 0xCC66AA;
            default -> 0xAAAAAA;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = topPos + 105;
        int listBottom = listTop + MAX_VISIBLE_RECIPES * RECIPE_ITEM_HEIGHT;
        int listAreaX = leftPos + RECIPE_DETAIL_WIDTH + 15;
        int listRight = leftPos + contentWidth - 50;

        if (mouseX >= listAreaX && mouseX < listRight &&
                mouseY >= listTop && mouseY < listBottom && !filteredRecipes.isEmpty()) {

            int clickedIndex = (int)((mouseY - listTop) / RECIPE_ITEM_HEIGHT) + scrollOffset;

            if (clickedIndex >= 0 && clickedIndex < filteredRecipes.size()) {
                if (selectedRecipeIndex == clickedIndex && button == 0) {
                    selectRecipe();
                    return true;
                } else {
                    selectedRecipeIndex = clickedIndex;
                    updateButtons();
                    parseSelectedRecipe(); // 解析选中的配方
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            scrollUp();
        } else {
            scrollDown();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 264 && !filteredRecipes.isEmpty()) { // DOWN
            if (selectedRecipeIndex < filteredRecipes.size() - 1) {
                selectedRecipeIndex++;
                if (selectedRecipeIndex >= scrollOffset + MAX_VISIBLE_RECIPES) {
                    scrollDown();
                }
                updateButtons();
                parseSelectedRecipe();
            }
            return true;
        } else if (keyCode == 265 && !filteredRecipes.isEmpty()) { // UP
            if (selectedRecipeIndex > 0) {
                selectedRecipeIndex--;
                if (selectedRecipeIndex < scrollOffset) {
                    scrollUp();
                }
                updateButtons();
                parseSelectedRecipe();
            }
            return true;
        } else if (keyCode == 257 && selectedRecipeIndex >= 0) { // ENTER
            selectRecipe();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void parseSelectedRecipe() {
        currentRecipeSlots.clear();
        currentResultSlot = null;
        currentRecipeTypeDisplay = "";

        if (selectedRecipeIndex < 0 || selectedRecipeIndex >= filteredRecipes.size()) {
            return;
        }

        RecipeEntry entry = filteredRecipes.get(selectedRecipeIndex);
        Recipe<?> recipe = entry.recipe;

        if (recipe == null) {
            return;
        }

        try {
            String recipeTypeName = recipe.getType().toString().toLowerCase();
            currentRecipeTypeDisplay = entry.recipeType;

            int detailX = leftPos + 10;
            int detailY = topPos + 60;

            // 设置结果槽位
            currentResultSlot = new SlotInfo(detailX + RECIPE_DETAIL_WIDTH - 50, detailY + 20, entry.resultItem.copy());

            if (recipeTypeName.contains("crafting_shaped") || recipeTypeName.contains("shaped")) {
                parseShapedRecipe(recipe, detailX + 20, detailY + 100);
            } else if (recipeTypeName.contains("crafting_shapeless") || recipeTypeName.contains("shapeless")) {
                parseShapelessRecipe(recipe, detailX + 20, detailY + 100);
            } else if (recipeTypeName.contains("smelting") || recipeTypeName.contains("blasting") ||
                    recipeTypeName.contains("smoking") || recipeTypeName.contains("campfire")) {
                parseSmeltingRecipe(recipe, detailX + 20, detailY + 100);
            } else if (recipeTypeName.contains("avaritia")) {
                if (recipeTypeName.contains("shaped")) {
                    parseAvaritiaShapedRecipe(recipe, detailX + 10, detailY + 100);
                } else {
                    parseAvaritiaShapelessRecipe(recipe, detailX + 10, detailY + 100);
                }
            } else {
                // 默认作为无序配方处理
                parseShapelessRecipe(recipe, detailX + 20, detailY + 100);
            }

        } catch (Exception e) {
            LOGGER.warn("解析配方失败: {}", e.getMessage());
            currentRecipeTypeDisplay = "解析失败";
        }
    }

    private void parseShapedRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();

        // 3x3网格
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int index = y * 3 + x;
                ItemStack item = ItemStack.EMPTY;

                if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                    ItemStack[] items = ingredients.get(index).getItems();
                    if (items.length > 0) {
                        item = items[0].copy();
                    }
                }

                currentRecipeSlots.add(new SlotInfo(
                        startX + x * SLOT_SPACING,
                        startY + y * SLOT_SPACING,
                        item
                ));
            }
        }
    }

    private void parseShapelessRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();

        // 3x3网格，但只显示有材料的槽位
        for (int i = 0; i < 9; i++) {
            int x = i % 3;
            int y = i / 3;
            ItemStack item = ItemStack.EMPTY;

            if (i < ingredients.size() && !ingredients.get(i).isEmpty()) {
                ItemStack[] items = ingredients.get(i).getItems();
                if (items.length > 0) {
                    item = items[0].copy();
                }
            }

            currentRecipeSlots.add(new SlotInfo(
                    startX + x * SLOT_SPACING,
                    startY + y * SLOT_SPACING,
                    item
            ));
        }
    }

    private void parseSmeltingRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        ItemStack item = ItemStack.EMPTY;

        if (!ingredients.isEmpty() && !ingredients.get(0).isEmpty()) {
            ItemStack[] items = ingredients.get(0).getItems();
            if (items.length > 0) {
                item = items[0].copy();
            }
        }

        // 单个槽位用于熔炼原料
        currentRecipeSlots.add(new SlotInfo(startX + SLOT_SPACING, startY + SLOT_SPACING, item));
    }

    private void parseAvaritiaShapedRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int gridSize = getAvaritiaGridSizeFromIngredientCount(ingredients.size());

        // 缩小槽位间距以适应详情区域
        int slotSpacing = Math.min(SLOT_SIZE + 2, (RECIPE_DETAIL_WIDTH - 40) / gridSize);

        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                int index = y * gridSize + x;
                ItemStack item = ItemStack.EMPTY;

                if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                    ItemStack[] items = ingredients.get(index).getItems();
                    if (items.length > 0) {
                        item = items[0].copy();
                    }
                }

                currentRecipeSlots.add(new SlotInfo(
                        startX + x * slotSpacing,
                        startY + y * slotSpacing,
                        item
                ));
            }
        }
    }

    private void parseAvaritiaShapelessRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int gridSize = getAvaritiaGridSizeFromIngredientCount(ingredients.size());

        // 缩小槽位间距以适应详情区域
        int slotSpacing = Math.min(SLOT_SIZE + 2, (RECIPE_DETAIL_WIDTH - 40) / gridSize);

        for (int i = 0; i < Math.min(ingredients.size(), gridSize * gridSize); i++) {
            int x = i % gridSize;
            int y = i / gridSize;
            ItemStack item = ItemStack.EMPTY;

            if (!ingredients.get(i).isEmpty()) {
                ItemStack[] items = ingredients.get(i).getItems();
                if (items.length > 0) {
                    item = items[0].copy();
                }
            }

            currentRecipeSlots.add(new SlotInfo(
                    startX + x * slotSpacing,
                    startY + y * slotSpacing,
                    item
            ));
        }
    }

    private int getAvaritiaGridSizeFromIngredientCount(int ingredientCount) {
        if (ingredientCount <= 9) return 3;
        if (ingredientCount <= 25) return 5;
        if (ingredientCount <= 49) return 7;
        return 9; // 修复：确保返回9而不是其他值
    }

    private static class RecipeEntry {
        public final ResourceLocation recipeId;
        public final ItemStack resultItem;
        public final String recipeType;
        public final Recipe<?> recipe; // 添加原始配方引用

        public RecipeEntry(ResourceLocation recipeId, ItemStack resultItem, String recipeType, Recipe<?> recipe) {
            this.recipeId = recipeId;
            this.resultItem = resultItem;
            this.recipeType = recipeType;
            this.recipe = recipe;
        }
    }

    private static class SlotInfo {
        public final int x, y;
        public final ItemStack item;

        public SlotInfo(int x, int y, ItemStack item) {
            this.x = x;
            this.y = y;
            this.item = item;
        }
    }
}