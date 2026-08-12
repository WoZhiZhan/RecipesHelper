package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Three-step recipe clone wizard with responsive columns and draggable lists. */
@OnlyIn(Dist.CLIENT)
public class RecipeCloneWizardScreen extends Screen {
    private static final int PREFERRED_WIDTH = 820;
    private static final int PREFERRED_HEIGHT = 520;
    private static final int MIN_WIDTH = 480;
    private static final int MIN_HEIGHT = 280;
    private static final int PAD = 8;
    private static final int SLOT = 18;
    private static final int ROW_H = 20;
    private static final int TITLE_H = 28;
    private static final int FOOT_H = 30;
    private static final int CONTENT_Y_OFFSET = TITLE_H + 30;

    private final Screen parent;
    private final Consumer<ResourceLocation> onClone;
    private final List<ItemStack> allItems = new ArrayList<>();
    private final List<ItemStack> filteredItems = new ArrayList<>();
    private EditBox searchBox;
    private int itemScroll;
    private int draggingScrollbar = -1;
    private double scrollbarGrabOffset;
    private ItemStack targetItem = ItemStack.EMPTY;
    private final PinyinSearchHelper<ItemStack> searchHelper;
    private int itemCols = 8;
    private int itemRows = 7;
    private final List<RecipeEntry> recipeList = new ArrayList<>();
    private int recipeScroll;
    private int selectedIdx = -1;
    private int listVisRows;
    private final List<int[]> previewSlots = new ArrayList<>();
    private final List<ItemStack> previewItems = new ArrayList<>();
    private ItemStack previewResult = ItemStack.EMPTY;
    private int previewSlotSize = SLOT;
    private int previewSlotStep = SLOT + 3;
    private int px, py, panelW, panelH;
    private int c1x, c2x, c3x, contentY, contentH;
    private int col1W, col2W, col3W;

    public RecipeCloneWizardScreen(Screen parent, Consumer<ResourceLocation> onClone) {
        super(GuiText.component("registerhelper.gui.recipe_clone.title"));
        this.parent = parent;
        this.onClone = onClone;
        searchHelper = new PinyinSearchHelper<>(
                item -> item.getHoverName().getString(),
                item -> {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
                    return id != null ? id.toString() : "";
                });
        loadItems();
    }

