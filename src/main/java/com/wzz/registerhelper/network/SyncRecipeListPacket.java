package com.wzz.registerhelper.network;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.ModMain;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        int totalRecipes,    // 总配方数
        int requestId        // 客户端请求代次
) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object BATCH_LOCK = new Object();
    private static BatchState batchState;

    public SyncRecipeListPacket {
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
    }

    public SyncRecipeListPacket(List<UnifiedRecipeInfo> recipes, int batchIndex,
                                int totalBatches, int totalRecipes) {
        this(recipes, batchIndex, totalBatches, totalRecipes, 0);
    }

    public static final Type<SyncRecipeListPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "sync_recipe_list")
            );

    /**
     * 单个 UnifiedRecipeInfo 的 StreamCodec
     */
    private static final StreamCodec<ByteBuf, String> RECIPE_ID_CODEC =
            NetworkProtocolLimits.string(NetworkProtocolLimits.MAX_RECIPE_ID_LENGTH);
    private static final StreamCodec<ByteBuf, String> SOURCE_CODEC =
            NetworkProtocolLimits.string(NetworkProtocolLimits.MAX_SOURCE_LENGTH);
    private static final StreamCodec<ByteBuf, String> DESCRIPTION_CODEC =
            NetworkProtocolLimits.string(NetworkProtocolLimits.MAX_DESCRIPTION_LENGTH);

    private static final StreamCodec<ByteBuf, UnifiedRecipeInfo> INFO_CODEC =
            new StreamCodec<>() {
                @Override
                public UnifiedRecipeInfo decode(ByteBuf buf) {
                    ResourceLocation id = ResourceLocation.parse(RECIPE_ID_CODEC.decode(buf));
                    String source = SOURCE_CODEC.decode(buf);
                    boolean isBlacklisted = ByteBufCodecs.BOOL.decode(buf);
                    boolean hasOverride = ByteBufCodecs.BOOL.decode(buf);
                    String description = DESCRIPTION_CODEC.decode(buf);
                    return new UnifiedRecipeInfo(id, source, isBlacklisted, hasOverride, description);
                }

                @Override
                public void encode(ByteBuf buf, UnifiedRecipeInfo info) {
                    RECIPE_ID_CODEC.encode(buf, info.id.toString());
                    SOURCE_CODEC.encode(buf, info.source);
                    ByteBufCodecs.BOOL.encode(buf, info.isBlacklisted);
                    ByteBufCodecs.BOOL.encode(buf, info.hasOverride);
                    DESCRIPTION_CODEC.encode(buf, info.description);
                }
            };

    private static final StreamCodec<ByteBuf, List<UnifiedRecipeInfo>> LIST_CODEC =
            NetworkProtocolLimits.list(INFO_CODEC, NetworkProtocolLimits.RECIPE_BATCH_SIZE);

    public static final StreamCodec<ByteBuf, SyncRecipeListPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncRecipeListPacket decode(ByteBuf buf) {
            List<UnifiedRecipeInfo> recipes = LIST_CODEC.decode(buf);
            int batchIndex = NetworkProtocolLimits.decodeNonNegative(
                    buf, "batchIndex", NetworkProtocolLimits.MAX_RECIPE_BATCHES - 1);
            int totalBatches = NetworkProtocolLimits.decodeNonNegative(
                    buf, "totalBatches", NetworkProtocolLimits.MAX_RECIPE_BATCHES);
            int totalRecipes = NetworkProtocolLimits.decodeNonNegative(
                    buf, "totalRecipes", NetworkProtocolLimits.MAX_TOTAL_RECIPES);
            int requestId = NetworkProtocolLimits.decodeNonNegative(buf, "requestId", Integer.MAX_VALUE);
            validateMetadata(recipes, batchIndex, totalBatches, totalRecipes, true);
            return new SyncRecipeListPacket(recipes, batchIndex, totalBatches, totalRecipes, requestId);
        }

        @Override
        public void encode(ByteBuf buf, SyncRecipeListPacket packet) {
            validateMetadata(packet.recipes(), packet.batchIndex(), packet.totalBatches(),
                    packet.totalRecipes(), false);
            LIST_CODEC.encode(buf, packet.recipes());
            NetworkProtocolLimits.encodeNonNegative(buf, "batchIndex", packet.batchIndex(),
                    NetworkProtocolLimits.MAX_RECIPE_BATCHES - 1);
            NetworkProtocolLimits.encodeNonNegative(buf, "totalBatches", packet.totalBatches(),
                    NetworkProtocolLimits.MAX_RECIPE_BATCHES);
            NetworkProtocolLimits.encodeNonNegative(buf, "totalRecipes", packet.totalRecipes(),
                    NetworkProtocolLimits.MAX_TOTAL_RECIPES);
            NetworkProtocolLimits.encodeNonNegative(buf, "requestId", packet.requestId(), Integer.MAX_VALUE);
        }
    };

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
            validateMetadata(packet.recipes(), packet.batchIndex(), packet.totalBatches(),
                    packet.totalRecipes(), false);

            if (!RecipeClientCache.isCurrentRequest(packet.requestId())) {
                LOGGER.debug("忽略过期的配方同步请求: {}", packet.requestId());
                return;
            }

            List<UnifiedRecipeInfo> completedRecipes = null;
            String syncError = null;

            synchronized (BATCH_LOCK) {
                if (batchState == null || batchState.requestId != packet.requestId()) {
                    batchState = new BatchState(packet.requestId(), packet.totalBatches(), packet.totalRecipes());
                    LOGGER.debug("开始接收配方数据，请求 {}，预期 {} 批，共 {} 个配方",
                            packet.requestId(), packet.totalBatches(), packet.totalRecipes());
                } else if (batchState.completed) {
                    return;
                } else if (batchState.expectedBatches != packet.totalBatches()
                        || batchState.expectedTotal != packet.totalRecipes()) {
                    syncError = "配方同步批次元数据不一致";
                    batchState = null;
                }

                if (syncError == null) {
                    boolean added = batchState.batches.putIfAbsent(
                            packet.batchIndex(), new ArrayList<>(packet.recipes())) == null;
                    if (added) {
                        LOGGER.debug("收到配方批次 {}/{}, 本批 {} 个配方",
                                packet.batchIndex() + 1, packet.totalBatches(), packet.recipes().size());
                    }

                    if (batchState.batches.size() == batchState.expectedBatches) {
                        List<UnifiedRecipeInfo> allRecipes = new ArrayList<>(batchState.expectedTotal);
                        for (int i = 0; i < batchState.expectedBatches; i++) {
                            List<UnifiedRecipeInfo> batch = batchState.batches.get(i);
                            if (batch == null) {
                                syncError = "配方同步缺少批次 " + i;
                                break;
                            }
                            allRecipes.addAll(batch);
                        }

                        if (syncError == null && allRecipes.size() != batchState.expectedTotal) {
                            syncError = "配方同步数量不匹配: expected=" + batchState.expectedTotal
                                    + ", actual=" + allRecipes.size();
                        }

                        if (syncError == null) {
                            batchState.completed = true;
                            completedRecipes = allRecipes;
                        } else {
                            batchState = null;
                        }
                    }
                }
            }

            if (syncError != null) {
                RecipeClientCache.setError(packet.requestId(), syncError);
            } else if (completedRecipes != null) {
                LOGGER.info("配方数据接收完成，共 {} 个配方", completedRecipes.size());
                RecipeClientCache.setRecipes(packet.requestId(), completedRecipes);
            }

        } catch (Exception e) {
            LOGGER.error("处理配方同步数据时出错", e);
            RecipeClientCache.setError(packet.requestId(), Component.translatable(
                    "registerhelper.recipe.sync.failed", e.getMessage()).getString());
        }
    }

    /**
     * 获取当前接收进度 (0.0 - 1.0)
     */
    public static float getProgress() {
        synchronized (BATCH_LOCK) {
            if (batchState == null || batchState.expectedBatches == 0) {
                return 0;
            }
            return (float) batchState.batches.size() / batchState.expectedBatches;
        }
    }

    /**
     * 是否正在接收数据
     */
    public static boolean isReceiving() {
        synchronized (BATCH_LOCK) {
            return batchState != null
                    && !batchState.completed
                    && !batchState.batches.isEmpty()
                    && batchState.batches.size() < batchState.expectedBatches;
        }
    }

    static void resetClientState() {
        synchronized (BATCH_LOCK) {
            batchState = null;
        }
    }

    private static void validateMetadata(List<UnifiedRecipeInfo> recipes, int batchIndex,
                                         int totalBatches, int totalRecipes, boolean decoding) {
        String error = null;
        if (recipes == null || recipes.size() > NetworkProtocolLimits.RECIPE_BATCH_SIZE) {
            error = "Recipe batch size is invalid";
        } else if (totalBatches < 1 || totalBatches > NetworkProtocolLimits.MAX_RECIPE_BATCHES) {
            error = "totalBatches is outside the allowed range: " + totalBatches;
        } else if (batchIndex < 0 || batchIndex >= totalBatches) {
            error = "batchIndex is outside the allowed range: " + batchIndex;
        } else if (totalRecipes < 0 || totalRecipes > NetworkProtocolLimits.MAX_TOTAL_RECIPES) {
            error = "totalRecipes is outside the allowed range: " + totalRecipes;
        } else if (recipes.size() > totalRecipes) {
            error = "Recipe batch is larger than totalRecipes";
        }

        if (error != null) {
            if (decoding) {
                throw new io.netty.handler.codec.DecoderException(error);
            }
            throw new IllegalArgumentException(error);
        }
    }

    private static final class BatchState {
        private final int requestId;
        private final int expectedBatches;
        private final int expectedTotal;
        private final Map<Integer, List<UnifiedRecipeInfo>> batches = new HashMap<>();
        private boolean completed;

        private BatchState(int requestId, int expectedBatches, int expectedTotal) {
            this.requestId = requestId;
            this.expectedBatches = expectedBatches;
            this.expectedTotal = expectedTotal;
        }
    }
}
