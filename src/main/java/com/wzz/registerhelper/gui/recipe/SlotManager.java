package com.wzz.registerhelper.gui.recipe;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeBuilder;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;
import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.gui.recipe.layout.RecipeLayout;
import com.wzz.registerhelper.util.ModLogger;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 槽位管理器 - 支持动态配方类型和IngredientData
 */
public class SlotManager {
    /** 默认槽位间距，可通过 setSlotSpacing 动态缩小以适应小屏幕 */
    private int slotSpacing = DEFAULT_SLOT_SPACING;
    /** 槽位间距的硬性下限，低于此值物品图标将无法辨认 */
    public static final int MIN_SLOT_SPACING = 4;
    public static final int DEFAULT_SLOT_SPACING = 18;

    private List<RecipeComponent> components = new ArrayList<>();
    private final List<IngredientSlot> ingredientSlots = new ArrayList<>();
    private final List<IngredientData> ingredients = new ArrayList<>();
    private IngredientSlot resultSlot;
    private ItemStack resultItem = ItemStack.EMPTY;

    private RecipeTypeDefinition currentRecipeType;
    private int customTier = 1;

    private int baseX, baseY;
    private int rightPanelX;
    private int resultSlotOffset = 130;
    private int gridTopOffset = 150;
    private int layoutAreaWidth = Integer.MAX_VALUE;
    private int layoutAreaHeight = Integer.MAX_VALUE;

    public record IngredientSlot(int x, int y, int width, int height, int index) {
        public IngredientSlot(int x, int y, int index) {
            this(x, y, 18, 18, index);
        }
    }

