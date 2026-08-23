package com.wzz.registerhelper.network;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import com.wzz.registerhelper.tags.CustomTagManager;
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
import java.util.Map;
import java.util.List;

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
    private final Map<String, List<String>> customTags;

    public CreateRecipeJsonPacket(String recipeId, String recipeJson, boolean isOverride) {
        this(recipeId, recipeJson, isOverride, Map.of());
    }

    public CreateRecipeJsonPacket(String recipeId, String recipeJson, boolean isOverride,
                                  Map<String, List<String>> customTags) {
        this.recipeId = recipeId;
        this.recipeJson = recipeJson;
        this.isOverride = isOverride;
        this.customTags = customTags == null ? Map.of() : customTags;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(recipeId);
        buf.writeUtf(recipeJson);
        buf.writeBoolean(isOverride);
        buf.writeVarInt(customTags.size());
        for (Map.Entry<String, List<String>> entry : customTags.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (String item : entry.getValue()) buf.writeUtf(item);
        }
    }

    public static CreateRecipeJsonPacket fromBytes(FriendlyByteBuf buf) {
        String recipeId = buf.readUtf(32767);
        String recipeJson = buf.readUtf(32767);
        boolean isOverride = buf.readBoolean();
        int tagCount = buf.readVarInt();
        Map<String, List<String>> customTags = new java.util.HashMap<>();
        for (int i = 0; i < tagCount; i++) {
            String tagId = buf.readUtf(32767);
            int itemCount = buf.readVarInt();
            List<String> items = new java.util.ArrayList<>();
            for (int j = 0; j < itemCount; j++) items.add(buf.readUtf(32767));
            customTags.put(tagId, items);
        }
        return new CreateRecipeJsonPacket(recipeId, recipeJson, isOverride, customTags);
    }

    public static void handle(CreateRecipeJsonPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            try {
                // 验证权限
                if (context.getSender() == null || !context.getSender().hasPermissions(2)) {
                    if (context.getSender() != null) {
                        context.getSender().sendSystemMessage(Component.translatable(
                                "registerhelper.recipe.create.permission_denied")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                    }
                    return;
                }

                // 解析并验证JSON
                JsonObject recipeObj;
                try {
                    recipeObj = JsonParser.parseString(packet.recipeJson).getAsJsonObject();
                } catch (Exception e) {
                    context.getSender().sendSystemMessage(Component.translatable(
                            "registerhelper.recipe.json.invalid")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                    LOGGER.error("配方JSON解析失败: {}", packet.recipeId, e);
                    return;
                }

                ResourceLocation recipeIdLoc = new ResourceLocation(packet.recipeId);
                registerCustomTags(packet.customTags);
                boolean success;

                if (packet.isOverride) {
                    success = UnifiedRecipeOverrideManager.addOverride(recipeIdLoc, recipeObj);

                    if (success) {
                        context.getSender().sendSystemMessage(Component.translatable(
                                "registerhelper.recipe.override.success", packet.recipeId)
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                    } else {
                        context.getSender().sendSystemMessage(Component.translatable(
                                "registerhelper.recipe.override.failed")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                        LOGGER.warn("配方覆盖失败: {}", packet.recipeId);
                    }
                } else {
                    success = saveRecipeFile(recipeIdLoc, recipeObj);

                    if (success) {
                        context.getSender().sendSystemMessage(Component.translatable(
                                "registerhelper.recipe.create.success", packet.recipeId)
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                    } else {
                        context.getSender().sendSystemMessage(Component.translatable(
                                "registerhelper.recipe.create.failed")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                        LOGGER.warn("配方创建失败: {}", packet.recipeId);
                    }
                }

            } catch (Exception e) {
                LOGGER.error("处理配方JSON包时发生错误", e);
                if (context.getSender() != null) {
                    context.getSender().sendSystemMessage(Component.translatable(
                            "registerhelper.recipe.operation.failed", e.getMessage())
                            .withStyle(net.minecraft.ChatFormatting.RED));
                }
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * 保存配方文件到服务器（创建模式）
     */
    private static boolean saveRecipeFile(ResourceLocation recipeId, JsonObject recipeJson) {
        try {
            String namespace = recipeId.getNamespace();
            String path = recipeId.getPath();
            String baseFileName = generateOptimizedFileName(path, recipeJson);

            // 检查是否是自定义配方类型（酿造台、铁砧等）
            String recipeType = recipeJson.has("type") ? recipeJson.get("type").getAsString() : "";
            boolean isCustomType = isCustomRecipeType(recipeType);

            Path baseDir;
            if (isCustomType) {
                // 自定义配方保存到 custom_recipes 目录
                String customCategory = getCustomRecipeCategory(recipeType);
                baseDir = FMLPaths.CONFIGDIR.get()
                        .resolve("registerhelper/custom_recipes")
                        .resolve(customCategory);
            } else {
                // 普通配方保存到 recipes 目录
                baseDir = FMLPaths.CONFIGDIR.get()
                        .resolve("registerhelper/recipes")
                        .resolve(namespace);
            }

            // 创建目录
            Files.createDirectories(baseDir);

            Path existingPath = findExistingRecipeFile(recipeId);

            // 检查文件是否存在，如果存在则追加数字后缀
            String fileName = baseFileName;
            Path recipePath = baseDir.resolve(fileName + ".json");
            if (existingPath != null) {
                recipePath = existingPath;
            }
            int counter = 1;

            while (existingPath == null && Files.exists(recipePath)) {
                fileName = baseFileName + "_" + counter;
                recipePath = baseDir.resolve(fileName + ".json");
                counter++;
            }

            // 写入JSON文件
            try (FileWriter writer = new FileWriter(recipePath.toFile())) {
                GSON.toJson(recipeJson, writer);
            }

            LOGGER.info(existingPath == null ? "配方已保存: {} -> {}" : "配方已更新: {} -> {}",
                    recipeId, recipePath);
            return true;

        } catch (Exception e) {
            LOGGER.error("保存配方文件失败: {}", recipeId, e);
            return false;
        }
    }

    private static void registerCustomTags(Map<String, List<String>> customTags) {
        for (Map.Entry<String, List<String>> entry : customTags.entrySet()) {
            try {
                ResourceLocation tagId = new ResourceLocation(entry.getKey());
                List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
                for (String itemId : entry.getValue()) {
                    var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                            new ResourceLocation(itemId));
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        stacks.add(new net.minecraft.world.item.ItemStack(item));
                    }
                }
                if (!stacks.isEmpty()) CustomTagManager.registerTag(tagId, stacks);
            } catch (Exception e) {
                LOGGER.warn("同步自定义标签失败: {}", entry.getKey(), e);
            }
        }
    }

    private static Path findExistingRecipeFile(ResourceLocation recipeId) {
        Path recipesRoot = FMLPaths.CONFIGDIR.get().resolve("registerhelper/recipes");
        Path namespaceDir = recipesRoot.resolve(recipeId.getNamespace());
        Path exact = findByRecipePath(namespaceDir, recipeId.getPath(), namespaceDir);
        if (exact != null) return exact;

        Path customRoot = FMLPaths.CONFIGDIR.get().resolve("registerhelper/custom_recipes");
        if (Files.exists(customRoot)) {
            try (var paths = Files.walk(customRoot)) {
                return paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .filter(path -> path.getFileName().toString()
                                .equals(recipeId.getPath() + ".json"))
                        .findFirst().orElse(null);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static Path findByRecipePath(Path namespaceDir, String recipePath, Path root) {
        if (!Files.exists(namespaceDir)) return null;
        try (var paths = Files.walk(namespaceDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> {
                        String relative = root.relativize(path).toString();
                        relative = relative.substring(0, relative.length() - 5)
                                .replace(java.io.File.separatorChar, '_');
                        return relative.equals(recipePath);
                    })
                    .findFirst().orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 检查是否是自定义配方类型
     */
    private static boolean isCustomRecipeType(String type) {
        return type.contains("brewing") || type.contains("anvil") ||
                type.equals("registerhelper:brewing") || type.equals("registerhelper:anvil");
    }

    /**
     * 获取自定义配方的分类目录
     */
    private static String getCustomRecipeCategory(String type) {
        if (type.contains("brewing")) {
            return "brewing";
        } else if (type.contains("anvil")) {
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

        // ResourceLocation path 只允许 [a-z0-9/._-]
        // 将空格和其他非法字符替换为下划线，并转换为小写
        result = result.toLowerCase()
                       .replaceAll("[^a-z0-9/._-]", "_")
                       .replaceAll("_+", "_")   // 合并连续下划线
                       .replaceAll("_$", "");    // 去掉末尾下划线

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
            
            // TACZ特殊格式：item是对象 {"item": {"item": "..."}}
            if (obj.has("item")) {
                JsonElement itemElement = obj.get("item");
                if (itemElement.isJsonPrimitive()) {
                    // 旧兼容 - item是字符串
                    return itemElement.getAsString();
                } else if (itemElement.isJsonObject()) {
                    // TACZ格式 - item是对象
                    JsonObject itemObj = itemElement.getAsJsonObject();
                    if (itemObj.has("item")) {
                        return itemObj.get("item").getAsString();
                    }
                    if (itemObj.has("tag")) {
                        return "#" + itemObj.get("tag").getAsString();
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
