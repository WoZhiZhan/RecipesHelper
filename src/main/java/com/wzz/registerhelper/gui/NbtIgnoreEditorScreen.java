package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.gui.recipe.IngredientData;
import com.wzz.registerhelper.util.NbtIgnorePresetManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class NbtIgnoreEditorScreen extends Screen {
    private static final Map<String, String> KEY_COMMENTS = new LinkedHashMap<>();
    private static final List<String> SLASHBLADE_QUICK_KEYS = List.of(
            "bladeState.lastActionTime", "bladeState.TargetEntity", "bladeState.Damage",
            "bladeState.currentCombo", "bladeState._onClick", "bladeState.killCount",
            "bladeState.proudSoul", "bladeState.RepairCounter");

    static {
        String[][] comments = {
                {"bladeState.lastActionTime", "last_action_time"}, {"bladeState.TargetEntity", "target_entity"},
                {"bladeState.Damage", "damage"}, {"bladeState.currentCombo", "current_combo"},
                {"bladeState._onClick", "on_click"}, {"bladeState.killCount", "kill_count"},
                {"bladeState.proudSoul", "proud_soul"}, {"bladeState.RepairCounter", "repair_counter"},
                {"bladeState.ComboRoot", "combo_root"}, {"bladeState.AttackAmplifier", "attack_amplifier"},
                {"bladeState.ModelName", "model_name"}, {"bladeState.TextureName", "texture_name"},
                {"bladeState.SummonedSwordColor", "summoned_color"},
                {"bladeState.SummonedSwordColorInverse", "summoned_color_inverse"},
                {"bladeState.baseAttackModifier", "base_attack_modifier"},
                {"bladeState.StandbyRenderType", "standby_render_type"},
                {"bladeState.translationKey", "translation_key"}, {"bladeState.isSealed", "is_sealed"},
                {"bladeState.isBroken", "is_broken"}, {"bladeState.maxDamage", "max_damage"},
                {"bladeState.isDefaultBewitched", "default_bewitched"},
                {"bladeState.fallDecreaseRate", "fall_decrease_rate"}, {"bladeState.adjustXYZ", "adjust_xyz"},
                {"bladeState.SpecialAttackType", "special_attack_type"}, {"Damage", "vanilla_damage"},
                {"RepairCost", "repair_cost"}, {"display.Name", "display_name"},
                {"HideFlags", "hide_flags"}, {"CustomModelData", "custom_model_data"},
                {"Enchantments", "enchantments"}
        };
        for (String[] comment : comments) {
            KEY_COMMENTS.put(comment[0], "registerhelper.gui.nbt_ignore.comment." + comment[1]);
        }
    }

    private static final int PREFERRED_WIDTH = 640;
    private static final int PREFERRED_HEIGHT = 480;
    private static final int MIN_WIDTH = 360;
    private static final int MIN_HEIGHT = 300;
    private static final int PAD = 12;
    private static final int ROW_H = 16;
    private static final int COLUMN_GAP = 16;

    private final Screen parent;
    private final IngredientData targetData;
    private final Runnable onConfirm;
    private final String itemNamespace;
    private final List<String> keyList = new ArrayList<>();
    private int keyListScroll;
    private int selectedKeyIdx = -1;
    private int presetScroll;
    private int selectedPresetIdx = -1;
    private int draggingScrollbar = -1;
    private double scrollbarGrabOffset;
    private EditBox keyInputBox;
    private EditBox presetNameBox;
    private boolean showPresetNameInput;
    private int px, py, panelW, panelH;
    private int leftX, leftW, listY, listH, keyVisible;
    private int rightX, presetY, rightW, presetH, presetVisible;
    private int descY, descH;

    public NbtIgnoreEditorScreen(Screen parent, IngredientData data, Runnable onConfirm) {
        super(GuiText.component("registerhelper.gui.nbt_ignore.title"));
        this.parent = parent;
        this.targetData = data;
        this.onConfirm = onConfirm;
        keyList.addAll(data.getIgnoreNbtKeys());
        ResourceLocationKey itemKey = new ResourceLocationKey(
                BuiltInRegistries.ITEM.getKey(data.getItemStack().getItem()));
        itemNamespace = itemKey.namespace();
    }

    @Override
    protected void init() {
        String pendingKey = keyInputBox != null ? keyInputBox.getValue() : "";
        String pendingPresetName = presetNameBox != null ? presetNameBox.getValue() : "";
        boolean presetWasFocused = showPresetNameInput && presetNameBox != null && presetNameBox.isFocused();
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(width, height,
                PREFERRED_WIDTH, PREFERRED_HEIGHT, MIN_WIDTH, MIN_HEIGHT, 8, 8);
        px = panel.x(); py = panel.y(); panelW = panel.width(); panelH = panel.height();
        leftX = px + PAD;
        int columnsWidth = panelW - PAD * 2 - COLUMN_GAP;
        leftW = GuiLayoutHelper.clamp(columnsWidth * 46 / 100,
                140, Math.max(140, columnsWidth - 140));
        listY = py + 52;
        keyVisible = GuiLayoutHelper.clamp((panelH - 230) / ROW_H, 3, 14);
        listH = keyVisible * ROW_H;
        rightX = leftX + leftW + COLUMN_GAP;
        rightW = panelW - (rightX - px) - PAD;
        presetY = py + 52;
        presetVisible = GuiLayoutHelper.clamp((panelH - 240) / ROW_H, 3, 10);
        presetH = presetVisible * ROW_H;
        descY = listY + listH + 4;
        descH = 50;

        int inputY = descY + descH + 4;
        keyInputBox = new EditBox(font, leftX, inputY, leftW - 60,
                14, GuiText.component("registerhelper.gui.nbt_ignore.key_field"));
        GuiTheme.styleInput(keyInputBox);
        keyInputBox.setMaxLength(256);
        keyInputBox.setHint(GuiText.component("registerhelper.gui.nbt_ignore.key_example"));
        keyInputBox.setValue(pendingKey);
        addWidget(keyInputBox);
        keyInputBox.setFocused(!presetWasFocused);
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.add"),
                        btn -> doAddKey()).bounds(leftX + leftW - 56, inputY, 56, 14).build());
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.remove_selected"),
                        btn -> doRemoveSelected()).bounds(leftX, inputY + 18, 72, 14).build());
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.clear"),
                        btn -> { keyList.clear(); selectedKeyIdx = -1; })
                .bounds(leftX + 76, inputY + 18, 44, 14).build());

        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.load"),
                        btn -> doLoadPreset()).bounds(rightX, presetY + presetH + 6, 64, 14).build());
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.delete"),
                        btn -> doDeletePreset()).bounds(rightX + 68, presetY + presetH + 6, 64, 14).build());
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.nbt_ignore.save_as_preset"),
                        btn -> { showPresetNameInput = !showPresetNameInput;
                            if (showPresetNameInput) presetNameBox.setValue(""); })
                .bounds(rightX, presetY + presetH + 24, 90, 14).build());

        presetNameBox = new EditBox(font, rightX, presetY + presetH + 42,
                rightW - 30, 14, GuiText.component("registerhelper.gui.nbt_ignore.name"));
        GuiTheme.styleInput(presetNameBox);
        presetNameBox.setMaxLength(64);
        presetNameBox.setHint(GuiText.component("registerhelper.gui.nbt_ignore.preset_name_hint"));
        presetNameBox.setValue(pendingPresetName);
        addWidget(presetNameBox);
        presetNameBox.setFocused(presetWasFocused);
        addRenderableWidget(Button.builder(Component.literal("§a✔"), btn -> doSavePreset())
                .bounds(rightX + rightW - 26, presetY + presetH + 42, 26, 14).build());

        boolean showQuickFill = "slashblade".equals(itemNamespace);
        int footerGap = 8;
        int footerButtonWidth = Math.min(90, Math.max(56,
                (panelW - 40 - (showQuickFill ? 2 : 1) * footerGap) / (showQuickFill ? 3 : 2)));
        int quickFillWidth = showQuickFill
                ? Math.min(132, Math.max(80, panelW - footerButtonWidth * 2 - footerGap * 2 - 24)) : 0;
        int footerWidth = footerButtonWidth * 2 + footerGap
                + (showQuickFill ? quickFillWidth + footerGap : 0);
        int footerStartX = px + (panelW - footerWidth) / 2;
        if (showQuickFill) {
            addRenderableWidget(Button.builder(GuiText.component(
                            "registerhelper.gui.nbt_ignore.slashblade_quick"), btn -> doSlashbladeQuickFill())
                    .bounds(footerStartX, py + panelH - 26, quickFillWidth, 20).build());
            footerStartX += quickFillWidth + footerGap;
        }
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.confirm"),
                        btn -> doConfirm()).bounds(footerStartX, py + panelH - 26,
                        footerButtonWidth, 20).build());
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.cancel"),
                        btn -> onClose()).bounds(footerStartX + footerButtonWidth + footerGap,
                        py + panelH - 26, footerButtonWidth, 20).build());
    }

    private void doAddKey() {
        String value = keyInputBox.getValue().trim();
        if (value.isEmpty()) return;
        for (String part : value.split(",")) {
            String key = part.trim();
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
            selectedPresetIdx = Math.min(selectedPresetIdx,
                    NbtIgnorePresetManager.getAll().size() - 1);
        }
    }

    private void doSavePreset() {
        String name = presetNameBox.getValue().trim();
        if (name.isEmpty() || keyList.isEmpty()) {
            showPresetNameInput = false;
            return;
        }
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
                ? GuiText.string("registerhelper.gui.nbt_ignore.preset.slashblade") : preset.name();
    }

    private void doConfirm() {
        targetData.setIgnoreNbtKeys(List.copyOf(keyList));
        if (!keyList.isEmpty()) targetData.setIncludeNBT(true);
        onConfirm.run();
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.drawBackdrop(g, width, height);
        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(px, py, panelW, panelH);
        GuiTheme.drawPanel(g, panel, 28, GuiTheme.INFO);
        g.drawCenteredString(font, GuiText.component("registerhelper.gui.nbt_ignore.title"),
                px + panelW / 2, py + 10, GuiTheme.TEXT_ON_HEADER);
        g.drawString(font, GuiLayoutHelper.ellipsis(font,
                GuiText.string("registerhelper.gui.nbt_ignore.warning"), panelW - PAD * 2),
                px + PAD, py + 32, GuiTheme.DANGER, false);
        g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.current_keys", keyList.size()),
                leftX, listY - 12, GuiTheme.TEXT_MUTED, false);
        GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(leftX, listY, leftW, listH), false);
        keyListScroll = clamp(keyListScroll, 0, Math.max(0, keyList.size() - keyVisible));
        for (int i = 0; i < keyVisible; i++) {
            int index = i + keyListScroll;
            if (index >= keyList.size()) break;
            int rowY = listY + i * ROW_H;
            boolean selected = index == selectedKeyIdx;
            boolean hovered = inBounds(mouseX, mouseY, leftX, rowY, leftW - 3, ROW_H);
            GuiTheme.drawRow(g, leftX, rowY, leftW, ROW_H - 1, i, hovered, selected);
            g.drawString(font, GuiLayoutHelper.ellipsis(font, "§a" + keyList.get(index), leftW - 10),
                    leftX + 4, rowY + 4, GuiTheme.TEXT, false);
        }
        renderScrollbar(g, leftX + leftW - 3, listY, 3, listH,
                keyList.size(), keyVisible, keyListScroll);

        GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(leftX, descY, leftW, descH), true);
        g.fill(leftX, descY, leftX + leftW, descY + 10, GuiTheme.SECTION);
        g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.description"),
                leftX + 3, descY + 1, GuiTheme.TEXT_MUTED, false);
        int hoverIndex = -1;
        if (inBounds(mouseX, mouseY, leftX, listY, leftW - 3, listH)) {
            hoverIndex = (mouseY - listY) / ROW_H + keyListScroll;
            if (hoverIndex >= keyList.size()) hoverIndex = -1;
        }
        int showIndex = selectedKeyIdx >= 0 ? selectedKeyIdx : hoverIndex;
        if (showIndex >= 0 && showIndex < keyList.size()) {
            String key = keyList.get(showIndex);
            String commentKey = KEY_COMMENTS.get(key);
            String comment = commentKey == null
                    ? GuiText.string("registerhelper.gui.nbt_ignore.no_description")
                    : GuiText.string(commentKey);
            g.drawString(font, GuiLayoutHelper.ellipsis(font, key, leftW - 8),
                    leftX + 4, descY + 12, GuiTheme.TEXT, false);
            renderWrappedText(g, comment, leftX + 4, descY + 23, leftW - 8, GuiTheme.TEXT_MUTED);
        } else {
            g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.select_key_help"),
                    leftX + 4, descY + 22, GuiTheme.TEXT_MUTED, false);
        }

        int inputY = descY + descH + 4;
        g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.batch_add"),
                leftX, inputY - 10, GuiTheme.TEXT_MUTED, false);
        GuiTheme.drawInput(g, keyInputBox);
        keyInputBox.render(g, mouseX, mouseY, partialTick);

        var presets = NbtIgnorePresetManager.getAll();
        g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.presets", presets.size()),
                rightX, presetY - 12, GuiTheme.TEXT_MUTED, false);
        GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(rightX, presetY, rightW, presetH), false);
        presetScroll = clamp(presetScroll, 0, Math.max(0, presets.size() - presetVisible));
        for (int i = 0; i < presetVisible; i++) {
            int index = i + presetScroll;
            if (index >= presets.size()) break;
            var preset = presets.get(index);
            int rowY = presetY + i * ROW_H;
            boolean selected = index == selectedPresetIdx;
            boolean hovered = inBounds(mouseX, mouseY, rightX, rowY, rightW - 3, ROW_H);
            GuiTheme.drawRow(g, rightX, rowY, rightW, ROW_H - 1, i, hovered, selected);
            g.drawString(font, GuiLayoutHelper.ellipsis(font, presetDisplayName(preset),
                    Math.max(1, rightW - 42)), rightX + 4, rowY + 4, GuiTheme.TEXT, false);
            g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.item_count", preset.keys().size()),
                    rightX + rightW - 36, rowY + 4, GuiTheme.TEXT_MUTED, false);
        }
        renderScrollbar(g, rightX + rightW - 3, presetY, 3, presetH,
                presets.size(), presetVisible, presetScroll);

        int previewY = presetY + presetH + 62;
        int previewBottom = py + panelH - 32;
        if (selectedPresetIdx >= 0 && selectedPresetIdx < presets.size() && !showPresetNameInput) {
            var preset = presets.get(selectedPresetIdx);
            g.drawString(font, GuiLayoutHelper.ellipsis(font,
                    GuiText.string("registerhelper.gui.nbt_ignore.preset_preview", presetDisplayName(preset)),
                    rightW), rightX, previewY - 10, GuiTheme.TEXT_MUTED, false);
            GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(rightX, previewY, rightW,
                    Math.max(1, previewBottom - previewY)), true);
            int lineY = previewY + 2;
            for (String key : preset.keys()) {
                if (lineY + 9 > previewBottom) {
                    g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.more"),
                            rightX + 4, lineY, GuiTheme.TEXT_MUTED, false);
                    break;
                }
                String commentKey = KEY_COMMENTS.get(key);
                String comment = commentKey == null ? "" : GuiText.string(commentKey);
                int available = Math.max(1, rightW - 8);
                String keyDisplay = GuiLayoutHelper.ellipsis(font, "§a" + key, available);
                int keyWidth = font.width(keyDisplay);
                g.drawString(font, keyDisplay, rightX + 4, lineY, GuiTheme.TEXT, false);
                int commentWidth = available - keyWidth - 4;
                if (!comment.isEmpty() && commentWidth > font.width("...")) {
                    g.drawString(font, GuiLayoutHelper.ellipsis(font, "§8  " + comment, commentWidth),
                            rightX + 8 + keyWidth, lineY, GuiTheme.TEXT, false);
                }
                lineY += 10;
            }
        }
        if (showPresetNameInput) {
            g.fill(rightX - 2, presetY + presetH + 38, rightX + rightW + 2,
                    presetY + presetH + 60, GuiTheme.SECTION);
            g.drawString(font, GuiText.string("registerhelper.gui.nbt_ignore.preset_name"),
                    rightX, presetY + presetH + 30, GuiTheme.TEXT_MUTED, false);
            GuiTheme.drawInput(g, presetNameBox);
            presetNameBox.render(g, mouseX, mouseY, partialTick);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderWrappedText(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        StringBuilder line = new StringBuilder();
        int currentY = y;
        for (char character : text.toCharArray()) {
            if (font.width(line + String.valueOf(character)) > maxWidth) {
                g.drawString(font, line.toString(), x, currentY, color, false);
                line.setLength(0);
                currentY += 10;
                if (currentY + 10 > y + descH - 14) break;
            }
            line.append(character);
        }
        if (!line.isEmpty()) g.drawString(font, line.toString(), x, currentY, color, false);
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int w, int h,
                                 int total, int visible, int scroll) {
        GuiLayoutHelper.Scrollbar scrollbar = GuiLayoutHelper.scrollbar(
                new GuiLayoutHelper.Bounds(x, y, w, h), total, visible, scroll, 8);
        GuiTheme.drawScrollbar(g, scrollbar, -1, -1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            GuiLayoutHelper.Scrollbar leftScrollbar = currentScrollbar(0);
            if (leftScrollbar.contains(mouseX, mouseY)) {
                draggingScrollbar = 0;
                scrollbarGrabOffset = leftScrollbar.grabOffset(mouseY);
                keyListScroll = leftScrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
                return true;
            }
            GuiLayoutHelper.Scrollbar rightScrollbar = currentScrollbar(1);
            if (rightScrollbar.contains(mouseX, mouseY)) {
                draggingScrollbar = 1;
                scrollbarGrabOffset = rightScrollbar.grabOffset(mouseY);
                presetScroll = rightScrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
                return true;
            }
        }
        if (inBounds((int) mouseX, (int) mouseY, leftX, listY, leftW, listH)) {
            int index = (int) ((mouseY - listY) / ROW_H) + keyListScroll;
            if (index >= 0 && index < keyList.size()) {
                selectedKeyIdx = index;
                return true;
            }
        }
        if (inBounds((int) mouseX, (int) mouseY, rightX, presetY, rightW, presetH)) {
            int index = (int) ((mouseY - presetY) / ROW_H) + presetScroll;
            if (index >= 0 && index < NbtIgnorePresetManager.getAll().size()) {
                selectedPresetIdx = index;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private GuiLayoutHelper.Scrollbar currentScrollbar(int side) {
        if (side == 0) {
            return GuiLayoutHelper.scrollbar(new GuiLayoutHelper.Bounds(
                            leftX + leftW - 3, listY, 3, listH),
                    keyList.size(), keyVisible, keyListScroll, 8);
        }
        return GuiLayoutHelper.scrollbar(new GuiLayoutHelper.Bounds(
                        rightX + rightW - 3, presetY, 3, presetH),
                NbtIgnorePresetManager.getAll().size(), presetVisible, presetScroll, 8);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (draggingScrollbar >= 0 && button == 0) {
            GuiLayoutHelper.Scrollbar scrollbar = currentScrollbar(draggingScrollbar);
            if (draggingScrollbar == 0) keyListScroll = scrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
            else presetScroll = scrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar >= 0) {
            draggingScrollbar = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inBounds((int) mouseX, (int) mouseY, leftX, listY, leftW, listH)) {
            keyListScroll = clamp(keyListScroll - (int) scrollY, 0,
                    Math.max(0, keyList.size() - keyVisible));
            return true;
        }
        if (inBounds((int) mouseX, (int) mouseY, rightX, presetY, rightW, presetH)) {
            presetScroll = clamp(presetScroll - (int) scrollY, 0,
                    Math.max(0, NbtIgnorePresetManager.getAll().size() - presetVisible));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showPresetNameInput && presetNameBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { doSavePreset(); return true; }
            if (keyCode == 256) { showPresetNameInput = false; return true; }
            return presetNameBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyInputBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) { doAddKey(); return true; }
            return keyInputBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 264 && selectedKeyIdx < keyList.size() - 1) { selectedKeyIdx++; return true; }
        if (keyCode == 265 && selectedKeyIdx > 0) { selectedKeyIdx--; return true; }
        if (keyCode == 261) { doRemoveSelected(); return true; }
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (showPresetNameInput && presetNameBox.isFocused()) return presetNameBox.charTyped(codePoint, modifiers);
        if (keyInputBox.isFocused()) return keyInputBox.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean inBounds(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private record ResourceLocationKey(ResourceLocation value) {
        String namespace() {
            return value == null ? "" : value.getNamespace();
        }
    }
}
