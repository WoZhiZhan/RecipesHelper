package com.wzz.registerhelper.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * 配方创建网络包 - 直接传输完整的JSON
 * 支持标签、NBT、自定义标签等所有高级特性
 * 集成 UnifiedRecipeOverrideManager
 */
public class CreateRecipeJsonPacket {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String recipeId;      // 配方ID (namespace:path)
    private final String recipeJson;    // 完整的配方JSON字符串
    private final boolean isOverride;   // 是否为覆盖模式

    public CreateRecipeJsonPacket(String recipeId, String recipeJson, boolean isOverride) {
        this.recipeId = recipeId;
        this.recipeJson = recipeJson;
        this.isOverride = isOverride;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(recipeId);
        buf.writeUtf(recipeJson);
        buf.writeBoolean(isOverride);
    }

    public static CreateRecipeJsonPacket fromBytes(FriendlyByteBuf buf) {
        String recipeId = buf.readUtf(32767);
        String recipeJson = buf.readUtf(32767);
        boolean isOverride = buf.readBoolean();
        return new CreateRecipeJsonPacket(recipeId, recipeJson, isOverride);
    }

    public static void handle(CreateRecipeJsonPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            try {
                // 验证权限
                if (context.getSender() == null || !context.getSender().hasPermissions(2)) {
                    if (context.getSender() != null) {
                        context.getSender().sendSystemMessage(Component.literal("§c您没有权限创建配方"));
                    }
                    return;
                }

                // 解析并验证JSON
                JsonObject recipeObj;
                try {
                    recipeObj = JsonParser.parseString(packet.recipeJson).getAsJsonObject();
                } catch (Exception e) {
                    context.getSender().sendSystemMessage(Component.literal("§c配方JSON格式无效"));
                    LOGGER.error("配方JSON解析失败: {}", packet.recipeId, e);
                    return;
                }

                ResourceLocation recipeIdLoc = new ResourceLocation(packet.recipeId);
                boolean success;

                if (packet.isOverride) {
                    success = UnifiedRecipeOverrideManager.addOverride(recipeIdLoc, recipeObj);

                    if (success) {
                        context.getSender().sendSystemMessage(
                                Component.literal("§a配方覆盖成功: " + packet.recipeId + " 使用 /reload 刷新配方")
                        );
                    } else {
                        context.getSender().sendSystemMessage(Component.literal("§c配方覆盖失败"));
                        LOGGER.warn("配方覆盖失败: {}", packet.recipeId);
                    }
                } else {
                    SaveResult result = saveRecipeFile(recipeIdLoc, recipeObj);

                    if (result.success) {
                        context.getSender().sendSystemMessage(
                                Component.literal("§a配方创建成功: " + result.fileName + " 使用 /reload 刷新配方")
                        );
                        LOGGER.info("配方创建成功: {} (保存为: {})", packet.recipeId, result.fileName);
                    } else {
                        context.getSender().sendSystemMessage(Component.literal("§c配方创建失败"));
                        LOGGER.warn("配方创建失败: {}", packet.recipeId);
                    }
                }

            } catch (Exception e) {
                LOGGER.error("处理配方JSON包时发生错误", e);
                if (context.getSender() != null) {
                    context.getSender().sendSystemMessage(
                            Component.literal("§c处理配方时发生错误: " + e.getMessage())
                    );
                }
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * 保存结果类
     */
    private static class SaveResult {
        final boolean success;
        final String fileName;  // 实际保存的文件名
        final Path filePath;    // 完整路径（可选）

        SaveResult(boolean success, String fileName, Path filePath) {
            this.success = success;
            this.fileName = fileName;
            this.filePath = filePath;
        }
    }

    /**
     * 保存配方文件到服务器（创建模式）
     * @return SaveResult 包含成功状态和实际文件名
     */
    private static SaveResult saveRecipeFile(ResourceLocation recipeId, JsonObject recipeJson) {
        try {
            String namespace = recipeId.getNamespace();
            String path = recipeId.getPath();

            // 生成简化的文件名，保留custom前缀
            String fileName = generateOptimizedFileName(path, recipeJson);

            // 保存到 recipes 目录
            Path recipePath = FMLPaths.GAMEDIR.get()
                    .resolve("config/registerhelper/recipes")
                    .resolve(namespace)
                    .resolve(fileName + ".json");

            // 如果文件已存在，添加序号
            int counter = 1;
            Path finalPath = recipePath;
            String finalFileName = fileName;
            while (Files.exists(finalPath)) {
                finalFileName = fileName + "_" + counter;
                finalPath = FMLPaths.GAMEDIR.get()
                        .resolve("config/registerhelper/recipes")
                        .resolve(namespace)
                        .resolve(finalFileName + ".json");
                counter++;
            }

            // 创建目录
            Files.createDirectories(finalPath.getParent());

            // 写入JSON文件
            try (FileWriter writer = new FileWriter(finalPath.toFile())) {
                GSON.toJson(recipeJson, writer);
            }

            LOGGER.info("配方已保存: {} -> {}", recipeId, finalPath);

            // 返回带namespace的完整文件名
            String fullFileName = namespace + ":" + finalFileName;
            return new SaveResult(true, fullFileName, finalPath);

        } catch (Exception e) {
            LOGGER.error("保存配方文件失败: {}", recipeId, e);
            return new SaveResult(false, null, null);
        }
    }

    /**
     * 生成优化的文件名
     * 格式: custom_<类型简写>_<结果物品>
     * 例如: custom_av_shaped_diamond_sword
     */
    private static String generateOptimizedFileName(String path, JsonObject recipeJson) {
        StringBuilder fileName = new StringBuilder("custom");

        // 添加简化的配方类型
        String recipeType = extractSimplifiedType(path, recipeJson);
        if (!recipeType.isEmpty()) {
            fileName.append("_").append(recipeType);
        }

        // 添加结果物品名称
        String resultItem = extractResultItemName(recipeJson);
        if (!resultItem.isEmpty()) {
            fileName.append("_").append(resultItem);
        }

        // 如果还是太长，截断
        String result = fileName.toString();
        if (result.length() > 80) {
            result = result.substring(0, 80);
        }

        return result;
    }

    /**
     * 提取简化的配方类型
     */
    private static String extractSimplifiedType(String path, JsonObject recipeJson) {
        String type = "";

        // 优先从JSON的type字段获取
        if (recipeJson.has("type")) {
            String typeStr = recipeJson.get("type").getAsString();
            type = simplifyRecipeType(typeStr);
        }

        // 如果JSON中没有，从path推断
        if (type.isEmpty()) {
            type = inferTypeFromPath(path);
        }

        return type;
    }

    /**
     * 简化配方类型名称
     */
    private static String simplifyRecipeType(String typeStr) {
        // 移除namespace
        if (typeStr.contains(":")) {
            typeStr = typeStr.substring(typeStr.indexOf(':') + 1);
        }

        // 映射到简短名称
        return switch (typeStr) {
            // Avaritia
            case "shaped_table_recipe", "shaped_extreme_craft" -> "av_shaped";
            case "shapeless_table_recipe", "shapeless_extreme_craft" -> "av_shapeless";

            // 普通合成
            case "crafting_shaped", "minecraft:crafting_shaped" -> "shaped";
            case "crafting_shapeless", "minecraft:crafting_shapeless" -> "shapeless";

            // 烹饪
            case "smelting", "minecraft:smelting" -> "smelt";
            case "blasting", "minecraft:blasting" -> "blast";
            case "smoking", "minecraft:smoking" -> "smoke";
            case "campfire_cooking", "minecraft:campfire_cooking" -> "campfire";

            // 其他
            case "stonecutting", "minecraft:stonecutting" -> "stone";
            case "smithing_transform", "minecraft:smithing_transform" -> "smith";
            case "brewing", "minecraft:brewing" -> "brew";

            default -> {
                // 通用简化：移除常见后缀
                String simplified = typeStr
                        .replace("_recipe", "")
                        .replace("_table", "")
                        .replace("crafting_", "");

                // 如果还是太长，取前8个字符
                yield simplified.length() > 12 ? simplified.substring(0, 12) : simplified;
            }
        };
    }

    /**
     * 从路径推断类型
     */
    private static String inferTypeFromPath(String path) {
        if (path.contains("avaritia") && path.contains("shaped")) return "av_shaped";
        if (path.contains("avaritia") && path.contains("shapeless")) return "av_shapeless";
        if (path.contains("shaped")) return "shaped";
        if (path.contains("shapeless")) return "shapeless";
        if (path.contains("smelting")) return "smelt";
        if (path.contains("blasting")) return "blast";
        if (path.contains("smoking")) return "smoke";
        return "";
    }

    /**
     * 提取结果物品名称
     */
    private static String extractResultItemName(JsonObject recipeJson) {
        try {
            if (recipeJson.has("result")) {
                JsonObject result = recipeJson.getAsJsonObject("result");
                if (result.has("item")) {
                    String itemId = result.get("item").getAsString();
                    return simplifyItemName(itemId);
                }
            }
            if (recipeJson.has("results")) {
                var results = recipeJson.getAsJsonArray("results");
                if (results.size() > 0) {
                    JsonObject firstResult = results.get(0).getAsJsonObject();
                    if (firstResult.has("item")) {
                        String itemId = firstResult.get("item").getAsString();
                        return simplifyItemName(itemId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("提取结果物品名称失败", e);
        }

        return "";
    }

    /**
     * 简化物品名称
     */
    private static String simplifyItemName(String itemId) {
        // 移除namespace (minecraft:diamond_sword -> diamond_sword)
        if (itemId.contains(":")) {
            itemId = itemId.substring(itemId.indexOf(':') + 1);
        }

        // 限制长度
        if (itemId.length() > 30) {
            itemId = itemId.substring(0, 30);
        }

        return itemId;
    }
}