    public SlotManager(int baseX, int baseY, int rightPanelX) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.rightPanelX = rightPanelX;
        this.currentRecipeType = DynamicRecipeTypeConfig.getRecipeType("crafting_shaped");
        initializeSlots();
    }

    /** Set the slot spacing used by traditional and generated grid layouts. */
    public void setSlotSpacing(int spacing) {
        this.slotSpacing = Math.max(MIN_SLOT_SPACING,
                Math.min(DEFAULT_SLOT_SPACING, spacing));
        updateSlotPositions();
    }

    public int getSlotSpacing() {
        return slotSpacing;
    }

    public void setResultSlotOffset(int offset) {
        this.resultSlotOffset = Math.max(0, offset);
        this.resultSlot = new IngredientSlot(rightPanelX + 20,
                baseY + resultSlotOffset, -1);
    }

    public void setLayoutAreaSize(int width, int height) {
        this.layoutAreaWidth = Math.max(1, width);
        this.layoutAreaHeight = Math.max(1, height);
        updateSlotPositions();
    }

    public void setGridTopOffset(int offset) {
        this.gridTopOffset = Math.max(0, offset);
        updateSlotPositions();
    }

    public void updateCoordinates(int baseX, int baseY, int rightPanelX) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.rightPanelX = rightPanelX;
        updateSlotPositions();
    }

    public void setRecipeType(RecipeTypeDefinition recipeType, int customTier,
                              boolean preserveIngredients) {
        if (recipeType == null) {
            ModLogger.getLogger().error("setRecipeType: recipeType为null");
            return;
        }

        List<IngredientData> oldIngredients = new ArrayList<>();
        if (preserveIngredients) {
            for (IngredientData data : ingredients) {
                oldIngredients.add(data.copy());
            }
        }

        this.currentRecipeType = recipeType;
        this.customTier = Math.max(1, customTier);
        initializeSlots();
        if (preserveIngredients) {
            for (int i = 0; i < Math.min(ingredientSlots.size(), oldIngredients.size()); i++) {
                if (!oldIngredients.get(i).isEmpty()) {
                    ingredients.set(i, oldIngredients.get(i).copy());
                }
            }
        }
    }

    private void updateSlotPositions() {
        if (currentRecipeType == null) return;

        String layoutId = currentRecipeType.getProperty("layout", String.class);
        if (layoutId != null) {
            updateCustomLayoutPositions(layoutId);
            resultSlot = new IngredientSlot(rightPanelX + 20,
                    baseY + resultSlotOffset, -1);
            return;
        }

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

        resultSlot = new IngredientSlot(rightPanelX + 20,
                baseY + resultSlotOffset, -1);
    }

    private void updateCraftingSlotPositions() {
        int gridWidth = Math.min(3, currentRecipeType.getMaxGridWidth());
        int gridHeight = Math.min(3, currentRecipeType.getMaxGridHeight());
        updateGridSlotPositions(gridWidth, gridHeight);
    }

    private void updateAvaritiaSlotPositions() {
        Integer tier = currentRecipeType.getProperty("tier", Integer.class);
        int actualTier = tier != null ? tier : customTier;
        int gridSize = DynamicRecipeBuilder.getGridSizeForTier(actualTier);
        updateGridSlotPositions(gridSize, gridSize);
    }

    private void updateCookingSlotPositions() {
        if (!ingredientSlots.isEmpty()) {
            int slotSize = Math.min(18, slotSpacing);
            ingredientSlots.set(0, new IngredientSlot(
                    baseX + slotSpacing, baseY + gridTopOffset + 20,
                    slotSize, slotSize, 0));
        }
    }

    private void updateCustomSlotPositions() {
        int gridWidth = currentRecipeType.getMaxGridWidth();
        int gridHeight = currentRecipeType.getMaxGridHeight();
        if (Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
            int dynamicSize = DynamicRecipeBuilder.getGridSizeForTier(customTier);
            gridWidth = dynamicSize;
            gridHeight = dynamicSize;
        }
        updateGridSlotPositions(gridWidth, gridHeight);
    }

    private void updateGridSlotPositions(int gridWidth, int gridHeight) {
        if (gridWidth <= 0 || gridHeight <= 0) return;
        int startX = baseX;
        int startY = baseY + gridTopOffset;

        for (int i = 0; i < ingredientSlots.size() && i < gridWidth * gridHeight; i++) {
            int x = i % gridWidth;
            int y = i / gridWidth;
            int slotSize = Math.min(18, slotSpacing);
            ingredientSlots.set(i, new IngredientSlot(
                    startX + x * slotSpacing,
                    startY + y * slotSpacing,
                    slotSize, slotSize, i));
        }
    }

    private void initializeSlots() {
        components.clear();
        ingredientSlots.clear();
        ingredients.clear();

        if (currentRecipeType == null) return;

        String layoutId = currentRecipeType.getProperty("layout", String.class);
        if (layoutId != null) {
            initializeCustomLayout(layoutId);
        } else {
            initializeTraditionalLayout();
        }

        resultSlot = new IngredientSlot(rightPanelX + 20,
                baseY + resultSlotOffset, -1);
    }

    private void initializeCustomLayout(String layoutId) {
        RecipeLayout layout = LayoutManager.getLayout(layoutId);
        if (layout == null) {
            ModLogger.getLogger().warn("找不到布局: {}，使用默认布局", layoutId);
            initializeTraditionalLayout();
            return;
        }

        components = generateFittedComponents(layout);
        for (RecipeComponent component : components) {
            if (component instanceof SlotComponent slotComp) {
                ingredientSlots.add(new IngredientSlot(
                        slotComp.getX(), slotComp.getY(),
                        slotComp.getWidth(), slotComp.getHeight(),
                        slotComp.getSlotIndex()));
                ingredients.add(IngredientData.empty());
            }
        }
    }

    private void updateCustomLayoutPositions(String layoutId) {
        RecipeLayout layout = LayoutManager.getLayout(layoutId);
        if (layout == null) {
            updateCustomSlotPositions();
            return;
        }

        List<IngredientData> oldIngredients = new ArrayList<>();
        for (IngredientData data : ingredients) {
            oldIngredients.add(data.copy());
        }
        components = generateFittedComponents(layout);
        ingredientSlots.clear();
        ingredients.clear();

        int ingredientIndex = 0;
        for (RecipeComponent component : components) {
            if (component instanceof SlotComponent slotComp) {
                ingredientSlots.add(new IngredientSlot(
                        slotComp.getX(), slotComp.getY(),
                        slotComp.getWidth(), slotComp.getHeight(),
                        slotComp.getSlotIndex()));
                ingredients.add(ingredientIndex < oldIngredients.size()
                        ? oldIngredients.get(ingredientIndex).copy() : IngredientData.empty());
                ingredientIndex++;
            }
        }
    }

    private List<RecipeComponent> generateFittedComponents(RecipeLayout layout) {
        int layoutOriginY = baseY + gridTopOffset;
        List<RecipeComponent> generated = layout.generateComponents(
                baseX, layoutOriginY, customTier);
        java.awt.Rectangle bounds = layout.getBounds(customTier);
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return generated;
        }

        double scale = Math.min(1.0, Math.min(
                layoutAreaWidth / (double) bounds.width,
                layoutAreaHeight / (double) bounds.height));
        if (scale >= 1.0) {
            return generated;
        }

        for (RecipeComponent component : generated) {
            int relativeX = component.getX() - baseX;
            int relativeY = component.getY() - layoutOriginY;
            component.setPosition(
                    baseX + (int) Math.round(relativeX * scale),
                    layoutOriginY + (int) Math.round(relativeY * scale));
            int minimumWidth = switch (component.getType()) {
                case SLOT -> MIN_SLOT_SPACING;
                case NUMBER_INPUT, STRING_INPUT, LABEL -> 14;
            };
            component.setSize(
                    Math.max(minimumWidth, (int) Math.round(component.getWidth() * scale)),
                    Math.max(4, (int) Math.round(component.getHeight() * scale)));
        }
        preventComponentOverlap(generated);
        return generated;
    }

    private void preventComponentOverlap(List<RecipeComponent> generated) {
        for (RecipeComponent component : generated) {
            int fittedWidth = component.getWidth();
            int fittedHeight = component.getHeight();
            for (RecipeComponent other : generated) {
                if (component == other) continue;

                boolean verticalOverlap = component.getY() < other.getY() + other.getHeight()
                        && component.getY() + component.getHeight() > other.getY();
                if (verticalOverlap && component.getX() < other.getX()) {
                    fittedWidth = Math.min(fittedWidth,
                            Math.max(1, other.getX() - component.getX() - 1));
                }

                boolean horizontalOverlap = component.getX() < other.getX() + other.getWidth()
                        && component.getX() + component.getWidth() > other.getX();
                if (horizontalOverlap && component.getY() < other.getY()) {
                    fittedHeight = Math.min(fittedHeight,
                            Math.max(1, other.getY() - component.getY() - 1));
                }
            }
            component.setSize(fittedWidth, fittedHeight);
        }
    }

    public int getBaseX() { return baseX; }
    public int getBaseY() { return baseY; }
    public List<RecipeComponent> getComponents() { return components; }
    public List<RecipeComponent> setComponents(List<RecipeComponent> components) {
        return this.components = components;
    }

    private void initializeTraditionalLayout() {
        String category = currentRecipeType.getProperty("category", String.class);
        if ("crafting".equals(category)) {
            initializeCraftingSlots();
        } else if ("avaritia".equals(category)) {
            initializeAvaritiaSlots();
        } else if ("cooking".equals(category) || currentRecipeType.supportsCookingSettings()) {
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
        int gridSize = DynamicRecipeBuilder.getGridSizeForTier(actualTier);
        initializeGridSlots(gridSize, gridSize);
    }

    private void initializeCookingSlots() {
        int slotSize = Math.min(18, slotSpacing);
        ingredientSlots.add(new IngredientSlot(
                baseX + slotSpacing, baseY + gridTopOffset + 20,
                slotSize, slotSize, 0));
        ingredients.add(IngredientData.empty());
    }

    private void initializeCustomSlots() {
        int gridWidth = currentRecipeType.getMaxGridWidth();
        int gridHeight = currentRecipeType.getMaxGridHeight();
        if (Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
            int dynamicSize = DynamicRecipeBuilder.getGridSizeForTier(customTier);
            gridWidth = dynamicSize;
            gridHeight = dynamicSize;
        }
        initializeGridSlots(gridWidth, gridHeight);
    }

    private void initializeGridSlots(int gridWidth, int gridHeight) {
        int startX = baseX;
        int startY = baseY + gridTopOffset;
        int slotSize = Math.min(18, slotSpacing);
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                ingredientSlots.add(new IngredientSlot(
                        startX + x * slotSpacing,
                        startY + y * slotSpacing,
                        slotSize, slotSize, y * gridWidth + x));
                ingredients.add(IngredientData.empty());
            }
        }
    }

    public void setIngredientData(int slotIndex, IngredientData data) {
        if (slotIndex >= 0 && slotIndex < ingredients.size() && data != null) {
            ingredients.set(slotIndex, data.copy());
        }
    }

    public IngredientData getIngredientData(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            return ingredients.get(slotIndex);
        }
        return IngredientData.empty();
    }

    public List<IngredientData> getIngredientsData() {
        List<IngredientData> result = new ArrayList<>();
        for (IngredientData data : ingredients) {
            result.add(data.copy());
        }
        return result;
    }

    /** Compatibility setter; NBT is retained by IngredientData.fromItem. */
    public void setIngredients(List<ItemStack> newIngredients) {
        ingredients.clear();
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (newIngredients != null && i < newIngredients.size()
                    && !newIngredients.get(i).isEmpty()) {
                ingredients.add(IngredientData.fromItem(newIngredients.get(i)));
            } else {
                ingredients.add(IngredientData.empty());
            }
        }
    }

    public void setIngredientsData(List<IngredientData> newData) {
        ingredients.clear();
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (newData != null && i < newData.size() && newData.get(i) != null) {
                ingredients.add(newData.get(i).copy());
            } else {
                ingredients.add(IngredientData.empty());
            }
        }
    }

    public boolean setIngredient(int slotIndex, ItemStack item) {
        if (slotIndex >= 0 && slotIndex < ingredients.size() && item != null) {
            ingredients.set(slotIndex, item.isEmpty()
                    ? IngredientData.empty() : IngredientData.fromItem(item));
            return true;
        }
        return false;
    }

    public ItemStack getIngredient(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            return ingredients.get(slotIndex).getItemStack();
        }
        return ItemStack.EMPTY;
    }

    public List<ItemStack> getIngredients() {
        List<ItemStack> result = new ArrayList<>();
        for (IngredientData data : ingredients) {
            result.add(data.getDisplayStack().copy());
        }
        return result;
    }

    public void clearSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < ingredients.size()) {
            ingredients.set(slotIndex, IngredientData.empty());
        }
    }

    public void clearAllIngredients() {
        ingredients.replaceAll(ignored -> IngredientData.empty());
        resultItem = ItemStack.EMPTY;
    }

    public void fillEmptySlots(ItemStack item) {
        if (item == null || item.isEmpty()) return;
        IngredientData data = IngredientData.fromItem(item);
        for (int i = 0; i < ingredients.size(); i++) {
            if (ingredients.get(i).isEmpty()) {
                ingredients.set(i, data.copy());
            }
        }
    }

    public GridDimensions getGridDimensions() {
        if (currentRecipeType == null) {
            return new GridDimensions(3, 3, slotSpacing);
        }

        String category = currentRecipeType.getProperty("category", String.class);
        if ("cooking".equals(category) || currentRecipeType.supportsCookingSettings()) {
            return new GridDimensions(1, 1, slotSpacing);
        } else if (currentRecipeType.isAvaritiaType()
                || Boolean.TRUE.equals(currentRecipeType.getProperty("supportsTiers", Boolean.class))) {
            int size = DynamicRecipeBuilder.getGridSizeForTier(customTier);
            return new GridDimensions(size, size, slotSpacing);
        } else {
            return new GridDimensions(currentRecipeType.getMaxGridWidth(),
                    currentRecipeType.getMaxGridHeight(), slotSpacing);
        }
    }

    public record GridDimensions(int width, int height, int spacing) {
        public GridDimensions(int width, int height) {
            this(width, height, DEFAULT_SLOT_SPACING);
        }

        public int getTotalSlots() { return width * height; }
        public int getPixelWidth() { return width * spacing; }
        public int getPixelHeight() { return height * spacing; }
    }

    public List<IngredientSlot> getIngredientSlots() { return ingredientSlots; }
    public IngredientSlot getResultSlot() { return resultSlot; }
    public ItemStack getResultItem() { return resultItem; }
    public RecipeTypeDefinition getCurrentRecipeType() { return currentRecipeType; }
    public int getCustomTier() { return customTier; }

    public void setResultItem(ItemStack resultItem) {
        this.resultItem = resultItem == null ? ItemStack.EMPTY : resultItem.copy();
    }
}
