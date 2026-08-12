package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.util.OldUtils;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Categorized, searchable item selector. */
@OnlyIn(Dist.CLIENT)
public class ItemSelectorScreen extends Screen {
    private static final int SLOT_SIZE = 18;
    private static final int HEADER_HEIGHT = 108;
    private static final int FOOTER_HEIGHT = 28;

    private int guiWidth, guiHeight;
    private int itemsPerRow, itemsPerPage;
    private final Screen parentScreen;
    private final Consumer<ItemStack> onItemSelected;
    private EditBox searchBox;
    private Button prevPageButton, nextPageButton, cancelButton;
    private CycleButton<SelectionMode> modeButton;
    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemStack> inventoryItems = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();
    private final PinyinSearchHelper<ItemStack> searchHelper;
    private int currentPage;
    private int maxPage;
    private SelectionMode currentMode = SelectionMode.ALL_ITEMS;
    private ItemCategory currentCategory = ItemCategory.ALL;
    private int leftPos, topPos;

    public enum SelectionMode {
        ALL_ITEMS("registerhelper.gui.item_selector.mode.all"),
        INVENTORY("registerhelper.gui.item_selector.mode.inventory");

        private final String translationKey;

        SelectionMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getTranslationKey() {
            return translationKey;
        }
    }

    public enum ItemCategory {
        ALL("registerhelper.gui.item_selector.category.all"),
        BLOCKS("registerhelper.gui.item_selector.category.blocks"),
        TOOLS("registerhelper.gui.item_selector.category.tools"),
        COMBAT("registerhelper.gui.item_selector.category.combat"),
        FOOD("registerhelper.gui.item_selector.category.food"),
        MISC("registerhelper.gui.item_selector.category.misc"),
        MODDED("registerhelper.gui.item_selector.category.modded");

        final String translationKey;

        ItemCategory(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    public ItemSelectorScreen(Screen parentScreen, Consumer<ItemStack> onItemSelected) {
        super(GuiText.component("registerhelper.gui.item_selector.title"));
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;
        this.searchHelper = new PinyinSearchHelper<>(
                item -> item.getItem().getDescription().getString(),
                item -> {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
                    return id != null ? id.toString() : "";
                });
        itemsPerRow = 11;
        itemsPerPage = 77;
        collectAllItems();
        collectInventoryItems();
        searchHelper.buildCache(allItems);
        updateFilteredItems("");
    }

    private void collectAllItems() {
        allItems.clear();
        List<ItemStack> common = List.of(
                Items.DIAMOND.getDefaultInstance(), Items.EMERALD.getDefaultInstance(),
                Items.GOLD_INGOT.getDefaultInstance(), Items.IRON_INGOT.getDefaultInstance(),
                Items.STICK.getDefaultInstance(), Items.STONE.getDefaultInstance(),
                Items.COBBLESTONE.getDefaultInstance(), Items.REDSTONE.getDefaultInstance(),
                Items.GLOWSTONE_DUST.getDefaultInstance(), Items.ENDER_PEARL.getDefaultInstance(),
                Items.BLAZE_ROD.getDefaultInstance(), Items.NETHER_STAR.getDefaultInstance(),
                Items.DRAGON_EGG.getDefaultInstance());
        allItems.addAll(common);
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (!allItems.contains(stack) && item != Items.AIR) allItems.add(stack);
        }
    }

