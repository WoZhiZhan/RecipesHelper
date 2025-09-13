package com.wzz.registerhelper.core;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.integration.AvaritiaIntegration;
import com.wzz.registerhelper.network.TriggerReloadPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.*;

public class SuperRuntimeRecipeManager extends RuntimeRecipeManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private boolean isReloading = false;

    public SuperRuntimeRecipeManager(String modId) {
        super(modId);
    }

    /**
     * 删除指定ID的配方（从游戏内存中删除）
     */
    public boolean deleteRecipeFromMemory(ResourceLocation recipeId) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.warn("服务器实例为空，无法删除配方");
                return false;
            }

            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();

            // 通过反射获取配方映射
            Field recipesField;
            try {
                recipesField = RecipeManager.class.getDeclaredField("f_44007_");
            } catch (NoSuchFieldException e) {
                recipesField = RecipeManager.class.getDeclaredField("recipes");
            }

            recipesField.setAccessible(true);
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> currentRecipes =
                    (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) recipesField.get(recipeManager);

            boolean deleted = false;
            for (Map<ResourceLocation, Recipe<?>> typeRecipes : currentRecipes.values()) {
                if (typeRecipes.remove(recipeId) != null) {
                    deleted = true;
                    LOGGER.info("从内存中删除配方: {}", recipeId);
                    break;
                }
            }

            if (getPendingRecipes().remove(recipeId) != null) {
                LOGGER.debug("从待注册列表中删除配方: {}", recipeId);
                deleted = true;
            }

            return deleted;

        } catch (Exception e) {
            LOGGER.error("删除配方失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 删除配方（同时删除内存和JSON文件）
     */
    public boolean deleteRecipe(ResourceLocation recipeId) {
        boolean deletedFromMemory = deleteRecipeFromMemory(recipeId);
        boolean deletedFromJson = RecipeJsonManager.deleteRecipe(recipeId.toString());

        if (deletedFromMemory || deletedFromJson) {
            LOGGER.info("成功删除配方: {} (内存: {}, JSON: {})", recipeId, deletedFromMemory, deletedFromJson);
            return true;
        }

        return false;
    }

    /**
     * 检查配方是否存在于内存中
     */
    public boolean recipeExistsInMemory(ResourceLocation recipeId) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return false;

            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();

            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (recipe.getId().equals(recipeId)) {
                    return true;
                }
            }

            // 同时检查待注册列表
            return getPendingRecipes().containsKey(recipeId);

        } catch (Exception e) {
            LOGGER.error("检查配方存在性失败: " + recipeId, e);
            return false;
        }
    }

    @Override
    public RuntimeRecipeManager addShapedRecipe(ResourceLocation id, ItemStack result, String[] pattern, Object... ingredients) {
        if (recipeExistsInMemory(id)) {
            deleteRecipeFromMemory(id);
        }
        super.addShapedRecipe(id, result, pattern, ingredients);
        if (!isReloading) {
            // 保存到JSON
            RecipeJsonManager.RecipeData data = new RecipeJsonManager.RecipeData();
            data.id = id.toString(); // 确保ID正确
            data.type = "shaped";
            data.result = getItemResourceLocation(result.getItem()).toString();
            data.count = result.getCount();
            data.pattern = pattern;
            data.materialMapping = convertIngredientsForJson(ingredients);

            RecipeJsonManager.saveRecipe(id.toString(), data);
        }
        return this;
    }

    @Override
    public RuntimeRecipeManager addShapelessRecipe(ResourceLocation id, ItemStack result, Object... ingredients) {
        if (recipeExistsInMemory(id)) {
            deleteRecipeFromMemory(id);
        }
        super.addShapelessRecipe(id, result, ingredients);

        if (!isReloading) {
            // 保存到JSON
            RecipeJsonManager.RecipeData data = new RecipeJsonManager.RecipeData();
            data.id = id.toString(); // 确保ID正确
            data.type = "shapeless";
            data.result = getItemResourceLocation(result.getItem()).toString();
            data.count = result.getCount();
            data.ingredients = convertIngredientsToStringArray(ingredients);

            RecipeJsonManager.saveRecipe(id.toString(), data);
        }
        return this;
    }

    @Override
    public RuntimeRecipeManager addSmeltingRecipe(ResourceLocation id, ItemLike ingredient, ItemLike result, float experience, int cookingTime) {
        if (recipeExistsInMemory(id)) {
            deleteRecipeFromMemory(id);
        }
        super.addSmeltingRecipe(id, ingredient, result, experience, cookingTime);
        if (!isReloading) {
            RecipeJsonManager.RecipeData data = new RecipeJsonManager.RecipeData();
            data.id = id.toString(); // 确保ID正确
            data.type = "smelting";
            data.result = getItemResourceLocation(result.asItem()).toString();
            data.count = 1;
            data.ingredients = new String[]{getItemResourceLocation(ingredient.asItem()).toString()};
            data.experience = experience;
            data.cookingTime = cookingTime;

            RecipeJsonManager.saveRecipe(id.toString(), data);
        }
        return this;
    }

    public SuperRuntimeRecipeManager addAvaritiaTableRecipe(ResourceLocation id, ItemStack result, int tier, String[] pattern, Object... ingredients) {
        if (!AvaritiaIntegration.isAvaritiaLoaded()) {
            LOGGER.warn("Avaritia未加载，无法添加工作台配方");
            return this;
        }
        try {
            // 如果配方已存在，先删除旧的
            if (recipeExistsInMemory(id)) {
                LOGGER.info("检测到重复配方ID，删除旧配方: {}", id);
                deleteRecipeFromMemory(id);
            }

            // 验证等级
            if (tier < 1 || tier > 4) {
                LOGGER.warn("无效的工作台等级: {}，使用默认等级1", tier);
                tier = 1;
            }

            // 验证模式大小
            if (!AvaritiaIntegration.validatePatternForTier(pattern, tier)) {
                LOGGER.warn("配方模式大小超过等级 {} 支持的最大大小", tier);
                return this;
            }

            // 直接注册到游戏中
            boolean success = AvaritiaIntegration.createShapedTableRecipe(this, id, result, tier, pattern, ingredients);

            if (success) {
                if (!isReloading) {
                    // 保存到JSON
                    RecipeJsonManager.RecipeData data = new RecipeJsonManager.RecipeData();
                    data.id = id.toString(); // 确保ID正确
                    data.type = "avaritia_shaped"; // 修正类型名称
                    data.result = getItemResourceLocation(result.getItem()).toString();
                    data.count = result.getCount();
                    data.pattern = pattern;
                    data.materialMapping = convertIngredientsForJson(ingredients);
                    data.tier = tier;

                    RecipeJsonManager.saveRecipe(id.toString(), data);
                }

                LOGGER.info("成功添加Avaritia工作台配方: {} (等级{})", id, tier);
            } else {
                LOGGER.warn("添加Avaritia工作台配方失败: {}", id);
            }

            return this;

        } catch (Exception e) {
            LOGGER.error("添加Avaritia工作台配方失败: " + id, e);
            return this;
        }
    }

    /**
     * 添加Avaritia无形状工作台配方
     */
    public SuperRuntimeRecipeManager addAvaritiaShapelessRecipe(ResourceLocation id, ItemStack result, int tier, List<ItemStack> ingredients) {
        if (!AvaritiaIntegration.isAvaritiaLoaded()) {
            LOGGER.warn("Avaritia未加载，无法添加工作台配方");
            return this;
        }

        try {
            // 如果配方已存在，先删除旧的
            if (recipeExistsInMemory(id)) {
                LOGGER.info("检测到重复配方ID，删除旧配方: {}", id);
                deleteRecipeFromMemory(id);
            }

            // 验证等级
            if (tier < 1 || tier > 4) {
                LOGGER.warn("无效的工作台等级: {}，使用默认等级1", tier);
                tier = 1;
            }

            // 传递重载状态给AvaritiaIntegration
            boolean success = AvaritiaIntegration.createShapelessTableRecipe(id, result, tier, ingredients, isReloading);

            if (success) {
                // 只有在非重载状态下才保存到JSON
                if (!isReloading) {
                    // 保存到JSON
                    RecipeJsonManager.RecipeData data = new RecipeJsonManager.RecipeData();
                    data.id = id.toString(); // 确保ID正确
                    data.type = "avaritia_shapeless";
                    data.result = getItemResourceLocation(result.getItem()).toString();
                    data.count = result.getCount();
                    data.ingredients = ingredients.stream()
                            .map(stack -> getItemResourceLocation(stack.getItem()).toString())
                            .toArray(String[]::new);
                    data.tier = tier;

                    RecipeJsonManager.saveRecipe(id.toString(), data);
                }

                LOGGER.info("成功添加Avaritia无形状工作台配方: {} (等级{})", id, tier);
            } else {
                LOGGER.warn("添加Avaritia无形状工作台配方失败: {}", id);
            }

            return this;

        } catch (Exception e) {
            LOGGER.error("添加Avaritia无形状工作台配方失败: " + id, e);
            return this;
        }
    }

    // ==================== 修复后的reloadFromJson方法 ====================

    /**
     * 修复后的reloadFromJson方法
     */
    public void reloadFromJson() {
        isReloading = true; // 设置重载标记

        try {
            clearPendingRecipes();

            List<String> recipeIds = RecipeJsonManager.getAllSavedRecipeIds();
            int loadedCount = 0;

            LOGGER.info("开始从JSON重新加载 {} 个配方...", recipeIds.size());

            for (String recipeId : recipeIds) {
                RecipeJsonManager.RecipeData data = RecipeJsonManager.loadRecipe(recipeId);
                if (data != null) {
                    try {
                        // 先解析ResourceLocation
                        ResourceLocation id = parseResourceLocation(recipeId);
                        if (id == null) {
                            LOGGER.warn("无效的配方ID格式: " + recipeId);
                            continue;
                        }

                        // 根据配方类型分别处理，支持新的类型名称
                        switch (data.type) {
                            case "shaped" -> recreateShapedRecipe(id, data);
                            case "shapeless" -> recreateShapelessRecipe(id, data);
                            case "smelting" -> recreateSmeltingRecipe(id, data);
                            case "avaritia_table", "avaritia_shaped" -> recreateAvaritiaShapedRecipe(id, data); // 支持两种名称
                            case "avaritia_shapeless" -> recreateAvaritiaShapelessRecipe(id, data);
                            default -> LOGGER.warn("未知的配方类型: {} (配方ID: {})", data.type, recipeId);
                        }
                        loadedCount++;
                    } catch (Exception e) {
                        LOGGER.warn("重新加载配方失败: {} - {}", recipeId, e.getMessage());
                    }
                }
            }

            LOGGER.info("从JSON文件重新加载了 {} 个配方", loadedCount);

        } finally {
            isReloading = false; // 确保重载标记被重置
        }
    }

    // ==================== 现有方法保持不变 ====================

    private void recreateShapedRecipe(ResourceLocation id, RecipeJsonManager.RecipeData data) {
        try {
            ResourceLocation resultLoc = parseResourceLocation(data.result);
            if (resultLoc == null) {
                LOGGER.warn("无效的物品ID: " + data.result);
                return;
            }

            Item resultItem = ForgeRegistries.ITEMS.getValue(resultLoc);
            if (resultItem != null) {
                ItemStack result = new ItemStack(resultItem, data.count);
                Object[] convertedMapping = convertMaterialMappingFromJson(data.materialMapping);
                addShapedRecipe(id, result, data.pattern, convertedMapping);
            }
        } catch (Exception e) {
            LOGGER.warn("重建shaped配方失败: {} - {}", id, e.getMessage());
        }
    }

    private void recreateShapelessRecipe(ResourceLocation id, RecipeJsonManager.RecipeData data) {
        try {
            ResourceLocation resultLoc = parseResourceLocation(data.result);
            if (resultLoc == null) {
                LOGGER.warn("无效的物品ID: " + data.result);
                return;
            }

            Item resultItem = ForgeRegistries.ITEMS.getValue(resultLoc);
            if (resultItem != null) {
                ItemStack result = new ItemStack(resultItem, data.count);
                Object[] ingredients = new Object[data.ingredients.length];
                for (int i = 0; i < data.ingredients.length; i++) {
                    ResourceLocation itemLoc = parseResourceLocation(data.ingredients[i]);
                    if (itemLoc != null) {
                        Item item = ForgeRegistries.ITEMS.getValue(itemLoc);
                        if (item != null) {
                            ingredients[i] = item;
                        }
                    }
                }
                addShapelessRecipe(id, result, ingredients);
            }
        } catch (Exception e) {
            LOGGER.warn("重建shapeless配方失败: {} - {}", id, e.getMessage());
        }
    }

    private void recreateSmeltingRecipe(ResourceLocation id, RecipeJsonManager.RecipeData data) {
        try {
            ResourceLocation resultLoc = parseResourceLocation(data.result);
            ResourceLocation ingredientLoc = parseResourceLocation(data.ingredients[0]);

            if (resultLoc == null || ingredientLoc == null) {
                LOGGER.warn("无效的物品ID - result: {}, ingredient: {}", data.result, data.ingredients[0]);
                return;
            }

            Item resultItem = ForgeRegistries.ITEMS.getValue(resultLoc);
            Item ingredientItem = ForgeRegistries.ITEMS.getValue(ingredientLoc);

            if (resultItem != null && ingredientItem != null) {
                addSmeltingRecipe(id, ingredientItem, resultItem, data.experience, data.cookingTime);
            }
        } catch (Exception e) {
            LOGGER.warn("重建smelting配方失败: {} - {}", id, e.getMessage());
        }
    }

    /**
     * 重建Avaritia有形状配方
     */
    private void recreateAvaritiaShapedRecipe(ResourceLocation id, RecipeJsonManager.RecipeData data) {
        try {
            ResourceLocation resultLoc = parseResourceLocation(data.result);
            if (resultLoc == null) {
                LOGGER.warn("无效的物品ID: " + data.result);
                return;
            }

            Item resultItem = ForgeRegistries.ITEMS.getValue(resultLoc);
            if (resultItem != null) {
                ItemStack result = new ItemStack(resultItem, data.count);
                Object[] convertedMapping = convertMaterialMappingFromJson(data.materialMapping);
                addAvaritiaTableRecipe(id, result, data.tier, data.pattern, convertedMapping);
            }
        } catch (Exception e) {
            LOGGER.warn("重建Avaritia有形状配方失败: {} - {}", id, e.getMessage());
        }
    }

    /**
     * 重建Avaritia无形状配方
     */
    private void recreateAvaritiaShapelessRecipe(ResourceLocation id, RecipeJsonManager.RecipeData data) {
        try {
            ResourceLocation resultLoc = parseResourceLocation(data.result);
            if (resultLoc == null) {
                LOGGER.warn("无效的物品ID: " + data.result);
                return;
            }

            Item resultItem = ForgeRegistries.ITEMS.getValue(resultLoc);
            if (resultItem != null) {
                ItemStack result = new ItemStack(resultItem, data.count);

                List<ItemStack> ingredients = new ArrayList<>();
                for (String ingredientStr : data.ingredients) {
                    ResourceLocation itemLoc = parseResourceLocation(ingredientStr);
                    if (itemLoc != null) {
                        Item item = ForgeRegistries.ITEMS.getValue(itemLoc);
                        if (item != null) {
                            ingredients.add(new ItemStack(item));
                        }
                    }
                }
                addAvaritiaShapelessRecipe(id, result, data.tier, ingredients);
            }
        } catch (Exception e) {
            LOGGER.warn("重建Avaritia无形状配方失败: {} - {}", id, e.getMessage());
        }
    }

    /**
     * 添加Avaritia工作台配方（自动生成ID）
     */
    public SuperRuntimeRecipeManager addAvaritiaTableRecipe(ItemStack result, int tier, String[] pattern, Object... ingredients) {
        ResourceLocation id = generateAvaritiaRecipeId(result, tier);
        return addAvaritiaTableRecipe(id, result, tier, pattern, ingredients);
    }

    /**
     * 生成Avaritia配方ID
     */
    private ResourceLocation generateAvaritiaRecipeId(ItemStack result, int tier) {
        String itemName = getItemName(result.getItem());
        return new ResourceLocation(getModId(), itemName + "_avaritia_t" + tier + "_recipe");
    }

    /**
     * 获取物品名称（用于生成配方ID）
     */
    protected String getItemName(Item item) {
        ResourceLocation location = getItemResourceLocation(item);
        return location.getPath().replaceAll("[^a-zA-Z0-9_]", "_");
    }

    @Override
    public void registerRecipes() {
        super.registerRecipes();
        triggerClientResourceReload();
    }

    /**
     * 触发服务端配方重载
     */
    public void triggerServerRecipeReload() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.warn("服务器实例为空，无法重载配方");
                return;
            }
            server.execute(() -> {
                try {
                    refreshRecipeManager(server);
                    syncRecipesToAllClients(server);
                    LOGGER.info("服务端配方重载完成");
                } catch (Exception e) {
                    LOGGER.error("服务端配方重载失败", e);
                }
            });

        } catch (Exception e) {
            LOGGER.error("触发服务端配方重载失败", e);
        }
    }


    /**
     * 刷新RecipeManager内部状态
     */
    private void refreshRecipeManager(MinecraftServer server) {
        try {
            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();

            Field recipesField;
            try {
                recipesField = RecipeManager.class.getDeclaredField("f_44007_");
            } catch (NoSuchFieldException e) {
                recipesField = RecipeManager.class.getDeclaredField("recipes");
            }

            recipesField.setAccessible(true);
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> currentRecipes =
                    (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) recipesField.get(recipeManager);

            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> refreshedRecipes = new HashMap<>();
            for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> entry : currentRecipes.entrySet()) {
                refreshedRecipes.put(entry.getKey(), Map.copyOf(entry.getValue()));
            }

            recipesField.set(recipeManager, Collections.unmodifiableMap(refreshedRecipes));
        } catch (Exception e) {
            LOGGER.error("刷新RecipeManager失败", e);
        }
    }

    /**
     * 同步配方到所有客户端
     */
    private void syncRecipesToAllClients(MinecraftServer server) {
        try {
            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();
            ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(
                    recipeManager.getRecipes()
            );
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(packet);
            }
        } catch (Exception e) {
            LOGGER.error("同步配方到客户端失败", e);
        }
    }

    public void triggerClientResourceReload() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TriggerReloadPacket());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("触发客户端刷新失败: {}", e.getMessage());
        }
    }

    /**
     * 安全获取物品的ResourceLocation
     */
    private ResourceLocation getItemResourceLocation(Item item) {
        ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
        return location != null ? location : new ResourceLocation("minecraft", "air");
    }

    /**
     * 安全解析ResourceLocation字符串
     */
    private ResourceLocation parseResourceLocation(String str) {
        try {
            if (str == null || str.trim().isEmpty()) {
                return null;
            }

            // 清理字符串，移除可能的前缀
            String cleaned = str.trim();
            if (cleaned.startsWith("Item{") && cleaned.endsWith("}")) {
                cleaned = cleaned.substring(5, cleaned.length() - 1);
            }

            return new ResourceLocation(cleaned);
        } catch (Exception e) {
            LOGGER.warn("解析ResourceLocation失败: {} - {}", str, e.getMessage());
            return null;
        }
    }

    /**
     * 转换材料列表为字符串数组
     */
    private String[] convertIngredientsToStringArray(Object[] ingredients) {
        String[] result = new String[ingredients.length];
        for (int i = 0; i < ingredients.length; i++) {
            if (ingredients[i] instanceof Item) {
                result[i] = getItemResourceLocation((Item) ingredients[i]).toString();
            } else if (ingredients[i] instanceof ItemLike) {
                result[i] = getItemResourceLocation(((ItemLike) ingredients[i]).asItem()).toString();
            } else if (ingredients[i] instanceof ItemStack) {
                result[i] = getItemResourceLocation(((ItemStack) ingredients[i]).getItem()).toString();
            } else {
                result[i] = ingredients[i].toString();
            }
        }
        return result;
    }

    /**
     * 转换有形状配方的材料映射为JSON友好格式
     */
    private Object[] convertIngredientsForJson(Object[] ingredients) {
        Object[] result = new Object[ingredients.length];
        for (int i = 0; i < ingredients.length; i++) {
            if (i % 2 == 0) {
                // 符号字符，转换为字符串保存
                if (ingredients[i] instanceof Character) {
                    result[i] = ingredients[i].toString();
                } else {
                    result[i] = ingredients[i];
                }
            } else {
                // 物品，转换为ResourceLocation字符串
                if (ingredients[i] instanceof Item) {
                    result[i] = getItemResourceLocation((Item) ingredients[i]).toString();
                } else if (ingredients[i] instanceof ItemLike) {
                    result[i] = getItemResourceLocation(((ItemLike) ingredients[i]).asItem()).toString();
                } else if (ingredients[i] instanceof ItemStack) {
                    result[i] = getItemResourceLocation(((ItemStack) ingredients[i]).getItem()).toString();
                } else {
                    result[i] = ingredients[i].toString();
                }
            }
        }
        return result;
    }

    /**
     * 从JSON格式转换materialMapping回原始格式
     */
    private Object[] convertMaterialMappingFromJson(Object[] jsonMapping) {
        if (jsonMapping == null) {
            return new Object[0];
        }

        Object[] result = new Object[jsonMapping.length];
        for (int i = 0; i < jsonMapping.length; i++) {
            if (i % 2 == 0) {
                // 符号字符，从字符串转回字符
                String str = jsonMapping[i].toString();
                if (str.length() == 1) {
                    result[i] = str.charAt(0);
                } else {
                    result[i] = str;
                }
            } else {
                // 物品，从ResourceLocation字符串转回Item
                ResourceLocation itemLoc = parseResourceLocation(jsonMapping[i].toString());
                if (itemLoc != null) {
                    Item item = ForgeRegistries.ITEMS.getValue(itemLoc);
                    if (item != null) {
                        result[i] = item;
                    } else {
                        LOGGER.warn("找不到物品: " + jsonMapping[i]);
                        result[i] = jsonMapping[i];
                    }
                } else {
                    result[i] = jsonMapping[i];
                }
            }
        }
        return result;
    }
}