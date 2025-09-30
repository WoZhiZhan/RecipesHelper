package com.wzz.registerhelper.gui;

import com.github.promeg.pinyinhelper_fork.Pinyin;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;
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

/**
 * 配方类型选择器屏幕
 * 提供分类浏览和搜索功能的下拉式选择器，支持拼音搜索
 */
@OnlyIn(Dist.CLIENT)
public class RecipeTypeSelectorScreen extends Screen {

    private final Screen parentScreen;
    private final Consumer<RecipeTypeDefinition> selectionCallback;
    private final List<RecipeTypeDefinition> allRecipeTypes;
    private final RecipeTypeDefinition currentSelection;

    // UI组件
    private EditBox searchBox;
    private Button cancelButton;
    private Button categoryAllButton;
    private Button categoryCraftingButton;
    private Button categoryCookingButton;
    private Button categoryModsButton;

    // 状态
    private List<RecipeTypeDefinition> filteredRecipeTypes;
    private String currentFilter = "";
    private String currentCategory = "all";
    private int scrollOffset = 0;
    private final int maxVisibleItems = 15; // 增加可见项目数量

    // 拼音缓存
    private final Map<RecipeTypeDefinition, PinyinInfo> pinyinCache = new HashMap<>();

    // 布局
    private int leftPos, topPos;
    private int contentWidth = 400;
    private int contentHeight = 400; // 进一步增加高度，给列表更多空间

    /**
     * 拼音信息类，包含完整拼音和首字母
     */
    private static class PinyinInfo {
        final String fullPinyin;    // 完整拼音：zhi ling tai
        final String initials;     // 首字母：zlt
        final String nospace;      // 无空格拼音：zhilingtai

        PinyinInfo(String fullPinyin, String initials, String nospace) {
            this.fullPinyin = fullPinyin;
            this.initials = initials;
            this.nospace = nospace;
        }
    }

    public RecipeTypeSelectorScreen(Screen parentScreen, Consumer<RecipeTypeDefinition> selectionCallback,
                                    List<RecipeTypeDefinition> recipeTypes, RecipeTypeDefinition currentSelection) {
        super(Component.literal("选择配方类型"));
        this.parentScreen = parentScreen;
        this.selectionCallback = selectionCallback;
        this.allRecipeTypes = new ArrayList<>(recipeTypes);
        this.currentSelection = currentSelection;
        this.filteredRecipeTypes = new ArrayList<>(recipeTypes);

        // 构建拼音缓存
        buildPinyinCache();
    }

    /**
     * 构建拼音缓存，提高搜索性能
     */
    private void buildPinyinCache() {
        pinyinCache.clear();

        for (RecipeTypeDefinition type : allRecipeTypes) {
            String displayName = type.getDisplayName();
            PinyinInfo pinyinInfo = convertToPinyinInfo(displayName);
            pinyinCache.put(type, pinyinInfo);
        }
    }

    /**
     * 将字符串转换为拼音信息
     */
    private PinyinInfo convertToPinyinInfo(String text) {
        if (text == null || text.isEmpty()) {
            return new PinyinInfo("", "", "");
        }

        try {
            String fullPinyin = Pinyin.toPinyin(text, " ").toLowerCase();
            StringBuilder initials = new StringBuilder();
            StringBuilder nospace = new StringBuilder();

            for (char c : text.toCharArray()) {
                if (Pinyin.isChinese(c)) {
                    String pinyin = Pinyin.toPinyin(c).toLowerCase();
                    if (!pinyin.isEmpty()) {
                        initials.append(pinyin.charAt(0));
                        nospace.append(pinyin);
                    }
                } else {
                    initials.append(Character.toLowerCase(c));
                    nospace.append(Character.toLowerCase(c));
                }
            }

            return new PinyinInfo(fullPinyin, initials.toString(), nospace.toString());

        } catch (Exception e) {
            return new PinyinInfo(text.toLowerCase(), text.toLowerCase(), text.toLowerCase());
        }
    }

