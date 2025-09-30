package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;

public class BotaniaProcessor implements ModRecipeProcessor {
    @Override
    public boolean isModLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("botania");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
                "botania:runic_altar",
                "botania:mana_infusion",
                "botania:elven_trade",
                "botania:terra_plate",
                "botania:petal_apothecary",
                "botania:pure_daisy",
                "botania:brew",
                "botania:orechid",
                "botania:orechid_ignem",
                "botania:marimorphosis"
        };
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", request.recipeType);

        switch (request.recipeType) {
            case "botania:runic_altar" -> createRunicAltarRecipe(recipe, request);
            case "botania:mana_infusion" -> createManaInfusionRecipe(recipe, request);
            case "botania:elven_trade" -> createElvenTradeRecipe(recipe, request);
            case "botania:terra_plate" -> createTerraPlateRecipe(recipe, request);
            case "botania:petal_apothecary" -> createPetalApothecaryRecipe(recipe, request);
            case "botania:pure_daisy" -> createPureDaisyRecipe(recipe, request);
            case "botania:brew" -> createBrewRecipe(recipe, request);
            case "botania:orechid" -> createOrechidRecipe(recipe, request);
            case "botania:orechid_ignem" -> createOrechidIgnemRecipe(recipe, request);
            case "botania:marimorphosis" -> createMarimorphosisRecipe(recipe, request);
        }

        return recipe;
    }

    private void createRunicAltarRecipe(JsonObject recipe, RecipeRequest request) {
        // 符文祭坛：使用 ingredients（官方格式）
        JsonArray ingredients = new JsonArray();
        for (Object ingredient : request.ingredients) {
            JsonObject input = createIngredientObject(ingredient);
            ingredients.add(input);
        }
        recipe.add("ingredients", ingredients);

        JsonObject result = new JsonObject();
        result.addProperty("item", ensureValidItemId(request.result.getItem().toString()));
        if (request.resultCount > 1) {
            result.addProperty("count", request.resultCount);
        }
        recipe.add("output", result);

        Integer mana = (Integer) request.properties.get("mana");
        recipe.addProperty("mana", mana != null ? mana : 5200);
    }

    private void createManaInfusionRecipe(JsonObject recipe, RecipeRequest request) {
        // 魔力灌注：单材料 + 魔力 -> 输出
        JsonObject input = createIngredientObject(request.ingredients[0]);
        recipe.add("input", input);

        JsonObject result = new JsonObject();
        result.addProperty("item", ensureValidItemId(request.result.getItem().toString()));
        if (request.resultCount > 1) {
            result.addProperty("count", request.resultCount);
        }
        recipe.add("output", result);

        Integer mana = (Integer) request.properties.get("mana");
        recipe.addProperty("mana", mana != null ? mana : 1000);

        Object catalyst = request.properties.get("catalyst");
        if (catalyst != null) {
            JsonObject catalystObj = new JsonObject();
            catalystObj.addProperty("type", "block");
            catalystObj.addProperty("block", ensureValidItemId(catalyst.toString()));
            recipe.add("catalyst", catalystObj);
        }
    }

    private void createElvenTradeRecipe(JsonObject recipe, RecipeRequest request) {
        // 精灵贸易：使用 ingredients 和 output（单数）
        JsonArray ingredients = new JsonArray();
        for (Object ingredient : request.ingredients) {
            JsonObject input = createIngredientObject(ingredient);
            ingredients.add(input);
        }
        recipe.add("ingredients", ingredients);

        JsonArray outputs = new JsonArray();
        JsonObject mainOutput = new JsonObject();
        mainOutput.addProperty("item", ensureValidItemId(request.result.getItem().toString()));
        if (request.resultCount > 1) {
            mainOutput.addProperty("count", request.resultCount);
        }
        outputs.add(mainOutput);

        // 额外输出
        Object[] extraOutputs = (Object[]) request.properties.get("extraOutputs");
        if (extraOutputs != null) {
            for (Object extra : extraOutputs) {
                JsonObject extraOutput = new JsonObject();
                extraOutput.addProperty("item", ensureValidItemId(extra.toString()));
                outputs.add(extraOutput);
            }
        }

        recipe.add("output", outputs);
    }

    private void createTerraPlateRecipe(JsonObject recipe, RecipeRequest request) {
        // 泰拉凝聚板：使用 ingredients 和 result
        JsonArray ingredients = new JsonArray();
        for (Object ingredient : request.ingredients) {
            JsonObject input = createIngredientObject(ingredient);
            ingredients.add(input);
        }
        recipe.add("ingredients", ingredients);

        JsonObject result = new JsonObject();
        result.addProperty("item", ensureValidItemId(request.result.getItem().toString()));
        if (request.resultCount > 1) {
            result.addProperty("count", request.resultCount);
        }
        recipe.add("result", result);

        Integer mana = (Integer) request.properties.get("mana");
        recipe.addProperty("mana", mana != null ? mana : 500000);
    }

    private void createPetalApothecaryRecipe(JsonObject recipe, RecipeRequest request) {
        // 花瓣药剂师：使用 ingredients
        JsonArray ingredients = new JsonArray();
        for (Object ingredient : request.ingredients) {
            JsonObject input = createIngredientObject(ingredient);
            ingredients.add(input);
        }
        recipe.add("ingredients", ingredients);

        JsonObject result = new JsonObject();
        result.addProperty("item", ensureValidItemId(request.result.getItem().toString()));
        recipe.add("output", result);

        // 必须的试剂字段
        String reagent = (String) request.properties.get("reagent");
        if (reagent != null) {
            JsonObject reagentObj = createIngredientObject(reagent);
            recipe.add("reagent", reagentObj);
        } else {
            // 默认试剂
            JsonObject reagentObj = new JsonObject();
            reagentObj.addProperty("tag", "botania:seed_apothecary_reagent");
            recipe.add("reagent", reagentObj);
        }
    }

    private void createPureDaisyRecipe(JsonObject recipe, RecipeRequest request) {
        // 纯洁雏菊：方块转换，输出使用 name，只能用方块
        JsonObject input = new JsonObject();
        input.addProperty("type", "block");
        input.addProperty("block", ensureValidItemId(request.ingredients[0].toString()));
        recipe.add("input", input);

        JsonObject result = new JsonObject();
        result.addProperty("name", ensureValidItemId(request.result.getItem().toString()));
        recipe.add("output", result);
    }

    private void createBrewRecipe(JsonObject recipe, RecipeRequest request) {
        // 酿造：ingredients + brew字段
        JsonArray ingredients = new JsonArray();
        for (Object ingredient : request.ingredients) {
            if (ingredient != null && !ingredient.toString().isEmpty()) {
                JsonObject input = createIngredientObject(ingredient);
                ingredients.add(input);
            }
        }
        recipe.add("ingredients", ingredients);

        // 必须的brew字段 - 这是酿造效果，不是输出物品
        String brew = (String) request.properties.get("brew");
        if (brew != null) {
            recipe.addProperty("brew", ensureValidItemId(brew));
        } else {
            // 默认酿造效果或从result推导
            String brewId = "botania:speed"; // 默认
            if (request.result != null) {
                // 可以从结果物品推导酿造效果
                brewId = convertItemToBrew(request.result.getItem().toString());
            }
            recipe.addProperty("brew", brewId);
        }

    }

    /**
     * 将物品转换为对应的酿造效果
     */
    private String convertItemToBrew(String itemId) {
        // 这里可以建立物品到酿造效果的映射
        return switch (itemId) {
            case "minecraft:golden_apple" -> "botania:absorption";
            case "minecraft:magma_cream" -> "botania:fire_resistance";
            default -> "botania:speed";
        };
    }

    private void createOrechidRecipe(JsonObject recipe, RecipeRequest request) {
        // 矿石兰：石头 + 魔力 -> 矿石
        JsonObject input = new JsonObject();
        input.addProperty("type", "block");
        input.addProperty("block", ensureValidItemId(request.ingredients[0].toString()));
        recipe.add("input", input);

        JsonObject result = new JsonObject();
        result.addProperty("type", "block");
        result.addProperty("block", ensureValidItemId(request.result.getItem().toString()));
        recipe.add("output", result);

        Integer weight = (Integer) request.properties.get("weight");
        recipe.addProperty("weight", weight != null ? weight : 67415); // 必须有weight
    }

    private void createOrechidIgnemRecipe(JsonObject recipe, RecipeRequest request) {
        // 下界矿石兰：下界岩 + 魔力 -> 下界矿石
        JsonObject input = new JsonObject();
        input.addProperty("type", "block");
        input.addProperty("block", ensureValidItemId(request.ingredients[0].toString()));
        recipe.add("input", input);

        JsonObject result = new JsonObject();
        result.addProperty("type", "block");
        result.addProperty("block", ensureValidItemId(request.result.getItem().toString()));
        recipe.add("output", result);

        Integer weight = (Integer) request.properties.get("weight");
        recipe.addProperty("weight", weight != null ? weight : 148); // 必须有weight
    }

    private void createMarimorphosisRecipe(JsonObject recipe, RecipeRequest request) {
        // 石之变换：石头类型转换，只能用方块
        JsonObject input = new JsonObject();

        // 检查是否是tag
        String inputStr = request.ingredients[0].toString();
        if (inputStr.startsWith("#")) {
            input.addProperty("type", "tag");
            input.addProperty("tag", inputStr.substring(1));
        } else {
            input.addProperty("type", "block");
            input.addProperty("block", ensureValidItemId(inputStr));
        }
        recipe.add("input", input);

        JsonObject result = new JsonObject();
        result.addProperty("type", "block");
        result.addProperty("block", ensureValidItemId(request.result.getItem().toString()));
        recipe.add("output", result);

        // 必须字段
        Integer weight = (Integer) request.properties.get("weight");
        recipe.addProperty("weight", weight != null ? weight : 1);

        Integer biomeBonus = (Integer) request.properties.get("biome_bonus");
        recipe.addProperty("biome_bonus", biomeBonus != null ? biomeBonus : 11);

        String biomeBonusTag = (String) request.properties.get("biome_bonus_tag");
        recipe.addProperty("biome_bonus_tag", biomeBonusTag != null ? biomeBonusTag : "botania:marimorphosis_desert_bonus");
    }

    /**
     * 创建材料对象，支持tag和item
     */
    private JsonObject createIngredientObject(Object ingredient) {
        JsonObject obj = new JsonObject();
        String str = ingredient.toString();

        if (str.startsWith("#")) {
            // Tag格式
            obj.addProperty("tag", str.substring(1));
        } else {
            // Item格式
            obj.addProperty("item", ensureValidItemId(str));
        }

        return obj;
    }

    /**
     * 确保物品ID包含命名空间
     */
    private String ensureValidItemId(String itemId) {
        if (itemId == null) {
            return "minecraft:air";
        }

        if (itemId.contains(":")) {
            return itemId;
        }

        return "minecraft:" + itemId;
    }
}