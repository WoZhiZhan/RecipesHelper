package com.wzz.registerhelper.gui;

import com.github.promeg.pinyinhelper_fork.Pinyin;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class ItemSelectorScreen extends Screen {
    private static final int ITEMS_PER_ROW = 11;
    private static final int ITEMS_PER_PAGE = 77;
    private static final int SLOT_SIZE = 18;

    private final Screen parentScreen;
    private final Consumer<ItemStack> onItemSelected;

    private EditBox searchBox;
    private Button prevPageButton;
    private Button nextPageButton;
    private Button cancelButton;

    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();

    private final Map<ItemStack, PinyinInfo> pinyinCache = new HashMap<>();
    private int currentPage = 0;
    private int maxPage = 0;

    private int leftPos, topPos;
    private static final int GUI_WIDTH = 220;
    private static final int GUI_HEIGHT = 200;

    /**
     * 拼音信息类，包含完整拼音和首字母
     */
    private static class PinyinInfo {
        final String fullPinyin;    // 完整拼音：zuan shi
        final String initials;     // 首字母：zs
        final String nospace;      // 无空格拼音：zuanshi

        PinyinInfo(String fullPinyin, String initials, String nospace) {
            this.fullPinyin = fullPinyin;
            this.initials = initials;
            this.nospace = nospace;
        }
    }

    public ItemSelectorScreen(Screen parentScreen, Consumer<ItemStack> onItemSelected) {
        super(Component.literal("选择物品"));
        this.parentScreen = parentScreen;
        this.onItemSelected = onItemSelected;

        collectAllItems();
        buildPinyinCache();
        updateFilteredItems("");
    }

    private void collectAllItems() {
        allItems.clear();

        List<ItemStack> commonItems = List.of(
                Items.DIAMOND.getDefaultInstance(), Items.EMERALD.getDefaultInstance(),
                Items.GOLD_INGOT.getDefaultInstance(), Items.IRON_INGOT.getDefaultInstance(),
                Items.STICK.getDefaultInstance(), Items.STONE.getDefaultInstance(),
                Items.COBBLESTONE.getDefaultInstance(), Items.REDSTONE.getDefaultInstance(),
                Items.GLOWSTONE_DUST.getDefaultInstance(), Items.ENDER_PEARL.getDefaultInstance(),
                Items.BLAZE_ROD.getDefaultInstance(), Items.NETHER_STAR.getDefaultInstance(),
                Items.DRAGON_EGG.getDefaultInstance()
        );

        allItems.addAll(commonItems);

        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ItemStack stack = item.getDefaultInstance();
            if (!allItems.contains(stack) && item != Items.AIR) {
                allItems.add(stack);
            }
        }
    }

    /**
     * 构建拼音缓存，提高搜索性能
     */
    private void buildPinyinCache() {
        pinyinCache.clear();

        for (ItemStack item : allItems) {
            String displayName = item.getItem().getDescription().getString();
            PinyinInfo pinyinInfo = convertToPinyinInfo(displayName);
            pinyinCache.put(item, pinyinInfo);
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
     * 解析mod过滤器，返回过滤后的搜索文本和mod名称
     */
    private SearchFilter parseModFilter(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return new SearchFilter("", null);
        }

        String trimmed = searchText.trim();

        int atIndex = trimmed.indexOf('@');
        if (atIndex != -1) {
            String modPart = trimmed.substring(atIndex + 1);
            String textPart = trimmed.substring(0, atIndex).trim();

            String modName = normalizeModName(modPart.toLowerCase());

            return new SearchFilter(textPart, modName);
        }

        return new SearchFilter(trimmed, null);
    }

    /**
     * 标准化mod名称，处理常见别名
     */
    private String normalizeModName(String modName) {
        return switch (modName) {
            case "mc", "minec", "minecraft" -> "minecraft";
            case "forge" -> "forge";
            default -> modName;
        };
    }

    /**
     * 搜索过滤器类
     */
    private static class SearchFilter {
        final String searchText;
        final String modFilter; // null表示不过滤mod

        SearchFilter(String searchText, String modFilter) {
            this.searchText = searchText;
            this.modFilter = modFilter;
        }
    }

    /**
     * 检查是否匹配搜索条件
     */
    private boolean matchesSearch(ItemStack item, String searchText) {
        SearchFilter filter = parseModFilter(searchText);

        if (filter.modFilter != null) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item.getItem());
            if (itemId == null || !itemId.getNamespace().toLowerCase().contains(filter.modFilter)) {
                return false;
            }
        }

        if (filter.searchText.isEmpty()) {
            return true;
        }

        String lowerSearch = filter.searchText.toLowerCase().trim();

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item.getItem());
        if (itemId != null && itemId.toString().toLowerCase().contains(lowerSearch)) {
            return true;
        }

        String displayName = item.getItem().getDescription().getString().toLowerCase();
        if (displayName.contains(lowerSearch)) {
            return true;
        }

        PinyinInfo pinyinInfo = pinyinCache.get(item);
        if (pinyinInfo != null) {
            if (pinyinInfo.fullPinyin.contains(lowerSearch)) {
                return true;
            }
            if (pinyinInfo.initials.contains(lowerSearch)) {
                return true;
            }
            if (pinyinInfo.nospace.contains(lowerSearch)) {
                return true;
            }

            String[] searchWords = lowerSearch.split("\\s+");
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
                if (allMatch) {
                    return true;
                }
            }
        }

        return false;
    }

    private void updateFilteredItems(String searchText) {
        filteredItems.clear();

        for (ItemStack item : allItems) {
            if (matchesSearch(item, searchText)) {
                filteredItems.add(item);
            }
        }

        maxPage = Math.max(0, (filteredItems.size() - 1) / ITEMS_PER_PAGE);
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        searchBox = new EditBox(this.font, leftPos + 8, topPos + 6, GUI_WIDTH - 16, 20, Component.literal("搜索"));
        searchBox.setHint(Component.literal("输入物品注册名/拼音/中文... 或 @mod名"));
        searchBox.setResponder(this::updateFilteredItems);
        addWidget(searchBox);

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

        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, topPos - 10, 0xFFFFFF);

        renderItemGrid(guiGraphics, mouseX, mouseY);
        searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 显示页面信息和搜索结果统计，调整位置
        String pageInfo = String.format("第 %d/%d 页 (共%d个物品)",
                currentPage + 1, maxPage + 1, filteredItems.size());
        guiGraphics.drawCenteredString(this.font, pageInfo, leftPos + GUI_WIDTH / 2, topPos + GUI_HEIGHT + 5, 0x404040);

        renderItemTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderItemGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int x = relativeIndex % ITEMS_PER_ROW;
            int y = relativeIndex / ITEMS_PER_ROW;

            int slotX = leftPos + 8 + x * SLOT_SIZE;
            int slotY = topPos + 32 + y * SLOT_SIZE;

            boolean isMouseOver = mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE;

            // 槽位背景
            int bgColor = isMouseOver ? 0x80FFFFFF : 0xFF373737;
            guiGraphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, bgColor);

            // 槽位边框
            int borderColor = isMouseOver ? 0xFFFFFFFF : 0xFF8B8B8B;
            guiGraphics.fill(slotX - 1, slotY - 1, slotX + SLOT_SIZE + 1, slotY, borderColor);
            guiGraphics.fill(slotX - 1, slotY + SLOT_SIZE, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE + 1, borderColor);
            guiGraphics.fill(slotX - 1, slotY, slotX, slotY + SLOT_SIZE, borderColor);
            guiGraphics.fill(slotX + SLOT_SIZE, slotY, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE, borderColor);

            ItemStack item = filteredItems.get(i);

            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(item, slotX + 1, slotY + 1);
            RenderSystem.disableDepthTest();
        }
    }

    private void renderItemTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int x = relativeIndex % ITEMS_PER_ROW;
            int y = relativeIndex / ITEMS_PER_ROW;

            int slotX = leftPos + 8 + x * SLOT_SIZE;
            int slotY = topPos + 32 + y * SLOT_SIZE;

            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                    mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {

                ItemStack item = filteredItems.get(i);
                List<Component> tooltip = new ArrayList<>();

                tooltip.add(item.getItem().getDescription());

                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item.getItem());
                if (itemId != null) {
                    tooltip.add(Component.literal("§7ID: " + itemId));
                    tooltip.add(Component.literal("§9From: " + itemId.getNamespace()));
                }

                PinyinInfo pinyinInfo = pinyinCache.get(item);
                if (pinyinInfo != null && !pinyinInfo.fullPinyin.trim().isEmpty()) {
                    String displayName = item.getItem().getDescription().getString();
                    if (containsChinese(displayName)) {
                        tooltip.add(Component.literal("§8拼音: " + pinyinInfo.fullPinyin));
                        tooltip.add(Component.literal("§8简写: " + pinyinInfo.initials));
                    }
                }

                guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // 左键点击
            int startIndex = currentPage * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredItems.size());

            for (int i = startIndex; i < endIndex; i++) {
                int relativeIndex = i - startIndex;
                int x = relativeIndex % ITEMS_PER_ROW;
                int y = relativeIndex / ITEMS_PER_ROW;

                int slotX = leftPos + 8 + x * SLOT_SIZE;
                int slotY = topPos + 32 + y * SLOT_SIZE;

                if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
                        mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                    ItemStack item = filteredItems.get(i);
                    onItemSelected.accept(item);
                    onClose();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC键
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