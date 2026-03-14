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
    private static final int C_BG_OUTER    = 0xFF0F0F0F;
    private static final int C_BG_MAIN     = 0xFF252525;
    private static final int C_TITLE_BAR   = 0xFF1A3A6A;
    private static final int C_TITLE_LINE  = 0xFF4A7ACF;
    private static final int C_PANEL       = 0xFF1A1A1A;
    private static final int C_SLOT_EMPTY  = 0xFF141414;
    private static final int C_SLOT_HOVER  = 0xFF1D3555;
    private static final int C_DIVIDER     = 0xFF333333;
    private static final int C_FOOTER      = 0xFF1E1E1E;
    private static final int C_TEXT        = 0xFFE0E0E0;
    private static final int C_TEXT_DIM    = 0xFF888888;

    public enum SelectionMode {
        ALL_ITEMS("所有物品"), INVENTORY("背包物品");
        private final String displayName;
        SelectionMode(String d) { this.displayName = d; }
        public String getDisplayName() { return displayName; }
    }

    /** 物品分类（仿原版创造栏标签） */
    public enum ItemCategory {
        ALL      ("全部",   0xFFE0E0E0),
        BLOCKS   ("方块",   0xFF88DDAA),
        TOOLS    ("工具",   0xFFDDAA55),
        COMBAT   ("战斗",   0xFFDD5555),
        FOOD     ("食物",   0xFFDDCC44),
        MISC     ("其他",   0xFFAAAACC);

        final String label;
        final int color;
        ItemCategory(String l, int c) { label = l; color = c; }
    }

    public ItemSelectorScreen(Screen parentScreen, Consumer<ItemStack> onItemSelected) {
        super(Component.literal("选择物品"));
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

    private void updateFilteredItems(String text) {
        filteredItems.clear();
        List<ItemStack> src = currentMode == SelectionMode.INVENTORY ? inventoryItems : allItems;
        List<ItemStack> searched = text.isEmpty() ? new ArrayList<>(src) : new ArrayList<>(searchHelper.filter(src, text));

        // 物品类型分类过滤
        if (currentCategory != ItemCategory.ALL) {
            searched.removeIf(s -> classifyItem(s) != currentCategory);
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
        // ── 动态尺寸 ──
        int maxW = Math.min(this.width - 30, 620);
        int maxH = Math.min(this.height - 50, 500);
        maxW = Math.max(maxW, 260);
        maxH = Math.max(maxH, 230);

        this.itemsPerRow  = Math.max(4, (maxW - 20) / SLOT_SIZE);
        int gridRows      = Math.max(3, (maxH - HEADER_HEIGHT - FOOTER_HEIGHT) / SLOT_SIZE);
        this.itemsPerPage = this.itemsPerRow * gridRows;
        this.guiWidth     = 20 + this.itemsPerRow * SLOT_SIZE;
        this.guiHeight    = HEADER_HEIGHT + gridRows * SLOT_SIZE + FOOTER_HEIGHT;

        this.leftPos = (this.width  - guiWidth)  / 2;
        this.topPos  = (this.height - guiHeight) / 2;

        // 重新分页
        updateFilteredItems(searchBox != null ? searchBox.getValue() : "");

        // ── 控件 ──
        searchBox = new EditBox(this.font, leftPos + 10, topPos + 32, guiWidth - 20, 18,
                Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入物品名/注册名/拼音... 或 @mod名"));
        searchBox.setResponder(this::updateFilteredItems);
        addWidget(searchBox);

        modeButton = addRenderableWidget(CycleButton.<SelectionMode>builder(
                        m -> Component.literal(m.getDisplayName()))
                .withValues(SelectionMode.values())
                .withInitialValue(currentMode)
                .displayOnlyValue()
                .create(leftPos + 10, topPos + 6, 90, 20,
                        Component.literal("选择模式"), this::onModeChanged));

        int btnY = topPos + guiHeight - FOOTER_HEIGHT + 4;
        prevPageButton = addRenderableWidget(Button.builder(Component.literal("◀"), b -> previousPage())
                .bounds(leftPos + 4, topPos + guiHeight - 22, 24, 18).build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal("▶"), b -> nextPage())
                .bounds(leftPos + guiWidth - 28, topPos + guiHeight - 22, 24, 18).build());
        // 取消按钮贴底，页码显示在其上
        cancelButton = addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
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

        // 外框
        g.fill(leftPos - 1, topPos - 1, leftPos + guiWidth + 1, topPos + guiHeight + 1, C_BG_OUTER);
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + guiHeight, C_BG_MAIN);

        // 标题栏
        int tb = topPos + 28;
        g.fill(leftPos, topPos, leftPos + guiWidth, tb, C_TITLE_BAR);
        g.fill(leftPos, topPos, leftPos + guiWidth, topPos + 1, C_TITLE_LINE);
        g.fill(leftPos, tb - 1, leftPos + guiWidth, tb, 0xFF335599);
        String titleText = currentMode == SelectionMode.INVENTORY ? "选择物品  §b[ 背包 ]" : "选择物品  §7[ 全部 ]";
        g.drawCenteredString(this.font, titleText, leftPos + guiWidth / 2, topPos + 9, C_TEXT);

        // 搜索栏区域
        g.fill(leftPos, tb, leftPos + guiWidth, topPos + HEADER_HEIGHT - 2, 0xFF1E1E1E);
        g.fill(leftPos + 5, topPos + HEADER_HEIGHT - 3, leftPos + guiWidth - 5, topPos + HEADER_HEIGHT - 2, C_DIVIDER);

        // ── Tab 栏区域背景 ──
        int tabAreaTop = topPos + 52;
        int tabAreaBot = topPos + 74;
        g.fill(leftPos, tabAreaTop, leftPos + guiWidth, tabAreaBot, 0xFF1A1A1A);

        // 物品格子区域
        int gridTop = topPos + HEADER_HEIGHT;
        int gridBot = topPos + guiHeight - FOOTER_HEIGHT;
        g.fill(leftPos, gridTop, leftPos + guiWidth, gridBot, C_PANEL);

        // 底栏
        g.fill(leftPos, gridBot, leftPos + guiWidth, topPos + guiHeight, C_FOOTER);
        g.fill(leftPos + 5, gridBot, leftPos + guiWidth - 5, gridBot + 1, C_DIVIDER);

        renderCategoryTabs(g, mouseX, mouseY);
        renderItemGrid(g, mouseX, mouseY);
        searchBox.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        // 页码（底栏中间，取消按钮上方）
        String pageInfo = String.format("§7第 %d / %d 页  (%d 个)", currentPage + 1, maxPage + 1, filteredItems.size());
        g.drawCenteredString(this.font, pageInfo, leftPos + guiWidth / 2, topPos + 81, C_TEXT_DIM);

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
        int tabW = Math.max(28, (guiWidth - 20 - totalSpacing) / cats.length);
        int tabX = leftPos + 10;

        for (ItemCategory cat : cats) {
            boolean sel   = cat == currentCategory;
            boolean hover = !sel && mouseX >= tabX && mouseX < tabX + tabW
                         && mouseY >= tabY && mouseY < tabY + tabH;

            // 背景
            int bg = sel ? 0xFF1D3A6A : (hover ? 0xFF252530 : 0xFF161616);
            g.fill(tabX, tabY, tabX + tabW, tabY + tabH, bg);
            // 顶部色条
            int lineColor = sel ? cat.color : (hover ? 0xFF444444 : 0xFF2A2A2A);
            g.fill(tabX, tabY, tabX + tabW, tabY + 2, lineColor);
            // 文字
            int txtColor = sel ? cat.color : (hover ? 0xFFCCCCCC : 0xFF777777);
            g.drawCenteredString(this.font, cat.label, tabX + tabW / 2, tabY + 5, txtColor);

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
            g.fill(sx,     sy,               sx + SLOT_SIZE, sy + 1,              0xFF0A0A0A);
            g.fill(sx,     sy,               sx + 1,          sy + SLOT_SIZE,     0xFF0A0A0A);
            g.fill(sx,     sy + SLOT_SIZE-1, sx + SLOT_SIZE, sy + SLOT_SIZE,      0xFF3A3A3A);
            g.fill(sx + SLOT_SIZE-1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE,          0xFF3A3A3A);
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
                    tt.add(Component.literal("§7ID: " + id));
                    tt.add(Component.literal("§9来自: " + id.getNamespace()));
                }
                if (currentMode == SelectionMode.INVENTORY && item.hasTag()) {
                    tt.add(Component.literal("§b✦ 包含NBT数据"));
                    tt.add(Component.literal("§8将保留附魔、名称等数据"));
                }
                if (item.getCount() > 1) tt.add(Component.literal("§7数量: " + item.getCount()));
                PinyinSearchHelper.PinyinInfo pi = searchHelper.getPinyinInfo(item);
                if (pi != null && !pi.fullPinyin.trim().isEmpty()) {
                    String dn = item.getItem().getDescription().getString();
                    if (PinyinSearchHelper.containsChinese(dn)) {
                        tt.add(Component.literal("§8拼音: " + pi.fullPinyin));
                        tt.add(Component.literal("§8简写: " + pi.initials));
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
                int tabW = Math.max(28, (guiWidth - 20 - totalSpacing) / cats.length);
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
