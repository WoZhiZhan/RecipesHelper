package com.wzz.registerhelper.gui;

import com.wzz.registerhelper.util.PinyinSearchHelper;
import com.wzz.registerhelper.gui.component.CenteredEditBox;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

/**
 * 标签选择界面
 * 显示游戏中所有可用的物品标签
 */
@OnlyIn(Dist.CLIENT)
public class TagSelectorScreen extends Screen {
    
    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 35;
    private static final int FOOTER_HEIGHT = 38;
    private static final int PREFERRED_WIDTH = 420;
    private static final int PREFERRED_HEIGHT = 440;
    private static final int MIN_WIDTH = 240;
    private static final int MIN_HEIGHT = 180;
    
    private final Screen parentScreen;
    private final Consumer<ResourceLocation> onTagSelected;
    
    private EditBox searchBox;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button cancelButton;
    
    private final List<TagEntry> allTags = new ArrayList<>();
    private final List<TagEntry> filteredTags = new ArrayList<>();
    private PinyinSearchHelper<TagEntry> searchHelper;
    
    private int currentPage = 0;
    private int maxPage = 0;
    private int tagsPerPage = 12;
    private int guiWidth, guiHeight;
    private int leftPos, topPos;
    private GuiLayoutHelper.Bounds listBounds;
    private GuiLayoutHelper.Bounds footerBounds;
    
    /**
     * 标签条目，包含标签ID和代表性物品
     */
    private static class TagEntry {
        final ResourceLocation tagId;
        final ItemStack representativeItem;
        final String displayName;
        final int itemCount;
        
        TagEntry(ResourceLocation tagId, ItemStack representativeItem, int itemCount) {
            this.tagId = tagId;
            this.representativeItem = representativeItem;
            this.itemCount = itemCount;
            this.displayName = tagId.toString();
        }
    }
    
