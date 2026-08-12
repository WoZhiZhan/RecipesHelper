package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.gui.recipe.IngredientData;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.Map;

import static com.wzz.registerhelper.util.RecipeUtil.createIngredientJson;
import static com.wzz.registerhelper.util.RecipeUtil.createResultJson;
import static com.wzz.registerhelper.util.RecipeUtil.getItemResourceLocation;

/** Botania recipe processor for the 1.21.1 recipe codecs. */
public class BotaniaProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("botania");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
                "runic_altar", "mana_infusion", "elven_trade", "terra_plate",
                "petal_apothecary", "pure_daisy", "brew", "orechid",
                "orechid_ignem", "marimorphosis"
        };
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String type = request.recipeType.toLowerCase();
        if (type.contains(":")) {
            type = type.substring(type.indexOf(":") + 1);
        }

        return switch (type) {
            case "runic_altar" -> createRunicAltarRecipe(request, "runic_altar");
            case "mana_infusion" -> createManaInfusionRecipe(request);
            case "elven_trade" -> createElvenTradeRecipe(request);
            case "terra_plate" -> createTerraPlateRecipe(request);
            case "petal_apothecary" -> createPetalApothecaryRecipe(request);
            case "pure_daisy" -> createPureDaisyRecipe(request);
            case "brew" -> createBrewRecipe(request);
            case "orechid" -> createOrechidRecipe(request, "minecraft:stone", "minecraft:iron_ore",
                    100, 17500);
            case "orechid_ignem" -> createOrechidRecipe(request, "minecraft:netherrack", "minecraft:nether_gold_ore",
                    100, 20000);
            case "marimorphosis" -> createOrechidRecipe(request,
                    "#botania:marimorphosis_convertable", "minecraft:stone", 0, 12);
            default -> null;
        };
    }

    private JsonObject createRunicAltarRecipe(RecipeRequest request, String recipeType) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "botania:" + recipeType);
        recipe.add("ingredients", createIngredients(request.ingredients));
        recipe.add("catalysts", createIngredients(request.properties.get("catalysts")));

        Object reagent = request.properties.getOrDefault("reagent", "botania:livingrock");
        JsonObject reagentJson = createIngredient(reagent);
        recipe.add("reagent", reagentJson == null ? createIngredient("botania:livingrock") : reagentJson);
        recipe.addProperty("mana", numberValue(request.properties.get("mana"), 5200));
        recipe.add("output", createResult(request));
        return recipe;
    }

    private JsonObject createManaInfusionRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "botania:mana_infusion");
        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject input = createIngredient(request.ingredients[0]);
            if (input != null) {
                recipe.add("input", input);
            }
        }
        recipe.add("output", createResult(request));
        recipe.addProperty("mana", numberValue(request.properties.get("mana"), 1000));

        Object catalyst = request.properties.get("catalyst");
        if (catalyst != null) {
            recipe.add("catalyst", createStateIngredient(catalyst, "minecraft:air"));
        }
        return recipe;
    }

    private JsonObject createElvenTradeRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "botania:elven_trade");
        recipe.add("ingredients", createIngredients(request.ingredients));

        JsonArray outputs = new JsonArray();
        JsonObject result = createResult(request);
        if (result != null) {
            outputs.add(result);
        }
        Object extraOutputs = request.properties.get("extraOutputs");
        if (extraOutputs instanceof Object[] values) {
            for (Object value : values) {
                JsonObject output = value instanceof ItemStack stack
                        ? createResult(stack, stack.getCount())
                        : createStack(value);
                if (output != null) {
                    outputs.add(output);
                }
            }
        }
        recipe.add("output", outputs);
        return recipe;
    }

    private JsonObject createTerraPlateRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "botania:terra_plate");
        recipe.add("ingredients", createIngredients(request.ingredients));
        recipe.add("result", createResult(request));
        recipe.addProperty("mana", numberValue(request.properties.get("mana"), 500000));
        return recipe;
    }

    private JsonObject createPetalApothecaryRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "botania:petal_apothecary");
        recipe.add("ingredients", createIngredients(request.ingredients));
        recipe.add("output", createResult(request));

        Object reagent = request.properties.getOrDefault("reagent", "#botania:seed_apothecary_reagent");
        JsonObject reagentJson = createIngredient(reagent);
        recipe.add("reagent", reagentJson == null
                ? createIngredient("#botania:seed_apothecary_reagent") : reagentJson);
        return recipe;
    }

    private JsonObject createPureDaisyRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "botania:pure_daisy");
        Object input = request.ingredients != null && request.ingredients.length > 0
                ? request.ingredients[0] : "minecraft:stone";
        recipe.add("input", createStateIngredient(input, "minecraft:stone"));
        Object output = request.properties.getOrDefault("output", request.result);
        recipe.add("output", createStateIngredient(output, "minecraft:air"));
        addBlockStateProperties(recipe, request);
        return recipe;
    }

    private JsonObject createBrewRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "botania:brew");
        recipe.add("ingredients", createIngredients(request.ingredients));
        recipe.addProperty("brew", ensureNamespace(stringValue(request.properties.get("brew"), "speed"), "botania"));
        return recipe;
    }

    private JsonObject createOrechidRecipe(RecipeRequest request, String defaultInput,
                                           String defaultOutput, int defaultCooldown,
                                           int defaultMana) {
        JsonObject recipe = new JsonObject();
        String type = request.recipeType.toLowerCase();
        if (type.contains(":")) {
            type = type.substring(type.indexOf(":") + 1);
        }
        recipe.addProperty("type", "botania:" + type);

        Object input = request.ingredients != null && request.ingredients.length > 0
                ? request.ingredients[0] : defaultInput;
        recipe.add("input", createStateIngredient(input, defaultInput));
        Object output = request.properties.getOrDefault("output", request.result);
        recipe.add("output", createStateIngredient(output, defaultOutput));
        recipe.addProperty("cooldown", numberValue(request.properties.get("cooldown"), defaultCooldown));
        recipe.addProperty("mana", numberValue(request.properties.get("mana"), defaultMana));
        recipe.addProperty("weight", numberValue(request.properties.get("weight"),
                "marimorphosis".equals(type) ? 1 : "orechid_ignem".equals(type) ? 148 : 67415));

        Object bonus = request.properties.containsKey("biome_bonus_weight")
                ? request.properties.get("biome_bonus_weight") : request.properties.get("biome_bonus");
        Object bonusTag = request.properties.get("biome_bonus_tag");
        if ("marimorphosis".equals(type) && bonus == null) {
            bonus = 11;
        }
        if (bonus != null) {
            recipe.addProperty("biome_bonus_weight", numberValue(bonus, 0));
        }
        if (bonusTag != null) {
            recipe.addProperty("biome_bonus_tag",
                    ensureNamespace(stringValue(bonusTag, "marimorphosis_desert_bonus"), "botania"));
        } else if ("marimorphosis".equals(type)) {
            recipe.addProperty("biome_bonus_tag", "botania:marimorphosis_desert_bonus");
        }
        return recipe;
    }

    private JsonArray createIngredients(Object[] values) {
        JsonArray ingredients = new JsonArray();
        if (values == null) {
            return ingredients;
        }
        for (Object value : values) {
            JsonObject ingredient = createIngredient(value);
            if (ingredient != null) {
                ingredients.add(ingredient);
            }
        }
        return ingredients;
    }

    private JsonArray createIngredients(Object value) {
        if (value instanceof Object[] values) {
            return createIngredients(values);
        }
        JsonArray ingredients = new JsonArray();
        if (value instanceof JsonArray values) {
            values.forEach(element -> {
                JsonObject ingredient = createIngredient(element);
                if (ingredient != null) {
                    ingredients.add(ingredient);
                }
            });
        } else if (value != null) {
            JsonObject ingredient = createIngredient(value);
            if (ingredient != null) {
                ingredients.add(ingredient);
            }
        }
        return ingredients;
    }

    private JsonObject createIngredient(Object value) {
        if (value instanceof JsonElement element && element.isJsonObject()) {
            return element.getAsJsonObject().deepCopy();
        }
        if (value instanceof JsonElement element && element.isJsonPrimitive()) {
            return createIngredientJson(element.getAsString());
        }
        return createIngredientJson(value);
    }

    private JsonObject createResult(RecipeRequest request) {
        return request.result == null ? new JsonObject()
                : createResult(request.result, Math.max(1, request.resultCount));
    }

    private JsonObject createResult(ItemStack stack, int count) {
        return stack == null || stack.isEmpty() ? null : createResultJson(stack, count);
    }

    private JsonObject createStack(Object value) {
        if (value instanceof ItemStack stack) {
            return createResult(stack, stack.getCount());
        }
        if (value instanceof IngredientData data && data.getType() == IngredientData.Type.ITEM) {
            return createResult(data.getItemStack(), data.getItemStack().getCount());
        }
        if (value instanceof JsonObject object) {
            JsonObject result = object.deepCopy();
            if (!result.has("id") && result.has("item")) {
                result.add("id", result.remove("item"));
            }
            return result.has("id") ? result : null;
        }
        if (value instanceof String id && !id.isBlank() && !id.startsWith("#")) {
            JsonObject result = new JsonObject();
            result.addProperty("id", ensureNamespace(id, "minecraft"));
            return result;
        }
        return null;
    }

    private JsonObject createStateIngredient(Object value, String defaultBlock) {
        if (value instanceof JsonObject object) {
            return normalizeStateIngredient(object, defaultBlock);
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            map.forEach((key, mapValue) -> {
                if (key instanceof String name && mapValue instanceof JsonElement element) {
                    object.add(name, element.deepCopy());
                } else if (key instanceof String name && mapValue != null) {
                    object.addProperty(name, String.valueOf(mapValue));
                }
            });
            return normalizeStateIngredient(object, defaultBlock);
        }

        String id = getIngredientString(value);
        if (id.startsWith("#")) {
            JsonObject result = new JsonObject();
            result.addProperty("type", "botania:tag");
            result.addProperty("tag", "#" + ensureNamespace(id.substring(1), "minecraft"));
            return result;
        }
        if (id.contains("[")) {
            JsonObject result = new JsonObject();
            result.addProperty("type", "botania:state");
            result.add("state", blockStateFromString(id));
            return result;
        }
        JsonObject result = new JsonObject();
        result.addProperty("type", "botania:block");
        result.addProperty("block", ensureNamespace(id.isBlank() ? defaultBlock : id, "minecraft"));
        return result;
    }

    private JsonObject normalizeStateIngredient(JsonObject source, String defaultBlock) {
        JsonObject result = source.deepCopy();
        String type = stringValue(result.get("type"), "");
        if (type.isBlank()) {
            if (result.has("tag")) type = "botania:tag";
            else if (result.has("state") || result.has("Name")) type = "botania:state";
            else type = "botania:block";
        } else if (!type.contains(":")) {
            type = "botania:" + type;
        }
        result.addProperty("type", type);

        if (type.endsWith(":tag")) {
            String tag = stringValue(result.get("tag"), defaultBlock);
            result.addProperty("tag", "#" + ensureNamespace(tag.startsWith("#") ? tag.substring(1) : tag,
                    "minecraft"));
        } else if (type.endsWith(":state")) {
            JsonElement state = result.get("state");
            if (state == null && result.has("Name")) {
                state = result;
            }
            result.add("state", state != null && state.isJsonPrimitive()
                    ? blockStateFromString(state.getAsString()) : normalizeBlockState(state, defaultBlock));
            result.remove("Name");
            result.remove("Properties");
        } else {
            String block = stringValue(result.get("block"), defaultBlock);
            result.addProperty("block", ensureNamespace(block, "minecraft"));
        }
        return result;
    }

    private JsonObject normalizeBlockState(JsonElement value, String defaultBlock) {
        if (value != null && value.isJsonObject()) {
            JsonObject state = value.getAsJsonObject().deepCopy();
            if (state.has("name") && !state.has("Name")) {
                state.add("Name", state.remove("name"));
            }
            if (state.has("Name")) {
                state.addProperty("Name", ensureNamespace(state.get("Name").getAsString(), "minecraft"));
            } else {
                state.addProperty("Name", ensureNamespace(defaultBlock, "minecraft"));
            }
            return state;
        }
        return blockStateFromString(defaultBlock);
    }

    private JsonObject blockStateFromString(String value) {
        int propertiesStart = value.indexOf('[');
        String block = propertiesStart >= 0 ? value.substring(0, propertiesStart) : value;
        JsonObject state = new JsonObject();
        state.addProperty("Name", ensureNamespace(block, "minecraft"));
        if (propertiesStart >= 0 && value.endsWith("]")) {
            JsonObject properties = new JsonObject();
            String body = value.substring(propertiesStart + 1, value.length() - 1);
            for (String pair : body.split(",")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    properties.addProperty(parts[0], parts[1]);
                }
            }
            if (properties.size() > 0) {
                state.add("Properties", properties);
            }
        }
        return state;
    }

    private void addBlockStateProperties(JsonObject recipe, RecipeRequest request) {
        if (request.properties.get("copy_properties") instanceof Boolean copyProperties) {
            recipe.addProperty("copy_properties", copyProperties);
        }
        if (request.properties.get("time") instanceof Number time) {
            recipe.addProperty("time", time.intValue());
        }
    }

    private String getIngredientString(Object ingredient) {
        if (ingredient instanceof IngredientData data) {
            if (data.getType() == IngredientData.Type.ITEM) {
                return getItemId(data.getItemStack());
            }
            return data.getTagId() == null ? "" : "#" + data.getTagId();
        }
        if (ingredient instanceof ItemStack stack) {
            return getItemId(stack);
        }
        if (ingredient instanceof Item item) {
            return getItemResourceLocation(item).toString();
        }
        if (ingredient instanceof String string) {
            return string;
        }
        return "";
    }

    private String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return getItemResourceLocation(stack.getItem()).toString();
    }

    private int numberValue(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private String stringValue(Object value, String defaultValue) {
        if (value instanceof JsonElement element && element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return value instanceof String string ? string : defaultValue;
    }

    private String ensureNamespace(String id, String defaultNamespace) {
        if (id == null || id.isBlank()) {
            return defaultNamespace + ":air";
        }
        return id.contains(":") ? id : defaultNamespace + ":" + id;
    }
}
