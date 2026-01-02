package com.wzz.registerhelper.recipe.integration.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.recipe.integration.ModRecipeProcessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import static com.wzz.registerhelper.util.RecipeUtil.*;

/**
 * 植物魔法配方处理器
 * 修复版：兼容新旧架构，正确处理物品ID，修复输出字段
 */
public class BotaniaProcessor implements ModRecipeProcessor {

    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("botania");
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        return new String[]{
                "runic_altar",
                "mana_infusion",
                "elven_trade",
                "terra_plate",
                "petal_apothecary",
                "pure_daisy",
                "brew",
                "orechid",
                "orechid_ignem",
                "marimorphosis"
        };
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        JsonObject recipe = new JsonObject();

        String type = request.recipeType;

        // 如果不包含modid，添加botania前缀
        if (!type.contains(":")) {
            type = "botania:" + type;
        }

        recipe.addProperty("type", type);

        switch (type) {
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

    /**
     * 符文祭坛配方
     * 格式: ingredients + output + mana
     */
    private void createRunicAltarRecipe(JsonObject recipe, RecipeRequest request) {
        // ingredients数组
        JsonArray ingredients = new JsonArray();
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                ingredients.add(createIngredientJson(ingredient));
            }
        }
        recipe.add("ingredients", ingredients);

