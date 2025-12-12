package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
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
    private CycleButton<SelectionMode> modeButton;

    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemStack> inventoryItems = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();

    private final PinyinSearchHelper<ItemStack> searchHelper;

    private int currentPage = 0;
    private int maxPage = 0;
    private SelectionMode currentMode = SelectionMode.ALL_ITEMS;

    private int leftPos, topPos;
    private static final int GUI_WIDTH = 220;
    private static final int GUI_HEIGHT = 200;

    // 选择模式枚举
    public enum SelectionMode {
        ALL_ITEMS("所有物品"),
        INVENTORY("背包物品");

        private final String displayName;

        SelectionMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public ItemSelectorScreen(Screen parentScreen, Consumer<ItemStack> onItemSelected) {
        super(Component.literal("选择物品"));
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;

        this.searchHelper = new PinyinSearchHelper<>(
                item -> item.getItem().getDescription().getString(),
                item -> {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem());
                    return id != null ? id.toString() : "";
                }
        );

        collectAllItems();
        collectInventoryItems();
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

    private void collectInventoryItems() {
        inventoryItems.clear();

        if (minecraft == null || minecraft.player == null) {
            return;
        }

        Player player = minecraft.player;
        Set<String> addedItems = new HashSet<>();

        // 遍历玩家背包
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                // 使用物品ID+NBT作为唯一标识，避免重复
                String identifier = getItemIdentifier(stack);
                if (!addedItems.contains(identifier)) {
                    inventoryItems.add(stack.copy());
                    addedItems.add(identifier);
                }
            }
        }

        // 按物品名称排序
        inventoryItems.sort((a, b) ->
                a.getHoverName().getString().compareTo(b.getHoverName().getString())
        );
    }

    private String getItemIdentifier(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String baseId = id != null ? id.toString() : "unknown";

        // 如果有NBT，加入NBT的哈希值以区分
        if (stack.hasTag()) {
            return baseId + "#" + stack.getTag().hashCode();
        }
        return baseId;
    }

    private void updateFilteredItems(String searchText) {
        filteredItems.clear();

        // 根据当前模式选择源列表
        List<ItemStack> sourceList = currentMode == SelectionMode.INVENTORY ?
                inventoryItems : allItems;

        if (searchText.isEmpty()) {
            filteredItems.addAll(sourceList);
        } else {
            filteredItems.addAll(searchHelper.filter(sourceList, searchText));
        }

        maxPage = Math.max(0, (filteredItems.size() - 1) / ITEMS_PER_PAGE);
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }

    private void onModeChanged(CycleButton<SelectionMode> button, SelectionMode newMode) {
        this.currentMode = newMode;

        // 切换模式时重新收集背包物品（以防物品有变化）
        if (newMode == SelectionMode.INVENTORY) {
            collectInventoryItems();
        }

        // 重新应用搜索过滤
        updateFilteredItems(searchBox.getValue());
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        // 搜索框
        searchBox = new EditBox(this.font, leftPos + 8, topPos + 32, GUI_WIDTH - 16, 20,
                Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入物品注册名/拼音/中文... 或 @mod名"));
        searchBox.setResponder(this::updateFilteredItems);
        addWidget(searchBox);

        // 模式切换按钮
        modeButton = addRenderableWidget(CycleButton.<SelectionMode>builder(
                        mode -> Component.literal(mode.getDisplayName()))
                .withValues(SelectionMode.values())
                .withInitialValue(currentMode)
                .displayOnlyValue()
                .create(leftPos + 8, topPos + 6, 80, 20,
                        Component.literal("选择模式"), this::onModeChanged));

        // 上一页按钮
        prevPageButton = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> previousPage())
                .bounds(leftPos + 8, topPos + GUI_HEIGHT - 24, 20, 20)
                .build());

        // 下一页按钮
        nextPageButton = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> nextPage())
                .bounds(leftPos + GUI_WIDTH - 28, topPos + GUI_HEIGHT - 24, 20, 20)
                .build());

        // 取消按钮
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

        // 主背景
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);

        // 标题
        String title = currentMode == SelectionMode.INVENTORY ? "选择物品 - 背包" : "选择物品 - 所有";
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, topPos - 10, 0xFFFFFF);

        renderItemGrid(guiGraphics, mouseX, mouseY);
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 页面信息
        String pageInfo = String.format("第 %d/%d 页 (共%d个物品)",
                currentPage + 1, maxPage + 1, filteredItems.size());
        guiGraphics.drawCenteredString(this.font, pageInfo, leftPos + GUI_WIDTH / 2,
                topPos + GUI_HEIGHT + 5, 0x404040);

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
            int slotY = topPos + 58 + y * SLOT_SIZE;

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

            // 如果有NBT数据，显示标记
            if (currentMode == SelectionMode.INVENTORY && item.hasTag()) {
                guiGraphics.fill(slotX + 10, slotY + 1, slotX + 18, slotY + 9, 0x80FF00FF);
                guiGraphics.drawString(this.font, "§d§l*", slotX + 12, slotY + 1, 0xFFFFFF, true);
            }

            // 显示数量
            if (item.getCount() > 1) {
                guiGraphics.renderItemDecorations(this.font, item, slotX + 1, slotY + 1);
            }

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
            int slotY = topPos + 58 + y * SLOT_SIZE;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {

                ItemStack item = filteredItems.get(i);
                List<Component> tooltip = new ArrayList<>();

                tooltip.add(item.getHoverName());

                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item.getItem());
                if (itemId != null) {
                    tooltip.add(Component.literal("§7ID: " + itemId));
                    tooltip.add(Component.literal("§9From: " + itemId.getNamespace()));
                }

                // 如果是背包模式且有NBT
                if (currentMode == SelectionMode.INVENTORY && item.hasTag()) {
                    tooltip.add(Component.literal("§b✦ 包含NBT数据"));
                    tooltip.add(Component.literal("§8将保留附魔、名称等数据"));
                }

                // 显示数量
                if (item.getCount() > 1) {
                    tooltip.add(Component.literal("§7数量: " + item.getCount()));
                }

                // 拼音信息
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
                int slotY = topPos + 58 + y * SLOT_SIZE;

                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                        mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    ItemStack item = filteredItems.get(i);
                    onItemSelected.accept(item.copy()); // 使用copy保留NBT
                    onClose();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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