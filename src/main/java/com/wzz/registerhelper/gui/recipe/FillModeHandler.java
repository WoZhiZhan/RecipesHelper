package com.wzz.registerhelper.gui.recipe;

import com.wzz.registerhelper.gui.GuiText;

import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * 填充模式处理器
 * 负责处理不同填充模式的逻辑
 */
public class FillModeHandler {
    private FillMode currentMode = FillMode.NORMAL;
    private ItemStack brushItem = ItemStack.EMPTY;
    
    // 回调函数
    private Consumer<String> errorCallback;
    private Consumer<Integer> itemSelectorCallback;
    private Runnable brushSelectorCallback;

    public FillModeHandler(Consumer<String> errorCallback, 
                          Consumer<Integer> itemSelectorCallback,
                          Runnable brushSelectorCallback) {
        this.errorCallback = errorCallback;
        this.itemSelectorCallback = itemSelectorCallback;
        this.brushSelectorCallback = brushSelectorCallback;
    }

    /**
     * 处理槽位点击事件
     */
    public void handleSlotClick(SlotManager slotManager, int slotIndex, boolean isRightClick) {
        if (slotIndex < 0 || slotIndex >= slotManager.getIngredients().size()) {
            return;
        }

        // 右键清空槽位
        if (isRightClick) {
            slotManager.clearSlot(slotIndex);
            return;
        }

        // 根据填充模式处理左键点击
        switch (currentMode) {
            case NORMAL -> handleNormalMode(slotIndex);
            case BRUSH -> handleBrushMode(slotManager, slotIndex);
            case FILL -> handleFillMode(slotManager);
        }
    }

    /**
     * 处理普通模式 - 打开物品选择器
     */
    private void handleNormalMode(int slotIndex) {
        if (itemSelectorCallback != null) {
            itemSelectorCallback.accept(slotIndex);
        }
    }

    /**
     * 处理画笔模式 - 用画笔物品填充单个槽位
     */
    private void handleBrushMode(SlotManager slotManager, int slotIndex) {
        if (brushItem.isEmpty()) {
            if (errorCallback != null) {
                errorCallback.accept(GuiText.string("registerhelper.message.recipe.select_brush_first"));
            }
            return;
        }

        slotManager.setIngredient(slotIndex, brushItem);
    }

    /**
     * 处理填充模式 - 用画笔物品填充所有空槽位
     */
    private void handleFillMode(SlotManager slotManager) {
        if (brushItem.isEmpty()) {
            if (errorCallback != null) {
                errorCallback.accept(GuiText.string("registerhelper.message.recipe.select_brush_first"));
            }
            return;
        }

        slotManager.fillEmptySlots(brushItem);
    }

    /**
     * 打开画笔物品选择器
     */
    public void openBrushSelector() {
        if (brushSelectorCallback != null) {
            brushSelectorCallback.run();
        }
    }

    /**
     * 获取填充模式提示文本
     */
    public String getHintText() {
        String hint = switch (currentMode) {
            case NORMAL -> GuiText.string("registerhelper.gui.recipe_creator.fill_hint.normal");
            case BRUSH -> GuiText.string("registerhelper.gui.recipe_creator.fill_hint.brush");
            case FILL -> GuiText.string("registerhelper.gui.recipe_creator.fill_hint.fill");
        };

        if (currentMode != FillMode.NORMAL && !brushItem.isEmpty()) {
            hint = GuiText.string("registerhelper.gui.recipe_creator.fill_hint.with_brush",
                    hint, brushItem.getHoverName());
        }

        return hint;
    }

    /**
     * 获取槽位工具提示文本
     */
    public String getSlotTooltip() {
        return switch (currentMode) {
            case NORMAL -> GuiText.string("registerhelper.tooltip.recipe_slot.mode_normal");
            case BRUSH -> GuiText.string("registerhelper.tooltip.recipe_slot.mode_brush");
            case FILL -> GuiText.string("registerhelper.tooltip.recipe_slot.mode_fill");
        };
    }

    /**
     * 检查是否需要显示画笔选择按钮
     */
    public boolean shouldShowBrushSelector() {
        return currentMode == FillMode.BRUSH || currentMode == FillMode.FILL;
    }

    /**
     * 重置填充模式状态
     */
    public void reset() {
        currentMode = FillMode.NORMAL;
        brushItem = ItemStack.EMPTY;
    }

    // Getters and Setters
    public FillMode getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(FillMode mode) {
        this.currentMode = mode;
    }

    public ItemStack getBrushItem() {
        return brushItem;
    }

    public void setBrushItem(ItemStack brushItem) {
        this.brushItem = brushItem.copy();
    }

    public boolean hasBrushItem() {
        return !brushItem.isEmpty();
    }
}
