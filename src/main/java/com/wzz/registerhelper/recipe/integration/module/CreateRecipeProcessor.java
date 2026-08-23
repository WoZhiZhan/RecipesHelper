package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

import static com.wzz.registerhelper.util.RecipeUtil.createIngredientJson;

/** JSON adapter for Create's data-driven recipe serializers. */
public class CreateRecipeProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("create");
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String type = normalizeType(request.recipeType);
        return switch (type) {
            case "emptying" -> createEmptyingRecipe(request);
            case "cutting" -> createProcessingRecipe(request, type);
            case "compacting" -> createProcessingRecipe(request, type);
            case "pressing" -> createProcessingRecipe(request, type);
            case "filling" -> createFillingRecipe(request);
            case "mixing" -> createProcessingRecipe(request, type);
            case "crushing", "milling", "splashing", "haunting",
                    "sandpaper_polishing" -> createProcessingRecipe(request, type);
            case "deploying", "item_application" -> createDeployingRecipe(request, type);
            case "mechanical_crafting" -> createMechanicalCraftingRecipe(request);
            case "sequenced_assembly" -> createSequencedAssemblyRecipe(request);
            default -> null;
        };
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
                "emptying", "cutting", "compacting", "pressing", "mixing", "filling",
                "crushing", "milling", "splashing", "haunting", "deploying", "item_application",
                "sandpaper_polishing", "mechanical_crafting", "sequenced_assembly"
        };
    }

    @Override
    public boolean isShapedRecipe(String recipeType) {
        return "mechanical_crafting".equals(normalizeType(recipeType));
    }

    private JsonObject createEmptyingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:emptying");
        recipe.add("ingredients", buildIngredients(request, false));
        JsonArray results = buildResults(request);
        if (!containsFluid(results)) {
            throw new IllegalArgumentException("Create emptying requires a fluid output");
        }
        recipe.add("results", results);
        return recipe;
    }

    private JsonObject createProcessingRecipe(RecipeRequest request, String type) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:" + type);
        boolean acceptsFluids = "compacting".equals(type) || "mixing".equals(type);
        recipe.add("ingredients", buildIngredients(request, acceptsFluids));
        recipe.add("results", buildResults(request));
        addProcessingProperties(recipe, request);
        return recipe;
    }

    private JsonObject createFillingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:filling");

        JsonArray ingredients = new JsonArray();
        boolean hasFluid = false;
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                JsonObject json = createIngredientJson(ingredient);
                if (json != null) {
                    ingredients.add(json);
                    hasFluid |= json.has("fluid") || json.has("fluidTag");
                }
            }
        }

        if (!hasFluid) {
            for (JsonObject fluid : configuredFluidInputs(request)) {
                ingredients.add(fluid);
                hasFluid = true;
            }
        }
        if (!hasFluid) {
            throw new IllegalArgumentException("Create filling requires a fluid input");
        }

        recipe.add("ingredients", ingredients);
        JsonArray results = buildResults(request);
        if (results.isEmpty()) throw new IllegalArgumentException("Create filling requires an item output");
        recipe.add("results", results);
        return recipe;
    }

    private JsonObject createDeployingRecipe(RecipeRequest request, String type) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:" + type);
        recipe.add("ingredients", buildIngredients(request, false));
        recipe.add("results", buildResults(request));
        if (request.properties.containsKey("keepHeldItem")) {
            recipe.addProperty("keepHeldItem",
                    Boolean.parseBoolean(String.valueOf(request.properties.get("keepHeldItem"))));
        }
        addProcessingProperties(recipe, request);
        return recipe;
    }

    private JsonObject createMechanicalCraftingRecipe(RecipeRequest request) {
        JsonObject recipe = RecipeUtil.createShapedTableRecipe("create:mechanical_crafting", request);
        Object mirrored = request.properties.get("acceptMirrored");
        if (mirrored != null) {
            recipe.addProperty("acceptMirrored", Boolean.parseBoolean(String.valueOf(mirrored)));
        }
        return recipe;
    }

    private JsonObject createSequencedAssemblyRecipe(RecipeRequest request) {
        Object input = roleValue(request, "INPUT");
        Object transitional = roleValue(request, "TRANSITIONAL");
        if (input == null || transitional == null) {
            throw new IllegalArgumentException("Sequenced assembly requires an input and transitional item");
        }

        ItemStack transitionalStack = asItemStack(transitional);
        if (transitionalStack.isEmpty()) {
            throw new IllegalArgumentException("Sequenced assembly transitional item must be an item");
        }

        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:sequenced_assembly");
        recipe.add("ingredient", ingredientJsonForRequest(input));
        recipe.add("transitionalItem", itemResult(transitionalStack, transitionalStack.getCount(), 1.0F));
        recipe.addProperty("loops", number(request.properties.get("loops"), 5));
        recipe.add("results", buildResults(request));

        Object rawSequence = request.properties.get("sequence");
        if (rawSequence instanceof JsonElement element && element.isJsonArray()
                && sequenceMatchesType(element, request.properties.get("sequenceType"))) {
            recipe.add("sequence", element.deepCopy());
        } else {
            recipe.add("sequence", defaultSequence(request, transitionalStack));
        }
        return recipe;
    }

    private JsonArray defaultSequence(RecipeRequest request, ItemStack transitional) {
        String type = String.valueOf(request.properties.getOrDefault("sequenceType", "create:pressing"));
        if (!type.contains(":")) type = "create:" + type;
        if (!"create:pressing".equals(type) && !"create:cutting".equals(type)) {
            throw new IllegalArgumentException(
                    "New sequenced assembly steps currently support create:pressing or create:cutting");
        }
        JsonObject step = new JsonObject();
        step.addProperty("type", type);
        JsonArray ingredients = new JsonArray();
        ingredients.add(ingredientJsonForRequest(transitional));
        step.add("ingredients", ingredients);
        JsonArray results = new JsonArray();
        results.add(itemResult(transitional, transitional.getCount(), 1.0F));
        step.add("results", results);
        Object processingTime = request.properties.get("processingTime");
        if (processingTime instanceof Number number) {
            step.addProperty("processingTime", number.intValue());
        }
        JsonArray sequence = new JsonArray();
        sequence.add(step);
        return sequence;
    }

    private boolean sequenceMatchesType(JsonElement sequence, Object requestedType) {
        if (requestedType == null || sequence.getAsJsonArray().isEmpty()) return true;
        JsonElement first = sequence.getAsJsonArray().get(0);
        if (!first.isJsonObject() || !first.getAsJsonObject().has("type")) return false;
        String requested = String.valueOf(requestedType);
        if (!requested.contains(":")) requested = "create:" + requested;
        return requested.equals(first.getAsJsonObject().get("type").getAsString());
    }

    private Object roleValue(RecipeRequest request, String role) {
        if (request.ingredients == null) return null;
        java.util.List<?> roles = request.properties.get("slotRoles") instanceof java.util.List<?> list
                ? list : java.util.List.of();
        for (int i = 0; i < request.ingredients.length; i++) {
            String actual = roles.size() > i ? String.valueOf(roles.get(i)) : "INPUT";
            if (role.equals(actual)) return request.ingredients[i];
        }
        return null;
    }

    private ItemStack asItemStack(Object value) {
        if (value instanceof ItemStack stack) return stack.copy();
        if (value instanceof com.wzz.registerhelper.gui.recipe.IngredientData data) {
            return data.getDisplayStack().copy();
        }
        return ItemStack.EMPTY;
    }

    private JsonObject ingredientJsonForRequest(Object value) {
        if (value instanceof ItemStack stack) return createIngredientJson(stack);
        return createIngredientJson(value);
    }

    private JsonArray buildIngredients(RecipeRequest request, boolean allowFluid) {
        JsonArray array = new JsonArray();
        boolean hasFluid = false;
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                JsonObject json = null;
                if (allowFluid && ingredient instanceof Map<?, ?> map && map.containsKey("fluid")) {
                    json = fluid(String.valueOf(map.get("fluid")),
                            number(map.get("amount"), 100), false);
                    Object nbt = map.get("nbt");
                    if (nbt instanceof JsonElement element) {
                        json.add("nbt", element);
                    }
                    hasFluid = true;
                } else {
                    json = createIngredientJson(ingredient);
                    if (json != null) {
                        hasFluid |= json.has("fluid") || json.has("fluidTag");
                    }
                }
                if (json != null) {
                    array.add(json);
                }
            }
        }

        if (allowFluid && !hasFluid) {
            for (JsonObject fluid : configuredFluidInputs(request)) {
                array.add(fluid);
            }
        }
        return array;
    }

    private JsonArray buildResults(RecipeRequest request) {
        JsonArray array = new JsonArray();
        int outputIndex = 0;
        Object chancesValue = request.properties.get("outputChances");
        java.util.List<?> chances = chancesValue instanceof java.util.List<?> list ? list : java.util.List.of();

        if (request.result != null && !request.result.isEmpty()) {
            JsonObject result = itemResult(request.result, request.resultCount,
                    chanceAt(chances, outputIndex++));
            array.add(result);
        }

        Object extraResults = request.properties.get("extraResults");
        if (extraResults instanceof ItemStack[] stacks) {
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) {
                    JsonObject result = itemResult(stack, stack.getCount(), chanceAt(chances, outputIndex++));
                    array.add(result);
                }
            }
        }

        Object fluidOutputs = request.properties.get("fluidOutputs");
        if (fluidOutputs instanceof Iterable<?> values) {
            for (Object value : values) addFluidResults(array, value);
        } else {
            addFluidResults(array, request.properties.get("fluidOutput"));
        }
        Object extraFluidOutputs = request.properties.get("extraFluidOutputs");
        if (extraFluidOutputs instanceof Iterable<?> values) {
            for (Object value : values) addFluidResults(array, value);
        }
        return array;
    }

    private void addFluidResults(JsonArray results, Object value) {
        if (!(value instanceof Map<?, ?> map) || map.get("fluid") == null) return;
        String id = String.valueOf(map.get("fluid"));
        if (id.startsWith("#")) {
            throw new IllegalArgumentException("Create fluid outputs must name a concrete fluid: " + id);
        }
        JsonObject result = fluid(id, number(map.get("amount"), 250), id.startsWith("#"));
        Object nbt = map.get("nbt");
        if (nbt instanceof JsonElement element) result.add("nbt", element);
        results.add(result);
    }

    private boolean containsFluid(JsonArray results) {
        for (JsonElement element : results) {
            if (element.isJsonObject() && element.getAsJsonObject().has("fluid")) return true;
        }
        return false;
    }

    private void addProcessingProperties(JsonObject recipe, RecipeRequest request) {
        Object processingTime = request.properties.get("processingTime");
        if (processingTime instanceof Number number) {
            recipe.addProperty("processingTime", number.intValue());
        }
        Object heat = request.properties.get("heatRequirement");
        if (heat != null && !String.valueOf(heat).isBlank()) {
            String value = String.valueOf(heat);
            if (!"heated".equals(value) && !"superheated".equals(value)) {
                throw new IllegalArgumentException("Invalid Create heat requirement: " + value);
            }
            recipe.addProperty("heatRequirement", value);
        }
    }

    private JsonObject configuredFluid(RecipeRequest request) {
        Object id = request.properties.get("fluid");
        if (id == null || String.valueOf(id).isBlank()) return null;
        Object amount = request.properties.containsKey("amount")
                ? request.properties.get("amount") : request.properties.get("fluidAmount");
        return fluid(String.valueOf(id), number(amount, 250), String.valueOf(id).startsWith("#"));
    }

    private java.util.List<JsonObject> configuredFluidInputs(RecipeRequest request) {
        java.util.List<JsonObject> fluids = new java.util.ArrayList<>();
        Object raw = request.properties.get("fluidInputs");
        if (raw instanceof Iterable<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> map && map.get("fluid") != null) {
                    String id = String.valueOf(map.get("fluid"));
                    JsonObject json = fluid(id, number(map.get("amount"), 250), id.startsWith("#"));
                    Object nbt = map.get("nbt");
                    if (nbt instanceof JsonElement element) json.add("nbt", element.deepCopy());
                    fluids.add(json);
                }
            }
            if (!fluids.isEmpty()) return fluids;
        }
        JsonObject configured = configuredFluid(request);
        if (configured != null) fluids.add(configured);
        return fluids;
    }

    private JsonObject itemResult(ItemStack stack, int count, float chance) {
        JsonObject result = new JsonObject();
        result.addProperty("item", RecipeUtil.getItemResourceLocation(stack.getItem()).toString());
        if (count > 1) result.addProperty("count", count);
        if (stack.hasTag()) {
            try {
                result.add("nbt", JsonParser.parseString(stack.getTag().toString()));
            } catch (Exception ignored) {
                result.addProperty("nbt", stack.getTag().toString());
            }
        }
        if (chance != 1.0F) result.addProperty("chance", chance);
        return result;
    }

    private float chanceAt(java.util.List<?> chances, int index) {
        if (index < chances.size() && chances.get(index) instanceof Number number) {
            return number.floatValue();
        }
        return 1.0F;
    }

    private JsonObject fluid(String id, int amount, boolean tag) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Create fluid id cannot be empty");
        }
        String normalized = tag ? id.substring(1) : id;
        if (ResourceLocation.tryParse(normalized) == null) {
            throw new IllegalArgumentException("Invalid Create fluid id: " + id);
        }
        if (!tag && ForgeRegistries.FLUIDS.getValue(new ResourceLocation(normalized)) == null) {
            throw new IllegalArgumentException("Unknown Create fluid: " + id);
        }
        JsonObject json = new JsonObject();
        if (tag) {
            json.addProperty("fluidTag", id.substring(1));
        } else {
            json.addProperty("fluid", id);
        }
        json.addProperty("amount", Math.max(1, amount));
        json.add("nbt", new JsonObject());
        return json;
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String normalizeType(String type) {
        if (type == null) return "";
        int separator = type.indexOf(':');
        return (separator >= 0 ? type.substring(separator + 1) : type).toLowerCase();
    }
}
