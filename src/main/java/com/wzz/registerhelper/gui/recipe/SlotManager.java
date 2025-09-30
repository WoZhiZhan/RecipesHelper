package com.wzz.registerhelper.gui.recipe;

import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.gui.recipe.layout.GridLayout;
import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.gui.recipe.layout.SlotPosition;
import com.wzz.registerhelper.util.ModLogger;
import net.minecraft.world.item.ItemStack;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 槽位管理器 - 支持动态配方类型
 */
public class SlotManager {
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_SPACING = 20;

    private final List<IngredientSlot> ingredientSlots = new ArrayList<>();
    private final List<ItemStack> ingredients = new ArrayList<>();
    private IngredientSlot resultSlot;
    private ItemStack resultItem = ItemStack.EMPTY;

    private RecipeTypeDefinition currentRecipeType;
    private int customTier = 1;

    private int baseX, baseY; // 基础坐标
    private int rightPanelX; // 右侧面板X坐标

    public static record IngredientSlot(int x, int y, int index) {}

    public SlotManager(int baseX, int baseY, int rightPanelX) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.rightPanelX = rightPanelX;
        // 设置默认配方类型
        this.currentRecipeType = DynamicRecipeTypeConfig.getRecipeType("crafting_shaped");
        initializeSlots();
    }

    /**
     * 更新基础坐标
     */
    public void updateCoordinates(int baseX, int baseY, int rightPanelX) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.rightPanelX = rightPanelX;
        updateSlotPositions();
    }

    /**
     * 设置配方类型
     */
    public void setRecipeType(RecipeTypeDefinition recipeType, int customTier) {
        setRecipeType(recipeType, customTier, true);
    }

    /**
     * 设置配方类型
     * @param preserveIngredients 是否保留现有材料
     */
    public void setRecipeType(RecipeTypeDefinition recipeType, int customTier, boolean preserveIngredients) {
        if (recipeType == null) return;

        List<ItemStack> oldIngredients = preserveIngredients ? new ArrayList<>(ingredients) : new ArrayList<>();
        this.currentRecipeType = recipeType;
        this.customTier = customTier;

        initializeSlots();

        // 恢复之前的材料（如果需要）
        if (preserveIngredients) {
            for (int i = 0; i < Math.min(ingredientSlots.size(), oldIngredients.size()); i++) {
                if (i < ingredients.size()) {
                    ingredients.set(i, oldIngredients.get(i));
                }
            }
        }
    }

    /**
     * 只更新槽位位置，保留现有材料
     */
    private void updateSlotPositions() {
        if (currentRecipeType == null) return;

        String category = currentRecipeType.getProperty("category", String.class);

        if ("crafting".equals(category)) {
            updateCraftingSlotPositions();
        } else if ("avaritia".equals(category)) {
            updateAvaritiaSlotPositions();
        } else if ("cooking".equals(category) || currentRecipeType.supportsCookingSettings()) {
            updateCookingSlotPositions();
        } else {
            // 默认使用配方类型定义的网格大小
            updateCustomSlotPositions();
        }

        // 更新结果槽位
        resultSlot = new IngredientSlot(rightPanelX + 20, baseY + 130, -1);
    }

    /**
     * 更新工作台槽位位置
     */
    private void updateCraftingSlotPositions() {
        int gridWidth = Math.min(3, currentRecipeType.getMaxGridWidth());
        int gridHeight = Math.min(3, currentRecipeType.getMaxGridHeight());
        updateGridSlotPositions(gridWidth, gridHeight);
    }

    /**
     * 更新Avaritia槽位位置
     */
    private void updateAvaritiaSlotPositions() {
        Integer tier = currentRecipeType.getProperty("tier", Integer.class);
        int actualTier = tier != null ? tier : customTier;
        int gridSize = getAvaritiaGridSize(actualTier);
        updateGridSlotPositions(gridSize, gridSize);
    }

    /**
     * 更新烹饪槽位位置
     */
    private void updateCookingSlotPositions() {
        if (!ingredientSlots.isEmpty()) {
            ingredientSlots.set(0, new IngredientSlot(baseX + SLOT_SPACING, baseY + 170, 0));
        }
    }

    /**
     * 更新自定义槽位位置
     */
    private void updateCustomSlotPositions() {
        int gridWidth = currentRecipeType.getMaxGridWidth();
        int gridHeight = currentRecipeType.getMaxGridHeight();
        if (Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
            int dynamicSize = getGridSizeForTier(customTier);
            gridWidth = dynamicSize;
            gridHeight = dynamicSize;
        }
        updateGridSlotPositions(gridWidth, gridHeight);
    }

    /**
     * 通用网格槽位位置更新
     */
    private void updateGridSlotPositions(int gridWidth, int gridHeight) {
        int startX = baseX;
        int startY = baseY + 150;

        for (int i = 0; i < ingredientSlots.size() && i < gridWidth * gridHeight; i++) {
            int x = i % gridWidth;
            int y = i / gridWidth;
            int slotX = startX + x * SLOT_SPACING;
            int slotY = startY + y * SLOT_SPACING;
            ingredientSlots.set(i, new IngredientSlot(slotX, slotY, i));
        }
    }

    /**
     * 初始化槽位
     */
    private void initializeSlots() {
        ingredientSlots.clear();
        ingredients.clear();

        if (currentRecipeType == null) return;

        // 检查是否有自定义布局
        String layoutId = currentRecipeType.getProperty("layout", String.class);
        if (layoutId != null) {
            initializeCustomLayout(layoutId);
        } else {
            // 使用原有的逻辑
            initializeTraditionalLayout();
        }

        // 初始化结果槽位
        resultSlot = new IngredientSlot(rightPanelX + 20, baseY + 130, -1);
    }

    private void initializeCustomLayout(String layoutId) {
        GridLayout layout = LayoutManager.getLayout(layoutId);
        if (layout == null) {
            ModLogger.getLogger().warn("找不到布局: {}，使用默认布局", layoutId);
            initializeTraditionalLayout();
            return;
        }

        List<SlotPosition> positions = layout.generateSlots(baseX, baseY + 150, customTier);

        for (SlotPosition pos : positions) {
            ingredientSlots.add(new IngredientSlot(pos.getPixelX(), pos.getPixelY(), pos.getIndex()));
            ingredients.add(ItemStack.EMPTY);
        }
    }

    private void initializeTraditionalLayout() {
        // 原有的初始化逻辑
        String category = currentRecipeType.getProperty("category", String.class);
        if ("crafting".equals(category)) {
            initializeCraftingSlots();
        } else if ("avaritia".equals(category)) {
            initializeAvaritiaSlots();
        } else if ("cooking".equals(category)) {
            initializeCookingSlots();
        } else {
            initializeCustomSlots();
        }
    }

    /**
     * 初始化工作台槽位 (3x3)
     */
    private void initializeCraftingSlots() {
        initializeGridSlots(3, 3);
    }

    /**
     * 初始化Avaritia槽位
     */
    private void initializeAvaritiaSlots() {
        Integer tier = currentRecipeType.getProperty("tier", Integer.class);
        int actualTier = tier != null ? tier : customTier;
        int gridSize = getAvaritiaGridSize(actualTier);
        initializeGridSlots(gridSize, gridSize);
    }

    /**
     * 初始化烹饪槽位 (1x1)
     */
    private void initializeCookingSlots() {
        ingredientSlots.add(new IngredientSlot(baseX + SLOT_SPACING, baseY + 170, 0));
        ingredients.add(ItemStack.EMPTY);
    }

    /**
     * 初始化自定义槽位
     */
    private void initializeCustomSlots() {
        int gridWidth = currentRecipeType.getMaxGridWidth();
        int gridHeight = currentRecipeType.getMaxGridHeight();
        if (Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
            int dynamicSize = getGridSizeForTier(customTier);
            gridWidth = dynamicSize;
            gridHeight = dynamicSize;
        }

        initializeGridSlots(gridWidth, gridHeight);
    }

    /**
     * 根据tier获取网格大小（复用Avaritia的逻辑）
     */
    private int getGridSizeForTier(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            case 4 -> 9;
            default -> 3;
        };
    }

    /**
     * 通用网格槽位初始化
     */
    private void initializeGridSlots(int gridWidth, int gridHeight) {
        int startX = baseX;
        int startY = baseY + 150;

        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int slotX = startX + x * SLOT_SPACING;
                int slotY = startY + y * SLOT_SPACING;
                ingredientSlots.add(new IngredientSlot(slotX, slotY, y * gridWidth + x));
                ingredients.add(ItemStack.EMPTY);
            }
        }
    }

    /**
     * 获取Avaritia网格大小
     */
    private int getAvaritiaGridSize(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            case 4 -> 9;
            default -> 3;
        };
    }

    /**
     * 设置材料列表（用于配方加载）
     */
    public void setIngredients(List<ItemStack> newIngredients) {
        ingredients.clear();
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (i < newIngredients.size()) {
                ingredients.add(newIngredients.get(i).copy());
            } else {
                ingredients.add(ItemStack.EMPTY);
            }
        }
    }

    /**
     * 设置指定槽位的材料
     */
    public boolean setIngredient(int slotIndex, ItemStack item) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            ingredients.set(slotIndex, item.copy());
            return true;
        }
        return false;
    }

    /**
     * 获取指定槽位的材料
     */
    public ItemStack getIngredient(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            return ingredients.get(slotIndex);
        }
        return ItemStack.EMPTY;
    }

    /**
     * 清空指定槽位
     */
    public void clearSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            ingredients.set(slotIndex, ItemStack.EMPTY);
        }
    }

    /**
     * 清空所有材料
     */
    public void clearAllIngredients() {
        Collections.fill(ingredients, ItemStack.EMPTY);
        resultItem = ItemStack.EMPTY;
    }

    /**
     * 填充所有空槽位
     */
    public void fillEmptySlots(ItemStack item) {
        for (int i = 0; i < ingredients.size(); i++) {
            if (ingredients.get(i).isEmpty()) {
                ingredients.set(i, item.copy());
            }
        }
    }

    /**
     * 获取当前网格大小（用于计算动态尺寸）
     */
    public GridDimensions getGridDimensions() {
        if (currentRecipeType == null) {
            return new GridDimensions(3, 3);
        }

        String category = currentRecipeType.getProperty("category", String.class);

        if ("cooking".equals(category) || currentRecipeType.supportsCookingSettings()) {
            return new GridDimensions(1, 1);
        } else if ("avaritia".equals(category)) {
            Integer tier = currentRecipeType.getProperty("tier", Integer.class);
            int actualTier = tier != null ? tier : customTier;
            int gridSize = getAvaritiaGridSize(actualTier);
            return new GridDimensions(gridSize, gridSize);
        } else {
            return new GridDimensions(
                    currentRecipeType.getMaxGridWidth(),
                    currentRecipeType.getMaxGridHeight()
            );
        }
    }

    /**
     * 网格尺寸记录类
     */
    public record GridDimensions(int width, int height) {
        public int getTotalSlots() {
            return width * height;
        }

        public int getPixelWidth() {
            return width * SLOT_SPACING;
        }

        public int getPixelHeight() {
            return height * SLOT_SPACING;
        }
    }

    // Getters
    public List<IngredientSlot> getIngredientSlots() { return ingredientSlots; }
    public List<ItemStack> getIngredients() { return ingredients; }
    public IngredientSlot getResultSlot() { return resultSlot; }
    public ItemStack getResultItem() { return resultItem; }
    public RecipeTypeDefinition getCurrentRecipeType() { return currentRecipeType; }
    public int getCustomTier() { return customTier; }

    // Setters
    public void setResultItem(ItemStack resultItem) {
        this.resultItem = resultItem.copy();
    }
}