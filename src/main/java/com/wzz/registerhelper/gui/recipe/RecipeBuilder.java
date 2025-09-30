package com.wzz.registerhelper.gui.recipe;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.network.CreateRecipePacket;
import com.wzz.registerhelper.generator.RecipeGenerator;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import com.wzz.registerhelper.gui.recipe.RecipeTypeConfig.*;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 配方构建器
 * 负责将编辑器中的数据构建为实际的配方
 */
public class RecipeBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final Consumer<String> successCallback;
    private final Consumer<String> errorCallback;

    public RecipeBuilder(Consumer<String> successCallback, Consumer<String> errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;
    }

    /**
     * 配方构建参数
     */
    public static class BuildParams {
        public final RecipeType recipeType;
        public final CraftingMode craftingMode;
        public final CookingType cookingType;
        public final int avaritiaTeir;
        public final ItemStack resultItem;
        public final List<ItemStack> ingredients;
        public final float cookingTime;
        public final float cookingExp;
        public final ResourceLocation editingRecipeId;
        public final boolean isEditing;

        public BuildParams(RecipeType recipeType, CraftingMode craftingMode, CookingType cookingType,
                          int avaritiaTeir, ItemStack resultItem, List<ItemStack> ingredients,
                          float cookingTime, float cookingExp, ResourceLocation editingRecipeId, boolean isEditing) {
            this.recipeType = recipeType;
            this.craftingMode = craftingMode;
            this.cookingType = cookingType;
            this.avaritiaTeir = avaritiaTeir;
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
        // 验证参数
        if (!validateParams(params)) {
            return;
        }

        try {
            // 获取配方模式
            RecipeGenerator.RecipeMode mode = getRecipeMode(params);
            String modId = getModId(params.recipeType);

            // 生成配方ID
            String recipeId = generateRecipeId(params, modId, mode);
            boolean isOverride = isOverrideMode(params);

            LOGGER.info("构建配方: {} (覆盖模式: {})", recipeId, isOverride);

            // 构建配方数据
            RecipeGenerator.RecipeData recipeData = buildRecipeData(params, modId, recipeId, mode);
            
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
            LOGGER.error("构建配方失败", e);
            showError("处理配方时发生错误: " + e.getMessage());
        }
    }

    /**
     * 验证构建参数
     */
    private boolean validateParams(BuildParams params) {
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

        return true;
    }

    /**
     * 获取配方模式
     */
    private RecipeGenerator.RecipeMode getRecipeMode(BuildParams params) {
        return switch (params.recipeType) {
            case CRAFTING -> switch (params.craftingMode) {
                case SHAPED -> RecipeGenerator.RecipeMode.SHAPED_3X3;
                case SHAPELESS -> RecipeGenerator.RecipeMode.SHAPELESS_3X3;
            };
            case COOKING -> switch (params.cookingType) {
                case SMELTING -> RecipeGenerator.RecipeMode.SMELTING;
                case BLASTING -> RecipeGenerator.RecipeMode.BLASTING;
                case SMOKING -> RecipeGenerator.RecipeMode.SMOKING;
                case CAMPFIRE -> RecipeGenerator.RecipeMode.CAMPFIRE;
            };
            case AVARITIA -> {
                yield switch (params.avaritiaTeir) {
                    case 1 -> params.craftingMode == CraftingMode.SHAPED ?
                            RecipeGenerator.RecipeMode.SHAPED_3X3 : RecipeGenerator.RecipeMode.SHAPELESS_3X3;
                    case 2 -> params.craftingMode == CraftingMode.SHAPED ?
                            RecipeGenerator.RecipeMode.SHAPED_5X5 : RecipeGenerator.RecipeMode.SHAPELESS_5X5;
                    case 3 -> params.craftingMode == CraftingMode.SHAPED ?
                            RecipeGenerator.RecipeMode.SHAPED_7X7 : RecipeGenerator.RecipeMode.SHAPELESS_7X7;
                    case 4 -> params.craftingMode == CraftingMode.SHAPED ?
                            RecipeGenerator.RecipeMode.SHAPED_9X9 : RecipeGenerator.RecipeMode.SHAPELESS_9X9;
                    default -> RecipeGenerator.RecipeMode.SHAPED_3X3;
                };
            }
        };
    }

    /**
     * 获取模组ID
     */
    private String getModId(RecipeType recipeType) {
        return switch (recipeType) {
            case CRAFTING, COOKING -> "minecraft";
            case AVARITIA -> "avaritia";
        };
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
     * 构建配方数据
     */
    private RecipeGenerator.RecipeData buildRecipeData(BuildParams params, String modId, 
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
     * 创建配方覆盖
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
     * 创建普通配方
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
     * 创建网络数据包
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