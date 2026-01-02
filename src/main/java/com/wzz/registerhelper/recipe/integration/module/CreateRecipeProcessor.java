package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import com.wzz.registerhelper.util.RecipeUtil;

import java.util.Map;

import static com.wzz.registerhelper.util.RecipeUtil.createIngredientJson;

public class CreateRecipeProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("create");
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        String type = request.recipeType.toLowerCase();
        if (type.contains(":")) {
            type = type.substring(type.indexOf(":") + 1);
        }
        return switch (type) {
            case "create:emptying" -> createEmptyingRecipe(request);
            case "create:cutting" -> createCuttingRecipe(request);
            case "create:compacting" -> createCompactingRecipe(request);
            case "create:pressing" -> createPressingRecipe(request);
            case "create:filling" -> createFillingRecipe(request);
            case "create:mixing" -> creatMixingRecipe(request);
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
        recipe.add("ingredients", buildIngredients(request.ingredients, false));
        recipe.add("results", buildResults(request));
        return recipe;
    }

    private JsonObject createCuttingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:cutting");
        recipe.add("ingredients", buildIngredients(request.ingredients, false));
        recipe.add("results", buildResults(request));
        recipe.addProperty("processingTime", (int) request.properties.getOrDefault("processingTime", 200));
        return recipe;
    }

    private JsonObject createCompactingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:compacting");
        recipe.add("ingredients", buildIngredients(request.ingredients, true));
        recipe.add("results", buildResults(request));
        return recipe;
    }

    private JsonObject creatMixingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:mixing");
        recipe.add("ingredients", buildIngredients(request.ingredients, true));
        recipe.add("results", buildResults(request));
        return recipe;
    }

    private JsonObject createPressingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:pressing");
        recipe.add("ingredients", buildIngredients(request.ingredients, false));
        recipe.add("results", buildResults(request));
        return recipe;
    }

    private JsonObject createFillingRecipe(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "create:filling");

        JsonArray ingredientsArray = new JsonArray();
        boolean hasFluid = false;

        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                JsonObject obj = createIngredientJson(ingredient); // 处理物品 + NBT
                if (obj != null) {
                    ingredientsArray.add(obj);
                    if (obj.has("fluid")) hasFluid = true;
                }
            }
        }

        // 如果没有流体输入，给一个默认流体（可以是水，也可以从 properties 读取）
        if (!hasFluid) {
            JsonObject defaultFluid = new JsonObject();
            defaultFluid.addProperty("fluid", "minecraft:water"); // 默认流体
            defaultFluid.addProperty("amount", 250);
            defaultFluid.add("nbt", new JsonObject());
            ingredientsArray.add(defaultFluid);
        }

        recipe.add("ingredients", ingredientsArray);

        // 输出结果
        recipe.add("results", buildResults(request));

        return recipe;
    }

    @SuppressWarnings("unchecked")
    private JsonArray buildIngredients(Object[] ingredients, boolean allowFluid) {
        JsonArray array = new JsonArray();
        if (ingredients == null) return array;
        for (Object ingredient : ingredients) {
            JsonObject obj;
            // 物品 / tag / NBT
            obj = RecipeUtil.createIngredientJson(ingredient);
            if (allowFluid && ingredient instanceof Map map && map.containsKey("fluid")) {
                obj = new JsonObject();
                String fluidId = (String) map.get("fluid");
                int amount = (int) map.getOrDefault("amount", 100);
                obj.addProperty("fluid", fluidId);
                obj.addProperty("amount", amount);
                if (map.containsKey("nbt") && map.get("nbt") instanceof JsonElement json) {
                    obj.add("nbt", json);
                } else {
                    obj.add("nbt", new JsonObject());
                }
            }
            if (obj != null) array.add(obj);
        }
        return array;
    }

    private JsonArray buildResults(RecipeRequest request) {
        JsonArray array = new JsonArray();

        if (request.result != null && !request.result.isEmpty()) {
            JsonObject resultItem = new JsonObject();
            resultItem.addProperty("item", RecipeUtil.getItemResourceLocation(request.result.getItem()).toString());
            if (request.resultCount > 1) resultItem.addProperty("count", request.resultCount);
            array.add(resultItem);
        }

        if (request.properties.containsKey("fluidOutput")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fluidOutput = (Map<String, Object>) request.properties.get("fluidOutput");
            JsonObject fluidResult = new JsonObject();
            fluidResult.addProperty("fluid", (String) fluidOutput.get("fluid"));
            fluidResult.addProperty("amount", (Integer) fluidOutput.getOrDefault("amount", 250));
            fluidResult.add("nbt", new JsonObject());
            array.add(fluidResult);
        }

        return array;
    }
}