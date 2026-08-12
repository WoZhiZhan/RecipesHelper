package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.neoforged.fml.ModList;

import java.util.Map;

public class CreateRecipeProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("create");
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String type = request.recipeType.toLowerCase();
        if (type.contains(":")) {
            type = type.substring(type.indexOf(":") + 1);
        }
        return switch (type) {
            case "emptying" -> createEmptyingRecipe(request);
            case "cutting" -> createCuttingRecipe(request);
            case "compacting" -> createCompactingRecipe(request);
            case "pressing" -> createPressingRecipe(request);
            case "filling" -> createFillingRecipe(request);
            case "mixing" -> createMixingRecipe(request);
            default -> null;
        };
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"emptying", "cutting", "compacting", "pressing", "mixing", "filling"};
    }

    private JsonObject createEmptyingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:emptying");
        recipe.add("ingredients", buildIngredients(request.ingredients, false, request));
        recipe.add("results", buildResults(request));
        return recipe;
    }

    private JsonObject createCuttingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:cutting");
        recipe.add("ingredients", buildIngredients(request.ingredients, false, request));
        recipe.add("results", buildResults(request));
        recipe.addProperty("processing_time", getNumber(request, "processing_time", "processingTime", 100));
        return recipe;
    }

    private JsonObject createCompactingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:compacting");
        recipe.add("ingredients", buildIngredients(request.ingredients, true, request));
        recipe.add("results", buildResults(request));
        return recipe;
    }

    private JsonObject createMixingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:mixing");
        recipe.add("ingredients", buildIngredients(request.ingredients, true, request));
        recipe.add("results", buildResults(request));
        return recipe;
    }

    private JsonObject createPressingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:pressing");
        recipe.add("ingredients", buildIngredients(request.ingredients, false, request));
        recipe.add("results", buildResults(request));
        return recipe;
    }

    private JsonObject createFillingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:filling");
        recipe.add("ingredients", buildIngredients(request.ingredients, true, request));
        recipe.add("results", buildResults(request));
        return recipe;
    }

    private JsonArray buildIngredients(Object[] ingredients, boolean allowFluid, RecipeRequest request) {
        JsonArray array = new JsonArray();
        boolean hasFluid = false;

        if (ingredients != null) {
            for (Object ingredient : ingredients) {
                JsonObject object = null;
                if (allowFluid && ingredient instanceof Map<?, ?> map && map.containsKey("fluid")) {
                    object = createFluidIngredient(map, 250);
                    hasFluid = object != null;
                } else {
                    object = RecipeUtil.createIngredientJson(ingredient);
                }
                if (object != null) {
                    array.add(object);
                }
            }
        }

        if (allowFluid && !hasFluid) {
            Object fluid = request.properties.get("fluid");
            if (fluid instanceof String fluidId && !fluidId.isBlank()) {
                Map<String, Object> fluidData = new java.util.HashMap<>();
                fluidData.put("fluid", fluidId);
                fluidData.put("amount", request.properties.getOrDefault("amount", 250));
                copyJsonProperty(request.properties.get("components"), fluidData, "components");
                JsonObject fluidIngredient = createFluidIngredient(fluidData, 250);
                if (fluidIngredient != null) {
                    array.add(fluidIngredient);
                    hasFluid = true;
                }
            }
        }

        // Filling has always offered a usable default in the editor. Keep that
        // default while still emitting the 1.21 typed fluid ingredient shape.
        if (allowFluid && !hasFluid && request.recipeType.toLowerCase().contains("filling")) {
            Map<String, Object> fluidData = new java.util.HashMap<>();
            fluidData.put("fluid", "minecraft:water");
            fluidData.put("amount", 250);
            array.add(createFluidIngredient(fluidData, 250));
        }
        return array;
    }

    private JsonArray buildResults(RecipeRequest request) {
        JsonArray array = new JsonArray();

        if (request.result != null && !request.result.isEmpty()) {
            array.add(RecipeUtil.createResultJson(request.result, request.resultCount));
        }

        if (request.properties.containsKey("fluidOutput")) {
            Object value = request.properties.get("fluidOutput");
            if (value instanceof Map<?, ?> fluidOutput) {
                JsonObject fluidResult = createFluidResult(fluidOutput);
                if (fluidResult != null) {
                    array.add(fluidResult);
                }
            }
        }

        return array;
    }

    private JsonObject createFluidIngredient(Map<?, ?> data, int defaultAmount) {
        String id = stringValue(data.get("fluid"), stringValue(data.get("id"), ""));
        if (id.isBlank()) return null;

        JsonObject ingredient = new JsonObject();
        JsonElement components = jsonValue(data.get("components"));
        if (components != null && components.isJsonObject()) {
            ingredient.addProperty("type", "neoforge:components");
            JsonArray fluids = new JsonArray();
            fluids.add(id.startsWith("#") ? id : ensureNamespace(id));
            ingredient.add("fluids", fluids);
            ingredient.add("components", components.deepCopy());
            if (Boolean.TRUE.equals(data.get("strict"))) {
                ingredient.addProperty("strict", true);
            }
        } else if (id.startsWith("#")) {
            ingredient.addProperty("type", "neoforge:tag");
            ingredient.addProperty("tag", ensureNamespace(id.substring(1)));
        } else {
            ingredient.addProperty("type", "neoforge:single");
            ingredient.addProperty("fluid", ensureNamespace(id));
        }
        ingredient.addProperty("amount", Math.max(1, numberValue(data.get("amount"), defaultAmount)));
        return ingredient;
    }

    private JsonObject createFluidResult(Map<?, ?> data) {
        String id = stringValue(data.get("fluid"), stringValue(data.get("id"), ""));
        if (id.isBlank()) return null;

        JsonObject result = new JsonObject();
        result.addProperty("id", ensureNamespace(id));
        result.addProperty("amount", Math.max(1, numberValue(data.get("amount"), 250)));
        JsonElement components = jsonValue(data.get("components"));
        if (components != null && components.isJsonObject()) {
            result.add("components", components.deepCopy());
        }
        return result;
    }

    private int getNumber(RecipeRequest request, String primary, String fallback, int defaultValue) {
        Object value = request.properties.get(primary);
        if (!(value instanceof Number)) {
            value = request.properties.get(fallback);
        }
        return numberValue(value, defaultValue);
    }

    private int numberValue(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private String stringValue(Object value, String defaultValue) {
        return value instanceof String string ? string : defaultValue;
    }

    private JsonElement jsonValue(Object value) {
        return value instanceof JsonElement json ? json : null;
    }

    private String ensureNamespace(String id) {
        if (id == null || id.isEmpty() || id.contains(":")) {
            return id == null || id.isEmpty() ? "minecraft:empty" : id;
        }
        return "minecraft:" + id;
    }

    private void copyJsonProperty(Object value, Map<String, Object> destination, String key) {
        if (value instanceof JsonElement) {
            destination.put(key, value);
        }
    }

}
