package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.util.OldUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Selects an item from the player inventory while preserving data components. */
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

    private void collectInventoryItems() {
        inventoryItems.clear();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        for (int i = 9; i < 36; i++) addInventoryItem(player, i);
        for (int i = 0; i < 9; i++) addInventoryItem(player, i);
        for (int i = 36; i < 40; i++) addInventoryItem(player, i);
        addInventoryItem(player, 40);
    }

    private void addInventoryItem(LocalPlayer player, int index) {
        ItemStack stack = player.getInventory().getItem(index);
        if (!stack.isEmpty() && stack.getItem() != Items.AIR) {
            inventoryItems.add(stack.copy());
        }
    }

    @Override
    protected void init() {
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(this.width, this.height,
                PREFERRED_WIDTH, PREFERRED_HEIGHT, MIN_WIDTH, MIN_HEIGHT, 8, 8);
        guiWidth = panel.width();
        guiHeight = panel.height();
        leftPos = panel.x();
        topPos = panel.y();
        slotsPerRow = Math.max(1, (guiWidth - 16) / SLOT_SIZE);
        rowsPerPage = Math.max(1,
                (guiHeight - HEADER_HEIGHT - FOOTER_HEIGHT) / SLOT_SIZE);
        itemsPerPage = slotsPerRow * rowsPerPage;
        maxPage = Math.max(0, (inventoryItems.size() - 1) / itemsPerPage);
        currentPage = Math.min(currentPage, maxPage);
        gridBounds = new GuiLayoutHelper.Bounds(leftPos + 8, topPos + HEADER_HEIGHT,
                slotsPerRow * SLOT_SIZE, rowsPerPage * SLOT_SIZE);
        int buttonY = topPos + guiHeight - 24;

        prevPageButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> previousPage())
                .bounds(leftPos + 8, buttonY, 20, 20).build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> nextPage())
                .bounds(leftPos + guiWidth - 28, buttonY, 20, 20).build());
        cancelButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.cancel"), button -> onClose())
                .bounds(leftPos + (guiWidth - 48) / 2, buttonY, 48, 20).build());
        updatePageButtons();
    }

    @Override public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}

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
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.drawBackdrop(g, this.width, this.height);

        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(leftPos, topPos, guiWidth, guiHeight);
        GuiTheme.drawPanel(g, panel, 0, GuiTheme.HEADER_ACCENT);
        g.drawCenteredString(this.font, this.title,
                leftPos + guiWidth / 2, topPos - 10, GuiTheme.TEXT_ON_HEADER);

        if (inventoryItems.isEmpty()) {
            g.drawCenteredString(this.font,
                    GuiText.component("registerhelper.gui.inventory_selector.empty"),
                    leftPos + guiWidth / 2, gridBounds.centerY(), GuiTheme.TEXT_MUTED);
        } else {
            String hint = GuiLayoutHelper.ellipsis(this.font,
                    GuiText.string("registerhelper.gui.inventory_selector.hint"),
                    Math.max(1, guiWidth - 16));
            g.drawString(this.font, hint, leftPos + 8, topPos + 6, GuiTheme.TEXT, false);
            g.drawString(this.font,
                    GuiText.string("registerhelper.gui.common.page", currentPage + 1, maxPage + 1),
                    leftPos + 8, topPos + 18, GuiTheme.TEXT_MUTED, false);
        }

        renderItemGrid(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        renderItemTooltip(g, mouseX, mouseY);
    }

    private void renderItemGrid(GuiGraphics g, int mouseX, int mouseY) {
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, inventoryItems.size());
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int slotX = gridBounds.x() + relativeIndex % slotsPerRow * SLOT_SIZE;
            int slotY = gridBounds.y() + relativeIndex / slotsPerRow * SLOT_SIZE;
            boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
            GuiTheme.drawSlot(g, slotX, slotY, SLOT_SIZE, SLOT_SIZE, hovered);

            ItemStack item = inventoryItems.get(i);
            RenderSystem.enableDepthTest();
            g.renderItem(item, slotX + 1, slotY + 1);
            g.renderItemDecorations(this.font, item, slotX + 1, slotY + 1);
            RenderSystem.disableDepthTest();
        }
    }

    private void renderItemTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, inventoryItems.size());
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int slotX = gridBounds.x() + relativeIndex % slotsPerRow * SLOT_SIZE;
            int slotY = gridBounds.y() + relativeIndex / slotsPerRow * SLOT_SIZE;
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                ItemStack item = inventoryItems.get(i);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(item.getHoverName());
                if (item.getCount() > 1) {
                    tooltip.add(GuiText.component("registerhelper.tooltip.item.quantity", item.getCount()));
                }
                if (OldUtils.hasTag(item)) {
                    tooltip.add(GuiText.component("registerhelper.tooltip.item.has_nbt"));
                    CompoundTag tag = OldUtils.getTag(item);
                    if (tag != null) {
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
                g.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int startIndex = currentPage * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, inventoryItems.size());
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int slotX = gridBounds.x() + relativeIndex % slotsPerRow * SLOT_SIZE;
                int slotY = gridBounds.y() + relativeIndex / slotsPerRow * SLOT_SIZE;
                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE
                        && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    onItemSelected.accept(inventoryItems.get(i).copy());
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) previousPage();
        else if (scrollY < 0) nextPage();
        return true;
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