    /**
     * 检查字符串是否包含中文字符
     */
    private boolean containsChinese(String text) {
        if (text == null) return false;
        for (char c : text.toCharArray()) {
            if (Pinyin.isChinese(c)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - contentWidth) / 2;
        this.topPos = (this.height - contentHeight) / 2;

        initializeSearchBox();
        initializeCategoryButtons();
        initializeActionButtons();

        updateFilteredList();
    }

    /**
     * 初始化搜索框
     */
    private void initializeSearchBox() {
        searchBox = new EditBox(this.font, leftPos + 20, topPos + 30, contentWidth - 40, 20,
                Component.literal("搜索配方类型"));
        searchBox.setHint(Component.literal("输入配方名称/拼音/mod名称..."));
        searchBox.setResponder(this::onSearchTextChanged);
        addRenderableWidget(searchBox);
    }

    /**
     * 初始化分类按钮
     */
    private void initializeCategoryButtons() {
        int buttonY = topPos + 60;
        int buttonWidth = 70;
        int buttonSpacing = 5;
        int currentX = leftPos + 20;

        categoryAllButton = addRenderableWidget(Button.builder(
                        Component.literal("全部"),
                        button -> selectCategory("all"))
                .bounds(currentX, buttonY, buttonWidth, 20)
                .build());
        currentX += buttonWidth + buttonSpacing;

        categoryCraftingButton = addRenderableWidget(Button.builder(
                        Component.literal("原版合成"),
                        button -> selectCategory("crafting"))
                .bounds(currentX, buttonY, buttonWidth, 20)
                .build());
        currentX += buttonWidth + buttonSpacing;

        categoryCookingButton = addRenderableWidget(Button.builder(
                        Component.literal("烹饪"),
                        button -> selectCategory("cooking"))
                .bounds(currentX, buttonY, buttonWidth, 20)
                .build());
        currentX += buttonWidth + buttonSpacing;

        categoryModsButton = addRenderableWidget(Button.builder(
                        Component.literal("Mods"),
                        button -> selectCategory("mods"))
                .bounds(currentX, buttonY, buttonWidth, 20)
                .build());

        updateCategoryButtonStates();
    }

    /**
     * 初始化操作按钮
     */
    private void initializeActionButtons() {
        int buttonY = topPos + contentHeight - 30;

        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(leftPos + contentWidth - 60, buttonY, 50, 20)
                .build());
    }

    /**
     * 搜索文本变化处理
     */
    private void onSearchTextChanged(String searchText) {
        this.currentFilter = searchText.toLowerCase();
        this.scrollOffset = 0;
        updateFilteredList();
    }

    /**
     * 选择分类
     */
    private void selectCategory(String category) {
        this.currentCategory = category;
        this.scrollOffset = 0;
        updateCategoryButtonStates();
        updateFilteredList();
    }

    /**
     * 更新分类按钮状态
     */
    private void updateCategoryButtonStates() {
        // 重置所有按钮到原始文本
        categoryAllButton.setMessage(Component.literal("全部"));
        categoryCraftingButton.setMessage(Component.literal("合成"));
        categoryCookingButton.setMessage(Component.literal("烹饪"));
        categoryModsButton.setMessage(Component.literal("Mods"));

        // 高亮当前选中的分类
        switch (currentCategory) {
            case "all" -> categoryAllButton.setMessage(Component.literal("§6全部"));
            case "crafting" -> categoryCraftingButton.setMessage(Component.literal("§6原版合成"));
            case "cooking" -> categoryCookingButton.setMessage(Component.literal("§6烹饪"));
            case "mods" -> categoryModsButton.setMessage(Component.literal("§6Mods"));
        }
    }

    /**
     * 更新过滤后的列表
     */
    private void updateFilteredList() {
        filteredRecipeTypes.clear();

        for (RecipeTypeDefinition type : allRecipeTypes) {
            if (matchesFilter(type) && matchesCategory(type)) {
                filteredRecipeTypes.add(type);
            }
        }

        // 按显示名称排序
        filteredRecipeTypes.sort((a, b) -> a.getDisplayName().compareTo(b.getDisplayName()));
    }