    private void collectInventoryItems() {
        inventoryItems.clear();
        if (minecraft == null || minecraft.player == null) return;
        Player player = minecraft.player;
        Set<String> added = new HashSet<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String key = getItemIdentifier(stack);
                if (added.add(key)) inventoryItems.add(stack.copy());
            }
        }
        inventoryItems.sort((a, b) -> a.getHoverName().getString()
                .compareTo(b.getHoverName().getString()));
    }

    private String getItemIdentifier(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String base = id != null ? id.toString() : "unknown";
        return OldUtils.hasTag(stack) && OldUtils.getTag(stack) != null
                ? base + "#" + OldUtils.getTag(stack).hashCode() : base;
    }

    private ItemCategory classifyItem(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BlockItem) return ItemCategory.BLOCKS;
        if (item instanceof SwordItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof ArmorItem
                || item instanceof TridentItem || item instanceof ShieldItem) {
            return ItemCategory.COMBAT;
        }
        if (item instanceof DiggerItem) return ItemCategory.TOOLS;
        if (item.components().has(DataComponents.FOOD)) return ItemCategory.FOOD;
        return ItemCategory.MISC;
    }

    private boolean isModded(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && !"minecraft".equals(id.getNamespace());
    }

    private void updateFilteredItems(String text) {
        filteredItems.clear();
        List<ItemStack> source = currentMode == SelectionMode.INVENTORY ? inventoryItems : allItems;
        List<ItemStack> searched = text.isEmpty() ? new ArrayList<>(source)
                : new ArrayList<>(searchHelper.filter(source, text));
        if (currentCategory != ItemCategory.ALL) {
            if (currentCategory == ItemCategory.MODDED) {
                searched.removeIf(stack -> !isModded(stack));
            } else {
                searched.removeIf(stack -> classifyItem(stack) != currentCategory);
            }
        }
        filteredItems.addAll(searched);
        maxPage = itemsPerPage > 0 ? Math.max(0, (filteredItems.size() - 1) / itemsPerPage) : 0;
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }

    private void onModeChanged(CycleButton<SelectionMode> button, SelectionMode mode) {
        currentMode = mode;
        if (mode == SelectionMode.INVENTORY) collectInventoryItems();
        updateFilteredItems(searchBox == null ? "" : searchBox.getValue());
    }

    @Override
    protected void init() {
        String currentSearch = searchBox != null ? searchBox.getValue() : "";
        int availableWidth = Math.max(1, this.width - 20);
        int availableHeight = Math.max(1, this.height - 20);
        int maxW = GuiLayoutHelper.fit(620, 20 + SLOT_SIZE * 4, availableWidth);
        int maxH = GuiLayoutHelper.fit(500,
                HEADER_HEIGHT + FOOTER_HEIGHT + SLOT_SIZE, availableHeight);
        itemsPerRow = Math.max(1, (maxW - 20) / SLOT_SIZE);
        int gridRows = Math.max(1,
                (maxH - HEADER_HEIGHT - FOOTER_HEIGHT) / SLOT_SIZE);
        itemsPerPage = itemsPerRow * gridRows;
        guiWidth = 20 + itemsPerRow * SLOT_SIZE;
        guiHeight = HEADER_HEIGHT + gridRows * SLOT_SIZE + FOOTER_HEIGHT;
        leftPos = (this.width - guiWidth) / 2;
        topPos = (this.height - guiHeight) / 2;
        updateFilteredItems(currentSearch);

        searchBox = new EditBox(this.font, leftPos + 10, topPos + 32, guiWidth - 20, 18,
                GuiText.component("registerhelper.gui.common.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setHint(GuiText.component("registerhelper.gui.item_selector.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::updateFilteredItems);
        addWidget(searchBox);

        modeButton = addRenderableWidget(CycleButton.<SelectionMode>builder(
                        mode -> GuiText.component(mode.getTranslationKey()))
                .withValues(SelectionMode.values()).withInitialValue(currentMode)
                .displayOnlyValue().create(leftPos + 10, topPos + 6, 90, 20,
                        GuiText.component("registerhelper.gui.item_selector.mode_label"),
                        this::onModeChanged));
        prevPageButton = addRenderableWidget(Button.builder(Component.literal("◀"),
                        button -> previousPage())
                .bounds(leftPos + 4, topPos + guiHeight - 22, 24, 18).build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal("▶"),
                        button -> nextPage())
                .bounds(leftPos + guiWidth - 28, topPos + guiHeight - 22, 24, 18).build());
        cancelButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.cancel"), button -> onClose())
                .bounds(leftPos + (guiWidth - 50) / 2, topPos + guiHeight - 22, 50, 18).build());
        updateButtons();
    }

    private void updateButtons() {
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

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        GuiTheme.drawBackdrop(g, this.width, this.height);
        g.fill(leftPos - 1, topPos - 1, leftPos + guiWidth + 1, topPos + guiHeight + 1,
                GuiTheme.PANEL_EDGE);
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + guiHeight, GuiTheme.PANEL);
        int titleBottom = topPos + 28;
        g.fill(leftPos, topPos, leftPos + guiWidth, titleBottom, GuiTheme.HEADER);
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + 1, GuiTheme.HEADER_ACCENT);
        g.fill(leftPos, titleBottom - 1, leftPos + guiWidth, titleBottom, GuiTheme.DIVIDER);
        Component title = GuiText.component("registerhelper.gui.item_selector.title_mode",
                GuiText.component(currentMode.getTranslationKey()));
        g.drawCenteredString(this.font, title, leftPos + guiWidth / 2,
                topPos + 9, GuiTheme.TEXT_ON_HEADER);

        g.fill(leftPos, titleBottom, leftPos + guiWidth, topPos + HEADER_HEIGHT - 2,
                GuiTheme.SURFACE_ALT);
        g.fill(leftPos + 5, topPos + HEADER_HEIGHT - 3,
                leftPos + guiWidth - 5, topPos + HEADER_HEIGHT - 2, GuiTheme.DIVIDER);
        g.fill(leftPos, topPos + 52, leftPos + guiWidth, topPos + 74,
                GuiTheme.SURFACE_ALT);
        int gridTop = topPos + HEADER_HEIGHT;
        int gridBottom = topPos + guiHeight - FOOTER_HEIGHT;
        g.fill(leftPos, gridTop, leftPos + guiWidth, gridBottom, GuiTheme.SURFACE_ALT);
        g.fill(leftPos, gridBottom, leftPos + guiWidth, topPos + guiHeight, GuiTheme.PANEL_ALT);
        g.fill(leftPos + 5, gridBottom, leftPos + guiWidth - 5, gridBottom + 1,
                GuiTheme.DIVIDER);

        renderCategoryTabs(g, mouseX, mouseY);
        renderItemGrid(g, mouseX, mouseY);
        GuiTheme.drawInput(g, searchBox);
        searchBox.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font,
                GuiText.component("registerhelper.gui.common.page_count",
                        currentPage + 1, maxPage + 1, filteredItems.size()),
                leftPos + guiWidth / 2, topPos + 81, GuiTheme.TEXT_MUTED);
        renderItemTooltip(g, mouseX, mouseY);
    }

    private void renderCategoryTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tabY = topPos + 54;
        int tabHeight = 18;
        int spacing = 3;
        ItemCategory[] categories = ItemCategory.values();
        int totalSpacing = spacing * (categories.length - 1);
        int tabWidth = Math.max(1, (guiWidth - 20 - totalSpacing) / categories.length);
        int tabX = leftPos + 10;
        for (ItemCategory category : categories) {
            boolean selected = category == currentCategory;
            boolean hovered = !selected && mouseX >= tabX && mouseX < tabX + tabWidth
                    && mouseY >= tabY && mouseY < tabY + tabHeight;
            g.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight,
                    selected ? GuiTheme.SELECTED : hovered ? GuiTheme.HOVER : GuiTheme.SURFACE_ALT);
            int lineColor = selected ? GuiTheme.categoryColor(category.name())
                    : hovered ? GuiTheme.INPUT_EDGE : GuiTheme.DIVIDER;
            g.fill(tabX, tabY, tabX + tabWidth, tabY + 2, lineColor);
            int textColor = selected ? GuiTheme.categoryColor(category.name())
                    : hovered ? GuiTheme.TEXT : GuiTheme.TEXT_MUTED;
            String label = GuiLayoutHelper.ellipsis(this.font,
                    GuiText.string(category.translationKey), Math.max(1, tabWidth - 4));
            g.drawCenteredString(this.font, label, tabX + tabWidth / 2, tabY + 5, textColor);
            tabX += tabWidth + spacing;
        }
    }

    private void renderItemGrid(GuiGraphics g, int mouseX, int mouseY) {
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredItems.size());
        int gridStartX = leftPos + 10;
        int gridStartY = topPos + HEADER_HEIGHT + 1;
        for (int i = 0; i < itemsPerPage; i++) {
            int sx = gridStartX + i % itemsPerRow * SLOT_SIZE;
            int sy = gridStartY + i / itemsPerRow * SLOT_SIZE;
            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, GuiTheme.SLOT);
            g.fill(sx, sy, sx + SLOT_SIZE, sy + 1, GuiTheme.SLOT_EDGE);
            g.fill(sx, sy, sx + 1, sy + SLOT_SIZE, GuiTheme.SLOT_EDGE);
            g.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, GuiTheme.SLOT_EDGE);
            g.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, GuiTheme.SLOT_EDGE);
        }
        for (int i = startIndex; i < endIndex; i++) {
            int relative = i - startIndex;
            int sx = gridStartX + relative % itemsPerRow * SLOT_SIZE;
            int sy = gridStartY + relative / itemsPerRow * SLOT_SIZE;
            boolean hovered = mouseX >= sx && mouseX < sx + SLOT_SIZE
                    && mouseY >= sy && mouseY < sy + SLOT_SIZE;
            if (hovered) {
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1,
                        GuiTheme.HOVER);
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x30AACCFF);
            }
            ItemStack item = filteredItems.get(i);
            if (item.isEmpty()) continue;
            RenderSystem.enableDepthTest();
            g.renderItem(item, sx + 1, sy + 1);
            if (currentMode == SelectionMode.INVENTORY && OldUtils.hasTag(item)) {
                g.fill(sx + 11, sy + 1, sx + SLOT_SIZE - 1, sy + 9, 0x90AA00FF);
                g.drawString(this.font, "§d✦", sx + 11, sy + 1, 0xFFFFFF, true);
            }
            if (item.getCount() > 1) g.renderItemDecorations(this.font, item, sx + 1, sy + 1);
            RenderSystem.disableDepthTest();
        }
    }

    private void renderItemTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredItems.size());
        int gridStartX = leftPos + 10;
        int gridStartY = topPos + HEADER_HEIGHT + 1;
        for (int i = startIndex; i < endIndex; i++) {
            int relative = i - startIndex;
            int sx = gridStartX + relative % itemsPerRow * SLOT_SIZE;
            int sy = gridStartY + relative / itemsPerRow * SLOT_SIZE;
            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                ItemStack item = filteredItems.get(i);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(item.getHoverName());
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
                if (id != null) {
                    tooltip.add(GuiText.component("registerhelper.tooltip.item.id", id));
                    tooltip.add(GuiText.component("registerhelper.tooltip.item.source", id.getNamespace()));
                }
                if (currentMode == SelectionMode.INVENTORY && OldUtils.hasTag(item)) {
                    tooltip.add(GuiText.component("registerhelper.tooltip.item.contains_nbt"));
                    tooltip.add(GuiText.component("registerhelper.tooltip.item.preserve_nbt"));
                }
                if (item.getCount() > 1) {
                    tooltip.add(GuiText.component("registerhelper.tooltip.item.quantity", item.getCount()));
                }
                PinyinSearchHelper.PinyinInfo info = searchHelper.getPinyinInfo(item);
                if (info != null && !info.fullPinyin.trim().isEmpty()
                        && PinyinSearchHelper.containsChinese(item.getItem().getDescription().getString())) {
                    tooltip.add(GuiText.component("registerhelper.tooltip.search.pinyin", info.fullPinyin));
                    tooltip.add(GuiText.component("registerhelper.tooltip.search.initials", info.initials));
                }
                g.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ItemCategory[] categories = ItemCategory.values();
            int tabWidth = Math.max(1,
                    (guiWidth - 20 - 3 * (categories.length - 1)) / categories.length);
            int tabX = leftPos + 10;
            for (ItemCategory category : categories) {
                if (mouseX >= tabX && mouseX < tabX + tabWidth
                        && mouseY >= topPos + 54 && mouseY < topPos + 72) {
                    currentCategory = category;
                    currentPage = 0;
                    updateFilteredItems(searchBox == null ? "" : searchBox.getValue());
                    return true;
                }
                tabX += tabWidth + 3;
            }
        }
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredItems.size());
        int gridStartX = leftPos + 10;
        int gridStartY = topPos + HEADER_HEIGHT + 1;
        for (int i = startIndex; i < endIndex; i++) {
            int relative = i - startIndex;
            int sx = gridStartX + relative % itemsPerRow * SLOT_SIZE;
            int sy = gridStartY + relative / itemsPerRow * SLOT_SIZE;
            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                onItemSelected.accept(filteredItems.get(i).copy());
                onClose();
                return true;
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
