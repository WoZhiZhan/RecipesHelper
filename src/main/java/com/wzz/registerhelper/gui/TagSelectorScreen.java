package com.wzz.registerhelper.gui;

import com.github.promeg.pinyinhelper_fork.Pinyin;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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
    
    private static final int TAGS_PER_PAGE = 12;
    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 320;
    
    private final Screen parentScreen;
    private final Consumer<ResourceLocation> onTagSelected;
    
    private EditBox searchBox;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button cancelButton;
    
    private final List<TagEntry> allTags = new ArrayList<>();
    private final List<TagEntry> filteredTags = new ArrayList<>();
    
    private int currentPage = 0;
    private int maxPage = 0;
    private int leftPos, topPos;
    
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
        super(Component.literal("选择标签"));
        this.parentScreen = parentScreen;
        this.onTagSelected = onTagSelected;
        
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
        
        maxPage = Math.max(0, (filteredTags.size() - 1) / TAGS_PER_PAGE);
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }
    
    /**
     * 检查标签是否匹配搜索条件
     */
    private boolean matchesSearch(TagEntry tag, String searchText) {
        if (searchText.isEmpty()) return true;
        
        String tagStr = tag.tagId.toString().toLowerCase();
        if (tagStr.contains(searchText)) {
            return true;
        }
        
        // 支持拼音搜索标签的中文名称（如果有）
        String itemName = tag.representativeItem.getItem().getDescription().getString().toLowerCase();
        if (itemName.contains(searchText)) {
            return true;
        }
        
        // 拼音匹配
        try {
            String pinyin = Pinyin.toPinyin(itemName, "").toLowerCase();
            if (pinyin.contains(searchText)) {
                return true;
            }
        } catch (Exception ignored) {}
        
        return false;
    }
    
    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
        
        // 搜索框
        searchBox = new EditBox(this.font, leftPos + 8, topPos + 6, GUI_WIDTH - 16, 20, Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入标签ID或物品名称..."));
        searchBox.setResponder(this::updateFilteredTags);
        addWidget(searchBox);
        
        // 翻页按钮
        prevPageButton = addRenderableWidget(Button.builder(
                Component.literal("<"),
                button -> previousPage())
                .bounds(leftPos + 8, topPos + GUI_HEIGHT - 24, 20, 20)
                .build());
        
        nextPageButton = addRenderableWidget(Button.builder(
                Component.literal(">"),
                button -> nextPage())
                .bounds(leftPos + GUI_WIDTH - 28, topPos + GUI_HEIGHT - 24, 20, 20)
                .build());
        
        cancelButton = addRenderableWidget(Button.builder(
                Component.literal("取消"),
                button -> onClose())
                .bounds(leftPos + (GUI_WIDTH - 48) / 2, topPos + GUI_HEIGHT - 24, 48, 20)
                .build());
        
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
        
        // 背景
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);
        
        // 标题
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos - 10, 0xFFFFFF);
        
        // 渲染标签列表
        renderTagList(guiGraphics, mouseX, mouseY);
        
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // 页面信息
        String pageInfo = String.format("第 %d/%d 页 (共%d个标签)",
                currentPage + 1, maxPage + 1, filteredTags.size());
        guiGraphics.drawCenteredString(this.font, pageInfo, leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT + 5, 0x404040);
        
        // 渲染工具提示
        renderTooltips(guiGraphics, mouseX, mouseY);
    }
    
    private void renderTagList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * TAGS_PER_PAGE;
        int endIndex = Math.min(startIndex + TAGS_PER_PAGE, filteredTags.size());
        
        int startY = topPos + 35;
        int rowHeight = 22;
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int rowY = startY + relativeIndex * rowHeight;
            
            TagEntry tag = filteredTags.get(i);
            
            // 检查鼠标悬停
            boolean isMouseOver = mouseX >= leftPos + 8 && mouseX < leftPos + GUI_WIDTH - 8 &&
                                mouseY >= rowY && mouseY < rowY + rowHeight - 2;
            
            // 背景
            int bgColor = isMouseOver ? 0x80FFFFFF : 0xFF555555;
            guiGraphics.fill(leftPos + 8, rowY, leftPos + GUI_WIDTH - 8, rowY + rowHeight - 2, bgColor);
            
            // 边框
            int borderColor = isMouseOver ? 0xFFFFFFFF : 0xFF8B8B8B;
            guiGraphics.fill(leftPos + 8, rowY, leftPos + GUI_WIDTH - 8, rowY + 1, borderColor);
            guiGraphics.fill(leftPos + 8, rowY + rowHeight - 2, leftPos + GUI_WIDTH - 8, rowY + rowHeight - 1, borderColor);
            
            // 渲染代表性物品图标
            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(tag.representativeItem, leftPos + 12, rowY + 2);
            RenderSystem.disableDepthTest();
            
            // 标签ID
            String displayText = "#" + tag.tagId.toString();
            if (displayText.length() > 35) {
                displayText = displayText.substring(0, 32) + "...";
            }
            guiGraphics.drawString(this.font, displayText, leftPos + 32, rowY + 4, 0xFFFFFF, false);
            
            // 物品数量
            String countText = "(" + tag.itemCount + "项)";
            guiGraphics.drawString(this.font, countText, leftPos + 32, rowY + 13, 0xCCCCCC, false);
        }
    }
    
    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * TAGS_PER_PAGE;
        int endIndex = Math.min(startIndex + TAGS_PER_PAGE, filteredTags.size());
        
        int startY = topPos + 35;
        int rowHeight = 22;
        
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int rowY = startY + relativeIndex * rowHeight;
            
            if (mouseX >= leftPos + 8 && mouseX < leftPos + GUI_WIDTH - 8 &&
                mouseY >= rowY && mouseY < rowY + rowHeight - 2) {
                
                TagEntry tag = filteredTags.get(i);
                List<Component> tooltip = new ArrayList<>();
                
                tooltip.add(Component.literal("§6标签: §f#" + tag.tagId));
                tooltip.add(Component.literal("§7包含 " + tag.itemCount + " 个物品"));
                tooltip.add(Component.literal("§8点击选择此标签"));
                
                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // 左键
            int startIndex = currentPage * TAGS_PER_PAGE;
            int endIndex = Math.min(startIndex + TAGS_PER_PAGE, filteredTags.size());
            
            int startY = topPos + 35;
            int rowHeight = 22;
            
            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int rowY = startY + relativeIndex * rowHeight;
                
                if (mouseX >= leftPos + 8 && mouseX < leftPos + GUI_WIDTH - 8 &&
                    mouseY >= rowY && mouseY < rowY + rowHeight - 2) {
                    
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