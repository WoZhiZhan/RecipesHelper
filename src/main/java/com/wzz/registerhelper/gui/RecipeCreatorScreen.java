package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.generator.KubeJSCodeGenerator;
import com.wzz.registerhelper.gui.recipe.*;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeBuilder;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class RecipeCreatorScreen extends Screen {

    private static final int PADDING = 20;

    // 核心组件
    private SlotManager slotManager;
    private FillModeHandler fillModeHandler;
    private RecipeLoader recipeLoader;
    private RecipeBuilder recipeBuilder;

    // 动态尺寸变量
    private int contentWidth;
    private int contentHeight;
    private int leftPos, topPos;

    // 配方状态 - 使用新的动态系统
    private RecipeTypeDefinition currentRecipeType;
    private String currentCraftingMode = "shaped"; // 字符串而不是枚举
    private String currentCookingType = "smelting";
    private int customTier = 1; // 用于支持动态tier
    private boolean isEditingExisting = false;
    private ResourceLocation editingRecipeId = null;

    // UI控件
    private Button recipeTypeButton; // 改为普通按钮触发下拉列表
    private CycleButton<String> craftingModeButton;
    private CycleButton<String> cookingTypeButton;
    private CycleButton<Integer> tierButton; // 更通用的tier按钮
    private CycleButton<FillMode> fillModeButton;
    private EditBox resultCountBox;
    private EditBox cookingTimeBox;
    private EditBox cookingExpBox;
    private Button createButton;
    private Button cancelButton;
    private Button clearAllButton;
    private Button selectBrushItemButton;
    private Button editExistingRecipeButton;
    private Button recipeOperationButton;
    private Button blacklistManagerButton;
    private Button overrideManagerButton;

    // 构造函数
    public RecipeCreatorScreen() {
        super(Component.literal("配方创建器"));
        // 设置默认配方类型
        this.currentRecipeType = DynamicRecipeTypeConfig.getRecipeType("crafting_shaped");
        if (this.currentRecipeType == null) {
            // 后备方案：使用第一个可用的配方类型
            List<RecipeTypeDefinition> available = DynamicRecipeTypeConfig.getAvailableRecipeTypes();
            this.currentRecipeType = available.isEmpty() ? null : available.get(0);
        }
        initializeComponents();
    }

    public RecipeCreatorScreen(ResourceLocation recipeId) {
        super(Component.literal("配方编辑器"));
        this.editingRecipeId = recipeId;
        this.isEditingExisting = true;
        // 设置默认配方类型
        this.currentRecipeType = DynamicRecipeTypeConfig.getRecipeType("crafting_shaped");
        initializeComponents();
        loadExistingRecipe(recipeId);
    }

    /**
     * 初始化核心组件
     */
    private void initializeComponents() {
        // 初始化回调函数
        Consumer<String> errorCallback = this::displayError;
        Consumer<String> successCallback = this::displaySuccess;
        Consumer<Integer> itemSelectorCallback = this::openItemSelectorForSlot;
        Runnable brushSelectorCallback = this::openBrushSelector;

        // 创建组件实例
        this.fillModeHandler = new FillModeHandler(errorCallback, itemSelectorCallback, brushSelectorCallback);
        this.recipeLoader = new RecipeLoader(this::displayInfo);
        this.recipeBuilder = new RecipeBuilder(successCallback, errorCallback);

        // SlotManager将在calculateDynamicSize后初始化
        calculateDynamicSize();
    }

    /**
     * 计算动态尺寸
     */
    private void calculateDynamicSize() {
        if (currentRecipeType == null) return;

        // 获取当前配方类型的网格尺寸
        SlotManager.GridDimensions gridDim = getGridDimensions();

        // 计算所需的最小尺寸
        int rightPanelWidth = 150;
        this.contentWidth = Math.max(560, PADDING + gridDim.getPixelWidth() + PADDING + rightPanelWidth + PADDING);
        this.contentHeight = Math.max(480, PADDING + 90 + gridDim.getPixelHeight() + PADDING + 80);

        // 确保不超过屏幕尺寸
        this.contentWidth = Math.min(this.contentWidth, this.width - 40);
        this.contentHeight = Math.min(this.contentHeight, this.height - 40);

        // 计算位置
        this.leftPos = (this.width - contentWidth) / 2;
        this.topPos = (this.height - contentHeight) / 2;

        // 初始化SlotManager
        int rightPanelX = leftPos + contentWidth - 150;
        if (slotManager == null) {
            slotManager = new SlotManager(leftPos + PADDING, topPos, rightPanelX);
        } else {
            slotManager.updateCoordinates(leftPos + PADDING, topPos, rightPanelX);
        }

        // 设置配方类型到SlotManager（需要更新SlotManager以支持RecipeTypeDefinition）
        updateSlotManagerRecipeType();
    }

    /**
     * 更新SlotManager的配方类型
     */
    private void updateSlotManagerRecipeType() {
        if (slotManager == null || currentRecipeType == null) return;

        // 直接使用更新后的SlotManager，它现在支持RecipeTypeDefinition
        slotManager.setRecipeType(currentRecipeType, customTier, true);
    }

    /**
     * 获取网格尺寸
     */
    private SlotManager.GridDimensions getGridDimensions() {
        if (currentRecipeType == null) {
            return new SlotManager.GridDimensions(3, 3);
        }

        return new SlotManager.GridDimensions(
                currentRecipeType.getMaxGridWidth(),
                currentRecipeType.getMaxGridHeight()
        );
    }

    @Override
    protected void init() {
        calculateDynamicSize();
        initializeControls();
        updateVisibility();
    }

    /**
     * 初始化控件
     */
    private void initializeControls() {
        // 第一行控件 - 动态布局
        int controlStartX = leftPos + 15;
        int controlY1 = topPos + 35;
        int controlSpacing = 10;
        int currentX = controlStartX;

        // 配方类型选择器 - 改为下拉列表
        List<RecipeTypeDefinition> availableTypes = DynamicRecipeTypeConfig.getAvailableRecipeTypes();
        String currentTypeName = currentRecipeType != null ? currentRecipeType.getDisplayName() : "选择配方类型";

        recipeTypeButton = addRenderableWidget(Button.builder(
                        Component.literal(currentTypeName + " ▼"),
                        button -> openRecipeTypeSelector())
                .bounds(currentX, controlY1, 120, 20)
                .build());
        recipeTypeButton.setMessage(Component.literal(currentTypeName + " ▼"));
        currentX += 130 + controlSpacing;

        // 合成模式选择器（动态显示）
        craftingModeButton = addRenderableWidget(CycleButton.<String>builder(
                        mode -> Component.literal(getDisplayNameForMode(mode)))
                .withValues("shaped", "shapeless")
                .withInitialValue(currentCraftingMode)
                .displayOnlyValue()
                .create(currentX, controlY1, 60, 20,
                        Component.literal("合成模式"), this::onCraftingModeChanged));

        // 烹饪类型选择器（动态显示）
        cookingTypeButton = addRenderableWidget(CycleButton.<String>builder(
                        type -> Component.literal(getDisplayNameForCookingType(type)))
                .withValues(getCookingTypes())
                .withInitialValue(currentCookingType)
                .displayOnlyValue()
                .create(currentX, controlY1, 70, 20,
                        Component.literal("烹饪类型"), this::onCookingTypeChanged));
        currentX += 80 + controlSpacing;

        // 等级选择器（支持动态等级）
        tierButton = addRenderableWidget(CycleButton.<Integer>builder(
                        tier -> Component.literal("T" + tier))
                .withValues(getAvailableTiers())
                .withInitialValue(customTier)
                .displayOnlyValue()
                .create(currentX, controlY1, 50, 20,
                        Component.literal("等级"), this::onTierChanged));

        // 第二行控件
        int controlY2 = topPos + 65;
        currentX = controlStartX;

        fillModeButton = addRenderableWidget(CycleButton.<FillMode>builder(
                        mode -> Component.literal(mode.getDisplayName()))
                .withValues(FillMode.values())
                .withInitialValue(fillModeHandler.getCurrentMode())
                .displayOnlyValue()
                .create(currentX, controlY2, 80, 20,
                        Component.literal("填充模式"), this::onFillModeChanged));
        currentX += 90 + controlSpacing;

        selectBrushItemButton = addRenderableWidget(Button.builder(
                        Component.literal("选择画笔物品"),
                        button -> fillModeHandler.openBrushSelector())
                .bounds(currentX, controlY2, 100, 20)
                .build());

        // 右侧面板
        initializeRightPanel();

        // 底部按钮
        initializeBottomButtons();
    }

    /**
     * 获取模式显示名称
     */
    private String getDisplayNameForMode(String mode) {
        return switch (mode) {
            case "shaped" -> "有序";
            case "shapeless" -> "无序";
            default -> mode;
        };
    }

    /**
     * 获取烹饪类型显示名称
     */
    private String getDisplayNameForCookingType(String type) {
        return switch (type) {
            case "smelting" -> "熔炉";
            case "blasting" -> "高炉";
            case "smoking" -> "烟熏炉";
            case "campfire_cooking" -> "营火";
            default -> type;
        };
    }

    /**
     * 获取可用的烹饪类型
     */
    private String[] getCookingTypes() {
        return new String[]{"smelting", "blasting", "smoking", "campfire_cooking"};
    }

    /**
     * 获取可用的等级
     */
    private Integer[] getAvailableTiers() {
        if (currentRecipeType != null &&
                (currentRecipeType.isAvaritiaType() ||
                        Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class)))) {
            return new Integer[]{1, 2, 3, 4};
        }
        return new Integer[]{1};
    }

    /**
     * 初始化右侧面板
     */
    private void initializeRightPanel() {
        int rightPanelX = leftPos + contentWidth - 150 + 10;
        int rightPanelStartY = topPos + 130;

        resultCountBox = new EditBox(this.font, rightPanelX + 60, rightPanelStartY + 30, 40, 20,
                Component.literal("数量"));
        resultCountBox.setValue("1");
        resultCountBox.setFilter(text -> text.matches("\\d*") &&
                (text.isEmpty() || Integer.parseInt(text) <= 64));
        addRenderableWidget(resultCountBox);

        // 烹饪时间和经验输入框
        String defaultTime = getDefaultTimeForCurrentType();
        String defaultExp = getDefaultExpForCurrentType();

        cookingTimeBox = new EditBox(this.font, rightPanelX + 60, rightPanelStartY + 60, 60, 20,
                Component.literal("烹饪时间"));
        cookingTimeBox.setValue(defaultTime);
        cookingTimeBox.setFilter(text -> text.matches("\\d*") &&
                (text.isEmpty() || Integer.parseInt(text) <= 32000));
        addRenderableWidget(cookingTimeBox);

        cookingExpBox = new EditBox(this.font, rightPanelX + 60, rightPanelStartY + 90, 60, 20,
                Component.literal("烹饪经验"));
        cookingExpBox.setValue(defaultExp);
        cookingExpBox.setFilter(text -> text.matches("\\d*\\.?\\d*"));
        addRenderableWidget(cookingExpBox);

        clearAllButton = addRenderableWidget(Button.builder(
                        Component.literal("清空材料"),
                        button -> clearAllIngredients())
                .bounds(rightPanelX, rightPanelStartY + 150, 80, 20)
                .build());
    }

    /**
     * 获取当前类型的默认时间
     */
    private String getDefaultTimeForCurrentType() {
        if (currentRecipeType != null && currentRecipeType.supportsCookingSettings()) {
            String defaultTime = currentRecipeType.getProperty("defaultTime", String.class);
            if (defaultTime != null) {
                return defaultTime;
            }
        }
        return switch (currentCookingType) {
            case "blasting", "smoking" -> "100";
            case "campfire_cooking" -> "600";
            default -> "200";
        };
    }

    /**
     * 获取当前类型的默认经验
     */
    private String getDefaultExpForCurrentType() {
        if (currentRecipeType != null && currentRecipeType.supportsCookingSettings()) {
            String defaultExp = currentRecipeType.getProperty("defaultExp", String.class);
            if (defaultExp != null) {
                return defaultExp;
            }
        }
        return switch (currentCookingType) {
            case "smoking", "campfire_cooking" -> "0.35";
            default -> "0.7";
        };
    }

    /**
     * 初始化底部按钮
     */
    private void initializeBottomButtons() {
        int buttonY = topPos + contentHeight - 30;
        int centerX = leftPos + contentWidth / 2;
        int buttonSpacing = 10;

        // 计算所有按钮的总宽度
        int[] buttonWidths = {80, 70, 70, 70, 80, 80, 50}; // 增加一个导出按钮
        int totalButtonWidth = Arrays.stream(buttonWidths).sum() + buttonSpacing * (buttonWidths.length - 1);
        int buttonStartX = centerX - totalButtonWidth / 2;

        blacklistManagerButton = addRenderableWidget(Button.builder(
                        Component.literal("黑名单管理"),
                        button -> openBlacklistManager())
                .bounds(buttonStartX, buttonY, buttonWidths[0], 20)
                .build());
        buttonStartX += buttonWidths[0] + buttonSpacing;

        overrideManagerButton = addRenderableWidget(Button.builder(
                        Component.literal("覆盖管理"),
                        button -> openOverrideManager())
                .bounds(buttonStartX, buttonY, buttonWidths[1], 20)
                .build());
        buttonStartX += buttonWidths[1] + buttonSpacing;

        editExistingRecipeButton = addRenderableWidget(Button.builder(
                        Component.literal("编辑配方"),
                        button -> openRecipeSelector())
                .bounds(buttonStartX, buttonY, buttonWidths[2], 20)
                .build());
        buttonStartX += buttonWidths[2] + buttonSpacing;

        recipeOperationButton = addRenderableWidget(Button.builder(
                        Component.literal("配方操作"),
                        button -> openRecipeOperationSelector())
                .bounds(buttonStartX, buttonY, buttonWidths[3], 20)
                .build());
        buttonStartX += buttonWidths[3] + buttonSpacing;

        createButton = addRenderableWidget(Button.builder(
                        Component.literal(isEditingExisting ? "更新配方" : "创建配方"),
                        button -> createRecipe())
                .bounds(buttonStartX, buttonY, buttonWidths[4], 20)
                .build());
        buttonStartX += buttonWidths[4] + buttonSpacing;

        // 新增导出KubeJS按钮
        Button exportKubeJSButton = addRenderableWidget(Button.builder(
                        Component.literal("导出KubeJS"),
                        button -> exportToKubeJS())
                .bounds(buttonStartX, buttonY, buttonWidths[5], 20)
                .build());
        buttonStartX += buttonWidths[5] + buttonSpacing;

        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(buttonStartX, buttonY, buttonWidths[6], 20)
                .build());
    }

    /**
     * 更新控件可见性
     */
    private void updateVisibility() {
        if (currentRecipeType == null) return;

        // 合成模式按钮
        if (craftingModeButton != null) {
            String category = currentRecipeType.getProperty("category", String.class);
            craftingModeButton.visible = "crafting".equals(category) || "avaritia".equals(category);
        }

        // 烹饪类型按钮
        if (cookingTypeButton != null) {
            cookingTypeButton.visible = currentRecipeType.supportsCookingSettings();
        }

        // 等级按钮
        if (tierButton != null) {
            boolean shouldShowTier = currentRecipeType.isAvaritiaType() ||
                    Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class));

            tierButton.visible = shouldShowTier;
            tierButton.active = shouldShowTier;

            if (tierButton.visible) {
                tierButton.setValue(customTier);
            }
        }

        // 填充模式按钮
        if (fillModeButton != null) {
            fillModeButton.visible = currentRecipeType.supportsFillMode();
        }

        // 画笔选择按钮
        if (selectBrushItemButton != null) {
            selectBrushItemButton.visible = currentRecipeType.supportsFillMode() &&
                    fillModeHandler.shouldShowBrushSelector();
        }

        // 烹饪相关输入框
        boolean isCookingType = currentRecipeType.supportsCookingSettings();
        if (cookingTimeBox != null) {
            cookingTimeBox.visible = isCookingType;
            cookingTimeBox.setVisible(isCookingType);
            if (isCookingType) {
                cookingTimeBox.setValue(getDefaultTimeForCurrentType());
            }
        }
        if (cookingExpBox != null) {
            cookingExpBox.visible = isCookingType;
            cookingExpBox.setVisible(isCookingType);
            if (isCookingType) {
                cookingExpBox.setValue(getDefaultExpForCurrentType());
            }
        }
    }

    /**
     * 打开配方类型选择器
     */
    private void openRecipeTypeSelector() {
        if (minecraft != null) {
            List<RecipeTypeDefinition> availableTypes = DynamicRecipeTypeConfig.getAvailableRecipeTypes();
            if (availableTypes.isEmpty()) {
                displayError("没有可用的配方类型");
                return;
            }
            minecraft.setScreen(new RecipeTypeSelectorScreen(this, this::onRecipeTypeSelected,
                    availableTypes, currentRecipeType));
        }
    }

    /**
     * 配方类型选择回调
     */
    private void onRecipeTypeSelected(RecipeTypeDefinition newType) {
        if (newType != currentRecipeType) {
            onRecipeTypeChanged(newType);
        }
    }

    /**
     * 配方类型更改处理（重命名原方法）
     */
    private void onRecipeTypeChanged(RecipeTypeDefinition newType) {
        this.currentRecipeType = newType;

        // 更新按钮显示
        if (recipeTypeButton != null) {
            recipeTypeButton.setMessage(Component.literal(newType.getDisplayName() + " ▼"));
        }

        // 重置相关设置
        if (newType.isAvaritiaType()) {
            Integer defaultTier = newType.getProperty("tier", Integer.class);
            if (defaultTier != null) {
                this.customTier = defaultTier;
            }
        }

        // 根据新类型自动设置模式
        String mode = newType.getProperty("mode", String.class);
        if (mode != null) {
            this.currentCraftingMode = mode;
            if (craftingModeButton != null) {
                craftingModeButton.setValue(mode);
            }
        }

        updateSlotManagerRecipeType();
        updateVisibility();

        // 可能需要重新计算尺寸
        if (newType.isAvaritiaType() || newType.getMaxGridWidth() != 3) {
            calculateDynamicSize();
        }
    }

    private void onCraftingModeChanged(CycleButton<String> button, String newMode) {
        this.currentCraftingMode = newMode;
    }

    private void onCookingTypeChanged(CycleButton<String> button, String newType) {
        this.currentCookingType = newType;
        if (cookingTimeBox != null) {
            cookingTimeBox.setValue(getDefaultTimeForCurrentType());
        }
        if (cookingExpBox != null) {
            cookingExpBox.setValue(getDefaultExpForCurrentType());
        }
    }

    private void onTierChanged(CycleButton<Integer> button, Integer newTier) {
        if (this.customTier != newTier) {
            this.customTier = newTier;
            if (currentRecipeType != null &&
                    (currentRecipeType.isAvaritiaType() ||
                            Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class)))) {
                updateSlotManagerRecipeType();
                calculateDynamicSize();
            }
        }
    }

    private void onFillModeChanged(CycleButton<FillMode> button, FillMode newMode) {
        fillModeHandler.setCurrentMode(newMode);
        updateVisibility();
    }

    private void openItemSelectorForSlot(int slotIndex) {
        if (minecraft != null) {
            minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                slotManager.setIngredient(slotIndex, item);
            }));
        }
    }

    private void openBrushSelector() {
        if (minecraft != null) {
            minecraft.setScreen(new ItemSelectorScreen(this, fillModeHandler::setBrushItem));
        }
    }

    private void openResultSelector() {
        if (minecraft != null) {
            minecraft.setScreen(new ItemSelectorScreen(this, slotManager::setResultItem));
        }
    }

    private void clearAllIngredients() {
        slotManager.clearAllIngredients();
        fillModeHandler.reset();
        editingRecipeId = null;
        isEditingExisting = false;
        createButton.setMessage(Component.literal("创建配方"));
    }

    private void openRecipeSelector() {
        if (minecraft != null) {
            List<UnifiedRecipeInfo> editableRecipes = recipeLoader.getEditableRecipes();
            if (editableRecipes.isEmpty()) {
                displayError("没有找到可编辑的配方");
                return;
            }
            minecraft.setScreen(new RecipeSelectorScreen(this, this::loadSelectedRecipe,
                    editableRecipes, "选择要编辑的配方"));
        }
    }

    private void openRecipeOperationSelector() {
        if (minecraft != null) {
            List<UnifiedRecipeInfo> allRecipes = recipeLoader.getAllRecipes();
            if (allRecipes.isEmpty()) {
                displayError("没有找到任何配方");
                return;
            }
            minecraft.setScreen(new RecipeSelectorScreen(this, this::handleRecipeOperation,
                    allRecipes, "选择要操作的配方"));
        }
    }

    private void openBlacklistManager() {
        if (minecraft != null) {
            minecraft.setScreen(new BlacklistManagerScreen(this));
        }
    }

    private void openOverrideManager() {
        if (minecraft != null) {
            minecraft.setScreen(new OverrideManagerScreen(this));
        }
    }

    private void loadSelectedRecipe(ResourceLocation recipeId) {
        UnifiedRecipeInfo info = recipeLoader.findRecipeInfo(recipeId);
        if (info == null) {
            displayError("找不到配方信息: " + recipeId);
            return;
        }

        this.editingRecipeId = recipeId;
        this.isEditingExisting = true;

        String buttonText = "更新配方";
        if (info.hasOverride || (!recipeLoader.isCustomRecipe(recipeId))) {
            buttonText += " (覆盖)";
        }
        createButton.setMessage(Component.literal(buttonText));

        loadExistingRecipe(recipeId);
        displayInfo("已载入 " + info.description);
    }

    private void loadExistingRecipe(ResourceLocation recipeId) {
        RecipeLoader.LoadResult result = recipeLoader.loadRecipe(recipeId);

        if (!result.success) {
            displayError(result.message);
            return;
        }

        // 根据加载的配方类型查找对应的RecipeTypeDefinition
        RecipeTypeDefinition loadedType = findRecipeTypeDefinition(result);
        if (loadedType != null) {
            this.currentRecipeType = loadedType;
            recipeTypeButton.setMessage(Component.literal(loadedType.getDisplayName() + " ▼"));
        }

        // 应用其他设置
        this.currentCraftingMode = result.craftingMode != null ?
                (result.craftingMode.name().toLowerCase()) : "shaped";
        this.currentCookingType = result.cookingType != null ?
                result.cookingType.name().toLowerCase() : "smelting";
        this.customTier = result.avaritiaTeir;

        // 更新UI控件
        craftingModeButton.setValue(currentCraftingMode);
        cookingTypeButton.setValue(currentCookingType);
        tierButton.setValue(customTier);

        // 设置材料和结果
        updateSlotManagerRecipeType();
        slotManager.setIngredients(result.ingredients);
        slotManager.setResultItem(result.resultItem);

        // 重新计算尺寸
        calculateDynamicSize();

        // 更新结果数量输入框
        if (resultCountBox != null) {
            resultCountBox.setValue(String.valueOf(result.resultItem.getCount()));
        }

        updateVisibility();
        displayInfo(result.message + ": " + recipeId);
    }

    /**
     * 根据加载结果查找对应的配方类型定义
     */
    private RecipeTypeDefinition findRecipeTypeDefinition(RecipeLoader.LoadResult result) {
        if (result.recipeType != null) {
            String recipeTypeName = result.recipeType.name().toLowerCase();

            if ("crafting".equals(recipeTypeName)) {
                String mode = result.craftingMode != null ? result.craftingMode.name().toLowerCase() : "shaped";
                return DynamicRecipeTypeConfig.getRecipeType("crafting_" + mode);
            } else if ("cooking".equals(recipeTypeName)) {
                String cookingType = result.cookingType != null ? result.cookingType.name().toLowerCase() : "smelting";
                return DynamicRecipeTypeConfig.getRecipeType(cookingType);
            } else if ("avaritia".equals(recipeTypeName)) {
                String mode = result.craftingMode != null ? result.craftingMode.name().toLowerCase() : "shaped";
                return DynamicRecipeTypeConfig.getRecipeType("avaritia_" + mode + "_t" + result.avaritiaTeir);
            } else if ("brewing".equals(recipeTypeName)) {
                return DynamicRecipeTypeConfig.getRecipeType("brewing");
            } else if ("stonecutting".equals(recipeTypeName)) {
                return DynamicRecipeTypeConfig.getRecipeType("stonecutting");
            } else if ("smithing".equals(recipeTypeName) || "smithing_transform".equals(recipeTypeName)) {
                return DynamicRecipeTypeConfig.getRecipeType("smithing_transform");
            }
        }

        return currentRecipeType;
    }

    private void handleRecipeOperation(ResourceLocation recipeId) {
        UnifiedRecipeInfo info = recipeLoader.findRecipeInfo(recipeId);
        if (info == null) {
            displayError("找不到配方信息: " + recipeId);
            return;
        }

        try {
            boolean success = false;
            String resultMessage = "";

            if (info.isBlacklisted) {
                success = RecipeBlacklistManager.removeFromBlacklist(recipeId);
                resultMessage = success ? "配方已恢复" : "恢复配方失败";
            } else if (info.hasOverride) {
                success = UnifiedRecipeOverrideManager.removeOverride(recipeId);
                resultMessage = success ? "覆盖已移除" : "移除覆盖失败";
            } else {
                success = RecipeBlacklistManager.addToBlacklist(recipeId);
                resultMessage = success ? "配方已禁用" : "禁用配方失败";
            }

            if (success) {
                displaySuccess(resultMessage + ": " + recipeId + " 使用 /reload 刷新配方");

                if (editingRecipeId != null && editingRecipeId.equals(recipeId)) {
                    if (info.isBlacklisted) {
                        clearAllIngredients();
                        displayInfo("配方已被禁用，已退出编辑模式");
                    } else if (info.hasOverride) {
                        displayInfo("覆盖已移除，当前编辑的是原始配方");
                    }
                }
            } else {
                displayError(resultMessage + ": " + recipeId);
            }

        } catch (Exception e) {
            displayError("操作配方时发生错误: " + e.getMessage());
        }
    }

    private void createRecipe() {
        if (currentRecipeType == null) {
            displayError("请选择配方类型！");
            return;
        }

        try {
            int count = Integer.parseInt(resultCountBox.getValue());
            if (count <= 0) {
                displayError("数量必须大于0！");
                return;
            }

            ItemStack resultItem = slotManager.getResultItem();
            if (resultItem.isEmpty()) {
                displayError("请选择结果物品！");
                return;
            }

            resultItem = resultItem.copy();
            resultItem.setCount(count);

            float cookingTime = currentRecipeType.supportsCookingSettings() ?
                    Float.parseFloat(cookingTimeBox.getValue()) : 0;
            float cookingExp = currentRecipeType.supportsCookingSettings() ?
                    Float.parseFloat(cookingExpBox.getValue()) : 0;

            // 创建构建参数 - 需要更新RecipeBuilder以支持新系统
            DynamicRecipeBuilder.BuildParams params = new DynamicRecipeBuilder.BuildParams(
                    currentRecipeType, currentCraftingMode, currentCookingType, customTier,
                    resultItem, slotManager.getIngredients(), cookingTime, cookingExp,
                    editingRecipeId, isEditingExisting
            );

            // 使用新的构建器
            DynamicRecipeBuilder dynamicBuilder = new DynamicRecipeBuilder(this::displaySuccess, this::displayError);
            dynamicBuilder.buildRecipe(params);

        } catch (NumberFormatException e) {
            displayError("请输入有效的数量、时间或经验值！");
        }
    }

    // 渲染方法保持不变...
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // 绘制主背景
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + contentHeight, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + contentWidth - 1, topPos + contentHeight - 1, 0xFF8B8B8B);

        // 绘制标题
        String titleText = isEditingExisting ?
                (editingRecipeId != null ? "配方编辑器 - " + editingRecipeId.toString() : "配方编辑器")
                : "配方创建器";
        guiGraphics.drawCenteredString(this.font, titleText, leftPos + contentWidth / 2, topPos + 15, 0x404040);

        // 绘制标签
        renderLabels(guiGraphics);

        // 绘制分割线
        int rightPanelX = leftPos + contentWidth - 150 + 10;
        guiGraphics.fill(rightPanelX - 20, topPos + 20, rightPanelX - 18, topPos + contentHeight - 60, 0xFF606060);

        // 渲染槽位
        renderSlots(guiGraphics, mouseX, mouseY);

        // 渲染填充模式提示
        if (currentRecipeType != null && currentRecipeType.supportsFillMode()) {
            renderFillModeHint(guiGraphics);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    private void renderLabels(GuiGraphics guiGraphics) {
        int labelStartX = leftPos + 15;
        int labelY1 = topPos + 25;
        int labelY2 = topPos + 55;

        guiGraphics.drawString(this.font, "配方类型:", labelStartX, labelY1, 0x404040, false);

        if (currentRecipeType != null) {
            String category = currentRecipeType.getProperty("category", String.class);

            if ("crafting".equals(category) || "avaritia".equals(category)) {
                guiGraphics.drawString(this.font, "合成模式:", labelStartX + 140, labelY1, 0x404040, false);
                guiGraphics.drawString(this.font, "填充模式:", labelStartX, labelY2, 0x404040, false);
            } else if (currentRecipeType.supportsCookingSettings()) {
                guiGraphics.drawString(this.font, "烹饪类型:", labelStartX + 140, labelY1, 0x404040, false);
            }

            if (currentRecipeType.isAvaritiaType()) {
                guiGraphics.drawString(this.font, "等级:", labelStartX + 220, labelY1, 0x404040, false);
            }
        }

        // 右侧面板标签
        int rightPanelX = leftPos + contentWidth - 150 + 10;
        int rightPanelStartY = topPos + 130;

        guiGraphics.drawString(this.font, "结果:", rightPanelX, rightPanelStartY - 20, 0x404040, false);
        guiGraphics.drawString(this.font, "数量:", rightPanelX, rightPanelStartY + 20, 0x404040, false);

        if (currentRecipeType != null && currentRecipeType.supportsCookingSettings()) {
            guiGraphics.drawString(this.font, "时间:", rightPanelX, rightPanelStartY + 50, 0x404040, false);
            guiGraphics.drawString(this.font, "经验:", rightPanelX, rightPanelStartY + 80, 0x404040, false);
        }

        // 底部状态信息
        if (isEditingExisting) {
            guiGraphics.drawString(this.font, "§6编辑模式", labelStartX, topPos + contentHeight - 45, 0xFFCC00, false);
        }

        // 显示当前配方类型信息
        if (currentRecipeType != null) {
            String typeInfo = "§7" + currentRecipeType.getModId() + " | " + currentRecipeType.getId();
            guiGraphics.drawString(this.font, typeInfo, labelStartX + 100, topPos + contentHeight - 45, 0x666666, false);
        }
    }

    private void renderSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 只渲染材料槽位，输出槽由GUI自动创建
        for (int i = 0; i < slotManager.getIngredientSlots().size(); i++) {
            SlotManager.IngredientSlot slot = slotManager.getIngredientSlots().get(i);
            ItemStack item = i < slotManager.getIngredients().size() ?
                    slotManager.getIngredients().get(i) : ItemStack.EMPTY;
            renderSlot(guiGraphics, slot, mouseX, mouseY, item);
        }

        renderSlot(guiGraphics, slotManager.getResultSlot(), mouseX, mouseY, slotManager.getResultItem());
    }

    private void renderSlot(GuiGraphics guiGraphics, SlotManager.IngredientSlot slot, int mouseX, int mouseY, ItemStack item) {
        boolean isMouseOver = mouseX >= slot.x() && mouseX < slot.x() + 18 &&
                mouseY >= slot.y() && mouseY < slot.y() + 18;

        int bgColor = isMouseOver ? 0x80FFFFFF : 0xFF373737;
        guiGraphics.fill(slot.x(), slot.y(), slot.x() + 18, slot.y() + 18, bgColor);

        // 边框
        guiGraphics.fill(slot.x() - 1, slot.y() - 1, slot.x() + 19, slot.y(), 0xFF000000);
        guiGraphics.fill(slot.x() - 1, slot.y() + 18, slot.x() + 19, slot.y() + 19, 0xFF000000);
        guiGraphics.fill(slot.x() - 1, slot.y(), slot.x(), slot.y() + 18, 0xFF000000);
        guiGraphics.fill(slot.x() + 18, slot.y(), slot.x() + 19, slot.y() + 18, 0xFF000000);

        if (!item.isEmpty()) {
            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(item, slot.x() + 1, slot.y() + 1);
            RenderSystem.disableDepthTest();
        }
    }

    private void renderFillModeHint(GuiGraphics guiGraphics) {
        String hint = fillModeHandler.getHintText();
        int hintX = leftPos + 15;
        int hintY = topPos + 125;
        guiGraphics.drawString(this.font, hint, hintX, hintY, 0x666666, false);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 材料槽位工具提示
        for (int i = 0; i < slotManager.getIngredientSlots().size(); i++) {
            SlotManager.IngredientSlot slot = slotManager.getIngredientSlots().get(i);
            if (mouseX >= slot.x() && mouseX < slot.x() + 18 &&
                    mouseY >= slot.y() && mouseY < slot.y() + 18) {

                if (i < slotManager.getIngredients().size() && !slotManager.getIngredients().get(i).isEmpty()) {
                    guiGraphics.renderTooltip(this.font, slotManager.getIngredients().get(i), mouseX, mouseY);
                } else {
                    String tooltip = fillModeHandler.getSlotTooltip();
                    guiGraphics.renderTooltip(this.font, Component.literal(tooltip), mouseX, mouseY);
                }
                return;
            }
        }


        SlotManager.IngredientSlot resultSlot = slotManager.getResultSlot();
        if (mouseX >= resultSlot.x() && mouseX < resultSlot.x() + 18 &&
                mouseY >= resultSlot.y() && mouseY < resultSlot.y() + 18) {
            if (!slotManager.getResultItem().isEmpty()) {
                guiGraphics.renderTooltip(this.font, slotManager.getResultItem(), mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(this.font, Component.literal("点击选择结果物品"), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        SlotManager.IngredientSlot resultSlot = slotManager.getResultSlot();
        if (mouseX >= resultSlot.x() && mouseX < resultSlot.x() + 18 &&
                mouseY >= resultSlot.y() && mouseY < resultSlot.y() + 18) {
            if (button == 0) {
                openResultSelector();
            } else if (button == 1) {
                slotManager.setResultItem(ItemStack.EMPTY);
            }
            return true;
        }

        // 材料槽位点击
        for (int i = 0; i < slotManager.getIngredientSlots().size(); i++) {
            SlotManager.IngredientSlot slot = slotManager.getIngredientSlots().get(i);
            if (mouseX >= slot.x() && mouseX < slot.x() + 18 &&
                    mouseY >= slot.y() && mouseY < slot.y() + 18) {
                fillModeHandler.handleSlotClick(slotManager, i, button == 1);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 导出为KubeJS代码
     */
    private void exportToKubeJS() {
        if (currentRecipeType == null) {
            displayError("请先选择配方类型！");
            return;
        }

        ItemStack resultItem = slotManager.getResultItem();
        if (resultItem.isEmpty()) {
            displayError("请选择结果物品！");
            return;
        }

        List<ItemStack> ingredients = slotManager.getIngredients();
        if (ingredients.isEmpty() || ingredients.stream().allMatch(ItemStack::isEmpty)) {
            displayError("请添加合成材料！");
            return;
        }

        try {
            int resultCount = Integer.parseInt(resultCountBox.getValue());
            if (resultCount <= 0) {
                displayError("数量必须大于0！");
                return;
            }

            KubeJSCodeGenerator.GenerationResult result = KubeJSCodeGenerator.generateCode(
                    currentRecipeType, currentCraftingMode, resultItem, resultCount, ingredients
            );

            if (result.success) {
                copyToClipboard(result.code);
                displaySuccess(result.message + " 代码已复制到剪贴板！");
            } else {
                displayError(result.message);
            }
        } catch (NumberFormatException e) {
            displayError("请输入有效的数量！");
        } catch (Exception e) {
            displayError("导出失败: " + e.getMessage());
        }
    }

    /**
     * 复制到剪贴板
     */
    private void copyToClipboard(String text) {
        if (minecraft != null) {
            minecraft.keyboardHandler.setClipboard(text);
        }
    }

    // 消息显示方法
    private void displayError(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§c" + message));
        }
    }

    private void displaySuccess(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§a" + message));
        }
    }

    private void displayInfo(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§e" + message));
        }
    }
}