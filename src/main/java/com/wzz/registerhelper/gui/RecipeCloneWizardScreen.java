package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.gui.component.CenteredEditBox;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * 配方克隆向导
 * 布局（固定三栏）：
 * ┌──────────────────────────────────────────────────────┐
 * │  标题栏                                               │
 * ├──────────────┬──────────────────┬───────────────────┤
 * │ 【物品选择】  │  【配方列表】      │  【配方预览】      │
 * │  搜索框       │  ─ 作为原料(N)   │  材料槽 3x3       │
 * │  背包物品     │  ─ 作为产物(N)   │  → 产物槽         │
 * │  （图标网格） │  （可滚动列表）   │  ID/来源信息       │
 * ├──────────────┴──────────────────┴───────────────────┤
 * │  [克隆此配方为模板]           [重选物品]  [关闭]       │
 * └──────────────────────────────────────────────────────┘
 */
@OnlyIn(Dist.CLIENT)
public class RecipeCloneWizardScreen extends Screen {

    // ── 尺寸常量 ─────────────────────────────────────────────────
    private static final int PREFERRED_WIDTH = 820;
    private static final int PREFERRED_HEIGHT = 520;
    private static final int MIN_WIDTH = 480;
    private static final int MIN_HEIGHT = 280;
    private static final int PAD = 8;

    private static final int SLOT   = 18;
    private static final int ROW_H  = 20;
    private static final int TITLE_H = 28;
    private static final int FOOT_H  = 30;
    private static final int CONTENT_Y_OFFSET = TITLE_H + 30; // 标题、搜索框后再开始内容区

    // ── 回调 ─────────────────────────────────────────────────────
    private final Screen parent;
    private final Consumer<ResourceLocation> onClone;

    // ── Step 1：物品选择 ──────────────────────────────────────────
    private final List<ItemStack> allItems      = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();
    private EditBox searchBox;
    private int itemScroll = 0;
    private int draggingScrollbar = -1;
    private double scrollbarGrabOffset;
    private ItemStack targetItem = ItemStack.EMPTY;

    // 拼音搜索助手
    private final PinyinSearchHelper<ItemStack> searchHelper;

    private int itemCols = 8;
    private int itemRows = 7;

    // ── Step 2：配方列表 ──────────────────────────────────────────
    private final List<RecipeEntry> recipeList = new ArrayList<>();  // 合并列表（原料在前，产物在后）
    private int recipeScroll  = 0;
    private int selectedIdx   = -1;
    private int listVisRows;   // 计算后赋值

    // ── Step 3：预览 ──────────────────────────────────────────────
    private final List<int[]> previewSlots = new ArrayList<>();  // [x,y,stackIdx]
    private final List<ItemStack> previewItems = new ArrayList<>();
    private ItemStack previewResult = ItemStack.EMPTY;
    private int previewSlotSize = SLOT;
    private int previewSlotStep = SLOT + 3;

    // ── 面板坐标（init后有效）────────────────────────────────────
    private int px, py, panelW, panelH;
    private int c1x, c2x, c3x, contentY, contentH;
    private int col1W, col2W, col3W;

