package com.wzz.registerhelper.gui.recipe.dynamic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.gui.recipe.IngredientData;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.network.CreateRecipeJsonPacket;
import com.wzz.registerhelper.network.CreateRecipePacket;
import com.wzz.registerhelper.generator.RecipeGenerator;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;
import com.wzz.registerhelper.recipe.RecipeJsonBuilder;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.tags.CustomTagManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 动态配方构建器
 * 支持所有已注册的配方类型，通过ModRecipeProcessor进行处理
 */
public class DynamicRecipeBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final Consumer<String> successCallback;
    private final Consumer<String> errorCallback;

    public DynamicRecipeBuilder(Consumer<String> successCallback, Consumer<String> errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;
    }

    /**
     * 动态配方构建参数
     */
    public static class BuildParams {
        public final DynamicRecipeTypeConfig.RecipeTypeDefinition recipeType;
        public final String craftingMode;
        public final String cookingType;
        public final int customTier;
        public final ItemStack resultItem;
        public final List<ItemStack> ingredients; // 保留用于兼容
        public final List<IngredientData> ingredientsData; // 新增：完整的材料数据
        public final float cookingTime;
        public final float cookingExp;
        public final ResourceLocation editingRecipeId;
        public final boolean isEditing;
        public final Map<String, Object> extraProperties;

        public BuildParams(RecipeTypeDefinition recipeType, String craftingMode, String cookingType,
                           int customTier, ItemStack resultItem, List<ItemStack> ingredients,
                           List<IngredientData> ingredientsData,
                           float cookingTime, float cookingExp, ResourceLocation editingRecipeId,
                           boolean isEditing, Map<String, Object> extraProperties) {
            this.recipeType = recipeType;
            this.craftingMode = craftingMode;
            this.cookingType = cookingType;
            this.customTier = customTier;
            this.resultItem = resultItem;
            this.ingredients = new ArrayList<>(ingredients);
            this.ingredientsData = ingredientsData != null ? new ArrayList<>(ingredientsData) : null;
            this.cookingTime = cookingTime;
            this.cookingExp = cookingExp;
            this.editingRecipeId = editingRecipeId;
            this.isEditing = isEditing;
            this.extraProperties = extraProperties != null ? new HashMap<>(extraProperties) : new HashMap<>();
        }

        public BuildParams(RecipeTypeDefinition recipeType, String craftingMode, String cookingType,
                           int customTier, ItemStack resultItem, List<ItemStack> ingredients,
                           float cookingTime, float cookingExp, ResourceLocation editingRecipeId,
                           boolean isEditing, Map<String, Object> extraProperties) {
            this(recipeType, craftingMode, cookingType, customTier, resultItem, ingredients, null,
                    cookingTime, cookingExp, editingRecipeId, isEditing, extraProperties);
        }
    }

    /**
     * 构建配方（主入口）
     */
    public void buildRecipe(BuildParams params) {
        if (!validateParams(params)) {
            return;
        }

        try {
            JsonObject recipeJson = generateRecipeJson(params);

            if (recipeJson == null) {
                showError("无法生成配方JSON");
                return;
            }

            saveCustomTags(params);

            String recipeId = params.isEditing && params.editingRecipeId != null ?
                    params.editingRecipeId.toString() : generateRecipeIdPath(params);

            boolean isOverride = isOverrideMode(params);

            Gson GSON = new Gson();
            String jsonString = GSON.toJson(recipeJson);
            CreateRecipeJsonPacket packet = new CreateRecipeJsonPacket(recipeId, jsonString, isOverride);
            ModNetwork.CHANNEL.sendToServer(packet);

            String action = params.isEditing ? "更新" : "创建";
            String method = isOverride ? "覆盖" : "创建";
            showSuccess(action + "配方成功！类型: " + params.recipeType.getDisplayName() +
                    " (" + method + "模式) 使用 /reload 刷新配方");

        } catch (Exception e) {
            LOGGER.error("构建配方失败", e);
            showError("处理配方时发生错误: " + e.getMessage());
        }
    }

    /**
     * 保存自定义标签
     */
    private void saveCustomTags(BuildParams params) {
        if (params.ingredientsData != null) {
            for (IngredientData data : params.ingredientsData) {
                if (data.getType() == IngredientData.Type.CUSTOM_TAG) {
                    if (!CustomTagManager.hasTag(data.getTagId())) {
                        CustomTagManager.registerTag(data.getTagId(), data.getCustomTagItems());
                        LOGGER.info("已注册自定义标签: {}", data.getTagId());
                    }
                }
            }
        }
    }

    /**
     * 生成配方JSON（核心方法，支持标签和NBT）
     */
    private JsonObject generateRecipeJson(BuildParams params) {
        // 获取IngredientData列表
        List<IngredientData> dataList = params.ingredientsData != null ?
                params.ingredientsData : convertItemStacksToIngredientData(params.ingredients);

        String category = params.recipeType.getProperty("category", String.class);

        if ("crafting".equals(category)) {
            // 工作台配方
            if ("shaped".equals(params.craftingMode)) {
                return RecipeJsonBuilder.createShapedRecipe(
                        dataList, params.resultItem, 3, 3);
            } else {
                return RecipeJsonBuilder.createShapelessRecipe(
                        dataList, params.resultItem);
            }
        } else if ("avaritia".equals(category)) {
            // Avaritia配方
            return RecipeJsonBuilder.createAvaritiaRecipe(
                    params.craftingMode, dataList, params.resultItem, params.customTier);
        } else if ("cooking".equals(category) || params.recipeType.supportsCookingSettings()) {
            // 烹饪配方
            IngredientData ingredient = dataList.stream()
                    .filter(data -> !data.isEmpty())
                    .findFirst()
                    .orElse(null);

            if (ingredient != null) {
                return RecipeJsonBuilder.createCookingRecipe(
                        params.cookingType, ingredient, params.resultItem,
                        params.cookingExp, (int) params.cookingTime);
            }
        } else if ("stonecutting".equals(category)) {
            // 切石机配方
            IngredientData ingredient = dataList.stream()
                    .filter(data -> !data.isEmpty())
                    .findFirst()
                    .orElse(null);

            if (ingredient != null) {
                JsonObject recipe = new JsonObject();
                recipe.addProperty("type", "minecraft:stonecutting");
                recipe.add("ingredient", createIngredientJsonObject(ingredient));
                recipe.addProperty("result",
                        ForgeRegistries.ITEMS.getKey(params.resultItem.getItem()).toString());
                recipe.addProperty("count", params.resultItem.getCount());
                return recipe;
            }
        } else if ("smithing".equals(category) || "smithing_transform".equals(category)) {
            // 锻造台配方
            if (dataList.size() >= 3) {
                JsonObject recipe = new JsonObject();
                recipe.addProperty("type", "minecraft:smithing_transform");
                recipe.add("template", createIngredientJsonObject(dataList.get(0)));
                recipe.add("base", createIngredientJsonObject(dataList.get(1)));
                recipe.add("addition", createIngredientJsonObject(dataList.get(2)));

                JsonObject resultObj = new JsonObject();
                resultObj.addProperty("item",
                        ForgeRegistries.ITEMS.getKey(params.resultItem.getItem()).toString());
                recipe.add("result", resultObj);
                return recipe;
            }
        }
        RecipeRequest request = createRecipeRequest(params);
        return params.recipeType.getProcessor().createRecipeJson(request);
    }

    /**
     * 创建材料JSON对象（关键：支持标签）
     */
    private JsonObject createIngredientJsonObject(IngredientData data) {
        JsonObject ingredient = new JsonObject();

        switch (data.getType()) {
            case ITEM -> {
                ItemStack stack = data.getItemStack();
                ingredient.addProperty("item",
                        ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());

                // 支持NBT
                if (stack.hasTag()) {
                    ingredient.addProperty("nbt", stack.getTag().toString());
                }

                if (stack.getCount() > 1) {
                    ingredient.addProperty("count", stack.getCount());
                }
            }
            case TAG, CUSTOM_TAG -> {
                // 关键：使用 "tag" 字段而不是 "item"
                ingredient.addProperty("tag", data.getTagId().toString());
            }
        }

        return ingredient;
    }

    /**
     * 将ItemStack列表转换为IngredientData列表（兼容旧代码）
     */
    private List<IngredientData> convertItemStacksToIngredientData(List<ItemStack> items) {
        List<IngredientData> result = new ArrayList<>();
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                result.add(IngredientData.fromItem(item));
            } else {
                result.add(IngredientData.empty());
            }
        }
        return result;
    }

    /**
     * 使用内置系统构建配方（向后兼容）
     */
    private void buildWithBuiltinSystem(BuildParams params) {
        try {
            // 转换为传统的RecipeGenerator模式
            RecipeGenerator.RecipeMode mode = convertToLegacyMode(params);
            String modId = params.recipeType.getModId();

            // 生成配方ID
            String recipeId = generateRecipeId(params, modId, mode);
            boolean isOverride = isOverrideMode(params);

            LOGGER.info("构建配方: {} (覆盖模式: {})", recipeId, isOverride);

            // 构建配方数据
            RecipeGenerator.RecipeData recipeData = buildLegacyRecipeData(params, modId, recipeId, mode);

            if (!RecipeGenerator.validateRecipeData(recipeData)) {
                showError("配方数据无效！");
                return;
            }

            // 创建配方
            boolean success = isOverride ?
                createRecipeOverride(recipeData) :
                createNormalRecipe(recipeData);

            if (success) {
                String action = params.isEditing ? "更新" : "创建";
                String method = isOverride ? "覆盖" : "创建";
                showSuccess(action + "配方成功！类型: " + mode.getDisplayName() +
                           " (" + method + "模式) 使用 /reload 刷新配方");
            } else {
                showError("配方" + (isOverride ? "覆盖" : "创建") + "失败！");
            }

        } catch (Exception e) {
            LOGGER.error("使用内置系统构建配方失败", e);
            showError("内置配方处理失败: " + e.getMessage());
        }
    }

    /**
     * 创建配方请求
     */
    private RecipeRequest createRecipeRequest(BuildParams params) {
        String category = params.recipeType.getProperty("category", String.class);
        String recipeId = params.editingRecipeId != null ?
                params.editingRecipeId.toString() :
                generateRecipeIdPath(params);

        // 根据配方类型创建相应的请求
        if ("cooking".equals(category) || params.recipeType.supportsCookingSettings()) {
            return createCookingRequest(params, recipeId);
        } else if ("crafting".equals(category)) {
            return createCraftingRequest(params, recipeId);
        } else if ("avaritia".equals(category)) {
            return createAvaritiaRequest(params, recipeId);
        } else {
            RecipeRequest request = createCustomRequest(params, recipeId);
            if (params.extraProperties != null) {
                params.extraProperties.forEach(request::withProperty);
            }
            return request;
        }
    }

    private String[] generateCustomPattern(List<ItemStack> ingredients, int gridWidth, int gridHeight) {
        String[] pattern = new String[gridHeight];
        AtomicReference<Character> currentChar = new AtomicReference<>('A');
        Map<String, Character> itemToChar = new HashMap<>();

        for (int row = 0; row < gridHeight; row++) {
            StringBuilder rowPattern = new StringBuilder();
            for (int col = 0; col < gridWidth; col++) {
                int index = row * gridWidth + col;
                if (index < ingredients.size() && !ingredients.get(index).isEmpty()) {
                    String itemKey = ForgeRegistries.ITEMS.getKey(ingredients.get(index).getItem()).toString();
                    char symbol = itemToChar.computeIfAbsent(itemKey,
                            k -> currentChar.getAndSet((char) (currentChar.get() + 1)));
                    rowPattern.append(symbol);
                } else {
                    rowPattern.append(' ');
                }
            }
            pattern[row] = rowPattern.toString();
        }

        return pattern;
    }

    /**
     * 创建烹饪配方请求
     */
    private RecipeRequest createCookingRequest(BuildParams params, String recipeId) {
        // 获取第一个非空材料作为烹饪材料
        Object ingredient = null;

        if (params.ingredientsData != null && !params.ingredientsData.isEmpty()) {
            for (IngredientData data : params.ingredientsData) {
                if (!data.isEmpty()) {
                    ingredient = convertIngredientDataToObject(data);
                    break;
                }
            }
        } else {
            // 兼容旧版本
            for (ItemStack item : params.ingredients) {
                if (!item.isEmpty()) {
                    ingredient = ForgeRegistries.ITEMS.getKey(item.getItem()).toString();
                    break;
                }
            }
        }

        String cookingType = params.cookingType;
        if (cookingType == null || cookingType.isEmpty()) {
            cookingType = params.recipeType.getId();
        }

        return RecipeRequest.cooking(
                params.recipeType.getModId(),
                cookingType,
                recipeId,
                params.resultItem,
                ingredient,
                params.cookingExp,
                (int) params.cookingTime
        );
    }

    /**
     * 创建工作台配方请求
     */
    private RecipeRequest createCraftingRequest(BuildParams params, String recipeId) {
        Object[] ingredients = convertIngredientsToArray(params);

        if ("shaped".equals(params.craftingMode)) {
            String[] pattern = generateCraftingPattern(params);
            return RecipeRequest.shaped(
                    params.recipeType.getModId(),
                    recipeId,
                    params.resultItem,
                    pattern,
                    ingredients
            );
        } else {
            return RecipeRequest.shapeless(
                    params.recipeType.getModId(),
                    recipeId,
                    params.resultItem,
                    ingredients
            );
        }
    }

    /**
     * 创建Avaritia配方请求
     */
    private RecipeRequest createAvaritiaRequest(BuildParams params, String recipeId) {
        Object[] ingredients = convertIngredientsToArray(params);

        RecipeRequest request;
        if ("shaped".equals(params.craftingMode)) {
            String[] pattern = generateAvaritiaPattern(params, params.customTier);
            request = RecipeRequest.shaped(
                    params.recipeType.getModId(),
                    recipeId,
                    params.resultItem,
                    pattern,
                    ingredients
            );
        } else {
            request = RecipeRequest.shapeless(
                    params.recipeType.getModId(),
                    recipeId,
                    params.resultItem,
                    ingredients
            );
        }

        // 添加Avaritia特定属性
        Integer tier = params.recipeType.getProperty("tier", Integer.class);
        request.withProperty("tier", tier != null ? tier : params.customTier);
        request.withProperty("recipeType", params.recipeType.getId());

        return request;
    }

    /**
     * 创建自定义配方请求
     */
    private RecipeRequest createCustomRequest(BuildParams params, String recipeId) {
        Object[] ingredients = convertIngredientsToArray(params);

        RecipeRequest request = new RecipeRequest();
        request.modId = params.recipeType.getModId();
        request.recipeType = params.recipeType.getId();
        request.recipeId = recipeId;
        request.result = params.resultItem;
        request.resultCount = params.resultItem.getCount();
        request.ingredients = ingredients;
        if ("shaped".equals(params.craftingMode)) {
            int gridWidth = params.recipeType.getMaxGridWidth();
            int gridHeight = params.recipeType.getMaxGridHeight();
            request.pattern = generateCustomPattern(params.ingredients, gridWidth, gridHeight);
        }
        // 添加所有自定义属性
        request.withProperty("craftingMode", params.craftingMode);
        request.withProperty("cookingType", params.cookingType);
        request.withProperty("customTier", params.customTier);
        request.withProperty("cookingTime", params.cookingTime);
        request.withProperty("cookingExp", params.cookingExp);
        request.withProperty("experience", params.cookingExp);
        request.withProperty("cookingtime", params.cookingTime);

        // 添加配方类型定义的所有属性
        if (params.recipeType.getProperty("category", String.class) != null) {
            request.withProperty("category", params.recipeType.getProperty("category", String.class));
        }

        return request;
    }

    /**
     * 将IngredientData列表转换为配方JSON使用的对象数组
     */
    private Object[] convertIngredientsToArray(BuildParams params) {
        // 优先使用 ingredientsData
        if (params.ingredientsData != null && !params.ingredientsData.isEmpty()) {
            return params.ingredientsData.stream()
                    .filter(data -> !data.isEmpty())
                    .map(this::convertIngredientDataToObject)
                    .toArray(Object[]::new);
        }

        // 兼容旧版本：使用 ingredients
        return params.ingredients.stream()
                .filter(item -> !item.isEmpty())
                .map(item -> ForgeRegistries.ITEMS.getKey(item.getItem()).toString())
                .toArray(Object[]::new);
    }

    /**
     * 将单个IngredientData转换为配方对象
     */
    private Object convertIngredientDataToObject(IngredientData data) {
        return switch (data.getType()) {
            case ITEM -> {
                ItemStack stack = data.getItemStack();
                if (stack.hasTag()) {
                    // 带NBT的物品：返回完整的ItemStack信息
                    yield createItemWithNBT(stack);
                } else {
                    // 普通物品：返回物品ID字符串
                    yield ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                }
            }
            case TAG -> {
                // 标签：返回 "#namespace:path" 格式
                yield "#" + data.getTagId().toString();
            }
            case CUSTOM_TAG -> {
                // 自定义标签：返回 "#namespace:path" 格式
                yield "#" + data.getTagId().toString();
            }
        };
    }

    /**
     * 创建带NBT的物品JSON对象
     */
    private JsonObject createItemWithNBT(ItemStack stack) {
        JsonObject itemObj = new JsonObject();

        // 物品ID
        itemObj.addProperty("item", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());

        // 数量
        if (stack.getCount() > 1) {
            itemObj.addProperty("count", stack.getCount());
        }

        // NBT数据
        if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                try {
                    // 将NBT转换为字符串格式
                    String nbtString = tag.toString();
                    itemObj.addProperty("nbt", nbtString);
                } catch (Exception e) {
                    LOGGER.error("无法序列化NBT数据", e);
                }
            }
        }

        return itemObj;
    }

    /**
     * 生成工作台配方模式
     */
    private String[] generateCraftingPattern(BuildParams params) {
        String[] pattern = new String[3];
        AtomicReference<Character> currentChar = new AtomicReference<>('A');
        Map<String, Character> itemToChar = new HashMap<>();

        List<IngredientData> dataList = params.ingredientsData;
        if (dataList == null || dataList.isEmpty()) {
            // 兼容旧版本
            dataList = new ArrayList<>();
            for (ItemStack stack : params.ingredients) {
                dataList.add(stack.isEmpty() ? IngredientData.empty() : IngredientData.fromItem(stack));
            }
        }

        for (int row = 0; row < 3; row++) {
            StringBuilder rowPattern = new StringBuilder();
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                if (index < dataList.size() && !dataList.get(index).isEmpty()) {
                    IngredientData data = dataList.get(index);
                    String key = getIngredientKey(data);
                    char symbol = itemToChar.computeIfAbsent(key,
                            k -> currentChar.getAndSet((char) (currentChar.get() + 1)));
                    rowPattern.append(symbol);
                } else {
                    rowPattern.append(' ');
                }
            }
            pattern[row] = rowPattern.toString();
        }

        return pattern;
    }

    /**
     * 获取材料的唯一键（用于模式生成）
     */
    private String getIngredientKey(IngredientData data) {
        return switch (data.getType()) {
            case ITEM -> {
                ItemStack stack = data.getItemStack();
                String key = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                if (stack.hasTag()) {
                    // 带NBT的物品需要包含NBT的哈希值以区分
                    key += "_nbt_" + stack.getTag().hashCode();
                }
                yield key;
            }
            case TAG, CUSTOM_TAG -> "#" + data.getTagId().toString();
        };
    }

    /**
     * 生成Avaritia配方模式
     */
    private String[] generateAvaritiaPattern(BuildParams params, int tier) {
        int gridSize = getAvaritiaGridSize(tier);
        String[] pattern = new String[gridSize];
        AtomicReference<Character> currentChar = new AtomicReference<>('A');
        Map<String, Character> itemToChar = new HashMap<>();

        List<IngredientData> dataList = params.ingredientsData;
        if (dataList == null || dataList.isEmpty()) {
            // 兼容旧版本
            dataList = new ArrayList<>();
            for (ItemStack stack : params.ingredients) {
                dataList.add(stack.isEmpty() ? IngredientData.empty() : IngredientData.fromItem(stack));
            }
        }

        for (int row = 0; row < gridSize; row++) {
            StringBuilder rowPattern = new StringBuilder();
            for (int col = 0; col < gridSize; col++) {
                int index = row * gridSize + col;
                if (index < dataList.size() && !dataList.get(index).isEmpty()) {
                    IngredientData data = dataList.get(index);
                    String key = getIngredientKey(data);
                    char symbol = itemToChar.computeIfAbsent(key,
                            k -> currentChar.getAndSet((char) (currentChar.get() + 1)));
                    rowPattern.append(symbol);
                } else {
                    rowPattern.append(' ');
                }
            }
            pattern[row] = rowPattern.toString();
        }

        return pattern;
    }

    /**
     * 获取Avaritia网格大小
     */
    private int getAvaritiaGridSize(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            case 4 -> 9;
            default -> 3;
        };
    }

    /**
     * 创建材料JSON
     */
    private JsonObject createIngredientsJson(List<ItemStack> ingredients) {
        JsonObject ingredientsJson = new JsonObject();
        
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack ingredient = ingredients.get(i);
            if (!ingredient.isEmpty()) {
                JsonObject ingredientObj = new JsonObject();
                ingredientObj.addProperty("item", ForgeRegistries.ITEMS.getKey(ingredient.getItem()).toString());
                if (ingredient.getCount() > 1) {
                    ingredientObj.addProperty("count", ingredient.getCount());
                }
                ingredientsJson.add(String.valueOf(i), ingredientObj);
            }
        }
        
        return ingredientsJson;
    }

    /**
     * 获取合成模式
     */
    private String getCraftingModeFromParams(BuildParams params) {
        String category = params.recipeType.getProperty("category", String.class);
        if ("crafting".equals(category) || "avaritia".equals(category)) {
            return params.craftingMode;
        }
        return params.recipeType.getProperty("mode", String.class);
    }

    /**
     * 生成配方ID路径
     */
    private String generateRecipeIdPath(BuildParams params) {
        String itemName = ForgeRegistries.ITEMS.getKey(params.resultItem.getItem()).getPath();
        String typeName = params.recipeType.getId().replace(":", "_");
        return "custom_" + typeName + "_" + itemName + "_" + System.currentTimeMillis();
    }

    /**
     * 转换为传统模式（向后兼容）
     */
    private RecipeGenerator.RecipeMode convertToLegacyMode(BuildParams params) {
        String category = params.recipeType.getProperty("category", String.class);

        if ("cooking".equals(category)) {
            return switch (params.recipeType.getId()) {
                case "blasting" -> RecipeGenerator.RecipeMode.BLASTING;
                case "smoking" -> RecipeGenerator.RecipeMode.SMOKING;
                case "campfire_cooking" -> RecipeGenerator.RecipeMode.CAMPFIRE;
                default -> RecipeGenerator.RecipeMode.SMELTING;
            };
        } else if ("avaritia".equals(category)) {
            Integer tier = params.recipeType.getProperty("tier", Integer.class);
            int actualTier = tier != null ? tier : params.customTier;
            boolean shaped = "shaped".equals(params.craftingMode);
            return getAvaritiaMode(actualTier, shaped);
        } else if ("brewing".equals(category)) {
            return RecipeGenerator.RecipeMode.BREWING; // 需要添加这个枚举值
        } else if ("stonecutting".equals(category)) {
            return RecipeGenerator.RecipeMode.STONECUTTING; // 需要添加这个枚举值
        } else if ("smithing".equals(category)) {
            return RecipeGenerator.RecipeMode.SMITHING; // 需要添加这个枚举值
        } else {
            // 保持现有的3x3逻辑作为最后的回退
            boolean shaped = "shaped".equals(params.craftingMode);
            return shaped ? RecipeGenerator.RecipeMode.SHAPED_3X3 : RecipeGenerator.RecipeMode.SHAPELESS_3X3;
        }
    }

    /**
     * 获取Avaritia模式
     */
    private RecipeGenerator.RecipeMode getAvaritiaMode(int tier, boolean shaped) {
        return switch (tier) {
            case 1 -> shaped ? RecipeGenerator.RecipeMode.SHAPED_3X3 : RecipeGenerator.RecipeMode.SHAPELESS_3X3;
            case 2 -> shaped ? RecipeGenerator.RecipeMode.SHAPED_5X5 : RecipeGenerator.RecipeMode.SHAPELESS_5X5;
            case 3 -> shaped ? RecipeGenerator.RecipeMode.SHAPED_7X7 : RecipeGenerator.RecipeMode.SHAPELESS_7X7;
            case 4 -> shaped ? RecipeGenerator.RecipeMode.SHAPED_9X9 : RecipeGenerator.RecipeMode.SHAPELESS_9X9;
            default -> RecipeGenerator.RecipeMode.SHAPED_3X3;
        };
    }

    /**
     * 验证构建参数（支持IngredientData）
     */
    private boolean validateParams(BuildParams params) {
        if (params.recipeType == null) {
            showError("请选择配方类型！");
            return false;
        }

        if (params.resultItem.isEmpty()) {
            showError("请选择结果物品！");
            return false;
        }

        if (params.resultItem.getCount() <= 0) {
            showError("数量必须大于0！");
            return false;
        }

        // 检查是否有有效的材料（支持IngredientData）
        boolean hasIngredients = false;

        // 优先检查 ingredientsData
        if (params.ingredientsData != null && !params.ingredientsData.isEmpty()) {
            hasIngredients = params.ingredientsData.stream()
                    .anyMatch(data -> !data.isEmpty());
        } else if (params.ingredients != null && !params.ingredients.isEmpty()) {
            // 兼容旧版本：检查 ingredients
            hasIngredients = params.ingredients.stream()
                    .anyMatch(item -> !item.isEmpty());
        }

        if (!hasIngredients) {
            showError("请至少添加一个材料！");
            return false;
        }

        // 检查mod是否已加载
        ModRecipeProcessor processor = params.recipeType.getProcessor();
        if (processor != null && !processor.isModLoaded()) {
            showError("所需的mod未加载: " + params.recipeType.getModId());
            return false;
        }

        return true;
    }

    /**
     * 判断是否为覆盖模式
     */
    private boolean isOverrideMode(BuildParams params) {
        if (!params.isEditing || params.editingRecipeId == null) {
            return false;
        }
        return !isCustomRecipe(params.editingRecipeId);
    }

    /**
     * 判断是否为自定义配方
     */
    private boolean isCustomRecipe(ResourceLocation recipeId) {
        String namespace = recipeId.getNamespace();
        String path = recipeId.getPath();
        return namespace.equals("registerhelper") ||
                path.startsWith("custom_") ||
                path.contains("_custom_");
    }

    /**
     * 生成配方ID
     */
    private String generateRecipeId(BuildParams params, String modId, RecipeGenerator.RecipeMode mode) {
        if (params.isEditing && params.editingRecipeId != null) {
            return params.editingRecipeId.toString();
        }
        return RecipeGenerator.generateRecipeId(modId, mode, params.resultItem);
    }

    /**
     * 构建传统配方数据
     */
    private RecipeGenerator.RecipeData buildLegacyRecipeData(BuildParams params, String modId, 
                                                           String recipeId, RecipeGenerator.RecipeMode mode) {
        RecipeGenerator.RecipeData recipeData = RecipeGenerator.buildFromGrid(
                modId, recipeId, mode, params.resultItem, params.ingredients);

        // 设置烹饪相关参数
        if (mode.isCooking()) {
            recipeData.experience = params.cookingExp;
            recipeData.cookingTime = (int) params.cookingTime;
        }

        return recipeData;
    }

    /**
     * 创建配方覆盖（传统方式）
     */
    private boolean createRecipeOverride(RecipeGenerator.RecipeData data) {
        try {
            JsonObject recipeJson;
            if (data.mode.isAvaritia()) {
                recipeJson = RecipeGenerator.generateAvaritiaRecipe(data);
            } else {
                recipeJson = RecipeGenerator.generateMinecraftRecipe(data);
            }
            ResourceLocation recipeId = new ResourceLocation(data.recipeId);
            return UnifiedRecipeOverrideManager.addOverride(recipeId, recipeJson);

        } catch (Exception e) {
            LOGGER.error("创建配方覆盖失败", e);
            return false;
        }
    }

    /**
     * 创建普通配方（传统方式）
     */
    private boolean createNormalRecipe(RecipeGenerator.RecipeData data) {
        try {
            CreateRecipePacket packet = createNetworkPacket(data);
            if (packet != null) {
                ModNetwork.CHANNEL.sendToServer(packet);
                return true;
            }
            return false;

        } catch (Exception e) {
            LOGGER.error("创建普通配方失败", e);
            return false;
        }
    }

    /**
     * 创建网络数据包（传统方式）
     */
    private CreateRecipePacket createNetworkPacket(RecipeGenerator.RecipeData data) {
        if (data.mode.isCooking()) {
            return CreateRecipePacket.cooking(
                    data.modId,
                    getCookingRecipeType(data.mode),
                    data.recipeId,
                    data.result,
                    ForgeRegistries.ITEMS.getKey(data.cookingIngredient.getItem()).toString(),
                    data.experience,
                    data.cookingTime
            );
        } else if (data.mode.isShaped()) {
            List<String> ingredientList = new ArrayList<>();
            for (Map.Entry<Character, ItemStack> entry : data.symbolMapping.entrySet()) {
                ingredientList.add(String.valueOf(entry.getKey()));
                ingredientList.add(ForgeRegistries.ITEMS.getKey(entry.getValue().getItem()).toString());
            }
            String[] ingredientItems = ingredientList.toArray(new String[0]);

            if (data.mode.isAvaritia()) {
                return CreateRecipePacket.avaritia(
                        "shaped_table",
                        data.recipeId,
                        data.result,
                        data.pattern,
                        ingredientItems,
                        RecipeGenerator.getTierFromGridSize(data.mode.getGridSize())
                );
            } else {
                return CreateRecipePacket.shaped(
                        data.modId,
                        data.recipeId,
                        data.result,
                        data.pattern,
                        ingredientItems
                );
            }
        } else {
            String[] ingredientItems = data.ingredients.stream()
                    .map(item -> ForgeRegistries.ITEMS.getKey(item.getItem()).toString())
                    .toArray(String[]::new);

            if (data.mode.isAvaritia()) {
                return CreateRecipePacket.avaritia(
                        "shapeless_table",
                        data.recipeId,
                        data.result,
                        null,
                        ingredientItems,
                        RecipeGenerator.getTierFromIngredientCount(data.ingredients.size())
                );
            } else {
                return CreateRecipePacket.shapeless(
                        data.modId,
                        data.recipeId,
                        data.result,
                        ingredientItems
                );
            }
        }
    }

    /**
     * 获取烹饪配方类型字符串
     */
    private String getCookingRecipeType(RecipeGenerator.RecipeMode mode) {
        return switch (mode) {
            case BLASTING -> "blasting";
            case SMOKING -> "smoking";
            case CAMPFIRE -> "campfire_cooking";
            default -> "smelting";
        };
    }

    private void showSuccess(String message) {
        if (successCallback != null) {
            successCallback.accept(message);
        }
    }

    private void showError(String message) {
        if (errorCallback != null) {
            errorCallback.accept(message);
        }
    }
}