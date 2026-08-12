package com.wzz.registerhelper.gui.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.wzz.registerhelper.gui.GuiText;
import com.wzz.registerhelper.gui.recipe.RecipeTypeConfig.*;
import com.wzz.registerhelper.gui.recipe.component.NumberInputComponent;
import com.wzz.registerhelper.gui.recipe.component.RecipeComponent;
import com.wzz.registerhelper.gui.recipe.component.StringInputComponent;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeBuilder;
import com.wzz.registerhelper.gui.recipe.layout.LayoutManager;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import com.wzz.registerhelper.util.DataComponentsHelper;
import com.wzz.registerhelper.util.OldUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Loads existing recipes and restores editor-only metadata from saved JSON. */
public class RecipeLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson PATCH_GSON = new GsonBuilder().create();

    private final Consumer<String> messageCallback;
    private final Minecraft minecraft;

    public RecipeLoader(Consumer<String> messageCallback) {
        this.messageCallback = messageCallback;
        this.minecraft = Minecraft.getInstance();
    }

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
        public List<IngredientData> ingredientsData;
        public Map<String, Object> componentData = new HashMap<>();

        public LoadResult(boolean success, String message) {
            this(success, null, null, null, 1, ItemStack.EMPTY,
                    Collections.emptyList(), message, null);
        }

        public LoadResult(boolean success, RecipeType recipeType, CraftingMode craftingMode,
                          CookingType cookingType, int avaritiaTeir, ItemStack resultItem,
                          List<ItemStack> ingredients, String message,
                          String originalRecipeTypeId) {
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

    public LoadResult loadRecipe(ResourceLocation recipeId) {
        LoadResult result = loadRecipeInternal(recipeId);
        if (result.success) {
            result.setRecipeId(recipeId);
            patchEditorMetadataFromJson(result, recipeId);
        }
        return result;
    }

    private void patchEditorMetadataFromJson(LoadResult result, ResourceLocation recipeId) {
        try {
            File jsonFile = findRecipeFile(recipeId);
            if (jsonFile == null) return;

            JsonObject json;
            try (FileReader reader = new FileReader(jsonFile)) {
                json = PATCH_GSON.fromJson(reader, JsonObject.class);
            }
            if (json == null) return;

            patchComponentDataFromJson(result, json);

            List<IngredientData> dataList = null;
            if (json.has("key") && json.has("pattern")) {
                dataList = patchFromShapedKey(json, result);
            } else if (json.has("ingredients") && json.get("ingredients").isJsonArray()) {
                dataList = patchFromIngredientArray(json.getAsJsonArray("ingredients"), result);
            } else if (json.has("ingredient") && !json.has("input")
                    && json.get("ingredient").isJsonObject()) {
                IngredientData data = ingredientDataFromJson(
                        json.getAsJsonObject("ingredient"),
                        result.ingredients.isEmpty() ? ItemStack.EMPTY : result.ingredients.get(0));
                dataList = new ArrayList<>();
                dataList.add(data);
            } else if (json.has("template") || json.has("base") || json.has("addition")) {
                dataList = patchSmithingIngredients(json, result);
            } else if (json.has("input") || json.has("left") || json.has("left_slot")) {
                dataList = patchNamedIngredients(json,
                        json.has("input") ? new String[]{"input", "ingredient"}
                                : json.has("left_slot")
                                ? new String[]{"left_slot", "center_slot"}
                                : new String[]{"left", "right"}, result);
            }

            if (dataList != null && !dataList.isEmpty()) {
                result.ingredientsData = dataList;
            }
        } catch (Exception e) {
            LOGGER.warn("[RegisterHelper] Failed to restore recipe editor metadata: {}",
                    e.getMessage());
        }
    }

    private void patchComponentDataFromJson(LoadResult result, JsonObject json) {
        Set<String> componentIds = new HashSet<>();
        for (var layout : LayoutManager.getAllLayouts()) {
            for (RecipeComponent component : layout.generateComponents(0, 0,
                    result.avaritiaTeir)) {
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

    private File findRecipeFile(ResourceLocation recipeId) {
        Path configRoot = FMLPaths.CONFIGDIR.get().resolve("registerhelper");
        Path customRoot = configRoot.resolve("custom_recipes");
        String recipePath = recipeId.getPath();
        if (recipePath.startsWith("custom_") && recipePath.contains("/")) {
            int slash = recipePath.indexOf('/');
            String category = recipePath.substring("custom_".length(), slash);
            Path candidate = customRoot.resolve(category)
                    .resolve(recipePath.substring(slash + 1) + ".json");
            if (candidate.toFile().isFile()) return candidate.toFile();
        }

        Path[] searchRoots = {configRoot.resolve("recipes"), customRoot};
        for (Path root : searchRoots) {
            File rootDir = root.toFile();
            if (!rootDir.exists()) continue;
            File found = searchInDir(rootDir, recipeId.getNamespace(), recipeId.getPath());
            if (found != null) return found;
        }
        return null;
    }

    private File searchInDir(File dir, String targetNamespace, String targetPath) {
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            if (child.getName().equals(targetNamespace)) {
                File found = findInNamespaceDir(child, child, targetPath);
                if (found != null) return found;
            } else {
                File found = searchInDir(child, targetNamespace, targetPath);
                if (found != null) return found;
            }
        }
        return null;
    }

    private File findInNamespaceDir(File namespaceDir, File currentDir,
                                    String targetPath) {
        File[] files = currentDir.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findInNamespaceDir(namespaceDir, file, targetPath);
                if (found != null) return found;
            } else if (file.getName().endsWith(".json")) {
                String relative = file.getAbsolutePath()
                        .substring(namespaceDir.getAbsolutePath().length() + 1);
                relative = relative.substring(0, relative.length() - 5)
                        .replace(File.separatorChar, '_');
                if (relative.equals(targetPath)) return file;
            }
        }
        return null;
    }

    private LoadResult loadRecipeInternal(ResourceLocation recipeId) {
        try {
            RecipeManager recipeManager = getRecipeManager();
            RegistryAccess registryAccess = getRegistryAccess();
            if (recipeManager == null || registryAccess == null) {
                return new LoadResult(false, GuiText.string(
                        "registerhelper.message.recipe.data_unavailable"));
            }

            RecipeHolder<?> holder = recipeManager.byKey(recipeId).orElse(null);
            if (holder == null) {
                return new LoadResult(false, GuiText.string(
                        "registerhelper.message.recipe.not_found", recipeId));
            }
            Recipe<?> recipe = holder.value();

            ResourceLocation serializerId = BuiltInRegistries.RECIPE_SERIALIZER
                    .getKey(recipe.getSerializer());
            String originalRecipeTypeId = serializerId != null ? serializerId.toString() : null;
            if (originalRecipeTypeId == null) {
                ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                originalRecipeTypeId = typeId != null
                        ? typeId.toString() : recipe.getType().toString();
            }

            ItemStack resultItem = recipe.getResultItem(registryAccess).copy();
            String recipeTypeName = originalRecipeTypeId.toLowerCase();
            String recipeClassName = recipe.getClass().getSimpleName().toLowerCase();

            if (isShapedCraftingRecipe(recipeTypeName, recipeClassName)) {
                return loadCraftingRecipe(holder, resultItem, CraftingMode.SHAPED,
                        originalRecipeTypeId);
            } else if (isShapelessCraftingRecipe(recipeTypeName, recipeClassName)) {
                return loadCraftingRecipe(holder, resultItem, CraftingMode.SHAPELESS,
                        originalRecipeTypeId);
            } else if (isSmeltingRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(holder, resultItem, CookingType.SMELTING,
                        originalRecipeTypeId);
            } else if (isBlastingRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(holder, resultItem, CookingType.BLASTING,
                        originalRecipeTypeId);
            } else if (isSmokingRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(holder, resultItem, CookingType.SMOKING,
                        originalRecipeTypeId);
            } else if (isCampfireRecipe(recipeTypeName, recipeClassName)) {
                return loadCookingRecipe(holder, resultItem, CookingType.CAMPFIRE,
                        originalRecipeTypeId);
            } else if (isAvaritiaRecipe(recipeTypeName, recipeClassName)) {
                CraftingMode mode = recipeTypeName.contains("shaped")
                        || recipeClassName.contains("shaped")
                        ? CraftingMode.SHAPED : CraftingMode.SHAPELESS;
                return loadAvaritiaRecipe(holder, resultItem, mode, originalRecipeTypeId);
            } else if (isSmithingRecipe(recipeTypeName, recipeClassName)) {
                return loadSmithingRecipe(holder, resultItem, originalRecipeTypeId);
            }
            return loadGenericModRecipe(holder, resultItem, originalRecipeTypeId);
        } catch (Exception e) {
            LOGGER.error("Failed to load recipe", e);
            return new LoadResult(false, GuiText.string(
                    "registerhelper.message.recipe.load_failed", e.getMessage()));
        }
    }

    private LoadResult loadGenericModRecipe(RecipeHolder<?> holder, ItemStack resultItem,
                                             String originalRecipeTypeId) {
        try {
            List<ItemStack> ingredients = readIngredients(holder.value(),
                    holder.value().getIngredients().size());
            LOGGER.info("Loaded recipe {} of type {} with {} ingredients",
                    holder.id(), originalRecipeTypeId, ingredients.size());
            return new LoadResult(true, null, null, null, 1, resultItem, ingredients,
                    GuiText.string("registerhelper.message.recipe.loaded_mod"),
                    originalRecipeTypeId);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse mod recipe", e);
            return new LoadResult(false, GuiText.string(
                    "registerhelper.message.recipe.parse_failed", e.getMessage()));
        }
    }

    private LoadResult loadCraftingRecipe(RecipeHolder<?> holder, ItemStack resultItem,
                                           CraftingMode mode, String originalRecipeTypeId) {
        try {
            List<ItemStack> ingredients = readIngredients(holder.value(), 9);
            return new LoadResult(true, RecipeType.CRAFTING, mode, null, 1,
                    resultItem, ingredients,
                    GuiText.string("registerhelper.message.recipe.loaded_crafting"),
                    originalRecipeTypeId);
        } catch (Exception e) {
            LOGGER.error("Failed to parse crafting recipe", e);
            return new LoadResult(false, GuiText.string(
                    "registerhelper.message.recipe.parse_crafting_failed", e.getMessage()));
        }
    }

    private LoadResult loadCookingRecipe(RecipeHolder<?> holder, ItemStack resultItem,
                                          CookingType cookingType, String originalRecipeTypeId) {
        try {
            List<ItemStack> ingredients = readIngredients(holder.value(), 1);
            return new LoadResult(true, RecipeType.COOKING, null, cookingType, 1,
                    resultItem, ingredients,
                    GuiText.string("registerhelper.message.recipe.loaded_cooking"),
                    originalRecipeTypeId);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse cooking recipe", e);
            return new LoadResult(false, GuiText.string(
                    "registerhelper.message.recipe.parse_cooking_failed", e.getMessage()));
        }
    }

    private LoadResult loadAvaritiaRecipe(RecipeHolder<?> holder, ItemStack resultItem,
                                           CraftingMode mode, String originalRecipeTypeId) {
        try {
            int tier = DynamicRecipeBuilder.getTierFromIngredientCount(
                    holder.value().getIngredients().size());
            int gridSize = DynamicRecipeBuilder.getGridSizeForTier(tier);
            List<ItemStack> ingredients = readIngredients(holder.value(), gridSize * gridSize);
            return new LoadResult(true, RecipeType.AVARITIA, mode, null, tier,
                    resultItem, ingredients,
                    GuiText.string("registerhelper.message.recipe.loaded_avaritia"),
                    originalRecipeTypeId);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Avaritia recipe", e);
            return new LoadResult(false, GuiText.string(
                    "registerhelper.message.recipe.parse_avaritia_failed", e.getMessage()));
        }
    }

    private LoadResult loadSmithingRecipe(RecipeHolder<?> holder, ItemStack resultItem,
                                           String originalRecipeTypeId) {
        try {
            List<ItemStack> ingredients = readIngredients(holder.value(), 3);
            return new LoadResult(true, null, null, null, 1, resultItem, ingredients,
                    GuiText.string("registerhelper.message.recipe.loaded_smithing"),
                    originalRecipeTypeId);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse smithing recipe", e);
            return new LoadResult(false, GuiText.string(
                    "registerhelper.message.recipe.parse_smithing_failed", e.getMessage()));
        }
    }

    private List<ItemStack> readIngredients(Recipe<?> recipe, int slotCount) {
        List<ItemStack> ingredients = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            ingredients.add(ItemStack.EMPTY);
        }
        var recipeIngredients = recipe.getIngredients();
        for (int i = 0; i < Math.min(recipeIngredients.size(), slotCount); i++) {
            var ingredient = recipeIngredients.get(i);
            if (ingredient == null || ingredient.isEmpty()) continue;
            ItemStack[] items = ingredient.getItems();
            if (items.length > 0) {
                ingredients.set(i, items[0].copy());
            }
        }
        return ingredients;
    }

    private boolean isShapedCraftingRecipe(String typeName, String className) {
        return typeName.contains("crafting_shaped") || className.contains("shapedrecipe");
    }

    private boolean isShapelessCraftingRecipe(String typeName, String className) {
        return typeName.contains("crafting_shapeless") || className.contains("shapelessrecipe");
    }

    private boolean isSmeltingRecipe(String typeName, String className) {
        return typeName.contains("smelting") || className.contains("smeltingrecipe");
    }

    private boolean isBlastingRecipe(String typeName, String className) {
        return typeName.contains("blasting") || className.contains("blastingrecipe");
    }

    private boolean isSmokingRecipe(String typeName, String className) {
        return typeName.contains("smoking") || className.contains("smokingrecipe");
    }

    private boolean isCampfireRecipe(String typeName, String className) {
        return typeName.contains("campfire") || className.contains("campfirerecipe");
    }

    private boolean isAvaritiaRecipe(String typeName, String className) {
        return typeName.contains("avaritia") || className.contains("avaritia");
    }

    private boolean isSmithingRecipe(String typeName, String className) {
        return typeName.contains("smithing") || className.contains("smithing");
    }

    private List<IngredientData> patchFromShapedKey(JsonObject json, LoadResult result) {
        if (!json.has("pattern") || !json.has("key")) return patchFallback(result);
        JsonArray patternArray = json.getAsJsonArray("pattern");
        JsonObject keyObject = json.getAsJsonObject("key");

        List<String> patternRows = new ArrayList<>();
        int patternWidth = 0;
        for (JsonElement row : patternArray) {
            String patternRow = row.getAsString();
            patternRows.add(patternRow);
            patternWidth = Math.max(patternWidth, patternRow.length());
        }
        int editorWidth = getEditorGridWidth(result, patternWidth);

        List<IngredientData> dataList = new ArrayList<>();
        for (int i = 0; i < result.ingredients.size(); i++) {
            int row = i / editorWidth;
            int column = i % editorWidth;
            if (row >= patternRows.size() || column >= patternRows.get(row).length()) {
                dataList.add(IngredientData.empty());
                continue;
            }
            String symbol = String.valueOf(patternRows.get(row).charAt(column));
            if (" ".equals(symbol) || !keyObject.has(symbol)
                    || !keyObject.get(symbol).isJsonObject()) {
                dataList.add(IngredientData.empty());
            } else {
                int sourceIndex = row * patternWidth + column;
                ItemStack fallback = sourceIndex < result.ingredients.size()
                        ? result.ingredients.get(sourceIndex) : ItemStack.EMPTY;
                dataList.add(ingredientDataFromJson(keyObject.getAsJsonObject(symbol),
                        fallback));
            }
        }
        return dataList;
    }

    private int getEditorGridWidth(LoadResult result, int patternWidth) {
        if (result.recipeType == RecipeType.CRAFTING) {
            return 3;
        }
        if (result.recipeType == RecipeType.AVARITIA) {
            return DynamicRecipeBuilder.getGridSizeForTier(result.avaritiaTeir);
        }

        int squareWidth = (int) Math.sqrt(result.ingredients.size());
        return squareWidth > 0 && squareWidth * squareWidth == result.ingredients.size()
                ? squareWidth : Math.max(1, patternWidth);
    }

    private List<IngredientData> patchFromIngredientArray(JsonArray array,
                                                           LoadResult result) {
        List<IngredientData> dataList = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            ItemStack fallback = i < result.ingredients.size()
                    ? result.ingredients.get(i) : ItemStack.EMPTY;
            JsonElement element = array.get(i);
            dataList.add(element.isJsonObject()
                    ? ingredientDataFromJson(element.getAsJsonObject(), fallback)
                    : fallback.isEmpty() ? IngredientData.empty()
                    : IngredientData.fromItem(fallback));
        }
        return dataList;
    }

    private List<IngredientData> patchSmithingIngredients(JsonObject json,
                                                           LoadResult result) {
        List<IngredientData> dataList = new ArrayList<>();
        String[] keys = {"template", "base", "addition"};
        for (int i = 0; i < keys.length; i++) {
            ItemStack fallback = i < result.ingredients.size()
                    ? result.ingredients.get(i) : ItemStack.EMPTY;
            JsonElement element = json.get(keys[i]);
            dataList.add(element != null && element.isJsonObject()
                    ? ingredientDataFromJson(element.getAsJsonObject(), fallback)
                    : fallback.isEmpty() ? IngredientData.empty()
                    : IngredientData.fromItem(fallback));
        }
        return dataList;
    }

    private List<IngredientData> patchNamedIngredients(JsonObject json, String[] keys,
                                                       LoadResult result) {
        List<IngredientData> dataList = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            ItemStack fallback = i < result.ingredients.size()
                    ? result.ingredients.get(i) : ItemStack.EMPTY;
            JsonElement element = json.get(keys[i]);
            dataList.add(element != null && element.isJsonObject()
                    ? ingredientDataFromJson(element.getAsJsonObject(), fallback)
                    : fallback.isEmpty() ? IngredientData.empty()
                    : IngredientData.fromItem(fallback));
        }
        return dataList;
    }

    private List<IngredientData> patchFallback(LoadResult result) {
        List<IngredientData> dataList = new ArrayList<>();
        for (ItemStack stack : result.ingredients) {
            dataList.add(stack.isEmpty()
                    ? IngredientData.empty() : IngredientData.fromItem(stack));
        }
        return dataList;
    }

    private IngredientData ingredientDataFromJson(JsonObject json,
                                                   ItemStack fallbackStack) {
        try {
            if (json.has("tag")) {
                return IngredientData.fromTag(ResourceLocation.parse(
                        json.get("tag").getAsString()));
            }

            String type = json.has("type") ? json.get("type").getAsString() : "";
            ItemStack decodedStack = decodeIngredientStack(json, fallbackStack);
            if ("registerhelper:partial_nbt".equals(type)) {
                IngredientData data = ingredientFromFallback(decodedStack, true);
                data.setIncludeNBT(true);
                if (json.has("ignore_keys") && json.get("ignore_keys").isJsonArray()) {
                    List<String> keys = new ArrayList<>();
                    json.getAsJsonArray("ignore_keys")
                            .forEach(element -> keys.add(element.getAsString()));
                    data.setIgnoreNbtKeys(keys);
                }
                return data;
            }

            IngredientData data = ingredientFromFallback(decodedStack, true);
            if ("forge:nbt".equals(type) || "neoforge:nbt".equals(type)
                    || json.has("components") || json.has("nbt")) {
                data.setIncludeNBT(true);
            } else if (OldUtils.hasTag(fallbackStack)) {
                data.setIncludeNBT(false);
            }
            return data;
        } catch (Exception e) {
            LOGGER.warn("[RegisterHelper] Failed to parse ingredient metadata: {}",
                    e.getMessage());
            return ingredientFromFallback(fallbackStack, true);
        }
    }

    private ItemStack decodeIngredientStack(JsonObject json, ItemStack fallbackStack) {
        ItemStack stack = createIngredientBaseStack(json, fallbackStack);
        if (stack.isEmpty()) {
            return stack;
        }

        if (json.has("components") && json.get("components").isJsonObject()) {
            var ops = OldUtils.getRegistryAccess().createSerializationContext(JsonOps.INSTANCE);
            DataComponentPredicate predicate = DataComponentPredicate.CODEC
                    .parse(ops, json.get("components"))
                    .resultOrPartial(message -> LOGGER.warn(
                            "[RegisterHelper] Failed to decode ingredient components: {}",
                            message))
                    .orElse(null);
            if (predicate != null) {
                stack.applyComponents(predicate.asPatch());
            }
        }

        if (json.has("nbt")) {
            var legacyNbt = DataComponentsHelper.parseSnbt(json.get("nbt"));
            if (legacyNbt != null) {
                OldUtils.setTag(stack, legacyNbt);
            }
        }
        return stack;
    }

    private ItemStack createIngredientBaseStack(JsonObject json,
                                                ItemStack fallbackStack) {
        JsonElement itemElement = json.has("id") ? json.get("id") : json.get("item");
        if (itemElement == null && json.has("items")) {
            JsonElement items = json.get("items");
            if (items.isJsonPrimitive()) {
                itemElement = items;
            } else if (items.isJsonArray() && !items.getAsJsonArray().isEmpty()) {
                itemElement = items.getAsJsonArray().get(0);
            }
        }

        if (itemElement != null && itemElement.isJsonPrimitive()
                && !itemElement.getAsString().startsWith("#")) {
            JsonObject stackJson = new JsonObject();
            stackJson.add("id", itemElement.deepCopy());
            if (json.has("count")) {
                stackJson.add("count", json.get("count").deepCopy());
            }
            ItemStack parsed = DataComponentsHelper.parseItemStack(stackJson);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        return fallbackStack == null ? ItemStack.EMPTY : fallbackStack.copy();
    }

    private IngredientData ingredientFromFallback(ItemStack fallbackStack,
                                                   boolean includeNbt) {
        IngredientData data = fallbackStack == null || fallbackStack.isEmpty()
                ? IngredientData.empty() : IngredientData.fromItem(fallbackStack);
        data.setIncludeNBT(includeNbt);
        return data;
    }

    public List<UnifiedRecipeInfo> getEditableRecipes() {
        List<UnifiedRecipeInfo> recipes = new ArrayList<>();
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                List<UnifiedRecipeInfo> cached =
                        com.wzz.registerhelper.network.RecipeClientCache.getCachedRecipes();
                if (cached.isEmpty()) {
                    LOGGER.info("Recipe cache empty; requesting editable recipes");
                    com.wzz.registerhelper.network.RequestRecipeListPacket.sendToServer(1);
                    return recipes;
                }
                for (UnifiedRecipeInfo info : cached) {
                    if (!info.isBlacklisted) recipes.add(info);
                }
                return recipes;
            }

            collectServerRecipes(server, recipes, false);
            recipes.removeIf(info -> info.isBlacklisted);
            recipes.sort((a, b) -> {
                int overrideCompare = Boolean.compare(b.hasOverride, a.hasOverride);
                if (overrideCompare != 0) return overrideCompare;
                int sourceCompare = a.source.compareTo(b.source);
                if (sourceCompare != 0) return sourceCompare;
                return a.id.toString().compareTo(b.id.toString());
            });
        } catch (Exception e) {
            LOGGER.error("Failed to get editable recipes", e);
        }
        return recipes;
    }

    public List<UnifiedRecipeInfo> getAllRecipes() {
        List<UnifiedRecipeInfo> recipes = new ArrayList<>();
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                List<UnifiedRecipeInfo> cached =
                        com.wzz.registerhelper.network.RecipeClientCache.getCachedRecipes();
                if (cached.isEmpty()) {
                    LOGGER.info("Recipe cache empty; requesting all recipes");
                    com.wzz.registerhelper.network.RequestRecipeListPacket.sendToServer(0);
                    return recipes;
                }
                return new ArrayList<>(cached);
            }

            collectServerRecipes(server, recipes, true);
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
            LOGGER.error("Failed to get recipe list", e);
        }
        return recipes;
    }

    private void collectServerRecipes(MinecraftServer server,
                                      List<UnifiedRecipeInfo> recipes,
                                      boolean includeBlacklisted) {
        ServerLevel level = server.overworld();
        level.getRecipeManager().getRecipes().forEach(holder -> {
            Recipe<?> recipe = holder.value();
            ResourceLocation id = holder.id();
            boolean blacklisted = RecipeBlacklistManager.isBlacklisted(id);
            if (!includeBlacklisted && blacklisted) return;
            boolean override = UnifiedRecipeOverrideManager.hasOverride(id);
            String description = recipe.getType() + " -> "
                    + recipe.getResultItem(server.registryAccess()).getHoverName().getString();
            recipes.add(new UnifiedRecipeInfo(id, determineRecipeSource(id),
                    blacklisted, override, description));
        });
    }

    public static boolean isRemoteServer() {
        return ServerLifecycleHooks.getCurrentServer() == null;
    }

    public void requestServerRecipes() {
        if (isRemoteServer()) {
            com.wzz.registerhelper.network.RecipeClientCache.clearCache();
            com.wzz.registerhelper.network.RequestRecipeListPacket.sendToServer(0);
        }
    }

    private RecipeManager getRecipeManager() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.getRecipeManager();
        return minecraft.level != null ? minecraft.level.getRecipeManager() : null;
    }

    private RegistryAccess getRegistryAccess() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        return minecraft.level != null ? minecraft.level.registryAccess() : null;
    }

    public UnifiedRecipeInfo findRecipeInfo(ResourceLocation recipeId) {
        return getAllRecipes().stream()
                .filter(info -> info.id.equals(recipeId))
                .findFirst()
                .orElse(null);
    }

    private String determineRecipeSource(ResourceLocation recipeId) {
        String namespace = recipeId.getNamespace();
        String path = recipeId.getPath();
        if (namespace.equals("registerhelper") || path.startsWith("custom_")
                || path.contains("_custom_")) {
            return GuiText.string("registerhelper.recipe.source.custom");
        }
        if (namespace.equals("minecraft")) {
            return GuiText.string("registerhelper.recipe.source.vanilla");
        }
        return GuiText.string("registerhelper.recipe.source.mod", namespace);
    }

    public boolean isCustomRecipe(ResourceLocation recipeId) {
        String namespace = recipeId.getNamespace();
        String path = recipeId.getPath();
        return namespace.equals("registerhelper") || path.startsWith("custom_")
                || path.contains("_custom_");
    }
}
