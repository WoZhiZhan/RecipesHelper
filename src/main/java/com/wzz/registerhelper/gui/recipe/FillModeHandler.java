package com.wzz.registerhelper.gui.recipe;

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
                errorCallback.accept("请先选择画笔物品！");
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
                errorCallback.accept("请先选择画笔物品！");
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
            case NORMAL -> "普通模式: 点击槽位选择物品";
            case BRUSH -> "画笔模式: 点击槽位填充画笔物品";
            case FILL -> "填充模式: 点击任意槽位填充所有空槽";
        };

        if (currentMode != FillMode.NORMAL && !brushItem.isEmpty()) {
            hint += " [当前画笔: " + brushItem.getHoverName().getString() + "]";
        }

        return hint;
    }

    /**
     * 获取槽位工具提示文本
     */
    public String getSlotTooltip() {
        return switch (currentMode) {
            case NORMAL -> "左键: 选择物品\n右键: 清空";
            case BRUSH -> "左键: 填充画笔物品\n右键: 清空";
            case FILL -> "左键: 填充所有空槽\n右键: 清空";
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