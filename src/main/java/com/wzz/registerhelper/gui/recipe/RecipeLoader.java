package com.wzz.registerhelper.gui.recipe;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
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
        public final int avaritiaTeir;
        public final ItemStack resultItem;
        public final List<ItemStack> ingredients;
        public final String message;
        public ResourceLocation recipeId;

        public LoadResult(boolean success, String message) {
            this(success, null, null, null, 1, ItemStack.EMPTY, Collections.emptyList(), message);
        }

        public LoadResult(boolean success, RecipeType recipeType, CraftingMode craftingMode,
                          CookingType cookingType, int avaritiaTeir, ItemStack resultItem,
                          List<ItemStack> ingredients, String message) {
            this.success = success;
            this.recipeType = recipeType;
            this.craftingMode = craftingMode;
            this.cookingType = cookingType;
            this.avaritiaTeir = avaritiaTeir;
            this.resultItem = resultItem;
            this.ingredients = ingredients;
            this.message = message;
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
            if (minecraft.level == null) {
                return new LoadResult(false, "无法获取配方数据");
            }

            var recipeManager = minecraft.level.getRecipeManager();
            var recipe = recipeManager.byKey(recipeId).orElse(null);

            if (recipe == null) {
                return new LoadResult(false, "找不到配方: " + recipeId);
            }

            // 获取结果物品
            ItemStack resultItem = recipe.getResultItem(minecraft.level.registryAccess()).copy();

            // 根据配方类型和类名加载
            String recipeTypeName = recipe.getType().toString().toLowerCase();
            String recipeClassName = recipe.getClass().getSimpleName().toLowerCase();

            // 检查工作台配方
            if (isShapedCraftingRecipe(recipeTypeName, recipeClassName)) {
                return loadCraftingRecipe(recipe, resultItem, CraftingMode.SHAPED);
            } else if (isShapelessCraftingRecipe(recipeTypeName, recipeClassName)) {
                return loadCraftingRecipe(recipe, resultItem, CraftingMode.SHAPELESS);
            }
            // 检查烹饪配方
            else if (isSmeltingRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(recipe, resultItem, CookingType.SMELTING);
            } else if (isBlastingRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(recipe, resultItem, CookingType.BLASTING);
            } else if (isSmokingRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(recipe, resultItem, CookingType.SMOKING);
            } else if (isCampfireRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(recipe, resultItem, CookingType.CAMPFIRE);
            }
            // 检查Avaritia配方
            else if (isAvaritiaRecipe(recipeTypeName, recipeClassName)) {
                CraftingMode mode = recipeTypeName.contains("shaped") || recipeClassName.contains("shaped") ?
                        CraftingMode.SHAPED : CraftingMode.SHAPELESS;
                return loadAvaritiaRecipe(recipe, resultItem, mode);
            }

            return new LoadResult(false, "不支持的配方类型: " + recipeTypeName + " (类名: " + recipe.getClass().getName() + ")");

        } catch (Exception e) {
            LOGGER.error("加载配方失败", e);
            return new LoadResult(false, "加载配方失败: " + e.getMessage());
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

    /**
     * 加载工作台配方
     */
    private LoadResult loadCraftingRecipe(Recipe<?> recipe, ItemStack resultItem, CraftingMode mode) {
        try {
            List<ItemStack> ingredients = new ArrayList<>();

            // 初始化9个空槽位
            for (int i = 0; i < 9; i++) {
                ingredients.add(ItemStack.EMPTY);
            }

            // 对ShapedRecipe进行特殊处理
            if (mode == CraftingMode.SHAPED && recipe.getClass().getSimpleName().contains("ShapedRecipe")) {
                return loadShapedRecipePattern(recipe, resultItem, ingredients);
            } else {
                // 无序配方或其他类型的有序配方
                return loadGenericCraftingRecipe(recipe, resultItem, mode, ingredients);
            }

        } catch (Exception e) {
            LOGGER.error("解析工作台配方失败", e);
            return new LoadResult(false, "解析工作台配方失败: " + e.getMessage());
        }
    }

    /**
     * 加载ShapedRecipe的网格模式
     */
    private LoadResult loadShapedRecipePattern(Recipe<?> recipe, ItemStack resultItem, List<ItemStack> ingredients) {
        return loadGenericCraftingRecipe(recipe, resultItem, CraftingMode.SHAPED, ingredients);
    }

    /**
     * 加载通用工作台配方
     */
    private LoadResult loadGenericCraftingRecipe(Recipe<?> recipe, ItemStack resultItem,
                                                 CraftingMode mode, List<ItemStack> ingredients) {
        var recipeIngredients = recipe.getIngredients();

        // 加载配方材料
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
                resultItem, ingredients, "成功载入工作台配方");
    }

    /**
     * 加载烹饪配方
     */
    private LoadResult loadCookingRecipe(Recipe<?> recipe, ItemStack resultItem, CookingType cookingType) {
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
                    resultItem, ingredients, "成功载入烹饪配方");

        } catch (Exception e) {
            LOGGER.warn("解析烹饪配方失败", e);
            return new LoadResult(false, "解析烹饪配方失败: " + e.getMessage());
        }
    }

    /**
     * 加载Avaritia配方
     */
    private LoadResult loadAvaritiaRecipe(Recipe<?> recipe, ItemStack resultItem, CraftingMode mode) {
        try {
            var recipeIngredients = recipe.getIngredients();
            int ingredientCount = recipeIngredients.size();

            // 确定网格大小
            int tier = AvaritiaConfig.getTierFromIngredientCount(ingredientCount);
            int maxSlots = AvaritiaConfig.getGridSizeForTier(tier) * AvaritiaConfig.getGridSizeForTier(tier);

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
                    resultItem, ingredients, "成功载入Avaritia配方");

        } catch (Exception e) {
            LOGGER.warn("解析Avaritia配方失败", e);
            return new LoadResult(false, "解析Avaritia配方失败: " + e.getMessage());
        }
    }

    /**
     * 获取可编辑的配方列表
     */
    public List<UnifiedRecipeInfo> getEditableRecipes() {
        List<UnifiedRecipeInfo> recipes = new ArrayList<>();

        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
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
     */
    public List<UnifiedRecipeInfo> getAllRecipes() {
        List<UnifiedRecipeInfo> recipes = new ArrayList<>();

        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return recipes;
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