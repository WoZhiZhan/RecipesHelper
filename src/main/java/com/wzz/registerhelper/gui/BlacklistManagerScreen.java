package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.network.BlacklistClientHelper;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class BlacklistManagerScreen extends Screen {
    private final Screen parent;
    private List<ResourceLocation> allBlacklistedRecipes;
    private List<ResourceLocation> filteredRecipes;
    private EditBox searchBox;
    private final PinyinSearchHelper<ResourceLocation> searchHelper;
    private Button removeButton;
    private Button addButton;
    private Button clearAllButton;
    private Button closeButton;
    private final Set<ResourceLocation> selected = new LinkedHashSet<>();
    private int lastClickedIndex = -1;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private static final int ITEM_HEIGHT = 18;
    private static final int HEADER_HEIGHT = 64;
    private static final int FOOTER_HEIGHT = 34;
    private static final int SIDEBAR_WIDTH = 150;
    private static final int PANEL_PREFERRED_WIDTH = 720;
    private static final int PANEL_MIN_WIDTH = 360;
    private static final int PANEL_MARGIN = 8;
    private static final int CONTENT_GAP = 10;
    private int visibleItems = 1;
    private GuiLayoutHelper.Bounds panelBounds;
    private GuiLayoutHelper.Bounds listBounds;
    private GuiLayoutHelper.Bounds sidebarBounds;
    private GuiLayoutHelper.Bounds footerBounds;
    private boolean sidebarVisible;
    private RecipeBlacklistManager.BlacklistStats stats;

    public BlacklistManagerScreen(Screen parent) {
        super(GuiText.component("registerhelper.gui.blacklist.title"));
        this.parent = parent;
        searchHelper = new PinyinSearchHelper<>(
                rl -> rl.getPath().replace('_', ' ').replace('/', ' '),
                ResourceLocation::toString);
        refreshData();
    }

    private void refreshData() {
        allBlacklistedRecipes = new ArrayList<>(RecipeBlacklistManager.getBlacklistedRecipes());
        allBlacklistedRecipes.sort(Comparator.comparing(ResourceLocation::toString));
        filteredRecipes = new ArrayList<>(allBlacklistedRecipes);
        stats = RecipeBlacklistManager.getStats();
        selected.clear();
        lastClickedIndex = -1;
        searchHelper.buildCache(allBlacklistedRecipes);
    }

    @Override
    protected void init() {
        String currentSearch = searchBox != null ? searchBox.getValue() : "";
        int panelWidth = GuiLayoutHelper.fit(PANEL_PREFERRED_WIDTH, PANEL_MIN_WIDTH,
                width - PANEL_MARGIN * 2);
        int panelHeight = Math.max(1, height - PANEL_MARGIN * 2);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        panelBounds = new GuiLayoutHelper.Bounds(panelX, panelY, panelWidth, panelHeight);
        sidebarVisible = panelWidth >= SIDEBAR_WIDTH + 520;
        int contentWidth = panelWidth - 20;
        int listWidth = sidebarVisible ? contentWidth - SIDEBAR_WIDTH - CONTENT_GAP : contentWidth;
        int listTop = panelY + HEADER_HEIGHT;
        int listHeight = Math.max(12, panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT - 8);
        listBounds = new GuiLayoutHelper.Bounds(panelX + 10, listTop, listWidth, listHeight);
        sidebarBounds = new GuiLayoutHelper.Bounds(
                listBounds.right() + CONTENT_GAP, listTop, SIDEBAR_WIDTH, listHeight);
        footerBounds = new GuiLayoutHelper.Bounds(panelX + 1,
                panelBounds.bottom() - FOOTER_HEIGHT, panelWidth - 2, FOOTER_HEIGHT);
        visibleItems = Math.max(1, (listHeight - 10) / ITEM_HEIGHT);

        searchBox = new EditBox(this.font, listBounds.x(), panelY + 36,
                listBounds.width(), 20, GuiText.component("registerhelper.gui.blacklist.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setHint(GuiText.component("registerhelper.gui.blacklist.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        String[] keys = {"registerhelper.gui.blacklist.add_recipe",
                "registerhelper.gui.blacklist.remove_selected",
                "registerhelper.gui.blacklist.clear", "registerhelper.gui.common.close"};
        int[] widths = Arrays.stream(keys).mapToInt(key -> font.width(GuiText.string(key)) + 14).toArray();
        int gap = 5;
        int totalWidth = Arrays.stream(widths).sum() + gap * 3;
        if (totalWidth > footerBounds.width() - 12) {
            int compact = Math.max(40, (footerBounds.width() - 12 - gap * 3) / 4);
            Arrays.fill(widths, compact);
            totalWidth = compact * 4 + gap * 3;
        }
        int currentX = footerBounds.x() + Math.max(6, (footerBounds.width() - totalWidth) / 2);
        int buttonY = footerBounds.y() + Math.max(0, (footerBounds.height() - 20) / 2);
        addButton = addRenderableWidget(Button.builder(GuiText.component(keys[0]), b -> openAddScreen())
                .bounds(currentX, buttonY, widths[0], 20).build());
        currentX += widths[0] + gap;
        removeButton = addRenderableWidget(Button.builder(GuiText.component(keys[1]), b -> removeSelectedRecipes())
                .bounds(currentX, buttonY, widths[1], 20).build());
        removeButton.active = !selected.isEmpty();
        currentX += widths[1] + gap;
        clearAllButton = addRenderableWidget(Button.builder(GuiText.component(keys[2]), b -> confirmClearAll())
                .bounds(currentX, buttonY, widths[2], 20).build());
        currentX += widths[2] + gap;
        closeButton = addRenderableWidget(Button.builder(GuiText.component(keys[3]),
                        b -> minecraft.setScreen(parent))
                .bounds(currentX, buttonY, widths[3], 20).build());
    }

    private void openAddScreen() {
        minecraft.setScreen(new BlacklistAddScreen(this, () -> {
            refreshData();
            if (searchBox != null) onSearchChanged(searchBox.getValue());
        }));
    }

    private void onSearchChanged(String searchText) {
        filteredRecipes.clear();
        lastClickedIndex = -1;
        scrollOffset = 0;
        String lowerSearch = searchText.toLowerCase();
        if (lowerSearch.isEmpty()) {
            filteredRecipes.addAll(allBlacklistedRecipes);
        } else {
            for (ResourceLocation recipe : allBlacklistedRecipes) {
                if (recipe.toString().toLowerCase().contains(lowerSearch)
                        || recipe.getNamespace().toLowerCase().contains(lowerSearch)
                        || recipe.getPath().toLowerCase().contains(lowerSearch)
                        || searchHelper.matches(recipe, searchText)) {
                    filteredRecipes.add(recipe);
                }
            }
        }
        selected.retainAll(allBlacklistedRecipes);
        if (removeButton != null) removeButton.active = !selected.isEmpty();
    }

    private void removeSelectedRecipes() {
        if (selected.isEmpty()) return;
        List<ResourceLocation> toRemove = new ArrayList<>(selected);
        if (toRemove.size() == 1) BlacklistClientHelper.removeFromBlacklist(toRemove.get(0));
        else BlacklistClientHelper.removeMultipleFromBlacklist(toRemove);
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(GuiText.component(
                    "registerhelper.message.blacklist.removing", toRemove.size()));
        }
        if (!BlacklistClientHelper.isRemoteServer()) {
            String search = searchBox != null ? searchBox.getValue() : "";
            refreshData();
            onSearchChanged(search);
        } else {
            selected.clear();
            removeButton.active = false;
        }
    }

    private void confirmClearAll() {
        if (allBlacklistedRecipes.isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(
                        GuiText.component("registerhelper.message.blacklist.already_empty"));
            }
            return;
        }
        minecraft.setScreen(new ConfirmClearAllScreen(
                this, allBlacklistedRecipes.size(), this::performClearAll));
    }

    private void performClearAll() {
        BlacklistClientHelper.clearBlacklist();
        if (!BlacklistClientHelper.isRemoteServer()) refreshData();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(GuiText.component("registerhelper.message.blacklist.clearing"));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        GuiTheme.drawBackdrop(g, width, height);
        GuiTheme.drawPanel(g, panelBounds, 26, GuiTheme.DANGER);
        g.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.bottom(), GuiTheme.PANEL_ALT);
        g.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.y() + 1, GuiTheme.DIVIDER);
        g.drawCenteredString(this.font, GuiText.component("registerhelper.gui.blacklist.title"),
                panelBounds.centerX(), panelBounds.y() + 9, GuiTheme.TEXT_ON_HEADER);
        String statsText = GuiText.string("registerhelper.gui.blacklist.stats",
                stats.totalBlacklisted, filteredRecipes.size(), selected.size());
        g.drawCenteredString(this.font,
                GuiLayoutHelper.ellipsis(this.font, statsText, panelBounds.width() - 20),
                panelBounds.centerX(), panelBounds.y() + 28, GuiTheme.TEXT_MUTED);
        GuiTheme.drawSurface(g, listBounds, false);
        renderRecipeList(g, mouseX, mouseY, listBounds.x() + 5, listBounds.y() + 5,
                listBounds.width() - 10, listBounds.height() - 10);
        if (filteredRecipes.size() > visibleItems) {
            GuiTheme.drawScrollbar(g, currentScrollbar(), mouseX, mouseY);
        }
        if (sidebarVisible) {
            GuiTheme.drawSurface(g, sidebarBounds, true);
            renderNamespaceStats(g, sidebarBounds.x() + 6, sidebarBounds.y() + 6);
        }
        GuiTheme.drawInput(g, searchBox);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderRecipeList(GuiGraphics g, int mouseX, int mouseY,
                                  int x, int y, int listWidth, int listHeight) {
        scrollOffset = GuiLayoutHelper.clamp(scrollOffset, 0,
                Math.max(0, filteredRecipes.size() - visibleItems));
        for (int i = 0; i < Math.min(visibleItems, filteredRecipes.size()); i++) {
            int index = i + scrollOffset;
            ResourceLocation recipe = filteredRecipes.get(index);
            int itemY = y + i * ITEM_HEIGHT;
            boolean selectedRow = selected.contains(recipe);
            boolean hovered = mouseX >= x && mouseX < x + listWidth
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            GuiTheme.drawRow(g, x, itemY, listWidth, ITEM_HEIGHT, i, hovered, selectedRow);
            int boxX = x + 4, boxY = itemY + 4, boxSize = 10;
            g.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, GuiTheme.SURFACE);
            g.fill(boxX, boxY, boxX + boxSize, boxY + 1, GuiTheme.INPUT_EDGE);
            g.fill(boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, GuiTheme.INPUT_EDGE);
            g.fill(boxX, boxY, boxX + 1, boxY + boxSize, GuiTheme.INPUT_EDGE);
            g.fill(boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, GuiTheme.INPUT_EDGE);
            if (selectedRow) {
                g.fill(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2, GuiTheme.DANGER);
            }
            String recipeText = GuiLayoutHelper.ellipsis(this.font,
                    recipe.toString(), Math.max(1, listWidth - 24));
            g.drawString(this.font, recipeText, x + 20, itemY + 5,
                    GuiTheme.namespaceColor(recipe.getNamespace()), false);
        }
        if (filteredRecipes.isEmpty()) {
            g.drawCenteredString(this.font, GuiText.component(allBlacklistedRecipes.isEmpty()
                            ? "registerhelper.gui.blacklist.empty" : "registerhelper.gui.common.no_match"),
                    x + listWidth / 2, y + listHeight / 2, GuiTheme.TEXT_MUTED);
        }
    }

    private void renderNamespaceStats(GuiGraphics g, int x, int y) {
        if (stats.byNamespace.isEmpty()) return;
        g.drawString(this.font, GuiText.string("registerhelper.gui.common.namespace_stats"),
                x, y, GuiTheme.TEXT, false);
        int lineY = y + 15;
        int maxLines = Math.max(1, (sidebarBounds.height() - 20) / 12);
        for (var entry : stats.byNamespace.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(maxLines).toList()) {
            String text = GuiLayoutHelper.ellipsis(this.font,
                    entry.getKey() + ": " + entry.getValue(), sidebarBounds.width() - 12);
            g.drawString(this.font, text, x, lineY,
                    GuiTheme.namespaceColor(entry.getKey()), false);
            lineY += 12;
        }
        if (stats.byNamespace.size() > maxLines) {
            g.drawString(this.font, GuiText.string("registerhelper.gui.common.more_count",
                    stats.byNamespace.size() - maxLines), x, lineY, GuiTheme.TEXT_MUTED, false);
        }
    }

    private GuiLayoutHelper.Scrollbar currentScrollbar() {
        return GuiLayoutHelper.scrollbar(new GuiLayoutHelper.Bounds(
                        listBounds.right() - 8, listBounds.y() + 5, 6,
                        Math.max(1, listBounds.height() - 10)),
                filteredRecipes.size(), visibleItems, scrollOffset, 10);
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
        int listX = listBounds.x() + 5;
        if (mouseX >= listX && mouseX < listX + listBounds.width() - 10
                && mouseY >= listBounds.y() + 5 && mouseY < listBounds.bottom() - 5) {
            int row = (int) ((mouseY - listBounds.y() - 5) / ITEM_HEIGHT);
            if (row < 0 || row >= visibleItems) return true;
            int clickedIndex = row + scrollOffset;
            if (clickedIndex >= 0 && clickedIndex < filteredRecipes.size()) {
                if (hasShiftDown() && lastClickedIndex >= 0
                        && lastClickedIndex < filteredRecipes.size()) {
                    int from = Math.min(lastClickedIndex, clickedIndex);
                    int to = Math.max(lastClickedIndex, clickedIndex);
                    for (int i = from; i <= to; i++) selected.add(filteredRecipes.get(i));
                } else {
                    ResourceLocation recipe = filteredRecipes.get(clickedIndex);
                    if (!selected.remove(recipe)) selected.add(recipe);
                    lastClickedIndex = clickedIndex;
                }
                removeButton.active = !selected.isEmpty();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (filteredRecipes.size() > visibleItems) {
            scrollOffset = GuiLayoutHelper.clamp(scrollOffset - (int) (scrollY * 3), 0,
                    filteredRecipes.size() - visibleItems);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        if (keyCode == 65 && hasControlDown()) {
            selected.addAll(filteredRecipes);
            removeButton.active = !selected.isEmpty();
            return true;
        }
        if ((keyCode == 46 || keyCode == 261) && !selected.isEmpty()) {
            removeSelectedRecipes();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class ConfirmClearAllScreen extends Screen {
        private final Screen parent;
        private final int recipeCount;
        private final Runnable onConfirm;
        private GuiLayoutHelper.Bounds dialogBounds;

        ConfirmClearAllScreen(Screen parent, int recipeCount, Runnable onConfirm) {
            super(GuiText.component("registerhelper.gui.blacklist.confirm.title"));
            this.parent = parent;
            this.recipeCount = recipeCount;
            this.onConfirm = onConfirm;
        }

        @Override
        protected void init() {
            dialogBounds = GuiLayoutHelper.centered(width, height, 300, 110, 220, 100, 8, 8);
            int buttonY = dialogBounds.bottom() - 40;
            int gap = 10;
            int buttonWidth = Math.min(80, Math.max(55,
                    (dialogBounds.width() - gap - 20) / 2));
            int startX = dialogBounds.centerX() - (buttonWidth * 2 + gap) / 2;
            addRenderableWidget(Button.builder(
                            GuiText.component("registerhelper.gui.blacklist.confirm.button"), button -> {
                                onConfirm.run();
                                minecraft.setScreen(parent);
                            }).bounds(startX, buttonY, buttonWidth, 20).build());
            addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.cancel"),
                            button -> minecraft.setScreen(parent))
                    .bounds(startX + buttonWidth + gap, buttonY, buttonWidth, 20).build());
        }

        @Override
        public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g, mouseX, mouseY, partialTick);
            GuiTheme.drawBackdrop(g, width, height);
            GuiTheme.drawPanel(g, dialogBounds, 0, GuiTheme.DANGER);
            int centerX = dialogBounds.centerX();
            int centerY = dialogBounds.centerY();
            g.drawCenteredString(this.font,
                    GuiText.component("registerhelper.gui.blacklist.confirm.heading"),
                    centerX, centerY - 35, GuiTheme.DANGER);
            g.drawCenteredString(this.font,
                    GuiText.component("registerhelper.gui.blacklist.confirm.remove_count", recipeCount),
                    centerX, centerY - 15, GuiTheme.TEXT);
            g.drawCenteredString(this.font,
                    GuiText.component("registerhelper.gui.blacklist.confirm.irreversible"),
                    centerX, centerY + 5, GuiTheme.WARNING);
            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
