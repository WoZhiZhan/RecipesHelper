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
    private static final List<String> SLASHBLADE_QUICK_KEYS = List.of(
            "bladeState.lastActionTime", "bladeState.TargetEntity",
            "bladeState.Damage", "bladeState.currentCombo",
            "bladeState._onClick", "bladeState.killCount",
            "bladeState.proudSoul", "bladeState.RepairCounter");
    static {
        KEY_COMMENTS.put("bladeState.lastActionTime",     "registerhelper.gui.nbt_ignore.comment.last_action_time");
        KEY_COMMENTS.put("bladeState.TargetEntity",       "registerhelper.gui.nbt_ignore.comment.target_entity");
        KEY_COMMENTS.put("bladeState.Damage",             "registerhelper.gui.nbt_ignore.comment.damage");
        KEY_COMMENTS.put("bladeState.currentCombo",       "registerhelper.gui.nbt_ignore.comment.current_combo");
        KEY_COMMENTS.put("bladeState._onClick",           "registerhelper.gui.nbt_ignore.comment.on_click");
        KEY_COMMENTS.put("bladeState.killCount",          "registerhelper.gui.nbt_ignore.comment.kill_count");
        KEY_COMMENTS.put("bladeState.proudSoul",          "registerhelper.gui.nbt_ignore.comment.proud_soul");
        KEY_COMMENTS.put("bladeState.RepairCounter",      "registerhelper.gui.nbt_ignore.comment.repair_counter");
        KEY_COMMENTS.put("bladeState.ComboRoot",          "registerhelper.gui.nbt_ignore.comment.combo_root");
        KEY_COMMENTS.put("bladeState.AttackAmplifier",    "registerhelper.gui.nbt_ignore.comment.attack_amplifier");
        KEY_COMMENTS.put("bladeState.ModelName",          "registerhelper.gui.nbt_ignore.comment.model_name");
        KEY_COMMENTS.put("bladeState.TextureName",        "registerhelper.gui.nbt_ignore.comment.texture_name");
        KEY_COMMENTS.put("bladeState.SummonedSwordColor", "registerhelper.gui.nbt_ignore.comment.summoned_color");
        KEY_COMMENTS.put("bladeState.SummonedSwordColorInverse", "registerhelper.gui.nbt_ignore.comment.summoned_color_inverse");
        KEY_COMMENTS.put("bladeState.baseAttackModifier", "registerhelper.gui.nbt_ignore.comment.base_attack_modifier");
        KEY_COMMENTS.put("bladeState.StandbyRenderType",  "registerhelper.gui.nbt_ignore.comment.standby_render_type");
        KEY_COMMENTS.put("bladeState.translationKey",     "registerhelper.gui.nbt_ignore.comment.translation_key");
        KEY_COMMENTS.put("bladeState.isSealed",           "registerhelper.gui.nbt_ignore.comment.is_sealed");
        KEY_COMMENTS.put("bladeState.isBroken",           "registerhelper.gui.nbt_ignore.comment.is_broken");
        KEY_COMMENTS.put("bladeState.maxDamage",          "registerhelper.gui.nbt_ignore.comment.max_damage");
        KEY_COMMENTS.put("bladeState.isDefaultBewitched", "registerhelper.gui.nbt_ignore.comment.default_bewitched");
        KEY_COMMENTS.put("bladeState.fallDecreaseRate",   "registerhelper.gui.nbt_ignore.comment.fall_decrease_rate");
        KEY_COMMENTS.put("bladeState.adjustXYZ",          "registerhelper.gui.nbt_ignore.comment.adjust_xyz");
        KEY_COMMENTS.put("bladeState.SpecialAttackType",  "registerhelper.gui.nbt_ignore.comment.special_attack_type");
        // 通用
        KEY_COMMENTS.put("Damage",                        "registerhelper.gui.nbt_ignore.comment.vanilla_damage");
        KEY_COMMENTS.put("RepairCost",                    "registerhelper.gui.nbt_ignore.comment.repair_cost");
        KEY_COMMENTS.put("display.Name",                  "registerhelper.gui.nbt_ignore.comment.display_name");
        KEY_COMMENTS.put("HideFlags",                     "registerhelper.gui.nbt_ignore.comment.hide_flags");
        KEY_COMMENTS.put("CustomModelData",               "registerhelper.gui.nbt_ignore.comment.custom_model_data");
        KEY_COMMENTS.put("Enchantments",                  "registerhelper.gui.nbt_ignore.comment.enchantments");
    }

    // ── 布局 ─────────────────────────────────────────────────────
    private static final int PREFERRED_WIDTH = 640;
    private static final int PREFERRED_HEIGHT = 480;
    private static final int MIN_WIDTH = 360;
    private static final int MIN_HEIGHT = 300;
    private static final int PAD = 12;
    private static final int ROW_H = 16;
    private static final int COLUMN_GAP = 16;

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
    private int draggingScrollbar = -1;
    private double scrollbarGrabOffset;

    private EditBox keyInputBox;
    private EditBox presetNameBox;
    private boolean showPresetNameInput = false;

    // 面板坐标（init后有效）
    private int px, py, panelW, panelH;
    // 左侧
    private int leftX, leftW, listY, listH, keyVisible;
    // 右侧
    private int rightX, presetY, rightW, presetH, presetVisible;
    // 说明面板（左侧列表下方）
    private int descY, descH;

    public NbtIgnoreEditorScreen(Screen parent, IngredientData data, Runnable onConfirm) {
        super(GuiText.component("registerhelper.gui.nbt_ignore.title"));
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
        String pendingKey = keyInputBox != null ? keyInputBox.getValue() : "";
        String pendingPresetName = presetNameBox != null ? presetNameBox.getValue() : "";
        boolean presetWasFocused = showPresetNameInput
                && presetNameBox != null && presetNameBox.isFocused();
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(this.width, this.height,
                PREFERRED_WIDTH, PREFERRED_HEIGHT, MIN_WIDTH, MIN_HEIGHT, 8, 8);
        px = panel.x();
        py = panel.y();
        panelW = panel.width();
        panelH = panel.height();

        // 左侧坐标
        leftX = px + PAD;
        int columnsWidth = panelW - PAD * 2 - COLUMN_GAP;
        leftW = GuiLayoutHelper.clamp(columnsWidth * 46 / 100,
                140, Math.max(140, columnsWidth - 140));
        listY = py + 52;
        keyVisible = GuiLayoutHelper.clamp((panelH - 230) / ROW_H, 3, 14);
        listH = keyVisible * ROW_H;

        // 右侧坐标
        rightX   = leftX + leftW + COLUMN_GAP;
        rightW   = panelW - (rightX - px) - PAD;
        presetY  = py + 52;
        presetVisible = GuiLayoutHelper.clamp((panelH - 240) / ROW_H, 3, 10);
        presetH  = presetVisible * ROW_H;

        // 说明面板：紧贴列表下方，高约50px
        descY = listY + listH + 4;
        descH = 50;

        // ── 左侧控件 ─────────────────────────────────────────────
        int inputY = descY + descH + 4;
        keyInputBox = new EditBox(this.font, leftX, inputY, leftW - 60, 14,
                GuiText.component("registerhelper.gui.nbt_ignore.key_field"));
        GuiTheme.styleInput(keyInputBox);
        keyInputBox.setMaxLength(256);
        keyInputBox.setHint(GuiText.component("registerhelper.gui.nbt_ignore.key_example"));
        keyInputBox.setValue(pendingKey);
        addWidget(keyInputBox);
        keyInputBox.setFocused(!presetWasFocused);

        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.add"),
                        btn -> doAddKey())
                .bounds(leftX + leftW - 56, inputY, 56, 14).build());

        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.remove_selected"),
                        btn -> doRemoveSelected())
                .bounds(leftX, inputY + 18, 72, 14).build());

        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.clear"),
                        btn -> { keyList.clear(); selectedKeyIdx = -1; })
                .bounds(leftX + 76, inputY + 18, 44, 14).build());

        // ── 右侧预设控件 ────────────────────────────────────────
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.load"),
                        btn -> doLoadPreset())
                .bounds(rightX, presetY + presetH + 6, 64, 14).build());

        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.delete"),
                        btn -> doDeletePreset())
                .bounds(rightX + 68, presetY + presetH + 6, 64, 14).build());

        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.save_as_preset"),
                        btn -> { showPresetNameInput = !showPresetNameInput; if (showPresetNameInput) presetNameBox.setValue(""); })
                .bounds(rightX, presetY + presetH + 24, 90, 14).build());

        presetNameBox = new EditBox(this.font,
                rightX, presetY + presetH + 42, rightW - 30, 14,
                GuiText.component("registerhelper.gui.nbt_ignore.name"));
        GuiTheme.styleInput(presetNameBox);
        presetNameBox.setMaxLength(64);
        presetNameBox.setHint(GuiText.component("registerhelper.gui.nbt_ignore.preset_name_hint"));
        presetNameBox.setValue(pendingPresetName);
        addWidget(presetNameBox);
        presetNameBox.setFocused(presetWasFocused);

        addRenderableWidget(Button.builder(Component.literal("§a✔"),
                        btn -> doSavePreset())
                .bounds(rightX + rightW - 26, presetY + presetH + 42, 26, 14).build());

        // 底部
        boolean showQuickFill = "slashblade".equals(itemNamespace);
        int footerGap = 8;
        int footerButtonWidth = Math.min(90, Math.max(56,
                (panelW - 40 - (showQuickFill ? 2 : 1) * footerGap)
                        / (showQuickFill ? 3 : 2)));
        int quickFillWidth = showQuickFill
                ? Math.min(132, Math.max(80, panelW - footerButtonWidth * 2 - footerGap * 2 - 24))
                : 0;
        int footerWidth = footerButtonWidth * 2 + footerGap
                + (showQuickFill ? quickFillWidth + footerGap : 0);
        int footerStartX = px + (panelW - footerWidth) / 2;
        if (showQuickFill) {
            addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.slashblade_quick"),
                            btn -> doSlashbladeQuickFill())
                    .bounds(footerStartX, py + panelH - 26, quickFillWidth, 20).build());
            footerStartX += quickFillWidth + footerGap;
        }
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.confirm"),
                        btn -> doConfirm())
                .bounds(footerStartX, py + panelH - 26, footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.cancel"),
                        btn -> onClose())
                .bounds(footerStartX + footerButtonWidth + footerGap,
                        py + panelH - 26, footerButtonWidth, 20).build());
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
        keyList.addAll(SLASHBLADE_QUICK_KEYS);
        selectedKeyIdx = -1;
    }

    private String presetDisplayName(NbtIgnorePresetManager.Preset preset) {
        return preset.keys().equals(SLASHBLADE_QUICK_KEYS)
                ? GuiText.string("registerhelper.gui.nbt_ignore.preset.slashblade")
                : preset.name();
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
        GuiTheme.drawBackdrop(g, this.width, this.height);

        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(px, py, panelW, panelH);
        GuiTheme.drawPanel(g, panel, 28, GuiTheme.INFO);
        g.drawCenteredString(font, GuiText.component("registerhelper.gui.nbt_ignore.title"),
                px+panelW/2, py+10, GuiTheme.TEXT_ON_HEADER);

        // 警告
        String warning = GuiLayoutHelper.ellipsis(font,
                GuiText.string("registerhelper.gui.nbt_ignore.warning"), panelW - PAD * 2);
        g.drawString(font, warning, px+PAD, py+32, GuiTheme.DANGER, false);

        g.drawString(font,
                GuiText.string("registerhelper.gui.nbt_ignore.current_keys", keyList.size()),
                leftX, listY-12, GuiTheme.TEXT_MUTED, false);

        // 列表背景
        GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(leftX, listY, leftW, listH), false);

        int maxKS = Math.max(0, keyList.size() - keyVisible);
        keyListScroll = clamp(keyListScroll, 0, maxKS);

        for (int i = 0; i < keyVisible; i++) {
            int idx  = i + keyListScroll;
            if (idx >= keyList.size()) break;
            String k = keyList.get(idx);
            int rowY = listY + i * ROW_H;
            boolean sel = idx == selectedKeyIdx;
            boolean hov = inBounds(mouseX, mouseY, leftX, rowY, leftW-3, ROW_H);
            GuiTheme.drawRow(g, leftX, rowY, leftW, ROW_H - 1, i, hov, sel);
            // 只绘制 key 本身，注释在下方说明面板里显示
            g.drawString(font, GuiLayoutHelper.ellipsis(font, "§a" + k, leftW - 10),
                    leftX+4, rowY+4, 0xFFFFFF, false);
        }
        renderScrollbar(g, leftX+leftW-3, listY, 3, listH,
                keyList.size(), keyVisible, keyListScroll);

        // ════ 说明面板（列表下方，显示选中/悬停行的注释）══════════
        GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(leftX, descY, leftW, descH), true);

        // 标题行
        g.fill(leftX, descY, leftX+leftW, descY+10, GuiTheme.SECTION);
        g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.description"),
                leftX+3, descY+1, GuiTheme.TEXT_MUTED, false);

        // 确定显示哪个 key 的说明
        // 优先：选中行；其次：鼠标悬停行
        int hoverIdx = -1;
        if (inBounds(mouseX, mouseY, leftX, listY, leftW-3, listH)) {
            hoverIdx = (mouseY - listY) / ROW_H + keyListScroll;
            if (hoverIdx >= keyList.size()) hoverIdx = -1;
        }
        int showIdx = (selectedKeyIdx >= 0) ? selectedKeyIdx : hoverIdx;

        if (showIdx >= 0 && showIdx < keyList.size()) {
            String k = keyList.get(showIdx);
            String commentKey = KEY_COMMENTS.get(k);
            String comment = commentKey == null ? GuiText.string("registerhelper.gui.nbt_ignore.no_description")
                    : GuiText.string(commentKey);
            // key 名
            g.drawString(font, GuiLayoutHelper.ellipsis(font, k, leftW - 8),
                    leftX+4, descY+12, GuiTheme.TEXT, false);
            // 注释（灰色，支持超出宽度换行）
            renderWrappedText(g, comment, leftX+4, descY+23, leftW-8, GuiTheme.TEXT_MUTED);
        } else {
            g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.select_key_help"),
                    leftX+4, descY+22, GuiTheme.TEXT_MUTED, false);
        }

        // 输入框标签
        int inputY = descY + descH + 4;
        g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.batch_add"),
                leftX, inputY-10, GuiTheme.TEXT_MUTED, false);
        GuiTheme.drawInput(g, keyInputBox);
        keyInputBox.render(g, mouseX, mouseY, pt);

        // ════ 右侧预设列表 ══════════════════════════════════════════

        var allPresets = NbtIgnorePresetManager.getAll();
        g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.presets", allPresets.size()),
                rightX, presetY-12, GuiTheme.TEXT_MUTED, false);

        GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(rightX, presetY, rightW, presetH), false);

        int maxPS = Math.max(0, allPresets.size() - presetVisible);
        presetScroll = clamp(presetScroll, 0, maxPS);

        for (int i = 0; i < presetVisible; i++) {
            int idx = i + presetScroll;
            if (idx >= allPresets.size()) break;
            var p = allPresets.get(idx);
            int rowY = presetY + i * ROW_H;
            boolean sel = idx == selectedPresetIdx;
            boolean hov = inBounds(mouseX, mouseY, rightX, rowY, rightW-3, ROW_H);
            GuiTheme.drawRow(g, rightX, rowY, rightW, ROW_H - 1, i, hov, sel);
            g.drawString(font, GuiLayoutHelper.ellipsis(font,
                    presetDisplayName(p), Math.max(1, rightW - 42)),
                    rightX+4, rowY+4, GuiTheme.TEXT, false);
            g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.item_count", p.keys().size()),
                    rightX+rightW-36, rowY+4, GuiTheme.TEXT_MUTED, false);
        }
        renderScrollbar(g, rightX+rightW-3, presetY, 3, presetH,
                allPresets.size(), presetVisible, presetScroll);

        // ── 选中预设的 key 预览（逐行，带注释）────────────────────
        int pvY = presetY + presetH + 62;
        int pvBottom = py + panelH - 32;
        if (selectedPresetIdx >= 0 && selectedPresetIdx < allPresets.size()
                && !showPresetNameInput) {
            var sel = allPresets.get(selectedPresetIdx);
            String previewLabel = GuiLayoutHelper.ellipsis(font,
                    GuiText.string("registerhelper.gui.nbt_ignore.preset_preview",
                            presetDisplayName(sel)), rightW);
            g.drawString(font, previewLabel, rightX, pvY-10, GuiTheme.TEXT_MUTED, false);
            // 背景
            GuiTheme.drawSurface(g,
                    new GuiLayoutHelper.Bounds(rightX, pvY, rightW, Math.max(1, pvBottom - pvY)), true);
            int ly = pvY + 2;
            for (String k : sel.keys()) {
                if (ly + 9 > pvBottom) {
                    g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.more"),
                            rightX+4, ly, GuiTheme.TEXT_MUTED, false);
                    break;
                }
                String commentKey = KEY_COMMENTS.get(k);
                String comment = commentKey == null ? "" : GuiText.string(commentKey);
                // key 用绿色，注释用灰色，分两段绘制
                int availableWidth = Math.max(1, rightW - 8);
                String keyDisplay = GuiLayoutHelper.ellipsis(font, "§a" + k, availableWidth);
                int keyWidth = font.width(keyDisplay);
                g.drawString(font, keyDisplay, rightX+4, ly, 0xFFFFFF, false);
                int maxCommentWidth = availableWidth - keyWidth - 4;
                if (!comment.isEmpty() && maxCommentWidth > font.width("...")) {
                    // 注释放在 key 右侧，超出则截断
                    String commentDisplay = GuiLayoutHelper.ellipsis(
                            font, "§8  " + comment, maxCommentWidth);
                    g.drawString(font, commentDisplay,
                            rightX + 8 + keyWidth, ly, 0xFFFFFF, false);
                }
                ly += 10;
            }
        }

        // 另存为预设输入区
        if (showPresetNameInput) {
            g.fill(rightX-2, presetY+presetH+38, rightX+rightW+2,
                    presetY+presetH+60, GuiTheme.SECTION);
            g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.preset_name"),
                    rightX, presetY+presetH+30, GuiTheme.TEXT_MUTED, false);
            GuiTheme.drawInput(g, presetNameBox);
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
        GuiLayoutHelper.Scrollbar scrollbar = GuiLayoutHelper.scrollbar(
                new GuiLayoutHelper.Bounds(x, y, w, h), total, visible, scroll, 8);
        if (!scrollbar.visible()) return;
        GuiTheme.drawScrollbar(g, scrollbar, -1, -1);
    }

    // ── 输入处理 ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            GuiLayoutHelper.Scrollbar leftScrollbar = currentScrollbar(0);
            if (leftScrollbar.contains(mx, my)) {
                draggingScrollbar = 0;
                scrollbarGrabOffset = leftScrollbar.grabOffset(my);
                keyListScroll = leftScrollbar.offsetForPointer(my, scrollbarGrabOffset);
                return true;
            }
            GuiLayoutHelper.Scrollbar rightScrollbar = currentScrollbar(1);
            if (rightScrollbar.contains(mx, my)) {
                draggingScrollbar = 1;
                scrollbarGrabOffset = rightScrollbar.grabOffset(my);
                presetScroll = rightScrollbar.offsetForPointer(my, scrollbarGrabOffset);
                return true;
            }
        }
        if (inBounds((int)mx, (int)my, leftX, listY, leftW, listH)) {
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

    private GuiLayoutHelper.Scrollbar currentScrollbar(int side) {
        if (side == 0) {
            return GuiLayoutHelper.scrollbar(
                    new GuiLayoutHelper.Bounds(leftX + leftW - 3, listY, 3, listH),
                    keyList.size(), keyVisible, keyListScroll, 8);
        }
        return GuiLayoutHelper.scrollbar(
                new GuiLayoutHelper.Bounds(rightX + rightW - 3, presetY, 3, presetH),
                NbtIgnorePresetManager.getAll().size(), presetVisible, presetScroll, 8);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingScrollbar >= 0 && btn == 0) {
            GuiLayoutHelper.Scrollbar scrollbar = currentScrollbar(draggingScrollbar);
            if (draggingScrollbar == 0) {
                keyListScroll = scrollbar.offsetForPointer(my, scrollbarGrabOffset);
            } else {
                presetScroll = scrollbar.offsetForPointer(my, scrollbarGrabOffset);
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
        if (inBounds((int)mx, (int)my, leftX, listY, leftW, listH)) {
            keyListScroll = clamp(keyListScroll-(int)delta, 0,
                    Math.max(0, keyList.size()-keyVisible)); return true;
        }
        if (inBounds((int)mx, (int)my, rightX, presetY, rightW, presetH)) {
            presetScroll = clamp(presetScroll-(int)delta, 0,
                    Math.max(0, NbtIgnorePresetManager.getAll().size()-presetVisible)); return true;
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
