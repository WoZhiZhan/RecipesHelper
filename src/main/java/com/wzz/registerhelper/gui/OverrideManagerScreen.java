package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class OverrideManagerScreen extends Screen {
    private final Screen parent;
    private List<ResourceLocation> allOverrideRecipes;
    private List<ResourceLocation> filteredRecipes;
    private EditBox searchBox;
    private final PinyinSearchHelper<ResourceLocation> searchHelper;
    private Button removeButton;
    private Button clearAllButton;
    private Button reloadButton;
    private Button closeButton;
    private int selectedIndex = -1;
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
    private UnifiedRecipeOverrideManager.OverrideStats stats;

    public OverrideManagerScreen(Screen parent) {
        super(GuiText.component("registerhelper.gui.override.title"));
        this.parent = parent;
        searchHelper = new PinyinSearchHelper<>(
                rl -> rl.getPath().replace('_', ' ').replace('/', ' '),
                ResourceLocation::toString);
        refreshData();
    }

    private void refreshData() {
        allOverrideRecipes = new ArrayList<>(UnifiedRecipeOverrideManager.getOverriddenRecipeIds());
        allOverrideRecipes.sort((a, b) -> a.toString().compareTo(b.toString()));
        filteredRecipes = new ArrayList<>(allOverrideRecipes);
        stats = UnifiedRecipeOverrideManager.getStats();
        selectedIndex = -1;
        searchHelper.buildCache(allOverrideRecipes);
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
        sidebarBounds = new GuiLayoutHelper.Bounds(listBounds.right() + CONTENT_GAP,
                listTop, SIDEBAR_WIDTH, listHeight);
        footerBounds = new GuiLayoutHelper.Bounds(panelX + 1,
                panelBounds.bottom() - FOOTER_HEIGHT, panelWidth - 2, FOOTER_HEIGHT);
        visibleItems = Math.max(1, (listHeight - 10) / ITEM_HEIGHT);

        searchBox = new EditBox(this.font, listBounds.x(), panelY + 36,
                listBounds.width(), 20, GuiText.component("registerhelper.gui.override.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setHint(GuiText.component("registerhelper.gui.override.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        String[] keys = {"registerhelper.gui.override.remove", "registerhelper.gui.override.clear",
                "registerhelper.gui.override.reload", "registerhelper.gui.common.close"};
        int[] widths = Arrays.stream(keys).mapToInt(key -> font.width(GuiText.string(key)) + 14).toArray();
        int gap = 5;
        int totalWidth = Arrays.stream(widths).sum() + gap * 3;
        if (totalWidth > footerBounds.width() - 12) {
            int compact = Math.max(40, (footerBounds.width() - 12 - gap * 3) / 4);
            Arrays.fill(widths, compact);
            totalWidth = compact * 4 + gap * 3;
        }
        int x = footerBounds.x() + Math.max(6, (footerBounds.width() - totalWidth) / 2);
        int y = footerBounds.y() + Math.max(0, (footerBounds.height() - 20) / 2);
        removeButton = addRenderableWidget(Button.builder(GuiText.component(keys[0]), b -> removeSelectedOverride())
                .bounds(x, y, widths[0], 20).build());
        removeButton.active = selectedIndex >= 0 && selectedIndex < filteredRecipes.size();
        x += widths[0] + gap;
        clearAllButton = addRenderableWidget(Button.builder(GuiText.component(keys[1]), b -> clearAllOverrides())
                .bounds(x, y, widths[1], 20).build());
        x += widths[1] + gap;
        reloadButton = addRenderableWidget(Button.builder(GuiText.component(keys[2]), b -> reloadRecipes())
                .bounds(x, y, widths[2], 20).build());
        x += widths[2] + gap;
        closeButton = addRenderableWidget(Button.builder(GuiText.component(keys[3]),
                        b -> minecraft.setScreen(parent))
                .bounds(x, y, widths[3], 20).build());
    }

    private void onSearchChanged(String searchText) {
        filteredRecipes.clear();
        selectedIndex = -1;
        if (removeButton != null) removeButton.active = false;
        scrollOffset = 0;
        String lower = searchText.toLowerCase();
        if (lower.isEmpty()) filteredRecipes.addAll(allOverrideRecipes);
        else for (ResourceLocation recipe : allOverrideRecipes) {
            if (recipe.toString().toLowerCase().contains(lower)
                    || recipe.getNamespace().toLowerCase().contains(lower)
                    || recipe.getPath().toLowerCase().contains(lower)
                    || searchHelper.matches(recipe, searchText)) filteredRecipes.add(recipe);
        }
    }

    private void removeSelectedOverride() {
        if (selectedIndex < 0 || selectedIndex >= filteredRecipes.size()) return;
        ResourceLocation id = filteredRecipes.get(selectedIndex);
        if (UnifiedRecipeOverrideManager.removeOverride(id)) {
            refreshData();
            if (minecraft.player != null) minecraft.player.sendSystemMessage(
                    GuiText.component("registerhelper.message.override.removed", id));
        } else if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(
                    GuiText.component("registerhelper.message.override.remove_failed", id));
        }
    }

    private void clearAllOverrides() {
        if (allOverrideRecipes.isEmpty()) {
            if (minecraft.player != null) minecraft.player.sendSystemMessage(
                    GuiText.component("registerhelper.message.override.already_empty"));
            return;
        }
        if (UnifiedRecipeOverrideManager.clearAllOverrides()) {
            refreshData();
            if (minecraft.player != null) minecraft.player.sendSystemMessage(
                    GuiText.component("registerhelper.message.override.cleared"));
        } else if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(
                    GuiText.component("registerhelper.message.override.clear_failed"));
        }
    }

    private void reloadRecipes() {
        if (minecraft.player != null) minecraft.player.sendSystemMessage(
                GuiText.component("registerhelper.message.override.reload_hint"));
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.drawBackdrop(g, width, height);
        GuiTheme.drawPanel(g, panelBounds, 24, GuiTheme.INFO);
        g.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.bottom(), GuiTheme.PANEL_ALT);
        g.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.y() + 1, GuiTheme.DIVIDER);
        g.drawCenteredString(this.font, GuiText.component("registerhelper.gui.override.title"),
                panelBounds.centerX(), panelBounds.y() + 8, GuiTheme.TEXT_ON_HEADER);
        String statsText = GuiText.string("registerhelper.gui.override.stats",
                stats.totalOverrides, filteredRecipes.size());
        g.drawCenteredString(this.font,
                GuiLayoutHelper.ellipsis(this.font, statsText, panelBounds.width() - 20),
                panelBounds.centerX(), panelBounds.y() + 26, GuiTheme.TEXT_MUTED);
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
            boolean hovered = mouseX >= x && mouseX < x + listWidth
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            GuiTheme.drawRow(g, x, itemY, listWidth, ITEM_HEIGHT, i, hovered, index == selectedIndex);
            String text = GuiLayoutHelper.ellipsis(this.font,
                    recipe.toString(), Math.max(1, listWidth - 10));
            g.drawString(this.font, text, x + 5, itemY + 5,
                    GuiTheme.namespaceColor(recipe.getNamespace()), false);
        }
        if (filteredRecipes.isEmpty()) {
            g.drawCenteredString(this.font, GuiText.component(allOverrideRecipes.isEmpty()
                            ? "registerhelper.gui.override.empty" : "registerhelper.gui.common.no_match"),
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
            int clickedIndex = row + scrollOffset;
            if (row >= 0 && row < visibleItems
                    && clickedIndex >= 0 && clickedIndex < filteredRecipes.size()) {
                selectedIndex = clickedIndex;
                removeButton.active = true;
                if (button == 0) {
                    removeSelectedOverride();
                    return true;
                }
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
            scrollOffset = GuiLayoutHelper.clamp(scrollOffset - (int) (scrollY * 3),
                    0, filteredRecipes.size() - visibleItems);
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
        if ((keyCode == 46 || keyCode == 261) && selectedIndex >= 0) {
            removeSelectedOverride();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
