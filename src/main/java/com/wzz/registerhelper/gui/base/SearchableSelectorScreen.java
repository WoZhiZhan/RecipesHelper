package com.wzz.registerhelper.gui.base;

import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 通用的可搜索选择器界面
 * 支持拼音搜索、分类过滤、滚动等功能
 * <p>
 * 使用示例：
 * <pre>
 * new SearchableSelectorScreen<>(
 *     parentScreen,
 *     "选择物品",
 *     allItems,
 *     item -> item.getDisplayName(),      // 显示名
 *     item -> item.getId().toString(),    // ID
 *     item -> ...,                        // 详细信息
 *     selectedItem -> { ... }             // 选择回调
 * ).open(minecraft);
 * </pre>
 * 
 * @param <T> 要选择的对象类型
 */
@OnlyIn(Dist.CLIENT)
public class SearchableSelectorScreen<T> extends Screen {
    
    private final Screen parentScreen;
    private final List<T> allItems;
    private final Consumer<T> onSelected;
    private final Function<T, String> displayNameGetter;
    private final Function<T, String> idGetter;
    private final Function<T, List<Component>> tooltipGetter;
    
    private final PinyinSearchHelper<T> searchHelper;
    private List<T> filteredItems;
    
    private EditBox searchBox;
    private Button cancelButton;
    
    private int scrollOffset = 0;
    private final int maxVisibleItems = 15;
    
    private int leftPos, topPos;
    private final int width = 400;
    private final int height = 400;
    
    /**
     * 构造函数
     * @param parentScreen 父界面
     * @param title 标题
     * @param items 所有可选项
     * @param displayNameGetter 获取显示名称的函数
     * @param idGetter 获取ID的函数（用于搜索）
     * @param tooltipGetter 获取工具提示的函数
     * @param onSelected 选择回调
     */
    public SearchableSelectorScreen(Screen parentScreen,
                                   String title,
                                   List<T> items,
                                   Function<T, String> displayNameGetter,
                                   Function<T, String> idGetter,
                                   Function<T, List<Component>> tooltipGetter,
                                   Consumer<T> onSelected) {
        super(Component.literal(title));
        this.parentScreen = parentScreen;
        this.allItems = new ArrayList<>(items);
        this.displayNameGetter = displayNameGetter;
        this.idGetter = idGetter;
        this.tooltipGetter = tooltipGetter;
        this.onSelected = onSelected;
        this.filteredItems = new ArrayList<>(items);
        
        // 初始化搜索助手
        this.searchHelper = new PinyinSearchHelper<>(displayNameGetter, idGetter);
        this.searchHelper.buildCache(allItems);
    }
    
    /**
     * 简化的构造函数（自动生成基础工具提示）
     */
    public SearchableSelectorScreen(Screen parentScreen,
                                   String title,
                                   List<T> items,
                                   Function<T, String> displayNameGetter,
                                   Function<T, String> idGetter,
                                   Consumer<T> onSelected) {
        this(parentScreen, title, items, displayNameGetter, idGetter,
            item -> List.of(
                Component.literal(displayNameGetter.apply(item)),
                Component.literal("§7" + idGetter.apply(item))
            ),
            onSelected);
    }
    
    @Override
    protected void init() {
        this.leftPos = (this.width - width) / 2;
        this.topPos = (this.height - height) / 2;
        
        // 搜索框
        searchBox = new EditBox(this.font, leftPos + 20, topPos + 30, width - 40, 20,
                Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入名称/拼音/ID... 或 @mod名"));
        searchBox.setResponder(this::updateFilter);
        addRenderableWidget(searchBox);
        
        // 取消按钮
        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(leftPos + width - 60, topPos + height - 30, 50, 20)
                .build());
        
        updateFilter("");
    }
    
