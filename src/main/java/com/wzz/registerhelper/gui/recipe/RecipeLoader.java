package com.wzz.registerhelper.gui.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import com.wzz.registerhelper.gui.recipe.RecipeTypeConfig.*;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
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
    private static final Gson RECIPE_GSON = new Gson();
    private static final com.google.gson.Gson PATCH_GSON = new com.google.gson.GsonBuilder().create();
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
        public List<IngredientData> ingredientsData = null;

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
        LoadResult result = loadRecipeInternal(recipeId);
        if (result.success) {
            result.setRecipeId(recipeId);
            patchIngredientDataFromJson(result, recipeId); // 补充 ignoreKeys
        }
        return result;
    }

    private void patchIngredientDataFromJson(LoadResult result, ResourceLocation recipeId) {
        try {
            // 全目录递归搜索
            java.io.File jsonFile = findRecipeFile(recipeId);
            if (jsonFile == null) return; // 不是本mod管理的配方，跳过

            com.google.gson.JsonObject json;
            try (java.io.FileReader reader = new java.io.FileReader(jsonFile)) {
                json = PATCH_GSON.fromJson(reader, com.google.gson.JsonObject.class);
            }
            if (json == null) return;

            List<com.wzz.registerhelper.gui.recipe.IngredientData> dataList = null;
            if (json.has("key") && json.has("pattern")) {
                dataList = patchFromShapedKey(json, result);
            } else if (json.has("ingredients")) {
                dataList = patchFromIngredientArray(
                        json.getAsJsonArray("ingredients"), result);
            } else if (json.has("ingredient")) {
                com.wzz.registerhelper.gui.recipe.IngredientData d =
                        ingredientDataFromJson(json.getAsJsonObject("ingredient"),
                                result.ingredients.isEmpty()
                                        ? net.minecraft.world.item.ItemStack.EMPTY
                                        : result.ingredients.get(0));
                dataList = new java.util.ArrayList<>();
                dataList.add(d);
            }
            if (dataList != null && !dataList.isEmpty()) {
                result.ingredientsData = dataList;
            }
        } catch (Exception e) {
            LOGGER.warn("[RegisterHelper] patchIngredientDataFromJson 失败: {}", e.getMessage());
        }
    }

    /**
     * 在 config/registerhelper/ 下递归搜索与 recipeId 对应的 JSON 文件。
     * 匹配规则：namespace 目录下的文件名（去掉 .json 后下划线替换路径分隔符）== recipeId.getPath()
     */
    private java.io.File findRecipeFile(net.minecraft.resources.ResourceLocation recipeId) {
        // 搜索根目录列表
        java.nio.file.Path[] searchRoots = {
                FMLPaths.CONFIGDIR.get().resolve("registerhelper/recipes"),
                FMLPaths.CONFIGDIR.get().resolve("registerhelper/custom_recipes")
        };
        String targetNamespace = recipeId.getNamespace();
        String targetPath      = recipeId.getPath(); // e.g. "custom_shaped_chiseled_sandstone"

        for (java.nio.file.Path root : searchRoots) {
            java.io.File rootDir = root.toFile();
            if (!rootDir.exists()) continue;

            java.io.File found = searchInDir(rootDir, targetNamespace, targetPath);
            if (found != null) return found;
        }
        return null;
    }

    private java.io.File searchInDir(java.io.File dir, String targetNamespace, String targetPath) {
        java.io.File[] children = dir.listFiles();
        if (children == null) return null;
        for (java.io.File child : children) {
            if (child.isDirectory()) {
                // 如果目录名是 namespace，在里面找
                if (child.getName().equals(targetNamespace)) {
                    java.io.File found = findInNamespaceDir(child, child, targetPath);
                    if (found != null) return found;
                } else {
                    // 递归进去（处理 custom_recipes/brewing/ 等子目录）
                    java.io.File found = searchInDir(child, targetNamespace, targetPath);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * 在 namespaceDir 下递归找文件，文件的相对 path（下划线化）== targetPath
     */
    private java.io.File findInNamespaceDir(java.io.File namespaceDir,
                                            java.io.File currentDir,
                                            String targetPath) {
        java.io.File[] files = currentDir.listFiles();
        if (files == null) return null;
        for (java.io.File f : files) {
            if (f.isDirectory()) {
                java.io.File found = findInNamespaceDir(namespaceDir, f, targetPath);
                if (found != null) return found;
            } else if (f.getName().endsWith(".json")) {
                // 计算相对路径并转成 recipeId path 格式
                String rel = f.getAbsolutePath()
                        .substring(namespaceDir.getAbsolutePath().length() + 1);
                rel = rel.substring(0, rel.length() - 5); // 去掉 .json
                rel = rel.replace(java.io.File.separatorChar, '_');
                if (rel.equals(targetPath)) return f;
            }
        }
        return null;
    }

    private LoadResult loadRecipeInternal(ResourceLocation recipeId) {
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
            int tier = DynamicRecipeBuilder.getTierFromIngredientCount(ingredientCount);
            int gridSize = DynamicRecipeBuilder.getGridSizeForTier(tier);
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

    /** 解析 shaped 的 key+pattern，按槽位顺序生成 IngredientData 列表 */
    private List<IngredientData> patchFromShapedKey(JsonObject json, LoadResult result) {
        if (!json.has("pattern")) return patchFallback(result);

        JsonArray patternArr = json.getAsJsonArray("pattern");
        JsonObject keyObj = json.getAsJsonObject("key");

        // 把 pattern 展开成字符列表（与 result.ingredients 顺序对应）
        List<Character> patternChars = new ArrayList<>();
        for (var row : patternArr) {
            String rowStr = row.getAsString();
            for (char c : rowStr.toCharArray()) {
                patternChars.add(c);
            }
        }

        List<IngredientData> result2 = new ArrayList<>();
        for (int i = 0; i < result.ingredients.size(); i++) {
            if (i >= patternChars.size()) {
                result2.add(IngredientData.fromItem(result.ingredients.get(i)));
                continue;
            }
            char sym = patternChars.get(i);
            String symStr = String.valueOf(sym);
            if (sym == ' ' || !keyObj.has(symStr)) {
                result2.add(IngredientData.empty());
            } else {
                JsonObject ingredJson = keyObj.getAsJsonObject(symStr);
                IngredientData d = ingredientDataFromJson(ingredJson, result.ingredients.get(i));
                result2.add(d);
            }
        }
        return result2;
    }

    /** 解析 ingredients 数组（shapeless / 其他） */
    private List<IngredientData> patchFromIngredientArray(JsonArray arr, LoadResult result) {
        List<IngredientData> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            ItemStack base = i < result.ingredients.size() ? result.ingredients.get(i) : ItemStack.EMPTY;
            list.add(ingredientDataFromJson(arr.get(i).getAsJsonObject(), base));
        }
        return list;
    }

    /** 回退：无法从 JSON 解析时，直接用 ItemStack 构建（不带 ignoreKeys）*/
    private List<IngredientData> patchFallback(LoadResult result) {
        List<IngredientData> list = new ArrayList<>();
        for (ItemStack s : result.ingredients) {
            list.add(s.isEmpty() ? IngredientData.empty() : IngredientData.fromItem(s));
        }
        return list;
    }

    /**
     * 从单个 ingredient JSON 节点构建 IngredientData，
     * 识别 forge:nbt（includeNBT=true）、registerhelper:partial_nbt（partial）、
     * 无 nbt（includeNBT=false）三种情况。
     */
    private IngredientData ingredientDataFromJson(JsonObject j, ItemStack fallbackStack) {
        try {
            // 标签型
            if (j.has("tag")) {
                return IngredientData.fromTag(new ResourceLocation(j.get("tag").getAsString()));
            }

            String type = j.has("type") ? j.get("type").getAsString() : "";

            if ("registerhelper:partial_nbt".equals(type)) {
                // 解析 item
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(j.get("item").getAsString()));
                if (item == null) return IngredientData.fromItem(fallbackStack);

                // 解析 nbt
                net.minecraft.nbt.CompoundTag nbt = null;
                if (j.has("nbt")) nbt = TagParser.parseTag(j.get("nbt").getAsString());
                ItemStack stack = new ItemStack(item);
                if (nbt != null) stack.setTag(nbt);

                IngredientData data = IngredientData.fromItem(stack);
                data.setIncludeNBT(true);

                // ★ 恢复 ignore_keys
                if (j.has("ignore_keys")) {
                    List<String> keys = new ArrayList<>();
                    j.getAsJsonArray("ignore_keys").forEach(el -> keys.add(el.getAsString()));
                    data.setIgnoreNbtKeys(keys);
                }
                return data;

            } else if ("forge:nbt".equals(type)) {
                // 精确 NBT 匹配，includeNBT=true（默认），ignoreKeys 为空
                IngredientData data = IngredientData.fromItem(fallbackStack);
                data.setIncludeNBT(true);
                return data;

            } else {
                // 无 nbt 字段 → ignoreNBT
                IngredientData data = IngredientData.fromItem(fallbackStack);
                data.setIncludeNBT(!fallbackStack.hasTag()); // 有 NBT 的物品默认关闭
                if (fallbackStack.hasTag() && !j.has("nbt")) {
                    data.setIncludeNBT(false);
                }
                return data;
            }
        } catch (Exception e) {
            LOGGER.warn("[RegisterHelper] ingredientDataFromJson 解析失败: {}", e.getMessage());
            return IngredientData.fromItem(fallbackStack);
        }
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