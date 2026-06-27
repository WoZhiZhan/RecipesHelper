package com.wzz.registerhelper.network;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.ModMain;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求配方列表的网络包
 * 客户端 -> 服务器
 *
 * NeoForge 1.21.1 版本：使用 CustomPacketPayload + StreamCodec
 */
public record RequestRecipeListPacket(int requestType) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 请求类型: 0=所有配方, 1=可编辑配方(排除黑名单)

    public static final Type<RequestRecipeListPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "request_recipe_list")
            );

    public static final StreamCodec<ByteBuf, RequestRecipeListPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            RequestRecipeListPacket::requestType,
            RequestRecipeListPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理数据包（服务器端）
     */
    public static void handle(RequestRecipeListPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                LOGGER.warn("收到配方列表请求，但发送者不是服务器玩家");
                return;
            }

            MinecraftServer server = player.getServer();
            if (server == null) {
                LOGGER.warn("无法获取服务器实例");
                return;
            }

            try {
                List<UnifiedRecipeInfo> recipes = collectRecipes(server, packet.requestType());
                LOGGER.info("为玩家 {} 收集了 {} 个配方", player.getName().getString(), recipes.size());

                // 发送配方列表给客户端（可能需要分包）
                sendRecipesToClient(player, recipes);

            } catch (Exception e) {
                LOGGER.error("收集配方列表时出错", e);
            }
        }).exceptionally(e -> {
            LOGGER.error("处理配方列表请求异步错误", e);
            return null;
        });
    }

    /**
     * 从服务器收集配方列表
     */
    private static List<UnifiedRecipeInfo> collectRecipes(MinecraftServer server, int requestType) {
        List<UnifiedRecipeInfo> recipes = new ArrayList<>();

        RecipeManager recipeManager = server.getRecipeManager();

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            ResourceLocation id = holder.id();
            boolean isBlacklisted = RecipeBlacklistManager.isBlacklisted(id);
            boolean hasOverride = UnifiedRecipeOverrideManager.hasOverride(id);

            // 如果请求类型是1，跳过黑名单配方
            if (requestType == 1 && isBlacklisted) {
                continue;
            }

            String source = determineRecipeSource(id);
            String description;
            try {
                description = holder.value().getType().toString() + " -> " +
                        holder.value().getResultItem(server.registryAccess()).getHoverName().getString();
            } catch (Exception e) {
                description = holder.value().getType().toString() + " -> ?";
            }

            recipes.add(new UnifiedRecipeInfo(id, source, isBlacklisted, hasOverride, description));
        }

        // 排序
        recipes.sort((a, b) -> {
            int blacklistCompare = Boolean.compare(a.isBlacklisted, b.isBlacklisted);
            if (blacklistCompare != 0) return blacklistCompare;

            int overrideCompare = Boolean.compare(b.hasOverride, a.hasOverride);
            if (overrideCompare != 0) return overrideCompare;

            int sourceCompare = a.source.compareTo(b.source);
            if (sourceCompare != 0) return sourceCompare;
            return a.id.toString().compareTo(b.id.toString());
        });

        return recipes;
    }

    /**
     * 确定配方来源
     */
    private static String determineRecipeSource(ResourceLocation recipeId) {
        String namespace = recipeId.getNamespace();
        String path = recipeId.getPath();

        if (namespace.equals("registerhelper") || path.startsWith("custom_") || path.contains("_custom_")) {
            return "自定义";
        }

        if (namespace.equals("minecraft")) {
            return "原版";
        }

        return "模组(" + namespace + ")";
    }

    /**
     * 发送配方列表给客户端（分包处理大量数据）
     */
    private static void sendRecipesToClient(ServerPlayer player, List<UnifiedRecipeInfo> recipes) {
        // 每包最多发送的配方数量
        final int BATCH_SIZE = 100;

        int totalRecipes = recipes.size();
        int totalBatches = (totalRecipes + BATCH_SIZE - 1) / BATCH_SIZE;

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int start = batchIndex * BATCH_SIZE;
            int end = Math.min(start + BATCH_SIZE, totalRecipes);

            List<UnifiedRecipeInfo> batch = new ArrayList<>(recipes.subList(start, end));

            SyncRecipeListPacket packet = new SyncRecipeListPacket(
                    batch,
                    batchIndex,
                    totalBatches,
                    totalRecipes
            );

            PacketDistributor.sendToPlayer(player, packet);
        }

        LOGGER.debug("已发送 {} 批配方数据给玩家 {}", totalBatches, player.getName().getString());
    }

    /**
     * 客户端调用，发送请求到服务器
     */
    public static void sendToServer() {
        sendToServer(0);
    }

    /**
     * 客户端调用，发送请求到服务器
     * @param requestType 0=所有配方, 1=可编辑配方
     */
    public static void sendToServer(int requestType) {
        PacketDistributor.sendToServer(new RequestRecipeListPacket(requestType));
    }
}
