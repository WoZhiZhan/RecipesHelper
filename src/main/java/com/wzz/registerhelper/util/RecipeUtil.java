package com.wzz.registerhelper.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.gui.recipe.IngredientData;
import com.wzz.registerhelper.recipe.RecipeRequest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipeUtil {
    // 优先级：大写字母 → 小写字母 → 特殊符号
    public static final String SYMBOL_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +           // 大写字母 (26个)
                    "abcdefghijklmnopqrstuvwxyz" +           // 小写字母 (26个)
                    "!@#$%^&*()_{}[];:'/.,`";                // 特殊符号 (26个，总共78个)

    public static JsonObject createShapedTableRecipe(String type, RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        Integer tier = (Integer) request.properties.get("tier");
        if (tier != null) {
            recipe.addProperty("tier", tier);
        }

        if (request.pattern != null) {
            JsonArray patternArray = new JsonArray();
            for (String row : request.pattern) {
                patternArray.add(row);
            }
            recipe.add("pattern", patternArray);
        }

        if (request.ingredients != null) {
            JsonObject key = new JsonObject();
            Map<Character, JsonObject> keyMapping = new HashMap<>();
            Map<String, Character> ingredientToChar = new HashMap<>();

            int charIndex = 0;

            for (Object ingredient : request.ingredients) {
                if (ingredient == null) continue;

                if (ingredient instanceof ItemStack stack && stack.isEmpty()) {
                    continue;
                }

                JsonObject ingredientJson = createIngredientJson(ingredient);
                if (ingredientJson != null) {
                    String ingredientKey = ingredientJson.toString();

                    if (!ingredientToChar.containsKey(ingredientKey)) {
                        if (charIndex >= SYMBOL_CHARS.length()) {
                            throw new IllegalArgumentException(
                                    String.format("合成表原料过多，超过%d个符号限制", SYMBOL_CHARS.length())
                            );
                        }

                        char symbol = SYMBOL_CHARS.charAt(charIndex);
                        ingredientToChar.put(ingredientKey, symbol);
                        keyMapping.put(symbol, ingredientJson);
                        charIndex++;
                    }
                }
            }

            for (Map.Entry<Character, JsonObject> entry : keyMapping.entrySet()) {
                key.add(String.valueOf(entry.getKey()), entry.getValue());
            }
            recipe.add("key", key);
        }

        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    public static JsonObject createShapelessTableRecipe(String type, RecipeRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        Integer tier = (Integer) request.properties.get("tier");
        if (tier != null) {
            recipe.addProperty("tier", tier);
        }

        if (request.ingredients != null) {
            JsonArray ingredientsArray = new JsonArray();
            for (Object ingredient : request.ingredients) {
                JsonObject ingredientJson = createIngredientJson(ingredient,
                        (Boolean) request.properties.getOrDefault("includeNBT", true));
                if (ingredientJson != null) {
                    ingredientsArray.add(ingredientJson);
                }
            }
            recipe.add("ingredients", ingredientsArray);
        }

        recipe.add("result", createResultJson(request.result, request.resultCount));

        return recipe;
    }

    public static JsonObject createSmeltingRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 200);
    }

    public static JsonObject createBlastingRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 100);
    }

    public static JsonObject createSmokingRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 100);
    }

    public static JsonObject createCampfireRecipe(String type, RecipeRequest request) {
        return createCookingRecipe(type, request, 600);
    }

    private static JsonObject createCookingRecipe(String type, RecipeRequest request, int defaultCookingTime) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        if (request.ingredients != null && request.ingredients.length > 0) {
            JsonObject ingredientJson = createIngredientJson(request.ingredients[0]);
            if (ingredientJson != null) {
                recipe.add("ingredient", ingredientJson);
            }
        }

        if (request.result != null) {
            String itemId = getItemResourceLocation(request.result.getItem()).toString();
            recipe.addProperty("result", itemId);
        }

        Float experience = (Float) request.properties.get("experience");
        if (experience == null) {
            experience = (Double) request.properties.get("experience") != null
                    ? ((Double) request.properties.get("experience")).floatValue()
                    : 0.1f;
        }
        recipe.addProperty("experience", experience);

        Integer cookingTime = (Integer) request.properties.get("cookingtime");
        if (cookingTime == null) {
            cookingTime = defaultCookingTime;
        }
        recipe.addProperty("cookingtime", cookingTime);

        return recipe;
    }

    public static JsonObject createMultiOutputRecipe(String type, RecipeRequest request, String resultFieldName) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", type);

        if (request.ingredients != null) {
            JsonArray ingredientsArray = new JsonArray();
            for (Object ingredient : request.ingredients) {
                JsonObject ingredientJson = createIngredientJson(ingredient);
                if (ingredientJson != null) {
                    ingredientsArray.add(ingredientJson);
                }
            }
            recipe.add("ingredients", ingredientsArray);
        }

        JsonArray resultArray = createMultipleResults(request);
        recipe.add(resultFieldName, resultArray);

        return recipe;
    }

    public static JsonArray createMultipleResults(RecipeRequest request) {
        JsonArray resultArray = new JsonArray();

        if (request.result != null) {
            JsonObject mainResult = createResultJson(request.result, request.resultCount);
            resultArray.add(mainResult);
        }

        Object extraResults = request.properties.get("extraResults");
        if (extraResults instanceof ItemStack[] extraStacks) {
            for (ItemStack stack : extraStacks) {
                if (!stack.isEmpty()) {
                    JsonObject extraResult = createResultJson(stack, stack.getCount());

                    Float chance = (Float) request.properties.get("chance_" + stack.getItem());
                    if (chance != null && chance < 1.0f) {
                        extraResult.addProperty("chance", chance);
                    }

                    resultArray.add(extraResult);
                }
            }
        }

        return resultArray;
    }

    // ======================================================================
    // createIngredientJson 重载族
    // ======================================================================

    /**
     * 创建材料 JSON（默认包含 NBT，使用 forge:nbt 精确匹配）
     */
    public static JsonObject createIngredientJson(Object ingredient) {
        return createIngredientJson(ingredient, true, List.of());
    }

    /**
     * 创建材料 JSON（兼容旧调用：仅控制是否包含 NBT）
     */
    public static JsonObject createIngredientJson(Object ingredient, boolean includeNBT) {
        return createIngredientJson(ingredient, includeNBT, List.of());
    }

    /**
     * 从 {@link IngredientData} 创建材料 JSON（自动读取 ignoreNbtKeys）。
     *
     * <p>这是推荐的新入口，GUI 层应优先使用这个方法。
     */
    public static JsonObject createIngredientJson(IngredientData data) {
        if (data == null || data.isEmpty()) return null;

        return switch (data.getType()) {
            case TAG, CUSTOM_TAG -> {
                JsonObject json = new JsonObject();
                json.addProperty("tag", data.getTagId().toString());
                yield json;
            }
            case ITEM -> createIngredientJson(
                    data.getItemStack(),
                    data.isIncludeNBT(),
                    data.getIgnoreNbtKeys()
            );
        };
    }

    /**
     * 核心实现：创建材料 JSON，支持 forge:nbt（精确）和 registerhelper:partial_nbt（部分匹配）。
     *
     * @param ingredient  物品对象（ItemStack / Item / String / Map）
     * @param includeNBT  是否写入 NBT 匹配字段
     * @param ignoreKeys  若非空且 includeNBT=true，则改用 registerhelper:partial_nbt，
     *                    并将这些 key 写入 ignore_keys，在匹配时忽略它们
     */
    @SuppressWarnings("unchecked")
    public static JsonObject createIngredientJson(Object ingredient, boolean includeNBT, List<String> ignoreKeys) {
        JsonObject ingredientJson = new JsonObject();
        if (ingredient instanceof IngredientData data) {
            return createIngredientJson(data); // 走专用重载，内部已处理 ignoreKeys
        }
        if (ingredient instanceof ItemStack stack) {
            String itemId = getItemResourceLocation(stack.getItem()).toString();
            ingredientJson.addProperty("item", itemId);

            if (stack.getCount() > 1) {
                ingredientJson.addProperty("count", stack.getCount());
            }

            if (includeNBT && stack.hasTag()) {
                if (ignoreKeys != null && !ignoreKeys.isEmpty()) {
                    // ---- 部分匹配 registerhelper:partial_nbt ----
                    ingredientJson.addProperty("type", "registerhelper:partial_nbt");
                    ingredientJson.addProperty("nbt", stack.getTag().toString());
                    JsonArray arr = new JsonArray();
                    ignoreKeys.forEach(arr::add);
                    ingredientJson.add("ignore_keys", arr);
                } else {
                    // ---- 精确匹配 forge:nbt ----
                    ingredientJson.addProperty("type", "forge:nbt");
                    ingredientJson.addProperty("nbt", stack.getTag().toString());
                }
            }
            // includeNBT=false：不写 type/nbt，仅匹配物品 ID

        } else if (ingredient instanceof Item item) {
            String itemId = getItemResourceLocation(item).toString();
            ingredientJson.addProperty("item", itemId);

        } else if (ingredient instanceof String str) {
            if (str.startsWith("#")) {
                ingredientJson.addProperty("tag", str.substring(1));
            } else {
                ingredientJson.addProperty("item", str);
            }

        } else if (ingredient instanceof Map map) {
            if (map.containsKey("fluid")) {
                String fluidId = (String) map.get("fluid");
                int amount = (int) map.getOrDefault("amount", 250);
                ingredientJson.addProperty("fluid", fluidId);
                ingredientJson.addProperty("amount", amount);

                if (includeNBT && map.containsKey("nbt") && map.get("nbt") instanceof JsonElement json) {
                    ingredientJson.add("nbt", json);
                } else if (includeNBT) {
                    ingredientJson.add("nbt", new JsonObject());
                }
            } else if (map.containsKey("item")) {
                ingredientJson.addProperty("item", (String) map.get("item"));
                if (includeNBT && map.containsKey("nbt") && map.get("nbt") instanceof JsonElement json) {
                    ingredientJson.add("nbt", json);
                }
            }
        } else {
            return null;
        }

        return ingredientJson;
    }

    /**
     * 创建结果JSON对象
     */
    public static JsonObject createResultJson(ItemStack result, int count) {
        JsonObject resultJson = new JsonObject();

        String itemId = getItemResourceLocation(result.getItem()).toString();
        resultJson.addProperty("item", itemId);

        if (count > 1) {
            resultJson.addProperty("count", count);
        }

        if (result.hasTag()) {
            resultJson.addProperty("type", "forge:nbt");
            resultJson.addProperty("nbt", result.getTag().toString());
        }

        return resultJson;
    }

    public static char getCharFromObject(Object obj) {
        if (obj instanceof Character c) {
            if (SYMBOL_CHARS.indexOf(c) >= 0) {
                return c;
            } else {
                throw new IllegalArgumentException("符号必须是允许的字符: " + c);
            }
        } else if (obj instanceof String str) {
            if (str.isBlank()) {
                throw new IllegalArgumentException("符号不能为空");
            }
            char c = str.charAt(0);

            if (c == '#') {
                return c;
            }

            if (SYMBOL_CHARS.indexOf(c) >= 0) {
                return c;
            } else {
                throw new IllegalArgumentException("符号必须是允许的字符: " + c);
            }
        }
        throw new IllegalArgumentException("无效的符号类型: " + obj);
    }

    public static ResourceLocation getItemResourceLocation(Item item) {
        ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
        return location != null ? location : new ResourceLocation("minecraft", "air");
    }
}
