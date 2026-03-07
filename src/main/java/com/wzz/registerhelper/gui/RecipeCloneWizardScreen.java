package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
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
    private static final int W  = 700;
    private static final int H  = 440;
    private static final int PAD = 8;

    // 三栏宽度
    private static final int COL1_W = 180;   // 物品选择
    private static final int COL2_W = 220;   // 配方列表
    // COL3 = 剩余宽度

    private static final int SLOT   = 18;
    private static final int ROW_H  = 20;
    private static final int TITLE_H = 28;
    private static final int FOOT_H  = 30;
    private static final int CONTENT_Y_OFFSET = TITLE_H + 6; // 内容区起始偏移

    // ── 回调 ─────────────────────────────────────────────────────
    private final Screen parent;
    private final Consumer<ResourceLocation> onClone;

    // ── Step 1：物品选择 ──────────────────────────────────────────
    private final List<ItemStack> allItems      = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();
    private EditBox searchBox;
    private int itemScroll = 0;
    private ItemStack targetItem = ItemStack.EMPTY;

    // 拼音搜索助手
    private final PinyinSearchHelper<ItemStack> searchHelper;

    private static final int ITEM_COLS = 8;
    private static final int ITEM_ROWS = 7;

    // ── Step 2：配方列表 ──────────────────────────────────────────
    private final List<RecipeEntry> recipeList = new ArrayList<>();  // 合并列表（原料在前，产物在后）
    private int recipeScroll  = 0;
    private int selectedIdx   = -1;
    private int listVisRows;   // 计算后赋值

    // ── Step 3：预览 ──────────────────────────────────────────────
    private final List<int[]> previewSlots = new ArrayList<>();  // [x,y,stackIdx]
    private final List<ItemStack> previewItems = new ArrayList<>();
    private ItemStack previewResult = ItemStack.EMPTY;

    // ── 面板坐标（init后有效）────────────────────────────────────
    private int px, py;
    private int c1x, c2x, c3x, contentY, contentH;
    private int col3W;

    public RecipeCloneWizardScreen(Screen parent, Consumer<ResourceLocation> onClone) {
        super(Component.literal("配方克隆向导"));
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
            int cols = ings.size() <= 9 ? 3 : ings.size() <= 16 ? 4 : 5;
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
        px = (width  - W) / 2;
        py = (height - H) / 2;

        contentY = py + CONTENT_Y_OFFSET;
        contentH = H - CONTENT_Y_OFFSET - FOOT_H;
        listVisRows = (contentH - 4) / ROW_H;

        c1x = px + PAD;
        c2x = c1x + COL1_W + PAD;
        c3x = c2x + COL2_W + PAD;
        col3W = W - (c3x - px) - PAD;

        // 搜索框（栏1顶部）
        searchBox = new EditBox(font, c1x, contentY + 2, COL1_W, 14,
                Component.literal("搜索"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("§8名称/拼音/首字母/@mod"));
        searchBox.setResponder(this::onSearch);
        addWidget(searchBox);
        searchBox.setFocused(true);

        // 底部按钮
        int footY = py + H - FOOT_H + 5;
        addRenderableWidget(Button.builder(
                Component.literal("§a⬡ 克隆此配方为模板"),
                btn -> doClone()
        ).bounds(c3x, footY, col3W, 20).build());

        addRenderableWidget(Button.builder(Component.literal("§7重选物品"),
                        btn -> { targetItem = ItemStack.EMPTY; recipeList.clear(); selectedIdx=-1; })
                .bounds(c2x, footY, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("§c关闭"),
                        btn -> onClose())
                .bounds(c2x + 74, footY, 50, 20).build());
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

        // 外框 + 背景
        g.fill(px-1, py-1, px+W+1, py+H+1, 0xFF060606);
        g.fill(px, py, px+W, py+H, 0xFF181820);

        // 标题栏
        g.fill(px, py, px+W, py+TITLE_H, 0xFF1A3050);
        g.fill(px, py+TITLE_H-1, px+W, py+TITLE_H, 0xFF3A70A0);
        g.drawCenteredString(font, "§b配方克隆向导", px+W/2, py+10, 0xFFFFFF);

        // 三栏分割线
        int divColor = 0xFF2A2A3A;
        g.fill(c2x-PAD/2, contentY, c2x-PAD/2+1, contentY+contentH, divColor);
        g.fill(c3x-PAD/2, contentY, c3x-PAD/2+1, contentY+contentH, divColor);

        // 底栏分割线
        g.fill(px, py+H-FOOT_H, px+W, py+H-FOOT_H+1, divColor);

        renderCol1(g, mx, my);    // 物品选择
        renderCol2(g, mx, my);    // 配方列表
        renderCol3(g, mx, my);    // 预览
        searchBox.render(g, mx, my, pt);
        super.render(g, mx, my, pt);
    }

    // ── 栏1：物品选择 ────────────────────────────────────────────
    private void renderCol1(GuiGraphics g, int mx, int my) {
        g.drawString(font, targetItem.isEmpty()
                        ? "§7① 选择目标物品"
                        : "§a✔ 已选: §f" + targetItem.getHoverName().getString(),
                c1x, contentY - 10, 0xAAAAAA, false);

        int gridTop = contentY + 18;
        int maxItemScroll = Math.max(0,
                (filteredItems.size() + ITEM_COLS - 1) / ITEM_COLS - ITEM_ROWS);
        itemScroll = clamp(itemScroll, 0, maxItemScroll);

        // 网格背景
        int gridW = ITEM_COLS * (SLOT+2);
        int gridH = ITEM_ROWS * (SLOT+2);
        g.fill(c1x-1, gridTop-1, c1x+gridW+1, gridTop+gridH+1, 0xFF333340);
        g.fill(c1x,   gridTop,   c1x+gridW,   gridTop+gridH,   0xFF0C0C18);

        for (int row = 0; row < ITEM_ROWS; row++) {
            for (int col = 0; col < ITEM_COLS; col++) {
                int idx = (row + itemScroll) * ITEM_COLS + col;
                if (idx >= filteredItems.size()) break;
                ItemStack it = filteredItems.get(idx);
                int sx = c1x + col*(SLOT+2);
                int sy = gridTop + row*(SLOT+2);
                boolean hov = mx>=sx && mx<sx+SLOT && my>=sy && my<sy+SLOT;
                boolean sel = ItemStack.isSameItem(it, targetItem);
                if (sel) g.fill(sx, sy, sx+SLOT, sy+SLOT, 0xFF3A6A3A);
                else if (hov) g.fill(sx, sy, sx+SLOT, sy+SLOT, 0xFF3A3A6A);
                RenderSystem.enableDepthTest();
                g.renderItem(it, sx+1, sy+1);
                RenderSystem.disableDepthTest();
                if (hov) g.renderTooltip(font, it, mx, my);
            }
        }

        // 滚动条
        renderSB(g, c1x+gridW+1, gridTop, 3, gridH,
                (filteredItems.size()+ITEM_COLS-1)/ITEM_COLS, ITEM_ROWS, itemScroll);

        // 计数
        g.drawString(font, "§8共 " + filteredItems.size() + " 项",
                c1x, gridTop + gridH + 2, 0x666666, false);
    }

    // ── 栏2：配方列表 ────────────────────────────────────────────
    private void renderCol2(GuiGraphics g, int mx, int my) {
        if (targetItem.isEmpty()) {
            g.drawCenteredString(font, "§8← 先选择物品",
                    c2x+COL2_W/2, contentY+contentH/2, 0x444455);
            return;
        }

        g.drawString(font,
                "§7② 选择配方  §8(" + recipeList.size() + "个)",
                c2x, contentY-10, 0xAAAAAA, false);

        // 列表背景
        g.fill(c2x-1, contentY-1, c2x+COL2_W+1, contentY+contentH+1, 0xFF333340);
        g.fill(c2x,   contentY,   c2x+COL2_W,   contentY+contentH,   0xFF0C0C18);

        if (recipeList.isEmpty()) {
            g.drawCenteredString(font, "§8无含此物品的配方",
                    c2x+COL2_W/2, contentY+contentH/2, 0x444455);
            return;
        }

        int maxS = Math.max(0, recipeList.size() - listVisRows);
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
                g.fill(c2x, ry, c2x+COL2_W, ry+ROW_H, 0xFF1A1A30);
                g.drawString(font, "§b▶ 作为产物", c2x+4, ry+6, 0x88AAFF, false);
                drawn++; ry = contentY + drawn * ROW_H;
                if (drawn >= listVisRows) break;
            }
            if (!e.asResult && !shownIngTitle) {
                shownIngTitle = true;
                g.fill(c2x, ry, c2x+COL2_W, ry+ROW_H, 0xFF1A1A30);
                g.drawString(font, "§e▶ 作为原料", c2x+4, ry+6, 0xFFCC44, false);
                drawn++; ry = contentY + drawn * ROW_H;
                if (drawn >= listVisRows) break;
            }

            boolean sel = i == selectedIdx;
            boolean hov = mx>=c2x && mx<c2x+COL2_W-3 && my>=ry && my<ry+ROW_H;
            g.fill(c2x, ry, c2x+COL2_W, ry+ROW_H-1,
                    sel ? 0xFF1E3A5A : hov ? 0xFF1A2030 : 0xFF0C0C18);

            // 左色条
            g.fill(c2x, ry, c2x+3, ry+ROW_H-1,
                    e.asResult ? 0xFF4488FF : 0xFFFFCC44);

            // 产物图标
            if (!e.result.isEmpty()) {
                RenderSystem.enableDepthTest();
                g.renderItem(e.result, c2x+5, ry+1);
                RenderSystem.disableDepthTest();
            }

            // 配方ID（截断）
            String label = e.id.getPath();
            int maxTW = COL2_W - SLOT - 14;
            while (font.width(label) > maxTW && label.length() > 4)
                label = label.substring(0, label.length()-1);
            if (!label.equals(e.id.getPath())) label += "…";
            g.drawString(font, "§f" + label, c2x+SLOT+9, ry+3, 0xFFFFFF, false);
            g.drawString(font, "§8" + e.typeName + "  §7" + e.id.getNamespace(),
                    c2x+SLOT+9, ry+12, 0xFFFFFF, false);
            drawn++;
        }

        // 滚动条（基于行数，包含分组标题）
        renderSB(g, c2x+COL2_W-3, contentY, 3, contentH,
                recipeList.size() + 2, listVisRows, recipeScroll);
    }

    // ── 栏3：配方预览 ────────────────────────────────────────────
    private void renderCol3(GuiGraphics g, int mx, int my) {
        g.drawString(font, "§7③ 预览", c3x, contentY-10, 0xAAAAAA, false);

        // 背景
        g.fill(c3x-1, contentY-1, c3x+col3W+1, contentY+contentH+1, 0xFF333340);
        g.fill(c3x,   contentY,   c3x+col3W,   contentY+contentH,   0xFF0C0C18);

        if (selectedIdx < 0 || selectedIdx >= recipeList.size()) {
            g.drawCenteredString(font, "§8← 选择配方",
                    c3x+col3W/2, contentY+contentH/2, 0x444455);
            return;
        }

        RecipeEntry entry = recipeList.get(selectedIdx);

        // 配方类型标题
        g.fill(c3x, contentY, c3x+col3W, contentY+14, 0xFF111130);
        g.drawString(font, "§7" + entry.typeName + "  §8" + entry.id.getNamespace(),
                c3x+4, contentY+3, 0xAAAAAA, false);

        // 材料槽网格
        int slotOff = (SLOT+3);
        int gsx = c3x + 8;
        int gsy = contentY + 20;
        for (int i = 0; i < previewSlots.size(); i++) {
            int[] pos = previewSlots.get(i);
            int sx = gsx + pos[0]*slotOff;
            int sy = gsy + pos[1]*slotOff;
            renderSlot(g, sx, sy, i < previewItems.size() ? previewItems.get(i) : ItemStack.EMPTY, false);
        }

        // 箭头 + 产物
        if (!previewResult.isEmpty()) {
            int rows = previewSlots.isEmpty() ? 0 :
                    previewSlots.stream().mapToInt(p -> p[1]).max().orElse(0) + 1;
            int arrowY = gsy + rows * slotOff / 2 - 4;
            int cols   = previewSlots.isEmpty() ? 0 :
                    previewSlots.stream().mapToInt(p -> p[0]).max().orElse(0) + 1;
            int arrowX = gsx + cols * slotOff + 4;
            g.drawString(font, "§7→", arrowX, arrowY, 0x888888, false);
            renderSlot(g, arrowX+14, arrowY-5, previewResult, true);
            // 产物名称
            g.drawString(font, "§f" + previewResult.getHoverName().getString(),
                    arrowX+14, arrowY + SLOT + 2, 0xFFFFFF, false);
        }

        // 完整 ID 信息（底部）
        int infoY = contentY + contentH - 28;
        g.fill(c3x, infoY, c3x+col3W, contentY+contentH, 0xFF0A0A16);
        g.drawString(font, "§8ID: §7" + entry.id, c3x+4, infoY+2, 0xFFFFFF, false);
        g.drawString(font, "§8材料: §7" + entry.recipe.getIngredients().size() + " 格",
                c3x+4, infoY+12, 0xFFFFFF, false);
    }

    private void renderSlot(GuiGraphics g, int x, int y, ItemStack item, boolean result) {
        g.fill(x-1, y-1, x+SLOT+1, y+SLOT+1, result ? 0xFF6666FF : 0xFF555555);
        g.fill(x, y, x+SLOT, y+SLOT, 0xFF373737);
        if (!item.isEmpty()) {
            RenderSystem.enableDepthTest();
            g.renderItem(item, x+1, y+1);
            RenderSystem.disableDepthTest();
        }
    }

    private void renderSB(GuiGraphics g, int x, int y, int w, int h,
                          int total, int visible, int scroll) {
        if (total <= visible) return;
        int maxS   = total - visible;
        int thumbH = Math.max(8, h * visible / total);
        int thumbY = y + (maxS > 0 ? scroll * (h-thumbH) / maxS : 0);
        g.fill(x, y, x+w, y+h, 0xFF333333);
        g.fill(x, thumbY, x+w, thumbY+thumbH, 0xFF7799FF);
    }

    // ── 输入 ─────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 物品网格
        int gridTop = contentY + 18;
        int gridW   = ITEM_COLS*(SLOT+2);
        int gridH   = ITEM_ROWS*(SLOT+2);
        if (mx>=c1x && mx<c1x+gridW && my>=gridTop && my<gridTop+gridH) {
            int col = ((int)mx-c1x)/(SLOT+2);
            int row = ((int)my-gridTop)/(SLOT+2);
            int idx = (row+itemScroll)*ITEM_COLS+col;
            if (idx >= 0 && idx < filteredItems.size()) {
                pickItem(filteredItems.get(idx)); return true;
            }
        }
        // 配方列表（需要把显示行映射回数据行）
        if (!targetItem.isEmpty() && mx>=c2x && mx<c2x+COL2_W-3
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        int gridTop = contentY + 18;
        int gridW   = ITEM_COLS*(SLOT+2);
        if (mx>=c1x && mx<c1x+gridW && my>=gridTop) {
            int max = Math.max(0,(filteredItems.size()+ITEM_COLS-1)/ITEM_COLS-ITEM_ROWS);
            itemScroll = clamp(itemScroll-(int)delta, 0, max); return true;
        }
        if (!targetItem.isEmpty() && mx>=c2x && mx<c2x+COL2_W) {
            recipeScroll = clamp(recipeScroll-(int)delta, 0,
                    Math.max(0, recipeList.size()-listVisRows+2)); return true;
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
            if (t.contains("shaped"))    return "有序合成";
            if (t.contains("shapeless")) return "无序合成";
            if (t.contains("smelting"))  return "熔炼";
            if (t.contains("blasting"))  return "高炉";
            if (t.contains("smoking"))   return "烟熏";
            if (t.contains("campfire"))  return "营火";
            return t.replaceAll(".*:", "");
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}