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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * 自定义标签创建界面（支持翻页，防止重复添加）
 */
@OnlyIn(Dist.CLIENT)
public class CustomTagCreatorScreen extends Screen {

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 300;
    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_PER_ROW = 9;
    private static final int SLOTS_PER_PAGE = 27; // 每页3行

    private final Screen parentScreen;
    private final BiConsumer<ResourceLocation, List<ItemStack>> onTagCreated;

    private EditBox namespaceBox;
    private EditBox pathBox;
    private Button addItemButton;
    private Button clearAllButton;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button createButton;
    private Button cancelButton;

    // 使用 LinkedHashSet 来保持顺序并去重
    private final LinkedHashSet<Item> tagItems = new LinkedHashSet<>();
    private final List<Item> displayList = new ArrayList<>();
    private int currentPage = 0;
    private int maxPage = 0;

    private int leftPos, topPos;
    private boolean isCreating = false; // 防止重复点击

    // 保存输入框的值，防止重新init时丢失
    private String savedNamespace = "custom";
    private String savedPath = "";

    public CustomTagCreatorScreen(Screen parentScreen, BiConsumer<ResourceLocation, List<ItemStack>> onTagCreated) {
        super(Component.literal("创建自定义标签"));
        this.parentScreen = parentScreen;
        this.onTagCreated = onTagCreated;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        // 命名空间输入框 - 使用保存的值
        namespaceBox = new EditBox(this.font, leftPos + 80, topPos + 30, 100, 20, Component.literal("命名空间"));
        namespaceBox.setHint(Component.literal("mymod"));
        namespaceBox.setValue(savedNamespace);
        namespaceBox.setFilter(text -> text.matches("[a-z0-9_]*"));
        addWidget(namespaceBox);

        // 路径输入框 - 使用保存的值
        pathBox = new EditBox(this.font, leftPos + 80, topPos + 55, 180, 20, Component.literal("路径"));
        pathBox.setHint(Component.literal("my_materials"));
        pathBox.setValue(savedPath);
        pathBox.setFilter(text -> text.matches("[a-z0-9_/]*"));
        addWidget(pathBox);

        // 添加物品按钮
        addItemButton = addRenderableWidget(Button.builder(
                        Component.literal("添加物品"),
                        button -> openItemSelector())
                .bounds(leftPos + 10, topPos + 85, 80, 20)
                .build());

        // 清空所有按钮
        clearAllButton = addRenderableWidget(Button.builder(
                        Component.literal("清空"),
                        button -> clearAllItems())
                .bounds(leftPos + 95, topPos + 85, 50, 20)
                .build());

        // 翻页按钮
        prevPageButton = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> previousPage())
                .bounds(leftPos + 10, topPos + 220, 20, 20)
                .build());

        nextPageButton = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> nextPage())
                .bounds(leftPos + 35, topPos + 220, 20, 20)
                .build());

        // 创建按钮
        createButton = addRenderableWidget(Button.builder(
                        Component.literal("创建标签"),
                        button -> createTag())
                .bounds(leftPos + GUI_WIDTH - 180, topPos + GUI_HEIGHT - 30, 80, 20)
                .build());

        // 取消按钮
        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(leftPos + GUI_WIDTH - 90, topPos + GUI_HEIGHT - 30, 80, 20)
                .build());

        updateButtons();
    }

    private void updateButtons() {
        displayList.clear();
        displayList.addAll(tagItems);

        maxPage = Math.max(0, (displayList.size() - 1) / SLOTS_PER_PAGE);
        currentPage = Math.min(currentPage, maxPage);

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

    private void openItemSelector() {
        if (minecraft != null) {
            minecraft.setScreen(new ItemSelectorScreen(this, item -> {
                if (!item.isEmpty()) {
                    Item itemType = item.getItem();

                    // 检查是否已存在
                    if (tagItems.contains(itemType)) {
                        displayMessage("§e该物品已在标签中，无法重复添加");
                        return;
                    }

                    tagItems.add(itemType);
                    updateButtons();
                    displayMessage("§a已添加: " + ForgeRegistries.ITEMS.getKey(itemType));
                }
            }));
        }
    }

    private void clearAllItems() {
        tagItems.clear();
        currentPage = 0;
        updateButtons();
    }

    private void createTag() {
        // 防止重复点击
        if (isCreating) {
            return;
        }

        // 保存输入框的值
        savedNamespace = namespaceBox.getValue();
        savedPath = pathBox.getValue();

        String namespace = savedNamespace.trim();
        String path = savedPath.trim();

        if (namespace.isEmpty() || path.isEmpty()) {
            displayMessage("§c请输入标签ID的命名空间和路径");
            return;
        }

        if (tagItems.isEmpty()) {
            displayMessage("§c请至少添加一个物品到标签中");
            return;
        }

        isCreating = true;

        try {
            ResourceLocation tagId = new ResourceLocation(namespace, path);

            // 将 Item 转换为 ItemStack 列表
            List<ItemStack> stackList = new ArrayList<>();
            for (Item item : tagItems) {
                stackList.add(new ItemStack(item));
            }

            if (onTagCreated != null) {
                onTagCreated.accept(tagId, stackList);
            }

            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(
                        Component.literal("§a自定义标签已创建: #" + tagId + " (包含 " + tagItems.size() + " 个物品)")
                );
                minecraft.player.sendSystemMessage(
                        Component.literal("§a使用/reload指令加载标签！")
                );
            }

            // 延迟关闭，确保消息显示
            if (minecraft != null) {
                minecraft.execute(() -> {
                    minecraft.setScreen(parentScreen);
                });
            }

        } catch (Exception e) {
            isCreating = false;
            displayMessage("§c创建标签失败: " + e.getMessage());
        }
    }

    private void displayMessage(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // 背景
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos - 10, 0xFFFFFF);

        // 标签
        guiGraphics.drawString(this.font, "命名空间:", leftPos + 10, topPos + 35, 0x404040, false);
        guiGraphics.drawString(this.font, "路径:", leftPos + 10, topPos + 60, 0x404040, false);

        // 预览标签ID
        String previewId = "#" + namespaceBox.getValue() + ":" + pathBox.getValue();
        guiGraphics.drawString(this.font, "预览: " + previewId, leftPos + 10, topPos + 110, 0x666666, false);

        // 提示文字（显示总数和当前页）
        String hint = String.format("§7物品列表 (共%d个，已去重) - 第%d/%d页",
                displayList.size(), currentPage + 1, maxPage + 1);
        guiGraphics.drawString(this.font, hint, leftPos + 10, topPos + 125, 0x666666, false);

        // 渲染物品槽位
        renderItemSlots(guiGraphics, mouseX, mouseY);

        namespaceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        pathBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    private void renderItemSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = leftPos + 10;
        int startY = topPos + 140;

        int startIndex = currentPage * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, displayList.size());

        // 渲染当前页的槽位
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;

            int slotX = startX + col * SLOT_SIZE;
            int slotY = startY + row * SLOT_SIZE;

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
            int itemIndex = startIndex + i;
            if (itemIndex < displayList.size()) {
                Item item = displayList.get(itemIndex);
                ItemStack stack = new ItemStack(item);
                RenderSystem.enableDepthTest();
                guiGraphics.renderItem(stack, slotX + 1, slotY + 1);
                RenderSystem.disableDepthTest();
            }
        }
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = leftPos + 10;
        int startY = topPos + 140;

        int startIndex = currentPage * SLOTS_PER_PAGE;

        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;

            int slotX = startX + col * SLOT_SIZE;
            int slotY = startY + row * SLOT_SIZE;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {

                int itemIndex = startIndex + i;
                if (itemIndex < displayList.size()) {
                    Item item = displayList.get(itemIndex);
                    ItemStack stack = new ItemStack(item);

                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(stack.getHoverName());

                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                    tooltip.add(Component.literal("§7" + itemId));
                    tooltip.add(Component.literal("§8右键删除"));

                    guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                }
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startX = leftPos + 10;
        int startY = topPos + 140;

        int startIndex = currentPage * SLOTS_PER_PAGE;

        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;

            int slotX = startX + col * SLOT_SIZE;
            int slotY = startY + row * SLOT_SIZE;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {

                int itemIndex = startIndex + i;
                if (itemIndex < displayList.size() && button == 1) { // 右键删除
                    Item item = displayList.get(itemIndex);
                    tagItems.remove(item);
                    updateButtons();
                    displayMessage("§c已移除: " + ForgeRegistries.ITEMS.getKey(item));
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