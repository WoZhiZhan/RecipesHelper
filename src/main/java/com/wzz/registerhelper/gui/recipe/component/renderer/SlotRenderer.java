package com.wzz.registerhelper.gui.recipe.component.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.gui.GuiTheme;
import com.wzz.registerhelper.gui.recipe.component.ComponentRenderer;
import com.wzz.registerhelper.gui.recipe.component.SlotComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 槽位渲染器
 */
public class SlotRenderer implements ComponentRenderer {
    private final SlotComponent component;
    private final Supplier<ItemStack> itemSupplier;
    private final Consumer<Integer> onLeftClick;
    private final Consumer<Integer> onRightClick;
    private boolean active = true;
    
    public SlotRenderer(SlotComponent component, 
                       Supplier<ItemStack> itemSupplier,
                       Consumer<Integer> onLeftClick,
                       Consumer<Integer> onRightClick) {
        this.component = component;
        this.itemSupplier = itemSupplier;
        this.onLeftClick = onLeftClick;
        this.onRightClick = onRightClick;
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!active) return;
        
        int x = component.getX();
        int y = component.getY();
        int width = component.getWidth();
        int height = component.getHeight();
        
        // 检查鼠标悬停
        boolean isMouseOver = mouseX >= x && mouseX < x + width &&
                             mouseY >= y && mouseY < y + height;
        
        GuiTheme.drawSlot(guiGraphics, x, y, width, height, isMouseOver);
        
        // 渲染物品
        ItemStack item = itemSupplier.get();
        if (!item.isEmpty()) {
            RenderSystem.enableDepthTest();
            float itemScale = Math.min(1.0F,
                    Math.max(0.125F, (Math.min(width, height) - 2) / 16.0F));
            guiGraphics.pose().pushPose();
            try {
                guiGraphics.pose().translate(x + 1, y + 1, 0);
                guiGraphics.pose().scale(itemScale, itemScale, 1.0F);
                guiGraphics.renderItem(item, 0, 0);
            } finally {
                guiGraphics.pose().popPose();
            }
            RenderSystem.disableDepthTest();
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active) return false;
        
        Rectangle bounds = getBounds();
        if (bounds.contains((int)mouseX, (int)mouseY)) {
            if (button == 0 && onLeftClick != null) {
                onLeftClick.accept(component.getSlotIndex());
                return true;
            } else if (button == 1 && onRightClick != null) {
                onRightClick.accept(component.getSlotIndex());
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
    
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
    
    @Override
    public Rectangle getBounds() {
        return component.getBounds();
    }
    
    @Override
    public boolean isActive() {
        return active;
    }
    
    @Override
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public ItemStack getItem() {
        return itemSupplier.get();
    }
}
