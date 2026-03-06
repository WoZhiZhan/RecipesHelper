package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.init.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod 配置界面
 */
@OnlyIn(Dist.CLIENT)
public class ConfigScreen extends Screen {

    private final Screen parent;

    // 本地暂存（点"保存"后写入 config）
    private boolean perSlotNBT;
    private boolean defaultIncludeNBT;
    private boolean debugLogging;

    // 按钮引用，用于实时刷新文字
    private final List<Button> toggleButtons = new ArrayList<>();

    // 配置项元数据
    private static final String[] LABELS = {
        "Per-slot NBT 控制",
        "新槽位默认包含 NBT",
        "调试日志"
    };
    private static final String[] DESCS = {
        "§8材料槽底部显示颜色条，中键独立切换每槽NBT匹配",
        "§8从背包选物品时，是否默认启用NBT匹配",
        "§8在日志中输出NBT匹配详细信息"
    };

    private static final int PANEL_W   = 300;
    private static final int ROW_H     = 30;
    private static final int TITLE_H   = 36;
    private static final int FOOT_H    = 44;
    private static final int ROWS      = 3;

    public ConfigScreen(Screen parent) {
        super(Component.literal("RegisterHelper 配置"));
        this.parent = parent;
    }

    private int panelHeight() { return TITLE_H + ROW_H * ROWS + FOOT_H; }
    private int panelX()      { return (this.width  - PANEL_W) / 2; }
    private int panelY()      { return (this.height - panelHeight()) / 2; }

    @Override
    protected void init() {
        perSlotNBT        = ModConfig.isPerSlotNBTEnabled();
        defaultIncludeNBT = ModConfig.getDefaultIncludeNBT();
        debugLogging      = ModConfig.isDebugLoggingEnabled();
        toggleButtons.clear();

        int cx   = panelX();
        int rowY = panelY() + TITLE_H;

        // 三行开关
        for (int i = 0; i < ROWS; i++) {
            final int idx = i;
            Button btn = addRenderableWidget(Button.builder(
                    Component.literal(toggleLabel(getVal(idx))),
                    b -> {
                        setVal(idx, !getVal(idx));
                        b.setMessage(Component.literal(toggleLabel(getVal(idx))));
                    })
                    .bounds(cx + PANEL_W - 100, rowY + (ROW_H - 18) / 2, 88, 18)
                    .build());
            toggleButtons.add(btn);
            rowY += ROW_H;
        }

        int footY = panelY() + panelHeight() - FOOT_H + (FOOT_H - 20) / 2;
        addRenderableWidget(Button.builder(Component.literal("§a✔ 保存"), b -> saveAndClose())
                .bounds(cx + PANEL_W / 2 - 96, footY, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§c✖ 取消"), b -> onClose())
                .bounds(cx + PANEL_W / 2 + 8,  footY, 88, 20).build());
    }

    private boolean getVal(int idx) {
        return switch (idx) {
            case 0 -> perSlotNBT;
            case 1 -> defaultIncludeNBT;
            case 2 -> debugLogging;
            default -> false;
        };
    }
    private void setVal(int idx, boolean v) {
        switch (idx) {
            case 0 -> perSlotNBT        = v;
            case 1 -> defaultIncludeNBT = v;
            case 2 -> debugLogging      = v;
        }
    }
    private static String toggleLabel(boolean on) {
        return on ? "§a● 开启" : "§c● 关闭";
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);

        int cx = panelX(), cy = panelY();
        int ph = panelHeight();

        // 外框 + 背景
        g.fill(cx - 1, cy - 1, cx + PANEL_W + 1, cy + ph + 1, 0xFF080808);
        g.fill(cx,     cy,     cx + PANEL_W,     cy + ph,      0xFF1E1E1E);

        // 标题栏
        g.fill(cx, cy, cx + PANEL_W, cy + TITLE_H, 0xFF252538);
        g.fill(cx, cy + TITLE_H - 1, cx + PANEL_W, cy + TITLE_H, 0xFF3A3A55);
        g.drawCenteredString(this.font, "§b⚙  RegisterHelper  配置",
                cx + PANEL_W / 2, cy + (TITLE_H - 8) / 2, 0xFFFFFF);

        // 行背景 + 文字
        int rowY = cy + TITLE_H;
        for (int i = 0; i < ROWS; i++) {
            int bg = (i % 2 == 0) ? 0xFF202020 : 0xFF242424;
            g.fill(cx, rowY, cx + PANEL_W, rowY + ROW_H, bg);
            g.fill(cx, rowY + ROW_H - 1, cx + PANEL_W, rowY + ROW_H, 0xFF333333);

            // 左侧竖色条：开=蓝, 关=红
            int barColor = getVal(i) ? 0xFF3355AA : 0xFF882222;
            g.fill(cx, rowY, cx + 3, rowY + ROW_H - 1, barColor);

            // 标签 + 说明
            g.drawString(this.font, LABELS[i], cx + 10, rowY + 5, 0xEEEEEE, false);
            g.drawString(this.font, DESCS[i],  cx + 10, rowY + 16, 0xFFFFFF, false);

            rowY += ROW_H;
        }

        // 底栏
        g.fill(cx, rowY, cx + PANEL_W, rowY + FOOT_H, 0xFF1A1A2A);
        g.fill(cx, rowY, cx + PANEL_W, rowY + 1, 0xFF3A3A55);

        super.render(g, mouseX, mouseY, partial);
    }

    private void saveAndClose() {
        ModConfig.COMMON.enablePerSlotNBT.set(perSlotNBT);
        ModConfig.COMMON.defaultIncludeNBT.set(defaultIncludeNBT);
        ModConfig.COMMON.enableDebugLogging.set(debugLogging);
        ModConfig.COMMON_SPEC.save();
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