    private void loadItems() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (ItemStack stack : player.getInventory().items) if (!stack.isEmpty()) addUnique(stack.copy());
        }
        for (var item : BuiltInRegistries.ITEM) addUnique(new ItemStack(item));
        filteredItems.addAll(allItems);
        searchHelper.buildCache(allItems);
    }

    private void addUnique(ItemStack stack) {
        for (ItemStack existing : allItems) if (ItemStack.isSameItem(existing, stack)) return;
        allItems.add(stack);
    }

    private void onSearch(String text) {
        filteredItems.clear();
        if (text.isBlank()) filteredItems.addAll(allItems);
        else for (ItemStack stack : allItems) if (searchHelper.matches(stack, text)) filteredItems.add(stack);
        itemScroll = 0;
    }

    private void pickItem(ItemStack item) {
        targetItem = item.copy();
        recipeList.clear(); selectedIdx = -1; recipeScroll = 0;
        previewItems.clear(); previewSlots.clear(); previewResult = ItemStack.EMPTY;
        RecipeManager recipeManager = getRecipeManager();
        if (recipeManager == null || minecraft.level == null) return;
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            try {
                Recipe<?> recipe = holder.value();
                boolean asIngredient = false;
                boolean asResult = ItemStack.isSameItem(
                        recipe.getResultItem(minecraft.level.registryAccess()), item);
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (!ingredient.isEmpty()) for (ItemStack material : ingredient.getItems()) {
                        if (ItemStack.isSameItem(material, item)) { asIngredient = true; break; }
                    }
                    if (asIngredient) break;
                }
                if ((asIngredient || asResult) && seen.add(holder.id())) {
                    recipeList.add(new RecipeEntry(holder, asIngredient, asResult));
                }
            } catch (Exception ignored) {
            }
        }
        recipeList.sort(Comparator.comparingInt(entry -> entry.asResult ? 0 : 1));
    }

    private void selectRecipe(int index) {
        selectedIdx = index;
        previewItems.clear(); previewSlots.clear(); previewResult = ItemStack.EMPTY;
        if (index < 0 || index >= recipeList.size() || minecraft.level == null) return;
        Recipe<?> recipe = recipeList.get(index).recipe;
        try {
            previewResult = recipe.getResultItem(minecraft.level.registryAccess()).copy();
            List<Ingredient> ingredients = recipe.getIngredients();
            int naturalCols = ingredients.size() <= 9 ? 3 : ingredients.size() <= 16 ? 4 : 5;
            int resultReserve = Math.min(36, Math.max(20, col3W / 3));
            int gridWidth = Math.max(4, col3W - 16 - resultReserve);
            int gridHeight = Math.max(4, contentH - 64);
            int maxCols = Math.max(1, gridWidth / 4);
            int maxRows = Math.max(1, gridHeight / 4);
            int neededCols = (ingredients.size() + maxRows - 1) / maxRows;
            int cols = Math.max(1, Math.min(maxCols, Math.max(naturalCols, neededCols)));
            int rows = Math.max(1, (ingredients.size() + cols - 1) / cols);
            previewSlotStep = Math.max(3, Math.min(SLOT + 3,
                    Math.min(gridWidth / cols, gridHeight / rows)));
            previewSlotSize = Math.max(2, Math.min(SLOT, previewSlotStep - 1));
            for (int i = 0; i < ingredients.size(); i++) {
                ItemStack item = ItemStack.EMPTY;
                if (!ingredients.get(i).isEmpty()) {
                    ItemStack[] choices = ingredients.get(i).getItems();
                    if (choices.length > 0) item = choices[0].copy();
                }
                previewItems.add(item);
                previewSlots.add(new int[]{i % cols, i / cols});
            }
        } catch (Exception ignored) {
        }
    }

    private RecipeManager getRecipeManager() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.getRecipeManager();
        return minecraft.level == null ? null : minecraft.level.getRecipeManager();
    }

    @Override
    protected void init() {
        String currentSearch = searchBox != null ? searchBox.getValue() : "";
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(width, height,
                PREFERRED_WIDTH, PREFERRED_HEIGHT, MIN_WIDTH, MIN_HEIGHT, 8, 8);
        px = panel.x(); py = panel.y(); panelW = panel.width(); panelH = panel.height();
        contentY = py + CONTENT_Y_OFFSET;
        contentH = panelH - CONTENT_Y_OFFSET - FOOT_H;
        listVisRows = Math.max(1, (contentH - 4) / ROW_H);

        int columnsWidth = panelW - PAD * 4;
        int minCol1Width = Math.min(110, Math.max(60, columnsWidth * 25 / 100));
        int minCol2Width = Math.min(140, Math.max(80, columnsWidth * 30 / 100));
        int minCol3Width = Math.min(120, Math.max(70, columnsWidth * 25 / 100));
        col1W = GuiLayoutHelper.clamp(columnsWidth * 26 / 100, minCol1Width,
                Math.max(minCol1Width, columnsWidth - minCol2Width - minCol3Width));
        col2W = GuiLayoutHelper.clamp(columnsWidth * 32 / 100, minCol2Width,
                Math.max(minCol2Width, columnsWidth - col1W - minCol3Width));
        c1x = px + PAD; c2x = c1x + col1W + PAD; c3x = c2x + col2W + PAD;
        col3W = panelW - (c3x - px) - PAD;
        itemCols = Math.max(1, (col1W - 4) / (SLOT + 2));
        itemRows = Math.max(1, (contentH - 18) / (SLOT + 2));

        searchBox = new EditBox(font, c1x, contentY - 18, col1W, 16,
                GuiText.component("registerhelper.gui.common.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setMaxLength(64);
        searchBox.setHint(GuiText.component("registerhelper.gui.recipe_clone.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::onSearch);
        addWidget(searchBox);
        searchBox.setFocused(true);

        int footY = py + panelH - FOOT_H + 5;
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.recipe_clone.clone"),
                        btn -> doClone()).bounds(c3x, footY, col3W, 20).build());
        int footerGap = 4;
        int closeWidth = Math.min(50, Math.max(30, col2W / 3));
        int resetWidth = Math.max(30, col2W - closeWidth - footerGap);
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.recipe_clone.reselect"),
                        btn -> { targetItem = ItemStack.EMPTY; recipeList.clear(); selectedIdx = -1; })
                .bounds(c2x, footY, resetWidth, 20).build());
        addRenderableWidget(Button.builder(GuiText.component("registerhelper.gui.common.close"),
                        btn -> onClose()).bounds(c2x + resetWidth + footerGap, footY,
                        closeWidth, 20).build());
        if (selectedIdx >= 0 && selectedIdx < recipeList.size()) selectRecipe(selectedIdx);
    }

    private void doClone() {
        if (selectedIdx >= 0 && selectedIdx < recipeList.size()) {
            onClone.accept(recipeList.get(selectedIdx).id);
            onClose();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        GuiTheme.drawBackdrop(g, width, height);
        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(px, py, panelW, panelH);
        GuiTheme.drawPanel(g, panel, TITLE_H, GuiTheme.INFO);
        g.drawCenteredString(font, GuiText.component("registerhelper.gui.recipe_clone.title"),
                px + panelW / 2, py + 10, GuiTheme.TEXT_ON_HEADER);
        g.fill(c2x - PAD / 2, contentY, c2x - PAD / 2 + 1, contentY + contentH, GuiTheme.DIVIDER);
        g.fill(c3x - PAD / 2, contentY, c3x - PAD / 2 + 1, contentY + contentH, GuiTheme.DIVIDER);
        g.fill(px, py + panelH - FOOT_H, px + panelW, py + panelH - FOOT_H + 1, GuiTheme.DIVIDER);
        renderCol1(g, mouseX, mouseY);
        renderCol2(g, mouseX, mouseY);
        renderCol3(g, mouseX, mouseY);
        GuiTheme.drawInput(g, searchBox);
        searchBox.render(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderCol1(GuiGraphics g, int mouseX, int mouseY) {
        String title = targetItem.isEmpty()
                ? GuiText.string("registerhelper.gui.recipe_clone.select_item")
                : GuiText.string("registerhelper.gui.recipe_clone.selected_item", targetItem.getHoverName());
        g.drawString(font, GuiLayoutHelper.ellipsis(font, title, col1W),
                c1x, contentY - 28, GuiTheme.TEXT_MUTED, false);
        int gridTop = contentY + 4;
        int maxScroll = Math.max(0, (filteredItems.size() + itemCols - 1) / itemCols - itemRows);
        itemScroll = clamp(itemScroll, 0, maxScroll);
        int gridW = itemCols * (SLOT + 2), gridH = itemRows * (SLOT + 2);
        GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(c1x, gridTop, gridW, gridH), false);
        for (int row = 0; row < itemRows; row++) for (int col = 0; col < itemCols; col++) {
            int index = (row + itemScroll) * itemCols + col;
            if (index >= filteredItems.size()) break;
            int sx = c1x + col * (SLOT + 2), sy = gridTop + row * (SLOT + 2);
            ItemStack item = filteredItems.get(index);
            boolean hovered = mouseX >= sx && mouseX < sx + SLOT
                    && mouseY >= sy && mouseY < sy + SLOT;
            boolean selected = ItemStack.isSameItem(item, targetItem);
            GuiTheme.drawSlot(g, sx, sy, SLOT, SLOT, hovered || selected);
            if (selected) g.fill(sx, sy, sx + 2, sy + SLOT, GuiTheme.SELECTED_EDGE);
            RenderSystem.enableDepthTest();
            g.renderItem(item, sx + 1, sy + 1);
            RenderSystem.disableDepthTest();
            if (hovered) g.renderTooltip(font, item, mouseX, mouseY);
        }
        renderScrollbar(g, c1x + gridW + 1, gridTop, 3, gridH,
                (filteredItems.size() + itemCols - 1) / itemCols, itemRows, itemScroll);
        g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.item_count", filteredItems.size()),
                c1x, gridTop + gridH + 2, GuiTheme.TEXT_MUTED, false);
    }

    private void renderCol2(GuiGraphics g, int mouseX, int mouseY) {
        if (targetItem.isEmpty()) {
            g.drawCenteredString(font, GuiText.component("registerhelper.gui.recipe_clone.select_item_first"),
                    c2x + col2W / 2, contentY + contentH / 2, GuiTheme.TEXT_MUTED);
            return;
        }
        g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.select_recipe", recipeList.size()),
                c2x, contentY - 28, GuiTheme.TEXT_MUTED, false);
        GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(c2x, contentY, col2W, contentH), false);
        if (recipeList.isEmpty()) {
            g.drawCenteredString(font, GuiText.component("registerhelper.gui.recipe_clone.no_recipe"),
                    c2x + col2W / 2, contentY + contentH / 2, GuiTheme.TEXT_MUTED);
            return;
        }
        int maxScroll = Math.max(0, recipeList.size() - recipeVisibleItems());
        recipeScroll = clamp(recipeScroll, 0, maxScroll);
        boolean shownResult = false, shownIngredient = false;
        int drawn = 0;
        for (int i = recipeScroll; i < recipeList.size() && drawn < listVisRows; i++) {
            RecipeEntry entry = recipeList.get(i);
            int rowY = contentY + drawn * ROW_H;
            if (entry.asResult && !shownResult) {
                shownResult = true;
                g.fill(c2x, rowY, c2x + col2W, rowY + ROW_H, GuiTheme.SECTION);
                g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.as_result"),
                        c2x + 4, rowY + 6, GuiTheme.INFO, false);
                drawn++; rowY = contentY + drawn * ROW_H;
                if (drawn >= listVisRows) break;
            }
            if (!entry.asResult && !shownIngredient) {
                shownIngredient = true;
                g.fill(c2x, rowY, c2x + col2W, rowY + ROW_H, GuiTheme.SECTION);
                g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.as_ingredient"),
                        c2x + 4, rowY + 6, GuiTheme.WARNING, false);
                drawn++; rowY = contentY + drawn * ROW_H;
                if (drawn >= listVisRows) break;
            }
            boolean selected = i == selectedIdx;
            boolean hovered = mouseX >= c2x && mouseX < c2x + col2W - 3
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            GuiTheme.drawRow(g, c2x, rowY, col2W, ROW_H - 1, drawn, hovered, selected);
            g.fill(c2x, rowY, c2x + 3, rowY + ROW_H - 1,
                    entry.asResult ? GuiTheme.INFO : GuiTheme.WARNING);
            if (!entry.result.isEmpty()) {
                RenderSystem.enableDepthTest();
                g.renderItem(entry.result, c2x + 5, rowY + 1);
                RenderSystem.disableDepthTest();
            }
            int maxTextWidth = col2W - SLOT - 14;
            g.drawString(font, GuiLayoutHelper.ellipsis(font, entry.id.getPath(), Math.max(1, maxTextWidth)),
                    c2x + SLOT + 9, rowY + 3, GuiTheme.TEXT, false);
            g.drawString(font, GuiLayoutHelper.ellipsis(font,
                            entry.typeName + "  " + entry.id.getNamespace(), Math.max(1, maxTextWidth)),
                    c2x + SLOT + 9, rowY + 12, GuiTheme.TEXT_MUTED, false);
            drawn++;
        }
        renderScrollbar(g, c2x + col2W - 3, contentY, 3, contentH,
                recipeList.size(), recipeVisibleItems(), recipeScroll);
    }

    private void renderCol3(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.preview"),
                c3x, contentY - 28, GuiTheme.TEXT_MUTED, false);
        GuiTheme.drawSurface(g, new GuiLayoutHelper.Bounds(c3x, contentY, col3W, contentH), true);
        if (selectedIdx < 0 || selectedIdx >= recipeList.size()) {
            g.drawCenteredString(font,
                    GuiText.component("registerhelper.gui.recipe_clone.select_recipe_first"),
                    c3x + col3W / 2, contentY + contentH / 2, GuiTheme.TEXT_MUTED);
            return;
        }
        RecipeEntry entry = recipeList.get(selectedIdx);
        g.fill(c3x, contentY, c3x + col3W, contentY + 14, GuiTheme.SECTION);
        g.drawString(font, GuiLayoutHelper.ellipsis(font,
                        entry.typeName + "  " + entry.id.getNamespace(), col3W - 8),
                c3x + 4, contentY + 3, GuiTheme.TEXT_MUTED, false);
        int slotStep = previewSlotStep, gridX = c3x + 8, gridY = contentY + 20;
        for (int i = 0; i < previewSlots.size(); i++) {
            int[] position = previewSlots.get(i);
            renderSlot(g, gridX + position[0] * slotStep, gridY + position[1] * slotStep,
                    previewSlotSize, i < previewItems.size() ? previewItems.get(i) : ItemStack.EMPTY, false);
        }
        if (!previewResult.isEmpty()) {
            int resultX = c3x + col3W - previewSlotSize - 6;
            int resultY = gridY;
            int arrowX = Math.max(gridX, resultX - 11);
            int arrowY = resultY + Math.max(0, previewSlotSize / 2 - 4);
            g.drawString(font, "→", arrowX, arrowY, GuiTheme.TEXT_MUTED, false);
            renderSlot(g, resultX, resultY, previewSlotSize, previewResult, true);
            g.drawString(font, GuiLayoutHelper.ellipsis(font, previewResult.getHoverName().getString(),
                    Math.max(1, col3W - 8)), c3x + 4, contentY + contentH - 38, GuiTheme.TEXT, false);
        }
        int infoY = contentY + contentH - 28;
        g.fill(c3x, infoY, c3x + col3W, contentY + contentH, GuiTheme.SECTION);
        g.drawString(font, GuiLayoutHelper.ellipsis(font, "ID: " + entry.id,
                Math.max(1, col3W - 8)), c3x + 4, infoY + 2, GuiTheme.TEXT_MUTED, false);
        g.drawString(font, GuiText.string("registerhelper.gui.recipe_clone.ingredient_slots",
                entry.recipe.getIngredients().size()), c3x + 4, infoY + 12, GuiTheme.TEXT, false);
    }

    private void renderSlot(GuiGraphics g, int x, int y, int size, ItemStack item, boolean result) {
        GuiTheme.drawSlot(g, x, y, size, size, result);
        if (result) g.fill(x, y, x + size, y + 2, GuiTheme.INFO);
        if (!item.isEmpty()) {
            RenderSystem.enableDepthTest();
            float scale = Math.min(1.0F, Math.max(0.125F, (size - 2) / 16.0F));
            g.pose().pushPose();
            try {
                g.pose().translate(x + 1, y + 1, 0);
                g.pose().scale(scale, scale, 1.0F);
                g.renderItem(item, 0, 0);
            } finally {
                g.pose().popPose();
            }
            RenderSystem.disableDepthTest();
            if (item.getCount() > 1 && size >= 10) {
                String count = String.valueOf(item.getCount());
                g.pose().pushPose();
                try {
                    g.pose().translate(0, 0, 300);
                    g.drawString(font, count, x + size - font.width(count), y + size - 8,
                            0xFFFFFF, true);
                } finally {
                    g.pose().popPose();
                }
            }
        }
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int w, int h,
                                 int total, int visible, int scroll) {
        GuiLayoutHelper.Scrollbar scrollbar = GuiLayoutHelper.scrollbar(
                new GuiLayoutHelper.Bounds(x, y, w, h), total, visible, scroll, 8);
        GuiTheme.drawScrollbar(g, scrollbar, -1, -1);
    }

    private GuiLayoutHelper.Scrollbar itemScrollbar() {
        int gridW = itemCols * (SLOT + 2), gridH = itemRows * (SLOT + 2);
        return GuiLayoutHelper.scrollbar(new GuiLayoutHelper.Bounds(
                        c1x + gridW + 1, contentY + 4, 3, gridH),
                (filteredItems.size() + itemCols - 1) / itemCols,
                itemRows, itemScroll, 8);
    }

    private GuiLayoutHelper.Scrollbar recipeScrollbar() {
        return GuiLayoutHelper.scrollbar(new GuiLayoutHelper.Bounds(
                        c2x + col2W - 3, contentY, 3, contentH),
                recipeList.size(), recipeVisibleItems(), recipeScroll, 8);
    }

    private int recipeVisibleItems() {
        return Math.max(1, listVisRows - 2);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            GuiLayoutHelper.Scrollbar itemScrollbar = itemScrollbar();
            if (itemScrollbar.contains(mouseX, mouseY)) {
                draggingScrollbar = 0;
                scrollbarGrabOffset = itemScrollbar.grabOffset(mouseY);
                itemScroll = itemScrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
                return true;
            }
            GuiLayoutHelper.Scrollbar recipeScrollbar = recipeScrollbar();
            if (recipeScrollbar.contains(mouseX, mouseY)) {
                draggingScrollbar = 1;
                scrollbarGrabOffset = recipeScrollbar.grabOffset(mouseY);
                recipeScroll = recipeScrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
                return true;
            }
        }
        int gridTop = contentY + 4;
        int gridW = itemCols * (SLOT + 2), gridH = itemRows * (SLOT + 2);
        if (mouseX >= c1x && mouseX < c1x + gridW
                && mouseY >= gridTop && mouseY < gridTop + gridH) {
            int col = ((int) mouseX - c1x) / (SLOT + 2);
            int row = ((int) mouseY - gridTop) / (SLOT + 2);
            int index = (row + itemScroll) * itemCols + col;
            if (index >= 0 && index < filteredItems.size()) {
                pickItem(filteredItems.get(index));
                return true;
            }
        }
        if (!targetItem.isEmpty() && mouseX >= c2x && mouseX < c2x + col2W - 3
                && mouseY >= contentY && mouseY < contentY + contentH) {
            int clickRow = ((int) mouseY - contentY) / ROW_H;
            int drawn = 0; boolean shownResult = false, shownIngredient = false;
            for (int i = recipeScroll; i < recipeList.size() && drawn < listVisRows; i++) {
                RecipeEntry entry = recipeList.get(i);
                if (entry.asResult && !shownResult) { shownResult = true; drawn++; if (drawn > listVisRows) break; }
                if (!entry.asResult && !shownIngredient) { shownIngredient = true; drawn++; if (drawn > listVisRows) break; }
                if (drawn - 1 == clickRow || drawn == clickRow) {
                    if (i == selectedIdx && button == 0) doClone();
                    else selectRecipe(i);
                    return true;
                }
                if (drawn == clickRow + 1) { selectRecipe(i); return true; }
                drawn++;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (draggingScrollbar >= 0 && button == 0) {
            GuiLayoutHelper.Scrollbar scrollbar = draggingScrollbar == 0 ? itemScrollbar() : recipeScrollbar();
            if (draggingScrollbar == 0) itemScroll = scrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
            else recipeScroll = scrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar >= 0) {
            draggingScrollbar = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int gridTop = contentY + 4;
        int gridW = itemCols * (SLOT + 2), gridH = itemRows * (SLOT + 2);
        if (mouseX >= c1x && mouseX < c1x + gridW
                && mouseY >= gridTop && mouseY < gridTop + gridH) {
            itemScroll = clamp(itemScroll - (int) scrollY, 0,
                    Math.max(0, (filteredItems.size() + itemCols - 1) / itemCols - itemRows));
            return true;
        }
        if (!targetItem.isEmpty() && mouseX >= c2x && mouseX < c2x + col2W
                && mouseY >= contentY && mouseY < contentY + contentH) {
            recipeScroll = clamp(recipeScroll - (int) scrollY, 0,
                    Math.max(0, recipeList.size() - recipeVisibleItems()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused()) return searchBox.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == 264 && selectedIdx < recipeList.size() - 1) { selectRecipe(selectedIdx + 1); return true; }
        if (keyCode == 265 && selectedIdx > 0) { selectRecipe(selectedIdx - 1); return true; }
        if (keyCode == 257 || keyCode == 335) { doClone(); return true; }
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.isFocused()) return searchBox.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    private static class RecipeEntry {
        final ResourceLocation id;
        final Recipe<?> recipe;
        final ItemStack result;
        final String typeName;
        final boolean asIngredient, asResult;

        RecipeEntry(RecipeHolder<?> holder, boolean asIngredient, boolean asResult) {
            recipe = holder.value(); id = holder.id(); this.asIngredient = asIngredient; this.asResult = asResult;
            typeName = classify(recipe);
            ItemStack output = ItemStack.EMPTY;
            try {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) output = recipe.getResultItem(minecraft.level.registryAccess()).copy();
            } catch (Exception ignored) { }
            result = output;
        }

        private static String classify(Recipe<?> recipe) {
            ResourceLocation serializerId = BuiltInRegistries.RECIPE_SERIALIZER
                    .getKey(recipe.getSerializer());
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            String type = serializerId != null ? serializerId.toString()
                    : typeId != null ? typeId.toString() : recipe.getType().toString();
            type = type.toLowerCase(Locale.ROOT);
            if (type.contains("shaped")) return GuiText.string("registerhelper.recipe_type.minecraft.crafting_shaped");
            if (type.contains("shapeless")) return GuiText.string("registerhelper.recipe_type.minecraft.crafting_shapeless");
            if (type.contains("smelting")) return GuiText.string("registerhelper.recipe_type.minecraft.smelting");
            if (type.contains("blasting")) return GuiText.string("registerhelper.recipe_type.minecraft.blasting");
            if (type.contains("smoking")) return GuiText.string("registerhelper.recipe_type.minecraft.smoking");
            if (type.contains("campfire")) return GuiText.string("registerhelper.recipe_type.minecraft.campfire");
            return type.replaceAll(".*:", "");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
