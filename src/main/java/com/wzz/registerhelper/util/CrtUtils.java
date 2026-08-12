package com.wzz.registerhelper.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class CrtUtils {
    private static final String NEW_LINE = System.lineSeparator() + "\t";

    // Recipe type to CraftTweaker converter mapping.
    private static final Map<String, RecipeConverter> RECIPE_CONVERTERS = new HashMap<>();

    static {
        // Avaritia recipes.
        RECIPE_CONVERTERS.put("avaritia:shaped_table", CrtUtils::convertAvaritiaShapedToZS);
        RECIPE_CONVERTERS.put("avaritia:shapeless_table", CrtUtils::convertAvaritiaShapelessToZS);

        // Vanilla recipes.
        RECIPE_CONVERTERS.put("minecraft:crafting_shaped", CrtUtils::convertVanillaShapedToZS);
        RECIPE_CONVERTERS.put("minecraft:crafting_shapeless", CrtUtils::convertVanillaShapelessToZS);

        // Extended Crafting recipes.
        RECIPE_CONVERTERS.put("extendedcrafting:shaped_table", CrtUtils::convertExtendedShapedToZS);
        RECIPE_CONVERTERS.put("extendedcrafting:shapeless_table", CrtUtils::convertExtendedShapelessToZS);
        RECIPE_CONVERTERS.put("extendedcrafting:combination", CrtUtils::convertExtendedCombinationToZS);

        // Thermal recipes.
        RECIPE_CONVERTERS.put("thermal:smelter", CrtUtils::convertThermalSmelterToZS);
        RECIPE_CONVERTERS.put("thermal:pulverizer", CrtUtils::convertThermalPulverizerToZS);

        // Mekanism recipes.
        RECIPE_CONVERTERS.put("mekanism:crushing", CrtUtils::convertMekanismCrushingToZS);
        RECIPE_CONVERTERS.put("mekanism:enriching", CrtUtils::convertMekanismEnrichingToZS);

        // Create recipes.
        RECIPE_CONVERTERS.put("create:mixing", CrtUtils::convertCreateMixingToZS);
        RECIPE_CONVERTERS.put("create:cutting", CrtUtils::convertCreateCuttingToZS);
    }

    @FunctionalInterface
    private interface RecipeConverter {
        String convert(JsonObject recipeJson);
    }

    // ==================== Avaritia conversion ====================

    private static String convertAvaritiaShapedToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 1;

        JsonObject result = recipeJson.getAsJsonObject("result");

        string.append("mods.avaritia.CraftingTable.addShaped(\"").append(uuid).append("\", ");
        string.append(tier).append(", ");
        string.append(formatItemZS(result));
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

                if (col < patternLine.length() - 1) {
                    string.append(", ");
                }
            }

            string.append("]");
            if (row < gridSize - 1) {
                string.append(",").append(System.lineSeparator());
            }
        }

        string.append(System.lineSeparator()).append("]);");
        return string.toString();
    }

    private static String convertAvaritiaShapelessToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();
        int tier = recipeJson.has("tier") ? recipeJson.get("tier").getAsInt() : 1;

        JsonObject result = recipeJson.getAsJsonObject("result");

        string.append("mods.avaritia.CraftingTable.addShapeless(\"").append(uuid).append("\", ");
        string.append(tier).append(", ");
        string.append(formatItemZS(result));
        string.append(", [").append(NEW_LINE);

        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            string.append("    ");
            string.append(formatIngredientZS(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) {
                string.append(",");
            }
            string.append(NEW_LINE);
        }

        string.append("]);");
        return string.toString();
    }

    // ==================== Vanilla conversion ====================

    private static String convertVanillaShapedToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        JsonObject result = recipeJson.getAsJsonObject("result");

        string.append("craftingTable.addShaped(\"").append(uuid).append("\", ");
        string.append(formatItemZS(result)).append(", [").append(NEW_LINE);

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
                if (col < patternLine.length() - 1) {
                    string.append(", ");
                }
            }

            string.append("]");
            if (row < pattern.size() - 1) {
                string.append(",").append(System.lineSeparator());
            }
        }

        string.append(System.lineSeparator()).append("]);");
        return string.toString();
    }

    private static String convertVanillaShapelessToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        JsonObject result = recipeJson.getAsJsonObject("result");

        string.append("craftingTable.addShapeless(\"").append(uuid).append("\", ");
        string.append(formatItemZS(result)).append(", [").append(NEW_LINE);

        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            string.append("    ");
            string.append(formatIngredientZS(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) {
                string.append(",");
            }
            string.append(NEW_LINE);
        }

        string.append("]").append(");");
        return string.toString();
    }

    // ==================== Extended Crafting conversion ====================

    private static String convertExtendedShapedToZS(JsonObject recipeJson) {
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

        string.append("mods.extendedcrafting.CombinationCrafting.addRecipe(\"").append(uuid).append("\", ");
        string.append(formatItemZS(result)).append(", ");
        string.append(powerCost).append(", [").append(NEW_LINE);

        JsonObject input = recipeJson.getAsJsonObject("input");
        string.append("    ").append(formatIngredientZS(input)).append(", ");

        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            string.append(formatIngredientZS(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) {
                string.append(", ");
            }
        }

        string.append(System.lineSeparator()).append("]);");
        return string.toString();
    }

    // ==================== Thermal conversion ====================

    private static String convertThermalSmelterToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        string.append("mods.thermal.Smelter.addRecipe(\"").append(uuid).append("\", [");

        JsonArray results = recipeJson.getAsJsonArray("result");
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getAsJsonObject();
            string.append(formatItemZS(result));
            if (i < results.size() - 1) {
                string.append(", ");
            }
        }

        string.append("], [");

        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            string.append(formatIngredientZS(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) {
                string.append(", ");
            }
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

    // ==================== Mekanism conversion ====================

    private static String convertMekanismCrushingToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        JsonObject output = recipeJson.getAsJsonObject("output");
        JsonObject input = recipeJson.getAsJsonObject("input");

        string.append("mods.mekanism.crusher.addRecipe(\"").append(uuid).append("\", ");
        string.append(formatIngredientZS(input)).append(", ");
        string.append(formatItemZS(output)).append(");");

        return string.toString();
    }

    private static String convertMekanismEnrichingToZS(JsonObject recipeJson) {
        return convertMekanismCrushingToZS(recipeJson).replace("crusher", "enriching");
    }

    // ==================== Create conversion ====================

    private static String convertCreateMixingToZS(JsonObject recipeJson) {
        StringBuilder string = new StringBuilder();
        UUID uuid = UUID.randomUUID();

        string.append("mods.create.Mixing.addRecipe(\"").append(uuid).append("\", [");

        JsonArray results = recipeJson.getAsJsonArray("results");
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getAsJsonObject();
            string.append(formatItemZS(result));
            if (i < results.size() - 1) {
                string.append(", ");
            }
        }

        string.append("], [");

        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        for (int i = 0; i < ingredients.size(); i++) {
            string.append(formatIngredientZS(ingredients.get(i).getAsJsonObject()));
            if (i < ingredients.size() - 1) {
                string.append(", ");
            }
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
        string.append(formatItemZS(result)).append(", ");
        string.append(formatIngredientZS(ingredient));

        if (recipeJson.has("processingTime")) {
            string.append(", ").append(recipeJson.get("processingTime").getAsInt());
        }

        string.append(");");
        return string.toString();
    }

    // ==================== Formatting helpers ====================

    private static String formatItemZS(JsonObject item) {
        StringBuilder formatted = new StringBuilder("<item:")
                .append(getItemId(item))
                .append(">");
        appendData(formatted, item);
        return formatted.toString();
    }

    private static String getItemId(JsonObject item) {
        JsonElement id = item.get("id");
        if (id == null || id.isJsonNull()) {
            id = item.get("item");
        }
        return id == null || id.isJsonNull() ? "minecraft:air" : id.getAsString();
    }

    private static void appendData(StringBuilder formatted, JsonObject item) {
        if (!ModList.get().isLoaded("crafttweaker")) {
            return;
        }

        // Keep both old nbt and 1.21 components usable without linking CraftTweaker APIs.
        JsonElement data = item.get("components");
        if (data == null || data.isJsonNull()) {
            data = item.get("nbt");
        }
        if (data != null && !data.isJsonNull()) {
            formatted.append(".withTag(").append(data).append(")");
        }
    }

    private static String formatIngredientZS(JsonObject ingredient) {
        if (ingredient.has("tag")) {
            return "<tag:items:" + ingredient.get("tag").getAsString() + ">";
        }

        if (ingredient.has("item") || ingredient.has("id")) {
            return formatItemZS(ingredient);
        }

        return "<item:minecraft:air>";
    }

    // ==================== Export methods ====================

    public static List<Path> getAllJsonRecipeFiles() {
        List<Path> jsonFiles = new ArrayList<>();
        try {
            Path recipesDir = FMLPaths.CONFIGDIR.get().resolve("registerhelper/recipes");

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
        Path recipesBaseDir = FMLPaths.CONFIGDIR.get().resolve("registerhelper/recipes");

        if (singleFile) {
            Map<String, List<String>> recipesByMod = new LinkedHashMap<>();

            for (Path jsonFile : jsonFiles) {
                try {
                    String jsonContent = Files.readString(jsonFile);
                    JsonObject recipeJson = JsonParser.parseString(jsonContent).getAsJsonObject();
                    String recipeType = recipeJson.get("type").getAsString();

                    RecipeConverter converter = RECIPE_CONVERTERS.get(recipeType);
                    if (converter == null) {
                        System.out.println("Unsupported recipe type: " + recipeType + " (file: " + jsonFile.getFileName() + ")");
                        continue;
                    }

                    String zsScript = converter.convert(recipeJson);
                    String modName = recipeType.split(":")[0];
                    recipesByMod.computeIfAbsent(modName, ignored -> new ArrayList<>()).add(zsScript);
                } catch (Exception e) {
                    System.out.println("Failed to parse recipe: " + jsonFile);
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
                System.out.println("Exported " + jsonFiles.size() + " recipes to registerhelper_recipes.zs");
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
                        System.out.println("Unsupported recipe type: " + recipeType);
                        continue;
                    }

                    String zsScript = converter.convert(recipeJson);
                    Path scriptsDir = FMLPaths.GAMEDIR.get().resolve("scripts");
                    Files.createDirectories(scriptsDir);
                    Files.writeString(scriptsDir.resolve(fileName + ".zs"), zsScript);
                } catch (Exception e) {
                    System.out.println("Failed to export recipe: " + jsonFile);
                    e.printStackTrace();
                }
            }
            System.out.println("Exported " + jsonFiles.size() + " recipes");
        }
    }

    public static List<String> getSupportedRecipeTypes() {
        return new ArrayList<>(RECIPE_CONVERTERS.keySet());
    }
}
