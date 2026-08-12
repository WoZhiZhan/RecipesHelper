package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Creates a custom item tag with responsive paging. */
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

    private final LinkedHashSet<Item> tagItems = new LinkedHashSet<>();
    private final List<Item> displayList = new ArrayList<>();
    private int currentPage;
    private int maxPage;
    private int guiWidth, guiHeight;
    private int slotsPerRow = 9;
    private int slotRows = 3;
    private int slotsPerPage = 27;
    private int leftPos, topPos, pagerY;
    private GuiLayoutHelper.Bounds slotGridBounds;
    private boolean isCreating;
    private String savedNamespace = "custom";
    private String savedPath = "";

    public CustomTagCreatorScreen(Screen parentScreen,
                                  BiConsumer<ResourceLocation, List<ItemStack>> onTagCreated) {
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
        leftPos = panel.x();
        topPos = panel.y();
        guiWidth = panel.width();
        guiHeight = panel.height();

        int footerY = panel.bottom() - FOOTER_HEIGHT;
        int gridTop = topPos + GRID_TOP_OFFSET;
        int availableGridHeight = Math.max(SLOT_SIZE, footerY - PAGER_HEIGHT - gridTop);
        slotsPerRow = Math.max(1, (guiWidth - 20) / SLOT_SIZE);
        slotRows = Math.max(1, availableGridHeight / SLOT_SIZE);
        slotsPerPage = slotsPerRow * slotRows;
        slotGridBounds = new GuiLayoutHelper.Bounds(leftPos + 10, gridTop,
                slotsPerRow * SLOT_SIZE, slotRows * SLOT_SIZE);
        pagerY = footerY - PAGER_HEIGHT + 4;

        int labelWidth = Math.max(
                this.font.width(GuiText.string("registerhelper.gui.custom_tag.label.namespace")),
                this.font.width(GuiText.string("registerhelper.gui.custom_tag.label.path"))) + 10;
        int inputX = leftPos + 10 + labelWidth;
        int inputWidth = Math.max(40, guiWidth - labelWidth - 30);

        namespaceBox = new EditBox(this.font, inputX, topPos + 30, inputWidth, 20,
                GuiText.component("registerhelper.gui.custom_tag.field.namespace"));
        GuiTheme.styleInput(namespaceBox);
        namespaceBox.setHint(Component.literal("mymod"));
        namespaceBox.setValue(savedNamespace);
        namespaceBox.setFilter(text -> text.matches("[a-z0-9_]*"));
        addWidget(namespaceBox);

        pathBox = new EditBox(this.font, inputX, topPos + 55, inputWidth, 20,
                GuiText.component("registerhelper.gui.custom_tag.field.path"));
        GuiTheme.styleInput(pathBox);
        pathBox.setHint(Component.literal("my_materials"));
        pathBox.setValue(savedPath);
        pathBox.setFilter(text -> text.matches("[a-z0-9_/]*"));
        addWidget(pathBox);

        int addWidth = Math.min(90, Math.max(70, guiWidth / 4));
        addItemButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.custom_tag.add_item"),
                        button -> openItemSelector())
                .bounds(leftPos + 10, topPos + 85, addWidth, 20).build());
        clearAllButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.clear"),
                        button -> clearAllItems())
                .bounds(leftPos + 15 + addWidth, topPos + 85,
                        Math.max(45, font.width(GuiText.string("registerhelper.gui.common.clear")) + 14), 20)
                .build());

        prevPageButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> previousPage())
                .bounds(leftPos + 10, pagerY, 20, 20).build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> nextPage())
                .bounds(leftPos + 35, pagerY, 20, 20).build());

        int actionGap = 8;
        int actionWidth = Math.min(100, Math.max(64, (guiWidth - 30 - actionGap) / 2));
        int actionStartX = leftPos + guiWidth / 2 - (actionWidth * 2 + actionGap) / 2;
        int actionY = footerY + (FOOTER_HEIGHT - 20) / 2;
        createButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.custom_tag.create"),
                        button -> createTag())
                .bounds(actionStartX, actionY, actionWidth, 20).build());
        cancelButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.cancel"),
                        button -> onClose())
                .bounds(actionStartX + actionWidth + actionGap, actionY, actionWidth, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        displayList.clear();
        displayList.addAll(tagItems);
        maxPage = Math.max(0, (displayList.size() - 1) / Math.max(1, slotsPerPage));
        currentPage = Math.min(currentPage, maxPage);
        if (prevPageButton != null) prevPageButton.active = currentPage > 0;
        if (nextPageButton != null) nextPageButton.active = currentPage < maxPage;
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
        if (minecraft == null) return;
        minecraft.setScreen(new ItemSelectorScreen(this, item -> {
            if (item.isEmpty()) return;
            Item itemType = item.getItem();
            if (tagItems.contains(itemType)) {
                displayMessage(GuiText.component("registerhelper.message.custom_tag.duplicate"));
                return;
            }
            tagItems.add(itemType);
            updateButtons();
            displayMessage(GuiText.component("registerhelper.message.custom_tag.added",
                    BuiltInRegistries.ITEM.getKey(itemType)));
        }));
    }

    private void clearAllItems() {
        tagItems.clear();
        currentPage = 0;
        updateButtons();
    }

    private void createTag() {
        if (isCreating) return;
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
            ResourceLocation tagId = ResourceLocation.fromNamespaceAndPath(namespace, path);
            List<ItemStack> stackList = new ArrayList<>();
            for (Item item : tagItems) stackList.add(new ItemStack(item));
            if (onTagCreated != null) onTagCreated.accept(tagId, stackList);
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(GuiText.component(
                        "registerhelper.message.custom_tag.created", tagId, tagItems.size()));
                minecraft.player.sendSystemMessage(
                        GuiText.component("registerhelper.message.custom_tag.reload"));
            }
            if (minecraft != null) {
                minecraft.execute(() -> minecraft.setScreen(parentScreen));
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
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.drawBackdrop(g, this.width, this.height);
        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(leftPos, topPos, guiWidth, guiHeight);
        GuiTheme.drawPanel(g, panel, 0, GuiTheme.HEADER_ACCENT);
        g.drawCenteredString(this.font, this.title,
                leftPos + guiWidth / 2, topPos - 10, GuiTheme.TEXT_ON_HEADER);
        g.drawString(this.font, GuiText.string("registerhelper.gui.custom_tag.label.namespace"),
                leftPos + 10, topPos + 35, GuiTheme.TEXT, false);
        g.drawString(this.font, GuiText.string("registerhelper.gui.custom_tag.label.path"),
                leftPos + 10, topPos + 60, GuiTheme.TEXT, false);
        String previewId = GuiLayoutHelper.ellipsis(this.font,
                GuiText.string("registerhelper.gui.custom_tag.preview",
                        namespaceBox.getValue(), pathBox.getValue()), guiWidth - 20);
        g.drawString(this.font, previewId, leftPos + 10, topPos + 110,
                GuiTheme.SELECTED_EDGE, false);
        String hint = GuiText.string("registerhelper.gui.custom_tag.item_list",
                displayList.size(), currentPage + 1, maxPage + 1);
        g.drawString(this.font, GuiLayoutHelper.ellipsis(this.font, hint, guiWidth - 20),
                leftPos + 10, topPos + 125, GuiTheme.TEXT_MUTED, false);

        renderItemSlots(g, mouseX, mouseY);
        GuiTheme.drawInput(g, namespaceBox);
        GuiTheme.drawInput(g, pathBox);
        namespaceBox.render(g, mouseX, mouseY, partialTick);
        pathBox.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltips(g, mouseX, mouseY);
    }

    private void renderItemSlots(GuiGraphics g, int mouseX, int mouseY) {
        int startIndex = currentPage * slotsPerPage;
        for (int i = 0; i < slotsPerPage; i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;
            int slotX = slotGridBounds.x() + col * SLOT_SIZE;
            int slotY = slotGridBounds.y() + row * SLOT_SIZE;
            boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
            GuiTheme.drawSlot(g, slotX, slotY, SLOT_SIZE, SLOT_SIZE, hovered);
            int itemIndex = startIndex + i;
            if (itemIndex < displayList.size()) {
                RenderSystem.enableDepthTest();
                g.renderItem(new ItemStack(displayList.get(itemIndex)), slotX + 1, slotY + 1);
                RenderSystem.disableDepthTest();
            }
        }
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int startIndex = currentPage * slotsPerPage;
        for (int i = 0; i < slotsPerPage; i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;
            int slotX = slotGridBounds.x() + col * SLOT_SIZE;
            int slotY = slotGridBounds.y() + row * SLOT_SIZE;
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                int itemIndex = startIndex + i;
                if (itemIndex < displayList.size()) {
                    Item item = displayList.get(itemIndex);
                    ItemStack stack = new ItemStack(item);
                    List<Component> tooltip = List.of(stack.getHoverName(),
                            GuiText.component("registerhelper.tooltip.item.id",
                                    BuiltInRegistries.ITEM.getKey(item)),
                            GuiText.component("registerhelper.tooltip.custom_tag.remove"));
                    g.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                }
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int startIndex = currentPage * slotsPerPage;
        for (int i = 0; i < slotsPerPage; i++) {
            int row = i / slotsPerRow;
            int col = i % slotsPerRow;
            int slotX = slotGridBounds.x() + col * SLOT_SIZE;
            int slotY = slotGridBounds.y() + row * SLOT_SIZE;
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                int itemIndex = startIndex + i;
                if (itemIndex < displayList.size() && button == 1) {
                    Item item = displayList.get(itemIndex);
                    tagItems.remove(item);
                    updateButtons();
                    displayMessage(GuiText.component("registerhelper.message.custom_tag.removed",
                            BuiltInRegistries.ITEM.getKey(item)));
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
        if (minecraft != null) minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
