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
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class RecipeCreatorScreen extends Screen {

    private static final int PADDING = 20;
    private static final int SCREEN_MARGIN = 10;
    private static final int MIN_CONTENT_WIDTH = 400;
    private static final int MIN_CONTENT_HEIGHT = 260;
    private static final int PREFERRED_MIN_WIDTH = 560;
    private static final int PREFERRED_MIN_HEIGHT = 480;
    private static final int PREFERRED_RIGHT_PANEL_WIDTH = 150;
    private static final int MIN_RIGHT_PANEL_WIDTH = 110;
    private static final int MAX_RIGHT_PANEL_WIDTH = 180;
    private static final int GRID_TOP_OFFSET = 150;
    private static final int FOOTER_HEIGHT = 35;

    // 核心组件
    private SlotManager slotManager;
    private FillModeHandler fillModeHandler;
    private RecipeLoader recipeLoader;

    // 动态尺寸变量
    private int contentWidth;
    private int contentHeight;
    private int leftPos, topPos;
    private int rightPanelWidth = PREFERRED_RIGHT_PANEL_WIDTH;
    private int rightPanelX;
    private int rightPanelStartY;
    private int rightPanelRowGap = 30;
    private int gridTopOffset = GRID_TOP_OFFSET;
    private int secondaryControlX;
    private int tierControlX;

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
    private ComponentRenderManager componentRenderManager;
    private boolean resetControlValuesOnInit;
    private boolean menuOpen = false;
    /** 下拉菜单的屏幕坐标（render 时计算，mouseClicked 时判断） */
    private int menuBtnX, menuBtnY, menuBtnW = 68;

    /** 下拉菜单三个选项的标签/动作 */
    private static final String[] MENU_LABELS = {
            "registerhelper.gui.recipe_creator.blacklist_manager",
            "registerhelper.gui.recipe_creator.override_manager",
            "registerhelper.gui.recipe_creator.add_blacklist"};
    private static final int        MENU_ITEM_H  = 18;

    // 构造函数
    public RecipeCreatorScreen() {
        super(GuiText.component("registerhelper.gui.recipe_creator.title"));
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
        super(GuiText.component("registerhelper.gui.recipe_creator.editor_title"));
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
     * 获取网格尺寸（含当前自适应 spacing）。
     * 对支持 Tier 的配方类型，使用当前 customTier 对应的实际尺寸，
     * 确保 calculateDynamicSize 能正确计算窗口高度，避免按钮遮挡格子。
     */
    private SlotManager.GridDimensions getGridDimensions() {
        if (currentRecipeType == null) {
            return new SlotManager.GridDimensions(3, 3);
        }

        int gridWidth, gridHeight;

        // Tier 动态类型：用当前 customTier 对应的实际尺寸
        if (currentRecipeType.isAvaritiaType() ||
                Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
            int gridSize = DynamicRecipeBuilder.getGridSizeForTier(customTier);
            gridWidth = gridSize;
            gridHeight = gridSize;
        } else {
            // 固定尺寸类型（包括 ArcaneVortex 16x16 等大型固定配方台）
            gridWidth  = currentRecipeType.getMaxGridWidth();
            gridHeight = currentRecipeType.getMaxGridHeight();
        }

        // 无论是动态 Tier 还是固定大格子，都需要计算自适应间距，
        // 否则 16x16 固定格子在小屏幕上同样会溢出遮住按钮。
        int spacing = computeAdaptiveSpacing(gridWidth, gridHeight);
        return new SlotManager.GridDimensions(gridWidth, gridHeight, spacing);
    }

    /**
     * 根据屏幕可用高度，为指定 gridSize 计算合适的槽位间距。
     * <p>
     * 垂直方向布局：
     *   - 顶部控件区（含标题）：150px
     *   - 格子区：gridSize * spacing
     *   - 底部按钮区：35px
     *   - 屏幕上下留边：40px
     * <p>
     * 因此：spacing = (screenH - 40 - 150 - 35) / gridSize
     * 结果钳制到 [MIN_SLOT_SPACING, DEFAULT_SLOT_SPACING]。
     */
    private int computeAdaptiveSpacing(int gridWidth, int gridHeight) {
        if (this.width == 0 || this.height == 0 || gridWidth <= 0 || gridHeight <= 0) {
            return SlotManager.DEFAULT_SLOT_SPACING;
        }
        int availableWidth = this.width - SCREEN_MARGIN * 2
                - PADDING * 3 - MIN_RIGHT_PANEL_WIDTH;
        int responsiveGridTop = GuiLayoutHelper.clamp(
                (this.height - SCREEN_MARGIN * 2) * 45 / 100, 100, GRID_TOP_OFFSET);
        int availableHeight = this.height - SCREEN_MARGIN * 2
                - responsiveGridTop - FOOTER_HEIGHT - PADDING;
        int spacing = Math.min(availableWidth / gridWidth, availableHeight / gridHeight);
        return Math.max(SlotManager.MIN_SLOT_SPACING,
                Math.min(SlotManager.DEFAULT_SLOT_SPACING, spacing));
    }

    @Override
    protected void init() {
        String savedResultCount = !resetControlValuesOnInit && resultCountBox != null
                ? resultCountBox.getValue() : null;
        String savedCookingTime = !resetControlValuesOnInit && cookingTimeBox != null
                ? cookingTimeBox.getValue() : null;
        String savedCookingExp = !resetControlValuesOnInit && cookingExpBox != null
                ? cookingExpBox.getValue() : null;
        if (componentRenderManager != null) {
            if (resetControlValuesOnInit) {
                componentRenderManager.clear();
            } else {
                componentRenderManager.clearRenderers();
            }
        }

        // 按顺序调用
        calculateDynamicSize();
        initializeControls();
        if (pendingLoadResult != null && pendingLoadResult.success
                && componentRenderManager != null) {
            componentRenderManager.getDataManager().putAll(pendingLoadResult.componentData);
        }
        initializeComponentRenderers();

        // 应用待加载的配方数据
        boolean appliedPendingData = pendingLoadResult != null && pendingLoadResult.success;
        if (pendingLoadResult != null && pendingLoadResult.success) {
            applyLoadedRecipe(pendingLoadResult);
            pendingLoadResult = null;
        }

        updateVisibility();
        if (!appliedPendingData) {
            if (savedResultCount != null) resultCountBox.setValue(savedResultCount);
            if (savedCookingTime != null) cookingTimeBox.setValue(savedCookingTime);
            if (savedCookingExp != null) cookingExpBox.setValue(savedCookingExp);
        }
        resetControlValuesOnInit = false;
    }

    /**
     * 计算动态尺寸
     */
    private void calculateDynamicSize() {
        if (currentRecipeType == null) {
            ModLogger.getLogger().error("calculateDynamicSize: currentRecipeType为null");
            return;
        }

        // 构造函数里 width/height 尚未初始化，跳过
        if (this.width == 0 || this.height == 0) return;

        // 获取当前配方类型的网格尺寸（已内含自适应 spacing）
        SlotManager.GridDimensions gridDim = getGridDimensions();

        int preferredWidth = Math.max(PREFERRED_MIN_WIDTH,
                PADDING * 3 + gridDim.getPixelWidth() + PREFERRED_RIGHT_PANEL_WIDTH);
        int preferredHeight = Math.max(PREFERRED_MIN_HEIGHT,
                GRID_TOP_OFFSET + gridDim.getPixelHeight() + FOOTER_HEIGHT + PADDING);
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(this.width, this.height,
                preferredWidth, preferredHeight, MIN_CONTENT_WIDTH, MIN_CONTENT_HEIGHT,
                SCREEN_MARGIN, SCREEN_MARGIN);
        this.contentWidth = panel.width();
        this.contentHeight = panel.height();
        this.leftPos = panel.x();
        this.topPos = panel.y();
        int responsiveMinRightWidth = Math.min(MIN_RIGHT_PANEL_WIDTH,
                Math.max(90, contentWidth / 4));
        this.rightPanelWidth = GuiLayoutHelper.clamp(contentWidth / 4,
                responsiveMinRightWidth, MAX_RIGHT_PANEL_WIDTH);
        this.rightPanelX = leftPos + contentWidth - rightPanelWidth;
        this.gridTopOffset = GuiLayoutHelper.clamp(
                contentHeight * 45 / 100, 100, GRID_TOP_OFFSET);
        this.rightPanelStartY = topPos + GuiLayoutHelper.clamp(
                contentHeight * 28 / 100, 95, 130);
        int rightPanelBottom = topPos + contentHeight - FOOTER_HEIGHT - 4;
        this.rightPanelRowGap = GuiLayoutHelper.clamp(
                (rightPanelBottom - rightPanelStartY - 20) / 3, 20, 30);
        int layoutAreaWidth = Math.max(1,
                rightPanelX - (leftPos + PADDING) - PADDING);
        int layoutAreaHeight = Math.max(1,
                contentHeight - gridTopOffset - FOOTER_HEIGHT - PADDING);

        if (slotManager == null) {
            slotManager = new SlotManager(leftPos + PADDING, topPos, rightPanelX);
            slotManager.setSlotSpacing(gridDim.spacing());
            slotManager.setResultSlotOffset(rightPanelStartY - topPos);
            slotManager.setGridTopOffset(gridTopOffset);
            slotManager.setLayoutAreaSize(layoutAreaWidth, layoutAreaHeight);
            updateSlotManagerRecipeType();
        } else {
            // 检查网格尺寸是否改变
            SlotManager.GridDimensions oldDim = slotManager.getGridDimensions();
            if (oldDim == null ||
                    oldDim.getPixelWidth() != gridDim.getPixelWidth() ||
                    oldDim.getPixelHeight() != gridDim.getPixelHeight()) {

                List<IngredientData> previousIngredients = slotManager.getIngredientsData();
                ItemStack previousResult = slotManager.getResultItem().copy();
                slotManager = new SlotManager(leftPos + PADDING, topPos, rightPanelX);
                slotManager.setSlotSpacing(gridDim.spacing());
                slotManager.setResultSlotOffset(rightPanelStartY - topPos);
                slotManager.setGridTopOffset(gridTopOffset);
                slotManager.setLayoutAreaSize(layoutAreaWidth, layoutAreaHeight);
                updateSlotManagerRecipeType();
                slotManager.setIngredientsData(previousIngredients);
                slotManager.setResultItem(previousResult);
            } else {
                // 网格尺寸没变，只更新坐标和 spacing
                slotManager.setSlotSpacing(gridDim.spacing());
                slotManager.setResultSlotOffset(rightPanelStartY - topPos);
                slotManager.setGridTopOffset(gridTopOffset);
                slotManager.setLayoutAreaSize(layoutAreaWidth, layoutAreaHeight);
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
            displayError(GuiText.component("registerhelper.message.recipe.info_not_found", recipeId));
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
            displayError(GuiText.component("registerhelper.message.recipe.type_unrecognized",
                    result.originalRecipeTypeId, currentRecipeType.getId()));
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
            this.resetControlValuesOnInit = true;

            this.clearWidgets();
            this.init();

        } else {
            this.currentCraftingMode = result.craftingMode != null ?
                    result.craftingMode.name().toLowerCase() : "shaped";
            this.currentCookingType = result.cookingType != null ?
                    result.cookingType.name().toLowerCase() : "smelting";
            this.pendingLoadResult = result;
            this.resetControlValuesOnInit = true;
            this.clearWidgets();
            this.init();
        }

        String buttonKey = info.hasOverride || !recipeLoader.isCustomRecipe(recipeId)
                ? "registerhelper.gui.recipe_creator.update_override"
                : "registerhelper.gui.recipe_creator.update";
        if (createButton != null) {
            createButton.setMessage(GuiText.component(buttonKey));
        }

        displayInfo(GuiText.component("registerhelper.message.recipe.loaded", info.description));
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
        resetControlValuesOnInit = true;
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
            if (result.ingredientsData != null && !result.ingredientsData.isEmpty()) {
                slotManager.setIngredientsData(result.ingredientsData);
            } else {
                slotManager.setIngredients(result.ingredients);          // 回退
            }
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

            switch (recipeTypeName) {
                case "crafting" -> {
                    String mode = result.craftingMode != null ? result.craftingMode.name().toLowerCase() : "shaped";
                    String typeId = "crafting_" + mode;
                    return DynamicRecipeTypeConfig.getRecipeType(typeId);

                }
                case "cooking" -> {
                    String cookingType = result.cookingType != null ? result.cookingType.name().toLowerCase() : "smelting";
                    return DynamicRecipeTypeConfig.getRecipeType(cookingType);
                }
                case "avaritia" -> {
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

                }
                case "brewing" -> {
                    return DynamicRecipeTypeConfig.getRecipeType("brewing");
                }
                case "stonecutting" -> {
                    return DynamicRecipeTypeConfig.getRecipeType("stonecutting");
                }
                case "smithing", "smithing_transform" -> {
                    return DynamicRecipeTypeConfig.getRecipeType("smithing_transform");
                }
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
        int controlSpacing = 8;
        int mainAreaWidth = Math.max(160, rightPanelX - controlStartX - 12);
        int typeButtonWidth = GuiLayoutHelper.clamp(mainAreaWidth * 42 / 100,
                70, 140);
        int optionAreaWidth = Math.max(74, mainAreaWidth - typeButtonWidth - controlSpacing);
        int optionButtonWidth = GuiLayoutHelper.clamp(optionAreaWidth * 55 / 100,
                42, 80);
        int tierButtonWidth = Math.max(32,
                optionAreaWidth - optionButtonWidth - controlSpacing);
        int currentX = controlStartX;

        String currentTypeName = currentRecipeType != null
                ? currentRecipeType.getDisplayName()
                : GuiText.string("registerhelper.gui.recipe_creator.select_type");

        recipeTypeButton = addRenderableWidget(Button.builder(
                        Component.literal(currentTypeName + " ▼"),
                        button -> openRecipeTypeSelector())
                .bounds(currentX, controlY1, typeButtonWidth, 20)
                .build());
        recipeTypeButton.setMessage(Component.literal(currentTypeName + " ▼"));
        currentX += typeButtonWidth + controlSpacing;
        secondaryControlX = currentX;

        // 合成模式选择器（动态显示）
        craftingModeButton = addRenderableWidget(CycleButton.<String>builder(
                        mode -> Component.literal(getDisplayNameForMode(mode)))
                .withValues("shaped", "shapeless")
                .withInitialValue(currentCraftingMode)
                .displayOnlyValue()
                .create(currentX, controlY1, optionButtonWidth, 20,
                        GuiText.component("registerhelper.gui.recipe_creator.crafting_mode"), this::onCraftingModeChanged));

        // 烹饪类型选择器（动态显示）
        cookingTypeButton = addRenderableWidget(CycleButton.<String>builder(
                        type -> Component.literal(getDisplayNameForCookingType(type)))
                .withValues(getCookingTypes())
                .withInitialValue(currentCookingType)
                .displayOnlyValue()
                .create(currentX, controlY1, optionButtonWidth, 20,
                        GuiText.component("registerhelper.gui.recipe_creator.cooking_type"), this::onCookingTypeChanged));
        currentX += optionButtonWidth + controlSpacing;
        tierControlX = currentX;

        // 等级选择器（支持动态等级）
        tierButton = addRenderableWidget(CycleButton.<Integer>builder(
                        tier -> Component.literal("T" + tier))
                .withValues(getAvailableTiers())
                .withInitialValue(customTier)
                .displayOnlyValue()
                .create(currentX, controlY1, tierButtonWidth, 20,
                        GuiText.component("registerhelper.gui.recipe_creator.tier"), this::onTierChanged));

        // 第二行控件
        int controlY2 = topPos + 65;
        currentX = controlStartX;

        int fillModeWidth = Math.min(90, Math.max(64, mainAreaWidth / 4));
        fillModeButton = addRenderableWidget(CycleButton.<FillMode>builder(
                        mode -> Component.literal(mode.getDisplayName()))
                .withValues(FillMode.values())
                .withInitialValue(fillModeHandler.getCurrentMode())
                .displayOnlyValue()
                .create(currentX, controlY2, fillModeWidth, 20,
                        GuiText.component("registerhelper.gui.recipe_creator.fill_mode"), this::onFillModeChanged));
        currentX += fillModeWidth + controlSpacing;

        int brushWidth = Math.max(70, Math.min(120,
                rightPanelX - currentX - 12));
        selectBrushItemButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.recipe_creator.select_brush"),
                        button -> fillModeHandler.openBrushSelector())
                .bounds(currentX, controlY2, brushWidth, 20)
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
            case "shaped" -> GuiText.string("registerhelper.gui.recipe_creator.crafting_mode.shaped");
            case "shapeless" -> GuiText.string("registerhelper.gui.recipe_creator.crafting_mode.shapeless");
            default -> mode;
        };
    }

    /**
     * 获取烹饪类型显示名称
     */
    private String getDisplayNameForCookingType(String type) {
        return switch (type) {
            case "smelting" -> GuiText.string("registerhelper.recipe_type.minecraft.smelting");
            case "blasting" -> GuiText.string("registerhelper.recipe_type.minecraft.blasting");
            case "smoking" -> GuiText.string("registerhelper.recipe_type.minecraft.smoking");
            case "campfire_cooking" -> GuiText.string("registerhelper.recipe_type.minecraft.campfire");
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
     * 获取可用的等级列表。
     * 根据配方类型注册时的 maxGridWidth 反推最大 Tier，
     * 避免出现超出实际格子上限的无效 Tier。
     */
    private Integer[] getAvailableTiers() {
        if (currentRecipeType != null &&
                (currentRecipeType.isAvaritiaType() ||
                        Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class)))) {

            // 用已注册的最大格子数推算上限，例如：
            //   avaritia      9x9  → T4
            //   ArcaneVortex  16x16 → T6
            int maxGrid = currentRecipeType.getMaxGridWidth();
            int maxTier = DynamicRecipeBuilder.getMaxTierForGridSize(maxGrid);

            Integer[] tiers = new Integer[maxTier];
            for (int i = 0; i < maxTier; i++) {
                tiers[i] = i + 1;
            }
            return tiers;
        }
        return new Integer[]{1};
    }

    /**
     * 初始化右侧面板
     */
    private void initializeRightPanel() {
        int panelContentX = rightPanelX + 10;
        int labelWidth = Math.min(54, Math.max(36, rightPanelWidth / 3));
        int inputX = panelContentX + labelWidth;
        int inputWidth = Math.max(32, rightPanelWidth - labelWidth - 20);

        resultCountBox = new EditBox(this.font, inputX,
                rightPanelStartY + rightPanelRowGap, inputWidth, 20,
                GuiText.component("registerhelper.gui.recipe_creator.count"));
        GuiTheme.styleInput(resultCountBox);
        resultCountBox.setValue("1");
        resultCountBox.setFilter(text -> text.matches("\\d*") &&
                (text.isEmpty() || Integer.parseInt(text) <= 64));
        addRenderableWidget(resultCountBox);

        // 烹饪时间和经验输入框
        String defaultTime = getDefaultTimeForCurrentType();
        String defaultExp = getDefaultExpForCurrentType();

        cookingTimeBox = new EditBox(this.font, inputX,
                rightPanelStartY + rightPanelRowGap * 2, inputWidth, 20,
                GuiText.component("registerhelper.gui.recipe_creator.cooking_time"));
        GuiTheme.styleInput(cookingTimeBox);
        cookingTimeBox.setValue(defaultTime);
        cookingTimeBox.setFilter(text -> text.matches("\\d*") &&
                (text.isEmpty() || Integer.parseInt(text) <= 32000));
        addRenderableWidget(cookingTimeBox);

        cookingExpBox = new EditBox(this.font, inputX,
                rightPanelStartY + rightPanelRowGap * 3, inputWidth, 20,
                GuiText.component("registerhelper.gui.recipe_creator.cooking_exp"));
        GuiTheme.styleInput(cookingExpBox);
        cookingExpBox.setValue(defaultExp);
        cookingExpBox.setFilter(text -> text.matches("\\d*\\.?\\d*"));
        addRenderableWidget(cookingExpBox);

        boolean cookingType = currentRecipeType != null
                && currentRecipeType.supportsCookingSettings();
        int lastInputBottom = rightPanelStartY
                + rightPanelRowGap * (cookingType ? 3 : 1) + 20;
        int maxClearButtonY = topPos + contentHeight - FOOTER_HEIGHT - 24;
        int clearButtonY = Math.max(lastInputBottom + 4,
                Math.min(rightPanelStartY + rightPanelRowGap * 4, maxClearButtonY));
        int clearButtonWidth = Math.min(rightPanelWidth - 20,
                Math.max(64, this.font.width(GuiText.string("registerhelper.gui.recipe_creator.clear_ingredients")) + 14));
        clearAllButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.recipe_creator.clear_ingredients"),
                        button -> clearAllIngredients())
                .bounds(panelContentX, clearButtonY, clearButtonWidth, 20)
                .build());
        clearAllButton.visible = clearButtonY <= maxClearButtonY;

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
     * 初始化底部按钮（自适应单行/双行）
     */
    private void initializeBottomButtons() {
        int spacing = 6;
        int buttonHeight = 20;
        int buttonY = topPos + contentHeight - 28;
        Component createLabel = GuiText.component(isEditingExisting
                ? "registerhelper.gui.recipe_creator.update"
                : "registerhelper.gui.recipe_creator.create");
        Component[] labels = {
                GuiText.component("registerhelper.gui.recipe_creator.manage"),
                GuiText.component("registerhelper.gui.recipe_creator.edit"),
                GuiText.component("registerhelper.gui.recipe_creator.clone"),
                createLabel,
                GuiText.component("registerhelper.gui.common.cancel")};
        int[] widths = new int[labels.length];
        int widthSum = 0;
        for (int i = 0; i < labels.length; i++) {
            widths[i] = Math.max(38, this.font.width(labels[i].getString()) + 16);
            widthSum += widths[i];
        }
        int availableWidth = contentWidth - 20;
        int totalWidth = widthSum + spacing * (labels.length - 1);
        if (totalWidth > availableWidth) {
            spacing = 3;
            int compactWidth = Math.max(32,
                    (availableWidth - spacing * (labels.length - 1)) / labels.length);
            Arrays.fill(widths, compactWidth);
            totalWidth = compactWidth * labels.length + spacing * (labels.length - 1);
        }
        int startX = leftPos + (contentWidth - totalWidth) / 2;
        menuBtnW = widths[0];

        // ⚙管理▾（普通按钮，点击切换 menuOpen）
        menuBtnX = startX;
        menuBtnY = buttonY;
        addRenderableWidget(Button.builder(
                labels[0],
                btn -> menuOpen = !menuOpen
        ).bounds(startX, buttonY, menuBtnW, buttonHeight).build());
        startX += menuBtnW + spacing;

        // 编辑配方
        editExistingRecipeButton = makeBtn(labels[1], this::openRecipeSelector,
                startX, buttonY, widths[1]);
        startX += widths[1] + spacing;

        // 配方克隆（新）
        makeBtn(labels[2], this::openCloneWizard, startX, buttonY, widths[2]);
        startX += widths[2] + spacing;

        // 创建/更新
        createButton = makeBtn(createLabel, this::createRecipe, startX, buttonY, widths[3]);
        startX += widths[3] + spacing;

        // 取消
        cancelButton = makeBtn(labels[4], this::onClose, startX, buttonY, widths[4]);
    }

    private void openCloneWizard() {
        if (minecraft != null) {
            minecraft.setScreen(new RecipeCloneWizardScreen(this, this::loadSelectedRecipe));
        }
    }

    /** 简化按钮创建 */
    private Button makeBtn(Component label, Runnable action, int x, int y, int w) {
        return addRenderableWidget(Button.builder(label, btn -> action.run())
                .bounds(x, y, w, 20).build());
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
                displayError(GuiText.component("registerhelper.message.recipe.no_types"));
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
            List<IngredientData> savedIngredients = slotManager != null
                    ? slotManager.getIngredientsData() : List.of();
            ItemStack savedResult = slotManager != null
                    ? slotManager.getResultItem().copy() : ItemStack.EMPTY;
            this.customTier = newTier;
            if (currentRecipeType != null &&
                    (currentRecipeType.isAvaritiaType() ||
                            Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class)))) {
                slotManager = null;
                clearWidgets();
                init();
                slotManager.setIngredientsData(savedIngredients);
                slotManager.setResultItem(savedResult);
                syncDataToRenderer();
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
                IngredientData data = IngredientData.fromItem(item);
                fillModeHandler.setBrushData(data);
                displayInfo(GuiText.component("registerhelper.message.recipe.brush_set", data.getDisplayText()));
            }));
            case INVENTORY -> minecraft.setScreen(new InventoryItemSelectorScreen(this, item -> {
                IngredientData data = IngredientData.fromItem(item);
                fillModeHandler.setBrushData(data);
                displayInfo(GuiText.component("registerhelper.message.recipe.brush_set", data.getDisplayText()));
            }));
            case TAG -> minecraft.setScreen(new TagSelectorScreen(this, tagId -> {
                IngredientData data = IngredientData.fromTag(tagId);
                fillModeHandler.setBrushData(data);
                displayInfo(GuiText.component("registerhelper.message.recipe.brush_set", data.getDisplayText()));
            }));
            case CUSTOM_TAG -> minecraft.setScreen(new CustomTagCreatorScreen(this, (tagId, items) -> {
                IngredientData data = IngredientData.fromCustomTag(tagId, items);
                CustomTagManager.registerTag(tagId, items);
                fillModeHandler.setBrushData(data);
                displayInfo(GuiText.component("registerhelper.message.recipe.brush_set", data.getDisplayText()));
            }));
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
        createButton.setMessage(GuiText.component("registerhelper.gui.recipe_creator.create"));
    }

    private void openRecipeSelector() {
        if (minecraft != null) {
            // 检查是否为远程服务器且缓存为空
            if (RecipeLoader.isRemoteServer()) {
                if (!com.wzz.registerhelper.network.RecipeClientCache.isLoaded()) {
                    displayInfo(GuiText.component("registerhelper.message.recipe.loading_list"));
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
                                                editableRecipes,
                                                GuiText.string("registerhelper.gui.recipe_creator.edit_selector_title")));
                                    } else {
                                        displayError(GuiText.component("registerhelper.message.recipe.no_editable"));
                                    }
                                } else {
                                    displayError(GuiText.component("registerhelper.message.recipe.no_editable"));
                                }
                            });
                        }
                    });
                    return;
                }
            }

            List<UnifiedRecipeInfo> editableRecipes = recipeLoader.getEditableRecipes();
            if (editableRecipes.isEmpty()) {
                displayError(GuiText.component("registerhelper.message.recipe.no_editable"));
                return;
            }
            minecraft.setScreen(new RecipeSelectorScreen(this, this::loadSelectedRecipe,
                    editableRecipes,
                    GuiText.string("registerhelper.gui.recipe_creator.edit_selector_title")));
        }
    }

    private void openAddBlacklist() {
        if (minecraft != null) {
            if (RecipeLoader.isRemoteServer()) {
                if (!com.wzz.registerhelper.network.RecipeClientCache.isLoaded()) {
                    displayInfo(GuiText.component("registerhelper.message.recipe.loading_list"));
                    recipeLoader.requestServerRecipes();
                    com.wzz.registerhelper.network.RecipeClientCache.addLoadCallback(recipes -> {
                        if (minecraft != null) {
                            minecraft.execute(() -> {
                                if (!recipes.isEmpty()) {
                                    minecraft.setScreen(new RecipeSelectorScreen(
                                            this, this::handleRecipeOperation,
                                             new ArrayList<>(recipes),
                                             GuiText.string("registerhelper.gui.recipe_creator.blacklist_selector_title")));
                                } else {
                                    displayError(GuiText.component("registerhelper.message.recipe.none"));
                                }
                            });
                        }
                    });
                    return;
                }
            }
            List<UnifiedRecipeInfo> allRecipes = recipeLoader.getAllRecipes();
            if (allRecipes.isEmpty()) {
                displayError(GuiText.component("registerhelper.message.recipe.none"));
                return;
            }
            minecraft.setScreen(new RecipeSelectorScreen(
                    this, this::handleRecipeOperation, allRecipes,
                    GuiText.string("registerhelper.gui.recipe_creator.blacklist_selector_title")));
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
                    displayInfo(GuiText.component("registerhelper.message.tag.added", tagId));
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
                    displayInfo(GuiText.component("registerhelper.message.custom_tag.added_to_slot",
                            tagId, items.size()));
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
            displayError(GuiText.component("registerhelper.message.recipe.info_not_found", recipeId));
            return;
        }

        try {
            boolean success = false;
            String resultMessage = "";

            if (info.isBlacklisted) {
                // 使用网络包辅助类
                success = BlacklistClientHelper.removeFromBlacklist(recipeId);
                resultMessage = success ? "registerhelper.message.recipe.restore_started"
                        : "registerhelper.message.recipe.restore_failed";
            } else if (info.hasOverride) {
                success = UnifiedRecipeOverrideManager.removeOverride(recipeId);
                resultMessage = success ? "registerhelper.message.recipe.override_removed"
                        : "registerhelper.message.recipe.override_remove_failed";
            } else {
                // 使用网络包辅助类
                success = BlacklistClientHelper.addToBlacklist(recipeId);
                resultMessage = success ? "registerhelper.message.recipe.disable_started"
                        : "registerhelper.message.recipe.disable_failed";
            }

            if (success) {
                displaySuccess(GuiText.component("registerhelper.message.recipe.operation_success",
                        GuiText.component(resultMessage), recipeId));

                if (editingRecipeId != null && editingRecipeId.equals(recipeId)) {
                    if (info.isBlacklisted) {
                        clearAllIngredients();
                        displayInfo(GuiText.component("registerhelper.message.recipe.disabled_exit"));
                    } else if (info.hasOverride) {
                        displayInfo(GuiText.component("registerhelper.message.recipe.override_original"));
                    }
                }
            } else {
                displayError(GuiText.component("registerhelper.message.recipe.operation_failed_status",
                        GuiText.component(resultMessage), recipeId));
            }

        } catch (Exception e) {
            displayError(GuiText.component("registerhelper.message.recipe.operation_error", e.getMessage()));
        }
    }

    private void createRecipe() {
        if (currentRecipeType == null) {
            displayError(GuiText.component("registerhelper.message.recipe.select_type"));
            return;
        }
        try {
            int count = Integer.parseInt(resultCountBox.getValue());
            if (count <= 0) {
                displayError(GuiText.component("registerhelper.message.recipe.positive_count"));
                return;
            }

            ItemStack resultItem = slotManager.getResultItem();
            if (resultItem.isEmpty()) {
                displayError(GuiText.component("registerhelper.message.recipe.select_result"));
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
            displayError(GuiText.component("registerhelper.message.recipe.invalid_numbers"));
        }
    }

    private DynamicRecipeBuilder.BuildParams getBuildParams(ItemStack resultItem, float cookingTime, float cookingExp) {
        Map<String, Object> componentData = new HashMap<>();
        if (componentRenderManager != null) {
            componentData = componentRenderManager.getDataManager().getAllData();
        }

        List<IngredientData> ingredientsData = slotManager.getIngredientsData();
        // NBT控制已改为 per-slot，由各槽位 IngredientData.isIncludeNBT() 决定
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
        GuiTheme.drawBackdrop(guiGraphics, this.width, this.height);

        int titleBarH = 24;
        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(
                leftPos, topPos, contentWidth, contentHeight);
        GuiTheme.drawPanel(guiGraphics, panel, titleBarH, GuiTheme.INFO);

        // ── 控件区背景 ──
        guiGraphics.fill(leftPos + 1, topPos + titleBarH, leftPos + contentWidth - 1, topPos + 98,
                GuiTheme.SECTION);
        guiGraphics.fill(leftPos + 5, topPos + 98, leftPos + contentWidth - 5, topPos + 99,
                GuiTheme.DIVIDER);

        // ── 主内容区背景 ──
        guiGraphics.fill(leftPos + 1, topPos + 99, leftPos + contentWidth - 1,
                topPos + contentHeight - FOOTER_HEIGHT, GuiTheme.SURFACE);

        // ── 底部按钮区背景 ──
        guiGraphics.fill(leftPos + 1, topPos + contentHeight - FOOTER_HEIGHT,
                leftPos + contentWidth - 1, topPos + contentHeight - 1, GuiTheme.PANEL_ALT);
        guiGraphics.fill(leftPos + 5, topPos + contentHeight - FOOTER_HEIGHT - 1,
                leftPos + contentWidth - 5, topPos + contentHeight - FOOTER_HEIGHT, GuiTheme.DIVIDER);

        // ── 右侧面板区域分隔线 ──
        guiGraphics.fill(rightPanelX - 8, topPos + titleBarH + 2,
                rightPanelX - 6, topPos + contentHeight - FOOTER_HEIGHT - 1, GuiTheme.DIVIDER);
        guiGraphics.fill(rightPanelX - 7, topPos + titleBarH + 2,
                rightPanelX - 5, topPos + contentHeight - FOOTER_HEIGHT - 1, GuiTheme.BORDER);

        // ── 标题文字 ──
        String titleText = isEditingExisting
                ? (editingRecipeId != null
                    ? GuiText.string("registerhelper.gui.recipe_creator.editor_title_id", editingRecipeId)
                    : GuiText.string("registerhelper.gui.recipe_creator.editor_title"))
                : GuiText.string("registerhelper.gui.recipe_creator.title");
        titleText = GuiLayoutHelper.ellipsis(this.font, titleText, contentWidth - 20);
        guiGraphics.drawCenteredString(this.font, titleText, leftPos + contentWidth / 2,
                topPos + 8, GuiTheme.TEXT_ON_HEADER);

        renderLabels(guiGraphics);

        GuiTheme.drawInput(guiGraphics, resultCountBox);
        if (cookingTimeBox.visible) GuiTheme.drawInput(guiGraphics, cookingTimeBox);
        if (cookingExpBox.visible) GuiTheme.drawInput(guiGraphics, cookingExpBox);

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
        if (menuOpen) {
            int mx2 = menuBtnX;
            int my2 = menuBtnY - MENU_ITEM_H * MENU_LABELS.length - 2; // 向上弹出

            // 浮层背景
            guiGraphics.fill(mx2 - 1, my2 - 1,
                    mx2 + menuBtnW + 1, menuBtnY, GuiTheme.PANEL_EDGE);
            guiGraphics.fill(mx2, my2,
                    mx2 + menuBtnW, menuBtnY - 1, GuiTheme.SURFACE);

            for (int i = 0; i < MENU_LABELS.length; i++) {
                int iy = my2 + i * MENU_ITEM_H;
                boolean hov = mouseX >= mx2 && mouseX < mx2 + menuBtnW
                        && mouseY >= iy && mouseY < iy + MENU_ITEM_H;
                if (hov) guiGraphics.fill(mx2, iy, mx2 + menuBtnW, iy + MENU_ITEM_H, GuiTheme.HOVER);
                // 左侧色条（黑名单=红, 覆盖=紫, 添加黑名单=橙）
                int[] barColors = { GuiTheme.DANGER, 0xFF7A63B8, GuiTheme.WARNING };
                guiGraphics.fill(mx2, iy, mx2 + 3, iy + MENU_ITEM_H, barColors[i]);
                guiGraphics.drawString(this.font, GuiText.string(MENU_LABELS[i]),
                        mx2 + 6, iy + 5, GuiTheme.TEXT, false);
            }
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    private void renderLabels(GuiGraphics guiGraphics) {
        int labelStartX = leftPos + 15;
        int labelY1 = topPos + 25;
        int labelY2 = topPos + 55;

        guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.recipe_creator.recipe_type_label"),
                labelStartX, labelY1, GuiTheme.TEXT_MUTED, false);

        if (currentRecipeType != null) {
            String category = currentRecipeType.getProperty("category", String.class);

            if ("crafting".equals(category) || "avaritia".equals(category)) {
                guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.recipe_creator.crafting_mode_label"),
                        secondaryControlX, labelY1, GuiTheme.TEXT_MUTED, false);
                guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.recipe_creator.fill_mode_label"),
                        labelStartX, labelY2, GuiTheme.TEXT_MUTED, false);
            } else if (currentRecipeType.supportsCookingSettings()) {
                guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.recipe_creator.cooking_type_label"),
                        secondaryControlX, labelY1, GuiTheme.TEXT_MUTED, false);
            }

            if (currentRecipeType.isAvaritiaType() || Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
                guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.recipe_creator.tier_label"),
                        tierControlX, labelY1, GuiTheme.TEXT_MUTED, false);
            }
        }

        // 右侧面板标签
        int panelContentX = rightPanelX + 10;

        guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.recipe_creator.result_label"), panelContentX,
                rightPanelStartY - 20, GuiTheme.TEXT_MUTED, false);
        guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.recipe_creator.count_label"), panelContentX,
                rightPanelStartY + rightPanelRowGap + 6, GuiTheme.TEXT_MUTED, false);

        if (currentRecipeType != null && currentRecipeType.supportsCookingSettings()) {
            guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.recipe_creator.time_label"), panelContentX,
                    rightPanelStartY + rightPanelRowGap * 2 + 6, GuiTheme.TEXT_MUTED, false);
            guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.recipe_creator.exp_label"), panelContentX,
                    rightPanelStartY + rightPanelRowGap * 3 + 6, GuiTheme.TEXT_MUTED, false);
        }

        // 底部状态信息
        boolean showStatusLine = contentHeight >= 300;
        if (isEditingExisting && showStatusLine) {
            guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.recipe_creator.editing_mode"), labelStartX,
                    topPos + contentHeight - FOOTER_HEIGHT - 13, GuiTheme.WARNING, false);
        }

        // 显示当前配方类型信息
        if (currentRecipeType != null && showStatusLine) {
            String typeInfo = currentRecipeType.getModId() + ":" + currentRecipeType.getId();
            int maxTypeWidth = Math.max(1, rightPanelX - labelStartX - 100);
            guiGraphics.drawString(this.font,
                    GuiLayoutHelper.ellipsis(this.font, typeInfo, maxTypeWidth),
                    labelStartX + 100,
                    topPos + contentHeight - FOOTER_HEIGHT - 13, GuiTheme.TEXT_MUTED, false);
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
        int slotWidth = slot.width();
        int slotHeight = slot.height();
        boolean isMouseOver = mouseX >= slot.x() && mouseX < slot.x() + slotWidth &&
                mouseY >= slot.y() && mouseY < slot.y() + slotHeight;

        GuiTheme.drawSlot(guiGraphics, slot.x(), slot.y(), slotWidth, slotHeight, isMouseOver);
        if (slot.index() < 0) {
            guiGraphics.fill(slot.x(), slot.y(), slot.x() + slotWidth, slot.y() + 2, GuiTheme.INFO);
        }

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
                renderScaledItem(guiGraphics, stackToRender, slot.x(), slot.y(), slotWidth, slotHeight);
                RenderSystem.disableDepthTest();
            }

            // 在右上角显示类型指示器
            switch (data.getType()) {
                case TAG -> {
                    // 标签：金色#标记 + 半透明金色背景
                    int markerX = slot.x() + Math.max(1, slotWidth - 8);
                    guiGraphics.fill(markerX, slot.y() + 1,
                            slot.x() + slotWidth, slot.y() + Math.min(9, slotHeight),
                            0x80E5AA62);
                    guiGraphics.drawString(this.font, "§6§l#", markerX + 1, slot.y() + 1, 0xFFFFFF, true);
                }
                case CUSTOM_TAG -> {
                    // 自定义标签：青色#标记 + 半透明青色背景
                    int markerX = slot.x() + Math.max(1, slotWidth - 8);
                    guiGraphics.fill(markerX, slot.y() + 1,
                            slot.x() + slotWidth, slot.y() + Math.min(9, slotHeight),
                            0x8074C4B5);
                    guiGraphics.drawString(this.font, "§b§l#", markerX + 1, slot.y() + 1, 0xFFFFFF, true);
                }
                case ITEM -> {
                    if (data.hasNBT()) {
                        int barColor;
                        String label;
                        switch (data.getNbtMode()) {
                            case "partial" -> { barColor = GuiTheme.WARNING; label = "§eN"; }
                            case "none"    -> { barColor = GuiTheme.TEXT_MUTED; label = "§8N"; }
                            default        -> { barColor = 0xFF8C5F9E; label = "§dN"; }
                        }
                        guiGraphics.fill(slot.x() + 1, slot.y() + Math.max(1, slotHeight - 3),
                                slot.x() + slotWidth - 1, slot.y() + slotHeight - 1, barColor);
                        if (slotWidth >= 12 && slotHeight >= 12) {
                            guiGraphics.drawString(this.font, label,
                                    slot.x() + slotWidth / 2 - 2,
                                    slot.y() + slotHeight - 8, 0xFFFFFF, true);
                        }
                    }
                }
            }
        } else if (!displayItem.isEmpty()) {
            // 兼容旧代码：直接显示ItemStack
            RenderSystem.enableDepthTest();
            renderScaledItem(guiGraphics, displayItem,
                    slot.x(), slot.y(), slotWidth, slotHeight);
            RenderSystem.disableDepthTest();
        }
    }

    private void renderScaledItem(GuiGraphics guiGraphics, ItemStack stack,
                                  int x, int y, int width, int height) {
        float scale = Math.min(1.0F,
                Math.max(0.125F, (Math.min(width, height) - 2) / 16.0F));
        guiGraphics.pose().pushPose();
        try {
            guiGraphics.pose().translate(x + 1, y + 1, 0);
            guiGraphics.pose().scale(scale, scale, 1.0F);
            guiGraphics.renderItem(stack, 0, 0);
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    private void renderFillModeHint(GuiGraphics guiGraphics) {
        String hint = fillModeHandler.getHintText();
        int hintX = leftPos + 15;
        int hintY = topPos + Math.min(125,
                Math.max(87, gridTopOffset - 10));
        int maxWidth = Math.max(1, rightPanelX - hintX - 10);
        guiGraphics.drawString(this.font,
                GuiLayoutHelper.ellipsis(this.font, hint, maxWidth),
                hintX, hintY, GuiTheme.TEXT_MUTED, false);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 材料槽位工具提示
        for (int i = 0; i < slotManager.getIngredientSlots().size(); i++) {
            SlotManager.IngredientSlot slot = slotManager.getIngredientSlots().get(i);
            if (mouseX >= slot.x() && mouseX < slot.x() + slot.width() &&
                    mouseY >= slot.y() && mouseY < slot.y() + slot.height()) {

                IngredientData data = slotManager.getIngredientData(i);

                if (!data.isEmpty()) {
                    List<Component> tooltip = new ArrayList<>();

                    // 根据类型显示不同的工具提示
                    switch (data.getType()) {
                        case ITEM -> {
                            ItemStack stack = data.getItemStack();
                            // 有NBT时附加操作提示
                            if (data.hasNBT()) {
                                List<Component> itemTip = new ArrayList<>();
                                itemTip.add(stack.getHoverName().copy());
                                String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                                itemTip.add(Component.literal("§8" + itemId));
                                itemTip.add(Component.literal(""));
                                switch (data.getNbtMode()) {
                                    case "exact" -> {
                                        itemTip.add(GuiText.component("registerhelper.tooltip.recipe_slot.exact_nbt"));
                                        itemTip.add(GuiText.component("registerhelper.tooltip.recipe_slot.toggle_ignore"));
                                        itemTip.add(GuiText.component("registerhelper.tooltip.recipe_slot.set_ignore_keys"));
                                    }
                                    case "partial" -> {
                                        itemTip.add(GuiText.component("registerhelper.tooltip.recipe_slot.partial_nbt",
                                                data.getIgnoreNbtKeys()));
                                        itemTip.add(GuiText.component("registerhelper.tooltip.recipe_slot.toggle_ignore_clear"));
                                        itemTip.add(GuiText.component("registerhelper.tooltip.recipe_slot.edit_ignore_keys"));
                                    }
                                    case "none" -> {
                                        itemTip.add(GuiText.component("registerhelper.tooltip.recipe_slot.ignore_nbt"));
                                        itemTip.add(GuiText.component("registerhelper.tooltip.recipe_slot.toggle_exact"));
                                        itemTip.add(GuiText.component("registerhelper.tooltip.recipe_slot.set_ignore_keys"));
                                    }
                                }
                                itemTip.add(GuiText.component("registerhelper.tooltip.recipe_slot.modify_clear"));
                                guiGraphics.renderTooltip(this.font, itemTip, Optional.empty(), mouseX, mouseY);
                                return;
                            } else {
                                guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
                            }
                            return;
                        }
                        case TAG -> {
                            tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.tag_material"));
                            tooltip.add(Component.literal("§e#" + data.getTagId()));

                            // 显示标签包含的物品示例
                            ItemStack displayItem = data.getDisplayStack();
                            if (!displayItem.isEmpty()) {
                                tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.example",
                                        displayItem.getHoverName()));
                            }

                            tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.tag_matches_all"));
                        }
                        case CUSTOM_TAG -> {
                            tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.custom_tag"));
                            tooltip.add(Component.literal("§3#" + data.getTagId()));

                            List<ItemStack> items = data.getCustomTagItems();
                            tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.contains_items", items.size()));

                            // 显示前3个物品
                            int showCount = Math.min(3, items.size());
                            for (int j = 0; j < showCount; j++) {
                                tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.item_bullet",
                                        items.get(j).getHoverName()));
                            }

                            if (items.size() > 3) {
                                tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.more_items",
                                        items.size() - 3));
                            }
                        }
                    }

                    tooltip.add(Component.literal("")); // blank line
                    tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.modify"));
                    if (data.getType() == IngredientData.Type.ITEM && data.hasNBT()) {
                        if (data.isIncludeNBT()) {
                            tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.nbt_bar_match"));
                        } else {
                            tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.nbt_bar_ignore"));
                        }
                        tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.clear"));
                    } else {
                        tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.clear"));
                    }

                    guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                } else {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.empty"));
                    tooltip.add(GuiText.component("registerhelper.tooltip.recipe_slot.choose_type"));
                    guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                }
                return;
            }
        }

        // 结果槽位工具提示
        SlotManager.IngredientSlot resultSlot = slotManager.getResultSlot();
        if (mouseX >= resultSlot.x() && mouseX < resultSlot.x() + resultSlot.width() &&
                mouseY >= resultSlot.y() && mouseY < resultSlot.y() + resultSlot.height()) {
            if (!slotManager.getResultItem().isEmpty()) {
                guiGraphics.renderTooltip(this.font, slotManager.getResultItem(), mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(this.font,
                        GuiText.component("registerhelper.tooltip.recipe_slot.choose_result"), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menuOpen) {
            int mx2 = menuBtnX;
            int my2 = menuBtnY - MENU_ITEM_H * MENU_LABELS.length - 2;
            if (mouseX >= mx2 && mouseX < mx2 + menuBtnW
                    && mouseY >= my2 && mouseY < menuBtnY - 1) {
                int idx = (int)(mouseY - my2) / MENU_ITEM_H;
                menuOpen = false;
                switch (idx) {
                    case 0 -> openBlacklistManager();
                    case 1 -> openOverrideManager();
                    case 2 -> openAddBlacklist();       // 原"配方操作"，改名后的方法
                }
                return true;
            }
            // 点击菜单外 → 关闭
            menuOpen = false;
        }
        if (slotManager != null) {
            for (int i = 0; i < slotManager.getIngredientSlots().size(); i++) {
                SlotManager.IngredientSlot slot = slotManager.getIngredientSlots().get(i);
                if (mouseX >= slot.x() && mouseX < slot.x() + slot.width() &&
                        mouseY >= slot.y() && mouseY < slot.y() + slot.height()) {
                    IngredientData slotData = slotManager.getIngredientData(i);
                    // 中键：切换该槽位 NBT 匹配（仅对带NBT的物品有效）
                    if (button == 2 && slotData.getType() == IngredientData.Type.ITEM
                            && slotData.hasNBT()) {

                        boolean shift = hasShiftDown(); // Screen.hasShiftDown()

                        if (shift) {
                            // Shift+中键：打开独立的 NbtIgnoreEditorScreen
                            if (minecraft != null) {
                                minecraft.setScreen(new NbtIgnoreEditorScreen(
                                        this,          // parent = 当前 RecipeCreatorScreen
                                        slotData,      // 目标 IngredientData（直接引用，修改立即生效）
                                            () -> {        // 确认回调：刷新显示
                                            if (slotData.getIgnoreNbtKeys().isEmpty()) {
                                                displayInfo(GuiText.component(
                                                        "registerhelper.message.nbt.exact_restored"));
                                            } else {
                                                displayInfo(GuiText.component("registerhelper.message.nbt.partial",
                                                        slotData.getIgnoreNbtKeys().size()));
                                            }
                                        }
                                ));
                            }
                            return true;
                        } else {
                            // 普通中键：精确 ↔ 忽略（与原逻辑完全一致）
                            // 若当前是部分匹配，先退出部分匹配再走二档切换
                            if ("partial".equals(slotData.getNbtMode())) {
                                slotData.setIgnoreNbtKeys(List.of());
                            }
                            boolean nowOn = slotData.toggleIncludeNBT();
                            displayInfo(GuiText.component(nowOn
                                    ? "registerhelper.message.nbt.exact"
                                    : "registerhelper.message.nbt.ignored"));
                        }
                        return true;
                    }
                    // 左键/右键：正常填充模式处理（左键选材料，右键清空）
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
            if (mouseX >= resultSlot.x() && mouseX < resultSlot.x() + resultSlot.width() &&
                    mouseY >= resultSlot.y() && mouseY < resultSlot.y() + resultSlot.height()) {
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
    private void displayError(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(message.copy().withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    private void displayError(String message) {
        displayError(Component.literal(message));
    }

    private void displaySuccess(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(message.copy().withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }

    private void displaySuccess(String message) {
        displaySuccess(Component.literal(message));
    }

    private void displayInfo(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(message.copy().withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }

    private void displayInfo(String message) {
        displayInfo(Component.literal(message));
    }
}
