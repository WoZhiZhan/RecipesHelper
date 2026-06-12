package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.network.BlacklistClientHelper;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 黑名单添加面板：从“全部已加载配方”中搜索、多选并批量加入黑名单。
 * 配方来源为客户端的 RecipeManager（已由服务端同步所有配方，含代码注册的配方）。
 */
@OnlyIn(Dist.CLIENT)
public class BlacklistAddScreen extends Screen {
    private final Screen parent;
    private final Runnable onApplied;

    private List<ResourceLocation> allRecipes = new ArrayList<>();
    private List<ResourceLocation> filteredRecipes = new ArrayList<>();
    private final Set<ResourceLocation> selected = new LinkedHashSet<>();

    private EditBox searchBox;
    private PinyinSearchHelper<ResourceLocation> searchHelper;
    private Button addButton;

    private int scrollOffset = 0;
    private int lastClickedIndex = -1; // 用于 Shift 区间选择
    private final int itemHeight = 18;
    private static final int LIST_TOP = 70;
    private static final int LIST_BOTTOM_MARGIN = 60;
    private int visibleItems = 15; // 每帧动态计算
    private String loadError = null;

    public BlacklistAddScreen(Screen parent, Runnable onApplied) {
        super(Component.literal("添加配方到黑名单"));
        this.parent = parent;
        this.onApplied = onApplied;
        this.searchHelper = new PinyinSearchHelper<>(
                rl -> rl.getPath().replace('_', ' ').replace('/', ' '),
                ResourceLocation::toString
        );
        loadAllRecipes();
    }

