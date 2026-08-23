package com.wzz.registerhelper.recipe.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.gui.recipe.dynamic.DynamicRecipeTypeConfig;
import com.wzz.registerhelper.recipe.integration.module.LearnedJsonRecipeProcessor;
import com.wzz.registerhelper.init.ModConfig;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Discovers ordinary square, item-only recipe JSON at reload time. */
public final class LearnedRecipeRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, LearnedJsonRecipeProcessor> PROCESSORS = new HashMap<>();

    private LearnedRecipeRegistry() {
    }

    public static synchronized void learn(Map<ResourceLocation, JsonElement> recipes) {
        PROCESSORS.clear();
        DynamicRecipeTypeConfig.clearLearnedRecipeTypes();
        if (!ModConfig.isLearnedRecipesEnabled()) return;
        if (recipes == null || recipes.isEmpty()) return;

        Set<String> ambiguousTypes = new HashSet<>();
        List<Map.Entry<ResourceLocation, JsonElement>> ordered = new ArrayList<>(recipes.entrySet());
        ordered.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        for (Map.Entry<ResourceLocation, JsonElement> entry : ordered) {
            ResourceLocation recipeId = entry.getKey();
            if (isIgnoredNamespace(recipeId.getNamespace())) continue;
            if (!entry.getValue().isJsonObject()) continue;

            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                String serializerId = json.has("type") && json.get("type").isJsonPrimitive()
                        ? json.get("type").getAsString() : "";
                if (serializerId.isBlank()) continue;
                ResourceLocation typeId = ResourceLocation.tryParse(serializerId);
                if (typeId == null || !typeId.getNamespace().equals(recipeId.getNamespace())) continue;
                if (!isSupportedNormalRecipe(json)) continue;

                String typeKey = typeId.toString();
                if (ambiguousTypes.contains(typeKey)) continue;
                LearnedJsonRecipeProcessor candidate = createProcessor(typeId, json);
                if (candidate == null) continue;
                LearnedJsonRecipeProcessor existing = PROCESSORS.get(typeKey);
                if (existing != null && existing.isShaped() != candidate.isShaped()) {
                    PROCESSORS.remove(typeKey);
                    ambiguousTypes.add(typeKey);
                    LOGGER.debug("跳过同时包含有序和无序结构的配方类型: {}", typeId);
                } else if (existing == null || candidate.getGridSize() > existing.getGridSize()) {
                    PROCESSORS.put(typeKey, candidate);
                }
            } catch (RuntimeException error) {
                LOGGER.debug("跳过格式异常的普通配方: {}", recipeId, error);
            }
        }

        PROCESSORS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> registerProcessor(entry.getKey(), entry.getValue()));
    }

    private static void registerProcessor(String typeKey, LearnedJsonRecipeProcessor processor) {
        if (DynamicRecipeTypeConfig.getRecipeType(typeKey) != null) return;
        ResourceLocation typeId = ResourceLocation.tryParse(typeKey);
        if (typeId == null) return;
        DynamicRecipeTypeConfig.registerRecipeType(
                new DynamicRecipeTypeConfig.RecipeTypeDefinition.Builder(typeKey, typeKey)
                        .modId(typeId.getNamespace())
                        .gridSize(processor.getGridSize(), processor.getGridSize())
                        .supportsFillMode(processor.getGridSize() > 1)
                        .property("category", typeId.getNamespace())
                        .property("mode", processor.isShaped() ? "shaped" : "shapeless")
                        .property("learned", true)
                        .processor(processor)
                        .build());
        LOGGER.info("自动学习普通配方类型: {} ({}x{})", typeId,
                processor.isShaped() ? "shaped" : "shapeless", processor.getGridSize());
    }

    private static LearnedJsonRecipeProcessor createProcessor(ResourceLocation typeId, JsonObject template) {
        boolean shaped = template.has("pattern") && template.has("key");
        int size = shaped ? patternSize(template.getAsJsonArray("pattern"))
                : ingredientGridSize(template.getAsJsonArray("ingredients"));
        if (size < 1 || size > 9) return null;
        String outputKey = template.has("result") ? "result" : "output";
        return new LearnedJsonRecipeProcessor(typeId, template, shaped, size, outputKey);
    }

    private static boolean isSupportedNormalRecipe(JsonObject json) {
        if (json == null || !json.has("type") || !json.get("type").isJsonPrimitive()) {
            return false;
        }
        boolean shaped = json.has("pattern") && json.has("key") && json.has("result");
        boolean shapeless = json.has("ingredients")
                && (json.has("result") || json.has("output"));
        if (!shaped && !shapeless) return false;
        if (json.has("fluid") || json.has("fluidTag") || json.has("heatRequirement")
                || json.has("sequence") || json.has("input") || json.has("catalyst")
                || json.has("reagent") || json.has("state") || json.has("outputState")) {
            return false;
        }
        if (shaped) {
            if (!json.get("pattern").isJsonArray() || !json.get("key").isJsonObject()) {
                return false;
            }
            JsonArray pattern = json.getAsJsonArray("pattern");
            if (pattern.isEmpty()) return false;
            int width = pattern.get(0).getAsString().length();
            if (width != pattern.size() || width < 1 || width > 9) return false;
            for (JsonElement row : pattern) {
                if (!row.isJsonPrimitive() || row.getAsString().length() != width) return false;
            }
            for (JsonElement key : json.getAsJsonObject("key").entrySet().stream()
                    .map(Map.Entry::getValue).toList()) {
                if (!isSimpleIngredient(key)) return false;
            }
            return isSimpleResult(json.get("result"));
        }

        if (!json.get("ingredients").isJsonArray()) return false;
        JsonArray ingredients = json.getAsJsonArray("ingredients");
        if (ingredients.isEmpty() || ingredients.size() > 81) return false;
        for (JsonElement ingredient : ingredients) {
            if (!isSimpleIngredient(ingredient)) return false;
        }
        JsonElement output = json.has("result") ? json.get("result") : json.get("output");
        return isSimpleResult(output);
    }

    private static boolean isSimpleIngredient(JsonElement element) {
        if (!element.isJsonObject()) return false;
        JsonObject object = element.getAsJsonObject();
        if (!object.has("item") && !object.has("tag")) return false;
        return !object.has("fluid") && !object.has("fluidTag");
    }

    private static boolean isSimpleResult(JsonElement element) {
        if (element == null || !element.isJsonObject()) return false;
        JsonObject object = element.getAsJsonObject();
        return object.has("item") && !object.has("fluid") && !object.has("fluidTag");
    }

    private static int patternSize(JsonArray pattern) {
        return pattern.isEmpty() ? 0 : pattern.get(0).getAsString().length();
    }

    private static int ingredientGridSize(JsonArray ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return 0;
        return Math.max(1, (int) Math.ceil(Math.sqrt(ingredients.size())));
    }

    private static boolean isIgnoredNamespace(String namespace) {
        return "minecraft".equals(namespace) || "registerhelper".equals(namespace);
    }
}
