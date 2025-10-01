package com.wzz.registerhelper.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 材料类型选择器
 * 弹出小窗口让用户选择材料的输入方式
 */
@OnlyIn(Dist.CLIENT)
public class IngredientTypeSelector extends Screen {
    
    private static final int MENU_WIDTH = 160;
    private static final int MENU_HEIGHT = 140;
    private static final int BUTTON_HEIGHT = 25;
    private static final int BUTTON_SPACING = 5;
    
    private final Screen parentScreen;
    private final Consumer<SelectionType> onSelect;
    private final int slotIndex;
    
    private int menuX, menuY;
    
    public enum SelectionType {
        ALL_ITEMS("从所有物品选择"),
        INVENTORY("从背包选择（带NBT）"),
        TAG("选择标签"),
        CUSTOM_TAG("创建自定义标签");
        
        private final String displayName;
        
        SelectionType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public IngredientTypeSelector(Screen parentScreen, int slotIndex, Consumer<SelectionType> onSelect) {
        super(Component.literal("选择材料类型"));
        this.parentScreen = parentScreen;
        this.slotIndex = slotIndex;
        this.onSelect = onSelect;
    }
    
    @Override
    protected void init() {
        this.menuX = (this.width - MENU_WIDTH) / 2;
        this.menuY = (this.height - MENU_HEIGHT) / 2;
        
        int buttonY = menuY + 30;
        
        // 从所有物品选择
        addRenderableWidget(Button.builder(
                Component.literal(SelectionType.ALL_ITEMS.getDisplayName()),
                button -> handleSelection(SelectionType.ALL_ITEMS))
                .bounds(menuX + 10, buttonY, MENU_WIDTH - 20, BUTTON_HEIGHT)
                .build());
        buttonY += BUTTON_HEIGHT + BUTTON_SPACING;
        
        // 从背包选择
        addRenderableWidget(Button.builder(
                Component.literal(SelectionType.INVENTORY.getDisplayName()),
                button -> handleSelection(SelectionType.INVENTORY))
                .bounds(menuX + 10, buttonY, MENU_WIDTH - 20, BUTTON_HEIGHT)
                .build());
        buttonY += BUTTON_HEIGHT + BUTTON_SPACING;
        
        // 选择标签
        addRenderableWidget(Button.builder(
                Component.literal(SelectionType.TAG.getDisplayName()),
                button -> handleSelection(SelectionType.TAG))
                .bounds(menuX + 10, buttonY, MENU_WIDTH - 20, BUTTON_HEIGHT)
                .build());
        buttonY += BUTTON_HEIGHT + BUTTON_SPACING;
        
        // 创建自定义标签
        addRenderableWidget(Button.builder(
                Component.literal(SelectionType.CUSTOM_TAG.getDisplayName()),
                button -> handleSelection(SelectionType.CUSTOM_TAG))
                .bounds(menuX + 10, buttonY, MENU_WIDTH - 20, BUTTON_HEIGHT)
                .build());
    }

    private void handleSelection(SelectionType type) {
        // 先关闭当前屏幕，切换回父屏幕
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }

        // 然后执行回调，让父屏幕处理后续的屏幕切换
        if (onSelect != null) {
            onSelect.accept(type);
        }
    }
    
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 半透明背景
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
        
        // 菜单背景
        guiGraphics.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + MENU_HEIGHT + 10, 0xFFC6C6C6);
        guiGraphics.fill(menuX + 1, menuY + 1, menuX + MENU_WIDTH - 1, menuY + MENU_HEIGHT - 1 + 10, 0xFF8B8B8B);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, menuX + MENU_WIDTH / 2, menuY + 10, 0x404040);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}