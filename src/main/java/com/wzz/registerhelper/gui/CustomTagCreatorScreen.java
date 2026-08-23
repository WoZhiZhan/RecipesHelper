package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.gui.component.CenteredEditBox;
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

    private static final int SLOT_SIZE = 18;
    private static final int PREFERRED_WIDTH = 420;
    private static final int PREFERRED_HEIGHT = 360;
    private static final int MIN_WIDTH = 260;
    private static final int MIN_HEIGHT = 250;
    private static final int GRID_TOP_OFFSET = 140;
    private static final int PAGER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 34;

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

    private int guiWidth, guiHeight;
    private int slotsPerRow = 9;
    private int slotRows = 3;
    private int slotsPerPage = 27;
    private int leftPos, topPos;
    private int pagerY;
    private GuiLayoutHelper.Bounds slotGridBounds;
    private boolean isCreating = false; // 防止重复点击

    // 保存输入框的值，防止重新init时丢失
    private String savedNamespace = "custom";
    private String savedPath = "";

    public CustomTagCreatorScreen(Screen parentScreen, BiConsumer<ResourceLocation, List<ItemStack>> onTagCreated) {
        super(GuiText.component("registerhelper.gui.custom_tag.title"));
        this.parentScreen = parentScreen;
        this.onTagCreated = onTagCreated;
    }

    @Override
    protected void init() {
        if (namespaceBox != null) savedNamespace = namespaceBox.getValue();
        if (pathBox != null) savedPath = pathBox.getValue();
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(this.width, this.height,
                PREFERRED_WIDTH, PREFERRED_HEIGHT, MIN_WIDTH, MIN_HEIGHT, 8, 8);
        this.leftPos = panel.x();
        this.topPos = panel.y();
        this.guiWidth = panel.width();
        this.guiHeight = panel.height();

        int footerY = panel.bottom() - FOOTER_HEIGHT;
        int gridTop = topPos + GRID_TOP_OFFSET;
        int availableGridHeight = Math.max(SLOT_SIZE,
                footerY - PAGER_HEIGHT - gridTop);
        this.slotsPerRow = Math.max(1, (guiWidth - 20) / SLOT_SIZE);
        this.slotRows = Math.max(1, availableGridHeight / SLOT_SIZE);
        this.slotsPerPage = slotsPerRow * slotRows;
        this.slotGridBounds = new GuiLayoutHelper.Bounds(leftPos + 10, gridTop,
                slotsPerRow * SLOT_SIZE, slotRows * SLOT_SIZE);
        this.pagerY = footerY - PAGER_HEIGHT + 4;

        int labelWidth = Math.max(this.font.width(GuiText.string("registerhelper.gui.custom_tag.label.namespace")),
                this.font.width(GuiText.string("registerhelper.gui.custom_tag.label.path"))) + 10;
        int inputX = leftPos + 10 + labelWidth;
        int inputWidth = Math.max(40, guiWidth - labelWidth - 30);

        // 命名空间输入框 - 使用保存的值
        namespaceBox = new CenteredEditBox(this.font, inputX, topPos + 30,
                inputWidth, 20, GuiText.component("registerhelper.gui.custom_tag.field.namespace"));
        GuiTheme.styleInput(namespaceBox);
        namespaceBox.setHint(Component.literal("mymod"));
        namespaceBox.setValue(savedNamespace);
        namespaceBox.setFilter(text -> text.matches("[a-z0-9_]*"));
        addWidget(namespaceBox);

        // 路径输入框 - 使用保存的值
        pathBox = new CenteredEditBox(this.font, inputX, topPos + 55,
                inputWidth, 20, GuiText.component("registerhelper.gui.custom_tag.field.path"));
        GuiTheme.styleInput(pathBox);
        pathBox.setHint(Component.literal("my_materials"));
        pathBox.setValue(savedPath);
        pathBox.setFilter(text -> text.matches("[a-z0-9_/]*"));
        addWidget(pathBox);

        // 添加物品按钮
        int addButtonWidth = Math.min(90, Math.max(70, guiWidth / 4));
        addItemButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.custom_tag.add_item"),
                        button -> openItemSelector())
                .bounds(leftPos + 10, topPos + 85, addButtonWidth, 20)
                .build());

        // 清空所有按钮
        clearAllButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.clear"),
                        button -> clearAllItems())
                .bounds(leftPos + 15 + addButtonWidth, topPos + 85,
                        Math.max(45, this.font.width(GuiText.string("registerhelper.gui.common.clear")) + 14), 20)
                .build());

        // 翻页按钮
        prevPageButton = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> previousPage())
                .bounds(leftPos + 10, pagerY, 20, 20)
                .build());

        nextPageButton = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> nextPage())
                .bounds(leftPos + 35, pagerY, 20, 20)
                .build());

        // 创建按钮
        int actionGap = 8;
        int actionWidth = Math.min(100, Math.max(64,
                (guiWidth - 30 - actionGap) / 2));
        int actionStartX = leftPos + guiWidth / 2 - (actionWidth * 2 + actionGap) / 2;
        int actionY = footerY + (FOOTER_HEIGHT - 20) / 2;
        createButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.custom_tag.create"),
                        button -> createTag())
                .bounds(actionStartX, actionY, actionWidth, 20)
                .build());

        // 取消按钮
        cancelButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.cancel"),
                        button -> onClose())
                .bounds(actionStartX + actionWidth + actionGap, actionY, actionWidth, 20)
                .build());

        updateButtons();
    }

    private void updateButtons() {
        displayList.clear();
        displayList.addAll(tagItems);

        maxPage = Math.max(0, (displayList.size() - 1) / Math.max(1, slotsPerPage));
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
                        displayMessage(GuiText.component("registerhelper.message.custom_tag.duplicate"));
                        return;
                    }

                    tagItems.add(itemType);
                    updateButtons();
                    displayMessage(GuiText.component("registerhelper.message.custom_tag.added",
                            ForgeRegistries.ITEMS.getKey(itemType)));
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
            displayMessage(GuiText.component("registerhelper.message.custom_tag.missing_id"));
            return;
        }

        if (tagItems.isEmpty()) {
            displayMessage(GuiText.component("registerhelper.message.custom_tag.missing_item"));
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
                        GuiText.component("registerhelper.message.custom_tag.created", tagId, tagItems.size())
                );
                minecraft.player.sendSystemMessage(
                        GuiText.component("registerhelper.message.custom_tag.reload")
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
            displayMessage(GuiText.component("registerhelper.message.custom_tag.failed", e.getMessage()));
        }
    }

    private void displayMessage(Component message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(message);
        }
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

        // 标签
        guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.custom_tag.label.namespace"),
                leftPos + 10, topPos + 35, GuiTheme.TEXT, false);
        guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.custom_tag.label.path"),
                leftPos + 10, topPos + 60, GuiTheme.TEXT, false);

        // 预览标签ID
        String previewId = GuiLayoutHelper.ellipsis(this.font,
                GuiText.string("registerhelper.gui.custom_tag.preview",
                        namespaceBox.getValue(), pathBox.getValue()), guiWidth - 20);
        guiGraphics.drawString(this.font, previewId, leftPos + 10, topPos + 110,
                GuiTheme.SELECTED_EDGE, false);

        // 提示文字（显示总数和当前页）
        String hint = GuiText.string("registerhelper.gui.custom_tag.item_list",
                displayList.size(), currentPage + 1, maxPage + 1);
        guiGraphics.drawString(this.font,
                GuiLayoutHelper.ellipsis(this.font, hint, guiWidth - 20),
                leftPos + 10, topPos + 125, GuiTheme.TEXT_MUTED, false);

        // 渲染物品槽位
        renderItemSlots(guiGraphics, mouseX, mouseY);

        GuiTheme.drawInput(guiGraphics, namespaceBox);
        GuiTheme.drawInput(guiGraphics, pathBox);
        namespaceBox.render(guiGraphics, mouseX, mouseY, partialTick);
        pathBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    private void renderItemSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = slotGridBounds.x();
        int startY = slotGridBounds.y();

        int startIndex = currentPage * slotsPerPage;

        // 渲染当前页的槽位
        for (int i = 0; i < slotsPerPage; i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;

            int slotX = startX + col * SLOT_SIZE;
            int slotY = startY + row * SLOT_SIZE;

            boolean isMouseOver = mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE;

            // 槽位背景
            GuiTheme.drawSlot(guiGraphics, slotX, slotY, SLOT_SIZE, SLOT_SIZE, isMouseOver);

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
        int startX = slotGridBounds.x();
        int startY = slotGridBounds.y();

        int startIndex = currentPage * slotsPerPage;

        for (int i = 0; i < slotsPerPage; i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;

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
                    tooltip.add(GuiText.component("registerhelper.tooltip.item.id", itemId));
                    tooltip.add(GuiText.component("registerhelper.tooltip.custom_tag.remove"));

                    guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                }
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startX = slotGridBounds.x();
        int startY = slotGridBounds.y();

        int startIndex = currentPage * slotsPerPage;

        for (int i = 0; i < slotsPerPage; i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;

            int slotX = startX + col * SLOT_SIZE;
            int slotY = startY + row * SLOT_SIZE;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {

                int itemIndex = startIndex + i;
                if (itemIndex < displayList.size() && button == 1) { // 右键删除
                    Item item = displayList.get(itemIndex);
                    tagItems.remove(item);
                    updateButtons();
                    displayMessage(GuiText.component("registerhelper.message.custom_tag.removed",
                            ForgeRegistries.ITEMS.getKey(item)));
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
