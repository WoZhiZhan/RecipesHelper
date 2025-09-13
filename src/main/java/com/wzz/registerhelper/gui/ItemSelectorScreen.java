package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ItemSelectorScreen extends Screen {
    private static final int ITEMS_PER_ROW = 9;
    private static final int ITEMS_PER_PAGE = 45; // 5行 x 9列
    private static final int SLOT_SIZE = 18;
    
    private final Screen parentScreen;
    private final Consumer<ItemStack> onItemSelected;
    
    private EditBox searchBox;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button cancelButton;
    
    private List<Item> allItems = new ArrayList<>();
    private List<Item> filteredItems = new ArrayList<>();
    private int currentPage = 0;
    private int maxPage = 0;
    
    private int leftPos, topPos;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    
    public ItemSelectorScreen(Screen parentScreen, Consumer<ItemStack> onItemSelected) {
        super(Component.literal("选择物品"));
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;

        collectAllItems();
        updateFilteredItems("");
    }
    
    private void collectAllItems() {
        allItems.clear();

        List<Item> commonItems = List.of(
            Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.IRON_INGOT,
            Items.STICK, Items.STONE, Items.COBBLESTONE,
            Items.REDSTONE, Items.GLOWSTONE_DUST, Items.ENDER_PEARL,
            Items.BLAZE_ROD, Items.NETHER_STAR, Items.DRAGON_EGG
        );
        
        allItems.addAll(commonItems);

        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (!allItems.contains(item) && item != Items.AIR) {
                allItems.add(item);
            }
        }
    }
    
    private void updateFilteredItems(String searchText) {
        filteredItems.clear();
        String lowerSearch = searchText.toLowerCase();
        
        for (Item item : allItems) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
            if (itemId != null) {
                String itemName = itemId.toString().toLowerCase();
                String displayName = item.getDescription().getString().toLowerCase();
                
                if (itemName.contains(lowerSearch) || displayName.contains(lowerSearch)) {
                    filteredItems.add(item);
                }
            }
        }
        
        maxPage = Math.max(0, (filteredItems.size() - 1) / ITEMS_PER_PAGE);
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }
    
    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        searchBox = new EditBox(this.font, leftPos + 8, topPos + 6, 160, 20, Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入物品名称..."));
        searchBox.setResponder(this::updateFilteredItems);
        addWidget(searchBox);

        prevPageButton = addRenderableWidget(Button.builder(
                Component.literal("<"),
                button -> previousPage())
                .bounds(leftPos + 8, topPos + 136, 20, 20)
                .build());

        nextPageButton = addRenderableWidget(Button.builder(
                Component.literal(">"),
                button -> nextPage())
                .bounds(leftPos + 148, topPos + 136, 20, 20)
                .build());

        cancelButton = addRenderableWidget(Button.builder(
                Component.literal("取消"),
                button -> onClose())
                .bounds(leftPos + 64, topPos + 136, 48, 20)
                .build());
        
        updateButtons();
    }
    
    private void updateButtons() {
        if (prevPageButton != null) {
            prevPageButton.active = currentPage > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = currentPage < maxPage;
        }
    }
    
    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updateButtons();
        }
    }
    
    private void nextPage() {
        if (currentPage < maxPage) {
            currentPage++;
            updateButtons();
        }
    }
    
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos - 10, 0xFFFFFF);

        renderItemGrid(guiGraphics, mouseX, mouseY);

        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        String pageInfo = String.format("第 %d/%d 页", currentPage + 1, maxPage + 1);
        guiGraphics.drawCenteredString(this.font, pageInfo, leftPos + GUI_WIDTH / 2, topPos + 160, 0x404040);

        renderItemTooltip(guiGraphics, mouseX, mouseY);
    }
    
    private void renderItemGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredItems.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int x = relativeIndex % ITEMS_PER_ROW;
            int y = relativeIndex / ITEMS_PER_ROW;
            
            int slotX = leftPos + 8 + x * SLOT_SIZE;
            int slotY = topPos + 32 + y * SLOT_SIZE;

            boolean isMouseOver = mouseX >= slotX && mouseX < slotX + SLOT_SIZE && 
                                mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
            
            if (isMouseOver) {
                guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFFFFFFFF);
            } else {
                guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF373737);
            }

            Item item = filteredItems.get(i);
            ItemStack itemStack = new ItemStack(item);
            
            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(itemStack, slotX + 1, slotY + 1);
            RenderSystem.disableDepthTest();
        }
    }
    
    private void renderItemTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredItems.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int x = relativeIndex % ITEMS_PER_ROW;
            int y = relativeIndex / ITEMS_PER_ROW;
            
            int slotX = leftPos + 8 + x * SLOT_SIZE;
            int slotY = topPos + 32 + y * SLOT_SIZE;
            
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && 
                mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                
                Item item = filteredItems.get(i);
                ItemStack itemStack = new ItemStack(item);
                
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(item.getDescription());
                
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                if (itemId != null) {
                    tooltip.add(Component.literal("§7" + itemId.toString()));
                }
                
                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // 左键点击
            int startIndex = currentPage * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredItems.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int x = relativeIndex % ITEMS_PER_ROW;
                int y = relativeIndex / ITEMS_PER_ROW;
                
                int slotX = leftPos + 8 + x * SLOT_SIZE;
                int slotY = topPos + 32 + y * SLOT_SIZE;
                
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && 
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    
                    Item item = filteredItems.get(i);
                    ItemStack itemStack = new ItemStack(item);

                    onItemSelected.accept(itemStack);
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