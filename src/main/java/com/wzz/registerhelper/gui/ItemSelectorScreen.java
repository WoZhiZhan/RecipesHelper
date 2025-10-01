package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.util.PinyinSearchHelper;
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

import java.util.*;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ItemSelectorScreen extends Screen {
    private static final int ITEMS_PER_ROW = 11;
    private static final int ITEMS_PER_PAGE = 77;
    private static final int SLOT_SIZE = 18;

    private final Screen parentScreen;
    private final Consumer<ItemStack> onItemSelected;

    private EditBox searchBox;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button cancelButton;

    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();

    // 使用拼音搜索助手
    private final PinyinSearchHelper<ItemStack> searchHelper;

    private int currentPage = 0;
    private int maxPage = 0;

    private int leftPos, topPos;
    private static final int GUI_WIDTH = 220;
    private static final int GUI_HEIGHT = 200;

    public ItemSelectorScreen(Screen parentScreen, Consumer<ItemStack> onItemSelected) {
        super(Component.literal("选择物品"));
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;

        // 初始化搜索助手
        this.searchHelper = new PinyinSearchHelper<>(
                item -> item.getItem().getDescription().getString(),  // 显示名称获取器
                item -> {  // ID获取器
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem());
                    return id != null ? id.toString() : "";
                }
        );

        collectAllItems();
        searchHelper.buildCache(allItems);
        updateFilteredItems("");
    }

    private void collectAllItems() {
        allItems.clear();

        List<ItemStack> commonItems = List.of(
                Items.DIAMOND.getDefaultInstance(), Items.EMERALD.getDefaultInstance(),
                Items.GOLD_INGOT.getDefaultInstance(), Items.IRON_INGOT.getDefaultInstance(),
                Items.STICK.getDefaultInstance(), Items.STONE.getDefaultInstance(),
                Items.COBBLESTONE.getDefaultInstance(), Items.REDSTONE.getDefaultInstance(),
                Items.GLOWSTONE_DUST.getDefaultInstance(), Items.ENDER_PEARL.getDefaultInstance(),
                Items.BLAZE_ROD.getDefaultInstance(), Items.NETHER_STAR.getDefaultInstance(),
                Items.DRAGON_EGG.getDefaultInstance()
        );

        allItems.addAll(commonItems);

        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ItemStack stack = item.getDefaultInstance();
            if (!allItems.contains(stack) && item != Items.AIR) {
                allItems.add(stack);
            }
        }
    }

    private void updateFilteredItems(String searchText) {
        filteredItems.clear();
        filteredItems.addAll(searchHelper.filter(allItems, searchText));

        maxPage = Math.max(0, (filteredItems.size() - 1) / ITEMS_PER_PAGE);
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        searchBox = new EditBox(this.font, leftPos + 8, topPos + 6, GUI_WIDTH - 16, 20, Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入物品注册名/拼音/中文... 或 @mod名"));
        searchBox.setResponder(this::updateFilteredItems);
        addWidget(searchBox);

        prevPageButton = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> previousPage())
                .bounds(leftPos + 8, topPos + GUI_HEIGHT - 24, 20, 20)
                .build());

        nextPageButton = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> nextPage())
                .bounds(leftPos + GUI_WIDTH - 28, topPos + GUI_HEIGHT - 24, 20, 20)
                .build());

        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(leftPos + (GUI_WIDTH - 48) / 2, topPos + GUI_HEIGHT - 24, 48, 20)
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

        String pageInfo = String.format("第 %d/%d 页 (共%d个物品)",
                currentPage + 1, maxPage + 1, filteredItems.size());
        guiGraphics.drawCenteredString(this.font, pageInfo, leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT + 5, 0x404040);

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

            int bgColor = isMouseOver ? 0x80FFFFFF : 0xFF373737;
            guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, bgColor);

            int borderColor = isMouseOver ? 0xFFFFFFFF : 0xFF8B8B8B;
            guiGraphics.fill(slotX - 1, slotY - 1, slotX + SLOT_SIZE + 1, slotY, borderColor);
            guiGraphics.fill(slotX - 1, slotY + SLOT_SIZE, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE + 1, borderColor);
            guiGraphics.fill(slotX - 1, slotY, slotX, slotY + SLOT_SIZE, borderColor);
            guiGraphics.fill(slotX + SLOT_SIZE, slotY, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE, borderColor);

            ItemStack item = filteredItems.get(i);

            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(item, slotX + 1, slotY + 1);
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

                ItemStack item = filteredItems.get(i);
                List<Component> tooltip = new ArrayList<>();

                tooltip.add(item.getItem().getDescription());

                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item.getItem());
                if (itemId != null) {
                    tooltip.add(Component.literal("§7ID: " + itemId));
                    tooltip.add(Component.literal("§9From: " + itemId.getNamespace()));
                }

                // 使用搜索助手获取拼音信息
                PinyinSearchHelper.PinyinInfo pinyinInfo = searchHelper.getPinyinInfo(item);
                if (pinyinInfo != null && !pinyinInfo.fullPinyin.trim().isEmpty()) {
                    String displayName = item.getItem().getDescription().getString();
                    if (PinyinSearchHelper.containsChinese(displayName)) {
                        tooltip.add(Component.literal("§8拼音: " + pinyinInfo.fullPinyin));
                        tooltip.add(Component.literal("§8简写: " + pinyinInfo.initials));
                    }
                }

                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
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
                    ItemStack item = filteredItems.get(i);
                    onItemSelected.accept(item);
                    onClose();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
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