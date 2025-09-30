package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
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
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class OverrideManagerScreen extends Screen {
    private final Screen parent;
    
    private List<ResourceLocation> allOverrideRecipes;
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
    
    private UnifiedRecipeOverrideManager.OverrideStats stats;
    
    public OverrideManagerScreen(Screen parent) {
        super(Component.literal("配方覆盖管理器"));
        this.parent = parent;
        refreshData();
    }
    
    private void refreshData() {
        Set<ResourceLocation> overrideRecipes = UnifiedRecipeOverrideManager.getOverriddenRecipeIds();
        this.allOverrideRecipes = new ArrayList<>(overrideRecipes);
        this.allOverrideRecipes.sort((a, b) -> a.toString().compareTo(b.toString()));
        this.filteredRecipes = new ArrayList<>(allOverrideRecipes);
        this.stats = UnifiedRecipeOverrideManager.getStats();
        this.selectedIndex = -1;
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int topY = 40;
        
        // 搜索框
        searchBox = new EditBox(this.font, centerX - 200, topY, 400, 20, Component.literal("搜索覆盖配方"));
        searchBox.setHint(Component.literal("搜索配方ID或命名空间..."));
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);
        
        // 按钮区域
        int buttonY = this.height - 40;

        removeButton = addRenderableWidget(Button.builder(
                Component.literal("移除覆盖"),
                button -> removeSelectedOverride())
                .bounds(centerX - 160, buttonY, 70, 20)
                .build());
        removeButton.active = false;
        
        clearAllButton = addRenderableWidget(Button.builder(
                Component.literal("清空覆盖"),
                button -> clearAllOverrides())
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
            filteredRecipes.addAll(allOverrideRecipes);
        } else {
            for (ResourceLocation recipe : allOverrideRecipes) {
                if (recipe.toString().toLowerCase().contains(lowerSearch) ||
                    recipe.getNamespace().toLowerCase().contains(lowerSearch) ||
                    recipe.getPath().toLowerCase().contains(lowerSearch)) {
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
                    minecraft.player.sendSystemMessage(Component.literal("§a覆盖已移除: " + recipeId + " 使用 /reload 刷新配方"));
                }
            } else {
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.literal("§c移除覆盖失败: " + recipeId));
                }
            }
        }
    }
    
    private void clearAllOverrides() {
        if (allOverrideRecipes.isEmpty()) {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§e覆盖列表已经是空的"));
            }
            return;
        }
        
        if (UnifiedRecipeOverrideManager.clearAllOverrides()) {
            refreshData();
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§a所有覆盖已清空，使用 /reload 刷新配方"));
            }
        } else {
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§c清空覆盖失败"));
            }
        }
    }
    
    private void reloadRecipes() {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§a请使用 /reload 命令重载配方"));
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
        guiGraphics.drawCenteredString(this.font, "配方覆盖管理器", centerX, 15, 0xFFFFFF);
        
        // 统计信息
        String statsText = String.format("覆盖配方: %d 个  |  显示: %d 个", 
            stats.totalOverrides, filteredRecipes.size());
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
            String emptyText = allOverrideRecipes.isEmpty() ? 
                "没有配方覆盖" : "没有匹配的配方";
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
                    removeSelectedOverride();
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