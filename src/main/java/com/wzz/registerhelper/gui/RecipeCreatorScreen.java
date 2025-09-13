package com.wzz.registerhelper.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.network.CreateRecipePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class RecipeCreatorScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum RecipeType {
        SHAPED("有序配方", 9),
        SHAPELESS("无序配方", 9),
        SMELTING("熔炼配方", 1),
        AVARITIA_SHAPELESS("Avaritia无序工作台", 81),
        AVARITIA_SHAPED("Avaritia有序工作台", 81);


        private final String displayName;
        private final int maxInputs;

        RecipeType(String displayName, int maxInputs) {
            this.displayName = displayName;
            this.maxInputs = maxInputs;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getMaxInputs() {
            return maxInputs;
        }
    }

    public enum FillMode {
        NORMAL("普通模式"),
        BRUSH("画笔模式"),
        FILL("填充模式");

        private final String displayName;

        FillMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final int GUI_WIDTH = 520;
    private static final int GUI_HEIGHT = 450;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_SPACING = 20;

    private int leftPos, topPos;
    private RecipeType currentRecipeType = RecipeType.SHAPED;
    private int avaritiaTeir = 1;
    private FillMode fillMode = FillMode.NORMAL;
    private boolean isEditingExisting = false;

    private CycleButton<RecipeType> recipeTypeButton;
    private CycleButton<Integer> avaritiaTeierButton;
    private CycleButton<FillMode> fillModeButton;
    private EditBox resultCountBox;
    private EditBox smeltingTimeBox;
    private EditBox smeltingExpBox;
    private Button createButton;
    private Button cancelButton;
    private Button clearAllButton;
    private Button selectBrushItemButton;
    private Button editExistingRecipeButton;

    private ItemStack resultItem = ItemStack.EMPTY;
    private List<ItemStack> ingredients = new ArrayList<>();
    private ItemStack brushItem = ItemStack.EMPTY;
    private ResourceLocation editingRecipeId = null;

    private int currentSelectedSlot = -1;
    private boolean isSelectingResult = false;

    private List<IngredientSlot> ingredientSlots = new ArrayList<>();
    private IngredientSlot resultSlot;

    public RecipeCreatorScreen() {
        super(Component.literal("配方创建器"));
        resetToDefaultType();
    }

    public RecipeCreatorScreen(ResourceLocation recipeId) {
        super(Component.literal("配方编辑器"));
        this.editingRecipeId = recipeId;
        this.isEditingExisting = true;
        resetToDefaultType();
        loadExistingRecipe(recipeId);
    }

    private void resetToDefaultType() {
        this.currentRecipeType = RecipeType.SHAPED;
        this.avaritiaTeir = 1;
        this.fillMode = FillMode.NORMAL;
        initializeSlots();
    }

    private void initializeSlots() {
        List<ItemStack> oldIngredients = new ArrayList<>(ingredients);
        ingredientSlots.clear();
        ingredients.clear();

        switch (currentRecipeType) {
            case SHAPED -> initializeShapedSlots();
            case AVARITIA_SHAPED -> initializeAvaritiaSlots();
            case SHAPELESS -> initializeShapelessSlots();
            case AVARITIA_SHAPELESS -> initializeAvaritiaShapelessSlots();
            case SMELTING -> initializeSmeltingSlots();
        }
        for (int i = 0; i < ingredientSlots.size(); i++) {
            if (i < oldIngredients.size()) {
                ingredients.add(oldIngredients.get(i));
            } else {
                ingredients.add(ItemStack.EMPTY);
            }
        }
        resultSlot = new IngredientSlot(leftPos + 400, topPos + 100, -1);
    }

    private void initializeShapedSlots() {
        int startX = leftPos + 50;
        int startY = topPos + 120;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int slotX = startX + x * SLOT_SPACING;
                int slotY = startY + y * SLOT_SPACING;
                ingredientSlots.add(new IngredientSlot(slotX, slotY, y * 3 + x));
            }
        }
    }

    private void initializeAvaritiaSlots() {
        int gridSize = getAvaritiaGridSize();
        int gridWidth = gridSize * SLOT_SPACING;
        int startX = leftPos + (320 - gridWidth) / 2;
        int startY = topPos + 120;

        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                int slotX = startX + x * SLOT_SPACING;
                int slotY = startY + y * SLOT_SPACING;
                ingredientSlots.add(new IngredientSlot(slotX, slotY, y * gridSize + x));
            }
        }
    }

    private void initializeAvaritiaShapelessSlots() {
        int gridSize = getAvaritiaGridSize();
        int totalSlots = gridSize * gridSize;

        int cols = Math.min(9, gridSize); // 最多9列
        int rows = (int) Math.ceil((double) totalSlots / cols);

        int gridWidth = cols * SLOT_SPACING;
        int gridHeight = rows * SLOT_SPACING;
        int startX = leftPos + (320 - gridWidth) / 2;
        int startY = topPos + 120;

        for (int i = 0; i < totalSlots; i++) {
            int x = i % cols;
            int y = i / cols;
            int slotX = startX + x * SLOT_SPACING;
            int slotY = startY + y * SLOT_SPACING;
            ingredientSlots.add(new IngredientSlot(slotX, slotY, i));
        }
    }

    private void initializeShapelessSlots() {
        int startX = leftPos + 50;
        int startY = topPos + 120;

        for (int i = 0; i < 9; i++) {
            int x = i % 3;
            int y = i / 3;
            int slotX = startX + x * SLOT_SPACING;
            int slotY = startY + y * SLOT_SPACING;
            ingredientSlots.add(new IngredientSlot(slotX, slotY, i));
        }
    }

    private void initializeSmeltingSlots() {
        ingredientSlots.add(new IngredientSlot(leftPos + 80, topPos + 140, 0));
    }

    private int getAvaritiaGridSize() {
        return switch (avaritiaTeir) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            case 4 -> 9;
            default -> 3;
        };
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
        recipeTypeButton = addRenderableWidget(CycleButton.<RecipeType>builder(
                        type -> Component.literal(type.getDisplayName()))
                .withValues(RecipeType.values())
                .withInitialValue(currentRecipeType)
                .displayOnlyValue()
                .create(leftPos + 15, topPos + 35, 120, 20,
                        Component.literal("配方类型"), this::onRecipeTypeChanged));
        avaritiaTeierButton = addRenderableWidget(CycleButton.<Integer>builder(
                        tier -> Component.literal("等级 " + tier))
                .withValues(1, 2, 3, 4)
                .withInitialValue(avaritiaTeir)
                .displayOnlyValue()
                .create(leftPos + 145, topPos + 35, 60, 20,
                        Component.literal("工作台等级"), this::onAvaritiaTeierChanged));
        fillModeButton = addRenderableWidget(CycleButton.<FillMode>builder(
                        mode -> Component.literal(mode.getDisplayName()))
                .withValues(FillMode.values())
                .withInitialValue(fillMode)
                .displayOnlyValue()
                .create(leftPos + 215, topPos + 35, 80, 20,
                        Component.literal("填充模式"), this::onFillModeChanged));
        int rightPanelX = leftPos + 370;
        resultCountBox = new EditBox(this.font, rightPanelX + 70, topPos + 130, 40, 20,
                Component.literal("数量"));
        resultCountBox.setValue("1");
        resultCountBox.setFilter(text -> text.matches("\\d*") &&
                (text.isEmpty() || Integer.parseInt(text) <= 64));
        addWidget(resultCountBox);
        smeltingTimeBox = new EditBox(this.font, rightPanelX + 70, topPos + 160, 60, 20,
                Component.literal("熔炼时间"));
        smeltingTimeBox.setValue("200");
        smeltingTimeBox.setFilter(text -> text.matches("\\d*") &&
                (text.isEmpty() || Integer.parseInt(text) <= 32000));
        addWidget(smeltingTimeBox);
        smeltingExpBox = new EditBox(this.font, rightPanelX + 70, topPos + 190, 60, 20,
                Component.literal("熔炼经验"));
        smeltingExpBox.setValue("0.1");
        smeltingExpBox.setFilter(text -> text.matches("\\d*\\.?\\d*"));
        addWidget(smeltingExpBox);
        selectBrushItemButton = addRenderableWidget(Button.builder(
                        Component.literal("选择画笔物品"),
                        button -> openItemSelector(this::setBrushItem))
                .bounds(leftPos + 15, topPos + 65, 120, 20)
                .build());
        editExistingRecipeButton = addRenderableWidget(Button.builder(
                        Component.literal("编辑现有配方"),
                        button -> openRecipeSelector())
                .bounds(leftPos + 300, topPos + 35, 100, 20)
                .build());
        clearAllButton = addRenderableWidget(Button.builder(
                        Component.literal("清空材料"),
                        button -> clearAllIngredients())
                .bounds(rightPanelX, topPos + 250, 80, 20)
                .build());
        createButton = addRenderableWidget(Button.builder(
                        Component.literal(isEditingExisting ? "更新配方" : "创建配方"),
                        button -> createRecipe())
                .bounds(leftPos + 180, topPos + 410, 80, 20)
                .build());

        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"),
                        button -> onClose())
                .bounds(leftPos + 270, topPos + 410, 50, 20)
                .build());

        initializeSlots();
        updateVisibility();
    }

    private void clearAllIngredients() {
        Collections.fill(ingredients, ItemStack.EMPTY);
        resultItem = ItemStack.EMPTY;
        editingRecipeId = null;
        isEditingExisting = false;
        createButton.setMessage(Component.literal("创建配方"));
    }

    private void updateVisibility() {
        if (avaritiaTeierButton != null) {
            avaritiaTeierButton.visible = (currentRecipeType == RecipeType.AVARITIA_SHAPED ||
                    currentRecipeType == RecipeType.AVARITIA_SHAPELESS);
        }
        if (fillModeButton != null) {
            fillModeButton.visible = (currentRecipeType == RecipeType.AVARITIA_SHAPED ||
                    currentRecipeType == RecipeType.AVARITIA_SHAPELESS);
        }
        if (selectBrushItemButton != null) {
            selectBrushItemButton.visible = ((currentRecipeType == RecipeType.AVARITIA_SHAPED ||
                    currentRecipeType == RecipeType.AVARITIA_SHAPELESS) &&
                    (fillMode == FillMode.BRUSH || fillMode == FillMode.FILL));
        }
        if (smeltingTimeBox != null) {
            smeltingTimeBox.visible = (currentRecipeType == RecipeType.SMELTING);
            if (smeltingTimeBox.visible) {
                smeltingTimeBox.setFocused(false);
            }
        }
        if (smeltingExpBox != null) {
            smeltingExpBox.visible = (currentRecipeType == RecipeType.SMELTING);
            if (smeltingExpBox.visible) {
                smeltingExpBox.setFocused(false);
            }
        }
    }

    private void openRecipeSelector() {
        if (minecraft != null) {
            minecraft.setScreen(new RecipeSelectorScreen(this, this::loadSelectedRecipe));
        }
    }

    private void loadSelectedRecipe(ResourceLocation recipeId) {
        this.editingRecipeId = recipeId;
        this.isEditingExisting = true;
        createButton.setMessage(Component.literal("更新配方"));
        loadExistingRecipe(recipeId);
        displaySuccess("已载入配方: " + recipeId.toString());
    }

    private void loadExistingRecipe(ResourceLocation recipeId) {
        try {
            if (minecraft == null || minecraft.level == null) {
                displayError("无法获取配方数据");
                return;
            }

            var recipeManager = minecraft.level.getRecipeManager();
            var recipe = recipeManager.byKey(recipeId).orElse(null);

            if (recipe == null) {
                displayError("找不到配方: " + recipeId);
                return;
            }

            Collections.fill(ingredients, ItemStack.EMPTY);
            resultItem = ItemStack.EMPTY;

            resultItem = recipe.getResultItem(minecraft.level.registryAccess()).copy();
            if (resultCountBox != null) {
                resultCountBox.setValue(String.valueOf(resultItem.getCount()));
            }

            String recipeTypeName = recipe.getType().toString().toLowerCase();

            if (recipeTypeName.contains("crafting_shaped")) {
                loadShapedRecipe(recipe);
            } else if (recipeTypeName.contains("crafting_shapeless")) {
                loadShapelessRecipe(recipe);
            } else if (recipeTypeName.contains("smelting")) {
                loadSmeltingRecipe(recipe);
            } else if (recipeTypeName.contains("avaritia")) {
                if (recipeTypeName.contains("shaped")) {
                    loadAvaritiaShapedRecipe(recipe);
                } else {
                    loadAvaritiaShapelessRecipe(recipe);
                }
            }

            displayInfo("成功载入配方: " + recipeId.toString());

        } catch (Exception e) {
            LOGGER.error("加载配方失败", e);
            displayError("加载配方失败: " + e.getMessage());
        }
    }

    // 其余方法保持不变...
    private void loadShapedRecipe(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        currentRecipeType = RecipeType.SHAPED;
        recipeTypeButton.setValue(currentRecipeType);
        initializeSlots();

        try {
            var ingredients = recipe.getIngredients();
            for (int i = 0; i < Math.min(ingredients.size(), this.ingredients.size()); i++) {
                var ingredient = ingredients.get(i);
                if (!ingredient.isEmpty()) {
                    var items = ingredient.getItems();
                    if (items.length > 0) {
                        this.ingredients.set(i, items[0].copy());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("解析有序配方失败", e);
        }

        updateVisibility();
    }

    private void loadShapelessRecipe(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        currentRecipeType = RecipeType.SHAPELESS;
        recipeTypeButton.setValue(currentRecipeType);
        initializeSlots();

        try {
            var ingredients = recipe.getIngredients();
            for (int i = 0; i < Math.min(ingredients.size(), this.ingredients.size()); i++) {
                var ingredient = ingredients.get(i);
                if (!ingredient.isEmpty()) {
                    var items = ingredient.getItems();
                    if (items.length > 0) {
                        this.ingredients.set(i, items[0].copy());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("解析无序配方失败", e);
        }

        updateVisibility();
    }

    private void loadSmeltingRecipe(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        currentRecipeType = RecipeType.SMELTING;
        recipeTypeButton.setValue(currentRecipeType);
        initializeSlots();

        try {
            var ingredients = recipe.getIngredients();
            if (!ingredients.isEmpty()) {
                var ingredient = ingredients.get(0);
                if (!ingredient.isEmpty()) {
                    var items = ingredient.getItems();
                    if (items.length > 0) {
                        this.ingredients.set(0, items[0].copy());
                    }
                }
            }

            if (smeltingTimeBox != null) {
                smeltingTimeBox.setValue("200");
            }
            if (smeltingExpBox != null) {
                smeltingExpBox.setValue("0.1");
            }

        } catch (Exception e) {
            LOGGER.warn("解析熔炼配方失败", e);
        }

        updateVisibility();
    }

    private void loadAvaritiaShapedRecipe(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        currentRecipeType = RecipeType.AVARITIA_SHAPED;
        recipeTypeButton.setValue(currentRecipeType);

        var ingredients = recipe.getIngredients();
        int ingredientCount = ingredients.size();
        if (ingredientCount <= 9) {
            avaritiaTeir = 1;
        } else if (ingredientCount <= 25) {
            avaritiaTeir = 2;
        } else if (ingredientCount <= 49) {
            avaritiaTeir = 3;
        } else {
            avaritiaTeir = 4;
        }

        avaritiaTeierButton.setValue(avaritiaTeir);
        initializeSlots();

        try {
            for (int i = 0; i < Math.min(ingredients.size(), this.ingredients.size()); i++) {
                var ingredient = ingredients.get(i);
                if (!ingredient.isEmpty()) {
                    var items = ingredient.getItems();
                    if (items.length > 0) {
                        this.ingredients.set(i, items[0].copy());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("解析Avaritia有序配方失败", e);
        }

        updateVisibility();
    }

    private void loadAvaritiaShapelessRecipe(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        currentRecipeType = RecipeType.AVARITIA_SHAPELESS;
        recipeTypeButton.setValue(currentRecipeType);

        var ingredients = recipe.getIngredients();
        int ingredientCount = ingredients.size();
        if (ingredientCount <= 9) {
            avaritiaTeir = 1;
        } else if (ingredientCount <= 25) {
            avaritiaTeir = 2;
        } else if (ingredientCount <= 49) {
            avaritiaTeir = 3;
        } else {
            avaritiaTeir = 4;
        }

        avaritiaTeierButton.setValue(avaritiaTeir);
        initializeSlots();

        try {
            for (int i = 0; i < Math.min(ingredients.size(), this.ingredients.size()); i++) {
                var ingredient = ingredients.get(i);
                if (!ingredient.isEmpty()) {
                    var items = ingredient.getItems();
                    if (items.length > 0) {
                        this.ingredients.set(i, items[0].copy());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("解析Avaritia无序配方失败", e);
        }

        updateVisibility();
    }

    private void onRecipeTypeChanged(CycleButton<RecipeType> button, RecipeType newType) {
        this.currentRecipeType = newType;
        initializeSlots();
        updateVisibility();
    }

    private void onAvaritiaTeierChanged(CycleButton<Integer> button, Integer newTier) {
        this.avaritiaTeir = newTier;
        if (currentRecipeType == RecipeType.AVARITIA_SHAPED ||
                currentRecipeType == RecipeType.AVARITIA_SHAPELESS) {
            initializeSlots();
        }
    }

    private void onFillModeChanged(CycleButton<FillMode> button, FillMode newMode) {
        this.fillMode = newMode;
        updateVisibility();
    }

    private void openItemSelector(java.util.function.Consumer<ItemStack> callback) {
        if (minecraft != null) {
            minecraft.setScreen(new ItemSelectorScreen(this, callback));
        }
    }

    private void setBrushItem(ItemStack item) {
        this.brushItem = item;
    }

    private void setResultItem(ItemStack item) {
        this.resultItem = item;
        isSelectingResult = false;
    }

    private void setIngredientItem(ItemStack item) {
        if (currentSelectedSlot >= 0 && currentSelectedSlot < ingredients.size()) {
            ingredients.set(currentSelectedSlot, item);
        }
        currentSelectedSlot = -1;
    }

    private void handleSlotClick(int slotIndex, boolean isRightClick) {
        if (slotIndex < 0 || slotIndex >= ingredients.size()) return;

        if (isRightClick) {
            ingredients.set(slotIndex, ItemStack.EMPTY);
            return;
        }

        switch (fillMode) {
            case NORMAL:
                currentSelectedSlot = slotIndex;
                openItemSelector(this::setIngredientItem);
                break;
            case BRUSH:
                if (!brushItem.isEmpty()) {
                    ingredients.set(slotIndex, brushItem.copy());
                } else {
                    displayError("请先选择画笔物品！");
                }
                break;
            case FILL:
                if (!brushItem.isEmpty()) {
                    fillEmptySlots(brushItem.copy());
                } else {
                    displayError("请先选择画笔物品！");
                }
                break;
        }
    }

    private void fillEmptySlots(ItemStack item) {
        for (int i = 0; i < ingredients.size(); i++) {
            if (ingredients.get(i).isEmpty()) {
                ingredients.set(i, item.copy());
            }
        }
    }

    private void createRecipe() {
        if (resultItem.isEmpty()) {
            displayError("请选择结果物品！");
            return;
        }

        try {
            int count = Integer.parseInt(resultCountBox.getValue());
            if (count <= 0) {
                displayError("数量必须大于0！");
                return;
            }

            ItemStack result = new ItemStack(resultItem.getItem(), count);

            switch (currentRecipeType) {
                case SHAPED -> createShapedRecipe(result);
                case SHAPELESS -> createShapelessRecipe(result);
                case SMELTING -> createSmeltingRecipe(result);
                case AVARITIA_SHAPED -> createAvaritiaRecipe(result);
                case AVARITIA_SHAPELESS -> createAvaritiaShapelessRecipe(result); // 修复：添加Avaritia无序配方创建
            }

        } catch (NumberFormatException e) {
            displayError("请输入有效的数量！");
        }
    }

    private void createShapedRecipe(ItemStack result) {
        boolean hasIngredients = ingredients.stream().anyMatch(item -> !item.isEmpty());
        if (!hasIngredients) {
            displayError("请至少添加一个材料！");
            return;
        }

        String[] pattern = new String[3];
        Map<ItemStack, Character> itemToSymbol = new HashMap<>();
        char currentSymbol = 'A';

        for (int y = 0; y < 3; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < 3; x++) {
                ItemStack ingredient = ingredients.get(y * 3 + x);
                if (ingredient.isEmpty()) {
                    row.append(' ');
                } else {
                    Character symbol = itemToSymbol.get(ingredient);
                    if (symbol == null) {
                        symbol = currentSymbol++;
                        itemToSymbol.put(ingredient, symbol);
                    }
                    row.append(symbol);
                }
            }
            pattern[y] = row.toString();
        }

        Object[] mappings = new Object[itemToSymbol.size() * 2];
        int index = 0;
        for (Map.Entry<ItemStack, Character> entry : itemToSymbol.entrySet()) {
            mappings[index++] = entry.getValue();
            mappings[index++] = entry.getKey().getItem();
        }

        ModNetwork.CHANNEL.sendToServer(new CreateRecipePacket(
                CreateRecipePacket.RecipeType.SHAPED,
                result, pattern, mappings, 0, editingRecipeId));

        displaySuccess(isEditingExisting ? "有形状配方更新成功！" : "有形状配方创建成功！");
        onClose();
    }

    private void createShapelessRecipe(ItemStack result) {
        List<ItemStack> validIngredients = ingredients.stream()
                .filter(item -> !item.isEmpty())
                .toList();

        if (validIngredients.isEmpty()) {
            displayError("请至少添加一个材料！");
            return;
        }

        Object[] ingredientItems = validIngredients.stream()
                .map(ItemStack::getItem)
                .toArray();

        ModNetwork.CHANNEL.sendToServer(new CreateRecipePacket(
                CreateRecipePacket.RecipeType.SHAPELESS,
                result, null, ingredientItems, 0, editingRecipeId));

        displaySuccess(isEditingExisting ? "无形状配方更新成功！" : "无形状配方创建成功！");
        onClose();
    }

    private void createSmeltingRecipe(ItemStack result) {
        if (ingredients.isEmpty() || ingredients.get(0).isEmpty()) {
            displayError("请选择熔炼原料！");
            return;
        }

        try {
            int cookingTime = Integer.parseInt(smeltingTimeBox.getValue());
            float experience = Float.parseFloat(smeltingExpBox.getValue());

            if (cookingTime <= 0) {
                displayError("熔炼时间必须大于0！");
                return;
            }

            Object[] smeltingData = {ingredients.get(0).getItem(), experience, cookingTime};

            ModNetwork.CHANNEL.sendToServer(new CreateRecipePacket(
                    CreateRecipePacket.RecipeType.SMELTING,
                    result, null, smeltingData, 0, editingRecipeId));

            displaySuccess(isEditingExisting ? "熔炼配方更新成功！" : "熔炼配方创建成功！");
            onClose();
        } catch (NumberFormatException e) {
            displayError("请输入有效的时间和经验值！");
        }
    }

    private void createAvaritiaRecipe(ItemStack result) {
        boolean hasIngredients = ingredients.stream().anyMatch(item -> !item.isEmpty());
        if (!hasIngredients) {
            displayError("请至少添加一个材料！");
            return;
        }

        int gridSize = getAvaritiaGridSize();
        String[] pattern = new String[gridSize];
        Map<ItemStack, Character> itemToSymbol = new HashMap<>();
        char currentSymbol = 'A';

        for (int y = 0; y < gridSize; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < gridSize; x++) {
                int index = y * gridSize + x;
                ItemStack ingredient = index < ingredients.size() ? ingredients.get(index) : ItemStack.EMPTY;

                if (ingredient.isEmpty()) {
                    row.append(' ');
                } else {
                    Character symbol = itemToSymbol.get(ingredient);
                    if (symbol == null) {
                        symbol = currentSymbol++;
                        itemToSymbol.put(ingredient, symbol);
                    }
                    row.append(symbol);
                }
            }
            pattern[y] = row.toString();
        }

        Object[] mappings = new Object[itemToSymbol.size() * 2];
        int index = 0;
        for (Map.Entry<ItemStack, Character> entry : itemToSymbol.entrySet()) {
            mappings[index++] = entry.getValue();
            mappings[index++] = entry.getKey().getItem();
        }

        ModNetwork.CHANNEL.sendToServer(new CreateRecipePacket(
                CreateRecipePacket.RecipeType.AVARITIA_SHAPED,
                result, pattern, mappings, avaritiaTeir, editingRecipeId));

        displaySuccess(isEditingExisting ? "Avaritia工作台配方更新成功！" : "Avaritia工作台配方创建成功！");
        onClose();
    }

    private void createAvaritiaShapelessRecipe(ItemStack result) {
        List<ItemStack> validIngredients = ingredients.stream()
                .filter(item -> !item.isEmpty())
                .toList();

        if (validIngredients.isEmpty()) {
            displayError("请至少添加一个材料！");
            return;
        }

        Object[] ingredientItems = validIngredients.stream()
                .map(ItemStack::getItem)
                .toArray();

        ModNetwork.CHANNEL.sendToServer(new CreateRecipePacket(
                CreateRecipePacket.RecipeType.AVARITIA_SHAPELESS,
                result, null, ingredientItems, avaritiaTeir, editingRecipeId));

        displaySuccess(isEditingExisting ? "Avaritia无形状配方更新成功！" : "Avaritia无形状配方创建成功！");
        onClose();
    }

    private void displayError(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§c" + message));
        }
    }

    private void displaySuccess(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§a" + message));
        }
    }

    private void displayInfo(String message) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§e" + message));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        // GUI背景
        guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xFFC6C6C6);
        guiGraphics.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF8B8B8B);

        // 标题
        String titleText = isEditingExisting ?
                (editingRecipeId != null ? "配方编辑器 - " + editingRecipeId.toString() : "配方编辑器")
                : "配方创建器";
        guiGraphics.drawCenteredString(this.font, titleText, this.width / 2, topPos + 15, 0x404040);

        // 标签
        guiGraphics.drawString(this.font, "配方类型:", leftPos + 15, topPos + 25, 0x404040, false);

        if (currentRecipeType == RecipeType.AVARITIA_SHAPED || currentRecipeType == RecipeType.AVARITIA_SHAPELESS) {
            guiGraphics.drawString(this.font, "等级:", leftPos + 145, topPos + 25, 0x404040, false);
            guiGraphics.drawString(this.font, "模式:", leftPos + 215, topPos + 25, 0x404040, false);
        }

        int rightPanelX = leftPos + 370;
        guiGraphics.drawString(this.font, "结果:", rightPanelX, topPos + 80, 0x404040, false);
        guiGraphics.drawString(this.font, "数量:", rightPanelX, topPos + 130, 0x404040, false);

        // 熔炼配方专用标签
        if (currentRecipeType == RecipeType.SMELTING) {
            guiGraphics.drawString(this.font, "时间(tick):", rightPanelX, topPos + 150, 0x404040, false);
            guiGraphics.drawString(this.font, "经验值:", rightPanelX, topPos + 180, 0x404040, false);
        }

        // 分隔线
        guiGraphics.fill(rightPanelX - 10, topPos + 20, rightPanelX - 8, topPos + GUI_HEIGHT - 40, 0xFF606060);

        // 渲染槽位
        renderIngredientSlots(guiGraphics, mouseX, mouseY);
        renderResultSlot(guiGraphics, mouseX, mouseY);

        // 渲染填充模式提示
        if (currentRecipeType == RecipeType.AVARITIA_SHAPED || currentRecipeType == RecipeType.AVARITIA_SHAPELESS) {
            renderFillModeHint(guiGraphics);
        }

        // 渲染编辑模式提示
        if (isEditingExisting) {
            guiGraphics.drawString(this.font, "§6编辑模式", leftPos + 15, topPos + 390, 0xFFCC00, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltips(guiGraphics, mouseX, mouseY);
    }

    private void renderIngredientSlots(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int i = 0; i < ingredientSlots.size(); i++) {
            IngredientSlot slot = ingredientSlots.get(i);
            renderSlot(guiGraphics, slot, mouseX, mouseY, i < ingredients.size() ? ingredients.get(i) : ItemStack.EMPTY);
        }
    }

    private void renderResultSlot(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        renderSlot(guiGraphics, resultSlot, mouseX, mouseY, resultItem);
    }

    private void renderSlot(GuiGraphics guiGraphics, IngredientSlot slot, int mouseX, int mouseY, ItemStack item) {
        boolean isMouseOver = mouseX >= slot.x && mouseX < slot.x + SLOT_SIZE &&
                mouseY >= slot.y && mouseY < slot.y + SLOT_SIZE;

        int bgColor = isMouseOver ? 0x80FFFFFF : 0xFF373737;
        guiGraphics.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, bgColor);

        guiGraphics.fill(slot.x - 1, slot.y - 1, slot.x + SLOT_SIZE + 1, slot.y, 0xFF000000);
        guiGraphics.fill(slot.x - 1, slot.y + SLOT_SIZE, slot.x + SLOT_SIZE + 1, slot.y + SLOT_SIZE + 1, 0xFF000000);
        guiGraphics.fill(slot.x - 1, slot.y, slot.x, slot.y + SLOT_SIZE, 0xFF000000);
        guiGraphics.fill(slot.x + SLOT_SIZE, slot.y, slot.x + SLOT_SIZE + 1, slot.y + SLOT_SIZE, 0xFF000000);

        if (!item.isEmpty()) {
            RenderSystem.enableDepthTest();
            guiGraphics.renderItem(item, slot.x + 1, slot.y + 1);
            RenderSystem.disableDepthTest();
        }
    }

    private void renderFillModeHint(GuiGraphics guiGraphics) {
        String hint = switch (fillMode) {
            case NORMAL -> "普通模式: 点击槽位选择物品";
            case BRUSH -> "画笔模式: 点击槽位填充画笔物品";
            case FILL -> "填充模式: 点击任意槽位填充所有空槽";
        };

        if (fillMode != FillMode.NORMAL && !brushItem.isEmpty()) {
            hint += " [当前画笔: " + brushItem.getHoverName().getString() + "]";
        }

        guiGraphics.drawString(this.font, hint, leftPos + 15, topPos + 95, 0x666666, false);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int i = 0; i < ingredientSlots.size(); i++) {
            IngredientSlot slot = ingredientSlots.get(i);
            if (mouseX >= slot.x && mouseX < slot.x + SLOT_SIZE &&
                    mouseY >= slot.y && mouseY < slot.y + SLOT_SIZE) {

                if (i < ingredients.size() && !ingredients.get(i).isEmpty()) {
                    guiGraphics.renderTooltip(this.font, ingredients.get(i), mouseX, mouseY);
                } else {
                    String tooltip = switch (fillMode) {
                        case NORMAL -> "左键: 选择物品\n右键: 清空";
                        case BRUSH -> "左键: 填充画笔物品\n右键: 清空";
                        case FILL -> "左键: 填充所有空槽\n右键: 清空";
                    };
                    guiGraphics.renderTooltip(this.font, Component.literal(tooltip), mouseX, mouseY);
                }
                return;
            }
        }

        if (mouseX >= resultSlot.x && mouseX < resultSlot.x + SLOT_SIZE &&
                mouseY >= resultSlot.y && mouseY < resultSlot.y + SLOT_SIZE) {
            if (!resultItem.isEmpty()) {
                guiGraphics.renderTooltip(this.font, resultItem, mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(this.font, Component.literal("点击选择结果物品"), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= resultSlot.x && mouseX < resultSlot.x + SLOT_SIZE &&
                mouseY >= resultSlot.y && mouseY < resultSlot.y + SLOT_SIZE) {
            if (button == 0) {
                isSelectingResult = true;
                openItemSelector(this::setResultItem);
            } else if (button == 1) {
                resultItem = ItemStack.EMPTY;
            }
            return true;
        }

        for (int i = 0; i < ingredientSlots.size(); i++) {
            IngredientSlot slot = ingredientSlots.get(i);
            if (mouseX >= slot.x && mouseX < slot.x + SLOT_SIZE &&
                    mouseY >= slot.y && mouseY < slot.y + SLOT_SIZE) {
                handleSlotClick(i, button == 1);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class IngredientSlot {
        public final int x, y;
        public final int index;

        public IngredientSlot(int x, int y, int index) {
            this.x = x;
            this.y = y;
            this.index = index;
        }
    }
}