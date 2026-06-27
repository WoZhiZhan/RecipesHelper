package com.wzz.registerhelper.network;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.ModMain;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同步配方列表的网络包
 * 服务器 -> 客户端
 *
 * NeoForge 1.21.1 版本：使用 CustomPacketPayload + StreamCodec
 */
public record SyncRecipeListPacket(
        List<UnifiedRecipeInfo> recipes,
        int batchIndex,      // 当前批次索引
        int totalBatches,    // 总批次数
        int totalRecipes     // 总配方数
) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    // 用于收集分包数据
    private static final ConcurrentHashMap<Integer, List<UnifiedRecipeInfo>> batchBuffer = new ConcurrentHashMap<>();
    private static volatile int expectedBatches = 0;
    private static volatile int receivedBatches = 0;
    private static volatile int expectedTotal = 0;

    public static final Type<SyncRecipeListPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "sync_recipe_list")
            );

    /**
     * 单个 UnifiedRecipeInfo 的 StreamCodec
     */
    private static final StreamCodec<ByteBuf, UnifiedRecipeInfo> INFO_CODEC =
            new StreamCodec<>() {
                @Override
                public UnifiedRecipeInfo decode(ByteBuf buf) {
                    ResourceLocation id = ResourceLocation.parse(ByteBufCodecs.STRING_UTF8.decode(buf));
                    String source = ByteBufCodecs.STRING_UTF8.decode(buf);
                    boolean isBlacklisted = ByteBufCodecs.BOOL.decode(buf);
                    boolean hasOverride = ByteBufCodecs.BOOL.decode(buf);
                    String description = ByteBufCodecs.STRING_UTF8.decode(buf);
                    return new UnifiedRecipeInfo(id, source, isBlacklisted, hasOverride, description);
                }

                @Override
                public void encode(ByteBuf buf, UnifiedRecipeInfo info) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, info.id.toString());
                    ByteBufCodecs.STRING_UTF8.encode(buf, info.source);
                    ByteBufCodecs.BOOL.encode(buf, info.isBlacklisted);
                    ByteBufCodecs.BOOL.encode(buf, info.hasOverride);
                    ByteBufCodecs.STRING_UTF8.encode(buf, info.description);
                }
            };

    private static final StreamCodec<ByteBuf, List<UnifiedRecipeInfo>> LIST_CODEC =
            INFO_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, SyncRecipeListPacket> STREAM_CODEC = StreamCodec.composite(
            LIST_CODEC,
            SyncRecipeListPacket::recipes,
            ByteBufCodecs.VAR_INT,
            SyncRecipeListPacket::batchIndex,
            ByteBufCodecs.VAR_INT,
            SyncRecipeListPacket::totalBatches,
            ByteBufCodecs.VAR_INT,
            SyncRecipeListPacket::totalRecipes,
            SyncRecipeListPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncRecipeListPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                handleOnClient(packet);
            }
        }).exceptionally(e -> {
            LOGGER.error("处理配方同步包异步错误", e);
            return null;
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient(SyncRecipeListPacket packet) {
        try {
            // 如果是第一批，重置状态
            if (packet.batchIndex() == 0) {
                batchBuffer.clear();
                receivedBatches = 0;
                expectedBatches = packet.totalBatches();
                expectedTotal = packet.totalRecipes();
                LOGGER.debug("开始接收配方数据，预期 {} 批，共 {} 个配方",
                        packet.totalBatches(), packet.totalRecipes());
            }

            // 存储当前批次
            batchBuffer.put(packet.batchIndex(), new ArrayList<>(packet.recipes()));
            receivedBatches++;

            LOGGER.debug("收到配方批次 {}/{}, 本批 {} 个配方",
                    packet.batchIndex() + 1, packet.totalBatches(), packet.recipes().size());

            // 检查是否所有批次都已收到
            if (receivedBatches >= expectedBatches) {
                // 合并所有批次
                List<UnifiedRecipeInfo> allRecipes = new ArrayList<>(expectedTotal);
                for (int i = 0; i < expectedBatches; i++) {
                    List<UnifiedRecipeInfo> batch = batchBuffer.get(i);
                    if (batch != null) {
                        allRecipes.addAll(batch);
                    }
                }

                LOGGER.info("配方数据接收完成，共 {} 个配方", allRecipes.size());

                // 更新客户端缓存
                RecipeClientCache.setRecipes(allRecipes);

                // 清理缓冲区
                batchBuffer.clear();
            }

        } catch (Exception e) {
            LOGGER.error("处理配方同步数据时出错", e);
            RecipeClientCache.setError("处理配方数据时出错: " + e.getMessage());
        }
    }

    /**
     * 获取当前接收进度 (0.0 - 1.0)
     */
    public static float getProgress() {
        if (expectedBatches == 0) return 0;
        return (float) receivedBatches / expectedBatches;
    }

    /**
     * 是否正在接收数据
     */
    public static boolean isReceiving() {
        return receivedBatches > 0 && receivedBatches < expectedBatches;
    }
}
