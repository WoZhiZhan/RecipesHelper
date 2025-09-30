package com.wzz.registerhelper.gui.recipe.dynamic;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.network.CreateRecipePacket;
import com.wzz.registerhelper.generator.RecipeGenerator;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig.*;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import com.wzz.registerhelper.recipe.UniversalRecipeManager;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
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
        public final String craftingMode;  // "shaped" 或 "shapeless"
        public final String cookingType;   // "smelting", "blasting" 等
        public final int customTier;
        public final ItemStack resultItem;
        public final List<ItemStack> ingredients;
        public final float cookingTime;
        public final float cookingExp;
        public final ResourceLocation editingRecipeId;
        public final boolean isEditing;

        public BuildParams(RecipeTypeDefinition recipeType, String craftingMode, String cookingType,
                          int customTier, ItemStack resultItem, List<ItemStack> ingredients,
                          float cookingTime, float cookingExp, ResourceLocation editingRecipeId, boolean isEditing) {
            this.recipeType = recipeType;
            this.craftingMode = craftingMode;
            this.cookingType = cookingType;
            this.customTier = customTier;
            this.resultItem = resultItem;
            this.ingredients = new ArrayList<>(ingredients);
            this.cookingTime = cookingTime;
            this.cookingExp = cookingExp;
            this.editingRecipeId = editingRecipeId;
            this.isEditing = isEditing;
        }
    }

    /**
     * 构建配方
     */
    public void buildRecipe(BuildParams params) {
        if (!validateParams(params)) {
            return;
        }

        try {
            ModRecipeProcessor processor = params.recipeType.getProcessor();
            String category = params.recipeType.getProperty("category", String.class);
            boolean needsCustomHandling = "stonecutting".equals(category) ||
                    "smithing".equals(category);

            if ((processor != null && processor.isModLoaded()) || needsCustomHandling) {
                buildWithCustomProcessor(params, processor);
            } else {
                buildWithBuiltinSystem(params);
            }

        } catch (Exception e) {
            LOGGER.error("构建配方失败", e);
            showError("处理配方时发生错误: " + e.getMessage());
        }
    }

    /**
     * 使用自定义处理器构建配方
     */
    private void buildWithCustomProcessor(BuildParams params, ModRecipeProcessor processor) {
        try {
            // 创建配方请求
            RecipeRequest request = createRecipeRequest(params);
            
            // 使用处理器生成配方JSON
            JsonObject recipeJson = processor.createRecipeJson(request);
            
            if (recipeJson == null) {
                showError("配方处理器未能生成有效的配方JSON");
                return;
            }

            // 决定是覆盖还是创建
            boolean isOverride = isOverrideMode(params);
            boolean success;

            if (isOverride) {
                success = createCustomRecipeOverride(params.editingRecipeId, recipeJson);
            } else {
                success = createCustomRecipe(params, recipeJson);
            }

            if (success) {
                String action = params.isEditing ? "更新" : "创建";
                String method = isOverride ? "覆盖" : "创建";
                showSuccess(action + "配方成功！类型: " + params.recipeType.getDisplayName() + 
                           " (" + method + "模式) 使用 /reload 刷新配方");
            } else {
                showError("配方" + (isOverride ? "覆盖" : "创建") + "失败！");
            }

        } catch (Exception e) {
            LOGGER.error("使用自定义处理器构建配方失败", e);
            showError("自定义配方处理失败: " + e.getMessage());
        }
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
            return createCustomRequest(params, recipeId);
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
        for (ItemStack item : params.ingredients) {
            if (!item.isEmpty()) {
                ingredient = ForgeRegistries.ITEMS.getKey(item.getItem()).toString();
                break;
            }
        }

        String cookingType = params.cookingType;
        if (cookingType == null || cookingType.isEmpty()) {
            cookingType = params.recipeType.getId(); // 使用配方类型ID作为烹饪类型
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
        Object[] ingredients = convertIngredientsToArray(params.ingredients);

        if ("shaped".equals(params.craftingMode)) {
            String[] pattern = generateCraftingPattern(params.ingredients);
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
        Object[] ingredients = convertIngredientsToArray(params.ingredients);

        RecipeRequest request;
        if ("shaped".equals(params.craftingMode)) {
            String[] pattern = generateAvaritiaPattern(params.ingredients, params.customTier);
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
        Object[] ingredients = convertIngredientsToArray(params.ingredients);

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

        // 添加配方类型定义的所有属性
        if (params.recipeType.getProperty("category", String.class) != null) {
            request.withProperty("category", params.recipeType.getProperty("category", String.class));
        }

        return request;
    }

    /**
     * 将ItemStack列表转换为Object数组
     */
    private Object[] convertIngredientsToArray(List<ItemStack> ingredients) {
        return ingredients.stream()
                .filter(item -> !item.isEmpty())
                .map(item -> Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item.getItem())).toString())
                .toArray(Object[]::new);
    }

    /**
     * 生成工作台配方模式
     */
    private String[] generateCraftingPattern(List<ItemStack> ingredients) {
        String[] pattern = new String[3];
        AtomicReference<Character> currentChar = new AtomicReference<>('A');
        Map<String, Character> itemToChar = new HashMap<>();

        for (int row = 0; row < 3; row++) {
            StringBuilder rowPattern = new StringBuilder();
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
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
     * 生成Avaritia配方模式
     */
    private String[] generateAvaritiaPattern(List<ItemStack> ingredients, int tier) {
        int gridSize = getAvaritiaGridSize(tier);
        String[] pattern = new String[gridSize];
        AtomicReference<Character> currentChar = new AtomicReference<>('A');
        Map<String, Character> itemToChar = new HashMap<>();

        for (int row = 0; row < gridSize; row++) {
            StringBuilder rowPattern = new StringBuilder();
            for (int col = 0; col < gridSize; col++) {
                int index = row * gridSize + col;
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
     * 验证构建参数
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

        // 检查是否有有效的材料
        boolean hasIngredients = params.ingredients.stream().anyMatch(item -> !item.isEmpty());
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
     * 创建自定义配方覆盖
     */
    private boolean createCustomRecipeOverride(ResourceLocation recipeId, JsonObject recipeJson) {
        try {
            if (!UnifiedRecipeOverrideManager.isValidOverrideJson(recipeJson)) {
                LOGGER.error("生成的覆盖JSON格式无效");
                return false;
            }

            return UnifiedRecipeOverrideManager.addOverride(recipeId, recipeJson);

        } catch (Exception e) {
            LOGGER.error("创建自定义配方覆盖失败", e);
            return false;
        }
    }

    /**
     * 创建自定义配方
     */
    private boolean createCustomRecipe(BuildParams params, JsonObject recipeJson) {
        try {
            RecipeRequest request = createRecipeRequest(params);
            return UniversalRecipeManager.getInstance().createRecipe(request);
        } catch (Exception e) {
            LOGGER.error("创建自定义配方失败", e);
            return false;
        }
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

            if (!UnifiedRecipeOverrideManager.isValidOverrideJson(recipeJson)) {
                LOGGER.error("生成的覆盖JSON格式无效");
                return false;
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