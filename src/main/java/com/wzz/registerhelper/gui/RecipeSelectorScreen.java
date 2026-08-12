package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.util.PinyinSearchHelper;
import com.wzz.registerhelper.network.RecipeClientCache;
import com.wzz.registerhelper.network.RequestRecipeListPacket;
import com.wzz.registerhelper.network.SyncRecipeListPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.world.item.crafting.RecipeHolder;

@OnlyIn(Dist.CLIENT)
public class RecipeSelectorScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PREFERRED_GUI_WIDTH = 900;
    private static final int PREFERRED_GUI_HEIGHT = 520;
    private static final int MIN_GUI_WIDTH = 400;
    private static final int MIN_GUI_HEIGHT = 260;
    private static final int RECIPE_DETAIL_WIDTH = 250;
    private static final int RECIPE_ITEM_HEIGHT = 22;
    private static final int MAX_VISIBLE_RECIPES = 11;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_SPACING = 20;

    private final Screen parentScreen;
    private final Consumer<ResourceLocation> onRecipeSelected;

    // 动态尺寸变量
    private int contentWidth;
    private int contentHeight;
    private int leftPos, topPos;

    private final List<RecipeEntry> allRecipes = new ArrayList<>();
    private List<RecipeEntry> filteredRecipes = new ArrayList<>();
    private int scrollOffset = 0;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private int recipeDetailWidth = RECIPE_DETAIL_WIDTH;
    private int visibleRecipeCount = MAX_VISIBLE_RECIPES;
    private int selectedRecipeIndex = -1;
    private String loadError = null;
    private boolean loadingFromServer;

    private final List<SlotInfo> currentRecipeSlots = new ArrayList<>();
    private SlotInfo currentResultSlot = null;
    private String currentRecipeTypeDisplay = "";
    private String currentRecipeTypeKey = "";

    private EditBox searchBox;
    private PinyinSearchHelper<RecipeEntry> searchHelper;
    private Button selectButton;
    private Button cancelButton;
    private Button scrollUpButton;
    private Button scrollDownButton;
    private Button refreshButton;
    private final Set<ResourceLocation> allowedRecipeIds = new HashSet<>();
    private final boolean useRecipeFilter;

    public RecipeSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onRecipeSelected) {
        super(GuiText.component("registerhelper.gui.recipe_selector.title"));
        this.minecraft = Minecraft.getInstance();
        this.parentScreen = parentScreen;
        this.onRecipeSelected = onRecipeSelected;
        this.useRecipeFilter = false;
        this.searchHelper = new PinyinSearchHelper<>(
                entry -> entry.resultItem.isEmpty() ? "" : entry.resultItem.getHoverName().getString(),
                entry -> entry.recipeId.toString()
        );
        loadRecipes();
    }

    public RecipeSelectorScreen(Screen parentScreen, Consumer<ResourceLocation> onRecipeSelected,
                                List<UnifiedRecipeInfo> allowedRecipes, String title) {
        super(Component.literal(title));
        this.minecraft = Minecraft.getInstance();
        this.parentScreen = parentScreen;
        this.onRecipeSelected = onRecipeSelected;
        this.useRecipeFilter = true;
        this.searchHelper = new PinyinSearchHelper<>(
                entry -> entry.resultItem.isEmpty() ? "" : entry.resultItem.getHoverName().getString(),
                entry -> entry.recipeId.toString()
        );
        for (UnifiedRecipeInfo recipe : allowedRecipes) {
            allowedRecipeIds.add(recipe.getRecipeId());
        }
        loadRecipes();
    }

    private void calculateDynamicSize() {
        GuiLayoutHelper.Bounds panel = GuiLayoutHelper.centered(this.width, this.height,
                PREFERRED_GUI_WIDTH, PREFERRED_GUI_HEIGHT,
                MIN_GUI_WIDTH, MIN_GUI_HEIGHT, 10, 10);
        this.contentWidth = panel.width();
        this.contentHeight = panel.height();
        this.leftPos = panel.x();
        this.topPos = panel.y();
        int maxDetailWidth = Math.max(170, contentWidth - 220);
        this.recipeDetailWidth = GuiLayoutHelper.clamp(contentWidth * 36 / 100,
                170, Math.min(300, maxDetailWidth));
        this.visibleRecipeCount = Math.max(1,
                (contentHeight - 135) / RECIPE_ITEM_HEIGHT);
    }

    private void loadRecipes() {
        allRecipes.clear();
        loadError = null;
        loadingFromServer = false;
        try {
            if (minecraft == null) {
                loadError = GuiText.string("registerhelper.message.recipe.minecraft_empty");
                LOGGER.error("Minecraft instance is null");
                return;
            }
            if (minecraft.level == null) {
                loadError = GuiText.string("registerhelper.message.world_not_loaded");
                LOGGER.error("Game level is null");
                return;
            }

            // 优先尝试从服务器直接获取（单人游戏或集成服务器）
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            RecipeManager recipeManager;

            if (server != null) {
                // 单人游戏或局域网主机，直接从服务器获取
                recipeManager = server.getRecipeManager();
                LOGGER.info("从集成服务器加载配方");
            } else {
                // 远程服务器，使用网络包请求
                LOGGER.info("检测到远程服务器，使用网络包获取配方列表");
                loadingFromServer = true;
                loadError = GuiText.string("registerhelper.message.recipe.loading");

                // 清除旧缓存
                RecipeClientCache.clearCache();

                // 发送请求
                RequestRecipeListPacket.sendToServer();

                // 添加回调，当数据返回时更新列表
                RecipeClientCache.addLoadCallback(recipes -> {
                    // 在主线程更新UI
                    minecraft.execute(() -> {
                        loadError = null;
                        loadingFromServer = false;
                        processRecipesFromCache(recipes);
                        // 也加载自定义配方
                        loadCustomRecipes();
                    });
                });

                return; // 异步加载，直接返回
            }

            if (recipeManager == null) {
                loadError = GuiText.string("registerhelper.message.recipe.manager_empty");
                LOGGER.error("Recipe manager is null");
                return;
            }
            Collection<RecipeHolder<?>> recipes = recipeManager.getRecipes();
            if (recipes.isEmpty()) {
                loadError = GuiText.string("registerhelper.message.recipe.none_loaded");
                LOGGER.warn("No recipes found in recipe manager");
                return;
            }
            int validRecipeCount = 0;
            for (RecipeHolder<?> holder : recipes) {
                try {
                    ResourceLocation id = holder.id();
                    if (id == null) {
                        LOGGER.warn("配方ID为空，跳过");
                        continue;
                    }
                    Recipe<?> recipe = holder.value();
                    ItemStack resultItem = ItemStack.EMPTY;
                    try {
                        resultItem = recipe.getResultItem(minecraft.level.registryAccess());
                    } catch (Exception e) {
                        LOGGER.warn("获取配方 {} 的结果物品失败: {}", id, e.getMessage());
                        // 即使获取结果物品失败也添加配方，使用空物品栈
                    }
                    String recipeTypeKey = getRecipeTypeKey(recipe);
                    String recipeType = classifyRecipeType(recipeTypeKey);
                    allRecipes.add(new RecipeEntry(id, resultItem, recipeType,
                            recipeTypeKey, recipe));
                    validRecipeCount++;
                } catch (Exception e) {
                    LOGGER.warn("处理配方时出错: {}", e.getMessage());
                }
            }

            // 加载自定义配方（酿造台、铁砧等）
            loadCustomRecipes();

            if (validRecipeCount == 0 && allRecipes.isEmpty()) {
                loadError = GuiText.string("registerhelper.message.recipe.no_data");
            }

            // 应用配方过滤器（但保留自定义配方）
            if (useRecipeFilter && !allowedRecipeIds.isEmpty()) {
                allRecipes.removeIf(entry -> {
                    // 保留自定义配方（registerhelper命名空间）
                    if (entry.recipeId.getNamespace().equals("registerhelper")) {
                        return false;
                    }
                    // 过滤其他不在白名单中的配方
                    return !allowedRecipeIds.contains(entry.recipeId);
                });
            }
        } catch (Exception e) {
            loadError = GuiText.string("registerhelper.message.recipe.load_error", e.getMessage());
            LOGGER.error("Error loading recipes", e);
        }

        allRecipes.sort(Comparator.comparing(entry -> entry.recipeId.toString()));
        filteredRecipes = new ArrayList<>(allRecipes);
        searchHelper.buildCache(allRecipes);
        filteredRecipes = new ArrayList<>(allRecipes);
    }

    /**
     * 加载自定义配方（酿造台、铁砧等）
     * 从config/registerhelper/custom_recipes/目录扫描JSON文件
     */
    private void loadCustomRecipes() {
        try {
            java.nio.file.Path customRecipesDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("registerhelper/custom_recipes");

            if (!java.nio.file.Files.exists(customRecipesDir)) {
                LOGGER.warn("自定义配方目录不存在: {}", customRecipesDir);
                return;
            }

            // 扫描酿造台配方
            loadCustomRecipesFromDirectory(customRecipesDir.resolve("brewing"),
                    GuiText.string("registerhelper.jei.brewing"));

            // 扫描铁砧配方
            loadCustomRecipesFromDirectory(customRecipesDir.resolve("anvil"),
                    GuiText.string("registerhelper.jei.anvil"));
        } catch (Exception e) {
            LOGGER.error("加载自定义配方时出错", e);
        }
    }

    /**
     * 从指定目录加载自定义配方JSON文件
     *
     * @return 加载的配方数量
     */
    private int loadCustomRecipesFromDirectory(java.nio.file.Path dir, String recipeType) {
        if (!java.nio.file.Files.exists(dir)) {
            LOGGER.debug("目录不存在: {}", dir);
            return 0;
        }

        final int[] count = {0};

        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(dir, 1)) {
            paths.filter(java.nio.file.Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(jsonFile -> {
                        try {

                            // 读取JSON获取输出物品
                            String content = java.nio.file.Files.readString(jsonFile);
                            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();

                            // 解析输出物品
                            ItemStack outputStack = ItemStack.EMPTY;
                            if (json.has("output")) {
                                com.google.gson.JsonElement outputElement = json.get("output");
                                if (outputElement.isJsonObject()) {
                                    com.google.gson.JsonObject outputObj = outputElement.getAsJsonObject();
                                    if (outputObj.has("item")) {
                                        String itemId = outputObj.get("item").getAsString();
                                        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                                .get(ResourceLocation.parse(itemId));
                                        if (item != null) {
                                            int itemCount = outputObj.has("count") ? outputObj.get("count").getAsInt() : 1;
                                            outputStack = new ItemStack(item, itemCount);
                                        } else {
                                            LOGGER.warn("未找到物品: {}", itemId);
                                        }
                                    }
                                }
                            } else {
                                LOGGER.warn("JSON中没有output字段: {}", jsonFile);
                            }

                            // 创建RecipeEntry，使用文件路径作为ID
                            String fileName = jsonFile.getFileName().toString().replace(".json", "");
                            String category = dir.getFileName().toString(); // brewing 或 anvil
                            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("registerhelper", "custom_" + category + "/" + fileName);

                            RecipeEntry entry = new RecipeEntry(id, outputStack, recipeType,
                                    "registerhelper:custom_" + category, null);
                            allRecipes.add(entry);
                            count[0]++;
                        } catch (Exception e) {
                            LOGGER.error("加载自定义配方文件失败: {}", jsonFile, e);
                        }
                    });
        } catch (Exception e) {
            LOGGER.error("扫描自定义配方目录失败: {}", dir, e);
        }

        return count[0];
    }

    /**
     * 从网络缓存处理配方数据（用于远程服务器）
     */
    private void processRecipesFromCache(List<UnifiedRecipeInfo> recipes) {
        allRecipes.clear();

        if (recipes.isEmpty()) {
            loadError = RecipeClientCache.getErrorMessage();
            if (loadError == null) {
                loadError = GuiText.string("registerhelper.message.recipe.server_empty");
            }
            filteredRecipes = new ArrayList<>();
            updateButtons();
            return;
        }

        // 将 UnifiedRecipeInfo 转换为 RecipeEntry
        RecipeManager clientRecipeManager = minecraft.level != null ?
                minecraft.level.getRecipeManager() : null;

        for (UnifiedRecipeInfo info : recipes) {
            try {
                // 尝试从客户端获取配方详情（用于显示）
                Recipe<?> recipe = null;
                ItemStack resultItem = ItemStack.EMPTY;

                if (clientRecipeManager != null) {
                    recipe = clientRecipeManager.byKey(info.id).map(RecipeHolder::value).orElse(null);
                    if (recipe != null) {
                        try {
                            resultItem = recipe.getResultItem(minecraft.level.registryAccess());
                        } catch (Exception e) {
                            // 忽略
                        }
                    }
                }

                String recipeTypeKey = recipe != null
                        ? getRecipeTypeKey(recipe) : extractRecipeTypeKey(info.description);
                String recipeType = classifyRecipeType(recipeTypeKey);

                // 应用过滤器
                if (useRecipeFilter && !allowedRecipeIds.isEmpty()) {
                    if (!allowedRecipeIds.contains(info.id)) {
                        continue;
                    }
                }

                allRecipes.add(new RecipeEntry(info.id, resultItem, recipeType,
                        recipeTypeKey, recipe));

            } catch (Exception e) {
                LOGGER.warn("处理配方 {} 时出错: {}", info.id, e.getMessage());
            }
        }
        allRecipes.sort(Comparator.comparing(entry -> entry.recipeId.toString()));
        filteredRecipes = new ArrayList<>(allRecipes);
        searchHelper.buildCache(allRecipes);

        if (searchBox != null && !searchBox.getValue().isEmpty()) {
            onSearchTextChanged(searchBox.getValue());
        }
        updateButtons();
    }

    private static String getRecipeTypeKey(Recipe<?> recipe) {
        ResourceLocation serializerId = BuiltInRegistries.RECIPE_SERIALIZER
                .getKey(recipe.getSerializer());
        if (serializerId != null) {
            return serializerId.toString().toLowerCase(Locale.ROOT);
        }
        ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return typeId != null ? typeId.toString().toLowerCase(Locale.ROOT)
                : recipe.getType().toString().toLowerCase(Locale.ROOT);
    }

    private static String extractRecipeTypeKey(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        int arrow = description.indexOf("->");
        String type = arrow >= 0 ? description.substring(0, arrow) : description;
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private String classifyRecipeType(String recipeTypeKey) {
        try {
            String typeName = recipeTypeKey.toLowerCase(Locale.ROOT);

            if (typeName.contains("crafting_shaped")) {
                return GuiText.string("registerhelper.recipe_type.minecraft.crafting_shaped");
            } else if (typeName.contains("crafting_shapeless")) {
                return GuiText.string("registerhelper.recipe_type.minecraft.crafting_shapeless");
            } else if (typeName.contains("smelting")) {
                return GuiText.string("registerhelper.recipe_type.minecraft.smelting");
            } else if (typeName.contains("blasting")) {
                return GuiText.string("registerhelper.recipe_type.minecraft.blasting");
            } else if (typeName.contains("smoking")) {
                return GuiText.string("registerhelper.recipe_type.minecraft.smoking");
            } else if (typeName.contains("campfire")) {
                return GuiText.string("registerhelper.recipe_type.minecraft.campfire");
            } else if (typeName.contains("avaritia")) {
                if (typeName.contains("shaped")) {
                    return GuiText.string("registerhelper.recipe_type.avaritia.shaped");
                } else if (typeName.contains("shapeless")) {
                    return GuiText.string("registerhelper.recipe_type.avaritia.shapeless");
                } else {
                    return GuiText.string("registerhelper.recipe_type.avaritia.generic");
                }
            } else if (typeName.isBlank()) {
                return GuiText.string("registerhelper.recipe_type.unknown");
            } else {
                return typeName;
            }

        } catch (Exception e) {
            LOGGER.warn("分类配方类型失败: {}", e.getMessage());
            return GuiText.string("registerhelper.recipe_type.unknown");
        }
    }

    @Override
    protected void init() {
        String currentSearch = searchBox != null ? searchBox.getValue() : "";
        calculateDynamicSize();
        scrollOffset = GuiLayoutHelper.clamp(scrollOffset, 0,
                Math.max(0, filteredRecipes.size() - visibleRecipeCount));
        if (selectedRecipeIndex >= 0) {
            if (selectedRecipeIndex < scrollOffset) {
                scrollOffset = selectedRecipeIndex;
            } else if (selectedRecipeIndex >= scrollOffset + visibleRecipeCount) {
                scrollOffset = selectedRecipeIndex - visibleRecipeCount + 1;
            }
        }

        // 搜索框位置调整，为右侧列表区域
        int listAreaX = leftPos + recipeDetailWidth + 20;
        int listAreaWidth = Math.max(40, contentWidth - recipeDetailWidth - 60);

        searchBox = new EditBox(this.font, listAreaX, topPos + 45, listAreaWidth, 20,
                GuiText.component("registerhelper.gui.recipe_selector.search"));
        GuiTheme.styleInput(searchBox);
        searchBox.setHint(GuiText.component("registerhelper.gui.recipe_selector.search_hint"));
        searchBox.setValue(currentSearch);
        searchBox.setResponder(this::onSearchTextChanged);
        addRenderableWidget(searchBox);

        String[] actionLabels = {
                GuiText.string("registerhelper.gui.recipe_selector.select"),
                GuiText.string("registerhelper.gui.common.cancel"),
                GuiText.string("registerhelper.gui.common.refresh")};
        int[] actionWidths = {
                this.font.width(actionLabels[0]) + 14,
                this.font.width(actionLabels[1]) + 14,
                this.font.width(actionLabels[2]) + 14};
        int actionGap = 5;
        int actionTotal = Arrays.stream(actionWidths).sum() + actionGap * 2;
        if (actionTotal > listAreaWidth) {
            int compactWidth = Math.max(32, (listAreaWidth - actionGap * 2) / 3);
            Arrays.fill(actionWidths, compactWidth);
        }
        int actionX = listAreaX;

        selectButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.recipe_selector.select"),
                        button -> selectRecipe())
                .bounds(actionX, topPos + 75, actionWidths[0], 20)
                .build());
        actionX += actionWidths[0] + actionGap;
        cancelButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.cancel"),
                        button -> onClose())
                .bounds(actionX, topPos + 75, actionWidths[1], 20)
                .build());
        actionX += actionWidths[1] + actionGap;
        refreshButton = addRenderableWidget(Button.builder(
                        GuiText.component("registerhelper.gui.common.refresh"),
                        button -> refreshRecipes())
                .bounds(actionX, topPos + 75, actionWidths[2], 20)
                .build());
        scrollUpButton = addRenderableWidget(Button.builder(
                        Component.literal("▲"),
                        button -> scrollUp())
                .bounds(leftPos + contentWidth - 40, topPos + 105, 30, 20)
                .build());

        scrollDownButton = addRenderableWidget(Button.builder(
                        Component.literal("▼"),
                        button -> scrollDown())
                .bounds(leftPos + contentWidth - 40,
                        topPos + 105 + visibleRecipeCount * RECIPE_ITEM_HEIGHT - 20, 30, 20)
                .build());
        if (selectedRecipeIndex >= 0 && selectedRecipeIndex < filteredRecipes.size()) {
            parseSelectedRecipe();
        }
        updateButtons();
    }

    private void refreshRecipes() {
        loadRecipes();
        scrollOffset = 0;
        selectedRecipeIndex = -1;
        if (searchBox != null && !searchBox.getValue().isEmpty()) {
            onSearchTextChanged(searchBox.getValue());
        }
        updateButtons();
    }

    private void onSearchTextChanged(String searchText) {
        if (searchText.isEmpty()) {
            filteredRecipes = new ArrayList<>(allRecipes);
        } else {
            String lowerSearch = searchText.toLowerCase(Locale.ROOT);

            filteredRecipes = allRecipes.stream()
                    .filter(entry -> {
                        String recipeIdStr = entry.recipeId.toString().toLowerCase(Locale.ROOT);
                        if (recipeIdStr.contains(lowerSearch)) {
                            return true;
                        }
                        String recipeTypeLower = entry.recipeType.toLowerCase(Locale.ROOT);
                        if (recipeTypeLower.contains(lowerSearch)) {
                            return true;
                        }
                        if (lowerSearch.contains(GuiText.string(
                                "registerhelper.gui.recipe_selector.search.custom").toLowerCase(Locale.ROOT))
                                || lowerSearch.contains("custom")) {
                            if (entry.recipeId.getNamespace().equals("registerhelper") &&
                                    entry.recipeId.getPath().startsWith("custom_")) {
                                return true;
                            }
                        }
                        if (lowerSearch.contains(GuiText.string(
                                "registerhelper.gui.recipe_selector.search.brewing").toLowerCase(Locale.ROOT))
                                || lowerSearch.contains("brew")) {
                            if (recipeTypeLower.contains(GuiText.string(
                                    "registerhelper.jei.brewing").toLowerCase(Locale.ROOT)) ||
                                    entry.recipeId.getPath().contains("brewing")) {
                                return true;
                            }
                        }
                        if (lowerSearch.contains(GuiText.string(
                                "registerhelper.gui.recipe_selector.search.anvil").toLowerCase(Locale.ROOT))
                                || lowerSearch.contains("anvil")) {
                            if (recipeTypeLower.contains(GuiText.string(
                                    "registerhelper.jei.anvil").toLowerCase(Locale.ROOT)) ||
                                    entry.recipeId.getPath().contains("anvil")) {
                                return true;
                            }
                        }
                        try {
                            if (!entry.resultItem.isEmpty()) {
                                String itemName = entry.resultItem.getHoverName().getString()
                                        .toLowerCase(Locale.ROOT);
                                if (itemName.contains(lowerSearch)) {
                                    return true;
                                }
                            }
                        } catch (Exception e) {
                            // 忽略异常，继续其他匹配
                        }
                        return searchHelper.matches(entry, searchText);
                    })
                    .collect(Collectors.toList());
        }

        scrollOffset = 0;
        selectedRecipeIndex = -1;
        updateButtons();
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            updateButtons();
        }
    }

    private void scrollDown() {
        int maxScroll = Math.max(0, filteredRecipes.size() - visibleRecipeCount);
        if (scrollOffset < maxScroll) {
            scrollOffset++;
            updateButtons();
        }
    }

    private void updateButtons() {
        if (scrollUpButton != null) {
            scrollUpButton.active = scrollOffset > 0;
        }
        if (scrollDownButton != null) {
            scrollDownButton.active = scrollOffset < Math.max(0, filteredRecipes.size() - visibleRecipeCount);
        }
        if (selectButton != null) {
            selectButton.active = selectedRecipeIndex >= 0;
        }
    }

    private void selectRecipe() {
        if (selectedRecipeIndex >= 0 && selectedRecipeIndex < filteredRecipes.size()) {
            RecipeEntry selected = filteredRecipes.get(selectedRecipeIndex);

            // 检查是否是自定义配方
            if (selected.recipeId.getNamespace().equals("registerhelper") &&
                    selected.recipeId.getPath().startsWith("custom_")) {
                // 自定义配方不支持GUI编辑，需要手动编辑JSON
                if (minecraft.player != null) {
                    String filePath = "config/registerhelper/custom_recipes/"
                            + (selected.recipeId.getPath().contains("brewing") ? "brewing/" : "anvil/")
                            + selected.recipeId.getPath().substring(
                                    selected.recipeId.getPath().lastIndexOf('/') + 1) + ".json";
                    minecraft.player.sendSystemMessage(GuiText.component(
                            "registerhelper.message.recipe.custom_edit_unsupported", filePath));
                }
                return;
            }

            onRecipeSelected.accept(selected.recipeId);
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parentScreen);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        GuiTheme.drawBackdrop(guiGraphics, this.width, this.height);
        // ── 外框 ──
        GuiLayoutHelper.Bounds panel = new GuiLayoutHelper.Bounds(leftPos, topPos, contentWidth, contentHeight);
        GuiTheme.drawPanel(guiGraphics, panel, 28, GuiTheme.INFO);
        // ── 主背景 ──
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + contentHeight, GuiTheme.PANEL);
        // ── 标题栏 ──
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + 28, GuiTheme.HEADER);
        guiGraphics.fill(leftPos, topPos, leftPos + contentWidth, topPos + 1, GuiTheme.HEADER_ACCENT);
        guiGraphics.fill(leftPos, topPos + 27, leftPos + contentWidth, topPos + 28, GuiTheme.DIVIDER);
        // ── 标题文字 ──
        guiGraphics.drawCenteredString(this.font, this.title,
                leftPos + contentWidth / 2, topPos + 10, GuiTheme.TEXT_ON_HEADER);

        // 渲染配方详情区域
        renderRecipeDetail(guiGraphics);

        // 分割线（双线带阴影）
        guiGraphics.fill(leftPos + recipeDetailWidth + 5, topPos + 30,
                leftPos + recipeDetailWidth + 6, topPos + contentHeight - 10, GuiTheme.DIVIDER);
        guiGraphics.fill(leftPos + recipeDetailWidth + 6, topPos + 30,
                leftPos + recipeDetailWidth + 7, topPos + contentHeight - 10, GuiTheme.BORDER);

        // 右侧列表区域
        int listAreaX = leftPos + recipeDetailWidth + 20;

        if (loadError != null) {
            // 检查是否正在加载
            if (loadingFromServer || RecipeClientCache.isLoading()) {
                float progress = SyncRecipeListPacket.getProgress();
                String progressText = GuiText.string(
                        "registerhelper.gui.recipe_selector.loading_progress",
                        String.format(Locale.ROOT, "%.0f", progress * 100));
                guiGraphics.drawCenteredString(this.font, progressText,
                        listAreaX + (contentWidth - recipeDetailWidth - 40) / 2, topPos + 30, GuiTheme.WARNING);

                // 绘制进度条
                int barX = listAreaX;
                int barY = topPos + 50;
                int barWidth = Math.max(1, contentWidth - recipeDetailWidth - 60);
                int barHeight = 10;
                guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight,
                        GuiTheme.SCROLL_TRACK);
                guiGraphics.fill(barX + 1, barY + 1,
                        barX + (int) ((barWidth - 2) * progress), barY + barHeight - 1,
                        GuiTheme.SUCCESS);
            } else {
                guiGraphics.drawCenteredString(this.font,
                        GuiText.component("registerhelper.gui.recipe_selector.error", loadError),
                        listAreaX + (contentWidth - recipeDetailWidth - 40) / 2, topPos + 30, GuiTheme.DANGER);
                guiGraphics.drawCenteredString(this.font,
                        GuiText.component("registerhelper.gui.recipe_selector.retry"),
                        listAreaX + (contentWidth - recipeDetailWidth - 40) / 2, topPos + 105, GuiTheme.WARNING);
            }
        } else {
            String countText = GuiText.string("registerhelper.gui.recipe_selector.count",
                    filteredRecipes.size(), allRecipes.size());
            guiGraphics.drawString(this.font, countText, listAreaX, topPos + 30,
                    GuiTheme.TEXT_MUTED, false);
        }

        int listTop = topPos + 105;
        int listBottom = listTop + visibleRecipeCount * RECIPE_ITEM_HEIGHT;
        int listRight = leftPos + contentWidth - 50;

        // 配方列表背景
        GuiTheme.drawSurface(guiGraphics, new GuiLayoutHelper.Bounds(listAreaX - 10, listTop,
                listRight - listAreaX + 10, listBottom - listTop), false);

        if (loadError == null) {
            renderRecipeList(guiGraphics, mouseX, mouseY, listTop, listAreaX, listRight);
        } else {
            guiGraphics.drawCenteredString(this.font,
                    GuiText.component("registerhelper.gui.recipe_selector.load_failed"),
                    listAreaX + (listRight - listAreaX) / 2, listTop + 50, GuiTheme.TEXT_MUTED);
        }

        GuiTheme.drawInput(guiGraphics, searchBox);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderRecipeDetail(GuiGraphics guiGraphics) {
        int detailX = leftPos + 10;
        int detailY = topPos + 30;
        int detailWidth = recipeDetailWidth - 10;
        int detailHeight = contentHeight - 40;

        GuiTheme.drawSurface(guiGraphics,
                new GuiLayoutHelper.Bounds(detailX, detailY, detailWidth, detailHeight), true);
        // 详情区标题
        guiGraphics.fill(detailX, detailY, detailX + detailWidth, detailY + 22, GuiTheme.SECTION);
        guiGraphics.fill(detailX, detailY, detailX + detailWidth, detailY + 2, GuiTheme.INFO);
        guiGraphics.drawCenteredString(this.font,
                GuiText.component("registerhelper.gui.recipe_selector.preview"),
                detailX + detailWidth / 2, detailY + 7, GuiTheme.TEXT);

        if (selectedRecipeIndex >= 0 && selectedRecipeIndex < filteredRecipes.size()) {
            RecipeEntry selected = filteredRecipes.get(selectedRecipeIndex);

            int typeColor = getTypeColor(currentRecipeTypeDisplay);
            String typeDisplay = GuiLayoutHelper.ellipsis(this.font,
                    currentRecipeTypeDisplay, Math.max(1, detailWidth - 16));
            guiGraphics.drawCenteredString(this.font, typeDisplay,
                    detailX + detailWidth / 2, detailY + 25, typeColor);

            if (currentResultSlot != null) {
                guiGraphics.drawString(this.font,
                        GuiText.string("registerhelper.gui.recipe_selector.output"),
                        detailX + 10, detailY + 45, GuiTheme.TEXT_MUTED, false);
                renderRecipeSlot(guiGraphics, currentResultSlot, true);
            }

            if (!currentRecipeSlots.isEmpty()) {
                guiGraphics.drawString(this.font,
                        GuiText.string("registerhelper.gui.recipe_selector.ingredients"),
                        detailX + 10, getPreviewGridStartY() - 12,
                        GuiTheme.TEXT_MUTED, false);

                for (SlotInfo slot : currentRecipeSlots) {
                    renderRecipeSlot(guiGraphics, slot, false);
                }

                if (isCookingRecipeType(currentRecipeTypeKey)) {
                    if (currentResultSlot != null && !currentRecipeSlots.isEmpty()) {
                        SlotInfo inputSlot = currentRecipeSlots.get(0);
                        int arrowStartX = inputSlot.x + inputSlot.size + 5;
                        int arrowEndX = currentResultSlot.x - 5;
                        int arrowY = inputSlot.y + inputSlot.size / 2;

                        guiGraphics.drawString(this.font, "→", arrowStartX + 5,
                                arrowY - 4, GuiTheme.TEXT_MUTED, false);
                    }
                }
            }

            String shortId = selected.recipeId.toString();
            guiGraphics.drawString(this.font,
                    GuiText.string("registerhelper.gui.common.id_colon"),
                    detailX + 10, detailY + detailHeight - 40, GuiTheme.TEXT_MUTED, false);

            // 分行显示长ID
            int maxLineWidth = detailWidth - 20;
            String[] idLines = wrapText(shortId, maxLineWidth);
            for (int i = 0; i < Math.min(idLines.length, 2); i++) {
                guiGraphics.drawString(this.font, idLines[i], detailX + 10,
                        detailY + detailHeight - 30 + i * 10, GuiTheme.TEXT, false);
            }

        } else {
            guiGraphics.drawCenteredString(this.font,
                    GuiText.component("registerhelper.gui.recipe_selector.select_for_details"),
                    detailX + detailWidth / 2, detailY + detailHeight / 2, GuiTheme.TEXT_MUTED);
        }
    }

    private String[] wrapText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return new String[]{text};
        }

        List<String> lines = new ArrayList<>();
        String remaining = text;

        while (!remaining.isEmpty() && this.font.width(remaining) > maxWidth) {
            int breakPoint = remaining.length();
            while (breakPoint > 0 && this.font.width(remaining.substring(0, breakPoint)) > maxWidth) {
                breakPoint--;
            }
            if (breakPoint == 0) breakPoint = 1; // 防止无限循环

            lines.add(remaining.substring(0, breakPoint));
            remaining = remaining.substring(breakPoint);
        }

        if (!remaining.isEmpty()) {
            lines.add(remaining);
        }

        return lines.toArray(new String[0]);
    }

    private void renderRecipeSlot(GuiGraphics guiGraphics, SlotInfo slot, boolean isResultSlot) {
        int slotSize = slot.size;
        GuiTheme.drawSlot(guiGraphics, slot.x, slot.y, slotSize, slotSize, isResultSlot);
        if (isResultSlot) {
            guiGraphics.fill(slot.x, slot.y, slot.x + slotSize, slot.y + 2, GuiTheme.INFO);
        }

        if (!slot.item.isEmpty()) {
            try {
                RenderSystem.enableDepthTest();
                float scale = Math.min(1.0F,
                        Math.max(0.125F, (slotSize - 2) / 16.0F));
                guiGraphics.pose().pushPose();
                try {
                    guiGraphics.pose().translate(slot.x + 1, slot.y + 1, 0);
                    guiGraphics.pose().scale(scale, scale, 1.0F);
                    guiGraphics.renderItem(slot.item, 0, 0);
                } finally {
                    guiGraphics.pose().popPose();
                }

                if (slot.item.getCount() > 1 && slotSize >= 12) {
                    String countText = String.valueOf(slot.item.getCount());
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 300);
                    guiGraphics.drawString(this.font, countText,
                            slot.x + slotSize - this.font.width(countText),
                            slot.y + slotSize - 8, 0xFFFFFF, true);
                    guiGraphics.pose().popPose();
                }

                RenderSystem.disableDepthTest();
            } catch (Exception e) {
                guiGraphics.fill(slot.x + 1, slot.y + 1,
                        slot.x + slotSize - 1, slot.y + slotSize - 1, GuiTheme.DANGER_SOFT);
            }
        }
    }

    private void renderRecipeList(GuiGraphics guiGraphics, int mouseX, int mouseY, int listTop, int listAreaX, int listRight) {
        if (filteredRecipes.isEmpty()) {
            String emptyKey = allRecipes.isEmpty()
                    ? "registerhelper.gui.recipe_selector.none"
                    : "registerhelper.gui.common.no_match";
            guiGraphics.drawCenteredString(this.font, GuiText.component(emptyKey),
                    listAreaX + (listRight - listAreaX) / 2, listTop + 50,
                    GuiTheme.TEXT_MUTED);
            return;
        }

        for (int i = 0; i < visibleRecipeCount && i + scrollOffset < filteredRecipes.size(); i++) {
            int recipeIndex = i + scrollOffset;
            RecipeEntry recipe = filteredRecipes.get(recipeIndex);

            int itemY = listTop + i * RECIPE_ITEM_HEIGHT;
            int itemX = listAreaX - 5;

            boolean isHovered = mouseX >= itemX && mouseX < listRight &&
                    mouseY >= itemY && mouseY < itemY + RECIPE_ITEM_HEIGHT;
            boolean isSelected = recipeIndex == selectedRecipeIndex;

            if (isSelected) {
                GuiTheme.drawRow(guiGraphics, itemX, itemY,
                        listRight - itemX, RECIPE_ITEM_HEIGHT, i, false, true);
            } else if (isHovered) {
                GuiTheme.drawRow(guiGraphics, itemX, itemY,
                        listRight - itemX, RECIPE_ITEM_HEIGHT, i, true, false);
            } else {
                GuiTheme.drawRow(guiGraphics, itemX, itemY,
                        listRight - itemX, RECIPE_ITEM_HEIGHT, i, false, false);
            }

            try {
                if (!recipe.resultItem.isEmpty()) {
                    RenderSystem.enableDepthTest();
                    guiGraphics.renderItem(recipe.resultItem, itemX + 2, itemY + 1);
                    RenderSystem.disableDepthTest();
                } else {
                    GuiTheme.drawSlot(guiGraphics, itemX + 2, itemY + 1, 16, 16, false);
                }
            } catch (Exception e) {
                guiGraphics.fill(itemX + 2, itemY + 1, itemX + 18, itemY + 17,
                        GuiTheme.DANGER_SOFT);
            }

            String displayText = GuiLayoutHelper.ellipsis(this.font,
                    recipe.recipeId.toString(), Math.max(1, listRight - itemX - 28));

            guiGraphics.drawString(this.font, displayText, itemX + 22, itemY + 2, GuiTheme.TEXT, false);

            String typeText = GuiLayoutHelper.ellipsis(this.font,
                    "[" + recipe.recipeType + "]", Math.max(1, listRight - itemX - 28));
            int typeColor = getTypeColor(recipe.recipeType);
            if (isSelected) typeColor = GuiTheme.SELECTED_EDGE;
            guiGraphics.drawString(this.font, typeText, itemX + 22, itemY + 12, typeColor, false);
        }

        if (filteredRecipes.size() > visibleRecipeCount) {
            GuiTheme.drawScrollbar(guiGraphics, currentScrollbar(listTop), mouseX, mouseY);
        }
    }

    private GuiLayoutHelper.Scrollbar currentScrollbar(int listTop) {
        return GuiLayoutHelper.scrollbar(new GuiLayoutHelper.Bounds(
                        leftPos + contentWidth - 45, listTop + 5, 3,
                        Math.max(1, visibleRecipeCount * RECIPE_ITEM_HEIGHT - 10)),
                filteredRecipes.size(), visibleRecipeCount, scrollOffset, 10);
    }

    private int getTypeColor(String recipeType) {
        if (recipeType.equals(GuiText.string("registerhelper.recipe_type.minecraft.crafting_shaped"))) {
            return GuiTheme.SUCCESS;
        }
        if (recipeType.equals(GuiText.string("registerhelper.recipe_type.minecraft.crafting_shapeless"))) {
            return GuiTheme.INFO;
        }
        if (recipeType.equals(GuiText.string("registerhelper.recipe_type.minecraft.smelting"))) {
            return GuiTheme.WARNING;
        }
        if (recipeType.equals(GuiText.string("registerhelper.recipe_type.minecraft.blasting"))) {
            return GuiTheme.DANGER;
        }
        if (recipeType.equals(GuiText.string("registerhelper.recipe_type.minecraft.smoking"))) {
            return GuiTheme.WARNING;
        }
        if (recipeType.equals(GuiText.string("registerhelper.recipe_type.minecraft.campfire"))) {
            return 0xFFB45E4C;
        }
        if (recipeType.equals(GuiText.string("registerhelper.recipe_type.avaritia.generic"))
                || recipeType.equals(GuiText.string("registerhelper.recipe_type.avaritia.shaped"))
                || recipeType.equals(GuiText.string("registerhelper.recipe_type.avaritia.shapeless"))) {
            return GuiTheme.SELECTED_EDGE;
        }
        return GuiTheme.TEXT_MUTED;
    }

    private boolean isCookingRecipeType(String recipeTypeKey) {
        String key = recipeTypeKey.toLowerCase(Locale.ROOT);
        return key.contains("smelting") || key.contains("blasting")
                || key.contains("smoking") || key.contains("campfire");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listTop = topPos + 105;
        int listBottom = listTop + visibleRecipeCount * RECIPE_ITEM_HEIGHT;
        int listAreaX = leftPos + recipeDetailWidth + 15;
        int listRight = leftPos + contentWidth - 50;

        if (button == 0) {
            GuiLayoutHelper.Scrollbar scrollbar = currentScrollbar(listTop);
            if (scrollbar.contains(mouseX, mouseY)) {
                draggingScrollbar = true;
                scrollbarGrabOffset = scrollbar.grabOffset(mouseY);
                scrollOffset = scrollbar.offsetForPointer(mouseY, scrollbarGrabOffset);
                return true;
            }
        }

        if (mouseX >= listAreaX && mouseX < listRight &&
                mouseY >= listTop && mouseY < listBottom && !filteredRecipes.isEmpty()) {

            int clickedIndex = (int) ((mouseY - listTop) / RECIPE_ITEM_HEIGHT) + scrollOffset;

            if (clickedIndex >= 0 && clickedIndex < filteredRecipes.size()) {
                if (selectedRecipeIndex == clickedIndex && button == 0) {
                    selectRecipe();
                    return true;
                } else {
                    selectedRecipeIndex = clickedIndex;
                    updateButtons();
                    parseSelectedRecipe(); // 解析选中的配方
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            scrollOffset = currentScrollbar(topPos + 105)
                    .offsetForPointer(mouseY, scrollbarGrabOffset);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) {
            scrollUp();
        } else {
            scrollDown();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 264 && !filteredRecipes.isEmpty()) { // DOWN
            if (selectedRecipeIndex < filteredRecipes.size() - 1) {
                selectedRecipeIndex++;
                if (selectedRecipeIndex >= scrollOffset + visibleRecipeCount) {
                    scrollDown();
                }
                updateButtons();
                parseSelectedRecipe();
            }
            return true;
        } else if (keyCode == 265 && !filteredRecipes.isEmpty()) { // UP
            if (selectedRecipeIndex > 0) {
                selectedRecipeIndex--;
                if (selectedRecipeIndex < scrollOffset) {
                    scrollUp();
                }
                updateButtons();
                parseSelectedRecipe();
            }
            return true;
        } else if (keyCode == 257 && selectedRecipeIndex >= 0) { // ENTER
            selectRecipe();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void parseSelectedRecipe() {
        currentRecipeSlots.clear();
        currentResultSlot = null;
        currentRecipeTypeDisplay = "";
        currentRecipeTypeKey = "";

        if (selectedRecipeIndex < 0 || selectedRecipeIndex >= filteredRecipes.size()) {
            return;
        }

        RecipeEntry entry = filteredRecipes.get(selectedRecipeIndex);
        Recipe<?> recipe = entry.recipe;

        // 检查是否是自定义配方（registerhelper命名空间且recipe为null）
        if (recipe == null && entry.recipeId.getNamespace().equals("registerhelper")) {
            // 处理自定义配方的预览
            parseCustomRecipe(entry);
            return;
        }

        if (recipe == null) {
            return;
        }

        try {
            String recipeTypeName = entry.recipeTypeKey;
            currentRecipeTypeDisplay = entry.recipeType;
            currentRecipeTypeKey = entry.recipeTypeKey;

            int detailX = leftPos + 10;
            int detailY = topPos + 60;
            int gridY = getPreviewGridStartY();

            // 设置结果槽位
            currentResultSlot = new SlotInfo(detailX + recipeDetailWidth - 50,
                    detailY + 20, entry.resultItem.copy());

            if (recipeTypeName.contains("crafting_shaped") || recipeTypeName.contains("shaped")) {
                parseShapedRecipe(recipe, detailX + 20, gridY);
            } else if (recipeTypeName.contains("crafting_shapeless") || recipeTypeName.contains("shapeless")) {
                parseShapelessRecipe(recipe, detailX + 20, gridY);
            } else if (recipeTypeName.contains("smelting") || recipeTypeName.contains("blasting") ||
                    recipeTypeName.contains("smoking") || recipeTypeName.contains("campfire")) {
                parseSmeltingRecipe(recipe, detailX + 20, gridY);
            } else if (recipeTypeName.contains("avaritia")) {
                if (recipeTypeName.contains("shaped")) {
                    parseAvaritiaShapedRecipe(recipe, detailX + 10, gridY);
                } else {
                    parseAvaritiaShapelessRecipe(recipe, detailX + 10, gridY);
                }
            } else {
                // 默认作为无序配方处理
                parseShapelessRecipe(recipe, detailX + 20, gridY);
            }

        } catch (Exception e) {
            LOGGER.warn("解析配方失败: {}", e.getMessage());
            currentRecipeTypeDisplay = GuiText.string("registerhelper.recipe_type.parse_failed");
        }
    }

    /**
     * 解析自定义配方（酿造台、铁砧等）
     */
    private void parseCustomRecipe(RecipeEntry entry) {
        try {
            currentRecipeTypeDisplay = entry.recipeType;
            currentRecipeTypeKey = entry.recipeTypeKey;

            int detailX = leftPos + 10;
            int detailY = topPos + 60;
            int gridY = getPreviewGridStartY();

            // 设置结果槽位
            currentResultSlot = new SlotInfo(detailX + recipeDetailWidth - 50,
                    detailY + 20, entry.resultItem.copy());

            // 读取JSON文件获取输入材料
            java.nio.file.Path jsonFile = getCustomRecipeJsonPath(entry.recipeId);

            if (jsonFile != null && java.nio.file.Files.exists(jsonFile)) {
                String content = java.nio.file.Files.readString(jsonFile);
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();

                if (entry.recipeId.getPath().contains("brewing")) {
                    // 酿造台配方：input + ingredient
                    parseCustomBrewingRecipe(json, detailX, gridY);
                } else if (entry.recipeId.getPath().contains("anvil")) {
                    // 铁砧配方：left + right
                    parseCustomAnvilRecipe(json, detailX, gridY);
                }
            } else {
                // 无法找到JSON文件，只显示输出
                LOGGER.warn("自定义配方JSON文件不存在: {}", jsonFile);
            }

        } catch (Exception e) {
            LOGGER.warn("解析自定义配方失败: {}", entry.recipeId, e);
            currentRecipeTypeDisplay = GuiText.string(
                    "registerhelper.gui.recipe_selector.type_details_failed", entry.recipeType);
        }
    }

    /**
     * 获取自定义配方的JSON文件路径
     */
    private java.nio.file.Path getCustomRecipeJsonPath(ResourceLocation recipeId) {
        // registerhelper:custom_brewing/custom_brew → custom_recipes/brewing/custom_brew.json
        String path = recipeId.getPath(); // custom_brewing/custom_brew
        if (path.startsWith("custom_")) {
            String[] parts = path.split("/", 2);
            if (parts.length == 2) {
                String category = parts[0].replace("custom_", ""); // brewing or anvil
                String filename = parts[1] + ".json"; // custom_brew.json

                return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                        .resolve("registerhelper/custom_recipes")
                        .resolve(category)
                        .resolve(filename);
            }
        }
        return null;
    }

    /**
     * 解析自定义酿造台配方
     */
    private void parseCustomBrewingRecipe(com.google.gson.JsonObject json, int detailX, int gridY) throws Exception {
        // 输入药水（底部左侧）
        if (json.has("input")) {
            ItemStack inputStack = parseJsonItemStack(json.get("input"));
            currentRecipeSlots.add(new SlotInfo(detailX + 20, gridY + 20, inputStack));
        }

        // 酿造材料（顶部中间）
        if (json.has("ingredient")) {
            ItemStack ingredientStack = parseJsonItemStack(json.get("ingredient"));
            currentRecipeSlots.add(new SlotInfo(detailX + 60, gridY, ingredientStack));
        }
    }

    /**
     * 解析自定义铁砧配方
     */
    private void parseCustomAnvilRecipe(com.google.gson.JsonObject json, int detailX, int gridY) throws Exception {
        // 左侧物品
        if (json.has("left")) {
            ItemStack leftStack = parseJsonItemStack(json.get("left"));
            currentRecipeSlots.add(new SlotInfo(detailX + 20, gridY, leftStack));
        }

        // 右侧物品
        if (json.has("right")) {
            ItemStack rightStack = parseJsonItemStack(json.get("right"));
            currentRecipeSlots.add(new SlotInfo(detailX + 80, gridY, rightStack));
        }
    }

    /**
     * 从JSON解析ItemStack
     */
    private ItemStack parseJsonItemStack(com.google.gson.JsonElement element) throws Exception {
        if (element.isJsonObject()) {
            com.google.gson.JsonObject obj = element.getAsJsonObject();
            if (obj.has("item")) {
                String itemId = obj.get("item").getAsString();
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(ResourceLocation.parse(itemId));
                if (item != null) {
                    int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
                    return new ItemStack(item, count);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private int getPreviewGridStartY() {
        return topPos + GuiLayoutHelper.clamp(contentHeight / 2, 105, 160);
    }

    private int getSlotSpacing(int gridSize) {
        int horizontalSpacing = Math.max(2, (recipeDetailWidth - 40) / gridSize);
        int previewInfoTop = topPos + contentHeight - 50;
        int availableHeight = Math.max(2,
                previewInfoTop - getPreviewGridStartY() - 4);
        int verticalSpacing = Math.max(2, availableHeight / gridSize);
        return Math.max(2, Math.min(SLOT_SPACING,
                Math.min(horizontalSpacing, verticalSpacing)));
    }

    private void parseShapedRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int gridSize = getGridSizeFromIngredientCount(ingredients.size());
        int slotSpacing = getSlotSpacing(gridSize);
        int slotSize = Math.min(SLOT_SIZE, slotSpacing);

        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                int index = y * gridSize + x;
                ItemStack item = ItemStack.EMPTY;

                if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                    ItemStack[] items = ingredients.get(index).getItems();
                    if (items.length > 0) {
                        item = items[0].copy();
                    }
                }

                currentRecipeSlots.add(new SlotInfo(
                        startX + x * slotSpacing,
                        startY + y * slotSpacing,
                        slotSize,
                        item
                ));
            }
        }
    }

    /**
     * 根据材料数量判断网格大小
     */
    private int getGridSizeFromIngredientCount(int ingredientCount) {
        if (ingredientCount <= 9) return 3;    // 3x3
        if (ingredientCount <= 25) return 5;   // 5x5
        if (ingredientCount <= 49) return 7;   // 7x7
        if (ingredientCount <= 81) return 9;   // 9x9
        if (ingredientCount <= 121) return 11;   // 11x11
        if (ingredientCount <= 256) return 16;   // 16x16
        return 21;
    }

    private void parseShapelessRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int ingredientCount = ingredients.size();

        // 动态计算显示网格大小 (3x3, 5x5, 7x7, 9x9...)
        int gridSize = getGridSizeFromIngredientCount(ingredientCount);
        int slotSpacing = getSlotSpacing(gridSize);
        int slotSize = Math.min(SLOT_SIZE, slotSpacing);

        // 计算每行能放几个，自动居中
        int cols = Math.min(ingredientCount, gridSize);
        int rows = (int) Math.ceil(ingredientCount / (double) gridSize);

        // 偏移量（让材料居中）
        int xOffset = (gridSize - cols) / 2;
        int yOffset = (gridSize - rows) / 2;

        for (int i = 0; i < ingredientCount; i++) {
            int col = i % gridSize;
            int row = i / gridSize;

            // 取 Ingredient
            ItemStack item = ItemStack.EMPTY;
            Ingredient ing = ingredients.get(i);
            if (!ing.isEmpty()) {
                ItemStack[] items = ing.getItems();
                if (items.length > 0) {
                    item = items[0].copy();
                }
            }

            // 计算实际渲染位置
            int drawX = startX + (col + xOffset) * slotSpacing;
            int drawY = startY + (row + yOffset) * slotSpacing;

            currentRecipeSlots.add(new SlotInfo(drawX, drawY, slotSize, item));
        }
    }

    private void parseSmeltingRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        ItemStack item = ItemStack.EMPTY;

        if (!ingredients.isEmpty() && !ingredients.get(0).isEmpty()) {
            ItemStack[] items = ingredients.get(0).getItems();
            if (items.length > 0) {
                item = items[0].copy();
            }
        }

        // 单个槽位用于熔炼原料
        currentRecipeSlots.add(new SlotInfo(startX, startY, item));
    }

    private void parseAvaritiaShapedRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int gridSize = getAvaritiaGridSizeFromIngredientCount(ingredients.size());

        // 缩小槽位间距以适应详情区域
        int slotSpacing = getSlotSpacing(gridSize);
        int slotSize = Math.min(SLOT_SIZE, slotSpacing);

        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                int index = y * gridSize + x;
                ItemStack item = ItemStack.EMPTY;

                if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                    ItemStack[] items = ingredients.get(index).getItems();
                    if (items.length > 0) {
                        item = items[0].copy();
                    }
                }

                currentRecipeSlots.add(new SlotInfo(
                        startX + x * slotSpacing,
                        startY + y * slotSpacing,
                        slotSize,
                        item
                ));
            }
        }
    }

    private void parseAvaritiaShapelessRecipe(Recipe<?> recipe, int startX, int startY) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int gridSize = getAvaritiaGridSizeFromIngredientCount(ingredients.size());

        // 缩小槽位间距以适应详情区域
        int slotSpacing = getSlotSpacing(gridSize);
        int slotSize = Math.min(SLOT_SIZE, slotSpacing);

        for (int i = 0; i < Math.min(ingredients.size(), gridSize * gridSize); i++) {
            int x = i % gridSize;
            int y = i / gridSize;
            ItemStack item = ItemStack.EMPTY;

            if (!ingredients.get(i).isEmpty()) {
                ItemStack[] items = ingredients.get(i).getItems();
                if (items.length > 0) {
                    item = items[0].copy();
                }
            }

            currentRecipeSlots.add(new SlotInfo(
                    startX + x * slotSpacing,
                    startY + y * slotSpacing,
                    slotSize,
                    item
            ));
        }
    }

    private int getAvaritiaGridSizeFromIngredientCount(int ingredientCount) {
        if (ingredientCount <= 9) return 3;
        if (ingredientCount <= 25) return 5;
        if (ingredientCount <= 49) return 7;
        return 9;
    }

    /**
     * @param recipe 添加原始配方引用
     */
    private record RecipeEntry(ResourceLocation recipeId, ItemStack resultItem,
                               String recipeType, String recipeTypeKey,
                               Recipe<?> recipe) {
    }

    private record SlotInfo(int x, int y, int size, ItemStack item) {
        private SlotInfo(int x, int y, ItemStack item) {
            this(x, y, SLOT_SIZE, item);
        }
    }
}
