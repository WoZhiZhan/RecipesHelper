package com.wzz.registerhelper.util;

import com.blamejared.crafttweaker.api.data.MapData;
import com.blamejared.crafttweaker.api.data.visitor.DataToTextComponentVisitor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class CrtUtils {
    private static final String NEW_LINE = System.lineSeparator() + "\t";
    
    // 支持的配方类型映射
    private static final Map<String, RecipeConverter> RECIPE_CONVERTERS = new HashMap<>();
    
    static {
        // Avaritia 配方
        RECIPE_CONVERTERS.put("avaritia:shaped_table", CrtUtils::convertAvaritiaShapedToZS);
        RECIPE_CONVERTERS.put("avaritia:shapeless_table", CrtUtils::convertAvaritiaShapelessToZS);
        
        // 原版配方
        RECIPE_CONVERTERS.put("minecraft:crafting_shaped", CrtUtils::convertVanillaShapedToZS);
        RECIPE_CONVERTERS.put("minecraft:crafting_shapeless", CrtUtils::convertVanillaShapelessToZS);
        
//        // Extended Crafting 配方
//        RECIPE_CONVERTERS.put("extendedcrafting:shaped_table", CrtUtils::convertExtendedShapedToZS);
//        RECIPE_CONVERTERS.put("extendedcrafting:shapeless_table", CrtUtils::convertExtendedShapelessToZS);
//        RECIPE_CONVERTERS.put("extendedcrafting:combination", CrtUtils::convertExtendedCombinationToZS);
//
//        // Thermal 系列
//        RECIPE_CONVERTERS.put("thermal:smelter", CrtUtils::convertThermalSmelterToZS);
//        RECIPE_CONVERTERS.put("thermal:pulverizer", CrtUtils::convertThermalPulverizerToZS);
//
//        // Mekanism 配方
//        RECIPE_CONVERTERS.put("mekanism:crushing", CrtUtils::convertMekanismCrushingToZS);
//        RECIPE_CONVERTERS.put("mekanism:enriching", CrtUtils::convertMekanismEnrichingToZS);
//
//        // Create 配方
//        RECIPE_CONVERTERS.put("create:mixing", CrtUtils::convertCreateMixingToZS);
//        RECIPE_CONVERTERS.put("create:cutting", CrtUtils::convertCreateCuttingToZS);
    }
    
    @FunctionalInterface
    private interface RecipeConverter {
        String convert(JsonObject recipeJson);
    }

    private static String writeTag(CompoundTag tag) {
        return (new MapData(tag).accept(new DataToTextComponentVisitor("", 0)).getString());
    }

    // ==================== Avaritia 配方转换 ====================
    
    private static String convertAvaritiaShapedToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 1;

        JsonObject result = recipeJson.getAsJsonObject("result");
        String outputItem = result.get("item").getAsString();

        string.append("mods.avaritia.CraftingTable.addShaped(\"").append(uuid).append("\", ");
        string.append(tier).append(", ");
        string.append("<item:").append(outputItem).append(">");
        
        if (result.has("nbt") && ModList.get().isLoaded("crafttweaker")) {
            JsonObject nbt = result.getAsJsonObject("nbt");
            string.append(".withTag(").append(nbt.toString()).append(")");
        }
        
        string.append(", [").append(NEW_LINE);

        JsonArray pattern = recipeJson.getAsJsonArray("pattern");
        JsonObject key = recipeJson.getAsJsonObject("key");
        int gridSize = pattern.size();

        for (int row = 0; row < gridSize; row++) {
            string.append("    [");
            String patternLine = pattern.get(row).getAsString();

            for (int col = 0; col < patternLine.length(); col++) {
                char keyChar = patternLine.charAt(col);

                if (keyChar == ' ') {
                    string.append("<item:minecraft:air>");
                } else {
                    JsonObject ingredient = key.getAsJsonObject(String.valueOf(keyChar));
                    string.append(formatIngredientZS(ingredient));
                }

                if (col < patternLine.length() - 1) string.append(", ");
            }

            if (row <= gridSize - 1) string.append("]");
            if (row < gridSize - 1) string.append(",").append(System.lineSeparator());
        }

        string.append(System.lineSeparator()).append("]);");
        return string.toString();
    }
    
    private static String convertAvaritiaShapelessToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 1;

        JsonObject result = recipeJson.getAsJsonObject("result");
        String outputItem = result.get("item").getAsString();

        string.append("mods.avaritia.CraftingTable.addShapeless(\"").append(uuid).append("\", ");
        string.append(tier).append(", ");
        string.append("<item:").append(outputItem).append(">");
        
        if (result.has("nbt") && ModList.get().isLoaded("crafttweaker")) {
            JsonObject nbt = result.getAsJsonObject("nbt");
            string.append(".withTag(").append(nbt.toString()).append(")");
        }
        
        string.append(", [").append(NEW_LINE);

        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            string.append("    ");
            string.append(formatIngredientZS(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) string.append(",");
            string.append(NEW_LINE);
        }

        string.append("]);");
        return string.toString();
    }

    // ==================== 原版配方转换 ====================
    
    private static String convertVanillaShapedToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        JsonObject result = recipeJson.getAsJsonObject("result");
        String outputItem = result.get("item").getAsString();

        string.append("craftingTable.addShaped(\"").append(uuid).append("\", ");
        string.append("<item:").append(outputItem).append(">, [").append(NEW_LINE);

        JsonArray pattern = recipeJson.getAsJsonArray("pattern");
        JsonObject key = recipeJson.getAsJsonObject("key");

        for (int row = 0; row < pattern.size(); row++) {
            string.append("    [");
            String patternLine = pattern.get(row).getAsString();

            for (int col = 0; col < patternLine.length(); col++) {
                char keyChar = patternLine.charAt(col);
                if (keyChar == ' ') {
                    string.append("<item:minecraft:air>");
                } else {
                    JsonObject ingredient = key.getAsJsonObject(String.valueOf(keyChar));
                    string.append(formatIngredientZS(ingredient));
                }
                if (col < patternLine.length() - 1) string.append(", ");
            }

            if (row <= pattern.size() - 1) string.append("]");
            if (row < pattern.size() - 1) string.append(",").append(System.lineSeparator());
        }

        string.append(System.lineSeparator()).append("]);");
        return string.toString();
    }
    
    private static String convertVanillaShapelessToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        JsonObject result = recipeJson.getAsJsonObject("result");
        String outputItem = result.get("item").getAsString();

        string.append("craftingTable.addShapeless(\"").append(uuid).append("\", ");
        string.append("<item:").append(outputItem).append(">, [").append(NEW_LINE);

        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            string.append("    ");
            string.append(formatIngredientZS(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) string.append(",");
            string.append(NEW_LINE);
        }

        string.append("]);");
        return string.toString();
    }

    // ==================== Extended Crafting 配方转换 ====================
    
    private static String convertExtendedShapedToZS(JsonObject recipeJson) {
        // 类似于 Avaritia，但使用不同的方法名
        return convertAvaritiaShapedToZS(recipeJson)
                .replace("mods.avaritia.CraftingTable", "mods.extendedcrafting.TableCrafting");
    }
    
    private static String convertExtendedShapelessToZS(JsonObject recipeJson) {
        return convertAvaritiaShapelessToZS(recipeJson)
                .replace("mods.avaritia.CraftingTable", "mods.extendedcrafting.TableCrafting");
    }
    
    private static String convertExtendedCombinationToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();
        int powerCost = recipeJson.has("powerCost") ? recipeJson.get("powerCost").getAsInt() : 0;

        JsonObject result = recipeJson.getAsJsonObject("result");
        String outputItem = result.get("item").getAsString();

        string.append("mods.extendedcrafting.CombinationCrafting.addRecipe(\"").append(uuid).append("\", ");
        string.append("<item:").append(outputItem).append(">, ");
        string.append(powerCost).append(", [").append(NEW_LINE);

        JsonObject input = recipeJson.getAsJsonObject("input");
        string.append("    ").append(formatIngredientZS(input)).append(", ");

        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            string.append(formatIngredientZS(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) string.append(", ");
        }

        string.append(System.lineSeparator()).append("]);");
        return string.toString();
    }

    // ==================== Thermal 配方转换 ====================
    
    private static String convertThermalSmelterToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        string.append("mods.thermal.Smelter.addRecipe(\"").append(uuid).append("\", [");

        JsonArray results = recipeJson.getAsJsonArray("result");
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getAsJsonObject();
            string.append("<item:").append(result.get("item").getAsString()).append(">");
            if (i < results.size() - 1) string.append(", ");
        }

        string.append("], [");

        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            string.append(formatIngredientZS(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) string.append(", ");
        }

        string.append("]");

        if (recipeJson.has("energy")) {
            string.append(", ").append(recipeJson.get("energy").getAsInt());
        }

        string.append(");");
        return string.toString();
    }
    
    private static String convertThermalPulverizerToZS(JsonObject recipeJson) {
        return convertThermalSmelterToZS(recipeJson).replace("Smelter", "Pulverizer");
    }

    // ==================== Mekanism 配方转换 ====================
    
    private static String convertMekanismCrushingToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        JsonObject output = recipeJson.getAsJsonObject("output");
        JsonObject input = recipeJson.getAsJsonObject("input");

        string.append("mods.mekanism.crusher.addRecipe(\"").append(uuid).append("\", ");
        string.append(formatIngredientZS(input)).append(", ");
        string.append("<item:").append(output.get("item").getAsString()).append(">);");

        return string.toString();
    }
    
    private static String convertMekanismEnrichingToZS(JsonObject recipeJson) {
        return convertMekanismCrushingToZS(recipeJson).replace("crusher", "enriching");
    }

    // ==================== Create 配方转换 ====================
    
    private static String convertCreateMixingToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        string.append("mods.create.Mixing.addRecipe(\"").append(uuid).append("\", [");

        JsonArray results = recipeJson.getAsJsonArray("results");
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getAsJsonObject();
            string.append("<item:").append(result.get("item").getAsString()).append(">");
            if (i < results.size() - 1) string.append(", ");
        }

        string.append("], [");

        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            string.append(formatIngredientZS(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) string.append(", ");
        }

        string.append("]");

        if (recipeJson.has("heatRequirement")) {
            string.append(", \"").append(recipeJson.get("heatRequirement").getAsString()).append("\"");
        }

        string.append(");");
        return string.toString();
    }
    
    private static String convertCreateCuttingToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        JsonArray results = recipeJson.getAsJsonArray("results");
        JsonObject result = results.get(0).getAsJsonObject();
        JsonObject ingredient = recipeJson.getAsJsonObject("ingredient");

        string.append("mods.create.Cutting.addRecipe(\"").append(uuid).append("\", ");
        string.append("<item:").append(result.get("item").getAsString()).append(">, ");
        string.append(formatIngredientZS(ingredient));

        if (recipeJson.has("processingTime")) {
            string.append(", ").append(recipeJson.get("processingTime").getAsInt());
        }

        string.append(");");
        return string.toString();
    }

    // ==================== 辅助方法 ====================
    
    private static String formatIngredientZS(JsonObject ingredient) {
        if (ingredient.has("tag")) {
            return "<tag:items:" + ingredient.get("tag").getAsString() + ">";
        } else if (ingredient.has("item")) {
            String item = ingredient.get("item").getAsString();
            StringBuilder ing = new StringBuilder("<item:").append(item).append(">");
            
            if (ingredient.has("nbt") && ModList.get().isLoaded("crafttweaker")) {
                JsonObject nbt = ingredient.getAsJsonObject("nbt");
                ing.append(".withTag(").append(nbt.toString()).append(")");
            }
            return ing.toString();
        }
        return "<item:minecraft:air>";
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
    
    public static void exportAllJsonRecipesToZS(boolean singleFile) {
        List<Path> jsonFiles = getAllJsonRecipeFiles();
        Path recipesBaseDir = FMLPaths.GAMEDIR.get().resolve("config/registerhelper/recipes");

        if (singleFile) {
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
                    
                    String zsScript = converter.convert(recipeJson);
                    String modName = recipeType.split(":")[0];
                    
                    recipesByMod.computeIfAbsent(modName, k -> new ArrayList<>()).add(zsScript);
                    
                } catch (Exception e) {
                    System.out.println("解析配方失败: " + jsonFile);
                    e.printStackTrace();
                }
            }

            StringBuilder allRecipes = new StringBuilder();
            allRecipes.append("// Auto-generated from config/registerhelper/recipes\n");
            allRecipes.append("// Total recipes: ").append(jsonFiles.size()).append("\n\n");
            
            for (Map.Entry<String, List<String>> entry : recipesByMod.entrySet()) {
                allRecipes.append("// ========== ").append(entry.getKey().toUpperCase())
                          .append(" Recipes (").append(entry.getValue().size()).append(") ==========\n\n");
                
                for (String recipe : entry.getValue()) {
                    allRecipes.append(recipe).append("\n\n");
                }
            }

            try {
                Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("scripts");
                Files.createDirectories(scriptsDir);
                Files.writeString(scriptsDir.resolve("registerhelper_recipes.zs"), allRecipes.toString());
                System.out.println("成功导出 " + jsonFiles.size() + " 个配方到 registerhelper_recipes.zs");
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
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
                    
                    String zsScript = converter.convert(recipeJson);
                    
                    Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("scripts");
                    Files.createDirectories(scriptsDir);
                    Files.writeString(scriptsDir.resolve(fileName + ".zs"), zsScript);
                    
                } catch (Exception e) {
                    System.out.println("导出配方失败: " + jsonFile);
                    e.printStackTrace();
                }
            }
            System.out.println("成功导出 " + jsonFiles.size() + " 个配方");
        }
    }
    
    public static List<String> getSupportedRecipeTypes() {
        return new ArrayList<>(RECIPE_CONVERTERS.keySet());
    }
}