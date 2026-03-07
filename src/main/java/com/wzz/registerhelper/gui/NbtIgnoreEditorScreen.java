package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.gui.recipe.IngredientData;
import com.wzz.registerhelper.util.NbtIgnorePresetManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class NbtIgnoreEditorScreen extends Screen {

    // ── NBT Key 含义注释 ────────────────────────────────────────
    // key → 说明文字（不含颜色码，渲染时统一加灰色）
    private static final Map<String, String> KEY_COMMENTS = new LinkedHashMap<>();
    static {
        KEY_COMMENTS.put("bladeState.lastActionTime",     "最后使用时间戳，每次挥砍后更新");
        KEY_COMMENTS.put("bladeState.TargetEntity",       "锁定目标的实体ID，无目标时为-1或0");
        KEY_COMMENTS.put("bladeState.Damage",             "当前耐久损耗值");
        KEY_COMMENTS.put("bladeState.currentCombo",       "当前连击动作状态");
        KEY_COMMENTS.put("bladeState._onClick",           "点击标志位，瞬时状态");
        KEY_COMMENTS.put("bladeState.killCount",          "累计击杀数");
        KEY_COMMENTS.put("bladeState.proudSoul",          "耀魂值");
        KEY_COMMENTS.put("bladeState.RepairCounter",      "已修复次数");
        KEY_COMMENTS.put("bladeState.ComboRoot",          "连击根节点配置");
        KEY_COMMENTS.put("bladeState.AttackAmplifier",    "攻击倍率修正");
        KEY_COMMENTS.put("bladeState.ModelName",          "刀身3D模型路径");
        KEY_COMMENTS.put("bladeState.TextureName",        "贴图路径");
        KEY_COMMENTS.put("bladeState.SummonedSwordColor", "召唤剑颜色（ARGB整数）");
        KEY_COMMENTS.put("bladeState.SummonedSwordColorInverse", "召唤剑颜色反转标志");
        KEY_COMMENTS.put("bladeState.baseAttackModifier", "基础攻击力修正值");
        KEY_COMMENTS.put("bladeState.StandbyRenderType",  "待机渲染模式编号");
        KEY_COMMENTS.put("bladeState.translationKey",     "多语言翻译键");
        KEY_COMMENTS.put("bladeState.isSealed",           "封印状态标志");
        KEY_COMMENTS.put("bladeState.isBroken",           "破损状态标志");
        KEY_COMMENTS.put("bladeState.maxDamage",          "最大耐久上限");
        KEY_COMMENTS.put("bladeState.isDefaultBewitched", "是否使用默认魔咒配置");
        KEY_COMMENTS.put("bladeState.fallDecreaseRate",   "下落速度衰减率");
        KEY_COMMENTS.put("bladeState.adjustXYZ",          "位置微调偏移量");
        KEY_COMMENTS.put("bladeState.SpecialAttackType",  "特殊攻击类型");
        // 通用
        KEY_COMMENTS.put("Damage",                        "原版物品耐久损耗");
        KEY_COMMENTS.put("RepairCost",                    "铁砧修复费用");
        KEY_COMMENTS.put("display.Name",                  "自定义显示名称");
        KEY_COMMENTS.put("HideFlags",                     "隐藏属性标志位");
        KEY_COMMENTS.put("CustomModelData",               "资源包自定义模型数据");
        KEY_COMMENTS.put("Enchantments",                  "附魔列表（通常不忽略）");
    }

    // ── 布局 ─────────────────────────────────────────────────────
    private static final int W = 520;
    private static final int H = 440;
    private static final int PAD = 12;
    private static final int ROW_H = 16;
    private static final int KEY_VISIBLE    = 12;
    private static final int PRESET_VISIBLE = 8;

    // ── 分割：左230 | 间隔16 | 右其余 ───────────────────────────
    private static final int LEFT_W = 230;

    // ── 状态 ─────────────────────────────────────────────────────
    private final Screen parent;
    private final IngredientData targetData;
    private final Runnable onConfirm;
    private final String itemNamespace;

    private final List<String> keyList = new ArrayList<>();
    private int keyListScroll  = 0;
    private int selectedKeyIdx = -1;  // 左侧选中行

    private int presetScroll       = 0;
    private int selectedPresetIdx  = -1;

    private EditBox keyInputBox;
    private EditBox presetNameBox;
    private boolean showPresetNameInput = false;

    // 面板坐标（init后有效）
    private int px, py;
    // 左侧
    private int leftX, listY, listH;
    // 右侧
    private int rightX, presetY, rightW, presetH;
    // 说明面板（左侧列表下方）
    private int descY, descH;

    public NbtIgnoreEditorScreen(Screen parent, IngredientData data, Runnable onConfirm) {
        super(Component.literal("编辑忽略 NBT Key"));
        this.parent      = parent;
        this.targetData  = data;
        this.onConfirm   = onConfirm;
        this.keyList.addAll(data.getIgnoreNbtKeys());
        String ns = "";
        var key = ForgeRegistries.ITEMS.getKey(data.getItemStack().getItem());
        if (key != null) ns = key.getNamespace();
        this.itemNamespace = ns;
    }

    @Override
    protected void init() {
        px = (this.width  - W) / 2;
        py = (this.height - H) / 2;

        // 左侧坐标
        leftX = px + PAD;
        listY = py + 52;
        listH = KEY_VISIBLE * ROW_H;

        // 右侧坐标
        rightX   = px + PAD + LEFT_W + 16;
        rightW   = W - LEFT_W - PAD * 3 - 16;
        presetY  = py + 52;
        presetH  = PRESET_VISIBLE * ROW_H;

        // 说明面板：紧贴列表下方，高约50px
        descY = listY + listH + 4;
        descH = 50;

        // ── 左侧控件 ─────────────────────────────────────────────
        int inputY = descY + descH + 4;
        keyInputBox = new EditBox(this.font, leftX, inputY, LEFT_W - 60, 14,
                Component.literal("key"));
        keyInputBox.setMaxLength(256);
        keyInputBox.setHint(Component.literal("§8如: bladeState.lastActionTime"));
        addWidget(keyInputBox);
        keyInputBox.setFocused(true);

        addRenderableWidget(Button.builder(Component.literal("§a+添加"),
                        btn -> doAddKey())
                .bounds(leftX + LEFT_W - 56, inputY, 56, 14).build());

        addRenderableWidget(Button.builder(Component.literal("§c删除选中"),
                        btn -> doRemoveSelected())
                .bounds(leftX, inputY + 18, 72, 14).build());

        addRenderableWidget(Button.builder(Component.literal("§8清空"),
                        btn -> { keyList.clear(); selectedKeyIdx = -1; })
                .bounds(leftX + 76, inputY + 18, 44, 14).build());

        // ── 右侧预设控件 ────────────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("§b←加载"),
                        btn -> doLoadPreset())
                .bounds(rightX, presetY + presetH + 6, 64, 14).build());

        addRenderableWidget(Button.builder(Component.literal("§c删除"),
                        btn -> doDeletePreset())
                .bounds(rightX + 68, presetY + presetH + 6, 64, 14).build());

        addRenderableWidget(Button.builder(Component.literal("§e另存为预设"),
                        btn -> { showPresetNameInput = !showPresetNameInput; if (showPresetNameInput) presetNameBox.setValue(""); })
                .bounds(rightX, presetY + presetH + 24, 90, 14).build());

        presetNameBox = new EditBox(this.font,
                rightX, presetY + presetH + 42, rightW - 30, 14,
                Component.literal("名称"));
        presetNameBox.setMaxLength(64);
        presetNameBox.setHint(Component.literal("§8输入预设名称后按 Enter 保存"));
        addWidget(presetNameBox);

        addRenderableWidget(Button.builder(Component.literal("§a✔"),
                        btn -> doSavePreset())
                .bounds(rightX + rightW - 26, presetY + presetH + 42, 26, 14).build());

        // 拔刀剑快速预设
        if ("slashblade".equals(itemNamespace)) {
            addRenderableWidget(Button.builder(Component.literal("§b⚡ 拔刀剑快速忽略"),
                            btn -> doSlashbladeQuickFill())
                    .bounds(px + PAD, py + H - 52, 132, 16).build());
        }

        // 底部
        addRenderableWidget(Button.builder(Component.literal("§a确认"),
                        btn -> doConfirm())
                .bounds(px + W / 2 - 92, py + H - 26, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§c取消"),
                        btn -> onClose())
                .bounds(px + W / 2 + 12, py + H - 26, 80, 20).build());
    }

    private void doAddKey() {
        String v = keyInputBox.getValue().trim();
        if (v.isEmpty()) return;
        for (String k : v.split(",")) {
            String key = k.trim();
            if (!key.isEmpty() && !keyList.contains(key)) keyList.add(key);
        }
        keyInputBox.setValue("");
    }

    private void doRemoveSelected() {
        if (selectedKeyIdx >= 0 && selectedKeyIdx < keyList.size()) {
            keyList.remove(selectedKeyIdx);
            selectedKeyIdx = Math.min(selectedKeyIdx, keyList.size() - 1);
        }
    }

    private void doLoadPreset() {
        var all = NbtIgnorePresetManager.getAll();
        if (selectedPresetIdx >= 0 && selectedPresetIdx < all.size()) {
            keyList.clear();
            keyList.addAll(all.get(selectedPresetIdx).keys());
            selectedKeyIdx = -1;
        }
    }

    private void doDeletePreset() {
        var all = NbtIgnorePresetManager.getAll();
        if (selectedPresetIdx >= 0 && selectedPresetIdx < all.size()) {
            NbtIgnorePresetManager.remove(all.get(selectedPresetIdx).name());
            selectedPresetIdx = Math.min(selectedPresetIdx, NbtIgnorePresetManager.getAll().size() - 1);
        }
    }

    private void doSavePreset() {
        String name = presetNameBox.getValue().trim();
        if (name.isEmpty() || keyList.isEmpty()) { showPresetNameInput = false; return; }
        NbtIgnorePresetManager.addOrUpdate(name, List.copyOf(keyList));
        showPresetNameInput = false;
    }

    private void doSlashbladeQuickFill() {
        keyList.clear();
        keyList.addAll(List.of(
                "bladeState.lastActionTime", "bladeState.TargetEntity",
                "bladeState.Damage",         "bladeState.currentCombo",
                "bladeState._onClick",       "bladeState.killCount",
                "bladeState.proudSoul",      "bladeState.RepairCounter"
        ));
        selectedKeyIdx = -1;
    }

    private void doConfirm() {
        targetData.setIgnoreNbtKeys(List.copyOf(keyList));
        if (!keyList.isEmpty()) targetData.setIncludeNBT(true);
        onConfirm.run();
        onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);

        // 主面板
        g.fill(px-1, py-1, px+W+1, py+H+1, 0xFF080808);
        g.fill(px,   py,   px+W,   py+H,   0xFF1E1E1E);

        // 标题栏
        g.fill(px, py, px+W, py+28, 0xFF1A2A4A);
        g.fill(px, py+27, px+W, py+28, 0xFF3A5A8A);
        g.drawCenteredString(font, "§b编辑忽略 NBT Key", px+W/2, py+10, 0xFFFFFF);

        // 警告
        g.drawString(font,
                "§c⚠ 部分匹配配方仅支持本模组加载",
                px+PAD, py+32, 0xFF5555, false);

        g.drawString(font,
                "§e当前忽略 Key  §8(" + keyList.size() + " 项)  §7↑↓选择 | Del删除",
                leftX, listY-12, 0xAAAAAA, false);

        // 列表背景
        g.fill(leftX-1, listY-1, leftX+LEFT_W+1, listY+listH+1, 0xFF444444);
        g.fill(leftX,   listY,   leftX+LEFT_W,   listY+listH,   0xFF0F1F0F);

        int maxKS = Math.max(0, keyList.size() - KEY_VISIBLE);
        keyListScroll = clamp(keyListScroll, 0, maxKS);

        for (int i = 0; i < KEY_VISIBLE; i++) {
            int idx  = i + keyListScroll;
            if (idx >= keyList.size()) break;
            String k = keyList.get(idx);
            int rowY = listY + i * ROW_H;
            boolean sel = idx == selectedKeyIdx;
            boolean hov = inBounds(mouseX, mouseY, leftX, rowY, LEFT_W-3, ROW_H);
            g.fill(leftX, rowY, leftX+LEFT_W, rowY+ROW_H-1,
                    sel ? 0xFF2A5A2A : hov ? 0xFF1A3A1A : 0xFF0F1F0F);
            // 只绘制 key 本身，注释在下方说明面板里显示
            g.drawString(font, "§a" + k, leftX+4, rowY+4, 0xFFFFFF, false);
        }
        renderScrollbar(g, leftX+LEFT_W-3, listY, 3, listH,
                keyList.size(), KEY_VISIBLE, keyListScroll);

        // ════ 说明面板（列表下方，显示选中/悬停行的注释）══════════
        g.fill(leftX-1, descY-1, leftX+LEFT_W+1, descY+descH+1, 0xFF333333);
        g.fill(leftX,   descY,   leftX+LEFT_W,   descY+descH,   0xFF0A0A18);

        // 标题行
        g.fill(leftX, descY, leftX+LEFT_W, descY+10, 0xFF111130);
        g.drawString(font, "§7说明", leftX+3, descY+1, 0x888888, false);

        // 确定显示哪个 key 的说明
        // 优先：选中行；其次：鼠标悬停行
        int hoverIdx = -1;
        if (inBounds(mouseX, mouseY, leftX, listY, LEFT_W-3, listH)) {
            hoverIdx = (mouseY - listY) / ROW_H + keyListScroll;
            if (hoverIdx >= keyList.size()) hoverIdx = -1;
        }
        int showIdx = (selectedKeyIdx >= 0) ? selectedKeyIdx : hoverIdx;

        if (showIdx >= 0 && showIdx < keyList.size()) {
            String k = keyList.get(showIdx);
            String comment = KEY_COMMENTS.getOrDefault(k, "（暂无说明）");
            // key 名
            g.drawString(font, "§f" + k, leftX+4, descY+12, 0xFFFFFF, false);
            // 注释（灰色，支持超出宽度换行）
            renderWrappedText(g, comment, leftX+4, descY+23, LEFT_W-8, 0x999999);
        } else {
            g.drawString(font, "§8点击左侧列表中的 Key 查看说明",
                    leftX+4, descY+22, 0x666666, false);
        }

        // 输入框标签
        int inputY = descY + descH + 4;
        g.drawString(font, "§7添加（支持逗号批量）:", leftX, inputY-10, 0x888888, false);
        keyInputBox.render(g, mouseX, mouseY, pt);

        // ════ 右侧预设列表 ══════════════════════════════════════════

        var allPresets = NbtIgnorePresetManager.getAll();
        g.drawString(font, "§6预设  §8(" + allPresets.size() + ")  §7单击选中",
                rightX, presetY-12, 0xAAAAAA, false);

        g.fill(rightX-1, presetY-1, rightX+rightW+1, presetY+presetH+1, 0xFF444444);
        g.fill(rightX,   presetY,   rightX+rightW,   presetY+presetH,   0xFF1A1A0F);

        int maxPS = Math.max(0, allPresets.size() - PRESET_VISIBLE);
        presetScroll = clamp(presetScroll, 0, maxPS);

        for (int i = 0; i < PRESET_VISIBLE; i++) {
            int idx = i + presetScroll;
            if (idx >= allPresets.size()) break;
            var p = allPresets.get(idx);
            int rowY = presetY + i * ROW_H;
            boolean sel = idx == selectedPresetIdx;
            boolean hov = inBounds(mouseX, mouseY, rightX, rowY, rightW-3, ROW_H);
            g.fill(rightX, rowY, rightX+rightW, rowY+ROW_H-1,
                    sel ? 0xFF4A3A00 : hov ? 0xFF2A2A00 : 0xFF1A1A0F);
            g.drawString(font, "§6" + p.name(), rightX+4, rowY+4, 0xFFFFFF, false);
            g.drawString(font, "§8" + p.keys().size() + "项",
                    rightX+rightW-36, rowY+4, 0xFFFFFF, false);
        }
        renderScrollbar(g, rightX+rightW-3, presetY, 3, presetH,
                allPresets.size(), PRESET_VISIBLE, presetScroll);

        // ── 选中预设的 key 预览（逐行，带注释）────────────────────
        int pvY = presetY + presetH + 62;
        int pvBottom = py + H - 32;
        if (selectedPresetIdx >= 0 && selectedPresetIdx < allPresets.size()
                && !showPresetNameInput) {
            var sel = allPresets.get(selectedPresetIdx);
            g.drawString(font, "§7「" + sel.name() + "」预览:", rightX, pvY-10, 0x888888, false);
            // 背景
            g.fill(rightX-1, pvY-1, rightX+rightW+1, pvBottom+1, 0xFF222222);
            g.fill(rightX,   pvY,   rightX+rightW,   pvBottom,   0xFF0D0D15);
            int ly = pvY + 2;
            for (String k : sel.keys()) {
                if (ly + 9 > pvBottom) {
                    g.drawString(font, "§8... 还有更多", rightX+4, ly, 0x666666, false);
                    break;
                }
                String comment = KEY_COMMENTS.getOrDefault(k, "");
                // key 用绿色，注释用灰色，分两段绘制
                int kw = font.width("§a" + k);
                g.drawString(font, "§a" + k, rightX+4, ly, 0xFFFFFF, false);
                if (!comment.isEmpty()) {
                    // 注释放在 key 右侧，超出则截断
                    int maxCommentW = rightW - kw - 14;
                    String commentDisp = comment;
                    while (font.width("§8  " + commentDisp) > maxCommentW && commentDisp.length() > 4)
                        commentDisp = commentDisp.substring(0, commentDisp.length()-1);
                    if (!commentDisp.equals(comment)) commentDisp += "…";
                    g.drawString(font, "§8  " + commentDisp, rightX+4+kw, ly, 0xFFFFFF, false);
                }
                ly += 10;
            }
        }

        // 另存为预设输入区
        if (showPresetNameInput) {
            g.fill(rightX-2, presetY+presetH+38, rightX+rightW+2,
                    presetY+presetH+60, 0xFF222200);
            g.drawString(font, "§e预设名称:", rightX, presetY+presetH+30, 0xAAAAAA, false);
            presetNameBox.render(g, mouseX, mouseY, pt);
        }

        super.render(g, mouseX, mouseY, pt);
    }

    /** 简单换行绘制，超宽时换到下一行 */
    private void renderWrappedText(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        if (font.width(text) <= maxW) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        // 按空格/汉字分行（简化：按字符逐字切割）
        StringBuilder line = new StringBuilder();
        int curY = y;
        for (char c : text.toCharArray()) {
            if (font.width(line + String.valueOf(c)) > maxW) {
                g.drawString(font, line.toString(), x, curY, color, false);
                line.setLength(0);
                curY += 10;
                if (curY + 10 > y + descH - 14) break; // 超出说明框高度
            }
            line.append(c);
        }
        if (line.length() > 0) g.drawString(font, line.toString(), x, curY, color, false);
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int w, int h,
                                 int total, int visible, int scroll) {
        if (total <= visible) return;
        int thumbH = Math.max(8, h * visible / total);
        int maxS   = total - visible;
        int thumbY = y + (maxS > 0 ? scroll * (h - thumbH) / maxS : 0);
        g.fill(x, y, x+w, y+h, 0xFF333333);
        g.fill(x, thumbY, x+w, thumbY+thumbH, 0xFF88AAFF);
    }

    // ── 输入处理 ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (inBounds((int)mx, (int)my, leftX, listY, LEFT_W, listH)) {
            int row = ((int)my - listY) / ROW_H;
            int idx = row + keyListScroll;
            if (idx >= 0 && idx < keyList.size()) { selectedKeyIdx = idx; return true; }
        }
        if (inBounds((int)mx, (int)my, rightX, presetY, rightW, presetH)) {
            int row = ((int)my - presetY) / ROW_H;
            int idx = row + presetScroll;
            if (idx >= 0 && idx < NbtIgnorePresetManager.getAll().size()) {
                selectedPresetIdx = idx; return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inBounds((int)mx, (int)my, leftX, listY, LEFT_W, listH)) {
            keyListScroll = clamp(keyListScroll-(int)delta, 0,
                    Math.max(0, keyList.size()-KEY_VISIBLE)); return true;
        }
        if (inBounds((int)mx, (int)my, rightX, presetY, rightW, presetH)) {
            presetScroll = clamp(presetScroll-(int)delta, 0,
                    Math.max(0, NbtIgnorePresetManager.getAll().size()-PRESET_VISIBLE)); return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mods) {
        if (showPresetNameInput && presetNameBox.isFocused()) {
            if (kc == 257 || kc == 335) { doSavePreset(); return true; }
            if (kc == 256) { showPresetNameInput = false; return true; }
            return presetNameBox.keyPressed(kc, sc, mods);
        }
        if (keyInputBox.isFocused()) {
            if (kc == 257 || kc == 335) { doAddKey(); return true; }
            return keyInputBox.keyPressed(kc, sc, mods);
        }
        if (kc == 264 && selectedKeyIdx < keyList.size()-1) { selectedKeyIdx++; return true; }
        if (kc == 265 && selectedKeyIdx > 0)                { selectedKeyIdx--; return true; }
        if (kc == 261)                                       { doRemoveSelected(); return true; }
        if (kc == 256) { onClose(); return true; }
        return super.keyPressed(kc, sc, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (showPresetNameInput && presetNameBox.isFocused()) return presetNameBox.charTyped(c, mods);
        if (keyInputBox.isFocused()) return keyInputBox.charTyped(c, mods);
        return super.charTyped(c, mods);
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }
}