    private void updateFilter(String searchText) {
        this.scrollOffset = 0;
        this.filteredItems = searchHelper.filter(allItems, searchText);
        // 按显示名称排序
        this.filteredItems.sort(Comparator.comparing(displayNameGetter));
    }
    
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        
        // 背景
        guiGraphics.fill(leftPos, topPos, leftPos + width, topPos + height, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + width - 1, topPos + height - 1, 0xFF8B8B8B);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, leftPos + width / 2, topPos + 10, 0x404040);
        
        // 列表区域
        int listTop = topPos + 60;
        int listHeight = height - 100;
        guiGraphics.fill(leftPos + 20, listTop, leftPos + width - 20, listTop + listHeight, 0xFF000000);
        guiGraphics.fill(leftPos + 21, listTop + 1, leftPos + width - 21, listTop + listHeight - 1, 0xFFFFFFFF);
        
        renderList(guiGraphics, mouseX, mouseY, listTop, listHeight);
        
        if (filteredItems.size() > maxVisibleItems) {
            renderScrollbar(guiGraphics, listTop, listHeight);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltips(guiGraphics, mouseX, mouseY, listTop, listHeight);
    }
    
    private void renderList(GuiGraphics guiGraphics, int mouseX, int mouseY, int listTop, int listHeight) {
        int itemHeight = 20;
        guiGraphics.enableScissor(leftPos + 21, listTop + 1, leftPos + width - 21, listTop + listHeight - 1);
        
        int visibleItems = Math.min(maxVisibleItems, listHeight / itemHeight);
        
        for (int i = 0; i < visibleItems; i++) {
            int itemIndex = scrollOffset + i;
            if (itemIndex >= filteredItems.size()) break;
            
            T item = filteredItems.get(itemIndex);
            int itemY = listTop + 2 + i * itemHeight;
            int itemX = leftPos + 25;
            
            boolean isHovered = mouseX >= itemX && mouseX <= leftPos + width - 25 &&
                              mouseY >= itemY && mouseY < itemY + itemHeight;
            
            // 背景
            if (isHovered) {
                guiGraphics.fill(itemX, itemY, leftPos + width - 25, itemY + itemHeight, 0xFFE0E0E0);
            }
            
            // 文本
            String displayText = displayNameGetter.apply(item);
            int maxTextWidth = width - 60;
            if (this.font.width(displayText) > maxTextWidth) {
                displayText = this.font.plainSubstrByWidth(displayText, maxTextWidth - 10) + "...";
            }
            
            guiGraphics.drawString(this.font, displayText, itemX + 5, itemY + 6, 0x000000, false);
        }
        
        guiGraphics.disableScissor();
    }
    
    private void renderScrollbar(GuiGraphics guiGraphics, int listTop, int listHeight) {
        int scrollbarX = leftPos + width - 15;
        int scrollbarHeight = listHeight - 4;
        
        guiGraphics.fill(scrollbarX, listTop + 2, scrollbarX + 10, listTop + listHeight - 2, 0xFF666666);
        
        float scrollPercentage = (float) scrollOffset / (filteredItems.size() - maxVisibleItems);
        int sliderHeight = Math.max(20, scrollbarHeight * maxVisibleItems / filteredItems.size());
        int sliderY = listTop + 2 + (int) ((scrollbarHeight - sliderHeight) * scrollPercentage);
        
        guiGraphics.fill(scrollbarX + 1, sliderY, scrollbarX + 9, sliderY + sliderHeight, 0xFFCCCCCC);
    }
    
    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, int listTop, int listHeight) {
        int itemHeight = 20;
        
        if (mouseX >= leftPos + 25 && mouseX <= leftPos + width - 25 &&
            mouseY >= listTop + 1 && mouseY < listTop + listHeight - 1) {
            
            int relativeY = mouseY - (listTop + 2);
            int hoveredRow = relativeY / itemHeight;
            int itemIndex = scrollOffset + hoveredRow;
            
            if (hoveredRow >= 0 && itemIndex < filteredItems.size()) {
                T item = filteredItems.get(itemIndex);
                List<Component> tooltip = tooltipGetter.apply(item);
                
                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = topPos + 60;
        int listHeight = height - 100;
        int itemHeight = 20;
        
        if (mouseX >= leftPos + 25 && mouseX <= leftPos + width - 25 &&
            mouseY >= listTop + 1 && mouseY < listTop + listHeight - 1) {
            
            int relativeY = (int)(mouseY - (listTop + 2));
            int clickedRow = relativeY / itemHeight;
            int itemIndex = scrollOffset + clickedRow;
            
            if (clickedRow >= 0 && itemIndex < filteredItems.size()) {
                T selected = filteredItems.get(itemIndex);
                onSelected.accept(selected);
                onClose();
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (filteredItems.size() > maxVisibleItems) {
            int maxScrollOffset = Math.max(0, filteredItems.size() - maxVisibleItems);
            scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset - (int) delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    /**
     * 便捷方法：打开此界面
     */
    public void open(net.minecraft.client.Minecraft minecraft) {
        minecraft.setScreen(this);
    }
}