    /**
     * 检查是否匹配搜索过滤器（支持拼音搜索）
     */
    private boolean matchesFilter(RecipeTypeDefinition type) {
        if (currentFilter.isEmpty()) {
            return true;
        }

        String lowerFilter = currentFilter.trim();
        String displayName = type.getDisplayName().toLowerCase();
        String modId = type.getModId().toLowerCase();
        String typeId = type.getId().toLowerCase();

        // 基础文本匹配
        if (displayName.contains(lowerFilter) ||
                modId.contains(lowerFilter) ||
                typeId.contains(lowerFilter)) {
            return true;
        }

        // 拼音匹配
        PinyinInfo pinyinInfo = pinyinCache.get(type);
        if (pinyinInfo != null) {
            // 完整拼音匹配
            if (pinyinInfo.fullPinyin.contains(lowerFilter)) {
                return true;
            }
            // 首字母匹配
            if (pinyinInfo.initials.contains(lowerFilter)) {
                return true;
            }
            // 无空格拼音匹配
            if (pinyinInfo.nospace.contains(lowerFilter)) {
                return true;
            }

            // 分词匹配（支持部分拼音输入）
            String[] searchWords = lowerFilter.split("\\s+");
            String[] pinyinWords = pinyinInfo.fullPinyin.split("\\s+");

            if (searchWords.length > 1 && pinyinWords.length >= searchWords.length) {
                boolean allMatch = true;
                for (String searchWord : searchWords) {
                    boolean found = false;
                    for (String pinyinWord : pinyinWords) {
                        if (pinyinWord.startsWith(searchWord)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        allMatch = false;
                        break;
                    }
                }
                return allMatch;
            }
        }

        return false;
    }

    /**
     * 检查是否匹配分类过滤器
     */
    private boolean matchesCategory(RecipeTypeDefinition type) {
        switch (currentCategory) {
            case "crafting" -> {
                return type.getModId().equals("minecraft");
            }
            case "cooking" -> {
                return type.supportsCookingSettings() ||
                        "cooking".equals(type.getProperty("category", String.class));
            }
            case "mods" -> {
                return !type.getModId().equals("minecraft");
            }
            default -> { return true; }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // 绘制主背景
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + contentHeight, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + contentWidth - 1, topPos + contentHeight - 1, 0xFF8B8B8B);

        // 绘制标题
        guiGraphics.drawCenteredString(this.font, this.title, leftPos + contentWidth / 2, topPos + 10, 0x404040);

        // 绘制列表背景
        int listTop = topPos + 90;
        int listHeight = contentHeight - 130;
        guiGraphics.fill(leftPos + 20, listTop, leftPos + contentWidth - 20, listTop + listHeight, 0xFF000000);
        guiGraphics.fill(leftPos + 21, listTop + 1, leftPos + contentWidth - 21, listTop + listHeight - 1, 0xFFFFFFFF);

        // 渲染配方类型列表
        renderRecipeTypeList(guiGraphics, mouseX, mouseY, listTop, listHeight);

        // 渲染滚动条（如果需要）
        if (filteredRecipeTypes.size() > maxVisibleItems) {
            renderScrollbar(guiGraphics, listTop, listHeight);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 渲染工具提示
        renderTooltips(guiGraphics, mouseX, mouseY, listTop, listHeight);
    }

    /**
     * 渲染配方类型列表
     */
    private void renderRecipeTypeList(GuiGraphics guiGraphics, int mouseX, int mouseY, int listTop, int listHeight) {
        int itemHeight = 18;

        // 设置裁剪区域防止超出边框
        guiGraphics.enableScissor(leftPos + 21, listTop + 1, leftPos + contentWidth - 21, listTop + listHeight - 1);

        // 计算可以显示的项目数量
        int visibleItems = Math.min(maxVisibleItems, listHeight / itemHeight);

        for (int i = 0; i < visibleItems; i++) {
            int typeIndex = scrollOffset + i;

            // 检查是否超出了过滤后的配方数量
            if (typeIndex >= filteredRecipeTypes.size()) {
                break;
            }

            RecipeTypeDefinition type = filteredRecipeTypes.get(typeIndex);

            int itemY = listTop + 2 + i * itemHeight;
            int itemX = leftPos + 25;

            // 检查鼠标悬停
            boolean isHovered = mouseX >= itemX && mouseX <= leftPos + contentWidth - 25 &&
                    mouseY >= itemY && mouseY < itemY + itemHeight;

            // 检查是否为当前选择
            boolean isSelected = type.equals(currentSelection);

            // 绘制背景
            if (isSelected) {
                guiGraphics.fill(itemX, itemY, leftPos + contentWidth - 25, itemY + itemHeight, 0xFF4A90E2);
            } else if (isHovered) {
                guiGraphics.fill(itemX, itemY, leftPos + contentWidth - 25, itemY + itemHeight, 0xFFE0E0E0);
            }

            // 绘制文本
            String displayText = type.getDisplayName();
            String modText = "[" + type.getModId() + "]";

            int textColor = isSelected ? 0xFFFFFF : 0x000000;
            int modColor = isSelected ? 0xCCCCCC : 0x666666;

            // 限制文本长度防止超出边界
            int maxTextWidth = contentWidth - 80; // 为mod文本预留空间
            if (this.font.width(displayText) > maxTextWidth) {
                displayText = this.font.plainSubstrByWidth(displayText, maxTextWidth - 10) + "...";
            }

            guiGraphics.drawString(this.font, displayText, itemX + 5, itemY + 5, textColor, false);

            // 绘制mod信息
            int modWidth = this.font.width(modText);
            guiGraphics.drawString(this.font, modText, leftPos + contentWidth - 30 - modWidth, itemY + 5, modColor, false);
        }

        // 恢复裁剪
        guiGraphics.disableScissor();
    }

    /**
     * 渲染滚动条
     */
    private void renderScrollbar(GuiGraphics guiGraphics, int listTop, int listHeight) {
        int scrollbarX = leftPos + contentWidth - 15;
        int scrollbarHeight = listHeight - 4;

        // 滚动条背景
        guiGraphics.fill(scrollbarX, listTop + 2, scrollbarX + 10, listTop + listHeight - 2, 0xFF666666);

        // 滚动条滑块
        float scrollPercentage = (float) scrollOffset / (filteredRecipeTypes.size() - maxVisibleItems);
        int sliderHeight = Math.max(20, scrollbarHeight * maxVisibleItems / filteredRecipeTypes.size());
        int sliderY = listTop + 2 + (int) ((scrollbarHeight - sliderHeight) * scrollPercentage);

        guiGraphics.fill(scrollbarX + 1, sliderY, scrollbarX + 9, sliderY + sliderHeight, 0xFFCCCCCC);
    }

    /**
     * 渲染工具提示
     */
    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, int listTop, int listHeight) {
        int itemHeight = 18;

        // 严格限制在列表区域内
        if (mouseX >= leftPos + 25 && mouseX <= leftPos + contentWidth - 25 &&
                mouseY >= listTop + 1 && mouseY < listTop + listHeight - 1) {

            // 计算鼠标悬停的是哪一行
            int relativeY = (int)(mouseY - (listTop + 2));
            int hoveredRow = relativeY / itemHeight;

            // 计算对应的配方索引
            int typeIndex = scrollOffset + hoveredRow;

            // 确保悬停的行是有效的
            if (hoveredRow >= 0 && typeIndex < filteredRecipeTypes.size()) {
                RecipeTypeDefinition type = filteredRecipeTypes.get(typeIndex);

                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("§6" + type.getDisplayName()));
                tooltip.add(Component.literal("§7ID: " + type.getId()));
                tooltip.add(Component.literal("§7Mod: " + type.getModId()));
                tooltip.add(Component.literal("§7网格: " + type.getMaxGridWidth() + "×" + type.getMaxGridHeight()));

                if (type.supportsFillMode()) {
                    tooltip.add(Component.literal("§a支持填充模式"));
                }
                if (type.supportsCookingSettings()) {
                    tooltip.add(Component.literal("§e支持烹饪设置"));
                }

                // 如果配方名包含中文，显示拼音信息
                PinyinInfo pinyinInfo = pinyinCache.get(type);
                if (pinyinInfo != null && !pinyinInfo.fullPinyin.trim().isEmpty()) {
                    String displayName = type.getDisplayName();
                    if (containsChinese(displayName)) {
                        tooltip.add(Component.literal("§8拼音: " + pinyinInfo.fullPinyin));
                        tooltip.add(Component.literal("§8简写: " + pinyinInfo.initials));
                    }
                }

                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查是否点击在列表项上
        int listTop = topPos + 90;
        int listHeight = contentHeight - 130;
        int itemHeight = 18;

        // 严格限制在列表区域内
        if (mouseX >= leftPos + 25 && mouseX <= leftPos + contentWidth - 25 &&
                mouseY >= listTop + 1 && mouseY < listTop + listHeight - 1) {

            // 计算点击的是哪一行
            int relativeY = (int)(mouseY - (listTop + 2));
            int clickedRow = relativeY / itemHeight;

            // 计算对应的配方索引
            int typeIndex = scrollOffset + clickedRow;

            // 确保点击的行是有效的
            if (clickedRow >= 0 && typeIndex < filteredRecipeTypes.size()) {
                RecipeTypeDefinition selectedType = filteredRecipeTypes.get(typeIndex);

                // 执行选择回调
                selectionCallback.accept(selectedType);
                onClose();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 滚动列表
        if (filteredRecipeTypes.size() > maxVisibleItems) {
            int maxScrollOffset = Math.max(0, filteredRecipeTypes.size() - maxVisibleItems);
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
}