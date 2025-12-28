package com.wzz.registerhelper.gui.recipe;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import com.wzz.registerhelper.gui.recipe.RecipeTypeConfig.*;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 配方加载器
 * 负责从现有配方中加载数据到编辑器
 */
public class RecipeLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Consumer<String> messageCallback;
    private final Minecraft minecraft;

    public RecipeLoader(Consumer<String> messageCallback) {
        this.messageCallback = messageCallback;
        this.minecraft = Minecraft.getInstance();
    }

    /**
     * 加载配方数据结果
     */
    public static class LoadResult {
        public final boolean success;
        public final RecipeType recipeType;
        public final CraftingMode craftingMode;
        public final CookingType cookingType;
        public int avaritiaTeir;
        public final ItemStack resultItem;
        public final List<ItemStack> ingredients;
        public final String message;
        public final String originalRecipeTypeId;
        public ResourceLocation recipeId;

        public LoadResult(boolean success, String message) {
            this(success, null, null, null, 1, ItemStack.EMPTY, Collections.emptyList(), message, null);
        }

        public LoadResult(boolean success, RecipeType recipeType, CraftingMode craftingMode,
                          CookingType cookingType, int avaritiaTeir, ItemStack resultItem,
                          List<ItemStack> ingredients, String message, String originalRecipeTypeId) {
            this.success = success;
            this.recipeType = recipeType;
            this.craftingMode = craftingMode;
            this.cookingType = cookingType;
            this.avaritiaTeir = avaritiaTeir;
            this.resultItem = resultItem;
            this.ingredients = ingredients;
            this.message = message;
            this.originalRecipeTypeId = originalRecipeTypeId;
        }

        public void setRecipeId(ResourceLocation id) {
            this.recipeId = id;
        }
    }

    /**
     * 加载现有配方
     */
    public LoadResult loadRecipe(ResourceLocation recipeId) {
        try {
            RecipeManager recipeManager = getRecipeManager();
            RegistryAccess registryAccess = getRegistryAccess();

            if (recipeManager == null || registryAccess == null) {
                return new LoadResult(false, "无法获取配方数据（服务器未启动或未连接）");
            }
            var recipe = recipeManager.byKey(recipeId).orElse(null);

            if (recipe == null) {
                return new LoadResult(false, "找不到配方: " + recipeId);
            }
            String originalRecipeTypeId;
            try {
                ResourceLocation serializerId = net.minecraftforge.registries.ForgeRegistries.RECIPE_SERIALIZERS
                        .getKey(recipe.getSerializer());
                originalRecipeTypeId = serializerId != null ? serializerId.toString() : null;
            } catch (Exception e) {
                LOGGER.warn("无法通过序列化器获取配方类型，使用RecipeType作为备选", e);
                originalRecipeTypeId = null;
            }

            if (originalRecipeTypeId == null) {
                ResourceLocation recipeTypeRL = net.minecraftforge.registries.ForgeRegistries.RECIPE_TYPES
                        .getKey(recipe.getType());
                originalRecipeTypeId = recipeTypeRL != null ? recipeTypeRL.toString() : recipe.getType().toString();
            }

            // 获取结果物品
            ItemStack resultItem = recipe.getResultItem(minecraft.level.registryAccess()).copy();

            // 根据配方类型和类名加载
            String recipeTypeName = originalRecipeTypeId.toLowerCase();
            String recipeClassName = recipe.getClass().getSimpleName().toLowerCase();

            // 检查工作台配方
            if (isShapedCraftingRecipe(recipeTypeName, recipeClassName)) {
                return loadCraftingRecipe(recipe, resultItem, CraftingMode.SHAPED, originalRecipeTypeId);
            } else if (isShapelessCraftingRecipe(recipeTypeName, recipeClassName)) {
                return loadCraftingRecipe(recipe, resultItem, CraftingMode.SHAPELESS, originalRecipeTypeId);
            }
            // 检查烹饪配方
            else if (isSmeltingRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(recipe, resultItem, CookingType.SMELTING, originalRecipeTypeId);
            } else if (isBlastingRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(recipe, resultItem, CookingType.BLASTING, originalRecipeTypeId);
            } else if (isSmokingRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(recipe, resultItem, CookingType.SMOKING, originalRecipeTypeId);
            } else if (isCampfireRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(recipe, resultItem, CookingType.CAMPFIRE, originalRecipeTypeId);
            }
            // 检查Avaritia配方
            else if (isAvaritiaRecipe(recipeTypeName, recipeClassName)) {
                CraftingMode mode = recipeTypeName.contains("shaped") || recipeClassName.contains("shaped") ?
                        CraftingMode.SHAPED : CraftingMode.SHAPELESS;
                return loadAvaritiaRecipe(recipe, resultItem, mode, originalRecipeTypeId);
            }
            // 检查锻造台配方
            else if (isSmithingRecipe(recipeTypeName, recipeClassName)) {
                return loadSmithingRecipe(recipe, resultItem, originalRecipeTypeId);
            }
            // 对于未知类型的配方，尝试通用加载
            else {
                return loadGenericModRecipe(recipe, resultItem, originalRecipeTypeId);
            }

        } catch (Exception e) {
            LOGGER.error("加载配方失败", e);
            return new LoadResult(false, "加载配方失败: " + e.getMessage());
        }
    }

    /**
     * 加载通用模组配方（用于不认识的配方类型）
     */
    private LoadResult loadGenericModRecipe(Recipe<?> recipe, ItemStack resultItem, String originalRecipeTypeId) {
        try {
            var recipeIngredients = recipe.getIngredients();
            List<ItemStack> ingredients = new ArrayList<>();

            // 尝试加载材料
            for (var ingredient : recipeIngredients) {
                if (ingredient != null && !ingredient.isEmpty()) {
                    var items = ingredient.getItems();
                    if (items != null && items.length > 0) {
                        ingredients.add(items[0].copy());
                    } else {
                        ingredients.add(ItemStack.EMPTY);
                    }
                } else {
                    ingredients.add(ItemStack.EMPTY);
                }
            }

            LOGGER.info("成功加载未知类型的配方: {}, 类型: {}, 材料数: {}",
                    recipe.getId(), originalRecipeTypeId, ingredients.size());

            return new LoadResult(true, null, null, null, 1,
                    resultItem, ingredients, "成功载入模组配方", originalRecipeTypeId);

        } catch (Exception e) {
            LOGGER.warn("解析通用配方失败", e);
            return new LoadResult(false, "解析配方失败: " + e.getMessage());
        }
    }

    /**
     * 加载工作台配方（修改所有返回语句）
     */
    private LoadResult loadCraftingRecipe(Recipe<?> recipe, ItemStack resultItem,
                                          CraftingMode mode, String originalRecipeTypeId) {
        try {
            List<ItemStack> ingredients = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                ingredients.add(ItemStack.EMPTY);
            }

            if (mode == CraftingMode.SHAPED && recipe.getClass().getSimpleName().contains("ShapedRecipe")) {
                return loadShapedRecipePattern(recipe, resultItem, ingredients, originalRecipeTypeId);
            } else {
                return loadGenericCraftingRecipe(recipe, resultItem, mode, ingredients, originalRecipeTypeId);
            }
        } catch (Exception e) {
            LOGGER.error("解析工作台配方失败", e);
            return new LoadResult(false, "解析工作台配方失败: " + e.getMessage());
        }
    }

    private LoadResult loadShapedRecipePattern(Recipe<?> recipe, ItemStack resultItem,
                                               List<ItemStack> ingredients, String originalRecipeTypeId) {
        return loadGenericCraftingRecipe(recipe, resultItem, CraftingMode.SHAPED, ingredients, originalRecipeTypeId);
    }

    private LoadResult loadGenericCraftingRecipe(Recipe<?> recipe, ItemStack resultItem,
                                                 CraftingMode mode, List<ItemStack> ingredients,
                                                 String originalRecipeTypeId) {
        var recipeIngredients = recipe.getIngredients();
        for (int i = 0; i < Math.min(recipeIngredients.size(), 9); i++) {
            var ingredient = recipeIngredients.get(i);
            if (ingredient != null && !ingredient.isEmpty()) {
                var items = ingredient.getItems();
                if (items != null && items.length > 0) {
                    ingredients.set(i, items[0].copy());
                }
            }
        }

        return new LoadResult(true, RecipeType.CRAFTING, mode, null, 1,
                resultItem, ingredients, "成功载入工作台配方", originalRecipeTypeId);
    }

    /**
     * 加载烹饪配方（添加 originalRecipeTypeId 参数）
     */
    private LoadResult loadCookingRecipe(Recipe<?> recipe, ItemStack resultItem,
                                         CookingType cookingType, String originalRecipeTypeId) {
        try {
            var recipeIngredients = recipe.getIngredients();
            List<ItemStack> ingredients = new ArrayList<>();
            ingredients.add(ItemStack.EMPTY);

            if (!recipeIngredients.isEmpty()) {
                var ingredient = recipeIngredients.get(0);
                if (!ingredient.isEmpty()) {
                    var items = ingredient.getItems();
                    if (items.length > 0) {
                        ingredients.set(0, items[0].copy());
                    }
                }
            }

            return new LoadResult(true, RecipeType.COOKING, null, cookingType, 1,
                    resultItem, ingredients, "成功载入烹饪配方", originalRecipeTypeId);

        } catch (Exception e) {
            LOGGER.warn("解析烹饪配方失败", e);
            return new LoadResult(false, "解析烹饪配方失败: " + e.getMessage());
        }
    }

    /**
     * 加载Avaritia配方
     */
    private LoadResult loadAvaritiaRecipe(Recipe<?> recipe, ItemStack resultItem,
                                          CraftingMode mode, String originalRecipeTypeId) {
        try {
            var recipeIngredients = recipe.getIngredients();
            int ingredientCount = recipeIngredients.size();

            // 正确计算tier
            int tier = calculateAvaritiaTeir(ingredientCount);
            int gridSize = AvaritiaConfig.getGridSizeForTier(tier);
            int maxSlots = gridSize * gridSize;
            List<ItemStack> ingredients = new ArrayList<>();
            for (int i = 0; i < maxSlots; i++) {
                ingredients.add(ItemStack.EMPTY);
            }

            // 加载配方材料
            for (int i = 0; i < Math.min(recipeIngredients.size(), maxSlots); i++) {
                var ingredient = recipeIngredients.get(i);
                if (!ingredient.isEmpty()) {
                    var items = ingredient.getItems();
                    if (items.length > 0) {
                        ingredients.set(i, items[0].copy());
                    }
                }
            }

            return new LoadResult(true, RecipeType.AVARITIA, mode, null, tier,
                    resultItem, ingredients, "成功载入Avaritia配方", originalRecipeTypeId);

        } catch (Exception e) {
            LOGGER.warn("解析Avaritia配方失败", e);
            return new LoadResult(false, "解析Avaritia配方失败: " + e.getMessage());
        }
    }

    private int calculateAvaritiaTeir(int ingredientCount) {
        return AvaritiaConfig.getTierFromIngredientCount(ingredientCount);
    }

    /**
     * 加载锻造台配方
     */
    private LoadResult loadSmithingRecipe(Recipe<?> recipe, ItemStack resultItem, String originalRecipeTypeId) {
        try {
            var recipeIngredients = recipe.getIngredients();
            List<ItemStack> ingredients = new ArrayList<>();

            // 锻造台配方有3个槽位：模板、基础物品、添加材料
            for (int i = 0; i < 3; i++) {
                ingredients.add(ItemStack.EMPTY);
            }

            // 加载配方材料
            for (int i = 0; i < Math.min(recipeIngredients.size(), 3); i++) {
                var ingredient = recipeIngredients.get(i);
                if (ingredient != null && !ingredient.isEmpty()) {
                    var items = ingredient.getItems();
                    if (items != null && items.length > 0) {
                        ingredients.set(i, items[0].copy());
                    }
                }
            }

            LOGGER.info("成功加载锻造台配方: {}, 材料数: {}", recipe.getId(), ingredients.size());

            // 使用null作为recipeType，让系统根据originalRecipeTypeId自动识别
            return new LoadResult(true, null, null, null, 1,
                    resultItem, ingredients, "成功载入锻造台配方", originalRecipeTypeId);

        } catch (Exception e) {
            LOGGER.warn("解析锻造台配方失败", e);
            return new LoadResult(false, "解析锻造台配方失败: " + e.getMessage());
        }
    }

    // 配方类型识别辅助方法
    private boolean isShapedCraftingRecipe(String typeName, String className) {
        return typeName.contains("crafting_shaped") ||
                className.contains("shapedrecipe") ||
                typeName.contains("minecraft:crafting_shaped");
    }

    private boolean isShapelessCraftingRecipe(String typeName, String className) {
        return typeName.contains("crafting_shapeless") ||
                className.contains("shapelessrecipe") ||
                typeName.contains("minecraft:crafting_shapeless");
    }

    private boolean isSmeltingRecipe(String typeName, String className) {
        return typeName.contains("smelting") ||
                className.contains("smeltingrecipe") ||
                typeName.contains("minecraft:smelting");
    }

    private boolean isStonecutterRecipe(String typeName, String className) {
        return typeName.contains("stonecutter") ||
                typeName.contains("minecraft:stonecutter");
    }

    private boolean isBlastingRecipe(String typeName, String className) {
        return typeName.contains("blasting") ||
                className.contains("blastingrecipe") ||
                typeName.contains("minecraft:blasting");
    }

    private boolean isSmokingRecipe(String typeName, String className) {
        return typeName.contains("smoking") ||
                className.contains("smokingrecipe") ||
                typeName.contains("minecraft:smoking");
    }

    private boolean isCampfireRecipe(String typeName, String className) {
        return typeName.contains("campfire") ||
                className.contains("campfirerecipe") ||
                typeName.contains("minecraft:campfire_cooking");
    }

    private boolean isAvaritiaRecipe(String typeName, String className) {
        return typeName.contains("avaritia") ||
                className.contains("avaritia");
    }

    private boolean isSmithingRecipe(String typeName, String className) {
        return typeName.contains("smithing") ||
                className.contains("smithing") ||
                typeName.contains("minecraft:smithing_transform") ||
                typeName.contains("minecraft:smithing_trim");
    }

    /**
     * 获取可编辑的配方列表
     * 支持单人游戏（直接获取）和远程服务器（从缓存获取）
     */
    public List<UnifiedRecipeInfo> getEditableRecipes() {
        List<UnifiedRecipeInfo> recipes = new ArrayList<>();

        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                // 远程服务器，从缓存获取
                List<UnifiedRecipeInfo> cached = com.wzz.registerhelper.network.RecipeClientCache.getCachedRecipes();
                if (cached.isEmpty()) {
                    // 缓存为空，触发加载
                    LOGGER.info("配方缓存为空，正在请求服务器数据...");
                    com.wzz.registerhelper.network.RequestRecipeListPacket.sendToServer(1); // 1=可编辑配方
                    return recipes;
                }
                // 过滤掉黑名单配方
                for (UnifiedRecipeInfo info : cached) {
                    if (!info.isBlacklisted) {
                        recipes.add(info);
                    }
                }
                return recipes;
            }

            ServerLevel level = server.overworld();
            RecipeManager recipeManager = level.getRecipeManager();

            recipeManager.getRecipes().forEach(recipe -> {
                ResourceLocation id = recipe.getId();
                boolean isBlacklisted = RecipeBlacklistManager.isBlacklisted(id);
                boolean hasOverride = UnifiedRecipeOverrideManager.hasOverride(id);

                if (!isBlacklisted) {
                    String source = determineRecipeSource(id);
                    String description = recipe.getType() + " -> " + recipe.getResultItem(server.registryAccess()).getHoverName().getString();
                    recipes.add(new UnifiedRecipeInfo(id, source, false, hasOverride, description));
                }
            });

            recipes.sort((a, b) -> {
                int overrideCompare = Boolean.compare(b.hasOverride, a.hasOverride);
                if (overrideCompare != 0) return overrideCompare;

                int sourceCompare = a.source.compareTo(b.source);
                if (sourceCompare != 0) return sourceCompare;
                return a.id.toString().compareTo(b.id.toString());
            });

        } catch (Exception e) {
            LOGGER.error("获取可编辑配方列表失败", e);
        }

        return recipes;
    }

    /**
     * 获取所有配方列表（包括被禁用的）
     * 支持单人游戏（直接获取）和远程服务器（从缓存获取）
     */
    public List<UnifiedRecipeInfo> getAllRecipes() {
        List<UnifiedRecipeInfo> recipes = new ArrayList<>();

        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                // 远程服务器，从缓存获取
                List<UnifiedRecipeInfo> cached = com.wzz.registerhelper.network.RecipeClientCache.getCachedRecipes();
                if (cached.isEmpty()) {
                    // 缓存为空，触发加载
                    LOGGER.info("配方缓存为空，正在请求服务器数据...");
                    com.wzz.registerhelper.network.RequestRecipeListPacket.sendToServer(0); // 0=所有配方
                    return recipes;
                }
                return new ArrayList<>(cached);
            }

            ServerLevel level = server.overworld();
            RecipeManager recipeManager = level.getRecipeManager();

            recipeManager.getRecipes().forEach(recipe -> {
                ResourceLocation id = recipe.getId();
                boolean isBlacklisted = RecipeBlacklistManager.isBlacklisted(id);
                boolean hasOverride = UnifiedRecipeOverrideManager.hasOverride(id);
                String source = determineRecipeSource(id);
                String description = recipe.getType() + " -> " + recipe.getResultItem(server.registryAccess()).getHoverName().getString();

                recipes.add(new UnifiedRecipeInfo(id, source, isBlacklisted, hasOverride, description));
            });

            recipes.sort((a, b) -> {
                int blacklistCompare = Boolean.compare(a.isBlacklisted, b.isBlacklisted);
                if (blacklistCompare != 0) return blacklistCompare;

                int overrideCompare = Boolean.compare(b.hasOverride, a.hasOverride);
                if (overrideCompare != 0) return overrideCompare;

                int sourceCompare = a.source.compareTo(b.source);
                if (sourceCompare != 0) return sourceCompare;
                return a.id.toString().compareTo(b.id.toString());
            });

        } catch (Exception e) {
            LOGGER.error("获取配方列表失败", e);
        }

        return recipes;
    }

    /**
     * 检查是否为远程服务器（没有本地服务器实例）
     */
    public static boolean isRemoteServer() {
        return ServerLifecycleHooks.getCurrentServer() == null;
    }

    /**
     * 请求服务器刷新配方缓存
     */
    public void requestServerRecipes() {
        if (isRemoteServer()) {
            com.wzz.registerhelper.network.RecipeClientCache.clearCache();
            com.wzz.registerhelper.network.RequestRecipeListPacket.sendToServer(0);
        }
    }

    /**
     * 获取 RecipeManager（兼容客户端和服务器）
     */
    private RecipeManager getRecipeManager() {
        // 优先尝试从服务器获取
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getRecipeManager();
        }

        // 如果是客户端连接到远程服务器，从客户端level获取
        if (minecraft.level != null) {
            return minecraft.level.getRecipeManager();
        }

        return null;
    }

    /**
     * 获取 RegistryAccess
     */
    private RegistryAccess getRegistryAccess() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }

        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }

        return null;
    }

    /**
     * 查找配方信息
     */
    public UnifiedRecipeInfo findRecipeInfo(ResourceLocation recipeId) {
        return getAllRecipes().stream()
                .filter(info -> info.id.equals(recipeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 确定配方来源
     */
    private String determineRecipeSource(ResourceLocation recipeId) {
        String namespace = recipeId.getNamespace();
        String path = recipeId.getPath();

        if (namespace.equals("registerhelper") || path.startsWith("custom_") || path.contains("_custom_")) {
            return "自定义";
        }

        if (namespace.equals("minecraft")) {
            return "原版";
        }

        return "模组(" + namespace + ")";
    }

    /**
     * 判断是否为自定义配方
     */
    public boolean isCustomRecipe(ResourceLocation recipeId) {
        String namespace = recipeId.getNamespace();
        String path = recipeId.getPath();

        return namespace.equals("registerhelper") ||
                path.startsWith("custom_") ||
                path.contains("_custom_");
    }
}