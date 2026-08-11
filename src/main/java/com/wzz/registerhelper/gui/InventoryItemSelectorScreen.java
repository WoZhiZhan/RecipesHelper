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
    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 28;
    private static final int PREFERRED_WIDTH = 380;
    private static final int PREFERRED_HEIGHT = 300;
    private static final int MIN_WIDTH = 120;
    private static final int MIN_HEIGHT = 120;
    
    private final Screen parentScreen;
    private final Consumer<ItemStack> onItemSelected;
    
    private final List<ItemStack> inventoryItems = new ArrayList<>();
    
    private int guiWidth, guiHeight;
    private int leftPos, topPos;
    private int slotsPerRow = 9;
    private int rowsPerPage = 5;
    private int itemsPerPage = 45;
    private int currentPage;
    private int maxPage;
    private GuiLayoutHelper.Bounds gridBounds;
    private Button cancelButton;
    private Button prevPageButton;
    private Button nextPageButton;
    
    public InventoryItemSelectorScreen(Screen parentScreen, Consumer<ItemStack> onItemSelected) {
        super(GuiText.component("registerhelper.gui.inventory_selector.title"));
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
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(this.width, this.height,
                PREFERRED_WIDTH, PREFERRED_HEIGHT, MIN_WIDTH, MIN_HEIGHT, 8, 8);
        this.guiWidth = panel.width();
        this.guiHeight = panel.height();
        this.leftPos = panel.x();
        this.topPos = panel.y();
        this.slotsPerRow = Math.max(1, (guiWidth - 16) / SLOT_SIZE);
        this.rowsPerPage = Math.max(1,
                (guiHeight - HEADER_HEIGHT - FOOTER_HEIGHT) / SLOT_SIZE);
        this.itemsPerPage = slotsPerRow * rowsPerPage;
        this.maxPage = Math.max(0, (inventoryItems.size() - 1) / itemsPerPage);
        this.currentPage = Math.min(currentPage, maxPage);
        this.gridBounds = new GuiLayoutHelper.Bounds(leftPos + 8, topPos + HEADER_HEIGHT,
                slotsPerRow * SLOT_SIZE, rowsPerPage * SLOT_SIZE);
        int buttonY = topPos + guiHeight - 24;

        prevPageButton = addRenderableWidget(Button.builder(
                        Component.literal("<"), button -> previousPage())
                .bounds(leftPos + 8, buttonY, 20, 20)
                .build());

        nextPageButton = addRenderableWidget(Button.builder(
                        Component.literal(">"), button -> nextPage())
                .bounds(leftPos + guiWidth - 28, buttonY, 20, 20)
                .build());
        
        cancelButton = addRenderableWidget(Button.builder(
                GuiText.component("registerhelper.gui.common.cancel"),
                button -> onClose())
                .bounds(leftPos + (guiWidth - 48) / 2, buttonY, 48, 20)
                .build());
        updatePageButtons();
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updatePageButtons();
        }
    }

    private void nextPage() {
        if (currentPage < maxPage) {
            currentPage++;
            updatePageButtons();
        }
    }

    private void updatePageButtons() {
        if (prevPageButton != null) prevPageButton.active = currentPage > 0;
        if (nextPageButton != null) nextPageButton.active = currentPage < maxPage;
    }
    
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        GuiTheme.drawBackdrop(guiGraphics, this.width, this.height);
        
        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(leftPos, topPos, guiWidth, guiHeight);
        GuiTheme.drawPanel(guiGraphics, panel, 0, GuiTheme.HEADER_ACCENT);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title,
                leftPos + guiWidth / 2, topPos - 10, 0xFFFFFF);
        
        // 提示文字
        if (inventoryItems.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    GuiText.component("registerhelper.gui.inventory_selector.empty"),
                    leftPos + guiWidth / 2, gridBounds.centerY(), GuiTheme.TEXT_MUTED);
        } else {
            String hintText = GuiLayoutHelper.ellipsis(this.font,
                    GuiText.string("registerhelper.gui.inventory_selector.hint"),
                    Math.max(1, guiWidth - 16));
            guiGraphics.drawString(this.font, hintText, leftPos + 8, topPos + 6, GuiTheme.TEXT, false);
            String pageText = GuiText.string("registerhelper.gui.common.page", currentPage + 1, maxPage + 1);
            guiGraphics.drawString(this.font, pageText, leftPos + 8, topPos + 18,
                    GuiTheme.TEXT_MUTED, false);
        }
        
        // 渲染物品网格
        renderItemGrid(guiGraphics, mouseX, mouseY);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 渲染工具提示
        renderItemTooltip(guiGraphics, mouseX, mouseY);
    }
    
    private void renderItemGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startY = gridBounds.y();
        int startX = gridBounds.x();
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, inventoryItems.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int x = relativeIndex % slotsPerRow;
            int y = relativeIndex / slotsPerRow;
            
            int slotX = startX + x * SLOT_SIZE;
            int slotY = startY + y * SLOT_SIZE;
            
            // 检查鼠标是否悬停
            boolean isMouseOver = mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                                mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
            
            // 槽位背景
            GuiTheme.drawSlot(guiGraphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, isMouseOver);
            
            // 渲染物品
            ItemStack item = inventoryItems.get(i);
            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(item, slotX + 1, slotY + 1);
            guiGraphics.renderItemDecorations(this.font, item, slotX + 1, slotY + 1);
            RenderSystem.disableDepthTest();
        }
    }
    
    private void renderItemTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startY = gridBounds.y();
        int startX = gridBounds.x();
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, inventoryItems.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int x = relativeIndex % slotsPerRow;
            int y = relativeIndex / slotsPerRow;
            
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
                    tooltip.add(GuiText.component("registerhelper.tooltip.item.quantity", item.getCount()));
                }
                
                // NBT提示
                if (item.hasTag()) {
                    tooltip.add(GuiText.component("registerhelper.tooltip.item.has_nbt"));
                    
                    CompoundTag tag = item.getTag();
                    if (tag != null) {
                        // 显示一些关键NBT信息
                        if (tag.contains("Enchantments")) {
                            tooltip.add(GuiText.component("registerhelper.tooltip.item.enchantments"));
                        }
                        if (tag.contains("display")) {
                            tooltip.add(GuiText.component("registerhelper.tooltip.item.custom_display"));
                        }
                        if (tag.contains("Damage")) {
                            tooltip.add(GuiText.component("registerhelper.tooltip.item.durability",
                                    item.getMaxDamage() - tag.getInt("Damage"), item.getMaxDamage()));
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
            int startY = gridBounds.y();
            int startX = gridBounds.x();
            int startIndex = currentPage * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, inventoryItems.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int x = relativeIndex % slotsPerRow;
                int y = relativeIndex / slotsPerRow;
                
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) previousPage(); else if (delta < 0) nextPage();
        return true;
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