    public RecipeCloneWizardScreen(Screen parent, Consumer<ResourceLocation> onClone) {
        super(GuiText.component("registerhelper.gui.recipe_clone.title"));
        this.parent  = parent;
        this.onClone = onClone;
        this.searchHelper = new PinyinSearchHelper<>(
                item -> item.getHoverName().getString(),
                item -> {
                    ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item.getItem());
                    return rl != null ? rl.toString() : "";
                }
        );
        loadItems();
    }

    // ── 物品加载 ─────────────────────────────────────────────────
    private void loadItems() {
        // 背包物品优先（方便选当前拿着的素材）
        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (ItemStack s : player.getInventory().items) {
                if (!s.isEmpty()) addUnique(s.copy());
            }
        }
        // 全部注册物品
        for (var item : ForgeRegistries.ITEMS.getValues()) addUnique(new ItemStack(item));
        filteredItems.addAll(allItems);
        // 构建拼音缓存
        searchHelper.buildCache(allItems);
    }

    private void addUnique(ItemStack s) {
        for (ItemStack x : allItems) if (ItemStack.isSameItem(x, s)) return;
        allItems.add(s);
    }

    // ── 搜索（支持中文名称、拼音、首字母、mod过滤） ───────────────
    private void onSearch(String text) {
        filteredItems.clear();
        if (text.isBlank()) {
            filteredItems.addAll(allItems);
        } else {
            for (ItemStack s : allItems) {
                if (searchHelper.matches(s, text)) {
                    filteredItems.add(s);
                }
            }
        }
        itemScroll = 0;
    }

    // ── 选中物品 → 加载配方 ──────────────────────────────────────
    private void pickItem(ItemStack item) {
        targetItem = item.copy();
        recipeList.clear();
        selectedIdx   = -1;
        recipeScroll  = 0;
        previewItems.clear();
        previewSlots.clear();
        previewResult = ItemStack.EMPTY;

        RecipeManager rm = getRM();
        if (rm == null) return;

        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (Recipe<?> r : rm.getRecipes()) {
            try {
                boolean asIng = false, asRes = false;
                ItemStack res = r.getResultItem(Minecraft.getInstance().level.registryAccess());
                if (ItemStack.isSameItem(res, item)) asRes = true;
                for (Ingredient ing : r.getIngredients()) {
                    if (!ing.isEmpty()) for (ItemStack m : ing.getItems())
                        if (ItemStack.isSameItem(m, item)) { asIng = true; break; }
                    if (asIng) break;
                }
                if ((asIng || asRes) && seen.add(r.getId()))
                    recipeList.add(new RecipeEntry(r, asIng, asRes));
            } catch (Exception ignored) {}
        }
        // 排序：产物在前，原料在后
        recipeList.sort(Comparator.comparingInt(e -> (e.asResult ? 0 : 1)));
    }

    // ── 选中配方 → 更新预览 ──────────────────────────────────────
    private void selectRecipe(int idx) {
        selectedIdx = idx;
        previewItems.clear();
        previewSlots.clear();
        previewResult = ItemStack.EMPTY;
        if (idx < 0 || idx >= recipeList.size()) return;
        Recipe<?> r = recipeList.get(idx).recipe;
        try {
            previewResult = r.getResultItem(Minecraft.getInstance().level.registryAccess()).copy();
            List<Ingredient> ings = r.getIngredients();
            int naturalCols = ings.size() <= 9 ? 3 : ings.size() <= 16 ? 4 : 5;
            int resultReserve = Math.min(36, Math.max(20, col3W / 3));
            int gridWidth = Math.max(4, col3W - 16 - resultReserve);
            int gridHeight = Math.max(4, contentH - 64);
            int maxCols = Math.max(1, gridWidth / 4);
            int maxRows = Math.max(1, gridHeight / 4);
            int neededCols = (ings.size() + maxRows - 1) / maxRows;
            int cols = Math.max(1, Math.min(maxCols, Math.max(naturalCols, neededCols)));
            int rows = Math.max(1, (ings.size() + cols - 1) / cols);
            previewSlotStep = Math.max(3, Math.min(SLOT + 3,
                    Math.min(gridWidth / cols, gridHeight / rows)));
            previewSlotSize = Math.max(2, Math.min(SLOT, previewSlotStep - 1));
            for (int i = 0; i < ings.size(); i++) {
                ItemStack it = ItemStack.EMPTY;
                if (!ings.get(i).isEmpty()) {
                    ItemStack[] arr = ings.get(i).getItems();
                    if (arr.length > 0) it = arr[0].copy();
                }
                previewItems.add(it);
                previewSlots.add(new int[]{ i % cols, i / cols });
            }
        } catch (Exception ignored) {}
    }

    private RecipeManager getRM() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s != null) return s.getRecipeManager();
        if (Minecraft.getInstance().level != null)
            return Minecraft.getInstance().level.getRecipeManager();
        return null;
    }

    // ── init ─────────────────────────────────────────────────────
    @Override
    protected void init() {
        String currentSearch = searchBox != null ? searchBox.getValue() : "";
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(width, height,
                PREFERRED_WIDTH, PREFERRED_HEIGHT, MIN_WIDTH, MIN_HEIGHT, 8, 8);
        px = panel.x();
        py = panel.y();
        panelW = panel.width();
        panelH = panel.height();

        contentY = py + CONTENT_Y_OFFSET;
        contentH = panelH - CONTENT_Y_OFFSET - FOOT_H;
        listVisRows = Math.max(1, (contentH - 4) / ROW_H);

        int columnsWidth = panelW - PAD * 4;
        int minCol1Width = Math.min(110, Math.max(60, columnsWidth * 25 / 100));
        int minCol2Width = Math.min(140, Math.max(80, columnsWidth * 30 / 100));
        int minCol3Width = Math.min(120, Math.max(70, columnsWidth * 25 / 100));
        col1W = GuiLayoutHelper.clamp(columnsWidth * 26 / 100,
                minCol1Width, Math.max(minCol1Width,
                        columnsWidth - minCol2Width - minCol3Width));
        col2W = GuiLayoutHelper.clamp(columnsWidth * 32 / 100,
                minCol2Width, Math.max(minCol2Width,
                        columnsWidth - col1W - minCol3Width));

        c1x = px + PAD;
        c2x = c1x + col1W + PAD;
        c3x = c2x + col2W + PAD;
        col3W = panelW - (c3x - px) - PAD;
        itemCols = Math.max(1, (col1W - 4) / (SLOT + 2));
        itemRows = Math.max(1, (contentH - 18) / (SLOT + 2));

        // 搜索框（栏1顶部）
        searchBox = new CenteredEditBox(font, c1x, contentY - 18, col1W, 16,
                GuiText.component("registerhelper.gui.common.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setMaxLength(64);
        searchBox.setHint(GuiText.component("registerhelper.gui.recipe_clone.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::onSearch);
        addWidget(searchBox);
        searchBox.setFocused(true);

        // 底部按钮
        int footY = py + panelH - FOOT_H + 5;
        addRenderableWidget(Button.builder(
                GuiText.component("registerhelper.gui.recipe_clone.clone"),
                btn -> doClone()
        ).bounds(c3x, footY, col3W, 20).build());

        int footerButtonGap = 4;
        int closeButtonWidth = Math.min(50, Math.max(30, col2W / 3));
        int resetButtonWidth = Math.max(30,
                col2W - closeButtonWidth - footerButtonGap);
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.recipe_clone.reselect"),
                        btn -> { targetItem = ItemStack.EMPTY; recipeList.clear(); selectedIdx=-1; })
                .bounds(c2x, footY, resetButtonWidth, 20).build());

        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.close"),
                        btn -> onClose())
                .bounds(c2x + resetButtonWidth + footerButtonGap,
                        footY, closeButtonWidth, 20).build());

        if (selectedIdx >= 0 && selectedIdx < recipeList.size()) {
            selectRecipe(selectedIdx);
        }
    }

    private void doClone() {
        if (selectedIdx >= 0 && selectedIdx < recipeList.size()) {
            onClone.accept(recipeList.get(selectedIdx).id);
            onClose();
        }
    }

    // ── 渲染 ─────────────────────────────────────────────────────
    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        GuiTheme.drawBackdrop(g, this.width, this.height);

        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(px, py, panelW, panelH);
        GuiTheme.drawPanel(g, panel, TITLE_H, GuiTheme.INFO);
        g.drawCenteredString(font, GuiText.component("registerhelper.gui.recipe_clone.title"),
                px+panelW/2, py+10, GuiTheme.TEXT_ON_HEADER);

        // 三栏分割线
        int divColor = GuiTheme.DIVIDER;
        g.fill(c2x-PAD/2, contentY, c2x-PAD/2+1, contentY+contentH, divColor);
        g.fill(c3x-PAD/2, contentY, c3x-PAD/2+1, contentY+contentH, divColor);

        // 底栏分割线
        g.fill(px, py+panelH-FOOT_H, px+panelW, py+panelH-FOOT_H+1, divColor);

        renderCol1(g, mx, my);    // 物品选择
        renderCol2(g, mx, my);    // 配方列表
        renderCol3(g, mx, my);    // 预览
        GuiTheme.drawInput(g, searchBox);
        searchBox.render(g, mx, my, pt);
        super.render(g, mx, my, pt);
    }

    // ── 栏1：物品选择 ────────────────────────────────────────────
    private void renderCol1(GuiGraphics g, int mx, int my) {
        String itemTitle = targetItem.isEmpty()
                ? GuiText.string("registerhelper.gui.recipe_clone.select_item")
                : GuiText.string("registerhelper.gui.recipe_clone.selected_item", targetItem.getHoverName());
        g.drawString(font, GuiLayoutHelper.ellipsis(font, itemTitle, col1W),
                c1x, contentY - 28, GuiTheme.TEXT_MUTED, false);

        int gridTop = contentY + 4;
        int maxItemScroll = Math.max(0,
                (filteredItems.size() + itemCols - 1) / itemCols - itemRows);
        itemScroll = clamp(itemScroll, 0, maxItemScroll);

        // 网格背景
        int gridW = itemCols * (SLOT+2);
        int gridH = itemRows * (SLOT+2);
        GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(c1x, gridTop, gridW, gridH), false);

        for (int row = 0; row < itemRows; row++) {
            for (int col = 0; col < itemCols; col++) {
                int idx = (row + itemScroll) * itemCols + col;
                if (idx >= filteredItems.size()) break;
                ItemStack it = filteredItems.get(idx);
                int sx = c1x + col*(SLOT+2);
                int sy = gridTop + row*(SLOT+2);
                boolean hov = mx>=sx && mx<sx+SLOT && my>=sy && my<sy+SLOT;
                boolean sel = ItemStack.isSameItem(it, targetItem);
                GuiTheme.drawSlot(g, sx, sy, SLOT, SLOT, hov || sel);
                if (sel) {
                    g.fill(sx, sy, sx + 2, sy + SLOT, GuiTheme.SELECTED_EDGE);
                }
                RenderSystem.enableDepthTest();
                g.renderItem(it, sx+1, sy+1);
                RenderSystem.disableDepthTest();
                if (hov) g.renderTooltip(font, it, mx, my);
            }
        }

        // 滚动条
        renderSB(g, c1x+gridW+1, gridTop, 3, gridH,
                (filteredItems.size()+itemCols-1)/itemCols, itemRows, itemScroll);

        // 计数
        g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.item_count", filteredItems.size()),
                c1x, gridTop + gridH + 2, GuiTheme.TEXT_MUTED, false);
    }

    // ── 栏2：配方列表 ────────────────────────────────────────────
    private void renderCol2(GuiGraphics g, int mx, int my) {
        if (targetItem.isEmpty()) {
            g.drawCenteredString(font, GuiText.component("registerhelper.gui.recipe_clone.select_item_first"),
                    c2x+col2W/2, contentY+contentH/2, GuiTheme.TEXT_MUTED);
            return;
        }

        g.drawString(font,
                GuiText.string("registerhelper.gui.recipe_clone.select_recipe", recipeList.size()),
                c2x, contentY-28, GuiTheme.TEXT_MUTED, false);

        // 列表背景
        GuiTheme.drawSurface(g,
                new GuiLayoutHelper.Bounds(c2x, contentY, col2W, contentH), false);

        if (recipeList.isEmpty()) {
            g.drawCenteredString(font, GuiText.component("registerhelper.gui.recipe_clone.no_recipe"),
                    c2x+col2W/2, contentY+contentH/2, GuiTheme.TEXT_MUTED);
            return;
        }

        int maxS = Math.max(0, recipeList.size() - recipeVisibleItems());
        recipeScroll = clamp(recipeScroll, 0, maxS);

        // 分组标题（产物组 / 原料组）
        boolean shownResTitle = false, shownIngTitle = false;
        int drawn = 0;
        for (int i = recipeScroll; i < recipeList.size() && drawn < listVisRows; i++) {
            RecipeEntry e = recipeList.get(i);
            int ry = contentY + drawn * ROW_H;

            // 分组标题行（占1行）
            if (e.asResult && !shownResTitle) {
                shownResTitle = true;
                g.fill(c2x, ry, c2x+col2W, ry+ROW_H, GuiTheme.SECTION);
                g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.as_result"),
                        c2x+4, ry+6, GuiTheme.INFO, false);
                drawn++; ry = contentY + drawn * ROW_H;
                if (drawn >= listVisRows) break;
            }
            if (!e.asResult && !shownIngTitle) {
                shownIngTitle = true;
                g.fill(c2x, ry, c2x+col2W, ry+ROW_H, GuiTheme.SECTION);
                g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.as_ingredient"),
                        c2x+4, ry+6, GuiTheme.WARNING, false);
                drawn++; ry = contentY + drawn * ROW_H;
                if (drawn >= listVisRows) break;
            }

            boolean sel = i == selectedIdx;
            boolean hov = mx>=c2x && mx<c2x+col2W-3 && my>=ry && my<ry+ROW_H;
            GuiTheme.drawRow(g, c2x, ry, col2W, ROW_H - 1, drawn, hov, sel);

            // 左色条
            g.fill(c2x, ry, c2x+3, ry+ROW_H-1,
                    e.asResult ? GuiTheme.INFO : GuiTheme.WARNING);

            // 产物图标
            if (!e.result.isEmpty()) {
                RenderSystem.enableDepthTest();
                g.renderItem(e.result, c2x+5, ry+1);
                RenderSystem.disableDepthTest();
            }

            // 配方ID（截断）
            String label = e.id.getPath();
            int maxTW = col2W - SLOT - 14;
            label = GuiLayoutHelper.ellipsis(font, label, Math.max(1, maxTW));
            g.drawString(font, label, c2x+SLOT+9, ry+3, GuiTheme.TEXT, false);
            String detail = GuiLayoutHelper.ellipsis(font,
                    e.typeName + "  " + e.id.getNamespace(), Math.max(1, maxTW));
            g.drawString(font, detail,
                    c2x+SLOT+9, ry+12, GuiTheme.TEXT_MUTED, false);
            drawn++;
        }

        // 滚动条（基于行数，包含分组标题）
        renderSB(g, c2x+col2W-3, contentY, 3, contentH,
                recipeList.size(), recipeVisibleItems(), recipeScroll);
    }

    // ── 栏3：配方预览 ────────────────────────────────────────────
    private void renderCol3(GuiGraphics g, int mx, int my) {
        g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.preview"),
                c3x, contentY-28, GuiTheme.TEXT_MUTED, false);

        // 背景
        GuiTheme.drawSurface(g,
                new GuiLayoutHelper.Bounds(c3x, contentY, col3W, contentH), true);

        if (selectedIdx < 0 || selectedIdx >= recipeList.size()) {
            g.drawCenteredString(font, GuiText.component("registerhelper.gui.recipe_clone.select_recipe_first"),
                    c3x+col3W/2, contentY+contentH/2, GuiTheme.TEXT_MUTED);
            return;
        }

        RecipeEntry entry = recipeList.get(selectedIdx);

        // 配方类型标题
        g.fill(c3x, contentY, c3x+col3W, contentY+14, GuiTheme.SECTION);
        String previewTitle = GuiLayoutHelper.ellipsis(font,
                entry.typeName + "  " + entry.id.getNamespace(), col3W - 8);
        g.drawString(font, previewTitle,
                c3x+4, contentY+3, GuiTheme.TEXT_MUTED, false);

        // 材料槽网格
        int slotOff = previewSlotStep;
        int gsx = c3x + 8;
        int gsy = contentY + 20;
        for (int i = 0; i < previewSlots.size(); i++) {
            int[] pos = previewSlots.get(i);
            int sx = gsx + pos[0]*slotOff;
            int sy = gsy + pos[1]*slotOff;
            renderSlot(g, sx, sy, previewSlotSize,
                    i < previewItems.size() ? previewItems.get(i) : ItemStack.EMPTY, false);
        }

        // 箭头 + 产物
        if (!previewResult.isEmpty()) {
            int resultX = c3x + col3W - previewSlotSize - 6;
            int resultY = gsy;
            int arrowX = Math.max(gsx, resultX - 11);
            int arrowY = resultY + Math.max(0, previewSlotSize / 2 - 4);
            g.drawString(font, "→", arrowX, arrowY, GuiTheme.TEXT_MUTED, false);
            renderSlot(g, resultX, resultY, previewSlotSize, previewResult, true);
            // 产物名称
            String resultName = GuiLayoutHelper.ellipsis(font,
                    previewResult.getHoverName().getString(),
                    Math.max(1, col3W - 8));
            g.drawString(font, resultName,
                    c3x + 4, contentY + contentH - 38, GuiTheme.TEXT, false);
        }

        // 完整 ID 信息（底部）
        int infoY = contentY + contentH - 28;
        g.fill(c3x, infoY, c3x+col3W, contentY+contentH, GuiTheme.SECTION);
        String idText = GuiLayoutHelper.ellipsis(font,
                "ID: " + entry.id, Math.max(1, col3W - 8));
        g.drawString(font, idText, c3x+4, infoY+2, GuiTheme.TEXT_MUTED, false);
        g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.ingredient_slots",
                        entry.recipe.getIngredients().size()),
                c3x+4, infoY+12, GuiTheme.TEXT, false);
    }

    private void renderSlot(GuiGraphics g, int x, int y, int size,
                            ItemStack item, boolean result) {
        GuiTheme.drawSlot(g, x, y, size, size, result);
        if (result) {
            g.fill(x, y, x + size, y + 2, GuiTheme.INFO);
        }
        if (!item.isEmpty()) {
            RenderSystem.enableDepthTest();
            float scale = Math.min(1.0F, Math.max(0.125F, (size - 2) / 16.0F));
            g.pose().pushPose();
            try {
                g.pose().translate(x + 1, y + 1, 0);
                g.pose().scale(scale, scale, 1.0F);
                g.renderItem(item, 0, 0);
            } finally {
                g.pose().popPose();
            }
            RenderSystem.disableDepthTest();
            if (item.getCount() > 1 && size >= 10) {
                String count = String.valueOf(item.getCount());
                g.pose().pushPose();
                try {
                    g.pose().translate(0, 0, 300);
                    g.drawString(font, count, x + size - font.width(count),
                            y + size - 8, 0xFFFFFF, true);
                } finally {
                    g.pose().popPose();
                }
            }
        }
    }

    private void renderSB(GuiGraphics g, int x, int y, int w, int h,
                          int total, int visible, int scroll) {
        GuiLayoutHelper.Scrollbar scrollbar = GuiLayoutHelper.scrollbar(
                new GuiLayoutHelper.Bounds(x, y, w, h), total, visible, scroll, 8);
        if (!scrollbar.visible()) return;
        GuiTheme.drawScrollbar(g, scrollbar, -1, -1);
    }

    private GuiLayoutHelper.Scrollbar itemScrollbar() {
        int gridW = itemCols * (SLOT + 2);
        int gridH = itemRows * (SLOT + 2);
        return GuiLayoutHelper.scrollbar(
                new GuiLayoutHelper.Bounds(c1x + gridW + 1, contentY + 4, 3, gridH),
                (filteredItems.size() + itemCols - 1) / itemCols,
                itemRows, itemScroll, 8);
    }

    private GuiLayoutHelper.Scrollbar recipeScrollbar() {
        return GuiLayoutHelper.scrollbar(
                new GuiLayoutHelper.Bounds(c2x + col2W - 3, contentY, 3, contentH),
                recipeList.size(), recipeVisibleItems(), recipeScroll, 8);
    }

    private int recipeVisibleItems() {
        return Math.max(1, listVisRows - 2);
    }

    // ── 输入 ─────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            GuiLayoutHelper.Scrollbar itemScrollbar = itemScrollbar();
            if (itemScrollbar.contains(mx, my)) {
                draggingScrollbar = 0;
                scrollbarGrabOffset = itemScrollbar.grabOffset(my);
                itemScroll = itemScrollbar.offsetForPointer(my, scrollbarGrabOffset);
                return true;
            }
            GuiLayoutHelper.Scrollbar recipeScrollbar = recipeScrollbar();
            if (recipeScrollbar.contains(mx, my)) {
                draggingScrollbar = 1;
                scrollbarGrabOffset = recipeScrollbar.grabOffset(my);
                recipeScroll = recipeScrollbar.offsetForPointer(my, scrollbarGrabOffset);
                return true;
            }
        }
        // 物品网格
        int gridTop = contentY + 4;
        int gridW   = itemCols*(SLOT+2);
        int gridH   = itemRows*(SLOT+2);
        if (mx>=c1x && mx<c1x+gridW && my>=gridTop && my<gridTop+gridH) {
            int col = ((int)mx-c1x)/(SLOT+2);
            int row = ((int)my-gridTop)/(SLOT+2);
            int idx = (row+itemScroll)*itemCols+col;
            if (idx >= 0 && idx < filteredItems.size()) {
                pickItem(filteredItems.get(idx)); return true;
            }
        }
        // 配方列表（需要把显示行映射回数据行）
        if (!targetItem.isEmpty() && mx>=c2x && mx<c2x+col2W-3
                && my>=contentY && my<contentY+contentH) {
            int clickRow = ((int)my-contentY)/ROW_H;
            // 重建 drawn→dataIdx 映射
            int drawn=0; boolean shownR=false, shownI=false;
            for (int i=recipeScroll; i<recipeList.size() && drawn<listVisRows; i++) {
                RecipeEntry e = recipeList.get(i);
                if (e.asResult && !shownR) { shownR=true; drawn++; if(drawn>listVisRows) break; }
                if (!e.asResult && !shownI) { shownI=true; drawn++; if(drawn>listVisRows) break; }
                if (drawn-1 == clickRow || drawn == clickRow) {
                    if (i == selectedIdx && btn==0) { doClone(); return true; }
                    selectRecipe(i); return true;
                }
                if (drawn == clickRow+1) { selectRecipe(i); return true; }
                drawn++;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingScrollbar >= 0 && btn == 0) {
            GuiLayoutHelper.Scrollbar scrollbar = draggingScrollbar == 0
                    ? itemScrollbar() : recipeScrollbar();
            if (draggingScrollbar == 0) {
                itemScroll = scrollbar.offsetForPointer(my, scrollbarGrabOffset);
            } else {
                recipeScroll = scrollbar.offsetForPointer(my, scrollbarGrabOffset);
            }
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        boolean wasDragging = draggingScrollbar >= 0;
        if (wasDragging) {
            draggingScrollbar = -1;
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int gridTop = contentY + 4;
        int gridW   = itemCols*(SLOT+2);
        int gridH   = itemRows*(SLOT+2);
        if (mx>=c1x && mx<c1x+gridW && my>=gridTop && my<gridTop+gridH) {
            int max = Math.max(0,
                    (filteredItems.size()+itemCols-1)/itemCols-itemRows);
            itemScroll = clamp(itemScroll-(int)delta, 0, max); return true;
        }
        if (!targetItem.isEmpty() && mx>=c2x && mx<c2x+col2W
                && my>=contentY && my<contentY+contentH) {
            recipeScroll = clamp(recipeScroll-(int)delta, 0,
                    Math.max(0, recipeList.size()-recipeVisibleItems())); return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mods) {
        if (searchBox.isFocused()) return searchBox.keyPressed(kc, sc, mods);
        if (kc == 264 && selectedIdx < recipeList.size()-1) { selectRecipe(selectedIdx+1); return true; }
        if (kc == 265 && selectedIdx > 0)                   { selectRecipe(selectedIdx-1); return true; }
        if (kc == 257 || kc == 335)                          { doClone(); return true; }
        if (kc == 256) { onClose(); return true; }
        return super.keyPressed(kc, sc, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (searchBox.isFocused()) return searchBox.charTyped(c, mods);
        return super.charTyped(c, mods);
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── 数据类 ───────────────────────────────────────────────────
    private static class RecipeEntry {
        final ResourceLocation id;
        final Recipe<?>        recipe;
        final ItemStack        result;
        final String           typeName;
        final boolean          asIngredient, asResult;

        RecipeEntry(Recipe<?> r, boolean asIng, boolean asRes) {
            this.recipe      = r;
            this.id          = r.getId();
            this.asIngredient = asIng;
            this.asResult    = asRes;
            this.typeName    = classify(r);
            ItemStack res = ItemStack.EMPTY;
            try { res = r.getResultItem(Minecraft.getInstance().level.registryAccess()).copy(); }
            catch (Exception ignored) {}
            this.result = res;
        }

        private static String classify(Recipe<?> r) {
            String t = r.getType().toString().toLowerCase();
            if (t.contains("shaped"))    return GuiText.string("registerhelper.recipe_type.minecraft.crafting_shaped");
            if (t.contains("shapeless")) return GuiText.string("registerhelper.recipe_type.minecraft.crafting_shapeless");
            if (t.contains("smelting"))  return GuiText.string("registerhelper.recipe_type.minecraft.smelting");
            if (t.contains("blasting"))  return GuiText.string("registerhelper.recipe_type.minecraft.blasting");
            if (t.contains("smoking"))   return GuiText.string("registerhelper.recipe_type.minecraft.smoking");
            if (t.contains("campfire"))  return GuiText.string("registerhelper.recipe_type.minecraft.campfire");
            return t.replaceAll(".*:", "");
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
