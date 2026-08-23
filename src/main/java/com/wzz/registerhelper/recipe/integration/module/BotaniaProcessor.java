package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wzz.registerhelper.gui.recipe.IngredientData;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

import static com.wzz.registerhelper.util.RecipeUtil.createIngredientJson;
import static com.wzz.registerhelper.util.RecipeUtil.getItemResourceLocation;

/** JSON adapter for Botania's machine-style recipe serializers. */
public class BotaniaProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("botania");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
                "runic_altar", "runic_altar_head", "mana_infusion", "elven_trade", "terra_plate",
                "petal_apothecary", "pure_daisy", "state_copying_pure_daisy", "brew", "orechid",
                "orechid_ignem", "marimorphosis"
        };
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String type = normalizeType(request.recipeType);
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "botania:" + type);
        switch (type) {
            case "runic_altar", "runic_altar_head" -> createRunicAltarRecipe(recipe, request);
            case "mana_infusion" -> createManaInfusionRecipe(recipe, request);
            case "elven_trade" -> createElvenTradeRecipe(recipe, request);
            case "terra_plate" -> createTerraPlateRecipe(recipe, request);
            case "petal_apothecary" -> createPetalApothecaryRecipe(recipe, request);
            case "pure_daisy" -> createPureDaisyRecipe(recipe, request);
            case "state_copying_pure_daisy" -> createStateCopyingPureDaisyRecipe(recipe, request);
            case "brew" -> createBrewRecipe(recipe, request);
            case "orechid", "orechid_ignem" -> createOrechidRecipe(recipe, request);
            case "marimorphosis" -> createMarimorphosisRecipe(recipe, request);
            default -> {
                return null;
            }
        }
        return recipe;
    }

    private void createRunicAltarRecipe(JsonObject recipe, RecipeRequest request) {
        recipe.add("ingredients", rawIngredients(request, ingredientArray(inputValues(request))));
        recipe.add("output", itemResult(request.result, request.resultCount));
        recipe.addProperty("mana", number(request.properties.get("mana"), 5200));
    }

    private void createManaInfusionRecipe(JsonObject recipe, RecipeRequest request) {
        Object input = firstValue(request, "INPUT");
        if (request.properties.get("rawInput") instanceof com.google.gson.JsonElement rawInput) {
            recipe.add("input", rawInput.deepCopy());
        } else if (input != null) {
            recipe.add("input", createIngredientJson(input));
        }
        recipe.add("output", itemResult(request.result, request.resultCount));
        recipe.addProperty("mana", number(request.properties.get("mana"), 1000));

        Object catalyst = firstValue(request, "CATALYST");
        if (catalyst == null) catalyst = request.properties.get("catalyst");
        if (catalyst != null && !String.valueOf(catalyst).isBlank()) {
            recipe.add("catalyst", stateIngredient(catalyst));
        }
    }

    private void createElvenTradeRecipe(JsonObject recipe, RecipeRequest request) {
        recipe.add("ingredients", rawIngredients(request, ingredientArray(inputValues(request))));
        JsonArray outputs = new JsonArray();
        if (request.result != null && !request.result.isEmpty()) {
            outputs.add(itemResult(request.result, request.resultCount));
        }
        Object extras = request.properties.get("extraOutputs");
        if (extras instanceof ItemStack[] stacks) {
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) outputs.add(itemResult(stack, stack.getCount()));
            }
        } else if (extras instanceof Iterable<?> values) {
            for (Object value : values) {
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    outputs.add(itemResult(stack, stack.getCount()));
                }
            }
        }
        recipe.add("output", outputs);
    }

    private void createTerraPlateRecipe(JsonObject recipe, RecipeRequest request) {
        recipe.add("ingredients", rawIngredients(request, ingredientArray(inputValues(request))));
        recipe.add("result", itemResult(request.result, request.resultCount));
        recipe.addProperty("mana", number(request.properties.get("mana"), 500000));
    }

    private void createPetalApothecaryRecipe(JsonObject recipe, RecipeRequest request) {
        recipe.add("ingredients", rawIngredients(request, ingredientArray(inputValues(request))));
        recipe.add("output", itemResult(request.result, request.resultCount));
        Object reagent = firstValue(request, "REAGENT");
        if (reagent == null) reagent = request.properties.get("reagent");
        recipe.add("reagent", reagent == null
                ? tagIngredient("botania:seed_apothecary_reagent")
                : ingredientJson(reagent));
    }

    private void createPureDaisyRecipe(JsonObject recipe, RecipeRequest request) {
        Object input = firstValue(request, "INPUT");
        if (input != null) recipe.add("input", stateIngredient(input));
        recipe.add("output", blockStateResult(request.result));
        Object time = request.properties.get("time");
        if (time instanceof Number) recipe.addProperty("time", ((Number) time).intValue());
        addSuccessFunction(recipe, request);
    }

    private void createStateCopyingPureDaisyRecipe(JsonObject recipe, RecipeRequest request) {
        Object input = firstValue(request, "INPUT");
        if (input != null) recipe.add("input", stateIngredient(input));
        ItemStack output = request.result;
        if (output == null || output.isEmpty() || !(output.getItem() instanceof BlockItem blockItem)) {
            throw new IllegalArgumentException("State-copying Pure Daisy requires a block output");
        }
        ResourceLocation id = getBlockId(blockItem.getBlock());
        if (id == null) throw new IllegalArgumentException("Unregistered Pure Daisy output block");
        recipe.addProperty("output", id.toString());
        Object time = request.properties.get("time");
        if (time instanceof Number) recipe.addProperty("time", ((Number) time).intValue());
    }

    private void createBrewRecipe(JsonObject recipe, RecipeRequest request) {
        recipe.add("ingredients", ingredientArray(inputValues(request)));
        Object brew = request.properties.get("brew");
        String brewId = ensureNamespace(brew == null ? "botania:speed" : String.valueOf(brew));
        if (brewId.startsWith("#")) {
            throw new IllegalArgumentException("Botania brew must use a concrete brew id");
        }
        recipe.addProperty("brew", brewId);
    }

    private void createOrechidRecipe(JsonObject recipe, RecipeRequest request) {
        Object input = firstValue(request, "INPUT");
        if (input != null) recipe.add("input", stateIngredient(input));
        recipe.add("output", stateIngredient(request.result));
        String type = normalizeType(request.recipeType);
        int defaultWeight = "orechid_ignem".equals(type) ? 148
                : "marimorphosis".equals(type) ? 1 : 67415;
        Object configuredWeight = request.properties.get("weight");
        if (configuredWeight instanceof Number number &&
                ("orechid".equals(type) || number.intValue() != 67415)) {
            defaultWeight = number.intValue();
        }
        recipe.addProperty("weight", defaultWeight);
        addSuccessFunction(recipe, request);
    }

    private void createMarimorphosisRecipe(JsonObject recipe, RecipeRequest request) {
        Object input = firstValue(request, "INPUT");
        if (input != null) recipe.add("input", stateIngredient(input));
        recipe.add("output", stateIngredient(request.result));
        recipe.addProperty("weight", number(request.properties.get("weight"), 1));
        recipe.addProperty("biome_bonus", number(request.properties.get("biome_bonus"), 11));
        recipe.addProperty("biome_bonus_tag", ensureNamespace(String.valueOf(
                request.properties.getOrDefault("biome_bonus_tag", "botania:marimorphosis_desert_bonus"))));
        addSuccessFunction(recipe, request);
    }

    private JsonArray ingredientArray(List<Object> values) {
        JsonArray array = new JsonArray();
        for (Object value : values) {
            JsonObject json = ingredientJson(value);
            if (json != null) array.add(json);
        }
        return array;
    }

    private JsonElement rawIngredients(RecipeRequest request, JsonArray fallback) {
        Object raw = request.properties.get("rawIngredients");
        return raw instanceof JsonElement element ? element.deepCopy() : fallback;
    }

    private JsonObject ingredientJson(Object value) {
        if (value instanceof IngredientData data
                && (data.getType() != IngredientData.Type.ITEM || data.getItemStack().isEmpty())) {
            return createIngredientJson(data);
        }
        return createIngredientJson(value);
    }

    private JsonObject stateIngredient(Object value) {
        if (value instanceof IngredientData data) {
            if (data.getType() == IngredientData.Type.TAG
                    || data.getType() == IngredientData.Type.CUSTOM_TAG) {
                if (data.getType() == IngredientData.Type.CUSTOM_TAG) {
                    throw new IllegalArgumentException("Custom item tags cannot be used as Botania block tags");
                }
                ResourceLocation tagId = data.getTagId();
                if (tagId == null || BuiltInRegistries.BLOCK.getTag(
                        TagKey.create(Registries.BLOCK, tagId)).isEmpty()) {
                    throw new IllegalArgumentException("Unknown Botania block tag: " + tagId);
                }
                return tagStateIngredient(tagId.toString());
            }
            value = data.getItemStack();
        }

        if (value instanceof ItemStack stack) {
            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                throw new IllegalArgumentException("Botania state ingredient must be a block: "
                        + getItemId(stack));
            }
            value = blockItem.getBlock();
        }

        if (value instanceof net.minecraft.world.level.block.Block block) {
            ResourceLocation id = getBlockId(block);
            if (id == null) throw new IllegalArgumentException("Unregistered Botania block ingredient");
            JsonObject state = new JsonObject();
            state.addProperty("type", "block");
            state.addProperty("block", id.toString());
            return state;
        }

        String id = getIngredientString(value);
        if (id.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(id.substring(1));
            if (tagId == null || BuiltInRegistries.BLOCK.getTag(
                    TagKey.create(Registries.BLOCK, tagId)).isEmpty()) {
                throw new IllegalArgumentException("Unknown Botania block tag: " + id);
            }
            return tagStateIngredient(tagId.toString());
        }
        JsonObject state = new JsonObject();
        state.addProperty("type", "block");
        String blockId = ensureNamespace(id);
        ResourceLocation blockLocation = ResourceLocation.tryParse(blockId);
        if (blockLocation == null || BuiltInRegistries.BLOCK.get(blockLocation)
                == net.minecraft.world.level.block.Blocks.AIR) {
            throw new IllegalArgumentException("Unknown Botania block: " + id);
        }
        state.addProperty("block", blockId);
        return state;
    }

    private ResourceLocation getBlockId(net.minecraft.world.level.block.Block block) {
        return net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
    }

    private JsonObject tagStateIngredient(String id) {
        JsonObject state = new JsonObject();
        state.addProperty("type", "tag");
        state.addProperty("tag", id);
        return state;
    }

    private JsonObject blockStateResult(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            throw new IllegalArgumentException("Botania block conversion requires a block output");
        }
        ResourceLocation id = getBlockId(blockItem.getBlock());
        if (id == null) throw new IllegalArgumentException("Unregistered Botania block output");
        JsonObject result = new JsonObject();
        result.addProperty("name", id.toString());
        return result;
    }

    private JsonObject tagIngredient(String id) {
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("tag", id);
        return ingredient;
    }

    private JsonObject itemResult(ItemStack stack, int count) {
        JsonObject result = new JsonObject();
        result.addProperty("item", getItemId(stack));
        if (count > 1) result.addProperty("count", count);
        if (stack != null && stack.hasTag()) {
            try {
                result.add("nbt", JsonParser.parseString(stack.getTag().toString()));
            } catch (Exception ignored) {
                result.addProperty("nbt", stack.getTag().toString());
            }
        }
        return result;
    }

    private List<Object> inputValues(RecipeRequest request) {
        List<Object> values = valuesForRole(request, "INPUT");
        if (values.isEmpty() && roleList(request).isEmpty() && request.ingredients != null) {
            for (Object value : request.ingredients) if (value != null) values.add(value);
        }
        return values;
    }

    private Object firstValue(RecipeRequest request, String role) {
        List<Object> values = valuesForRole(request, role);
        return values.isEmpty() ? null : values.get(0);
    }

    private List<Object> valuesForRole(RecipeRequest request, String role) {
        List<Object> values = new ArrayList<>();
        List<?> roles = roleList(request);
        if (request.ingredients == null) return values;
        for (int i = 0; i < request.ingredients.length; i++) {
            String actualRole = roles.size() > i ? String.valueOf(roles.get(i)) : "INPUT";
            if (role.equals(actualRole)) values.add(request.ingredients[i]);
        }
        return values;
    }

    private List<?> roleList(RecipeRequest request) {
        Object roles = request.properties.get("slotRoles");
        return roles instanceof List<?> list ? list : List.of();
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private void addSuccessFunction(JsonObject recipe, RecipeRequest request) {
        Object function = request.properties.get("success_function");
        if (function != null && !String.valueOf(function).isBlank()) {
            recipe.addProperty("success_function", String.valueOf(function));
        }
    }

    private String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        return getItemResourceLocation(stack.getItem()).toString();
    }

    private String getIngredientString(Object value) {
        if (value instanceof ItemStack stack) return getItemId(stack);
        if (value instanceof Item item) return getItemResourceLocation(item).toString();
        if (value instanceof IngredientData data) return getIngredientString(data.getItemStack());
        if (value instanceof String string) return string;
        return "minecraft:air";
    }

    private String ensureNamespace(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Botania identifier cannot be empty");
        }
        String normalized = id.startsWith("#") ? id.substring(1) : id;
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        if (ResourceLocation.tryParse(normalized) == null) {
            throw new IllegalArgumentException("Invalid Botania identifier: " + id);
        }
        return id.startsWith("#") ? "#" + normalized : normalized;
    }

    private String normalizeType(String type) {
        if (type == null) return "";
        int separator = type.indexOf(':');
        return (separator >= 0 ? type.substring(separator + 1) : type).toLowerCase();
    }
}