        // output字段
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);

        // mana消耗
        Integer mana = (Integer) request.properties.get("mana");
        recipe.addProperty("mana", mana != null ? mana : 5200);
    }

    /**
     * 魔力池配方
     * 格式: input + output + mana + 可选catalyst
     */
    private void createManaInfusionRecipe(JsonObject recipe, RecipeRequest request) {
        // 单个input
        if (request.ingredients != null && request.ingredients.length > 0) {
            recipe.add("input", createIngredientJson(request.ingredients[0]));
        }

        // output字段
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            output.addProperty("count", request.resultCount);
        }
        recipe.add("output", output);

        // mana消耗
        Integer mana = (Integer) request.properties.get("mana");
        recipe.addProperty("mana", mana != null ? mana : 1000);

        // 可选的催化剂
        String catalyst = (String) request.properties.get("catalyst");
        if (catalyst != null && !catalyst.isEmpty()) {
            JsonObject catalystObj = new JsonObject();
            catalystObj.addProperty("type", "block");
            catalystObj.addProperty("block", ensureNamespace(catalyst));
            recipe.add("catalyst", catalystObj);
        }
    }

    /**
     * 精灵贸易配方
     * 格式: ingredients + output (数组)
     */
    private void createElvenTradeRecipe(JsonObject recipe, RecipeRequest request) {
        // ingredients数组
        JsonArray ingredients = new JsonArray();
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                ingredients.add(createIngredientJson(ingredient));
            }
        }
        recipe.add("ingredients", ingredients);

        // output是数组
        JsonArray outputs = new JsonArray();

        // 主要输出
        JsonObject mainOutput = new JsonObject();
        mainOutput.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            mainOutput.addProperty("count", request.resultCount);
        }
        outputs.add(mainOutput);

        // 额外输出
        Object extraOutputsObj = request.properties.get("extraOutputs");
        if (extraOutputsObj instanceof Object[] extraOutputs) {
            for (Object extra : extraOutputs) {
                JsonObject extraOutput = new JsonObject();
                if (extra instanceof ItemStack stack) {
                    extraOutput.addProperty("item", getItemId(stack));
                    if (stack.getCount() > 1) {
                        extraOutput.addProperty("count", stack.getCount());
                    }
                } else if (extra instanceof String str) {
                    extraOutput.addProperty("item", ensureNamespace(str));
                }
                outputs.add(extraOutput);
            }
        }

        recipe.add("output", outputs);
    }

    /**
     * 泰拉凝聚板配方
     * 格式: ingredients + result (不是output!) + mana
     */
    private void createTerraPlateRecipe(JsonObject recipe, RecipeRequest request) {
        // ingredients数组
        JsonArray ingredients = new JsonArray();
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                ingredients.add(createIngredientJson(ingredient));
            }
        }
        recipe.add("ingredients", ingredients);

        // 注意：这里用result而不是output！
        JsonObject result = new JsonObject();
        result.addProperty("item", getItemId(request.result));
        if (request.resultCount > 1) {
            result.addProperty("count", request.resultCount);
        }
        recipe.add("result", result);

        // mana消耗
        Integer mana = (Integer) request.properties.get("mana");
        recipe.addProperty("mana", mana != null ? mana : 500000);
    }

    /**
     * 花瓣药台配方
     * 格式: ingredients + output + reagent
     */
    private void createPetalApothecaryRecipe(JsonObject recipe, RecipeRequest request) {
        // ingredients数组
        JsonArray ingredients = new JsonArray();
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                ingredients.add(createIngredientJson(ingredient));
            }
        }
        recipe.add("ingredients", ingredients);

        // output字段
        JsonObject output = new JsonObject();
        output.addProperty("item", getItemId(request.result));
        recipe.add("output", output);

        // reagent字段（试剂）
        String reagent = (String) request.properties.get("reagent");
        JsonObject reagentObj = new JsonObject();
        if (reagent != null && !reagent.isEmpty()) {
            if (reagent.startsWith("#")) {
                reagentObj.addProperty("tag", reagent.substring(1));
            } else {
                reagentObj.addProperty("item", ensureNamespace(reagent));
            }
        } else {
            // 默认试剂
            reagentObj.addProperty("tag", "botania:seed_apothecary_reagent");
        }
        recipe.add("reagent", reagentObj);
    }

    /**
     * 纯洁雏菊配方
     * 格式: input (block) + output (name字段)
     */
    private void createPureDaisyRecipe(JsonObject recipe, RecipeRequest request) {
        // input必须是方块
        JsonObject input = new JsonObject();
        if (request.ingredients != null && request.ingredients.length > 0) {
            String inputStr = getIngredientString(request.ingredients[0]);
            if (inputStr.startsWith("#")) {
                input.addProperty("type", "tag");
                input.addProperty("tag", inputStr.substring(1));
            } else {
                input.addProperty("type", "block");
                input.addProperty("block", ensureNamespace(inputStr));
            }
        }
        recipe.add("input", input);

        // output使用name字段
        JsonObject output = new JsonObject();
        output.addProperty("name", getItemId(request.result));
        recipe.add("output", output);
    }

    /**
     * 酿造配方
     * 格式: ingredients + brew (效果ID)
     */
    private void createBrewRecipe(JsonObject recipe, RecipeRequest request) {
        // ingredients数组
        JsonArray ingredients = new JsonArray();
        if (request.ingredients != null) {
            for (Object ingredient : request.ingredients) {
                if (ingredient != null) {
                    String str = getIngredientString(ingredient);
                    if (str != null && !str.isEmpty()) {
                        ingredients.add(createIngredientJson(ingredient));
                    }
                }
            }
        }
        recipe.add("ingredients", ingredients);

        // brew字段（酿造效果，不是输出物品）
        String brew = (String) request.properties.get("brew");
        if (brew != null && !brew.isEmpty()) {
            recipe.addProperty("brew", ensureNamespace(brew));
        } else {
            // 默认效果
            recipe.addProperty("brew", "botania:speed");
        }
    }

    /**
     * 矿石兰配方
     * 格式: input (block) + output (block) + weight
     */
    private void createOrechidRecipe(JsonObject recipe, RecipeRequest request) {
        // input方块
        JsonObject input = new JsonObject();
        input.addProperty("type", "block");
        if (request.ingredients != null && request.ingredients.length > 0) {
            input.addProperty("block", ensureNamespace(getIngredientString(request.ingredients[0])));
        } else {
            input.addProperty("block", "minecraft:stone");
        }
        recipe.add("input", input);

        // output方块
        JsonObject output = new JsonObject();
        output.addProperty("type", "block");
        output.addProperty("block", getItemId(request.result));
        recipe.add("output", output);

        // weight权重
        Integer weight = (Integer) request.properties.get("weight");
        recipe.addProperty("weight", weight != null ? weight : 67415);
    }

    /**
     * 下界矿石兰配方
     * 格式: input (block) + output (block) + weight
     */
    private void createOrechidIgnemRecipe(JsonObject recipe, RecipeRequest request) {
        // input方块
        JsonObject input = new JsonObject();
        input.addProperty("type", "block");
        if (request.ingredients != null && request.ingredients.length > 0) {
            input.addProperty("block", ensureNamespace(getIngredientString(request.ingredients[0])));
        } else {
            input.addProperty("block", "minecraft:netherrack");
        }
        recipe.add("input", input);

        // output方块
        JsonObject output = new JsonObject();
        output.addProperty("type", "block");
        output.addProperty("block", getItemId(request.result));
        recipe.add("output", output);

        // weight权重
        Integer weight = (Integer) request.properties.get("weight");
        recipe.addProperty("weight", weight != null ? weight : 148);
    }

    /**
     * 石之变换配方
     * 格式: input (block/tag) + output (block) + weight + biome_bonus + biome_bonus_tag
     */
    private void createMarimorphosisRecipe(JsonObject recipe, RecipeRequest request) {
        // input支持tag或block
        JsonObject input = new JsonObject();
        if (request.ingredients != null && request.ingredients.length > 0) {
            String inputStr = getIngredientString(request.ingredients[0]);
            if (inputStr.startsWith("#")) {
                input.addProperty("type", "tag");
                input.addProperty("tag", inputStr.substring(1));
            } else {
                input.addProperty("type", "block");
                input.addProperty("block", ensureNamespace(inputStr));
            }
        }
        recipe.add("input", input);

        // output方块
        JsonObject output = new JsonObject();
        output.addProperty("type", "block");
        output.addProperty("block", getItemId(request.result));
        recipe.add("output", output);

        // 必须字段
        Integer weight = (Integer) request.properties.get("weight");
        recipe.addProperty("weight", weight != null ? weight : 1);

        Integer biomeBonus = (Integer) request.properties.get("biome_bonus");
        recipe.addProperty("biome_bonus", biomeBonus != null ? biomeBonus : 11);

        String biomeBonusTag = (String) request.properties.get("biome_bonus_tag");
        recipe.addProperty("biome_bonus_tag",
                biomeBonusTag != null ? biomeBonusTag : "botania:marimorphosis_desert_bonus");
    }

    /**
     * 获取物品ID（从ItemStack）
     */
    private String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        return getItemResourceLocation(stack.getItem()).toString();
    }

    /**
     * 获取材料的字符串表示
     */
    private String getIngredientString(Object ingredient) {
        if (ingredient instanceof ItemStack stack) {
            return getItemId(stack);
        } else if (ingredient instanceof Item item) {
            return getItemResourceLocation(item).toString();
        } else if (ingredient instanceof String str) {
            return str;
        }
        return "minecraft:air";
    }

    /**
     * 确保ID包含命名空间
     */
    private String ensureNamespace(String id) {
        if (id == null || id.isEmpty()) {
            return "minecraft:air";
        }
        if (id.startsWith("#")) {
            return id;  // tag保持原样
        }
        if (!id.contains(":")) {
            return "minecraft:" + id;
        }
        return id;
    }
}