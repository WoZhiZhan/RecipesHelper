package com.wzz.registerhelper.network;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.ModMain;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配方创建网络包 - 直接传输完整的JSON
 * 支持标签、NBT、自定义标签等所有高级特性
 * 集成 UnifiedRecipeOverrideManager
 */
public record CreateRecipeJsonPacket(
        String recipeId,      // 配方ID (namespace:path)
        String recipeJson,    // 完整的配方JSON字符串
        boolean isOverride,   // 是否为覆盖模式
        boolean replaceExisting // 编辑自定义配方时覆盖原文件
) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public CreateRecipeJsonPacket {
        recipeId = recipeId == null ? "" : recipeId;
        recipeJson = recipeJson == null ? "" : recipeJson;
        NetworkProtocolLimits.validateString(
                recipeId, "recipeId", NetworkProtocolLimits.MAX_RECIPE_ID_LENGTH);
        NetworkProtocolLimits.validateString(
                recipeJson, "recipeJson", NetworkProtocolLimits.MAX_RECIPE_JSON_LENGTH);
    }

    public CreateRecipeJsonPacket(String recipeId, String recipeJson, boolean isOverride) {
        this(recipeId, recipeJson, isOverride, false);
    }

    public static final CustomPacketPayload.Type<CreateRecipeJsonPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "create_recipe_json")
            );

    public static final StreamCodec<ByteBuf, CreateRecipeJsonPacket> STREAM_CODEC = StreamCodec.composite(
            NetworkProtocolLimits.string(NetworkProtocolLimits.MAX_RECIPE_ID_LENGTH),
            CreateRecipeJsonPacket::recipeId,
            NetworkProtocolLimits.string(NetworkProtocolLimits.MAX_RECIPE_JSON_LENGTH),
            CreateRecipeJsonPacket::recipeJson,
            ByteBufCodecs.BOOL,
            CreateRecipeJsonPacket::isOverride,
            ByteBufCodecs.BOOL,
            CreateRecipeJsonPacket::replaceExisting,
            CreateRecipeJsonPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理数据包（服务器端）
     */
    public static void handle(CreateRecipeJsonPacket packet, IPayloadContext context) {
        // 在主线程处理
                context.enqueueWork(() -> {
                    try {
                        // 验证权限
                        if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
                            if (context.player() != null) {
                                context.player().sendSystemMessage(Component.translatable(
                                        "registerhelper.recipe.create.permission_denied")
                                        .withStyle(net.minecraft.ChatFormatting.RED));
                            }
                            return;
                        }

                        // 解析并验证JSON
                        JsonObject recipeObj;
                        try {
                            recipeObj = JsonParser.parseString(packet.recipeJson()).getAsJsonObject();
                        } catch (Exception e) {
                            player.sendSystemMessage(Component.translatable("registerhelper.recipe.json.invalid")
                                    .withStyle(net.minecraft.ChatFormatting.RED));
                            LOGGER.error("配方JSON解析失败: {}", packet.recipeId(), e);
                            return;
                        }

                        ResourceLocation recipeIdLoc;
                        try {
                            recipeIdLoc = ResourceLocation.parse(packet.recipeId());
                        } catch (Exception e) {
                            player.sendSystemMessage(Component.translatable(
                                    "registerhelper.recipe.id.invalid", packet.recipeId())
                                    .withStyle(net.minecraft.ChatFormatting.RED));
                            return;
                        }
                        boolean success;

                        if (packet.isOverride()) {
                            success = UnifiedRecipeOverrideManager.addOverride(recipeIdLoc, recipeObj);

                            if (success) {
                                player.sendSystemMessage(Component.translatable(
                                        "registerhelper.recipe.override.success", packet.recipeId())
                                        .withStyle(net.minecraft.ChatFormatting.GREEN));
                            } else {
                                player.sendSystemMessage(Component.translatable(
                                        "registerhelper.recipe.override.failed")
                                        .withStyle(net.minecraft.ChatFormatting.RED));
                                LOGGER.warn("配方覆盖失败: {}", packet.recipeId());
                            }
                        } else {
                            SaveResult result = saveRecipeFile(
                                    recipeIdLoc, recipeObj, packet.replaceExisting());

                            if (result.success) {
                                player.sendSystemMessage(Component.translatable(
                                        "registerhelper.recipe.create.success", result.fileName)
                                        .withStyle(net.minecraft.ChatFormatting.GREEN));
                                LOGGER.info("配方创建成功: {} (保存为: {})", packet.recipeId(), result.fileName);
                            } else {
                                player.sendSystemMessage(Component.translatable(
                                        "registerhelper.recipe.create.failed")
                                        .withStyle(net.minecraft.ChatFormatting.RED));
                                LOGGER.warn("配方创建失败: {}", packet.recipeId());
                            }
                        }

                    } catch (Exception e) {
                        LOGGER.error("处理配方JSON包时发生错误", e);
                        if (context.player() != null) {
                            context.player().sendSystemMessage(Component.translatable(
                                    "registerhelper.recipe.operation.failed", e.getMessage())
                                    .withStyle(net.minecraft.ChatFormatting.RED));
                        }
                    }
                })
                .exceptionally(e -> {
                    LOGGER.error("处理配方JSON包异步错误", e);
                    return null;
                });
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
    private static SaveResult saveRecipeFile(ResourceLocation recipeId, JsonObject recipeJson,
                                             boolean replaceExisting) {
        try {
            String namespace = recipeId.getNamespace();
            String path = recipeId.getPath();

            String baseFileName = generateOptimizedFileName(path, recipeJson);
            String recipeType = recipeJson.has("type") ? recipeJson.get("type").getAsString() : "";

            Path baseDir;
            if (isCustomRecipeType(recipeType)) {
                baseDir = FMLPaths.CONFIGDIR.get()
                        .resolve("registerhelper/custom_recipes")
                        .resolve(getCustomRecipeCategory(recipeType));
            } else {
                baseDir = FMLPaths.CONFIGDIR.get()
                        .resolve("registerhelper/recipes")
                        .resolve(namespace);
            }

            Files.createDirectories(baseDir);

            Path existingPath = replaceExisting
                    ? findExistingRecipePath(recipeId, recipeType, baseDir) : null;
            Path finalPath;
            String finalFileName;
            if (existingPath != null) {
                // 编辑已有自定义配方时，沿用原文件，避免每次编辑都生成 _1、_2...
                finalPath = existingPath;
                finalFileName = stripJsonExtension(existingPath.getFileName().toString());
            } else {
                // 新建同名配方时保留序号，避免覆盖用户已有文件
                int counter = 1;
                finalPath = baseDir.resolve(baseFileName + ".json");
                finalFileName = baseFileName;
                while (Files.exists(finalPath)) {
                    finalFileName = baseFileName + "_" + counter;
                    finalPath = baseDir.resolve(finalFileName + ".json");
                    counter++;
                }
            }

            // 写入JSON文件
            Files.createDirectories(finalPath.getParent());
            try (FileWriter writer = new FileWriter(finalPath.toFile())) {
                GSON.toJson(recipeJson, writer);
            }

            LOGGER.info("配方已保存: {} -> {}", recipeId, finalPath);

            String fullFileName = isCustomRecipeType(recipeType)
                    ? getCustomRecipeCategory(recipeType) + "/" + finalFileName
                    : namespace + ":" + finalFileName;
            return new SaveResult(true, fullFileName, finalPath);

        } catch (Exception e) {
            LOGGER.error("保存配方文件失败: {}", recipeId, e);
            return new SaveResult(false, null, null);
        }
    }

    private static Path findExistingRecipePath(ResourceLocation recipeId,
                                                String recipeType,
                                                Path baseDir) {
        String path = recipeId.getPath();
        if (path.isBlank() || path.contains("..") || path.startsWith("/")) {
            return null;
        }

        if (isCustomRecipeType(recipeType) && path.startsWith("custom_") && path.contains("/")) {
            String category = path.substring("custom_".length(), path.indexOf('/'));
            String fileName = path.substring(path.indexOf('/') + 1);
            if (category.equals(getCustomRecipeCategory(recipeType))
                    && !fileName.isBlank() && !fileName.contains("/")) {
                Path candidate = baseDir.resolve(fileName + ".json");
                return Files.isRegularFile(candidate) ? candidate : null;
            }
        }

        if (path.startsWith("custom_")) {
            Path candidate = baseDir.resolve(path + ".json").normalize();
            if (candidate.startsWith(baseDir.normalize()) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String stripJsonExtension(String fileName) {
        return fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - ".json".length())
                : fileName;
    }

    private static boolean isCustomRecipeType(String type) {
        return type.contains("brewing") || type.contains("anvil")
                || type.equals("registerhelper:brewing") || type.equals("registerhelper:anvil");
    }

    private static String getCustomRecipeCategory(String type) {
        if (type.contains("brewing")) {
            return "brewing";
        }
        if (type.contains("anvil")) {
            return "anvil";
        }
        return "other";
    }

    /**
     * 生成优化的文件名
     * 格式: custom_<类型简写>_<结果物品>
     * 例如: custom_av_shaped_diamond_sword
     */
    public static String generateOptimizedFileName(String path, JsonObject recipeJson) {
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

        result = result.toLowerCase()
                .replaceAll("[^a-z0-9/._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("_$", "");

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
            // ---------- 单 result ----------
            if (recipeJson.has("result")) {
                String id = extractIdFromResultElement(recipeJson.get("result"));
                if (id != null) {
                    return simplifyItemName(id);
                }
            }

            // ---------- 多 results ----------
            if (recipeJson.has("results")) {
                var results = recipeJson.getAsJsonArray("results");
                if (!results.isEmpty()) {
                    String id = extractIdFromResultElement(results.get(0));
                    if (id != null) {
                        return simplifyItemName(id);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("提取结果物品名称失败", e);
        }
        return "";
    }

    private static String extractIdFromResultElement(JsonElement element) {
        // 简写形式："minecraft:stone"
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }

        // 标准对象形式
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            // 1.21 新标准
            if (obj.has("id")) {
                JsonElement idElement = obj.get("id");
                if (idElement.isJsonPrimitive()) {
                    return idElement.getAsString();
                }
            }

            if (obj.has("item")) {
                JsonElement itemElement = obj.get("item");
                if (itemElement.isJsonPrimitive()) {
                    return itemElement.getAsString();
                }
                if (itemElement.isJsonObject()) {
                    JsonObject itemObject = itemElement.getAsJsonObject();
                    if (itemObject.has("item")) {
                        return itemObject.get("item").getAsString();
                    }
                    if (itemObject.has("tag")) {
                        return "#" + itemObject.get("tag").getAsString();
                    }
                }
            }
            if (obj.has("block")) {
                return obj.get("block").getAsString();
            }
            if (obj.has("name")) { // 某些模组（如 Botania）
                return obj.get("name").getAsString();
            }
        }

        return null;
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
