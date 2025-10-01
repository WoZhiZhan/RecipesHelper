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
                    // 覆盖模式：使用 UnifiedRecipeOverrideManager
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
                    // 创建模式：保存到 recipes 目录
                    success = saveRecipeFile(recipeIdLoc, recipeObj);

                    if (success) {
                        context.getSender().sendSystemMessage(
                                Component.literal("§a配方创建成功: " + packet.recipeId + " 使用 /reload 刷新配方")
                        );
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
     * 保存配方文件到服务器（创建模式）
     */
    private static boolean saveRecipeFile(ResourceLocation recipeId, JsonObject recipeJson) {
        try {
            String namespace = recipeId.getNamespace();
            String path = recipeId.getPath();

            // 保存到 recipes 目录
            Path recipePath = FMLPaths.GAMEDIR.get()
                    .resolve("config/registerhelper/recipes")
                    .resolve(namespace)
                    .resolve(path + ".json");

            // 创建目录
            Files.createDirectories(recipePath.getParent());

            // 写入JSON文件
            try (FileWriter writer = new FileWriter(recipePath.toFile())) {
                GSON.toJson(recipeJson, writer);
            }

            LOGGER.info("配方已保存: {} -> {}", recipeId, recipePath);
            return true;

        } catch (Exception e) {
            LOGGER.error("保存配方文件失败: {}", recipeId, e);
            return false;
        }
    }
}