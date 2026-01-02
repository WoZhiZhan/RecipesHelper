package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.gui.recipe.*;
import com.wzz.registerhelper.gui.recipe.component.ComponentRenderManager;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeBuilder;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.network.BlacklistClientHelper;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.tags.CustomTagManager;
import com.wzz.registerhelper.util.ModLogger;
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

import java.util.*;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class RecipeCreatorScreen extends Screen {

    private static final int PADDING = 20;

    // 核心组件
    private SlotManager slotManager;
    private FillModeHandler fillModeHandler;
    private RecipeLoader recipeLoader;

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
    private ComponentRenderManager componentRenderManager;

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
    private RecipeLoader.LoadResult pendingLoadResult = null;

    public RecipeCreatorScreen(ResourceLocation recipeId) {
        super(Component.literal("配方编辑器"));
        this.editingRecipeId = recipeId;
        this.isEditingExisting = true;
        // 设置默认配方类型
        this.currentRecipeType = DynamicRecipeTypeConfig.getRecipeType("crafting_shaped");
        initializeComponents();
        pendingLoadResult = recipeLoader.loadRecipe(recipeId);
        if (pendingLoadResult.success) {
            RecipeTypeDefinition loadedType = findRecipeTypeDefinition(pendingLoadResult);
            if (loadedType != null) {
                this.currentRecipeType = loadedType;
                this.currentCraftingMode = pendingLoadResult.craftingMode != null ?
                        pendingLoadResult.craftingMode.name().toLowerCase() : "shaped";
                this.currentCookingType = pendingLoadResult.cookingType != null ?
                        pendingLoadResult.cookingType.name().toLowerCase() : "smelting";
                this.customTier = pendingLoadResult.avaritiaTeir;
            }
        }
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

        calculateDynamicSize();
        this.componentRenderManager = new ComponentRenderManager(this.font);
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
        if (componentRenderManager != null) {
            componentRenderManager.clear();
        }

        // 按顺序调用
        calculateDynamicSize();
        initializeControls();
        initializeComponentRenderers();

        // 应用待加载的配方数据
        if (pendingLoadResult != null && pendingLoadResult.success) {
            applyLoadedRecipe(pendingLoadResult);
            pendingLoadResult = null;
        }

        updateVisibility();
    }

    /**
     * 计算动态尺寸
     */
    private void calculateDynamicSize() {
        if (currentRecipeType == null) {
            ModLogger.getLogger().error("calculateDynamicSize: currentRecipeType为null");
            return;
        }

        // 获取当前配方类型的网格尺寸
        SlotManager.GridDimensions gridDim = getGridDimensions();

        // 计算所需的最小尺寸
        int rightPanelWidth = 150;
        this.contentWidth = Math.max(560, PADDING + gridDim.getPixelWidth() + PADDING + rightPanelWidth + PADDING);
        this.contentHeight = Math.max(480, PADDING + 90 + gridDim.getPixelHeight() + PADDING + 80);

        this.contentWidth = Math.min(this.contentWidth, this.width - 40);
        this.contentHeight = Math.min(this.contentHeight, this.height - 40);

        this.leftPos = (this.width - contentWidth) / 2;
        this.topPos = (this.height - contentHeight) / 2;

        int rightPanelX = leftPos + contentWidth - 150;

        if (slotManager == null) {
            slotManager = new SlotManager(leftPos + PADDING, topPos, rightPanelX);
            updateSlotManagerRecipeType();
        } else {
            // 检查网格尺寸是否改变
            SlotManager.GridDimensions oldDim = slotManager.getGridDimensions();
            if (oldDim == null ||
                    oldDim.getPixelWidth() != gridDim.getPixelWidth() ||
                    oldDim.getPixelHeight() != gridDim.getPixelHeight()) {

                slotManager = new SlotManager(leftPos + PADDING, topPos, rightPanelX);
                updateSlotManagerRecipeType();
            } else {
                // 网格尺寸没变，只更新坐标
                slotManager.updateCoordinates(leftPos + PADDING, topPos, rightPanelX);
                updateSlotManagerRecipeType();
            }
        }
    }

    /**
     * 更新SlotManager的配方类型
     */
    private void updateSlotManagerRecipeType() {
        if (slotManager == null) {
            ModLogger.getLogger().error("slotManager为null");
            return;
        }

        if (currentRecipeType == null) {
            ModLogger.getLogger().error("currentRecipeType为null");
            return;
        }
        boolean shouldPreserve = (pendingLoadResult == null);
        slotManager.setRecipeType(currentRecipeType, customTier, shouldPreserve);
    }

    /**
     * 从选择器加载配方
     */
    private void loadSelectedRecipe(ResourceLocation recipeId) {
        UnifiedRecipeInfo info = recipeLoader.findRecipeInfo(recipeId);
        if (info == null) {
            displayError("找不到配方信息: " + recipeId);
            return;
        }

        this.editingRecipeId = recipeId;
        this.isEditingExisting = true;

        RecipeLoader.LoadResult result = recipeLoader.loadRecipe(recipeId);
        if (!result.success) {
            displayError(result.message);
            return;
        }

        RecipeTypeDefinition loadedType = findRecipeTypeDefinition(result);
        if (loadedType == null) {
            displayError("无法识别配方类型: " + result.originalRecipeTypeId + " 类型：" + currentRecipeType.getId());
            return;
        }

        int inferredTier = inferTierFromIngredientCount(result.ingredients.size(), loadedType);
        if (inferredTier != result.avaritiaTeir) {
            result.avaritiaTeir = inferredTier;
        }

        boolean typeChanged = !loadedType.getId().equals(this.currentRecipeType.getId());
        boolean tierChanged = result.avaritiaTeir != this.customTier;

        if (typeChanged || tierChanged) {
            this.currentRecipeType = loadedType;
            this.currentCraftingMode = result.craftingMode != null ?
                    result.craftingMode.name().toLowerCase() : "shaped";
            this.currentCookingType = result.cookingType != null ?
                    result.cookingType.name().toLowerCase() : "smelting";
            this.customTier = result.avaritiaTeir;

            this.slotManager = null;
            this.pendingLoadResult = result;

            this.clearWidgets();
            this.init();

        } else {
            // 类型没变，直接应用材料
            this.currentCraftingMode = result.craftingMode != null ?
                    result.craftingMode.name().toLowerCase() : "shaped";
            this.currentCookingType = result.cookingType != null ?
                    result.cookingType.name().toLowerCase() : "smelting";

            if (craftingModeButton != null) {
                craftingModeButton.setValue(currentCraftingMode);
            }
            if (cookingTypeButton != null) {
                cookingTypeButton.setValue(currentCookingType);
            }
            if (tierButton != null) {
                tierButton.setValue(customTier);
            }

            applyLoadedRecipe(result);
        }

        String buttonText = "更新配方";
        if (info.hasOverride || (!recipeLoader.isCustomRecipe(recipeId))) {
            buttonText += " (覆盖)";
        }
        if (createButton != null) {
            createButton.setMessage(Component.literal(buttonText));
        }

        displayInfo("已载入 " + info.description);
    }

    /**
     * 根据材料数量推断tier
     */
    private int inferTierFromIngredientCount(int ingredientCount, RecipeTypeDefinition recipeType) {
        if (!Boolean.TRUE.equals(recipeType.getProperty("supportsTiers", Boolean.class))) {
            return 1;
        }
        return DynamicRecipeBuilder.getTierFromIngredientCount(ingredientCount);
    }

    /**
     * 配方类型更改处理
     */
    private void onRecipeTypeChanged(RecipeTypeDefinition newType) {
        if (newType == null) {
            ModLogger.getLogger().error("新配方类型为null，取消切换");
            return;
        }

        // 保存数据
        ItemStack currentResult = slotManager != null ? slotManager.getResultItem() : ItemStack.EMPTY;
        List<ItemStack> currentIngredients = slotManager != null ?
                new ArrayList<>(slotManager.getIngredients()) : new ArrayList<>();
        String currentResultCount = resultCountBox != null ? resultCountBox.getValue() : "1";

        // 设置新类型
        this.currentRecipeType = newType;

        Integer defaultTier = newType.getProperty("tier", Integer.class);
        if (defaultTier != null) {
            this.customTier = defaultTier;
        }

        String mode = newType.getProperty("mode", String.class);
        if (mode != null) {
            this.currentCraftingMode = mode;
        }

        // 完全重建界面
        this.clearWidgets();
        this.init();
        // 恢复数据
        if (slotManager != null && !currentResult.isEmpty()) {
            slotManager.setResultItem(currentResult);

            int newSlotCount = slotManager.getIngredientSlots().size();
            for (int i = 0; i < Math.min(currentIngredients.size(), newSlotCount); i++) {
                if (!currentIngredients.get(i).isEmpty()) {
                    slotManager.setIngredient(i, currentIngredients.get(i));
                }
            }
        }

        if (resultCountBox != null) {
            resultCountBox.setValue(currentResultCount);
        }

        // 同步到渲染器
        syncDataToRenderer();
    }

    /**
     * 初始化组件渲染器 - 修复版
     */
    private void initializeComponentRenderers() {
        if (slotManager == null) {
            ModLogger.getLogger().warn("slotManager为空，无法初始化渲染器");
            return;
        }

        if (componentRenderManager == null) {
            ModLogger.getLogger().warn("componentRenderManager为空，无法初始化渲染器");
            return;
        }

        List<RecipeComponent> components = slotManager.getComponents();
        if (components == null || components.isEmpty()) {
            componentRenderManager.clear();
            //ModLogger.getLogger().warn("components为空，无法初始化渲染器");
            return;
        }

        // 设置回调
        componentRenderManager.setSlotCallbacks(
                this::openItemSelectorForSlot,
                slotManager::clearSlot
        );
        componentRenderManager.setResultCallback(this::openResultSelector);

        // 初始化渲染器
        componentRenderManager.initializeRenderers(components);

        // 注册EditBox
        for (EditBox editBox : componentRenderManager.getEditBoxes()) {
            addRenderableWidget(editBox);
        }

        // 同步当前数据
        syncDataToRenderer();
    }

    /**
     * 应用已加载的配方数据
     */
    private void applyLoadedRecipe(RecipeLoader.LoadResult result) {
        if (resultCountBox != null) {
            resultCountBox.setValue(String.valueOf(result.resultItem.getCount()));
        }

        // 设置材料和结果到 slotManager
        if (slotManager != null) {
            // 将ItemStack列表转换为IngredientData列表
            slotManager.setIngredients(result.ingredients); // 这会自动转换
            slotManager.setResultItem(result.resultItem);
        }
        syncDataToRenderer();
    }

    /**
     * 同步数据到渲染器
     */
    private void syncDataToRenderer() {
        if (componentRenderManager == null) {
            ModLogger.getLogger().warn("componentRenderManager 为空，无法同步");
            return;
        }

        if (slotManager == null) {
            ModLogger.getLogger().warn("slotManager 为空，无法同步");
            return;
        }

        // 同步所有槽位物品
        List<ItemStack> ingredients = slotManager.getIngredients();
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack item = ingredients.get(i);
            componentRenderManager.updateSlotItem(i, item);
        }

        // 同步结果物品
        ItemStack result = slotManager.getResultItem();
        componentRenderManager.updateResultItem(result);
    }

    /**
     * 根据加载结果查找配方类型定义
     */
    private RecipeTypeDefinition findRecipeTypeDefinition(RecipeLoader.LoadResult result) {
        // 优先使用原始配方类型ID
        if (result.originalRecipeTypeId != null && !result.originalRecipeTypeId.isEmpty()) {
            RecipeTypeDefinition found = DynamicRecipeTypeConfig.getRecipeType(result.originalRecipeTypeId);
            if (found != null) {
                return found;
            }
            found = findByProcessorSupport(result.originalRecipeTypeId);
            if (found != null) {
                return found;
            }
            if (result.originalRecipeTypeId.contains("shaped_table")) {
                found = DynamicRecipeTypeConfig.getRecipeType("avaritia_shaped");
                if (found != null) {
                    return found;
                }
            } else if (result.originalRecipeTypeId.contains("shapeless_table")) {
                found = DynamicRecipeTypeConfig.getRecipeType("avaritia_shapeless");
                if (found != null) {
                    return found;
                }
            }
        }

        // 降级到枚举类型匹配
        if (result.recipeType != null) {
            String recipeTypeName = result.recipeType.name().toLowerCase();

            if ("crafting".equals(recipeTypeName)) {
                String mode = result.craftingMode != null ? result.craftingMode.name().toLowerCase() : "shaped";
                String typeId = "crafting_" + mode;
                return DynamicRecipeTypeConfig.getRecipeType(typeId);

            } else if ("cooking".equals(recipeTypeName)) {
                String cookingType = result.cookingType != null ? result.cookingType.name().toLowerCase() : "smelting";
                return DynamicRecipeTypeConfig.getRecipeType(cookingType);

            } else if ("avaritia".equals(recipeTypeName)) {
                String mode = result.craftingMode != null ? result.craftingMode.name().toLowerCase() : "shaped";
                String typeId = "avaritia_" + mode;

                RecipeTypeDefinition found = DynamicRecipeTypeConfig.getRecipeType(typeId);
                if (found != null) {
                    return found;
                }

                // 如果没找到，尝试查找原始ID（移除 ":crafting_table_recipe" 后缀）
                if (result.originalRecipeTypeId != null) {
                    String namespace = result.originalRecipeTypeId.split(":")[0];
                    if ("avaritia".equals(namespace)) {
                        // 尝试直接使用 "avaritia_shaped"
                        return DynamicRecipeTypeConfig.getRecipeType("avaritia_shaped");
                    }
                }
                return found;

            } else if ("brewing".equals(recipeTypeName)) {
                return DynamicRecipeTypeConfig.getRecipeType("brewing");

            } else if ("stonecutting".equals(recipeTypeName)) {
                return DynamicRecipeTypeConfig.getRecipeType("stonecutting");

            } else if ("smithing".equals(recipeTypeName) || "smithing_transform".equals(recipeTypeName)) {
                return DynamicRecipeTypeConfig.getRecipeType("smithing_transform");
            }
        }
        return null;
    }

    /**
     * 通过 processor 的 supportedRecipeTypes 查找配方类型定义
     */
    private RecipeTypeDefinition findByProcessorSupport(String recipeTypeId) {
        for (RecipeTypeDefinition definition : DynamicRecipeTypeConfig.getAvailableRecipeTypes()) {
            ModRecipeProcessor processor = definition.getProcessor();
            if (processor != null) {
                String[] supportedTypes = processor.getSupportedRecipeTypes();
                if (supportedTypes != null) {
                    for (String supportedType : supportedTypes) {
                        String fullType = supportedType.contains(":") ? supportedType : definition.getModId() + ":" + supportedType;
                        if (recipeTypeId.equals(fullType) || recipeTypeId.endsWith(":" + supportedType)) {
                            return definition;
                        }
                    }
                }
            }
        }
        return null;
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

        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(buttonStartX, buttonY, buttonWidths[5], 20)
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
            List<RecipeTypeDefinition> availableTypes = DynamicRecipeTypeConfig.getAvailableDisplayRecipeTypes();
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
            // 打开材料类型选择器
            minecraft.setScreen(new IngredientTypeSelector(this, slotIndex, selectionType -> {
                handleIngredientTypeSelection(slotIndex, selectionType);
            }));
        }
    }

    private void openBrushSelector() {
        if (minecraft != null) {
            minecraft.setScreen(new IngredientTypeSelector(this, -1, this::handleBrushSelection));
        }
    }

    private void handleBrushSelection(IngredientTypeSelector.SelectionType type) {
        if (minecraft == null) return;

        switch (type) {
            case ALL_ITEMS -> minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                fillModeHandler.setBrushItem(item);
                displayInfo("已设置画笔物品: " + item.getHoverName().getString());
            }));
            case INVENTORY -> minecraft.setScreen(new InventoryItemSelectorScreen(this, item -> {
                fillModeHandler.setBrushItem(item);
                displayInfo("已设置画笔物品: " + item.getHoverName().getString());
            }));
            case TAG, CUSTOM_TAG -> {
                displayError("画笔模式不支持标签");
                minecraft.setScreen(this);
            }
        }
    }

    private void openResultSelector() {
        if (minecraft != null) {
            minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                slotManager.setResultItem(item);
                // 同步到渲染器
                if (componentRenderManager != null) {
                    componentRenderManager.updateResultItem(item);
                }
            }));
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
            // 检查是否为远程服务器且缓存为空
            if (RecipeLoader.isRemoteServer()) {
                if (!com.wzz.registerhelper.network.RecipeClientCache.isLoaded()) {
                    displayInfo("正在从服务器加载配方列表，请稍候...");
                    recipeLoader.requestServerRecipes();
                    // 添加回调，数据加载完成后重新打开选择器
                    com.wzz.registerhelper.network.RecipeClientCache.addLoadCallback(recipes -> {
                        if (minecraft != null) {
                            minecraft.execute(() -> {
                                if (!recipes.isEmpty()) {
                                    List<UnifiedRecipeInfo> editableRecipes = new ArrayList<>();
                                    for (UnifiedRecipeInfo info : recipes) {
                                        if (!info.isBlacklisted) {
                                            editableRecipes.add(info);
                                        }
                                    }
                                    if (!editableRecipes.isEmpty()) {
                                        minecraft.setScreen(new RecipeSelectorScreen(this, this::loadSelectedRecipe,
                                                editableRecipes, "选择要编辑的配方"));
                                    } else {
                                        displayError("没有找到可编辑的配方");
                                    }
                                } else {
                                    displayError("没有找到可编辑的配方");
                                }
                            });
                        }
                    });
                    return;
                }
            }

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
            // 检查是否为远程服务器且缓存为空
            if (RecipeLoader.isRemoteServer()) {
                if (!com.wzz.registerhelper.network.RecipeClientCache.isLoaded()) {
                    displayInfo("正在从服务器加载配方列表，请稍候...");
                    recipeLoader.requestServerRecipes();
                    // 添加回调，数据加载完成后重新打开选择器
                    com.wzz.registerhelper.network.RecipeClientCache.addLoadCallback(recipes -> {
                        if (minecraft != null) {
                            minecraft.execute(() -> {
                                if (!recipes.isEmpty()) {
                                    minecraft.setScreen(new RecipeSelectorScreen(this, this::handleRecipeOperation,
                                            new ArrayList<>(recipes), "选择要操作的配方"));
                                } else {
                                    displayError("没有找到任何配方");
                                }
                            });
                        }
                    });
                    return;
                }
            }

            List<UnifiedRecipeInfo> allRecipes = recipeLoader.getAllRecipes();
            if (allRecipes.isEmpty()) {
                displayError("没有找到任何配方");
                return;
            }
            minecraft.setScreen(new RecipeSelectorScreen(this, this::handleRecipeOperation,
                    allRecipes, "选择要操作的配方"));
        }
    }

    /**
     * 处理材料类型选择
     */
    private void handleIngredientTypeSelection(int slotIndex, IngredientTypeSelector.SelectionType type) {
        if (minecraft == null) return;

        switch (type) {
            case ALL_ITEMS -> {
                // 从所有物品选择（原有功能）
                minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                    IngredientData data = IngredientData.fromItem(item);
                    slotManager.setIngredientData(slotIndex, data);
                    // 同步到渲染器
                    if (componentRenderManager != null) {
                        componentRenderManager.updateSlotItem(slotIndex, item);
                    }
                }));
            }
            case INVENTORY -> {
                // 从背包选择（带NBT）
                minecraft.setScreen(new InventoryItemSelectorScreen(this, item -> {
                    IngredientData data = IngredientData.fromItem(item);
                    slotManager.setIngredientData(slotIndex, data);
                    // 同步到渲染器
                    if (componentRenderManager != null) {
                        componentRenderManager.updateSlotItem(slotIndex, item);
                    }
                }));
            }
            case TAG -> {
                // 选择标签
                minecraft.setScreen(new TagSelectorScreen(this, tagId -> {
                    IngredientData data = IngredientData.fromTag(tagId);
                    slotManager.setIngredientData(slotIndex, data);
                    // 同步到渲染器（使用标签的第一个物品显示）
                    if (componentRenderManager != null) {
                        componentRenderManager.updateSlotItem(slotIndex, data.getDisplayStack());
                    }
                    displayInfo("已添加标签: #" + tagId);
                }));
            }
            case CUSTOM_TAG -> {
                // 创建自定义标签
                minecraft.setScreen(new CustomTagCreatorScreen(this, (tagId, items) -> {
                    CustomTagManager.registerTag(tagId, items);
                    IngredientData data = IngredientData.fromCustomTag(tagId, items);
                    slotManager.setIngredientData(slotIndex, data);
                    // 同步到渲染器（使用第一个物品显示）
                    if (componentRenderManager != null) {
                        componentRenderManager.updateSlotItem(slotIndex, data.getDisplayStack());
                    }
                    displayInfo("已添加自定义标签: #" + tagId + " (包含 " + items.size() + " 个物品)");
                }));
            }
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
                // 使用网络包辅助类
                success = BlacklistClientHelper.removeFromBlacklist(recipeId);
                resultMessage = success ? "正在恢复配方" : "恢复配方失败";
            } else if (info.hasOverride) {
                success = UnifiedRecipeOverrideManager.removeOverride(recipeId);
                resultMessage = success ? "覆盖已移除" : "移除覆盖失败";
            } else {
                // 使用网络包辅助类
                success = BlacklistClientHelper.addToBlacklist(recipeId);
                resultMessage = success ? "正在禁用配方" : "禁用配方失败";
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

            DynamicRecipeBuilder.BuildParams params = getBuildParams(resultItem, cookingTime, cookingExp);
            DynamicRecipeBuilder dynamicBuilder = new DynamicRecipeBuilder(this::displaySuccess, this::displayError);
            dynamicBuilder.buildRecipe(params);

        } catch (NumberFormatException e) {
            displayError("请输入有效的数量、时间或经验值！");
        }
    }

    private DynamicRecipeBuilder.BuildParams getBuildParams(ItemStack resultItem, float cookingTime, float cookingExp) {
        Map<String, Object> componentData = new HashMap<>();
        if (componentRenderManager != null) {
            componentData = componentRenderManager.getDataManager().getAllData();
        }

        List<IngredientData> ingredientsData = slotManager.getIngredientsData();

        List<ItemStack> ingredients = slotManager.getIngredients();
        DynamicRecipeBuilder.BuildParams buildParams = new DynamicRecipeBuilder.BuildParams(
                currentRecipeType,
                currentCraftingMode,
                currentCookingType,
                customTier,
                resultItem,
                ingredients,
                ingredientsData,
                cookingTime,
                cookingExp,
                editingRecipeId,
                isEditingExisting,
                componentData
        );
        buildParams.componentDataManager = componentRenderManager.getDataManager();
        return buildParams;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // 绘制主背景
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + contentHeight, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + contentWidth - 1, topPos + contentHeight - 1, 0xFF8B8B8B);

        // 绘制标题
        String titleText = isEditingExisting ?
                (editingRecipeId != null ? "配方编辑器 - " + editingRecipeId : "配方编辑器")
                : "配方创建器";
        guiGraphics.drawCenteredString(this.font, titleText, leftPos + contentWidth / 2, topPos + 15, 0x404040);

        renderLabels(guiGraphics);

        int rightPanelX = leftPos + contentWidth - 150 + 10;
        guiGraphics.fill(rightPanelX - 20, topPos + 20, rightPanelX - 18, topPos + contentHeight - 60, 0xFF606060);

        // 使用组件渲染器渲染
        if (componentRenderManager != null && !slotManager.getComponents().isEmpty()) {
            componentRenderManager.renderAll(guiGraphics, mouseX, mouseY);
            // 如果使用组件渲染器，还需要手动渲染结果槽
            renderResultSlot(guiGraphics, mouseX, mouseY);
        } else {
            renderSlots(guiGraphics, mouseX, mouseY);
        }

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

            if (currentRecipeType.isAvaritiaType() || Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
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

    private void renderResultSlot(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (slotManager == null) return;
        renderSlot(guiGraphics, slotManager.getResultSlot(), mouseX, mouseY, slotManager.getResultItem());
    }

    private void renderSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 渲染材料槽位
        for (int i = 0; i < slotManager.getIngredientSlots().size(); i++) {
            SlotManager.IngredientSlot slot = slotManager.getIngredientSlots().get(i);
            IngredientData data = slotManager.getIngredientData(i);
            ItemStack displayItem = data != null ? data.getDisplayStack() : ItemStack.EMPTY;
            renderSlot(guiGraphics, slot, mouseX, mouseY, displayItem);
        }

        // 渲染结果槽位
        renderSlot(guiGraphics, slotManager.getResultSlot(), mouseX, mouseY, slotManager.getResultItem());
    }

    private void renderSlot(GuiGraphics guiGraphics, SlotManager.IngredientSlot slot,
                            int mouseX, int mouseY, ItemStack displayItem) {
        boolean isMouseOver = mouseX >= slot.x() && mouseX < slot.x() + 18 &&
                mouseY >= slot.y() && mouseY < slot.y() + 18;

        int bgColor = isMouseOver ? 0x80FFFFFF : 0xFF373737;
        guiGraphics.fill(slot.x(), slot.y(), slot.x() + 18, slot.y() + 18, bgColor);

        // 边框
        guiGraphics.fill(slot.x() - 1, slot.y() - 1, slot.x() + 19, slot.y(), 0xFF000000);
        guiGraphics.fill(slot.x() - 1, slot.y() + 18, slot.x() + 19, slot.y() + 19, 0xFF000000);
        guiGraphics.fill(slot.x() - 1, slot.y(), slot.x(), slot.y() + 18, 0xFF000000);
        guiGraphics.fill(slot.x() + 18, slot.y(), slot.x() + 19, slot.y() + 18, 0xFF000000);

        // 获取对应的IngredientData
        int slotIndex = slot.index();
        IngredientData data = null;

        if (slotIndex >= 0 && slotManager != null) {
            data = slotManager.getIngredientData(slotIndex);
        }

        // 渲染物品或标签图标
        if (data != null && !data.isEmpty()) {
            ItemStack stackToRender = data.getDisplayStack();

            if (!stackToRender.isEmpty()) {
                RenderSystem.enableDepthTest();
                guiGraphics.renderItem(stackToRender, slot.x() + 1, slot.y() + 1);
                RenderSystem.disableDepthTest();
            }

            // 在右上角显示类型指示器
            switch (data.getType()) {
                case TAG -> {
                    // 标签：金色#标记 + 半透明金色背景
                    guiGraphics.fill(slot.x() + 10, slot.y() + 1, slot.x() + 18, slot.y() + 9, 0x80FFD700);
                    guiGraphics.drawString(this.font, "§6§l#", slot.x() + 11, slot.y() + 1, 0xFFFFFF, true);
                }
                case CUSTOM_TAG -> {
                    // 自定义标签：青色#标记 + 半透明青色背景
                    guiGraphics.fill(slot.x() + 10, slot.y() + 1, slot.x() + 18, slot.y() + 9, 0x8000FFFF);
                    guiGraphics.drawString(this.font, "§b§l#", slot.x() + 11, slot.y() + 1, 0xFFFFFF, true);
                }
                case ITEM -> {
                    if (data.hasNBT()) {
                        // 带NBT：紫色*标记 + 半透明紫色背景
                        guiGraphics.fill(slot.x() + 10, slot.y() + 1, slot.x() + 18, slot.y() + 9, 0x80FF00FF);
                        guiGraphics.drawString(this.font, "§d§l*", slot.x() + 12, slot.y() + 1, 0xFFFFFF, true);
                    }
                }
            }
        } else if (!displayItem.isEmpty()) {
            // 兼容旧代码：直接显示ItemStack
            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(displayItem, slot.x() + 1, slot.y() + 1);
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

                IngredientData data = slotManager.getIngredientData(i);

                if (!data.isEmpty()) {
                    List<Component> tooltip = new ArrayList<>();

                    // 根据类型显示不同的工具提示
                    switch (data.getType()) {
                        case ITEM -> {
                            ItemStack stack = data.getItemStack();
                            guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
                            return;
                        }
                        case TAG -> {
                            tooltip.add(Component.literal("§6§l[标签材料]"));
                            tooltip.add(Component.literal("§e#" + data.getTagId()));

                            // 显示标签包含的物品示例
                            ItemStack displayItem = data.getDisplayStack();
                            if (!displayItem.isEmpty()) {
                                tooltip.add(Component.literal("§7示例: " + displayItem.getHoverName().getString()));
                            }

                            tooltip.add(Component.literal("§8匹配该标签的所有物品"));
                        }
                        case CUSTOM_TAG -> {
                            tooltip.add(Component.literal("§b§l[自定义标签]"));
                            tooltip.add(Component.literal("§3#" + data.getTagId()));

                            List<ItemStack> items = data.getCustomTagItems();
                            tooltip.add(Component.literal("§7包含 " + items.size() + " 个物品:"));

                            // 显示前3个物品
                            int showCount = Math.min(3, items.size());
                            for (int j = 0; j < showCount; j++) {
                                tooltip.add(Component.literal("  §8• " +
                                        items.get(j).getHoverName().getString()));
                            }

                            if (items.size() > 3) {
                                tooltip.add(Component.literal("  §8... 还有 " +
                                        (items.size() - 3) + " 个物品"));
                            }
                        }
                    }

                    tooltip.add(Component.literal("")); // 空行
                    tooltip.add(Component.literal("§7左键: 修改材料"));
                    tooltip.add(Component.literal("§7右键: 清空槽位"));

                    guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                } else {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal("§7空槽位"));
                    tooltip.add(Component.literal("§8左键选择材料类型"));
                    guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                }
                return;
            }
        }

        // 结果槽位工具提示
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
        // 优先处理材料槽的填充模式
        if (slotManager != null) {
            for (int i = 0; i < slotManager.getIngredientSlots().size(); i++) {
                SlotManager.IngredientSlot slot = slotManager.getIngredientSlots().get(i);
                if (mouseX >= slot.x() && mouseX < slot.x() + 18 &&
                        mouseY >= slot.y() && mouseY < slot.y() + 18) {
                    // 使用填充模式处理
                    fillModeHandler.handleSlotClick(slotManager, i, button == 1);
                    // 同步到渲染器
                    if (componentRenderManager != null) {
                        List<ItemStack> ingredients = slotManager.getIngredients();
                        if (i < ingredients.size()) {
                            componentRenderManager.updateSlotItem(i, ingredients.get(i));
                        }
                    }
                    return true;
                }
            }
        }

        // 处理结果槽点击
        if (slotManager != null) {
            SlotManager.IngredientSlot resultSlot = slotManager.getResultSlot();
            if (mouseX >= resultSlot.x() && mouseX < resultSlot.x() + 18 &&
                    mouseY >= resultSlot.y() && mouseY < resultSlot.y() + 18) {
                if (button == 0) {
                    openResultSelector();
                } else if (button == 1) {
                    slotManager.setResultItem(ItemStack.EMPTY);
                    if (componentRenderManager != null) {
                        componentRenderManager.updateResultItem(ItemStack.EMPTY);
                    }
                }
                return true;
            }
        }

        // 其他组件的点击处理
        if (componentRenderManager != null &&
                componentRenderManager.handleMouseClick(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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