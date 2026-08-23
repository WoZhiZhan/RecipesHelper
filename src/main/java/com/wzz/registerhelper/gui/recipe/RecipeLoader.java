package com.wzz.registerhelper.gui.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.gui.recipe.component.NumberInputComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.StringInputComponent;
import com.wzz.registerhelper.gui.GuiText;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeBuilder;
import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import com.wzz.registerhelper.gui.recipe.RecipeTypeConfig.*;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import com.wzz.registerhelper.network.RecipeJsonClientCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.io.BufferedReader;
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
        public ItemStack resultItem;
        public final List<ItemStack> ingredients;
        public final String message;
        public final String originalRecipeTypeId;
        public ResourceLocation recipeId;
        public List<IngredientData> ingredientsData = null;
        public Map<String, Object> componentData = new HashMap<>();
        public List<ItemStack> extraOutputs = new ArrayList<>();
        public Recipe<?> runtimeRecipe;

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
            try {
                result.runtimeRecipe = getRecipeManager().byKey(recipeId).orElse(null);
            } catch (Exception ignored) {
                result.runtimeRecipe = null;
            }
            patchIngredientDataFromJson(result, recipeId); // 补充 ignoreKeys
            JsonObject recipeJson = loadRecipeJson(recipeId);
            if (recipeJson != null) {
                ItemStack originalResult = result.resultItem.copy();
                List<IngredientData> originalIngredients = result.ingredientsData;
                Map<String, Object> originalComponents = new HashMap<>(result.componentData);
                try {
                    applyRecipeJson(result, recipeJson);
                } catch (Exception e) {
                    result.resultItem = originalResult;
                    result.ingredientsData = originalIngredients;
                    result.componentData.clear();
                    result.componentData.putAll(originalComponents);
                    LOGGER.warn("特殊配方 JSON overlay 解析失败，回退运行时数据: {}", recipeId, e);
                }
            }
        }
        return result;
    }

    /**
     * Reads the recipe data that produced the runtime object. Special recipe
     * implementations deliberately return an empty result from the vanilla
     * Recipe API, so the JSON is the authoritative display/edit source.
     */
    private JsonObject loadRecipeJson(ResourceLocation recipeId) {
        try {
            String serverJson = RecipeJsonClientCache.get(recipeId);
            if (serverJson != null && !serverJson.isBlank()) {
                return PATCH_GSON.fromJson(serverJson, JsonObject.class);
            }
            JsonObject override = UnifiedRecipeOverrideManager.getOverride(recipeId);
            if (override != null) return override;

            java.io.File configFile = findRecipeFile(recipeId);
            if (configFile != null) {
                try (java.io.FileReader reader = new java.io.FileReader(configFile)) {
                    return PATCH_GSON.fromJson(reader, JsonObject.class);
                }
            }

            ResourceLocation resourceId = new ResourceLocation(
                    recipeId.getNamespace(), "recipes/" + recipeId.getPath() + ".json");
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                JsonObject json = readRecipeResource(server.getResourceManager(), resourceId);
                if (json != null) return json;
            }
            if (minecraft != null) {
                return readRecipeResource(minecraft.getResourceManager(), resourceId);
            }
        } catch (Exception e) {
            LOGGER.debug("无法读取配方 JSON: {}", recipeId, e);
        }
        return null;
    }

    private JsonObject readRecipeResource(ResourceManager manager, ResourceLocation resourceId) {
        if (manager == null) return null;
        try {
            Optional<Resource> resource = manager.getResource(resourceId);
            if (resource.isEmpty()) return null;
            try (BufferedReader reader = resource.get().openAsReader()) {
                return PATCH_GSON.fromJson(reader, JsonObject.class);
            }
        } catch (Exception e) {
            LOGGER.debug("无法读取资源配方: {}", resourceId, e);
            return null;
        }
    }

    private void applyRecipeJson(LoadResult result, JsonObject json) {
        List<IngredientData> ingredients = new ArrayList<>();
        List<String> roles = new ArrayList<>();
        String type = json.has("type") ? json.get("type").getAsString() : "";

        if (json.has("ingredients") && json.get("ingredients").isJsonArray()
                && json.getAsJsonArray("ingredients").asList().stream().anyMatch(JsonElement::isJsonArray)) {
            result.componentData.put("rawIngredients", json.get("ingredients").deepCopy());
        }
        if (json.has("input") && json.get("input").isJsonArray()) {
            result.componentData.put("rawInput", json.get("input").deepCopy());
        }

        if (type.endsWith(":sequenced_assembly")) {
            if (json.has("ingredient")) {
                IngredientData input = specialIngredientData(json.get("ingredient"),
                        result.ingredients.isEmpty() ? ItemStack.EMPTY : result.ingredients.get(0));
                if (input != null && !input.isEmpty()) setRoleSlot(ingredients, roles, 0, input, "INPUT");
            }
            if (json.has("transitionalItem")) {
                IngredientData transitional = specialIngredientData(json.get("transitionalItem"), ItemStack.EMPTY);
                if (transitional != null && !transitional.isEmpty()) {
                    setRoleSlot(ingredients, roles, 1, transitional, "TRANSITIONAL");
                }
            }
            if (json.has("sequence")) result.componentData.put("sequence", json.get("sequence").deepCopy());
            if (json.has("sequence") && json.getAsJsonArray("sequence").size() > 0) {
                JsonElement step = json.getAsJsonArray("sequence").get(0);
                if (step.isJsonObject() && step.getAsJsonObject().has("type")) {
                    result.componentData.put("sequenceType", step.getAsJsonObject().get("type").getAsString());
                }
            }
        } else if (json.has("key") && json.has("pattern")) {
            JsonArray pattern = json.getAsJsonArray("pattern");
            if (!pattern.isEmpty()) {
                result.componentData.put("patternWidth", pattern.get(0).getAsString().length());
                result.componentData.put("patternHeight", pattern.size());
            }
            List<IngredientData> shaped = patchFromShapedKey(json, result);
            if (shaped != null) {
                ingredients.addAll(shaped);
                for (IngredientData data : shaped) {
                    roles.add("INPUT");
                }
            }
        } else if (json.has("ingredients") && json.get("ingredients").isJsonArray()) {
            boolean preserveRawIngredients = false;
            for (JsonElement element : json.getAsJsonArray("ingredients")) {
                if (isFluidElement(element)) {
                    captureFluidInput(result, element);
                    continue;
                }
                preserveRawIngredients |= element.isJsonArray();
                IngredientData data = specialIngredientData(element,
                        ingredients.size() < result.ingredients.size()
                                ? result.ingredients.get(ingredients.size()) : ItemStack.EMPTY);
                if (data != null && !data.isEmpty()) {
                    ingredients.add(data);
                    roles.add("INPUT");
                }
            }
            if (preserveRawIngredients) {
                result.componentData.put("rawIngredients", json.get("ingredients").deepCopy());
            }
        } else if (json.has("input")) {
            IngredientData data = specialIngredientData(json.get("input"),
                    result.ingredients.isEmpty() ? ItemStack.EMPTY : result.ingredients.get(0));
            if (data != null && !data.isEmpty()) {
                ingredients.add(data);
                roles.add("INPUT");
            }
        }

        if (type.endsWith(":mana_infusion") && json.has("catalyst")) {
            IngredientData catalyst = specialIngredientData(json.get("catalyst"), ItemStack.EMPTY);
            if (catalyst != null && !catalyst.isEmpty()) {
                setRoleSlot(ingredients, roles, 1, catalyst, "CATALYST");
            }
        }
        if (type.endsWith(":petal_apothecary") && json.has("reagent")) {
            IngredientData reagent = specialIngredientData(json.get("reagent"), ItemStack.EMPTY);
            if (reagent != null && !reagent.isEmpty()) {
                setRoleSlot(ingredients, roles, 16, reagent, "REAGENT");
            }
        }

        if (!ingredients.isEmpty()) {
            result.ingredientsData = ingredients;
            result.componentData.put("slotRoles", roles);
        }

        copyJsonNumber(result, json, "mana");
        copyJsonNumber(result, json, "weight");
        copyJsonNumber(result, json, "biome_bonus");
        copyJsonNumber(result, json, "time");
        copyJsonNumber(result, json, "processingTime");
        copyJsonNumber(result, json, "amount");
        copyJsonNumber(result, json, "loops");
        if (json.has("heatRequirement")) {
            result.componentData.put("heatRequirement", json.get("heatRequirement").getAsString());
        }
        if (json.has("acceptMirrored")) {
            result.componentData.put("acceptMirrored", json.get("acceptMirrored").getAsBoolean());
        }
        if (json.has("keepHeldItem")) {
            result.componentData.put("keepHeldItem", json.get("keepHeldItem").getAsBoolean());
        }
        if (json.has("brew")) {
            result.componentData.put("brew", json.get("brew").getAsString());
        }
        if (json.has("reagent")) {
            result.componentData.put("reagent", ingredientValueString(json.get("reagent")));
        }
        if (json.has("success_function")) {
            result.componentData.put("success_function", json.get("success_function").getAsString());
        }
        if (json.has("biome_bonus_tag")) {
            result.componentData.put("biome_bonus_tag", json.get("biome_bonus_tag").getAsString());
        }

        List<ItemStack> outputs = new ArrayList<>();
        List<Float> outputChances = new ArrayList<>();
        if (json.has("result")) collectOutput(result, outputs, outputChances, json.get("result"));
        if (json.has("output")) collectOutput(result, outputs, outputChances, json.get("output"));
        if (json.has("results")) collectOutput(result, outputs, outputChances, json.get("results"));
        if (!outputs.isEmpty()) {
            result.resultItem = outputs.remove(0);
            result.extraOutputs.clear();
            result.extraOutputs.addAll(outputs);
            result.componentData.put("extraOutputs", new ArrayList<>(outputs));
            result.componentData.put("extraResults", outputs.toArray(new ItemStack[0]));
            result.componentData.put("outputChances", outputChances);
        }
    }

    private void copyJsonNumber(LoadResult result, JsonObject json, String key) {
        if (json.has(key) && json.get(key).isJsonPrimitive()
                && json.get(key).getAsJsonPrimitive().isNumber()) {
            result.componentData.put(key, json.get(key).getAsInt());
        }
    }

    private void setRoleSlot(List<IngredientData> ingredients, List<String> roles,
                             int index, IngredientData data, String role) {
        while (ingredients.size() <= index) {
            ingredients.add(IngredientData.empty());
            roles.add("INPUT");
        }
        ingredients.set(index, data);
        roles.set(index, role);
    }

    private void captureFluidInput(LoadResult result, JsonElement element) {
        if (!element.isJsonObject()) return;
        JsonObject fluid = element.getAsJsonObject();
        Map<String, Object> fluidData = fluidData(fluid);
        if (fluidData != null) addFluidData(result, "fluidInputs", fluidData);
        if (fluid.has("fluid")) {
            result.componentData.putIfAbsent("fluid", fluid.get("fluid").getAsString());
        } else if (fluid.has("fluidTag")) {
            result.componentData.putIfAbsent("fluid", "#" + fluid.get("fluidTag").getAsString());
        }
        if (fluid.has("amount")) {
            int amount = fluid.get("amount").getAsInt();
            result.componentData.putIfAbsent("amount", amount);
            result.componentData.putIfAbsent("fluidAmount", amount);
        }
    }

    private boolean isFluidElement(JsonElement element) {
        return element != null && element.isJsonObject()
                && (element.getAsJsonObject().has("fluid")
                || element.getAsJsonObject().has("fluidTag"));
    }

    private void collectOutput(LoadResult result, List<ItemStack> outputs,
                               List<Float> chances, JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectOutput(result, outputs, chances, child);
            }
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String id = element.getAsString();
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                Item item = Item.byBlock(block);
                if (item != Items.AIR) {
                    outputs.add(new ItemStack(item));
                    chances.add(1.0F);
                }
            }
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (object.has("fluid") || object.has("fluidTag")) {
            Map<String, Object> fluid = new HashMap<>();
            String fluidId = object.has("fluid")
                    ? object.get("fluid").getAsString()
                    : "#" + object.get("fluidTag").getAsString();
            int amount = object.has("amount") ? object.get("amount").getAsInt() : 250;
            fluid.put("fluid", fluidId);
            fluid.put("amount", amount);
            if (object.has("nbt")) fluid.put("nbt", object.get("nbt").deepCopy());
            addFluidData(result, "fluidOutputs", fluid);
            // Fluid output is stored separately from item outputs.
            result.componentData.putIfAbsent("fluidOutput", fluid);
            result.componentData.putIfAbsent("fluidOut", fluidId);
            result.componentData.putIfAbsent("fluidOutAmount", amount);
            result.componentData.putIfAbsent("fluid", fluidId);
            result.componentData.putIfAbsent("fluidAmount", amount);
            return;
        }

        ItemStack stack = stackFromJson(object);
        if (stack.isEmpty()) return;
        outputs.add(stack);
        chances.add(object.has("chance") ? object.get("chance").getAsFloat() : 1.0F);
    }

    private Map<String, Object> fluidData(JsonObject object) {
        String id = object.has("fluid") ? object.get("fluid").getAsString()
                : object.has("fluidTag") ? "#" + object.get("fluidTag").getAsString() : null;
        if (id == null || id.isBlank()) return null;
        Map<String, Object> data = new HashMap<>();
        data.put("fluid", id);
        data.put("amount", object.has("amount") ? object.get("amount").getAsInt() : 250);
        if (object.has("nbt")) data.put("nbt", object.get("nbt").deepCopy());
        return data;
    }

    @SuppressWarnings("unchecked")
    private void addFluidData(LoadResult result, String key, Map<String, Object> value) {
        Object existing = result.componentData.get(key);
        List<Map<String, Object>> values;
        if (existing instanceof List<?> list) {
            values = (List<Map<String, Object>>) (List<?>) list;
        } else {
            values = new ArrayList<>();
            result.componentData.put(key, values);
        }
        values.add(value);
    }

    private ItemStack stackFromJson(JsonObject object) {
        try {
            if (object.has("item")) {
                return net.minecraft.world.item.crafting.ShapedRecipe.itemStackFromJson(object);
            }
            String id = null;
            if (object.has("name")) id = object.get("name").getAsString();
            if (object.has("block")) id = object.get("block").getAsString();
            if (id == null || id.startsWith("#")) return ItemStack.EMPTY;
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            if (block == null) return ItemStack.EMPTY;
            Item item = Item.byBlock(block);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private IngredientData specialIngredientData(JsonElement element, ItemStack fallback) {
        if (element != null && element.isJsonArray()) {
            if (element.getAsJsonArray().isEmpty()) return null;
            element = element.getAsJsonArray().get(0);
        }
        if (element == null || element.isJsonNull() || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        try {
            if (object.has("block") || object.has("name")) {
                ItemStack stack = stackFromJson(object);
                return stack.isEmpty() ? IngredientData.fromItem(fallback) : IngredientData.fromItem(stack);
            }
            if (object.has("item")) {
                ItemStack stack = stackFromJson(object);
                return stack.isEmpty() ? IngredientData.fromItem(fallback) : IngredientData.fromItem(stack);
            }
            if (object.has("type") && "tag".equals(object.get("type").getAsString())
                    && object.has("tag")) {
                return IngredientData.fromTag(new ResourceLocation(object.get("tag").getAsString()));
            }
            return ingredientDataFromJson(object, fallback);
        } catch (Exception e) {
            return fallback.isEmpty() ? IngredientData.empty() : IngredientData.fromItem(fallback);
        }
    }

    private String ingredientValueString(JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("tag")) return "#" + object.get("tag").getAsString();
            if (object.has("item")) return object.get("item").getAsString();
            if (object.has("block")) return object.get("block").getAsString();
        }
        return element == null || element.isJsonNull() ? "" : element.getAsString();
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

            patchComponentDataFromJson(result, json);

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

    private void patchComponentDataFromJson(LoadResult result, JsonObject json) {
        Set<String> componentIds = new HashSet<>();
        for (var layout : LayoutManager.getAllLayouts()) {
            for (RecipeComponent component : layout.generateComponents(0, 0, result.avaritiaTeir)) {
                if (component instanceof NumberInputComponent
                        || component instanceof StringInputComponent) {
                    componentIds.add(component.getId());
                }
            }
        }

        for (String componentId : componentIds) {
            String jsonKey = "fluidAmount".equals(componentId) ? "amount" : componentId;
            JsonElement value = findJsonValue(json, jsonKey);
            if (value == null || !value.isJsonPrimitive()) continue;
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                result.componentData.put(componentId, primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                result.componentData.put(componentId, primitive.getAsInt());
            } else if (primitive.isString()) {
                result.componentData.put(componentId, primitive.getAsString());
            }
        }
    }

    private JsonElement findJsonValue(JsonElement element, String key) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has(key)) return object.get(key);
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement nested = findJsonValue(entry.getValue(), key);
                if (nested != null) return nested;
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                JsonElement nested = findJsonValue(child, key);
                if (nested != null) return nested;
            }
        }
        return null;
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
                return new LoadResult(false, GuiText.string("registerhelper.message.recipe.data_unavailable"));
            }
            var recipe = recipeManager.byKey(recipeId).orElse(null);

            if (recipe == null) {
                return new LoadResult(false, GuiText.string("registerhelper.message.recipe.not_found", recipeId));
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
            ItemStack resultItem = recipe.getResultItem(registryAccess).copy();

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
            return new LoadResult(false, GuiText.string("registerhelper.message.recipe.load_failed", e.getMessage()));
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
                    resultItem, ingredients, GuiText.string("registerhelper.message.recipe.loaded_mod"), originalRecipeTypeId);

        } catch (Exception e) {
            LOGGER.warn("解析通用配方失败", e);
            return new LoadResult(false, GuiText.string("registerhelper.message.recipe.parse_failed", e.getMessage()));
        }
    }

    /**
     * 加载工作台配方（修改所有返回语句）
     */
    private LoadResult loadCraftingRecipe(Recipe<?> recipe, ItemStack resultItem,
                                          CraftingMode mode, String originalRecipeTypeId) {
        try {
            List<ItemStack> ingredients = new ArrayList<>();
            int slotCount = recipe.getClass().getName().contains("MechanicalCrafting") ? 81 : 9;
            for (int i = 0; i < slotCount; i++) {
                ingredients.add(ItemStack.EMPTY);
            }

            if (mode == CraftingMode.SHAPED && recipe.getClass().getSimpleName().contains("ShapedRecipe")) {
                return loadShapedRecipePattern(recipe, resultItem, ingredients, originalRecipeTypeId);
            } else {
                return loadGenericCraftingRecipe(recipe, resultItem, mode, ingredients, originalRecipeTypeId);
            }
        } catch (Exception e) {
            LOGGER.error("解析工作台配方失败", e);
            return new LoadResult(false, GuiText.string("registerhelper.message.recipe.parse_crafting_failed", e.getMessage()));
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
        for (int i = 0; i < Math.min(recipeIngredients.size(), ingredients.size()); i++) {
            var ingredient = recipeIngredients.get(i);
            if (ingredient != null && !ingredient.isEmpty()) {
                var items = ingredient.getItems();
                if (items != null && items.length > 0) {
                    ingredients.set(i, items[0].copy());
                }
            }
        }

        return new LoadResult(true, RecipeType.CRAFTING, mode, null, 1,
                resultItem, ingredients, GuiText.string("registerhelper.message.recipe.loaded_crafting"), originalRecipeTypeId);
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
                    resultItem, ingredients, GuiText.string("registerhelper.message.recipe.loaded_cooking"), originalRecipeTypeId);

        } catch (Exception e) {
            LOGGER.warn("解析烹饪配方失败", e);
            return new LoadResult(false, GuiText.string("registerhelper.message.recipe.parse_cooking_failed", e.getMessage()));
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
                    resultItem, ingredients, GuiText.string("registerhelper.message.recipe.loaded_avaritia"), originalRecipeTypeId);

        } catch (Exception e) {
            LOGGER.warn("解析Avaritia配方失败", e);
            return new LoadResult(false, GuiText.string("registerhelper.message.recipe.parse_avaritia_failed", e.getMessage()));
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
                    resultItem, ingredients, GuiText.string("registerhelper.message.recipe.loaded_smithing"), originalRecipeTypeId);

        } catch (Exception e) {
            LOGGER.warn("解析锻造台配方失败", e);
            return new LoadResult(false, GuiText.string("registerhelper.message.recipe.parse_smithing_failed", e.getMessage()));
        }
    }

    // 配方类型识别辅助方法
    private boolean isShapedCraftingRecipe(String typeName, String className) {
        return typeName.contains("crafting_shaped") ||
                className.contains("shapedrecipe") ||
                typeName.contains("mechanical_crafting") ||
                className.contains("mechanicalcrafting") ||
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

        // Vanilla uses a 3-wide grid. Create mechanical crafting uses a
        // larger flat list, but its JSON pattern remains the source of the
        // row/column positions.
        int targetWidth = patternArr.isEmpty()
                ? (result.ingredients.size() >= 81 ? 9 : 3)
                : patternArr.get(0).getAsString().length();
        List<IngredientData> result2 = new ArrayList<>();
        for (int i = 0; i < result.ingredients.size(); i++) {
            result2.add(IngredientData.empty());
        }
        for (int row = 0; row < patternArr.size(); row++) {
            String rowString = patternArr.get(row).getAsString();
            for (int column = 0; column < rowString.length() && column < targetWidth; column++) {
                int index = row * targetWidth + column;
                if (index >= result2.size()) continue;
                char symbol = rowString.charAt(column);
                if (symbol == ' ') continue;
                JsonElement element = keyObj.get(String.valueOf(symbol));
                if (element == null) continue;
                ItemStack fallback = index < result.ingredients.size()
                        ? result.ingredients.get(index) : ItemStack.EMPTY;
                result2.set(index, ingredientDataFromJsonElement(element, fallback));
            }
        }
        return result2;
    }

    private IngredientData ingredientDataFromJsonElement(JsonElement element, ItemStack fallback) {
        if (element != null && element.isJsonArray() && !element.getAsJsonArray().isEmpty()) {
            element = element.getAsJsonArray().get(0);
        }
        return element != null && element.isJsonObject()
                ? ingredientDataFromJson(element.getAsJsonObject(), fallback)
                : (fallback.isEmpty() ? IngredientData.empty() : IngredientData.fromItem(fallback));
    }

    /** 解析 ingredients 数组（shapeless / 其他） */
    private List<IngredientData> patchFromIngredientArray(JsonArray arr, LoadResult result) {
        List<IngredientData> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            if (isFluidElement(arr.get(i))) continue;
            ItemStack base = i < result.ingredients.size() ? result.ingredients.get(i) : ItemStack.EMPTY;
            list.add(ingredientDataFromJsonElement(arr.get(i), base));
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
            return GuiText.string("registerhelper.recipe.source.custom");
        }

        if (namespace.equals("minecraft")) {
            return GuiText.string("registerhelper.recipe.source.vanilla");
        }

        return GuiText.string("registerhelper.recipe.source.mod", namespace);
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
