package com.wzz.registerhelper.recipe.integration;

import com.google.gson.*;
import com.wzz.registerhelper.recipe.RecipeRequest;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraftforge.fml.ModList;

import java.util.*;
import java.lang.reflect.Method;

public class JsonDefinedProcessor implements ModRecipeProcessor {
    private final JsonObject processorDef;
    private final String modid;

    private static final Map<String, GeneratorInvoker> GENERATORS = new HashMap<>();
    static {
        GENERATORS.put("shaped", RecipeUtil::createShapedTableRecipe);
        GENERATORS.put("shapeless", RecipeUtil::createShapelessTableRecipe);
        GENERATORS.put("smelting", RecipeUtil::createSmeltingRecipe);
        GENERATORS.put("blasting", RecipeUtil::createBlastingRecipe);
        GENERATORS.put("smoking", RecipeUtil::createSmokingRecipe);
        GENERATORS.put("campfire", RecipeUtil::createCampfireRecipe);
    }

    public JsonDefinedProcessor(JsonObject processorDef) {
        this.processorDef = processorDef;
        this.modid = processorDef.get("modid").getAsString();
    }

    @Override
    public boolean isModLoaded() {
        JsonObject methods = processorDef.getAsJsonObject("methods");
        if (methods.has("isModLoaded")) {
            JsonObject def = methods.getAsJsonObject("isModLoaded");
            if (def.has("condition")) {
                JsonObject cond = def.getAsJsonObject("condition");
                String type = cond.get("type").getAsString();
                if ("forge:mod_loaded".equals(type)) {
                    return ModList.get().isLoaded(cond.get("modid").getAsString());
                }
            }
        }
        return ModList.get().isLoaded(modid);
    }

    @Override
    public String[] getSupportedRecipeTypes() {
        JsonObject methods = processorDef.getAsJsonObject("methods");
        if (methods.has("getSupportedRecipeTypes")) {
            JsonObject def = methods.getAsJsonObject("getSupportedRecipeTypes");
            if (def.has("return")) {
                JsonArray arr = def.getAsJsonArray("return");
                List<String> result = new ArrayList<>();
                for (JsonElement e : arr) result.add(e.getAsString());
                return result.toArray(new String[0]);
            }
        }
        return new String[0];
    }

    @Override
    public JsonObject createRecipeJson(RecipeRequest request) {
        JsonObject methods = processorDef.getAsJsonObject("methods");
        if (!methods.has("createRecipeJson")) {
            throw new UnsupportedOperationException("createRecipeJson 未定义: " + modid);
        }

        JsonObject def = methods.getAsJsonObject("createRecipeJson");

        // 1. delegate
        if (def.has("delegate")) {
            String delegate = def.get("delegate").getAsString();
            if ("createShapedTableRecipe".equals(delegate)) {
                return createShapedTableRecipe(request);
            }
            throw new UnsupportedOperationException("不支持的 delegate: " + delegate);
        }

        // 2. generator
        if (def.has("generator")) {
            return invokeGenerator(def, request);
        }

        throw new UnsupportedOperationException("createRecipeJson 定义不正确: " + modid);
    }

    private JsonObject createShapedTableRecipe(RecipeRequest request) {
        JsonObject methods = processorDef.getAsJsonObject("methods");
        JsonObject def = methods.getAsJsonObject("createShapedTableRecipe");
        String recipeType = def.get("recipeType").getAsString();
        return RecipeUtil.createShapedTableRecipe(recipeType, request);
    }

    /**
     * 通用 generator 调用
     */
    private JsonObject invokeGenerator(JsonObject def, RecipeRequest request) {
        String generator = def.get("generator").getAsString();
        String recipeType = def.get("recipeType").getAsString();

        // 简写方式: 直接映射
        if (GENERATORS.containsKey(generator)) {
            return GENERATORS.get(generator).generate(recipeType, request);
        }

        // 完整方式: class.method
        if (generator.contains(".")) {
            return reflectInvoke(generator, recipeType, request);
        }

        throw new RuntimeException("未知 generator: " + generator);
    }

    private JsonObject reflectInvoke(String generator, String recipeType, RecipeRequest request) {
        String className = generator.substring(0, generator.lastIndexOf('.'));
        String methodName = generator.substring(generator.lastIndexOf('.') + 1);

        try {
            Class<?> clazz = Class.forName(className);
            Method m = clazz.getDeclaredMethod(methodName, String.class, RecipeRequest.class);
            return (JsonObject) m.invoke(null, recipeType, request);
        } catch (Exception e) {
            throw new RuntimeException("调用 generator 失败: " + generator, e);
        }
    }

    // 简单的函数式接口
    private interface GeneratorInvoker {
        JsonObject generate(String type, RecipeRequest req);
    }
}