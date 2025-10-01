package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 从玩家背包选择物品（包含NBT数据）
 */
@OnlyIn(Dist.CLIENT)
public class InventoryItemSelectorScreen extends Screen {
    
    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_PER_ROW = 9;
    private static final int GUI_WIDTH = 200;
    private static final int GUI_HEIGHT = 220;
    
    private final Screen parentScreen;
    private final Consumer<ItemStack> onItemSelected;
    
    private final List<ItemStack> inventoryItems = new ArrayList<>();
    
    private int leftPos, topPos;
    private Button cancelButton;
    
    public InventoryItemSelectorScreen(Screen parentScreen, Consumer<ItemStack> onItemSelected) {
        super(Component.literal("从背包选择"));
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;
        
        collectInventoryItems();
    }
    
    /**
     * 收集玩家背包物品（带NBT）
     */
    private void collectInventoryItems() {
        inventoryItems.clear();
        
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        
        // 主背包 (9-35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
                inventoryItems.add(stack.copy());
            }
        }
        
        // 快捷栏 (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
                inventoryItems.add(stack.copy());
            }
        }
        
        // 盔甲栏 (36-39)
        for (int i = 36; i < 40; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
                inventoryItems.add(stack.copy());
            }
        }
        
        // 副手 (40)
        ItemStack offhand = player.getInventory().getItem(40);
        if (!offhand.isEmpty() && offhand.getItem() != Items.AIR) {
            inventoryItems.add(offhand.copy());
        }
    }
    
    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
        
        cancelButton = addRenderableWidget(Button.builder(
                Component.literal("取消"),
                button -> onClose())
                .bounds(leftPos + (GUI_WIDTH - 48) / 2, topPos + GUI_HEIGHT - 24, 48, 20)
                .build());
    }
    
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        // 背景
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos - 10, 0xFFFFFF);
        
        // 提示文字
        if (inventoryItems.isEmpty()) {
            String emptyText = "背包中没有物品";
            guiGraphics.drawCenteredString(this.font, emptyText, leftPos + GUI_WIDTH / 2, topPos + 50, 0x666666);
        } else {
            String hintText = "点击选择物品（将保留NBT数据）";
            guiGraphics.drawString(this.font, hintText, leftPos + 8, topPos + 6, 0x404040, false);
        }
        
        // 渲染物品网格
        renderItemGrid(guiGraphics, mouseX, mouseY);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 渲染工具提示
        renderItemTooltip(guiGraphics, mouseX, mouseY);
    }
    
    private void renderItemGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startY = topPos + 30;
        int startX = leftPos + 8;
        
        for (int i = 0; i < inventoryItems.size(); i++) {
            int x = i % SLOTS_PER_ROW;
            int y = i / SLOTS_PER_ROW;
            
            int slotX = startX + x * SLOT_SIZE;
            int slotY = startY + y * SLOT_SIZE;
            
            // 检查鼠标是否悬停
            boolean isMouseOver = mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                                mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
            
            // 槽位背景
            int bgColor = isMouseOver ? 0x80FFFFFF : 0xFF373737;
            guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, bgColor);
            
            // 槽位边框
            int borderColor = isMouseOver ? 0xFFFFFFFF : 0xFF8B8B8B;
            guiGraphics.fill(slotX - 1, slotY - 1, slotX + SLOT_SIZE + 1, slotY, borderColor);
            guiGraphics.fill(slotX - 1, slotY + SLOT_SIZE, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE + 1, borderColor);
            guiGraphics.fill(slotX - 1, slotY, slotX, slotY + SLOT_SIZE, borderColor);
            guiGraphics.fill(slotX + SLOT_SIZE, slotY, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE, borderColor);
            
            // 渲染物品
            ItemStack item = inventoryItems.get(i);
            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(item, slotX + 1, slotY + 1);
            guiGraphics.renderItemDecorations(this.font, item, slotX + 1, slotY + 1);
            RenderSystem.disableDepthTest();
        }
    }
    
    private void renderItemTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startY = topPos + 30;
        int startX = leftPos + 8;
        
        for (int i = 0; i < inventoryItems.size(); i++) {
            int x = i % SLOTS_PER_ROW;
            int y = i / SLOTS_PER_ROW;
            
            int slotX = startX + x * SLOT_SIZE;
            int slotY = startY + y * SLOT_SIZE;
            
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                
                ItemStack item = inventoryItems.get(i);
                List<Component> tooltip = new ArrayList<>();
                
                // 物品名称
                tooltip.add(item.getHoverName());
                
                // 数量
                if (item.getCount() > 1) {
                    tooltip.add(Component.literal("§7数量: " + item.getCount()));
                }
                
                // NBT提示
                if (item.hasTag()) {
                    tooltip.add(Component.literal("§b带有NBT数据"));
                    
                    CompoundTag tag = item.getTag();
                    if (tag != null) {
                        // 显示一些关键NBT信息
                        if (tag.contains("Enchantments")) {
                            tooltip.add(Component.literal("§d附魔"));
                        }
                        if (tag.contains("display")) {
                            tooltip.add(Component.literal("§e自定义显示"));
                        }
                        if (tag.contains("Damage")) {
                            tooltip.add(Component.literal("§7耐久: " + 
                                (item.getMaxDamage() - tag.getInt("Damage")) + "/" + item.getMaxDamage()));
                        }
                    }
                }
                
                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // 左键点击
            int startY = topPos + 30;
            int startX = leftPos + 8;
            
            for (int i = 0; i < inventoryItems.size(); i++) {
                int x = i % SLOTS_PER_ROW;
                int y = i / SLOTS_PER_ROW;
                
                int slotX = startX + x * SLOT_SIZE;
                int slotY = startY + y * SLOT_SIZE;
                
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    
                    ItemStack selectedItem = inventoryItems.get(i);
                    onItemSelected.accept(selectedItem.copy());
                    onClose();
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC键
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