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
import java.util.Arrays;
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
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private int lastClickedIndex = -1; // 用于 Shift 区间选择
    private static final int ITEM_HEIGHT = 18;
    private static final int HEADER_HEIGHT = 64;
    private static final int FOOTER_HEIGHT = 54;
    private static final int PANEL_PREFERRED_WIDTH = 520;
    private static final int PANEL_MIN_WIDTH = 340;
    private static final int PANEL_MARGIN = 8;
    private int visibleItems = 1;
    private GuiLayoutHelper.Bounds panelBounds;
    private GuiLayoutHelper.Bounds listBounds;
    private GuiLayoutHelper.Bounds footerBounds;
    private String loadError = null;

    public BlacklistAddScreen(Screen parent, Runnable onApplied) {
        super(GuiText.component("registerhelper.gui.blacklist_add.title"));
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
                loadError = GuiText.string("registerhelper.message.world_not_loaded");
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
            loadError = GuiText.string("registerhelper.message.recipe.load_failed", e.getMessage());
        }
    }

    @Override
    protected void init() {
        String currentSearch = searchBox != null ? searchBox.getValue() : "";
        int panelWidth = GuiLayoutHelper.fit(PANEL_PREFERRED_WIDTH, PANEL_MIN_WIDTH,
                this.width - PANEL_MARGIN * 2);
        int panelHeight = Math.max(1, this.height - PANEL_MARGIN * 2);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        panelBounds = new GuiLayoutHelper.Bounds(panelX, panelY, panelWidth, panelHeight);
        listBounds = new GuiLayoutHelper.Bounds(panelX + 10, panelY + HEADER_HEIGHT,
                panelWidth - 20,
                Math.max(12, panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT));
        footerBounds = new GuiLayoutHelper.Bounds(panelX + 1,
                panelBounds.bottom() - FOOTER_HEIGHT, panelWidth - 2, FOOTER_HEIGHT);
        visibleItems = Math.max(1, (listBounds.height() - 10) / ITEM_HEIGHT);

        searchBox = new EditBox(this.font, listBounds.x(), panelY + 36,
                listBounds.width(), 20, GuiText.component("registerhelper.gui.blacklist_add.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setHint(GuiText.component("registerhelper.gui.blacklist_add.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        int[] buttonWidths = {font.width(GuiText.string("registerhelper.gui.blacklist_add.add_selected")) + 14,
                font.width(GuiText.string("registerhelper.gui.blacklist_add.select_all")) + 14,
                font.width(GuiText.string("registerhelper.gui.blacklist_add.clear_selection")) + 14,
                font.width(GuiText.string("registerhelper.gui.common.back")) + 14};
        int buttonGap = 5;
        int totalWidth = Arrays.stream(buttonWidths).sum() + buttonGap * (buttonWidths.length - 1);
        if (totalWidth > footerBounds.width() - 12) {
            int compactWidth = Math.max(40,
                    (footerBounds.width() - 12 - buttonGap * (buttonWidths.length - 1))
                            / buttonWidths.length);
            Arrays.fill(buttonWidths, compactWidth);
            totalWidth = compactWidth * buttonWidths.length + buttonGap * (buttonWidths.length - 1);
        }
        int currentX = footerBounds.x() + Math.max(6, (footerBounds.width() - totalWidth) / 2);
        int buttonY = footerBounds.bottom() - 27;

        addButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.blacklist_add.add_selected"),
                        b -> applyAdd())
                .bounds(currentX, buttonY, buttonWidths[0], 20)
                .build());
        addButton.active = !selected.isEmpty();
        currentX += buttonWidths[0] + buttonGap;

        addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.blacklist_add.select_all"),
                        b -> selectAllFiltered())
                .bounds(currentX, buttonY, buttonWidths[1], 20)
                .build());
        currentX += buttonWidths[1] + buttonGap;

        addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.blacklist_add.clear_selection"),
                        b -> {
                            selected.clear();
                            addButton.active = false;
                        })
                .bounds(currentX, buttonY, buttonWidths[2], 20)
                .build());
        currentX += buttonWidths[2] + buttonGap;

        addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.back"),
                        b -> minecraft.setScreen(parent))
                .bounds(currentX, buttonY, buttonWidths[3], 20)
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
            minecraft.player.sendSystemMessage(GuiText.component(
                    "registerhelper.message.blacklist.adding_many", selected.size()));
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
        GuiTheme.drawBackdrop(g, this.width, this.height);
        int centerX = panelBounds.centerX();
        int listTop = listBounds.y();
        int listBottom = listBounds.bottom();

        int px = panelBounds.x(), py = panelBounds.y();
        int pw = panelBounds.width(), ph = panelBounds.height();
        GuiTheme.drawPanel(g, panelBounds, 26, GuiTheme.SUCCESS);
        g.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.bottom(),
                GuiTheme.PANEL_ALT);
        g.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.y() + 1,
                GuiTheme.DIVIDER);

        // 标题栏（绿色，区别于移除界面的红色）
        g.drawCenteredString(this.font,
                GuiText.component("registerhelper.gui.blacklist_add.title_multi"), centerX, py + 9,
                GuiTheme.TEXT_ON_HEADER);

        String info = GuiText.string("registerhelper.gui.blacklist_add.stats",
                selected.size(), filteredRecipes.size(), allRecipes.size());
        info = GuiLayoutHelper.ellipsis(this.font, info, panelBounds.width() - 20);
        g.drawCenteredString(this.font, info, centerX, py + 28, GuiTheme.TEXT_MUTED);

        GuiTheme.drawSurface(g, listBounds, false);

        renderList(g, mouseX, mouseY, listBounds.x() + 5, listTop + 5,
                listBounds.width() - 10, listBounds.height() - 10);

        if (filteredRecipes.size() > visibleItems) {
            renderScrollbar(g, listBounds.right() - 8, listTop + 5, listBounds.height() - 10);
        }

        // 操作提示
        g.drawString(this.font, GuiText.string("registerhelper.gui.blacklist_add.selection_help"),
                footerBounds.x() + 8, footerBounds.y() + 4, GuiTheme.TEXT_MUTED, false);

        GuiTheme.drawInput(g, searchBox);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        if (loadError != null) {
            g.drawCenteredString(this.font, "§c" + loadError, x + width / 2, y + height / 2, GuiTheme.DANGER);
            return;
        }

        int maxScroll = Math.max(0, filteredRecipes.size() - visibleItems);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        for (int i = 0; i < Math.min(visibleItems, filteredRecipes.size()); i++) {
            int index = i + scrollOffset;
            if (index >= filteredRecipes.size()) break;

            ResourceLocation recipe = filteredRecipes.get(index);
            int itemY = y + i * ITEM_HEIGHT;

            boolean isSelected = selected.contains(recipe);
            boolean isHovered = mouseX >= x && mouseX < x + width &&
                    mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

            if (isSelected) {
                GuiTheme.drawRow(g, x, itemY, width, ITEM_HEIGHT, i, false, true);
            } else if (isHovered) {
                GuiTheme.drawRow(g, x, itemY, width, ITEM_HEIGHT, i, true, false);
            } else {
                GuiTheme.drawRow(g, x, itemY, width, ITEM_HEIGHT, i, false, false);
            }

            // 复选框
            int boxX = x + 4, boxY = itemY + 4, boxSize = 10;
            g.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, GuiTheme.SURFACE);
            g.fill(boxX, boxY, boxX + boxSize, boxY + 1, GuiTheme.INPUT_EDGE);
            g.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, GuiTheme.INPUT_EDGE);
            g.fill(boxX, boxY, boxX + 1, boxY + boxSize, GuiTheme.INPUT_EDGE);
            g.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, GuiTheme.INPUT_EDGE);
            if (isSelected) {
                g.fill(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2, GuiTheme.SUCCESS);
            }

            String text = GuiLayoutHelper.ellipsis(
                    this.font, recipe.toString(), Math.max(1, width - 24));
            g.drawString(this.font, text, x + 20, itemY + 5, getNamespaceColor(recipe.getNamespace()), false);
        }

        if (filteredRecipes.isEmpty() && loadError == null) {
            g.drawCenteredString(this.font, GuiText.component("registerhelper.gui.common.no_match"),
                    x + width / 2, y + height / 2, GuiTheme.TEXT_MUTED);
        }
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int height) {
        GuiLayoutHelper.Scrollbar scrollbar = GuiLayoutHelper.scrollbar(
                new GuiLayoutHelper.Bounds(x, y, 6, height),
                filteredRecipes.size(), visibleItems, scrollOffset, 10);
        if (!scrollbar.visible()) return;
        GuiTheme.drawScrollbar(g, scrollbar, -1, -1);
    }

    private int getNamespaceColor(String namespace) {
        return GuiTheme.namespaceColor(namespace);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            GuiLayoutHelper.Scrollbar scrollbar = currentScrollbar();
            if (scrollbar.contains(mouseX, mouseY)) {
                draggingScrollbar = true;
                scrollbarGrabOffset = scrollbar.grabOffset(mouseY);
                scrollOffset = scrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
                return true;
            }
        }
        int listTop = listBounds.y();
        int listX = listBounds.x() + 5;
        int listWidth = listBounds.width() - 10;
        int listBottom = listBounds.bottom();

        if (mouseX >= listX && mouseX < listX + listWidth &&
                mouseY >= listTop + 5 && mouseY < listBottom - 5) {

            int row = (int) ((mouseY - listTop - 5) / ITEM_HEIGHT);
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

    private GuiLayoutHelper.Scrollbar currentScrollbar() {
        return GuiLayoutHelper.scrollbar(
                new GuiLayoutHelper.Bounds(listBounds.right() - 8, listBounds.y() + 5,
                        6, Math.max(1, listBounds.height() - 10)),
                filteredRecipes.size(), visibleItems, scrollOffset, 10);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            scrollOffset = currentScrollbar().offsetForPointer(mouseY, scrollbarGrabOffset);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasDragging = draggingScrollbar;
        if (wasDragging) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
