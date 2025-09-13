package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.ItemStack;
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
    private static final int GUI_WIDTH = 450;
    private static final int GUI_HEIGHT = 350;
    private static final int RECIPE_ITEM_HEIGHT = 22;
    private static final int MAX_VISIBLE_RECIPES = 11;

    private final Screen parentScreen;
    private final Consumer<ResourceLocation> onRecipeSelected;

    private int leftPos, topPos;
    private List<RecipeEntry> allRecipes = new ArrayList<>();
    private List<RecipeEntry> filteredRecipes = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedRecipeIndex = -1;
    private String loadError = null;

    private EditBox searchBox;
    private Button selectButton;
    private Button cancelButton;
    private Button scrollUpButton;
    private Button scrollDownButton;
    private Button refreshButton; // 添加刷新按钮

    public RecipeSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onRecipeSelected) {
        super(Component.literal("选择配方"));
        this.parentScreen = parentScreen;
        this.onRecipeSelected = onRecipeSelected;
        loadRecipes();
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
                    allRecipes.add(new RecipeEntry(id, resultItem, recipeType));
                    validRecipeCount++;
                } catch (Exception e) {
                    LOGGER.warn("处理配方时出错: {}", e.getMessage());
                }
            }
            if (validRecipeCount == 0) {
                loadError = "没有可用的配方数据";
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
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
        searchBox = new EditBox(this.font, leftPos + 20, topPos + 45, GUI_WIDTH - 40, 20,
                Component.literal("搜索配方"));
        searchBox.setHint(Component.literal("输入配方ID或物品名称进行搜索..."));
        searchBox.setResponder(this::onSearchTextChanged);
        addRenderableWidget(searchBox);
        selectButton = addRenderableWidget(Button.builder(
                        Component.literal("选择配方"),
                        button -> selectRecipe())
                .bounds(leftPos + 20, topPos + 75, 80, 20)
                .build());
        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(leftPos + 110, topPos + 75, 60, 20)
                .build());
        refreshButton = addRenderableWidget(Button.builder(
                        Component.literal("刷新"),
                        button -> refreshRecipes())
                .bounds(leftPos + 180, topPos + 75, 50, 20)
                .build());
        scrollUpButton = addRenderableWidget(Button.builder(
                        Component.literal("▲"),
                        button -> scrollUp())
                .bounds(leftPos + GUI_WIDTH - 40, topPos + 105, 30, 20)
                .build());

        scrollDownButton = addRenderableWidget(Button.builder(
                        Component.literal("▼"),
                        button -> scrollDown())
                .bounds(leftPos + GUI_WIDTH - 40, topPos + GUI_HEIGHT - 50, 30, 20)
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
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFF404040);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF606060);
        guiGraphics.drawCenteredString(this.font, this.title, leftPos + GUI_WIDTH / 2, topPos + 15, 0xFFFFFF);
        if (loadError != null) {
            guiGraphics.drawCenteredString(this.font, "§c错误: " + loadError,
                    leftPos + GUI_WIDTH / 2, topPos + 30, 0xFFAAAA);
            guiGraphics.drawCenteredString(this.font, "§e点击刷新按钮重试",
                    leftPos + GUI_WIDTH / 2, topPos + 105, 0xFFCC66);
        } else {
            String countText = String.format("显示 %d/%d 个配方", filteredRecipes.size(), allRecipes.size());
            guiGraphics.drawString(this.font, countText, leftPos + 20, topPos + 30, 0xCCCCCC, false);
        }
        int listTop = topPos + 105;
        int listBottom = listTop + MAX_VISIBLE_RECIPES * RECIPE_ITEM_HEIGHT;
        guiGraphics.fill(leftPos + 10, listTop, leftPos + GUI_WIDTH - 50, listBottom, 0xFF000000);
        guiGraphics.fill(leftPos + 11, listTop + 1, leftPos + GUI_WIDTH - 51, listBottom - 1, 0xFF808080); // 改为深灰色背景
        if (loadError == null) {
            renderRecipeList(guiGraphics, mouseX, mouseY, listTop);
        } else {
            guiGraphics.drawCenteredString(this.font, "无法加载配方列表",
                    leftPos + GUI_WIDTH / 2, listTop + 50, 0xCCCCCC);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderRecipeList(GuiGraphics guiGraphics, int mouseX, int mouseY, int listTop) {
        if (filteredRecipes.isEmpty()) {
            String emptyMessage = allRecipes.isEmpty() ? "没有找到任何配方" : "没有匹配的配方";
            guiGraphics.drawCenteredString(this.font, emptyMessage,
                    leftPos + GUI_WIDTH / 2, listTop + 50, 0xCCCCCC);
            return;
        }

        for (int i = 0; i < MAX_VISIBLE_RECIPES && i + scrollOffset < filteredRecipes.size(); i++) {
            int recipeIndex = i + scrollOffset;
            RecipeEntry recipe = filteredRecipes.get(recipeIndex);

            int itemY = listTop + i * RECIPE_ITEM_HEIGHT;
            int itemX = leftPos + 15;

            boolean isHovered = mouseX >= itemX && mouseX < leftPos + GUI_WIDTH - 50 &&
                    mouseY >= itemY && mouseY < itemY + RECIPE_ITEM_HEIGHT;
            boolean isSelected = recipeIndex == selectedRecipeIndex;
            if (isSelected) {
                guiGraphics.fill(itemX, itemY, leftPos + GUI_WIDTH - 50, itemY + RECIPE_ITEM_HEIGHT, 0xFF4488CC);
            } else if (isHovered) {
                guiGraphics.fill(itemX, itemY, leftPos + GUI_WIDTH - 50, itemY + RECIPE_ITEM_HEIGHT, 0xFF666699);
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
            if (displayText.length() > 50) {
                displayText = displayText.substring(0, 47) + "...";
            }
            int textColor = isSelected ? 0xFFFFFF : 0xFFFFFF; // 统一使用白色文字
            guiGraphics.drawString(this.font, displayText, itemX + 22, itemY + 2, textColor, false);
            String typeText = "[" + recipe.recipeType + "]";
            int typeColor = getTypeColor(recipe.recipeType);
            if (isSelected) typeColor = 0xFFFFFF;
            guiGraphics.drawString(this.font, typeText, itemX + 22, itemY + 12, typeColor, false);
        }
        if (filteredRecipes.size() > MAX_VISIBLE_RECIPES) {
            int scrollBarX = leftPos + GUI_WIDTH - 45;
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
        int listTop = topPos + 105; // 更新为新的列表顶部位置
        int listBottom = listTop + MAX_VISIBLE_RECIPES * RECIPE_ITEM_HEIGHT;

        if (mouseX >= leftPos + 15 && mouseX < leftPos + GUI_WIDTH - 50 &&
                mouseY >= listTop && mouseY < listBottom && !filteredRecipes.isEmpty()) {

            int clickedIndex = (int)((mouseY - listTop) / RECIPE_ITEM_HEIGHT) + scrollOffset;

            if (clickedIndex >= 0 && clickedIndex < filteredRecipes.size()) {
                if (selectedRecipeIndex == clickedIndex && button == 0) {
                    selectRecipe();
                    return true;
                } else {
                    selectedRecipeIndex = clickedIndex;
                    updateButtons();
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
            }
            return true;
        } else if (keyCode == 265 && !filteredRecipes.isEmpty()) { // UP
            if (selectedRecipeIndex > 0) {
                selectedRecipeIndex--;
                if (selectedRecipeIndex < scrollOffset) {
                    scrollUp();
                }
                updateButtons();
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

    private static class RecipeEntry {
        public final ResourceLocation recipeId;
        public final ItemStack resultItem;
        public final String recipeType;

        public RecipeEntry(ResourceLocation recipeId, ItemStack resultItem, String recipeType) {
            this.recipeId = recipeId;
            this.resultItem = resultItem;
            this.recipeType = recipeType;
        }
    }
}