package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class BlacklistManagerScreen extends Screen {
    private final Screen parent;
    
    private List<ResourceLocation> allBlacklistedRecipes;
    private List<ResourceLocation> filteredRecipes;
    private EditBox searchBox;
    private Button removeButton;
    private Button clearAllButton;
    private Button reloadButton;
    private Button closeButton;
    
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private final int itemHeight = 18;
    private final int visibleItems = 15;
    
    private RecipeBlacklistManager.BlacklistStats stats;
    
    public BlacklistManagerScreen(Screen parent) {
        super(Component.literal("配方黑名单管理器"));
        this.parent = parent;
        refreshData();
    }
    
    private void refreshData() {
        Set<ResourceLocation> blacklistedRecipes = RecipeBlacklistManager.getBlacklistedRecipes();
        this.allBlacklistedRecipes = new ArrayList<>(blacklistedRecipes);
        this.allBlacklistedRecipes.sort(Comparator.comparing(ResourceLocation::toString));
        this.filteredRecipes = new ArrayList<>(allBlacklistedRecipes);
        this.stats = RecipeBlacklistManager.getStats();
        this.selectedIndex = -1;
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int topY = 40;
        
        // 搜索框
        searchBox = new EditBox(this.font, centerX - 200, topY, 400, 20, Component.literal("搜索黑名单配方"));
        searchBox.setHint(Component.literal("搜索配方ID或命名空间..."));
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);
        
        // 按钮区域
        int buttonY = this.height - 40;

        removeButton = addRenderableWidget(Button.builder(
                Component.literal("移除选中"),
                button -> removeSelectedRecipe())
                .bounds(centerX - 160, buttonY, 70, 20)
                .build());
        removeButton.active = false;
        
        clearAllButton = addRenderableWidget(Button.builder(
                Component.literal("清空黑名单"),
                button -> confirmClearAll())
                .bounds(centerX - 80, buttonY, 70, 20)
                .build());
        
        reloadButton = addRenderableWidget(Button.builder(
                Component.literal("重载配方"),
                button -> reloadRecipes())
                .bounds(centerX + 10, buttonY, 70, 20)
                .build());
        
        closeButton = addRenderableWidget(Button.builder(
                Component.literal("关闭"),
                button -> minecraft.setScreen(parent))
                .bounds(centerX + 90, buttonY, 50, 20)
                .build());
    }
    
    private void onSearchChanged(String searchText) {
        filteredRecipes.clear();
        selectedIndex = -1;
        removeButton.active = false;
        scrollOffset = 0;
        
        String lowerSearch = searchText.toLowerCase();
        
        if (lowerSearch.isEmpty()) {
            filteredRecipes.addAll(allBlacklistedRecipes);
        } else {
            for (ResourceLocation recipe : allBlacklistedRecipes) {
                if (recipe.toString().toLowerCase().contains(lowerSearch) ||
                    recipe.getNamespace().toLowerCase().contains(lowerSearch) ||
                    recipe.getPath().toLowerCase().contains(lowerSearch)) {
                    filteredRecipes.add(recipe);
                }
            }
        }
    }
    
    private void removeSelectedRecipe() {
        if (selectedIndex >= 0 && selectedIndex < filteredRecipes.size()) {
            ResourceLocation recipeId = filteredRecipes.get(selectedIndex);
            performRemoveOperation(recipeId);
        }
    }
    
    private void performRemoveOperation(ResourceLocation recipeId) {
        if (RecipeBlacklistManager.removeFromBlacklist(recipeId)) {
            refreshData();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§a配方已从黑名单移除: " + recipeId));
            }
        }
    }
    
    private void confirmClearAll() {
        if (allBlacklistedRecipes.isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§e黑名单已经是空的"));
            }
            return;
        }
        
        minecraft.setScreen(new ConfirmClearAllScreen(this, allBlacklistedRecipes.size(), this::performClearAll));
    }
    
    private void performClearAll() {
        if (RecipeBlacklistManager.clearBlacklist()) {
            refreshData();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§a黑名单已清空"));
            }
        }
    }
    
    private void reloadRecipes() {
        if (RecipeBlacklistManager.triggerRecipeReload()) {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§a配方重载已触发"));
            }
        } else {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§c无法触发配方重载"));
            }
        }
    }
    
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        int centerX = this.width / 2;
        int listTop = 70;
        int listBottom = this.height - 60;
        int listHeight = listBottom - listTop;
        
        // 标题
        guiGraphics.drawCenteredString(this.font, "配方黑名单管理器", centerX, 15, 0xFFFFFF);
        
        // 统计信息
        String statsText = String.format("黑名单配方: %d 个  |  显示: %d 个", 
            stats.totalBlacklisted, filteredRecipes.size());
        guiGraphics.drawCenteredString(this.font, statsText, centerX, 28, 0xAAAAAA);
        
        // 列表背景
        guiGraphics.fill(centerX - 250, listTop, centerX + 250, listBottom, 0x88000000);
        guiGraphics.fill(centerX - 249, listTop + 1, centerX + 249, listBottom - 1, 0xFF2D2D30);
        
        // 渲染配方列表
        renderRecipeList(guiGraphics, mouseX, mouseY, centerX - 240, listTop + 5, 480, listHeight - 10);
        
        // 滚动条
        if (filteredRecipes.size() > visibleItems) {
            renderScrollbar(guiGraphics, centerX + 240, listTop + 5, listHeight - 10);
        }
        
        // 侧边栏 - 命名空间统计
        renderNamespaceStats(guiGraphics, centerX + 260, listTop);
        
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
            int itemY = y + i * itemHeight;
            
            boolean isSelected = (index == selectedIndex);
            boolean isHovered = mouseX >= x && mouseX < x + width && 
                              mouseY >= itemY && mouseY < itemY + itemHeight;
            
            // 背景
            if (isSelected) {
                guiGraphics.fill(x, itemY, x + width, itemY + itemHeight, 0xFF4A6EBD);
            } else if (isHovered) {
                guiGraphics.fill(x, itemY, x + width, itemY + itemHeight, 0xFF3C3C3C);
            }
            
            // 配方ID
            String recipeText = recipe.toString();
            if (recipeText.length() > 70) {
                recipeText = recipeText.substring(0, 67) + "...";
            }
            
            // 命名空间颜色
            int textColor = getNamespaceColor(recipe.getNamespace());
            guiGraphics.drawString(this.font, recipeText, x + 5, itemY + 5, textColor, false);
        }
        
        // 空列表提示
        if (filteredRecipes.isEmpty()) {
            String emptyText = allBlacklistedRecipes.isEmpty() ? 
                "黑名单为空" : "没有匹配的配方";
            guiGraphics.drawCenteredString(this.font, emptyText, x + width / 2, y + height / 2, 0xAAAAAA);
        }
    }
    
    private void renderScrollbar(GuiGraphics guiGraphics, int x, int y, int height) {
        if (filteredRecipes.size() <= visibleItems) return;
        
        // 滚动条背景
        guiGraphics.fill(x, y, x + 6, y + height, 0xFF1E1E1E);
        
        // 滚动条滑块
        int maxScroll = filteredRecipes.size() - visibleItems;
        int thumbHeight = Math.max(10, height * visibleItems / filteredRecipes.size());
        int thumbY = y + (height - thumbHeight) * scrollOffset / maxScroll;
        
        guiGraphics.fill(x + 1, thumbY, x + 5, thumbY + thumbHeight, 0xFF8B8B8B);
    }
    
    private void renderNamespaceStats(GuiGraphics guiGraphics, int x, int y) {
        if (stats.byNamespace.isEmpty()) return;
        
        guiGraphics.drawString(this.font, "§l命名空间统计:", x, y, 0xFFFFFF, false);
        
        int lineY = y + 15;
        int maxLines = 10;
        int lineCount = 0;
        
        for (var entry : stats.byNamespace.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(maxLines)
                .toList()) {
            
            String namespace = entry.getKey();
            int count = entry.getValue();
            
            int color = getNamespaceColor(namespace);
            String text = namespace + ": " + count;
            
            guiGraphics.drawString(this.font, text, x, lineY, color, false);
            lineY += 12;
            lineCount++;
        }
        
        if (stats.byNamespace.size() > maxLines) {
            guiGraphics.drawString(this.font, "§7... 还有 " + (stats.byNamespace.size() - maxLines) + " 个", 
                x, lineY, 0xAAAAAA, false);
        }
    }
    
    private int getNamespaceColor(String namespace) {
        return switch (namespace) {
            case "minecraft" -> 0xFF55FF55;  // 绿色 - 原版
            case "registerhelper" -> 0xFFFF5555;  // 红色 - 自定义
            case "avaritia" -> 0xFF5555FF;  // 蓝色 - Avaritia
            default -> 0xFFFFAA00;  // 橙色 - 其他模组
        };
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int listTop = 70;
        int listBottom = this.height - 60;
        int listX = centerX - 240;
        int listWidth = 480;
        
        if (mouseX >= listX && mouseX < listX + listWidth && 
            mouseY >= listTop + 5 && mouseY < listBottom - 5) {
            
            int clickedIndex = (int) ((mouseY - listTop - 5) / itemHeight) + scrollOffset;
            
            if (clickedIndex >= 0 && clickedIndex < filteredRecipes.size()) {
                selectedIndex = clickedIndex;
                removeButton.active = true;

                // 双击移除
                if (button == 0) {
                    removeSelectedRecipe();
                    return true;
                }
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
        
        if (keyCode == 46 || keyCode == 261) { // DELETE
            if (selectedIndex >= 0) {
                removeSelectedRecipe();
                return true;
            }
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    /**
     * 清空确认对话框
     */
    private static class ConfirmClearAllScreen extends Screen {
        private final Screen parent;
        private final int recipeCount;
        private final Runnable onConfirm;
        
        public ConfirmClearAllScreen(Screen parent, int recipeCount, Runnable onConfirm) {
            super(Component.literal("确认清空"));
            this.parent = parent;
            this.recipeCount = recipeCount;
            this.onConfirm = onConfirm;
        }
        
        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            
            addRenderableWidget(Button.builder(
                    Component.literal("§c确认清空"),
                    button -> {
                        onConfirm.run();
                        minecraft.setScreen(parent);
                    })
                    .bounds(centerX - 70, centerY + 20, 60, 20)
                    .build());
            
            addRenderableWidget(Button.builder(
                    Component.literal("取消"),
                    button -> minecraft.setScreen(parent))
                    .bounds(centerX + 10, centerY + 20, 60, 20)
                    .build());
        }
        
        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(guiGraphics);
            
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            
            guiGraphics.fill(centerX - 150, centerY - 50, centerX + 150, centerY + 60, 0xC0000000);
            guiGraphics.fill(centerX - 149, centerY - 49, centerX + 149, centerY + 59, 0xFFFFFFFF);
            guiGraphics.fill(centerX - 148, centerY - 48, centerX + 148, centerY + 58, 0xFF8B8B8B);
            
            guiGraphics.drawCenteredString(this.font, "§c§l确认清空黑名单", centerX, centerY - 35, 0xFF0000);
            guiGraphics.drawCenteredString(this.font, "将移除 " + recipeCount + " 个配方", centerX, centerY - 15, 0x404040);
            guiGraphics.drawCenteredString(this.font, "§e此操作无法撤销！", centerX, centerY + 5, 0xFFAA00);
            
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}