package com.wzz.registerhelper.integration;

import com.wzz.registerhelper.core.RuntimeRecipeManager;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapelessTableCraftingRecipe;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AvaritiaIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isAvaritiaLoaded() {
        try {
            Class.forName("committee.nova.mods.avaritia.ModApi");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 创建Avaritia有形状工作台配方 - 旧版本（兼容性）
     */
    public static boolean createShapedTableRecipe(RuntimeRecipeManager recipeManager, ResourceLocation id, ItemStack result, int tier, String[] pattern, Object... ingredients) {
        return createShapedTableRecipe(recipeManager, id, result, tier, pattern, false, ingredients);
    }

    /**
     * 创建Avaritia有形状工作台配方 - 新版本（支持重载状态）
     */
    public static boolean createShapedTableRecipe(RuntimeRecipeManager recipeManager, ResourceLocation id, ItemStack result, int tier, String[] pattern, boolean isReloading, Object... ingredients) {
        if (!isAvaritiaLoaded()) {
            LOGGER.warn("Avaritia未加载，无法创建工作台配方");
            return false;
        }

        try {
            Map<Character, Ingredient> key = new HashMap<>();
            for (int i = 0; i < ingredients.length; i += 2) {
                if (i + 1 < ingredients.length) {
                    char symbol;
                    Object symbolObj = ingredients[i];

                    if (symbolObj instanceof Character) {
                        symbol = (Character) symbolObj;
                    } else if (symbolObj instanceof String str) {
                        if (!str.isEmpty()) {
                            symbol = str.charAt(0);  // 取字符串的第一个字符
                        } else {
                            LOGGER.warn("空字符串作为配方符号，跳过");
                            continue;
                        }
                    } else {
                        LOGGER.error("无效的符号类型: " + symbolObj.getClass().getName());
                        continue;
                    }

                    Object ingredient = ingredients[i + 1];

                    if (ingredient instanceof ItemStack stack) {
                        key.put(symbol, Ingredient.of(stack));
                    } else if (ingredient instanceof net.minecraft.world.item.Item item) {
                        key.put(symbol, Ingredient.of(item.getDefaultInstance()));
                    } else if (ingredient instanceof Ingredient ing) {
                        key.put(symbol, ing);
                    }
                }
            }

            int width = pattern[0].length();
            int height = pattern.length;
            NonNullList<Ingredient> inputs = NonNullList.withSize(width * height, Ingredient.EMPTY);

            for (int y = 0; y < height; y++) {
                String row = pattern[y];
                for (int x = 0; x < width; x++) {
                    if (x < row.length()) {
                        char symbol = row.charAt(x);
                        Ingredient ing = key.getOrDefault(symbol, Ingredient.EMPTY);
                        inputs.set(y * width + x, ing);
                    }
                }
            }
            ShapedTableCraftingRecipe recipe = new ShapedTableCraftingRecipe(id, width, height, inputs, result, tier);
            recipeManager.getPendingRecipes().put(id, recipe);
            return registerAvaritiaRecipe(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), id, recipe, isReloading);

        } catch (Exception e) {
            LOGGER.error("创建Avaritia有形状配方失败: " + id, e);
            return false;
        }
    }

    /**
     * 创建Avaritia无形状工作台配方 - 新版本（支持重载状态）
     */
    public static boolean createShapelessTableRecipe(ResourceLocation id, ItemStack result, int tier, List<ItemStack> ingredients, boolean isReloading) {
        if (!isAvaritiaLoaded()) {
            LOGGER.warn("Avaritia未加载，无法创建工作台配方");
            return false;
        }

        try {
            ShapelessTableCraftingRecipe recipe = addModShapelessRecipe(id, result, ingredients, tier);

            return registerAvaritiaRecipe(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get(), id, recipe, isReloading);

        } catch (Exception e) {
            LOGGER.error("创建Avaritia无形状配方失败: " + id, e);
            return false;
        }
    }

    public static ShapelessTableCraftingRecipe addModShapelessRecipe(ResourceLocation id, ItemStack result, List<ItemStack> ingredients, int tier) {
        List<ItemStack> arraylist = new ArrayList<>();
        for(ItemStack stack : ingredients) {
            if (stack == null) {
                throw new RuntimeException("Invalid shapeless recipes!");
            }

            arraylist.add(stack.copy());
        }
        return new ShapelessTableCraftingRecipe(id, getList(arraylist), result, tier);
    }

    private static NonNullList<Ingredient> getList(List<ItemStack> arrayList) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for(ItemStack stack : arrayList) {
            ingredients.add(Ingredient.of(stack));
        }
        return ingredients;
    }

    /**
     * 注册Avaritia配方到游戏中
     */
    private static boolean registerAvaritiaRecipe(RecipeType<?> recipeType, ResourceLocation id, Object recipe, boolean isReloading) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.warn("服务器未启动，无法注册Avaritia配方");
                return false;
            }

            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();

            Field recipesField;
            try {
                recipesField = RecipeManager.class.getDeclaredField("f_44007_");
            } catch (NoSuchFieldException e) {
                recipesField = RecipeManager.class.getDeclaredField("recipes");
            }

            recipesField.setAccessible(true);
            Map<RecipeType<?>, Map<ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>>> currentRecipes =
                    (Map<RecipeType<?>, Map<ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>>>) recipesField.get(recipeManager);
            Map<RecipeType<?>, Map<ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>>> newRecipes = new HashMap<>(currentRecipes);
            Map<ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>> typeRecipes = newRecipes.get(recipeType);
            if (typeRecipes == null) {
                typeRecipes = new HashMap<>();
                newRecipes.put(recipeType, typeRecipes);
            } else {
                typeRecipes = new HashMap<>(typeRecipes);
                newRecipes.put(recipeType, typeRecipes);
            }
            typeRecipes.put(id, (net.minecraft.world.item.crafting.Recipe<?>) recipe);
            recipesField.set(recipeManager, newRecipes);
            if (!isReloading) {
                syncRecipesToClients(server, recipeManager);
            } else {
                LOGGER.debug("跳过客户端同步（正在重载）: " + id);
            }

            return true;

        } catch (Exception e) {
            LOGGER.error("注册Avaritia配方失败: " + id, e);
            return false;
        }
    }

    /**
     * 同步配方到所有客户端
     */
    private static void syncRecipesToClients(MinecraftServer server, RecipeManager recipeManager) {
        try {
            net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket packet =
                    new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(recipeManager.getRecipes());

            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(packet);
            }

            LOGGER.debug("配方已同步到客户端");
        } catch (Exception e) {
            LOGGER.warn("同步配方到客户端失败", e);
        }
    }

    /**
     * 获取工作台等级对应的网格大小
     */
    public static int getGridSizeForTier(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 7;
            case 4 -> 9;
            default -> 3;
        };
    }

    /**
     * 验证配方模式是否适合指定等级
     */
    public static boolean validatePatternForTier(String[] pattern, int tier) {
        int maxSize = getGridSizeForTier(tier);
        if (pattern.length > maxSize) {
            LOGGER.info("模式行数超限: {} > {}", pattern.length, maxSize);
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            String row = pattern[i];
            if (row.length() > maxSize) {
                LOGGER.info("第{}行长度超限: {} > {}", i, row.length(), maxSize);
                return false;
            }
        }

        return true;
    }
}