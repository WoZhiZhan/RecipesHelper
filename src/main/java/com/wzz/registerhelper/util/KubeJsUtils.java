package com.wzz.registerhelper.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class KubeJsUtils {
    
    // 支持的配方类型映射
    private static final Map<String, RecipeConverter> RECIPE_CONVERTERS = new HashMap<>();
    
    static {
        // Avaritia 配方
        RECIPE_CONVERTERS.put("avaritia:shaped_table", KubeJsUtils::convertAvaritiaShapedToJS);
        RECIPE_CONVERTERS.put("avaritia:shapeless_table", KubeJsUtils::convertAvaritiaShapelessToJS);
        
        // 原版配方
        RECIPE_CONVERTERS.put("minecraft:crafting_shaped", KubeJsUtils::convertVanillaShapedToJS);
        RECIPE_CONVERTERS.put("minecraft:crafting_shapeless", KubeJsUtils::convertVanillaShapelessToJS);
        
//        // Extended Crafting 配方
//        RECIPE_CONVERTERS.put("extendedcrafting:shaped_table", KubeJsUtils::convertExtendedShapedToJS);
//        RECIPE_CONVERTERS.put("extendedcrafting:shapeless_table", KubeJsUtils::convertExtendedShapelessToJS);
//        RECIPE_CONVERTERS.put("extendedcrafting:combination", KubeJsUtils::convertExtendedCombinationToJS);
//
//        // Thermal 系列配方
//        RECIPE_CONVERTERS.put("thermal:smelter", KubeJsUtils::convertThermalSmelterToJS);
//        RECIPE_CONVERTERS.put("thermal:pulverizer", KubeJsUtils::convertThermalPulverizerToJS);
//        RECIPE_CONVERTERS.put("thermal:centrifuge", KubeJsUtils::convertThermalCentrifugeToJS);
//
//        // Mekanism 配方
//        RECIPE_CONVERTERS.put("mekanism:crushing", KubeJsUtils::convertMekanismCrushingToJS);
//        RECIPE_CONVERTERS.put("mekanism:enriching", KubeJsUtils::convertMekanismEnrichingToJS);
//        RECIPE_CONVERTERS.put("mekanism:smelting", KubeJsUtils::convertMekanismSmeltingToJS);
//
//        // Create 配方
//        RECIPE_CONVERTERS.put("create:mixing", KubeJsUtils::convertCreateMixingToJS);
//        RECIPE_CONVERTERS.put("create:cutting", KubeJsUtils::convertCreateCuttingToJS);
//        RECIPE_CONVERTERS.put("create:pressing", KubeJsUtils::convertCreatePressingToJS);
//        RECIPE_CONVERTERS.put("create:crushing", KubeJsUtils::convertCreateCrushingToJS);
    }
    
    @FunctionalInterface
    private interface RecipeConverter {
        String convert(JsonObject recipeJson);
    }

    // ==================== Avaritia 配方转换 ====================
    
    private static String convertAvaritiaShapedToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 1;
        
        script.append("    avaritia.shaped_table(\n");
        script.append("        ").append(tier).append(",\n");
        script.append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray pattern = recipeJson.getAsJsonArray("pattern");
        for (int i = 0; i < pattern.size(); i++) {
            script.append("            '").append(pattern.get(i).getAsString()).append("'");
            if (i < pattern.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ],\n        {\n");
        
        JsonObject key = recipeJson.getAsJsonObject("key");
        boolean first = true;
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            if (!first) script.append(",\n");
            script.append("            ").append(entry.getKey()).append(": ");
            script.append(formatIngredient(entry.getValue().getAsJsonObject()));
            first = false;
        }
        script.append("\n        }\n    )");
        return script.toString();
    }
    
    private static String convertAvaritiaShapelessToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 1;
        
        script.append("    avaritia.shapeless_table(\n");
        script.append("        ").append(tier).append(",\n");
        script.append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            script.append("            ");
            script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ]\n    )");
        return script.toString();
    }

    // ==================== 原版配方转换 ====================
    
    private static String convertVanillaShapedToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.shaped(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray pattern = recipeJson.getAsJsonArray("pattern");
        for (int i = 0; i < pattern.size(); i++) {
            script.append("            '").append(pattern.get(i).getAsString()).append("'");
            if (i < pattern.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ],\n        {\n");
        
        JsonObject key = recipeJson.getAsJsonObject("key");
        boolean first = true;
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            if (!first) script.append(",\n");
            script.append("            ").append(entry.getKey()).append(": ");
            script.append(formatIngredient(entry.getValue().getAsJsonObject()));
            first = false;
        }
        script.append("\n        }\n    )");
        return script.toString();
    }
    
    private static String convertVanillaShapelessToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.shapeless(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            script.append("            ");
            script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ]\n    )");
        return script.toString();
    }

    // ==================== Extended Crafting 配方转换 ====================
    
    private static String convertExtendedShapedToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 0;
        
        script.append("    event.recipes.extendedcrafting.shaped_table(\n");
        if (tier > 0) {
            script.append("        ").append(tier).append(",\n");
        }
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray pattern = recipeJson.getAsJsonArray("pattern");
        for (int i = 0; i < pattern.size(); i++) {
            script.append("            '").append(pattern.get(i).getAsString()).append("'");
            if (i < pattern.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ],\n        {\n");
        
        JsonObject key = recipeJson.getAsJsonObject("key");
        boolean first = true;
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            if (!first) script.append(",\n");
            script.append("            ").append(entry.getKey()).append(": ");
            script.append(formatIngredient(entry.getValue().getAsJsonObject()));
            first = false;
        }
        script.append("\n        }\n    )");
        return script.toString();
    }
    
    private static String convertExtendedShapelessToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 0;
        
        script.append("    event.recipes.extendedcrafting.shapeless_table(\n");
        if (tier > 0) {
            script.append("        ").append(tier).append(",\n");
        }
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        [\n");
        
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            script.append("            ");
            script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ]\n    )");
        return script.toString();
    }
    
    private static String convertExtendedCombinationToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        int powerCost = recipeJson.has("powerCost") ? recipeJson.get("powerCost").getAsInt() : 0;
        
        script.append("    event.recipes.extendedcrafting.combination(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("result")));
        script.append(",\n        ").append(formatIngredient(recipeJson.getAsJsonObject("input")));
        script.append(",\n        [\n");
        
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            script.append("            ");
            script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) script.append(",");
            script.append("\n");
        }
        script.append("        ]\n    )");
        
        if (powerCost > 0) {
            script.append(".powerCost(").append(powerCost).append(")");
        }
        return script.toString();
    }

    // ==================== Thermal 配方转换 ====================
    
    private static String convertThermalSmelterToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.thermal.smelter(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("result");
        if (results.size() == 1) {
            script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        } else {
            script.append("        [");
            for (int i = 0; i < results.size(); i++) {
                script.append(formatOutput(results.get(i).getAsJsonObject()));
                if (i < results.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        
        script.append(",\n        ");
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        if (ingredients.size() == 1) {
            script.append(formatIngredient(ingredients.get(0).getAsJsonObject()));
        } else {
            script.append("[");
            for (int i = 0; i < ingredients.size(); i++) {
                script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
                if (i < ingredients.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        script.append("\n    )");
        
        if (recipeJson.has("energy")) {
            script.append(".energy(").append(recipeJson.get("energy").getAsInt()).append(")");
        }
        return script.toString();
    }
    
    private static String convertThermalPulverizerToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.thermal.pulverizer(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("result");
        if (results.size() == 1) {
            script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        } else {
            script.append("        [");
            for (int i = 0; i < results.size(); i++) {
                script.append(formatOutput(results.get(i).getAsJsonObject()));
                if (i < results.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("ingredient")));
        script.append("\n    )");
        
        if (recipeJson.has("energy")) {
            script.append(".energy(").append(recipeJson.get("energy").getAsInt()).append(")");
        }
        return script.toString();
    }
    
    private static String convertThermalCentrifugeToJS(JsonObject recipeJson) {
        return convertThermalPulverizerToJS(recipeJson).replace("pulverizer", "centrifuge");
    }

    // ==================== Mekanism 配方转换 ====================
    
    private static String convertMekanismCrushingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.mekanism.crushing(\n");
        script.append("        ").append(formatOutput(recipeJson.getAsJsonObject("output")));
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("input")));
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertMekanismEnrichingToJS(JsonObject recipeJson) {
        return convertMekanismCrushingToJS(recipeJson).replace("crushing", "enriching");
    }
    
    private static String convertMekanismSmeltingToJS(JsonObject recipeJson) {
        return convertMekanismCrushingToJS(recipeJson).replace("crushing", "smelting");
    }

    // ==================== Create 配方转换 ====================
    
    private static String convertCreateMixingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.create.mixing(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("results");
        if (results.size() == 1) {
            script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        } else {
            script.append("        [");
            for (int i = 0; i < results.size(); i++) {
                script.append(formatOutput(results.get(i).getAsJsonObject()));
                if (i < results.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        
        script.append(",\n        [");
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            script.append(formatIngredient(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) script.append(", ");
        }
        script.append("]\n    )");
        
        if (recipeJson.has("heatRequirement")) {
            script.append(".heated()");
        }
        return script.toString();
    }
    
    private static String convertCreateCuttingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.create.cutting(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("results");
        if (results.size() == 1) {
            script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        } else {
            script.append("        [");
            for (int i = 0; i < results.size(); i++) {
                script.append(formatOutput(results.get(i).getAsJsonObject()));
                if (i < results.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("ingredient")));
        script.append("\n    )");
        
        if (recipeJson.has("processingTime")) {
            script.append(".processingTime(").append(recipeJson.get("processingTime").getAsInt()).append(")");
        }
        return script.toString();
    }
    
    private static String convertCreatePressingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.create.pressing(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("results");
        script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("ingredient")));
        script.append("\n    )");
        return script.toString();
    }
    
    private static String convertCreateCrushingToJS(JsonObject recipeJson) {
        StringBuilder script = new StringBuilder();
        script.append("    event.recipes.create.crushing(\n");
        
        JsonArray results = recipeJson.getAsJsonArray("results");
        if (results.size() == 1) {
            script.append("        ").append(formatOutput(results.get(0).getAsJsonObject()));
        } else {
            script.append("        [");
            for (int i = 0; i < results.size(); i++) {
                script.append(formatOutput(results.get(i).getAsJsonObject()));
                if (i < results.size() - 1) script.append(", ");
            }
            script.append("]");
        }
        
        script.append(",\n        ");
        script.append(formatIngredient(recipeJson.getAsJsonObject("ingredient")));
        script.append("\n    )");
        
        if (recipeJson.has("processingTime")) {
            script.append(".processingTime(").append(recipeJson.get("processingTime").getAsInt()).append(")");
        }
        return script.toString();
    }

    // ==================== 辅助方法 ====================
    
    private static String formatOutput(JsonObject result) {
        String item = result.get("item").getAsString();
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        
        StringBuilder output = new StringBuilder("Item.of('").append(item).append("'");
        if (count > 1) {
            output.append(", ").append(count);
        }
        if (result.has("nbt")) {
            output.append(", '").append(result.get("nbt").toString()).append("'");
        }
        output.append(")");
        return output.toString();
    }
    
    private static String formatIngredient(JsonObject ingredient) {
        if (ingredient.has("tag")) {
            return "'#" + ingredient.get("tag").getAsString() + "'";
        } else if (ingredient.has("item")) {
            String item = ingredient.get("item").getAsString();
            StringBuilder ing = new StringBuilder("Item.of('").append(item).append("'");
            if (ingredient.has("nbt")) {
                ing.append(", '").append(ingredient.get("nbt").toString()).append("'");
            }
            ing.append(")");
            
            if (ingredient.has("nbt")) {
                ing.append(".strongNBT()");
            }
            return ing.toString();
        }
        return "Item.of('minecraft:air')";
    }

    // ==================== 主要导出方法 ====================
    
    public static List<Path> getAllJsonRecipeFiles() {
        List<Path> jsonFiles = new ArrayList<>();
        try {
            Path recipesDir = FMLPaths.GAMEDIR.get().resolve("config/registerhelper/recipes");
            
            if (Files.exists(recipesDir)) {
                try (Stream<Path> paths = Files.walk(recipesDir)) {
                    paths.filter(Files::isRegularFile)
                         .filter(path -> path.toString().endsWith(".json"))
                         .forEach(jsonFiles::add);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jsonFiles;
    }
    
    public static void exportAllJsonRecipesToJS(boolean singleFile) {
        List<Path> jsonFiles = getAllJsonRecipeFiles();
        Path recipesBaseDir = FMLPaths.GAMEDIR.get().resolve("config/registerhelper/recipes");

        if (singleFile) {
            // 按配方类型分组
            Map<String, List<String>> recipesByMod = new LinkedHashMap<>();
            
            for (Path jsonFile : jsonFiles) {
                try {
                    String jsonContent = Files.readString(jsonFile);
                    JsonObject recipeJson = JsonParser.parseString(jsonContent).getAsJsonObject();
                    String recipeType = recipeJson.get("type").getAsString();
                    
                    RecipeConverter converter = RECIPE_CONVERTERS.get(recipeType);
                    if (converter == null) {
                        System.out.println("不支持的配方类型: " + recipeType + " (文件: " + jsonFile.getFileName() + ")");
                        continue;
                    }
                    
                    String jsScript = converter.convert(recipeJson);
                    String modName = recipeType.split(":")[0];
                    
                    recipesByMod.computeIfAbsent(modName, k -> new ArrayList<>()).add(jsScript);
                    
                } catch (Exception e) {
                    System.out.println("解析配方失败: " + jsonFile);
                    e.printStackTrace();
                }
            }

            // 生成脚本
            StringBuilder allRecipes = new StringBuilder();
            allRecipes.append("// Auto-generated from config/registerhelper/recipes\n");
            allRecipes.append("// Total recipes: ").append(jsonFiles.size()).append("\n\n");
            
            allRecipes.append("ServerEvents.recipes(event => {\n");
            
            // 添加必要的常量声明
            if (recipesByMod.containsKey("avaritia")) {
                allRecipes.append("    const { avaritia } = event.recipes;\n");
            }
            
            allRecipes.append("\n");
            
            // 按 mod 分组输出
            for (Map.Entry<String, List<String>> entry : recipesByMod.entrySet()) {
                allRecipes.append("    // ========== ").append(entry.getKey().toUpperCase())
                          .append(" Recipes (").append(entry.getValue().size()).append(") ==========\n\n");
                
                for (String recipe : entry.getValue()) {
                    allRecipes.append(recipe).append(";\n\n");
                }
            }
            
            allRecipes.append("});");

            try {
                Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts");
                Files.createDirectories(scriptsDir);
                Files.writeString(scriptsDir.resolve("registerhelper_recipes.js"), allRecipes.toString());
                System.out.println("成功导出 " + jsonFiles.size() + " 个配方到 registerhelper_recipes.js");
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            // 按目录结构分文件导出
            for (Path jsonFile : jsonFiles) {
                try {
                    Path relativePath = recipesBaseDir.relativize(jsonFile);
                    String fileName = relativePath.toString()
                            .replace(".json", "")
                            .replace("\\", "_")
                            .replace("/", "_");
                    
                    String jsonContent = Files.readString(jsonFile);
                    JsonObject recipeJson = JsonParser.parseString(jsonContent).getAsJsonObject();
                    String recipeType = recipeJson.get("type").getAsString();
                    
                    RecipeConverter converter = RECIPE_CONVERTERS.get(recipeType);
                    if (converter == null) {
                        System.out.println("不支持的配方类型: " + recipeType);
                        continue;
                    }
                    
                    String jsScript = converter.convert(recipeJson);
                    
                    Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts");
                    Files.createDirectories(scriptsDir);
                    Path outputPath = scriptsDir.resolve(fileName + ".js");

                    StringBuilder content = new StringBuilder();
                    content.append("ServerEvents.recipes(event => {\n");
                    
                    String modName = recipeType.split(":")[0];
                    if (modName.equals("avaritia")) {
                        content.append("    const { avaritia } = event.recipes;\n\n");
                    }
                    
                    content.append(jsScript).append(";\n");
                    content.append("});");
                    
                    Files.writeString(outputPath, content.toString());
                    
                } catch (Exception e) {
                    System.out.println("导出配方失败: " + jsonFile);
                    e.printStackTrace();
                }
            }
            System.out.println("成功导出 " + jsonFiles.size() + " 个配方");
        }
    }
    
    // 获取支持的配方类型列表
    public static List<String> getSupportedRecipeTypes() {
        return new ArrayList<>(RECIPE_CONVERTERS.keySet());
    }
}