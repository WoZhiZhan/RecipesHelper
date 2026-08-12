package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Displays all available item tags. */
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
    private final PinyinSearchHelper<TagEntry> searchHelper;
    private int currentPage;
    private int maxPage;
    private int tagsPerPage = 12;
    private int guiWidth, guiHeight;
    private int leftPos, topPos;
    private GuiLayoutHelper.Bounds listBounds;
    private GuiLayoutHelper.Bounds footerBounds;

    private static class TagEntry {
        final ResourceLocation tagId;
        final ItemStack representativeItem;
        final int itemCount;

        TagEntry(ResourceLocation tagId, ItemStack representativeItem, int itemCount) {
            this.tagId = tagId;
            this.representativeItem = representativeItem;
            this.itemCount = itemCount;
        }
    }

    public TagSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onTagSelected) {
        super(GuiText.component("registerhelper.gui.tag_selector.title"));
        this.parentScreen = parentScreen;
        this.onTagSelected = onTagSelected;
        this.searchHelper = new PinyinSearchHelper<>(
                tag -> tag.representativeItem.getItem().getDescription().getString(),
                tag -> tag.tagId.toString());
        collectAllTags();
        updateFilteredTags("");
    }

    private void collectAllTags() {
        allTags.clear();
        Set<ResourceLocation> processedTags = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            item.builtInRegistryHolder().tags().forEach(tagKey -> {
                ResourceLocation tagId = tagKey.location();
                if (processedTags.add(tagId)) {
                    int itemCount = (int) BuiltInRegistries.ITEM.stream()
                            .filter(candidate -> candidate.builtInRegistryHolder().is(tagKey)).count();
                    allTags.add(new TagEntry(tagId, new ItemStack(item), itemCount));
                }
            });
        }
        allTags.sort(Comparator.comparing(tag -> tag.tagId.toString()));
        searchHelper.buildCache(allTags);
    }

    private void updateFilteredTags(String searchText) {
        filteredTags.clear();
        String lowerSearch = searchText.toLowerCase().trim();
        for (TagEntry tag : allTags) {
            if (lowerSearch.isEmpty() || tag.tagId.toString().toLowerCase().contains(lowerSearch)
                    || searchHelper.matches(tag, lowerSearch)) {
                filteredTags.add(tag);
            }
        }
        maxPage = Math.max(0, (filteredTags.size() - 1) / Math.max(1, tagsPerPage));
        currentPage = Math.min(currentPage, maxPage);
        updateButtons();
    }

    @Override
    protected void init() {
        String currentSearch = searchBox != null ? searchBox.getValue() : "";
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(this.width, this.height,
                PREFERRED_WIDTH, PREFERRED_HEIGHT, MIN_WIDTH, MIN_HEIGHT, 8, 8);
        leftPos = panel.x();
        topPos = panel.y();
        guiWidth = panel.width();
        guiHeight = panel.height();
        int listHeight = Math.max(ROW_HEIGHT, guiHeight - HEADER_HEIGHT - FOOTER_HEIGHT);
        tagsPerPage = Math.max(1, listHeight / ROW_HEIGHT);
        listBounds = new GuiLayoutHelper.Bounds(leftPos + 8, topPos + HEADER_HEIGHT,
                guiWidth - 16, tagsPerPage * ROW_HEIGHT);
        footerBounds = new GuiLayoutHelper.Bounds(leftPos,
                topPos + guiHeight - FOOTER_HEIGHT, guiWidth, FOOTER_HEIGHT);

        searchBox = new EditBox(this.font, leftPos + 8, topPos + 6,
                guiWidth - 16, 20, GuiText.component("registerhelper.gui.common.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setHint(GuiText.component("registerhelper.gui.tag_selector.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::updateFilteredTags);
        addWidget(searchBox);

        int buttonY = footerBounds.bottom() - 23;
        prevPageButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> previousPage())
                .bounds(leftPos + 8, buttonY, 20, 20).build());
        nextPageButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> nextPage())
                .bounds(leftPos + guiWidth - 28, buttonY, 20, 20).build());
        cancelButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.cancel"), button -> onClose())
                .bounds(leftPos + (guiWidth - 48) / 2, buttonY, 48, 20).build());
        updateFilteredTags(currentSearch);
    }

    private void updateButtons() {
        if (prevPageButton != null) prevPageButton.active = currentPage > 0;
        if (nextPageButton != null) nextPageButton.active = currentPage < maxPage;
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
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.drawBackdrop(g, this.width, this.height);
        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(leftPos, topPos, guiWidth, guiHeight);
        GuiTheme.drawPanel(g, panel, 0, GuiTheme.HEADER_ACCENT);
        GuiTheme.drawSurface(g, listBounds, false);
        g.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.bottom(),
                GuiTheme.PANEL_ALT);
        g.fill(footerBounds.x(), footerBounds.y(), footerBounds.right(), footerBounds.y() + 1,
                GuiTheme.DIVIDER);
        g.drawCenteredString(this.font, this.title,
                leftPos + guiWidth / 2, topPos - 10, GuiTheme.TEXT_ON_HEADER);

        renderTagList(g, mouseX, mouseY);
        GuiTheme.drawInput(g, searchBox);
        searchBox.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font,
                GuiText.component("registerhelper.gui.tag_selector.page",
                        currentPage + 1, maxPage + 1, filteredTags.size()),
                leftPos + guiWidth / 2, footerBounds.y() + 2, GuiTheme.TEXT_MUTED);
        renderTooltips(g, mouseX, mouseY);
    }

    private void renderTagList(GuiGraphics g, int mouseX, int mouseY) {
        int startIndex = currentPage * tagsPerPage;
        int endIndex = Math.min(startIndex + tagsPerPage, filteredTags.size());
        for (int i = startIndex; i < endIndex; i++) {
            int relativeIndex = i - startIndex;
            int rowY = listBounds.y() + relativeIndex * ROW_HEIGHT;
            TagEntry tag = filteredTags.get(i);
            boolean hovered = listBounds.contains(mouseX, mouseY)
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            GuiTheme.drawRow(g, listBounds.x(), rowY, listBounds.width(),
                    ROW_HEIGHT - 1, relativeIndex, hovered, false);
            RenderSystem.enableDepthTest();
            g.renderItem(tag.representativeItem, listBounds.x() + 4, rowY + 2);
            RenderSystem.disableDepthTest();
            String displayText = GuiLayoutHelper.ellipsis(this.font,
                    "#" + tag.tagId, Math.max(1, listBounds.width() - 34));
            g.drawString(this.font, displayText,
                    listBounds.x() + 24, rowY + 4, GuiTheme.TEXT, false);
            g.drawString(this.font,
                    GuiText.string("registerhelper.gui.tag_selector.item_count", tag.itemCount),
                    listBounds.x() + 24, rowY + 13, GuiTheme.TEXT_MUTED, false);
        }
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int startIndex = currentPage * tagsPerPage;
        int endIndex = Math.min(startIndex + tagsPerPage, filteredTags.size());
        for (int i = startIndex; i < endIndex; i++) {
            int rowY = listBounds.y() + (i - startIndex) * ROW_HEIGHT;
            if (listBounds.contains(mouseX, mouseY)
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                TagEntry tag = filteredTags.get(i);
                List<Component> tooltip = List.of(
                        GuiText.component("registerhelper.tooltip.tag.id", tag.tagId),
                        GuiText.component("registerhelper.tooltip.tag.contains", tag.itemCount),
                        GuiText.component("registerhelper.tooltip.tag.select"));
                g.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && listBounds.contains(mouseX, mouseY)) {
            int index = currentPage * tagsPerPage
                    + (int) ((mouseY - listBounds.y()) / ROW_HEIGHT);
            if (index >= 0 && index < filteredTags.size()) {
                onTagSelected.accept(filteredTags.get(index).tagId);
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
