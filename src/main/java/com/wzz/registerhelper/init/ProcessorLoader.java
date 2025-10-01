package com.wzz.registerhelper.init;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wzz.registerhelper.recipe.integration.JsonDefinedProcessor;
import com.wzz.registerhelper.util.RegisterHelper;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.stream.Stream;

public class ProcessorLoader {
    public static void loadProcessors(FMLCommonSetupEvent event) {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("registerhelper/processor");
        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        try (Stream<Path> paths = Files.walk(configDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".json"))
                 .forEach(path -> {
                     try (Reader reader = Files.newBufferedReader(path)) {
                         JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                         JsonObject processorDef = root.getAsJsonObject("processor");
                         String modid = processorDef.get("modid").getAsString();

                         JsonDefinedProcessor processor = new JsonDefinedProcessor(processorDef);

                         // 如果 JSON 里定义了 recipeType 注册方式
                         if (root.has("recipeType")) {
                             JsonObject typeDef = root.getAsJsonObject("recipeType");
                             String type = typeDef.get("type").getAsString();
                             String displayName = typeDef.get("displayName").getAsString();

                             if (typeDef.has("layout")) {
                                 String layout = typeDef.get("layout").getAsString();
                                 RegisterHelper.registerRecipeTypeWithLayout(modid, type, displayName, processor, layout);
                             } else {
                                 int gridWidth = typeDef.has("gridWidth") ? typeDef.get("gridWidth").getAsInt() : 3;
                                 int gridHeight = typeDef.has("gridHeight") ? typeDef.get("gridHeight").getAsInt() : 3;
                                 boolean supportsTiers = typeDef.has("supportsTiers") && typeDef.get("supportsTiers").getAsBoolean();
                                 RegisterHelper.registerRecipeType(modid, type, displayName, processor, gridWidth, gridHeight, supportsTiers);
                             }
                         } else {
                             // 只注册处理器
                             RegisterHelper.registerProcessor(modid, processor);
                         }
                     } catch (Exception e) {
                         System.err.println("加载配方处理器失败: " + path + " -> " + e.getMessage());
                         e.printStackTrace();
                     }
                 });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}