    private void loadAllRecipes() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                loadError = "游戏世界未加载";
                return;
            }
            RecipeManager rm = mc.level.getRecipeManager();
            Set<ResourceLocation> ids = new LinkedHashSet<>();
            for (Recipe<?> recipe : rm.getRecipes()) {
                ResourceLocation id = recipe.getId();
                if (id != null) {
                    ids.add(id);
                }
            }
            // 过滤掉已经在黑名单里的，添加面板只显示“还能加”的
            Set<ResourceLocation> blacklisted = RecipeBlacklistManager.getBlacklistedRecipes();
            allRecipes = new ArrayList<>();
            for (ResourceLocation id : ids) {
                if (!blacklisted.contains(id)) {
                    allRecipes.add(id);
                }
            }
            allRecipes.sort(Comparator.comparing(ResourceLocation::toString));
            filteredRecipes = new ArrayList<>(allRecipes);
            searchHelper.buildCache(allRecipes);
        } catch (Exception e) {
            loadError = "加载配方失败: " + e.getMessage();
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int topY = 40;

        searchBox = new EditBox(this.font, centerX - 200, topY, 400, 20, Component.literal("搜索配方"));
        searchBox.setHint(Component.literal("搜索配方ID、命名空间或拼音..."));
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        int buttonY = this.height - 40;

        addButton = addRenderableWidget(Button.builder(
                        Component.literal("添加选中"),
                        b -> applyAdd())
                .bounds(centerX - 185, buttonY, 80, 20)
                .build());
        addButton.active = false;

        addRenderableWidget(Button.builder(
                        Component.literal("全选当前"),
                        b -> selectAllFiltered())
                .bounds(centerX - 100, buttonY, 70, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal("清空选择"),
                        b -> {
                            selected.clear();
                            addButton.active = false;
                        })
                .bounds(centerX - 25, buttonY, 70, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal("返回"),
                        b -> minecraft.setScreen(parent))
                .bounds(centerX + 50, buttonY, 60, 20)
                .build());
    }

    private void onSearchChanged(String searchText) {
        filteredRecipes.clear();
        scrollOffset = 0;
        lastClickedIndex = -1;

        String lower = searchText.toLowerCase();
        if (lower.isEmpty()) {
            filteredRecipes.addAll(allRecipes);
        } else {
            for (ResourceLocation r : allRecipes) {
                if (r.toString().toLowerCase().contains(lower) ||
                        r.getNamespace().toLowerCase().contains(lower) ||
                        r.getPath().toLowerCase().contains(lower) ||
                        searchHelper.matches(r, searchText)) {
                    filteredRecipes.add(r);
                }
            }
        }
    }

    private void selectAllFiltered() {
        selected.addAll(filteredRecipes);
        addButton.active = !selected.isEmpty();
    }

    private void applyAdd() {
        if (selected.isEmpty()) {
            return;
        }
        BlacklistClientHelper.addMultipleToBlacklist(new ArrayList<>(selected));

        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§e正在批量添加 " + selected.size() + " 个配方到黑名单..."));
        }

        // 单人游戏立即生效，刷新父界面数据
        if (!BlacklistClientHelper.isRemoteServer() && onApplied != null) {
            onApplied.run();
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int centerX = this.width / 2;
        int listTop = LIST_TOP;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;

        // 动态计算可见行数（适配不同 GUI 缩放）
        this.visibleItems = Math.max(1, (listBottom - listTop - 10) / itemHeight);

        g.fill(0, 0, this.width, this.height, 0x80000000);

        int px = centerX - 252, py = 5, pw = 504, ph = this.height - 10;
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF0A0A0A);
        g.fill(px, py, px + pw, py + ph, 0xFF252525);

        // 标题栏（绿色，区别于移除界面的红色）
        g.fill(px, py, px + pw, py + 26, 0xFF1A5A1A);
        g.fill(px, py, px + pw, py + 1, 0xFF4ACF4A);
        g.fill(px, py + 25, px + pw, py + 26, 0xFF228022);
        g.drawCenteredString(this.font, "§a添加配方到黑名单（可多选）", centerX, py + 9, 0xFFFFFF);

        String info = String.format("§7已选: §e%d §7个  |  当前显示: §e%d §7个  |  可添加: §e%d §7个",
                selected.size(), filteredRecipes.size(), allRecipes.size());
        g.drawCenteredString(this.font, info, centerX, 33, 0xAAAAAA);

        g.fill(centerX - 251, listTop - 1, centerX + 251, listBottom + 1, 0xFF0A0A0A);
        g.fill(centerX - 250, listTop, centerX + 250, listBottom, 0xFF1A1A1A);

        renderList(g, mouseX, mouseY, centerX - 240, listTop + 5, 480, listBottom - listTop - 10);

        if (filteredRecipes.size() > visibleItems) {
            renderScrollbar(g, centerX + 240, listTop + 5, listBottom - listTop - 10);
        }

        // 操作提示
        g.drawString(this.font, "§7单击切换选择 · Shift 区间选择 · Ctrl+A 全选",
                centerX - 250, listBottom + 4, 0x888888, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        if (loadError != null) {
            g.drawCenteredString(this.font, "§c" + loadError, x + width / 2, y + height / 2, 0xFF5555);
            return;
        }

        int maxScroll = Math.max(0, filteredRecipes.size() - visibleItems);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        for (int i = 0; i < Math.min(visibleItems, filteredRecipes.size()); i++) {
            int index = i + scrollOffset;
            if (index >= filteredRecipes.size()) break;

            ResourceLocation recipe = filteredRecipes.get(index);
            int itemY = y + i * itemHeight;

            boolean isSelected = selected.contains(recipe);
            boolean isHovered = mouseX >= x && mouseX < x + width &&
                    mouseY >= itemY && mouseY < itemY + itemHeight;

            if (isSelected) {
                g.fill(x, itemY, x + width, itemY + itemHeight, 0xFF2E6E3C);
            } else if (isHovered) {
                g.fill(x, itemY, x + width, itemY + itemHeight, 0xFF3C3C3C);
            }

            // 复选框
            int boxX = x + 4, boxY = itemY + 4, boxSize = 10;
            g.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xFF111111);
            g.fill(boxX, boxY, boxX + boxSize, boxY + 1, 0xFF888888);
            g.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, 0xFF888888);
            g.fill(boxX, boxY, boxX + 1, boxY + boxSize, 0xFF888888);
            g.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, 0xFF888888);
            if (isSelected) {
                g.fill(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2, 0xFF55FF55);
            }

            String text = recipe.toString();
            if (text.length() > 66) {
                text = text.substring(0, 63) + "...";
            }
            g.drawString(this.font, text, x + 20, itemY + 5, getNamespaceColor(recipe.getNamespace()), false);
        }

        if (filteredRecipes.isEmpty() && loadError == null) {
            g.drawCenteredString(this.font, "没有匹配的配方", x + width / 2, y + height / 2, 0xAAAAAA);
        }
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int height) {
        g.fill(x, y, x + 6, y + height, 0xFF1E1E1E);
        int maxScroll = filteredRecipes.size() - visibleItems;
        int thumbHeight = Math.max(10, height * visibleItems / filteredRecipes.size());
        int thumbY = y + (maxScroll == 0 ? 0 : (height - thumbHeight) * scrollOffset / maxScroll);
        g.fill(x + 1, thumbY, x + 5, thumbY + thumbHeight, 0xFF8B8B8B);
    }

    private int getNamespaceColor(String namespace) {
        return switch (namespace) {
            case "minecraft" -> 0xFF55FF55;
            case "registerhelper" -> 0xFFFF5555;
            case "avaritia" -> 0xFF5555FF;
            default -> 0xFFFFAA00;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int listTop = LIST_TOP;
        int listX = centerX - 240;
        int listWidth = 480;
        int listBottom = this.height - LIST_BOTTOM_MARGIN;

        if (mouseX >= listX && mouseX < listX + listWidth &&
                mouseY >= listTop + 5 && mouseY < listBottom - 5) {

            int row = (int) ((mouseY - listTop - 5) / itemHeight);
            if (row < 0 || row >= visibleItems) {
                return true;
            }
            int clickedIndex = row + scrollOffset;
            if (clickedIndex >= 0 && clickedIndex < filteredRecipes.size()) {
                boolean shift = hasShiftDown();
                if (shift && lastClickedIndex >= 0 && lastClickedIndex < filteredRecipes.size()) {
                    int from = Math.min(lastClickedIndex, clickedIndex);
                    int to = Math.max(lastClickedIndex, clickedIndex);
                    for (int i = from; i <= to; i++) {
                        selected.add(filteredRecipes.get(i));
                    }
                } else {
                    ResourceLocation recipe = filteredRecipes.get(clickedIndex);
                    if (selected.contains(recipe)) {
                        selected.remove(recipe);
                    } else {
                        selected.add(recipe);
                    }
                    lastClickedIndex = clickedIndex;
                }
                addButton.active = !selected.isEmpty();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (filteredRecipes.size() > visibleItems) {
            scrollOffset -= (int) (delta * 3);
            int maxScroll = filteredRecipes.size() - visibleItems;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            minecraft.setScreen(parent);
            return true;
        }
        // Ctrl+A 全选当前过滤结果
        if (keyCode == 65 && hasControlDown()) {
            selectAllFiltered();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