    public TagSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onTagSelected) {
        super(GuiText.component("registerhelper.gui.tag_selector.title"));
        this.parentScreen = parentScreen;
        this.onTagSelected = onTagSelected;
        this.searchHelper = new PinyinSearchHelper<>(
                tag -> tag.representativeItem.getItem().getDescription().getString(),
                tag -> tag.tagId.toString()
        );
        collectAllTags();
        updateFilteredTags("");
    }
    
    /**
     * 收集所有可用的物品标签
     */
    private void collectAllTags() {
        allTags.clear();
        
        Set<ResourceLocation> processedTags = new HashSet<>();
        
        // 遍历所有物品，收集它们的标签
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            
            // 获取物品的所有标签
            item.builtInRegistryHolder().tags().forEach(tagKey -> {
                ResourceLocation tagId = tagKey.location();
                
                if (!processedTags.contains(tagId)) {
                    processedTags.add(tagId);
                    
                    // 计算该标签包含的物品数量
                    int itemCount = (int) ForgeRegistries.ITEMS.getValues().stream()
                            .filter(i -> i.builtInRegistryHolder().is(tagKey))
                            .count();
                    
                    // 创建标签条目
                    ItemStack representativeItem = new ItemStack(item, 1);
                    allTags.add(new TagEntry(tagId, representativeItem, itemCount));
                }
            });
        }
        
        // 按命名空间和路径排序
        allTags.sort(Comparator.comparing(tag -> tag.tagId.toString()));
        searchHelper.buildCache(allTags);
    }
    
    /**
     * 更新过滤后的标签列表
     */
    private void updateFilteredTags(String searchText) {
        filteredTags.clear();
        
        String lowerSearch = searchText.toLowerCase().trim();
        
        for (TagEntry tag : allTags) {
            if (matchesSearch(tag, lowerSearch)) {
                filteredTags.add(tag);
            }
        }
        
        maxPage = Math.max(0, (filteredTags.size() - 1) / Math.max(1, tagsPerPage));
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }
    
    /**
     * 检查标签是否匹配搜索条件
     */
    private boolean matchesSearch(TagEntry tag, String searchText) {
        if (searchText.isEmpty()) return true;

        // 标签ID直接匹配
        String tagStr = tag.tagId.toString().toLowerCase();
        if (tagStr.contains(searchText)) {
            return true;
        }

        // 物品名称 + 拼音搜索（完整拼音、首字母、无空格拼音均支持）
        return searchHelper.matches(tag, searchText);
    }
    
    @Override
    protected void init() {
        String currentSearch = searchBox != null ? searchBox.getValue() : "";
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(this.width, this.height,
                PREFERRED_WIDTH, PREFERRED_HEIGHT, MIN_WIDTH, MIN_HEIGHT, 8, 8);
        this.leftPos = panel.x();
        this.topPos = panel.y();
        this.guiWidth = panel.width();
        this.guiHeight = panel.height();
        int listHeight = Math.max(ROW_HEIGHT,
                guiHeight - HEADER_HEIGHT - FOOTER_HEIGHT);
        this.tagsPerPage = Math.max(1, listHeight / ROW_HEIGHT);
        this.listBounds = new GuiLayoutHelper.Bounds(leftPos + 8, topPos + HEADER_HEIGHT,
                guiWidth - 16, tagsPerPage * ROW_HEIGHT);
        this.footerBounds = new GuiLayoutHelper.Bounds(leftPos,
                topPos + guiHeight - FOOTER_HEIGHT, guiWidth, FOOTER_HEIGHT);
        
        // 搜索框
        searchBox = new CenteredEditBox(this.font, leftPos + 8, topPos + 6,
                guiWidth - 16, 20, GuiText.component("registerhelper.gui.common.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setHint(GuiText.component("registerhelper.gui.tag_selector.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::updateFilteredTags);
        addWidget(searchBox);
        
        // 翻页按钮
        prevPageButton = addRenderableWidget(Button.builder(
                Component.literal("<"),
                button -> previousPage())
                .bounds(leftPos + 8, footerBounds.bottom() - 23, 20, 20)
                .build());
        
        nextPageButton = addRenderableWidget(Button.builder(
                Component.literal(">"),
                button -> nextPage())
                .bounds(leftPos + guiWidth - 28, footerBounds.bottom() - 23, 20, 20)
                .build());
        
        cancelButton = addRenderableWidget(Button.builder(
                GuiText.component("registerhelper.gui.common.cancel"),
                button -> onClose())
                .bounds(leftPos + (guiWidth - 48) / 2, footerBounds.bottom() - 23, 48, 20)
                .build());
        
        updateFilteredTags(currentSearch);
        updateButtons();
    }
    
    private void updateButtons() {
        if (prevPageButton != null) {
            prevPageButton.active = currentPage > 0;
        }
        if (nextPageButton != null) {
            nextPageButton.active = currentPage < maxPage;
        }
    }
    
    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updateButtons();
        }
    }
    
    private void nextPage() {
        if (currentPage < maxPage) {
            currentPage++;
            updateButtons();
        }
    }
    
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        GuiTheme.drawBackdrop(guiGraphics, this.width, this.height);
        
        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(leftPos, topPos, guiWidth, guiHeight);
        GuiTheme.drawPanel(guiGraphics, panel, 0, GuiTheme.HEADER_ACCENT);
        GuiTheme.drawSurface(guiGraphics, listBounds, false);
        guiGraphics.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.bottom(),
                GuiTheme.PANEL_ALT);
        guiGraphics.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.y() + 1,
                GuiTheme.DIVIDER);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title,
                leftPos + guiWidth / 2, topPos - 10, 0xFFFFFF);
        
        // 渲染标签列表
        renderTagList(guiGraphics, mouseX, mouseY);
        
        GuiTheme.drawInput(guiGraphics, searchBox);
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 页面信息
        guiGraphics.drawCenteredString(this.font,
                GuiText.component("registerhelper.gui.tag_selector.page",
                        currentPage + 1, maxPage + 1, filteredTags.size()),
                leftPos + guiWidth / 2, footerBounds.y() + 2, GuiTheme.TEXT_MUTED);
        
        // 渲染工具提示
        renderTooltips(guiGraphics, mouseX, mouseY);
    }
    
    private void renderTagList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * tagsPerPage;
        int endIndex = Math.min(startIndex + tagsPerPage, filteredTags.size());
        int startY = listBounds.y();
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int rowY = startY + relativeIndex * ROW_HEIGHT;
            
            TagEntry tag = filteredTags.get(i);
            
            // 检查鼠标悬停
            boolean isMouseOver = listBounds.contains(mouseX, mouseY)
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            
            // 背景
            GuiTheme.drawRow(guiGraphics, listBounds.x(), rowY, listBounds.width(),
                    ROW_HEIGHT - 1, relativeIndex, isMouseOver, false);
            
            // 渲染代表性物品图标
            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(tag.representativeItem, listBounds.x() + 4, rowY + 2);
            RenderSystem.disableDepthTest();
            
            // 标签ID
            String displayText = GuiLayoutHelper.ellipsis(this.font,
                    "#" + tag.tagId, Math.max(1, listBounds.width() - 34));
            guiGraphics.drawString(this.font, displayText,
                    listBounds.x() + 24, rowY + 4, GuiTheme.TEXT, false);
            
            // 物品数量
            guiGraphics.drawString(this.font,
                    GuiText.string("registerhelper.gui.tag_selector.item_count", tag.itemCount),
                    listBounds.x() + 24, rowY + 13, GuiTheme.TEXT_MUTED, false);
        }
    }
    
    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * tagsPerPage;
        int endIndex = Math.min(startIndex + tagsPerPage, filteredTags.size());
        int startY = listBounds.y();
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int rowY = startY + relativeIndex * ROW_HEIGHT;
            
            if (listBounds.contains(mouseX, mouseY)
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                
                TagEntry tag = filteredTags.get(i);
                List<Component> tooltip = new ArrayList<>();
                
                tooltip.add(GuiText.component("registerhelper.tooltip.tag.id", tag.tagId));
                tooltip.add(GuiText.component("registerhelper.tooltip.tag.contains", tag.itemCount));
                tooltip.add(GuiText.component("registerhelper.tooltip.tag.select"));
                
                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // 左键
            int startIndex = currentPage * tagsPerPage;
            int endIndex = Math.min(startIndex + tagsPerPage, filteredTags.size());
            int startY = listBounds.y();
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int rowY = startY + relativeIndex * ROW_HEIGHT;
                
                if (listBounds.contains(mouseX, mouseY)
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                    
                    TagEntry tag = filteredTags.get(i);
                    onTagSelected.accept(tag.tagId);
                    onClose();
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
}
