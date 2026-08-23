package com.wzz.registerhelper.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Resolves the authoritative JSON behind a server recipe id. */
public final class RecipeJsonResolver {
    private static final Gson GSON = new Gson();

    private RecipeJsonResolver() {
    }

    public static JsonObject resolve(MinecraftServer server, ResourceLocation recipeId) {
        JsonObject override = UnifiedRecipeOverrideManager.getOverride(recipeId);
        if (override != null) return override;

        File configFile = findConfigRecipe(recipeId);
        if (configFile != null) {
            try (FileReader reader = new FileReader(configFile)) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (Exception ignored) {
            }
        }

        if (server == null) return null;
        ResourceLocation resourceId = new ResourceLocation(
                recipeId.getNamespace(), "recipes/" + recipeId.getPath() + ".json");
        try {
            Optional<Resource> resource = server.getResourceManager().getResource(resourceId);
            if (resource.isPresent()) {
                try (var reader = resource.get().openAsReader()) {
                    return GSON.fromJson(reader, JsonObject.class);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static File findConfigRecipe(ResourceLocation recipeId) {
        Path root = FMLPaths.CONFIGDIR.get().resolve("registerhelper/recipes")
                .resolve(recipeId.getNamespace());
        File found = findInNamespace(root, recipeId.getPath());
        if (found != null) return found;

        Path customRoot = FMLPaths.CONFIGDIR.get().resolve("registerhelper/custom_recipes");
        if (!Files.exists(customRoot)) return null;
        try (var paths = Files.walk(customRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> path.getFileName().toString()
                            .equals(recipeId.getPath() + ".json"))
                    .map(Path::toFile)
                    .findFirst().orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static File findInNamespace(Path namespaceRoot, String recipePath) {
        if (!Files.exists(namespaceRoot)) return null;
        try (var paths = Files.walk(namespaceRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> {
                        String relative = namespaceRoot.relativize(path).toString();
                        relative = relative.substring(0, relative.length() - 5)
                                .replace(File.separatorChar, '_');
                        return relative.equals(recipePath);
                    })
                    .map(Path::toFile)
                    .findFirst().orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }
}
