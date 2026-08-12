package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.gui.recipe.IngredientData;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MysticalAgricultureProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("mysticalagriculture");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{"infusion", "awakening", "reprocessor"};
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String type = request.recipeType.toLowerCase();
        if (type.contains(":")) {
            type = type.substring(type.indexOf(":") + 1);
        }

        return switch (type) {
            case "infusion" -> createInfusionRecipe(request);
            case "awakening" -> createAwakeningRecipe(request);
            case "reprocessor" -> createReprocessorRecipe(request);
            default -> null;
        };
    }

    private JsonObject createInfusionRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "mysticalagriculture:infusion");

        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject input = createIngredient(request.ingredients[0]);
            if (input != null) {
                recipe.add("input", input);
            }
        }

        JsonArray ingredients = new JsonArray();
        if (request.ingredients != null) {
            for (int i = 1; i < Math.min(9, request.ingredients.length); i++) {
                JsonObject ingredient = createIngredient(request.ingredients[i]);
                if (ingredient != null) {
                    ingredients.add(ingredient);
                }
            }
        }
        recipe.add("ingredients", ingredients);
        recipe.add("result", createResult(request.result, request.resultCount));
        addTransferComponents(recipe, request);
        return recipe;
    }

    private JsonObject createAwakeningRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "mysticalagriculture:awakening");

        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject input = createIngredient(request.ingredients[0]);
            if (input != null) {
                recipe.add("input", input);
            }
        }

        JsonArray ingredients = new JsonArray();
        if (request.ingredients != null) {
            for (int i = 1; i < Math.min(5, request.ingredients.length); i++) {
                JsonObject ingredient = createIngredient(request.ingredients[i]);
                if (ingredient != null) {
                    ingredients.add(ingredient);
                }
            }
        }
        recipe.add("ingredients", ingredients);
        recipe.add("essences", createEssences(request));
        recipe.add("result", createResult(request.result, request.resultCount));
        addTransferComponents(recipe, request);
        return recipe;
    }

    private JsonObject createReprocessorRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "mysticalagriculture:reprocessor");

        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject input = createIngredient(request.ingredients[0]);
            if (input != null) {
                recipe.add("input", input);
            }
        }
        recipe.add("result", createResult(request.result, request.resultCount));
        return recipe;
    }

    private JsonArray createEssences(RecipeRequest request) {
        String[] defaults = {
                "mysticalagriculture:air_essence",
                "mysticalagriculture:earth_essence",
                "mysticalagriculture:water_essence",
                "mysticalagriculture:fire_essence"
        };
        String[] propertyNames = {"airEssence", "earthEssence", "waterEssence", "fireEssence"};
        String[] countNames = {"airEssenceCount", "earthEssenceCount", "waterEssenceCount", "fireEssenceCount"};

        List<Object> configured = new ArrayList<>();
        Object value = request.properties.get("essences");
        if (value instanceof Object[] values) {
            configured.addAll(List.of(values));
        } else if (value instanceof JsonArray values) {
            values.forEach(configured::add);
        }

        JsonArray essences = new JsonArray();
        for (int i = 0; i < defaults.length; i++) {
            Object essence = i < configured.size() ? configured.get(i) : null;
            if (essence == null && request.ingredients != null && request.ingredients.length > i + 5) {
                essence = request.ingredients[i + 5];
            }
            if (essence == null) {
                essence = request.properties.get(propertyNames[i]);
            }

            JsonObject stack = createStack(essence);
            if (stack == null) {
                stack = new JsonObject();
                stack.addProperty("id", defaults[i]);
                stack.addProperty("count", numberValue(request.properties.get(countNames[i]), 40));
            } else if (!stack.has("count") && request.properties.containsKey(countNames[i])) {
                stack.addProperty("count", numberValue(request.properties.get(countNames[i]), 40));
            }
            essences.add(stack);
        }
        return essences;
    }

    private JsonObject createIngredient(Object ingredient) {
        return RecipeUtil.createIngredientJson(ingredient);
    }

    private JsonObject createResult(ItemStack result, int count) {
        return RecipeUtil.createResultJson(result, count);
    }

    private JsonObject createStack(Object value) {
        if (value instanceof ItemStack stack && !stack.isEmpty()) {
            return RecipeUtil.createResultJson(stack, stack.getCount());
        }
        if (value instanceof IngredientData data && data.getType() == IngredientData.Type.ITEM
                && !data.getItemStack().isEmpty()) {
            return RecipeUtil.createResultJson(data.getItemStack(), data.getItemStack().getCount());
        }
        if (value instanceof JsonObject object) {
            return createStackFromMap(object);
        }
        if (value instanceof JsonElement element && element.isJsonPrimitive()) {
            value = element.getAsString();
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            Object id = map.containsKey("id") ? map.get("id") : map.get("item");
            if (id instanceof String string) {
                object.addProperty("id", string);
            }
            if (map.get("count") instanceof Number count) {
                object.addProperty("count", count.intValue());
            }
            if (map.get("components") instanceof JsonElement components) {
                object.add("components", components.deepCopy());
            }
            return object.has("id") ? object : null;
        }
        if (value instanceof String id && !id.isBlank() && !id.startsWith("#")) {
            JsonObject object = new JsonObject();
            object.addProperty("id", id);
            return object;
        }
        return null;
    }

    private JsonObject createStackFromMap(JsonObject source) {
        JsonObject stack = source.deepCopy();
        if (!stack.has("id") && stack.has("item")) {
            stack.add("id", stack.remove("item"));
        }
        return stack.has("id") ? stack : null;
    }

    private void addTransferComponents(JsonObject recipe, RecipeRequest request) {
        Object value = request.properties.get("transfer_components");
        if (value instanceof Boolean transfer) {
            recipe.addProperty("transfer_components", transfer);
        }
    }

    private int numberValue(Object value, int defaultValue) {
        return value instanceof Number number ? Math.max(1, number.intValue()) : defaultValue;
    }
}
