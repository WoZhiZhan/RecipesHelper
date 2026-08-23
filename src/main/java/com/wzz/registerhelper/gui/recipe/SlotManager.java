package com.wzz.registerhelper.gui.recipe;

import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeBuilder;
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
    /** 默认槽位间距，可通过 setSlotSpacing 动态缩小以适应小屏幕 */
    private int slotSpacing = 18;
    /** 槽位间距的硬性下限，低于此值物品图标将无法辨认 */
    public static final int MIN_SLOT_SPACING = 4;
    public static final int DEFAULT_SLOT_SPACING = 18;

    private List<RecipeComponent> components = new ArrayList<>();
    private final List<IngredientSlot> ingredientSlots = new ArrayList<>();
    private final List<IngredientData> ingredients = new ArrayList<>(); // 改用IngredientData
    private final List<SlotComponent.SlotRole> ingredientRoles = new ArrayList<>();
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
    private double layoutScale = 1.0;

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

    /**
     * 动态设置槽位间距（用于大格子在小屏幕上自适应缩放）。
     * 必须在 setRecipeType / updateCoordinates 之前调用才能生效。
     */
    public void setSlotSpacing(int spacing) {
        this.slotSpacing = Math.max(MIN_SLOT_SPACING, Math.min(DEFAULT_SLOT_SPACING, spacing));
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
    }

    public void setGridTopOffset(int offset) {
        this.gridTopOffset = Math.max(0, offset);
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

        String layoutId = currentRecipeType.getProperty("layout", String.class);
        if (layoutId != null) {
            updateCustomLayoutPositions(layoutId);
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
        int startX = baseX;
        int startY = baseY + gridTopOffset;

        for (int i = 0; i < ingredientSlots.size() && i < gridWidth * gridHeight; i++) {
            int x = i % gridWidth;
            int y = i / gridWidth;
            int slotX = startX + x * slotSpacing;
            int slotY = startY + y * slotSpacing;
            int slotSize = Math.min(18, slotSpacing);
            ingredientSlots.set(i, new IngredientSlot(
                    slotX, slotY, slotSize, slotSize, i));
        }
    }

    private void initializeSlots() {
        components.clear();
        ingredientSlots.clear();
        ingredients.clear();
        ingredientRoles.clear();
        resultSlot = null;

        if (currentRecipeType == null) return;

        String layoutId = currentRecipeType.getProperty("layout", String.class);
        if (layoutId != null) {
            initializeCustomLayout(layoutId);
        } else {
            initializeTraditionalLayout();
        }

        if (resultSlot == null) {
            resultSlot = new IngredientSlot(rightPanelX + 20,
                    baseY + resultSlotOffset, -1);
        }
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
        ingredientRoles.clear();

        components = generateFittedComponents(layout);
        for (RecipeComponent component : components) {
            if (component instanceof SlotComponent slotComp) {
                if (slotComp.getRole() == SlotComponent.SlotRole.OUTPUT) {
                    resultSlot = new IngredientSlot(slotComp.getX(), slotComp.getY(),
                            slotComp.getWidth(), slotComp.getHeight(), -1);
                    continue;
                }
                int index = slotComp.getSlotIndex();
                ingredientSlots.add(new IngredientSlot(
                        slotComp.getX(),
                        slotComp.getY(),
                        slotComp.getWidth(),
                        slotComp.getHeight(),
                        index
                ));
                ingredients.add(IngredientData.empty());
                ingredientRoles.add(slotComp.getRole());
            }
        }
    }

    private void updateCustomLayoutPositions(String layoutId) {
        RecipeLayout layout = LayoutManager.getLayout(layoutId);
        if (layout == null) {
            updateCustomSlotPositions();
            return;
        }

        List<IngredientData> oldIngredients = new ArrayList<>(ingredients);
        components = generateFittedComponents(layout);
        ingredientSlots.clear();
        ingredients.clear();
        ingredientRoles.clear();
        resultSlot = null;

        int ingredientIndex = 0;
        for (RecipeComponent component : components) {
            if (component instanceof SlotComponent slotComp) {
                if (slotComp.getRole() == SlotComponent.SlotRole.OUTPUT) {
                    resultSlot = new IngredientSlot(slotComp.getX(), slotComp.getY(),
                            slotComp.getWidth(), slotComp.getHeight(), -1);
                    continue;
                }
                ingredientSlots.add(new IngredientSlot(
                        slotComp.getX(), slotComp.getY(),
                        slotComp.getWidth(), slotComp.getHeight(),
                        slotComp.getSlotIndex()));
                ingredients.add(ingredientIndex < oldIngredients.size()
                        ? oldIngredients.get(ingredientIndex) : IngredientData.empty());
                ingredientRoles.add(slotComp.getRole());
                ingredientIndex++;
            }
        }
        if (resultSlot == null) {
            resultSlot = new IngredientSlot(rightPanelX + 20,
                    baseY + resultSlotOffset, -1);
        }
    }

    private List<RecipeComponent> generateFittedComponents(RecipeLayout layout) {
        int layoutOriginY = baseY + gridTopOffset;
        List<RecipeComponent> generated = layout.generateComponents(
                baseX, layoutOriginY, customTier);
        var bounds = layout.getBounds(customTier);
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            layoutScale = 1.0;
            return generated;
        }

        double scale = Math.min(1.0, Math.min(
                layoutAreaWidth / (double) bounds.width,
                layoutAreaHeight / (double) bounds.height));
        layoutScale = scale;
        if (scale < 1.0) {
            for (RecipeComponent component : generated) {
                int relativeX = component.getX() - baseX;
                int relativeY = component.getY() - layoutOriginY;
                component.setPosition(
                        baseX + (int) Math.round(relativeX * scale),
                        layoutOriginY + (int) Math.round(relativeY * scale));
                int minimumWidth = switch (component.getType()) {
                    case SLOT -> 4;
                    case NUMBER_INPUT, STRING_INPUT, LABEL -> 14;
                };
                int minimumHeight = 4;
                component.setSize(
                        Math.max(minimumWidth, (int) Math.round(component.getWidth() * scale)),
                        Math.max(minimumHeight, (int) Math.round(component.getHeight() * scale)));
            }
        }
        preventComponentOverlap(generated, scale);
        moveEditorComponentsBelowSlots(generated);
        return generated;
    }

    private void moveEditorComponentsBelowSlots(List<RecipeComponent> generated) {
        int maxSlotBottom = Integer.MIN_VALUE;
        int minEditorY = Integer.MAX_VALUE;
        for (RecipeComponent component : generated) {
            if (component.getType() == RecipeComponent.ComponentType.SLOT) {
                maxSlotBottom = Math.max(maxSlotBottom,
                        component.getY() + component.getHeight());
            } else {
                minEditorY = Math.min(minEditorY, component.getY());
            }
        }
        if (maxSlotBottom == Integer.MIN_VALUE || minEditorY == Integer.MAX_VALUE) return;
        String layoutId = currentRecipeType == null
                ? "" : currentRecipeType.getProperty("layout", String.class);
        int editorGap = switch (layoutId) {
            case "terra_plate" -> 58;
            case "mana_infusion", "runic_altar" -> 50;
            default -> 28;
        };
        int shift = maxSlotBottom + editorGap - minEditorY;
        moveNonSlotComponentsBy(generated, shift);
    }

    public int moveNonSlotComponentsBelow(int targetY) {
        int minEditorY = Integer.MAX_VALUE;
        for (RecipeComponent component : components) {
            if (component.getType() != RecipeComponent.ComponentType.SLOT) {
                minEditorY = Math.min(minEditorY, component.getY());
            }
        }
        if (minEditorY == Integer.MAX_VALUE) return 0;
        int shift = Math.max(0, targetY - minEditorY);
        moveNonSlotComponentsBy(components, shift);
        return shift;
    }

    private void moveNonSlotComponentsBy(List<RecipeComponent> generated, int shift) {
        if (shift <= 0) return;
        for (RecipeComponent component : generated) {
            if (component.getType() != RecipeComponent.ComponentType.SLOT) {
                component.setPosition(component.getX(), component.getY() + shift);
            }
        }
    }

    private void preventComponentOverlap(List<RecipeComponent> generated, double scale) {
        int minimumSlotSize = Math.max(6,
                Math.min(14, (int) Math.round(18.0 * scale)));
        for (RecipeComponent component : generated) {
            if (component.getType() == RecipeComponent.ComponentType.SLOT) {
                // A slot is an interactive target, not a text-flow element.
                // Keep it usable while preserving the layout's fitted scale.
                component.setSize(Math.max(minimumSlotSize, component.getWidth()),
                        Math.max(minimumSlotSize, component.getHeight()));
            }
        }
    }

    public int getBaseX() {
        return baseX;
    }

    public int getBaseY() {
        return baseY;
    }

    public int getLayoutOriginX() {
        return baseX;
    }

    public int getLayoutOriginY() {
        return baseY + gridTopOffset;
    }

    public List<RecipeComponent> getComponents() {
        return components;
    }

    public double getLayoutScale() {
        return layoutScale;
    }

    public int getFittedLayoutWidth() {
        RecipeLayout layout = getCurrentLayout();
        if (layout == null || layout.getBounds(customTier) == null) return 0;
        return Math.max(1, (int) Math.round(layout.getBounds(customTier).width * layoutScale));
    }

    public int getFittedLayoutHeight() {
        RecipeLayout layout = getCurrentLayout();
        if (layout == null || layout.getBounds(customTier) == null) return 0;
        return Math.max(1, (int) Math.round(layout.getBounds(customTier).height * layoutScale));
    }

    private RecipeLayout getCurrentLayout() {
        if (currentRecipeType == null) return null;
        String layoutId = currentRecipeType.getProperty("layout", String.class);
        return layoutId == null ? null : LayoutManager.getLayout(layoutId);
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
        int gridSize = DynamicRecipeBuilder.getGridSizeForTier(actualTier);
        initializeGridSlots(gridSize, gridSize);
    }

    private void initializeCookingSlots() {
        int slotSize = Math.min(18, slotSpacing);
        ingredientSlots.add(new IngredientSlot(
                baseX + slotSpacing, baseY + gridTopOffset + 20,
                slotSize, slotSize, 0));
        ingredients.add(IngredientData.empty());
        ingredientRoles.add(SlotComponent.SlotRole.INPUT);
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
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                int slotX = startX + x * slotSpacing;
                int slotY = startY + y * slotSpacing;
                int slotSize = Math.min(18, slotSpacing);
                ingredientSlots.add(new IngredientSlot(
                        slotX, slotY, slotSize, slotSize, y * gridWidth + x));
                ingredients.add(IngredientData.empty());
                ingredientRoles.add(SlotComponent.SlotRole.INPUT);
            }
        }
    }
    
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
            result.add(data.getDisplayStack().copy());
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
        ingredients.replaceAll(ignored -> IngredientData.empty());
        resultItem = ItemStack.EMPTY;
    }

    /**
     * 填充所有空槽位（兼容旧代码）
     */
    public void fillEmptySlots(ItemStack item) {
        IngredientData data = IngredientData.fromItem(item);
        fillEmptySlots(data);
    }

    public void fillEmptySlots(IngredientData data) {
        if (data == null || data.isEmpty()) return;
        for (int i = 0; i < ingredients.size(); i++) {
            if (isInputSlot(i) && ingredients.get(i).isEmpty()) {
                ingredients.set(i, data.copy());
            }
        }
    }

    /**
     * 用 IngredientData 列表直接设置槽位（保留 ignoreKeys 等信息）
     */
    public void setIngredientsData(List<IngredientData> newData) {
        ingredients.clear();
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (i < newData.size()) {
                ingredients.add(newData.get(i).copy());
            } else {
                ingredients.add(IngredientData.empty());
            }
        }
    }

    /**
     * 获取当前网格大小
     */
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
            return new GridDimensions(
                    currentRecipeType.getMaxGridWidth(),
                    currentRecipeType.getMaxGridHeight(),
                    slotSpacing
            );
        }
    }

    public record GridDimensions(int width, int height, int spacing) {
        /** 兼容旧调用：默认 20px 间距 */
        public GridDimensions(int width, int height) {
            this(width, height, DEFAULT_SLOT_SPACING);
        }

        public int getTotalSlots() {
            return width * height;
        }

        public int getPixelWidth() {
            return width * spacing;
        }

        public int getPixelHeight() {
            return height * spacing;
        }
    }

    // Getters
    public List<IngredientSlot> getIngredientSlots() { return ingredientSlots; }
    public List<SlotComponent.SlotRole> getIngredientRoles() {
        return new ArrayList<>(ingredientRoles);
    }

    public SlotComponent.SlotRole getIngredientRole(int index) {
        if (index >= 0 && index < ingredientRoles.size()) {
            return ingredientRoles.get(index);
        }
        return SlotComponent.SlotRole.INPUT;
    }

    public boolean isInputSlot(int index) {
        return getIngredientRole(index) == SlotComponent.SlotRole.INPUT;
    }

    /** Reproduces JEI's dynamic circular ingredient placement for Botania. */
    public void reflowSpecialSlots() {
        String layout = currentRecipeType == null
                ? "" : currentRecipeType.getProperty("layout", String.class);
        if (!"terra_plate".equals(layout) && !"runic_altar".equals(layout)
                && !"petal_apothecary".equals(layout)) {
            return;
        }

        long centerSumX = 0;
        long centerSumY = 0;
        int centerCount = 0;
        for (RecipeComponent component : components) {
            if (component.getType() != RecipeComponent.ComponentType.SLOT) continue;
            if (component instanceof SlotComponent slot
                    && slot.getRole() != SlotComponent.SlotRole.INPUT) continue;
            centerSumX += component.getX() + component.getWidth() / 2;
            centerSumY += component.getY() + component.getHeight() / 2;
            centerCount++;
        }
        if (centerCount == 0) return;
        int centerX = (int) (centerSumX / centerCount);
        int centerY = (int) (centerSumY / centerCount);
        int radius = Math.max(12, (int) Math.round(32.0 * layoutScale));

        List<Integer> active = new ArrayList<>();
        int firstEmpty = -1;
        for (IngredientSlot slot : ingredientSlots) {
            if (!isInputSlot(slot.index())) continue;
            if (!getIngredientData(slot.index()).isEmpty()) {
                active.add(slot.index());
            } else if (firstEmpty < 0) {
                firstEmpty = slot.index();
            }
        }
        if (firstEmpty >= 0) active.add(firstEmpty);
        if (active.isEmpty()) return;

        double step = Math.PI * 2.0 / active.size();
        for (IngredientSlot slot : new ArrayList<>(ingredientSlots)) {
            if (!isInputSlot(slot.index())) continue;
            int position = active.indexOf(slot.index());
            if (position < 0) {
                replaceIngredientSlot(slot, -1000, -1000);
                continue;
            }
            double angle = position * step - Math.PI / 2.0;
            int x = (int) Math.round(centerX + Math.cos(angle) * radius - slot.width() / 2.0);
            int y = (int) Math.round(centerY + Math.sin(angle) * radius - slot.height() / 2.0);
            replaceIngredientSlot(slot, x, y);
        }
    }

    private void replaceIngredientSlot(IngredientSlot old, int x, int y) {
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (ingredientSlots.get(i).index() == old.index()) {
                ingredientSlots.set(i, new IngredientSlot(x, y,
                        old.width(), old.height(), old.index()));
                return;
            }
        }
    }

    public IngredientSlot getResultSlot() { return resultSlot; }
    public ItemStack getResultItem() { return resultItem; }
    public RecipeTypeDefinition getCurrentRecipeType() { return currentRecipeType; }
    public int getCustomTier() { return customTier; }

    // Setters
    public void setResultItem(ItemStack resultItem) {
        this.resultItem = resultItem.copy();
    }
}
