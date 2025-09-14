package com.wzz.registerhelper.core;

import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class RuntimeRecipeManager {
    private final String modId;
    private final Map<ResourceLocation, Recipe<?>> pendingRecipes = new HashMap<>();

    /**
     * 构造函数
     * @param modId 模组ID
     */
    public RuntimeRecipeManager(String modId) {
        this.modId = modId;
    }

    public Map<ResourceLocation, Recipe<?>> getPendingRecipes() {
        return pendingRecipes;
    }

    /**
     * 添加有序合成表（支持ItemStack作为结果，保留NBT数据）
     */
    public RuntimeRecipeManager addShapedRecipe(ItemStack result, String[] pattern, Object... ingredients) {
        return addShapedRecipe(generateRecipeId(result.getItem()), result, pattern, ingredients);
    }

    /**
     * 添加有序合成表（自定义ID + ItemStack结果）
     */
    public RuntimeRecipeManager addShapedRecipe(ResourceLocation id, ItemStack result, String[] pattern, Object... ingredients) {
        Map<Character, Ingredient> key = new HashMap<>();
        for (int i = 0; i < ingredients.length; i += 2) {
            char symbol = (Character) ingredients[i];
            Object ingredient = ingredients[i + 1];

            if (ingredient instanceof ItemLike item) {
                key.put(symbol, Ingredient.of(item));
            } else if (ingredient instanceof Ingredient ing) {
                key.put(symbol, ing);
            } else if (ingredient instanceof ItemStack stack) {
                key.put(symbol, Ingredient.of(stack));
            }
        }
        int height = pattern.length;
        int width = pattern[0].length();

        NonNullList<Ingredient> inputs = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int y = 0; y < height; y++) {
            String row = pattern[y];
            for (int x = 0; x < width; x++) {
                char symbol = row.charAt(x);
                Ingredient ing = key.getOrDefault(symbol, Ingredient.EMPTY);
                inputs.set(y * width + x, ing);
            }
        }

        ShapedRecipe recipe = new ShapedRecipe(
                id,
                "",
                CraftingBookCategory.MISC,
                width,
                height,
                inputs,
                result.copy(),
                true
        );

        pendingRecipes.put(id, recipe);
        return this;
    }

    /**
     * 添加无序合成表（支持ItemStack作为结果）
     */
    public RuntimeRecipeManager addShapelessRecipe(ItemStack result, Object... ingredients) {
        return addShapelessRecipe(generateRecipeId(result.getItem()), result, ingredients);
    }

    /**
     * 添加无序合成表（自定义ID + ItemStack结果）
     */
    public RuntimeRecipeManager addShapelessRecipe(ResourceLocation id, ItemStack result, Object... ingredients) {
        NonNullList<Ingredient> ingredientList = NonNullList.create();

        for (Object ingredient : ingredients) {
            if (ingredient instanceof ItemLike item) {
                ingredientList.add(Ingredient.of(item));
            } else if (ingredient instanceof Ingredient ing) {
                ingredientList.add(ing);
            } else if (ingredient instanceof ItemStack stack) {
                ingredientList.add(Ingredient.of(stack));
            }
        }

        ShapelessRecipe recipe = new ShapelessRecipe(
                id,
                "", // group
                CraftingBookCategory.MISC,
                result.copy(),
                ingredientList
        );

        pendingRecipes.put(id, recipe);
        return this;
    }

    protected String getItemName(Item item) {
        ResourceLocation itemLocation = item.builtInRegistryHolder().key().location();
        return itemLocation.getPath();
    }

    /**
     * 添加3x3有序合成表
     * @param result 产出物品
     * @param count 产出数量
     * @param pattern 合成图案（3行字符串数组）
     * @param ingredients 字符对应的材料（字符, 物品, 字符, 物品...）
     */
    public RuntimeRecipeManager addShapedRecipe(ItemLike result, int count, String[] pattern, Object... ingredients) {
        return addShapedRecipe(generateRecipeId(result), result, count, pattern, ingredients);
    }

    /**
     * 添加有序合成表（自定义ID）
     */
    public RuntimeRecipeManager addShapedRecipe(ResourceLocation id, ItemLike result, int count, String[] pattern, Object... ingredients) {
        // 构建材料映射
        Map<Character, Ingredient> key = new HashMap<>();
        for (int i = 0; i < ingredients.length; i += 2) {
            char symbol = (Character) ingredients[i];
            Object ingredient = ingredients[i + 1];

            if (ingredient instanceof ItemLike item) {
                key.put(symbol, Ingredient.of(item));
            } else if (ingredient instanceof Ingredient ing) {
                key.put(symbol, ing);
            } else if (ingredient instanceof ItemStack stack) {
                key.put(symbol, Ingredient.of(stack));
            }
        }

        int height = pattern.length;
        int width = pattern[0].length();

        NonNullList<Ingredient> inputs = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int y = 0; y < height; y++) {
            String row = pattern[y];
            for (int x = 0; x < width; x++) {
                char symbol = row.charAt(x);
                Ingredient ing = key.getOrDefault(symbol, Ingredient.EMPTY);
                inputs.set(y * width + x, ing);
            }
        }

        ShapedRecipe recipe = new ShapedRecipe(
                id,
                "", // group
                CraftingBookCategory.MISC,
                width,
                height,
                inputs,
                new ItemStack(result, count),
                true
        );

        pendingRecipes.put(id, recipe);
        return this;
    }

    /**
     * 添加无序合成表
     */
    public RuntimeRecipeManager addShapelessRecipe(ItemLike result, int count, Object... ingredients) {
        return addShapelessRecipe(generateRecipeId(result), result, count, ingredients);
    }

    /**
     * 添加无序合成表（自定义ID）
     */
    public RuntimeRecipeManager addShapelessRecipe(ResourceLocation id, ItemLike result, int count, Object... ingredients) {
        NonNullList<Ingredient> ingredientList = NonNullList.create();

        for (Object ingredient : ingredients) {
            if (ingredient instanceof ItemLike item) {
                ingredientList.add(Ingredient.of(item));
            } else if (ingredient instanceof Ingredient ing) {
                ingredientList.add(ing);
            } else if (ingredient instanceof ItemStack stack) {
                ingredientList.add(Ingredient.of(stack));
            }
        }

        ShapelessRecipe recipe = new ShapelessRecipe(
                id,
                "", // group
                CraftingBookCategory.MISC,
                new ItemStack(result, count),
                ingredientList
        );

        pendingRecipes.put(id, recipe);
        return this;
    }

    /**
     * 添加熔炉烧炼配方
     */
    public RuntimeRecipeManager addSmeltingRecipe(ItemLike ingredient, ItemLike result, float experience, int cookingTime) {
        return addSmeltingRecipe(generateSmeltingRecipeId(result), ingredient, result, experience, cookingTime);
    }

    /**
     * 添加熔炉烧炼配方（自定义ID）
     */
    public RuntimeRecipeManager addSmeltingRecipe(ResourceLocation id, ItemLike ingredient, ItemLike result, float experience, int cookingTime) {
        SmeltingRecipe recipe = new SmeltingRecipe(
                id,
                "", // group
                CookingBookCategory.MISC,
                Ingredient.of(ingredient),
                new ItemStack(result),
                experience,
                cookingTime
        );

        pendingRecipes.put(id, recipe);
        return this;
    }

    /**
     * 添加熔炉烧炼配方（默认参数）
     */
    public RuntimeRecipeManager addSmeltingRecipe(ItemLike ingredient, ItemLike result) {
        return addSmeltingRecipe(ingredient, result, 0.7f, 200);
    }

    /**
     * 添加高炉烧炼配方
     */
    public RuntimeRecipeManager addBlastingRecipe(ItemLike ingredient, ItemLike result, float experience, int cookingTime) {
        return addBlastingRecipe(generateBlastingRecipeId(result), ingredient, result, experience, cookingTime);
    }

    /**
     * 添加高炉烧炼配方（自定义ID）
     */
    public RuntimeRecipeManager addBlastingRecipe(ResourceLocation id, ItemLike ingredient, ItemLike result, float experience, int cookingTime) {
        BlastingRecipe recipe = new BlastingRecipe(
                id,
                "", // group
                CookingBookCategory.MISC,
                Ingredient.of(ingredient),
                new ItemStack(result),
                experience,
                cookingTime
        );

        pendingRecipes.put(id, recipe);
        return this;
    }

    /**
     * 添加高炉烧炼配方（默认参数）
     */
    public RuntimeRecipeManager addBlastingRecipe(ItemLike ingredient, ItemLike result) {
        return addBlastingRecipe(ingredient, result, 0.7f, 100);
    }

    /**
     * 添加营火烹饪配方
     */
    public RuntimeRecipeManager addCampfireRecipe(ItemLike ingredient, ItemLike result, float experience, int cookingTime) {
        return addCampfireRecipe(generateCampfireRecipeId(result), ingredient, result, experience, cookingTime);
    }

    /**
     * 添加营火烹饪配方（自定义ID）
     */
    public RuntimeRecipeManager addCampfireRecipe(ResourceLocation id, ItemLike ingredient, ItemLike result, float experience, int cookingTime) {
        CampfireCookingRecipe recipe = new CampfireCookingRecipe(
                id,
                "", // group
                CookingBookCategory.MISC,
                Ingredient.of(ingredient),
                new ItemStack(result),
                experience,
                cookingTime
        );

        pendingRecipes.put(id, recipe);
        return this;
    }

    /**
     * 添加营火烹饪配方（默认参数）
     */
    public RuntimeRecipeManager addCampfireRecipe(ItemLike ingredient, ItemLike result) {
        return addCampfireRecipe(ingredient, result, 0.35f, 600);
    }

    /**
     * 添加烟熏炉烹饪配方
     */
    public RuntimeRecipeManager addSmokingRecipe(ItemLike ingredient, ItemLike result, float experience, int cookingTime) {
        return addSmokingRecipe(generateSmokingRecipeId(result), ingredient, result, experience, cookingTime);
    }

    /**
     * 添加烟熏炉烹饪配方（自定义ID）
     */
    public RuntimeRecipeManager addSmokingRecipe(ResourceLocation id, ItemLike ingredient, ItemLike result, float experience, int cookingTime) {
        SmokingRecipe recipe = new SmokingRecipe(
                id,
                "", // group
                CookingBookCategory.MISC,
                Ingredient.of(ingredient),
                new ItemStack(result),
                experience,
                cookingTime
        );

        pendingRecipes.put(id, recipe);
        return this;
    }

    /**
     * 添加烟熏炉烹饪配方（默认参数）
     */
    public RuntimeRecipeManager addSmokingRecipe(ItemLike ingredient, ItemLike result) {
        return addSmokingRecipe(ingredient, result, 0.35f, 100);
    }

    /**
     * 生成合成表ID
     */
    private ResourceLocation generateRecipeId(ItemLike item) {
        String itemName = getItemName(item);
        return new ResourceLocation(modId, itemName + "_recipe");
    }

    private ResourceLocation generateSmeltingRecipeId(ItemLike item) {
        String itemName = getItemName(item);
        return new ResourceLocation(modId, itemName + "_smelting");
    }

    private ResourceLocation generateBlastingRecipeId(ItemLike item) {
        String itemName = getItemName(item);
        return new ResourceLocation(modId, itemName + "_blasting");
    }

    private ResourceLocation generateCampfireRecipeId(ItemLike item) {
        String itemName = getItemName(item);
        return new ResourceLocation(modId, itemName + "_campfire");
    }

    private ResourceLocation generateSmokingRecipeId(ItemLike item) {
        String itemName = getItemName(item);
        return new ResourceLocation(modId, itemName + "_smoking");
    }

    /**
     * 获取物品名称
     */
    private String getItemName(ItemLike item) {
        ResourceLocation itemLocation = item.asItem().builtInRegistryHolder().key().location();
        return itemLocation.getPath();
    }
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * 注册这个实例的所有合成表
     */
    public void registerRecipes() {
        if (pendingRecipes.isEmpty()) {
            LOGGER.warn("没有待注册的配方");
            return;
        }
        try {
            var currentServer = ServerLifecycleHooks.getCurrentServer();
            if (currentServer == null) {
                LOGGER.error("getCurrentServer() 返回 null");
                return;
            }
            ServerLevel serverLevel = currentServer.overworld();
            if (serverLevel == null) {
                LOGGER.error("无法获取服务器世界");
                return;
            }
            RecipeManager recipeManager = serverLevel.getRecipeManager();
            if (recipeManager == null) {
                LOGGER.error("无法获取配方管理器");
                return;
            }
            java.lang.reflect.Field recipesField;
            try {
                recipesField = RecipeManager.class.getDeclaredField("f_44007_");
            } catch (NoSuchFieldException e) {
                recipesField = RecipeManager.class.getDeclaredField("recipes");
            }
            recipesField.setAccessible(true);
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> currentRecipes =
                    (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) recipesField.get(recipeManager);
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> newRecipes = new HashMap<>();
            for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> entry : currentRecipes.entrySet()) {
                newRecipes.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            for (Map.Entry<ResourceLocation, Recipe<?>> entry : pendingRecipes.entrySet()) {
                try {
                    Recipe<?> recipe = entry.getValue();
                    RecipeType<?> type = recipe.getType();
                    ResourceLocation id = entry.getKey();
                    newRecipes.computeIfAbsent(type, k -> new HashMap<>());
                    newRecipes.get(type).put(id, recipe);
                } catch (Exception e) {
                    LOGGER.error("添加单个配方失败: {}", entry.getKey(), e);
                }
            }
            recipesField.set(recipeManager, newRecipes);
            try {
                for (var player : currentServer.getPlayerList().getPlayers()) {
                    var updatePacket = new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(
                            recipeManager.getRecipes()
                    );
                    player.connection.send(updatePacket);
                }
            } catch (Exception e) {
                LOGGER.warn("同步配方到客户端失败: ", e);
            }

        } catch (Exception e) {
            LOGGER.error("模组 [{}] 注册运行时合成表失败: ", modId, e);
        }
    }

    /**
     * 清除所有待注册的合成表
     */
    public void clearPendingRecipes() {
        pendingRecipes.clear();
    }

    /**
     * 获取待注册合成表数量
     */
    public int getPendingRecipeCount() {
        return pendingRecipes.size();
    }

    /**
     * 获取模组ID
     */
    public String getModId() {
        return modId;
    }
}