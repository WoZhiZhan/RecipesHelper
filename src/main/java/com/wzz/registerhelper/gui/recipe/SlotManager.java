package com.wzz.registerhelper.gui.recipe;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import com.wzz.registerhelper.util.ModLogger;
import net.minecraft.world.item.ItemStack;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 槽位管理器 - 支持动态配方类型和IngredientData
 */
public class SlotManager {
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_SPACING = 20;

    private List<RecipeComponent> components = new ArrayList<>();
    private final List<IngredientSlot> ingredientSlots = new ArrayList<>();
    private final List<IngredientData> ingredients = new ArrayList<>(); // 改用IngredientData
    private IngredientSlot resultSlot;
    private ItemStack resultItem = ItemStack.EMPTY;

    private RecipeTypeDefinition currentRecipeType;
    private int customTier = 1;

    private int baseX, baseY;
    private int rightPanelX;

    public record IngredientSlot(int x, int y, int index) {}

    public SlotManager(int baseX, int baseY, int rightPanelX) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.rightPanelX = rightPanelX;
        this.currentRecipeType = DynamicRecipeTypeConfig.getRecipeType("crafting_shaped");
        initializeSlots();
    }

    public void updateCoordinates(int baseX, int baseY, int rightPanelX) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.rightPanelX = rightPanelX;
        updateSlotPositions();
    }

    public void setRecipeType(RecipeTypeDefinition recipeType, int customTier, boolean preserveIngredients) {
        if (recipeType == null) {
            ModLogger.getLogger().error("setRecipeType: recipeType为null");
            return;
        }
        List<IngredientData> oldIngredients = preserveIngredients ? new ArrayList<>(ingredients) : new ArrayList<>();

        this.currentRecipeType = recipeType;
        this.customTier = customTier;
        initializeSlots();
        if (preserveIngredients && !oldIngredients.isEmpty()) {
            for (int i = 0; i < Math.min(ingredientSlots.size(), oldIngredients.size()); i++) {
                if (i < ingredients.size() && !oldIngredients.get(i).isEmpty()) {
                    ingredients.set(i, oldIngredients.get(i));
                }
            }
        }
    }

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
            updateCustomSlotPositions();
        }

        resultSlot = new IngredientSlot(rightPanelX + 20, baseY + 130, -1);
    }

    private void updateCraftingSlotPositions() {
        int gridWidth = Math.min(3, currentRecipeType.getMaxGridWidth());
        int gridHeight = Math.min(3, currentRecipeType.getMaxGridHeight());
        updateGridSlotPositions(gridWidth, gridHeight);
    }

    private void updateAvaritiaSlotPositions() {
        Integer tier = currentRecipeType.getProperty("tier", Integer.class);
        int actualTier = tier != null ? tier : customTier;
        int gridSize = getAvaritiaGridSize(actualTier);
        updateGridSlotPositions(gridSize, gridSize);
    }

    private void updateCookingSlotPositions() {
        if (!ingredientSlots.isEmpty()) {
            ingredientSlots.set(0, new IngredientSlot(baseX + SLOT_SPACING, baseY + 170, 0));
        }
    }

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

    private void initializeSlots() {
        ingredientSlots.clear();
        ingredients.clear();

        if (currentRecipeType == null) return;

        String layoutId = currentRecipeType.getProperty("layout", String.class);
        if (layoutId != null) {
            initializeCustomLayout(layoutId);
        } else {
            initializeTraditionalLayout();
        }

        resultSlot = new IngredientSlot(rightPanelX + 20, baseY + 130, -1);
    }

    private void initializeCustomLayout(String layoutId) {
        RecipeLayout layout = LayoutManager.getLayout(layoutId);
        if (layout == null) {
            ModLogger.getLogger().warn("找不到布局: {}，使用默认布局", layoutId);
            initializeTraditionalLayout();
            return;
        }

        components.clear();
        ingredientSlots.clear();
        ingredients.clear();

        components = layout.generateComponents(baseX, baseY + 150, customTier);
        for (RecipeComponent component : components) {
            if (component instanceof SlotComponent slotComp) {
                int index = slotComp.getSlotIndex();
                ingredientSlots.add(new IngredientSlot(
                        slotComp.getX(),
                        slotComp.getY(),
                        index
                ));
                ingredients.add(IngredientData.empty());
            }
        }
    }

    public int getBaseX() {
        return baseX;
    }

    public int getBaseY() {
        return baseY;
    }

    public List<RecipeComponent> getComponents() {
        return components;
    }

    public List<RecipeComponent> setComponents(List<RecipeComponent> components) {
        return this.components = components;
    }

    private void initializeTraditionalLayout() {
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

    private void initializeCraftingSlots() {
        initializeGridSlots(3, 3);
    }

    private void initializeAvaritiaSlots() {
        Integer tier = currentRecipeType.getProperty("tier", Integer.class);
        int actualTier = tier != null ? tier : customTier;
        int gridSize = getAvaritiaGridSize(actualTier);
        initializeGridSlots(gridSize, gridSize);
    }

    private void initializeCookingSlots() {
        ingredientSlots.add(new IngredientSlot(baseX + SLOT_SPACING, baseY + 170, 0));
        ingredients.add(IngredientData.empty());
    }

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

    private int getGridSizeForTier(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            case 4 -> 9;
            default -> 3;
        };
    }

    private void initializeGridSlots(int gridWidth, int gridHeight) {
        int startX = baseX;
        int startY = baseY + 150;
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int slotX = startX + x * SLOT_SPACING;
                int slotY = startY + y * SLOT_SPACING;
                ingredientSlots.add(new IngredientSlot(slotX, slotY, y * gridWidth + x));
                ingredients.add(IngredientData.empty());
            }
        }
    }

    private int getAvaritiaGridSize(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            case 4 -> 9;
            default -> 3;
        };
    }

    // === 新的方法：支持IngredientData ===
    
    /**
     * 设置材料数据（新方法）
     */
    public void setIngredientData(int slotIndex, IngredientData data) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            ingredients.set(slotIndex, data.copy());
        }
    }
    
    /**
     * 获取材料数据（新方法）
     */
    public IngredientData getIngredientData(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            return ingredients.get(slotIndex);
        }
        return IngredientData.empty();
    }
    
    /**
     * 获取所有材料数据（新方法）
     */
    public List<IngredientData> getIngredientsData() {
        return new ArrayList<>(ingredients);
    }

    /**
     * 设置材料列表（兼容旧代码，从ItemStack转换为IngredientData）
     */
    public void setIngredients(List<ItemStack> newIngredients) {
        ingredients.clear();
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (i < newIngredients.size()) {
                ItemStack stack = newIngredients.get(i);
                if (!stack.isEmpty()) {
                    ingredients.add(IngredientData.fromItem(stack));
                } else {
                    ingredients.add(IngredientData.empty());
                }
            } else {
                ingredients.add(IngredientData.empty());
            }
        }
    }

    /**
     * 设置指定槽位的材料（兼容旧代码）
     */
    public boolean setIngredient(int slotIndex, ItemStack item) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            if (!item.isEmpty()) {
                ingredients.set(slotIndex, IngredientData.fromItem(item));
            } else {
                ingredients.set(slotIndex, IngredientData.empty());
            }
            return true;
        }
        return false;
    }

    /**
     * 获取指定槽位的材料（兼容旧代码，返回ItemStack）
     */
    public ItemStack getIngredient(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            return ingredients.get(slotIndex).getItemStack();
        }
        return ItemStack.EMPTY;
    }
    
    /**
     * 获取所有材料（兼容旧代码，转换为ItemStack列表）
     */
    public List<ItemStack> getIngredients() {
        List<ItemStack> result = new ArrayList<>();
        for (IngredientData data : ingredients) {
            result.add(data.getItemStack());
        }
        return result;
    }

    /**
     * 清空指定槽位
     */
    public void clearSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            ingredients.set(slotIndex, IngredientData.empty());
        }
    }

    /**
     * 清空所有材料
     */
    public void clearAllIngredients() {
        for (int i = 0; i < ingredients.size(); i++) {
            ingredients.set(i, IngredientData.empty());
        }
        resultItem = ItemStack.EMPTY;
    }

    /**
     * 填充所有空槽位（兼容旧代码）
     */
    public void fillEmptySlots(ItemStack item) {
        IngredientData data = IngredientData.fromItem(item);
        for (int i = 0; i < ingredients.size(); i++) {
            if (ingredients.get(i).isEmpty()) {
                ingredients.set(i, data.copy());
            }
        }
    }

    /**
     * 获取当前网格大小
     */
    public GridDimensions getGridDimensions() {
        if (currentRecipeType == null) {
            return new GridDimensions(3, 3);
        }

        String category = currentRecipeType.getProperty("category", String.class);

        if ("cooking".equals(category) || currentRecipeType.supportsCookingSettings()) {
            return new GridDimensions(1, 1);
//        } else if ("avaritia".equals(category)) {
//            Integer tier = currentRecipeType.getProperty("tier", Integer.class);
//            int actualTier = tier != null ? tier : customTier;
//            int gridSize = getAvaritiaGridSize(actualTier);
//            return new GridDimensions(gridSize, gridSize);
        } else {
            return new GridDimensions(
                    currentRecipeType.getMaxGridWidth(),
                    currentRecipeType.getMaxGridHeight()
            );
        }
    }

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
    public IngredientSlot getResultSlot() { return resultSlot; }
    public ItemStack getResultItem() { return resultItem; }
    public RecipeTypeDefinition getCurrentRecipeType() { return currentRecipeType; }
    public int getCustomTier() { return customTier; }

    // Setters
    public void setResultItem(ItemStack resultItem) {
        this.resultItem = resultItem.copy();
    }
}