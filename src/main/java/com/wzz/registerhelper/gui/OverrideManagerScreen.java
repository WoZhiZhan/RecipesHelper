package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import com.wzz.registerhelper.gui.component.CenteredEditBox;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class OverrideManagerScreen extends Screen {
    private final Screen parent;
    
    private List<ResourceLocation> allOverrideRecipes;
    private List<ResourceLocation> filteredRecipes;
    private EditBox searchBox;
    private PinyinSearchHelper<ResourceLocation> searchHelper;
    private Button removeButton;
    private Button clearAllButton;
    private Button reloadButton;
    private Button closeButton;
    
    private int selectedIndex = -1;
    private int scrollOffset = 0;
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
        this.searchHelper = new PinyinSearchHelper<>(
                rl -> rl.getPath().replace('_', ' ').replace('/', ' '),
                ResourceLocation::toString
        );
        refreshData();
    }
    
    private void refreshData() {
        Set<ResourceLocation> overrideRecipes = UnifiedRecipeOverrideManager.getOverriddenRecipeIds();
        this.allOverrideRecipes = new ArrayList<>(overrideRecipes);
        this.allOverrideRecipes.sort((a, b) -> a.toString().compareTo(b.toString()));
        this.filteredRecipes = new ArrayList<>(allOverrideRecipes);
        this.stats = UnifiedRecipeOverrideManager.getStats();
        this.selectedIndex = -1;
        if (searchHelper != null) {
            searchHelper.buildCache(allOverrideRecipes);
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

        sidebarVisible = panelWidth >= SIDEBAR_WIDTH + 520;
        int contentWidth = panelWidth - 20;
        int listWidth = sidebarVisible
                ? contentWidth - SIDEBAR_WIDTH - CONTENT_GAP
                : contentWidth;
        int listX = panelX + 10;
        int listTop = panelY + HEADER_HEIGHT;
        int listHeight = Math.max(12, panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT - 8);
        listBounds = new GuiLayoutHelper.Bounds(listX, listTop, listWidth, listHeight);
        sidebarBounds = new GuiLayoutHelper.Bounds(
                listX + listWidth + CONTENT_GAP, listTop, SIDEBAR_WIDTH, listHeight);
        footerBounds = new GuiLayoutHelper.Bounds(panelX + 1,
                panelBounds.bottom() - FOOTER_HEIGHT, panelWidth - 2, FOOTER_HEIGHT);
        visibleItems = Math.max(1, (listHeight - 10) / ITEM_HEIGHT);
        
        // 搜索框
        searchBox = new CenteredEditBox(this.font, listBounds.x(), panelY + 36,
                listBounds.width(), 20, GuiText.component("registerhelper.gui.override.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setHint(GuiText.component("registerhelper.gui.override.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);
        
        // 按钮区域
        int[] buttonWidths = {font.width(GuiText.string("registerhelper.gui.override.remove")) + 14,
                font.width(GuiText.string("registerhelper.gui.override.clear")) + 14,
                font.width(GuiText.string("registerhelper.gui.override.reload")) + 14,
                font.width(GuiText.string("registerhelper.gui.common.close")) + 14};
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
        int buttonY = footerBounds.y() + Math.max(0, (footerBounds.height() - 20) / 2);

        removeButton = addRenderableWidget(Button.builder(
                GuiText.component("registerhelper.gui.override.remove"),
                button -> removeSelectedOverride())
                .bounds(currentX, buttonY, buttonWidths[0], 20)
                .build());
        removeButton.active = selectedIndex >= 0 && selectedIndex < filteredRecipes.size();
        currentX += buttonWidths[0] + buttonGap;
        
        clearAllButton = addRenderableWidget(Button.builder(
                GuiText.component("registerhelper.gui.override.clear"),
                button -> clearAllOverrides())
                .bounds(currentX, buttonY, buttonWidths[1], 20)
                .build());
        currentX += buttonWidths[1] + buttonGap;
        
        reloadButton = addRenderableWidget(Button.builder(
                GuiText.component("registerhelper.gui.override.reload"),
                button -> reloadRecipes())
                .bounds(currentX, buttonY, buttonWidths[2], 20)
                .build());
        currentX += buttonWidths[2] + buttonGap;
        
        closeButton = addRenderableWidget(Button.builder(
                GuiText.component("registerhelper.gui.common.close"),
                button -> minecraft.setScreen(parent))
                .bounds(currentX, buttonY, buttonWidths[3], 20)
                .build());
    }
    
    private void onSearchChanged(String searchText) {
        filteredRecipes.clear();
        selectedIndex = -1;
        removeButton.active = false;
        scrollOffset = 0;
        
        String lowerSearch = searchText.toLowerCase();
        
        if (lowerSearch.isEmpty()) {
            filteredRecipes.addAll(allOverrideRecipes);
        } else {
            for (ResourceLocation recipe : allOverrideRecipes) {
                if (recipe.toString().toLowerCase().contains(lowerSearch) ||
                    recipe.getNamespace().toLowerCase().contains(lowerSearch) ||
                    recipe.getPath().toLowerCase().contains(lowerSearch) ||
                    searchHelper.matches(recipe, searchText)) {
                    filteredRecipes.add(recipe);
                }
            }
        }
    }
    
    private void removeSelectedOverride() {
        if (selectedIndex >= 0 && selectedIndex < filteredRecipes.size()) {
            ResourceLocation recipeId = filteredRecipes.get(selectedIndex);
            
            if (UnifiedRecipeOverrideManager.removeOverride(recipeId)) {
                refreshData();
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(GuiText.component(
                            "registerhelper.message.override.removed", recipeId));
                }
            } else {
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(GuiText.component(
                            "registerhelper.message.override.remove_failed", recipeId));
                }
            }
        }
    }
    
    private void clearAllOverrides() {
        if (allOverrideRecipes.isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(GuiText.component("registerhelper.message.override.already_empty"));
            }
            return;
        }
        
        if (UnifiedRecipeOverrideManager.clearAllOverrides()) {
            refreshData();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(GuiText.component("registerhelper.message.override.cleared"));
            }
        } else {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(GuiText.component("registerhelper.message.override.clear_failed"));
            }
        }
    }
    
    private void reloadRecipes() {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(GuiText.component("registerhelper.message.override.reload_hint"));
        }
    }
    
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        GuiTheme.drawBackdrop(guiGraphics, this.width, this.height);
        
        int centerX = panelBounds.centerX();
        int listTop = listBounds.y();
        int listBottom = listBounds.bottom();
        int listHeight = listBounds.height();

        GuiTheme.drawPanel(guiGraphics, panelBounds, 24, GuiTheme.INFO);
        guiGraphics.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.bottom(),
                GuiTheme.PANEL_ALT);
        guiGraphics.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.y() + 1,
                GuiTheme.DIVIDER);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, GuiText.component("registerhelper.gui.override.title"), centerX,
                panelBounds.y() + 8, GuiTheme.TEXT_ON_HEADER);
        
        // 统计信息
        String statsText = GuiText.string("registerhelper.gui.override.stats",
                stats.totalOverrides, filteredRecipes.size());
        statsText = GuiLayoutHelper.ellipsis(this.font, statsText, panelBounds.width() - 20);
        guiGraphics.drawCenteredString(this.font, statsText, centerX,
                panelBounds.y() + 26, GuiTheme.TEXT_MUTED);
        
        // 列表背景
        GuiTheme.drawSurface(guiGraphics, listBounds, false);
        
        // 渲染配方列表
        renderRecipeList(guiGraphics, mouseX, mouseY, listBounds.x() + 5, listTop + 5,
                listBounds.width() - 10, listHeight - 10);
        
        // 滚动条
        if (filteredRecipes.size() > visibleItems) {
            renderScrollbar(guiGraphics, listBounds.right() - 8, listTop + 5, listHeight - 10);
        }
        
        // 侧边栏 - 命名空间统计
        if (sidebarVisible) {
            GuiTheme.drawSurface(guiGraphics, sidebarBounds, true);
            renderNamespaceStats(guiGraphics, sidebarBounds.x() + 6, sidebarBounds.y() + 6);
        }
        
        GuiTheme.drawInput(guiGraphics, searchBox);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    private void renderRecipeList(GuiGraphics guiGraphics, int mouseX, int mouseY, 
                                 int x, int y, int width, int height) {
        int maxScroll = Math.max(0, filteredRecipes.size() - visibleItems);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        scrollOffset = Math.max(scrollOffset, 0);
        
        for (int i = 0; i < Math.min(visibleItems, filteredRecipes.size()); i++) {
            int index = i + scrollOffset;
            if (index >= filteredRecipes.size()) break;
            
            ResourceLocation recipe = filteredRecipes.get(index);
            int itemY = y + i * ITEM_HEIGHT;
            
            boolean isSelected = (index == selectedIndex);
            boolean isHovered = mouseX >= x && mouseX < x + width && 
                               mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            
            // 背景
            if (isSelected) {
                GuiTheme.drawRow(guiGraphics, x, itemY, width, ITEM_HEIGHT, i, false, true);
            } else if (isHovered) {
                GuiTheme.drawRow(guiGraphics, x, itemY, width, ITEM_HEIGHT, i, true, false);
            } else {
                GuiTheme.drawRow(guiGraphics, x, itemY, width, ITEM_HEIGHT, i, false, false);
            }
            
            // 配方ID
            String recipeText = GuiLayoutHelper.ellipsis(
                    this.font, recipe.toString(), Math.max(1, width - 10));
            
            // 命名空间颜色
            int textColor = getNamespaceColor(recipe.getNamespace());
            guiGraphics.drawString(this.font, recipeText, x + 5, itemY + 5, textColor, false);
        }
        
        // 空列表提示
        if (filteredRecipes.isEmpty()) {
            String emptyText = GuiText.string(allOverrideRecipes.isEmpty()
                    ? "registerhelper.gui.override.empty"
                    : "registerhelper.gui.common.no_match");
            guiGraphics.drawCenteredString(this.font, emptyText, x + width / 2, y + height / 2,
                    GuiTheme.TEXT_MUTED);
        }
    }
    
    private void renderScrollbar(GuiGraphics guiGraphics, int x, int y, int height) {
        GuiLayoutHelper.Scrollbar scrollbar = GuiLayoutHelper.scrollbar(
                new GuiLayoutHelper.Bounds(x, y, 6, height),
                filteredRecipes.size(), visibleItems, scrollOffset, 10);
        if (!scrollbar.visible()) return;
        GuiTheme.drawScrollbar(guiGraphics, scrollbar, -1, -1);
    }
    
    private void renderNamespaceStats(GuiGraphics guiGraphics, int x, int y) {
        if (stats.byNamespace.isEmpty()) return;
        
        guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.common.namespace_stats"),
                x, y, GuiTheme.TEXT, false);
        
        int lineY = y + 15;
        int maxLines = Math.max(1, (sidebarBounds.height() - 20) / 12);
        int lineCount = 0;
        
        for (var entry : stats.byNamespace.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(maxLines)
                .toList()) {
            
            String namespace = entry.getKey();
            int count = entry.getValue();
            
            int color = getNamespaceColor(namespace);
            String text = GuiLayoutHelper.ellipsis(
                    this.font, namespace + ": " + count, Math.max(1, sidebarBounds.width() - 12));
            
            guiGraphics.drawString(this.font, text, x, lineY, color, false);
            lineY += 12;
            lineCount++;
        }
        
        if (stats.byNamespace.size() > maxLines) {
            guiGraphics.drawString(this.font, GuiText.string("registerhelper.gui.common.more_count",
                    stats.byNamespace.size() - maxLines), x, lineY, GuiTheme.TEXT_MUTED, false);
        }
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
        int listBottom = listBounds.bottom();
        int listX = listBounds.x() + 5;
        int listWidth = listBounds.width() - 10;
        
        if (mouseX >= listX && mouseX < listX + listWidth && 
            mouseY >= listTop + 5 && mouseY < listBottom - 5) {
            
            int row = (int) ((mouseY - listTop - 5) / ITEM_HEIGHT);
            if (row < 0 || row >= visibleItems) {
                return true;
            }
            int clickedIndex = row + scrollOffset;
            
            if (clickedIndex >= 0 && clickedIndex < filteredRecipes.size()) {
                selectedIndex = clickedIndex;
                removeButton.active = true;

                // 双击移除
                if (button == 0) {
                    removeSelectedOverride();
                    return true;
                }
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
        
        if (keyCode == 46 || keyCode == 261) { // DELETE
            if (selectedIndex >= 0) {
                removeSelectedOverride();
                return true;
            }
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
