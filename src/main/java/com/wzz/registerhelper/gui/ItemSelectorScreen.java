package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ItemSelectorScreen extends Screen {

    private static final int SLOT_SIZE = 18;
    private static final int HEADER_HEIGHT = 108;  // 标题栏28 + 搜索框区域26 + 分类Tab22 + 间距
    private static final int FOOTER_HEIGHT = 28;

    // 动态尺寸（init() 里计算）
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

    private int currentPage = 0;
    private int maxPage = 0;
    private SelectionMode currentMode = SelectionMode.ALL_ITEMS;
    private ItemCategory currentCategory = ItemCategory.ALL;
    private int leftPos, topPos;

    // 颜色主题
    private static final int C_BG_OUTER    = GuiTheme.PANEL_EDGE;
    private static final int C_BG_MAIN     = GuiTheme.PANEL;
    private static final int C_TITLE_BAR   = GuiTheme.HEADER;
    private static final int C_TITLE_LINE  = GuiTheme.HEADER_ACCENT;
    private static final int C_PANEL       = GuiTheme.SURFACE_ALT;
    private static final int C_SLOT_EMPTY  = GuiTheme.SLOT;
    private static final int C_SLOT_HOVER  = GuiTheme.HOVER;
    private static final int C_DIVIDER     = GuiTheme.DIVIDER;
    private static final int C_FOOTER      = GuiTheme.PANEL_ALT;
    private static final int C_TEXT_DIM    = GuiTheme.TEXT_MUTED;

    public enum SelectionMode {
        ALL_ITEMS("registerhelper.gui.item_selector.mode.all"),
        INVENTORY("registerhelper.gui.item_selector.mode.inventory");
        private final String translationKey;
        SelectionMode(String translationKey) { this.translationKey = translationKey; }
        public String getTranslationKey() { return translationKey; }
    }

    /** 物品分类（仿原版创造栏标签） */
    public enum ItemCategory {
        ALL      ("registerhelper.gui.item_selector.category.all"),
        BLOCKS   ("registerhelper.gui.item_selector.category.blocks"),
        TOOLS    ("registerhelper.gui.item_selector.category.tools"),
        COMBAT   ("registerhelper.gui.item_selector.category.combat"),
        FOOD     ("registerhelper.gui.item_selector.category.food"),
        MISC     ("registerhelper.gui.item_selector.category.misc"),
        MODDED   ("registerhelper.gui.item_selector.category.modded");

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
                item -> { ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem()); return id != null ? id.toString() : ""; }
        );
        // 初始化时用默认值，init() 里会重算
        this.itemsPerRow = 11;
        this.itemsPerPage = 77;
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
            Items.DRAGON_EGG.getDefaultInstance()
        );
        allItems.addAll(common);
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack s = item.getDefaultInstance();
            if (!allItems.contains(s) && item != Items.AIR) allItems.add(s);
        }
    }

    private void collectInventoryItems() {
        inventoryItems.clear();
        if (minecraft == null || minecraft.player == null) return;
        Player player = minecraft.player;
        Set<String> added = new HashSet<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty()) {
                String key = getItemIdentifier(s);
                if (!added.contains(key)) { inventoryItems.add(s.copy()); added.add(key); }
            }
        }
        inventoryItems.sort((a, b) -> a.getHoverName().getString().compareTo(b.getHoverName().getString()));
    }

    private String getItemIdentifier(ItemStack s) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        String base = id != null ? id.toString() : "unknown";
        return s.hasTag() ? base + "#" + s.getTag().hashCode() : base;
    }

    /** 判断物品属于哪个分类 */
    private ItemCategory classifyItem(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof net.minecraft.world.item.BlockItem) return ItemCategory.BLOCKS;
        if (item instanceof SwordItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof ArmorItem
                || item instanceof net.minecraft.world.item.TridentItem
                || item instanceof net.minecraft.world.item.ShieldItem)
            return ItemCategory.COMBAT;
        if (item instanceof DiggerItem) return ItemCategory.TOOLS;
        if (item.isEdible()) return ItemCategory.FOOD;
        return ItemCategory.MISC;
    }

    private boolean isModded(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && !"minecraft".equals(id.getNamespace());
    }

    private void updateFilteredItems(String text) {
        filteredItems.clear();
        List<ItemStack> src = currentMode == SelectionMode.INVENTORY ? inventoryItems : allItems;
        List<ItemStack> searched = text.isEmpty() ? new ArrayList<>(src) : new ArrayList<>(searchHelper.filter(src, text));

        // 物品类型分类过滤
        if (currentCategory != ItemCategory.ALL) {
            if (currentCategory == ItemCategory.MODDED) {
                searched.removeIf(s -> !isModded(s));
            } else {
                searched.removeIf(s -> classifyItem(s) != currentCategory);
            }
        }
        filteredItems.addAll(searched);
        maxPage = itemsPerPage > 0 ? Math.max(0, (filteredItems.size() - 1) / itemsPerPage) : 0;
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }

    private void onModeChanged(CycleButton<SelectionMode> btn, SelectionMode mode) {
        this.currentMode = mode;
        if (mode == SelectionMode.INVENTORY) collectInventoryItems();
        updateFilteredItems(searchBox.getValue());
    }

    @Override
    protected void init() {
        String currentSearch = searchBox != null ? searchBox.getValue() : "";
        int availableWidth = Math.max(1, this.width - 20);
        int availableHeight = Math.max(1, this.height - 20);
        int maxW = GuiLayoutHelper.fit(620, 20 + SLOT_SIZE * 4, availableWidth);
        int maxH = GuiLayoutHelper.fit(500,
                HEADER_HEIGHT + FOOTER_HEIGHT + SLOT_SIZE, availableHeight);

        this.itemsPerRow = Math.max(1, (maxW - 20) / SLOT_SIZE);
        int gridRows = Math.max(1,
                (maxH - HEADER_HEIGHT - FOOTER_HEIGHT) / SLOT_SIZE);
        this.itemsPerPage = this.itemsPerRow * gridRows;
        this.guiWidth     = 20 + this.itemsPerRow * SLOT_SIZE;
        this.guiHeight    = HEADER_HEIGHT + gridRows * SLOT_SIZE + FOOTER_HEIGHT;

        this.leftPos = (this.width  - guiWidth)  / 2;
        this.topPos  = (this.height - guiHeight) / 2;

        // 重新分页
        updateFilteredItems(currentSearch);

        // ── 控件 ──
        searchBox = new EditBox(this.font, leftPos + 10, topPos + 32, guiWidth - 20, 18,
                GuiText.component("registerhelper.gui.common.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setHint(GuiText.component("registerhelper.gui.item_selector.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::updateFilteredItems);
        addWidget(searchBox);

        modeButton = addRenderableWidget(CycleButton.<SelectionMode>builder(
                        m -> GuiText.component(m.getTranslationKey()))
                .withValues(SelectionMode.values())
                .withInitialValue(currentMode)
                .displayOnlyValue()
                .create(leftPos + 10, topPos + 6, 90, 20,
                        GuiText.component("registerhelper.gui.item_selector.mode_label"), this::onModeChanged));

        prevPageButton = addRenderableWidget(Button.builder(Component.literal("◀"), b -> previousPage())
                .bounds(leftPos + 4, topPos + guiHeight - 22, 24, 18).build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal("▶"), b -> nextPage())
                .bounds(leftPos + guiWidth - 28, topPos + guiHeight - 22, 24, 18).build());
        // 取消按钮贴底，页码显示在其上
        cancelButton = addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.cancel"), b -> onClose())
                .bounds(leftPos + (guiWidth - 50) / 2, topPos + guiHeight - 22, 50, 18).build());

        updateButtons();
    }

    private void updateButtons() {
        if (prevPageButton != null) prevPageButton.active = currentPage > 0;
        if (nextPageButton != null) nextPageButton.active = currentPage < maxPage;
    }

    private void previousPage() { if (currentPage > 0) { currentPage--; updateButtons(); } }
    private void nextPage()     { if (currentPage < maxPage) { currentPage++; updateButtons(); } }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        GuiTheme.drawBackdrop(g, this.width, this.height);

        // 外框
        g.fill(leftPos - 1, topPos - 1, leftPos + guiWidth + 1, topPos + guiHeight + 1, C_BG_OUTER);
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + guiHeight, C_BG_MAIN);

        // 标题栏
        int tb = topPos + 28;
        g.fill(leftPos, topPos, leftPos + guiWidth, tb, C_TITLE_BAR);
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + 1, C_TITLE_LINE);
        g.fill(leftPos, tb - 1, leftPos + guiWidth, tb, C_DIVIDER);
        Component titleText = GuiText.component("registerhelper.gui.item_selector.title_mode",
                GuiText.component(currentMode.getTranslationKey()));
        g.drawCenteredString(this.font, titleText, leftPos + guiWidth / 2,
                topPos + 9, GuiTheme.TEXT_ON_HEADER);

        // 搜索栏区域
        g.fill(leftPos, tb, leftPos + guiWidth, topPos + HEADER_HEIGHT - 2, C_PANEL);
        g.fill(leftPos + 5, topPos + HEADER_HEIGHT - 3, leftPos + guiWidth - 5,
                topPos + HEADER_HEIGHT - 2, C_DIVIDER);

        // ── Tab 栏区域背景 ──
        int tabAreaTop = topPos + 52;
        int tabAreaBot = topPos + 74;
        g.fill(leftPos, tabAreaTop, leftPos + guiWidth, tabAreaBot, C_PANEL);

        // 物品格子区域
        int gridTop = topPos + HEADER_HEIGHT;
        int gridBot = topPos + guiHeight - FOOTER_HEIGHT;
        g.fill(leftPos, gridTop, leftPos + guiWidth, gridBot, C_PANEL);

        // 底栏
        g.fill(leftPos, gridBot, leftPos + guiWidth, topPos + guiHeight, C_FOOTER);
        g.fill(leftPos + 5, gridBot, leftPos + guiWidth - 5, gridBot + 1, C_DIVIDER);

        renderCategoryTabs(g, mouseX, mouseY);
        renderItemGrid(g, mouseX, mouseY);
        GuiTheme.drawInput(g, searchBox);
        searchBox.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        // 页码（底栏中间，取消按钮上方）
        g.drawCenteredString(this.font,
                GuiText.component("registerhelper.gui.common.page_count",
                        currentPage + 1, maxPage + 1, filteredItems.size()),
                leftPos + guiWidth / 2, topPos + 81, C_TEXT_DIM);

        renderItemTooltip(g, mouseX, mouseY);
    }

    /** 渲染物品分类 Tab 行 */
    private void renderCategoryTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tabY      = topPos + 54;
        int tabH      = 18;
        int tabSpacing = 3;
        ItemCategory[] cats = ItemCategory.values();
        // 均分宽度
        int totalSpacing = tabSpacing * (cats.length - 1);
        int tabW = Math.max(1, (guiWidth - 20 - totalSpacing) / cats.length);
        int tabX = leftPos + 10;

        for (ItemCategory cat : cats) {
            boolean sel   = cat == currentCategory;
            boolean hover = !sel && mouseX >= tabX && mouseX < tabX + tabW
                         && mouseY >= tabY && mouseY < tabY + tabH;

            // 背景
            int bg = sel ? GuiTheme.SELECTED : (hover ? GuiTheme.HOVER : GuiTheme.SURFACE_ALT);
            g.fill(tabX, tabY, tabX + tabW, tabY + tabH, bg);
            // 顶部色条
            int lineColor = sel ? GuiTheme.categoryColor(cat.name())
                    : (hover ? GuiTheme.INPUT_EDGE : GuiTheme.DIVIDER);
            g.fill(tabX, tabY, tabX + tabW, tabY + 2, lineColor);
            // 文字
            int txtColor = sel ? GuiTheme.categoryColor(cat.name())
                    : (hover ? GuiTheme.TEXT : GuiTheme.TEXT_MUTED);
            String tabLabel = GuiLayoutHelper.ellipsis(this.font,
                    GuiText.string(cat.translationKey), Math.max(1, tabW - 4));
            g.drawCenteredString(this.font, tabLabel,
                    tabX + tabW / 2, tabY + 5, txtColor);

            tabX += tabW + tabSpacing;
        }
    }

    private void renderItemGrid(GuiGraphics g, int mouseX, int mouseY) {
        int startIdx    = currentPage * itemsPerPage;
        int endIdx      = Math.min(startIdx + itemsPerPage, filteredItems.size());
        int gridStartX  = leftPos + 10;
        int gridStartY  = topPos + HEADER_HEIGHT + 1;
        int totalSlots  = itemsPerPage;

        // 空格子底色
        for (int i = 0; i < totalSlots; i++) {
            int sx = gridStartX + (i % itemsPerRow) * SLOT_SIZE;
            int sy = gridStartY + (i / itemsPerRow) * SLOT_SIZE;
            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, C_SLOT_EMPTY);
            g.fill(sx,     sy,               sx + SLOT_SIZE, sy + 1,              GuiTheme.SLOT_EDGE);
            g.fill(sx,     sy,               sx + 1,          sy + SLOT_SIZE,     GuiTheme.SLOT_EDGE);
            g.fill(sx,     sy + SLOT_SIZE-1, sx + SLOT_SIZE, sy + SLOT_SIZE,      GuiTheme.SLOT_EDGE);
            g.fill(sx + SLOT_SIZE-1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE,          GuiTheme.SLOT_EDGE);
        }

        // 物品
        for (int i = startIdx; i < endIdx; i++) {
            int rel = i - startIdx;
            int sx  = gridStartX + (rel % itemsPerRow) * SLOT_SIZE;
            int sy  = gridStartY + (rel / itemsPerRow) * SLOT_SIZE;
            boolean hover = mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE;

            if (hover) {
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, C_SLOT_HOVER);
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x30AACCFF);
            }

            ItemStack item = filteredItems.get(i);
            if (item == null || item.isEmpty()) {
                continue; // 神秘
            }
            RenderSystem.enableDepthTest();
            g.renderItem(item, sx + 1, sy + 1);
            if (currentMode == SelectionMode.INVENTORY && item.hasTag()) {
                g.fill(sx + 11, sy + 1, sx + SLOT_SIZE - 1, sy + 9, 0x90AA00FF);
                g.drawString(this.font, "§d✦", sx + 11, sy + 1, 0xFFFFFF, true);
            }
            if (item.getCount() > 1) g.renderItemDecorations(this.font, item, sx + 1, sy + 1);
            RenderSystem.disableDepthTest();
        }
    }

    private void renderItemTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int startIdx   = currentPage * itemsPerPage;
        int endIdx     = Math.min(startIdx + itemsPerPage, filteredItems.size());
        int gridStartX = leftPos + 10;
        int gridStartY = topPos + HEADER_HEIGHT + 1;

        for (int i = startIdx; i < endIdx; i++) {
            int rel = i - startIdx;
            int sx  = gridStartX + (rel % itemsPerRow) * SLOT_SIZE;
            int sy  = gridStartY + (rel / itemsPerRow) * SLOT_SIZE;

            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                ItemStack item = filteredItems.get(i);
                List<Component> tt = new ArrayList<>();
                tt.add(item.getHoverName());
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
                if (id != null) {
                    tt.add(GuiText.component("registerhelper.tooltip.item.id", id));
                    tt.add(GuiText.component("registerhelper.tooltip.item.source", id.getNamespace()));
                }
                if (currentMode == SelectionMode.INVENTORY && item.hasTag()) {
                    tt.add(GuiText.component("registerhelper.tooltip.item.contains_nbt"));
                    tt.add(GuiText.component("registerhelper.tooltip.item.preserve_nbt"));
                }
                if (item.getCount() > 1) {
                    tt.add(GuiText.component("registerhelper.tooltip.item.quantity", item.getCount()));
                }
                PinyinSearchHelper.PinyinInfo pi = searchHelper.getPinyinInfo(item);
                if (pi != null && !pi.fullPinyin.trim().isEmpty()) {
                    String dn = item.getItem().getDescription().getString();
                    if (PinyinSearchHelper.containsChinese(dn)) {
                        tt.add(GuiText.component("registerhelper.tooltip.search.pinyin", pi.fullPinyin));
                        tt.add(GuiText.component("registerhelper.tooltip.search.initials", pi.initials));
                    }
                }
                g.renderTooltip(this.font, tt, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 物品分类 tab 点击
            {
                ItemCategory[] cats = ItemCategory.values();
                int totalSpacing = 3 * (cats.length - 1);
                int tabW = Math.max(1, (guiWidth - 20 - totalSpacing) / cats.length);
                int tabX = leftPos + 10;
                int tabY = topPos + 54, tabH = 18;
                for (ItemCategory cat : cats) {
                    if (mouseX >= tabX && mouseX < tabX + tabW && mouseY >= tabY && mouseY < tabY + tabH) {
                        currentCategory = cat;
                        currentPage = 0;
                        updateFilteredItems(searchBox != null ? searchBox.getValue() : "");
                        return true;
                    }
                    tabX += tabW + 3;
                }
            }
        }
        {   // 物品格子点击
            int startIdx   = currentPage * itemsPerPage;
            int endIdx     = Math.min(startIdx + itemsPerPage, filteredItems.size());
            int gridStartX = leftPos + 10;
            int gridStartY = topPos + HEADER_HEIGHT + 1;
            for (int i = startIdx; i < endIdx; i++) {
                int rel = i - startIdx;
                int sx  = gridStartX + (rel % itemsPerRow) * SLOT_SIZE;
                int sy  = gridStartY + (rel / itemsPerRow) * SLOT_SIZE;
                if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                    onItemSelected.accept(filteredItems.get(i).copy());
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) previousPage(); else nextPage();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parentScreen); }

    @Override
    public boolean isPauseScreen() { return false; }
}
