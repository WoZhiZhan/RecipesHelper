package com.wzz.registerhelper.network;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 同步配方列表的网络包
 * 服务器 -> 客户端
 */
public class SyncRecipeListPacket {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 用于收集分包数据
    private static final ConcurrentHashMap<Integer, List<UnifiedRecipeInfo>> batchBuffer = new ConcurrentHashMap<>();
    private static volatile int expectedBatches = 0;
    private static volatile int receivedBatches = 0;
    private static volatile int expectedTotal = 0;
    
    private final List<UnifiedRecipeInfo> recipes;
    private final int batchIndex;      // 当前批次索引
    private final int totalBatches;    // 总批次数
    private final int totalRecipes;    // 总配方数
    
    public SyncRecipeListPacket(List<UnifiedRecipeInfo> recipes, int batchIndex, int totalBatches, int totalRecipes) {
        this.recipes = recipes;
        this.batchIndex = batchIndex;
        this.totalBatches = totalBatches;
        this.totalRecipes = totalRecipes;
    }
    
    public static void encode(SyncRecipeListPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.batchIndex);
        buf.writeInt(packet.totalBatches);
        buf.writeInt(packet.totalRecipes);
        buf.writeInt(packet.recipes.size());
        
        for (UnifiedRecipeInfo info : packet.recipes) {
            buf.writeResourceLocation(info.id);
            buf.writeUtf(info.source);
            buf.writeBoolean(info.isBlacklisted);
            buf.writeBoolean(info.hasOverride);
            buf.writeUtf(info.description);
        }
    }
    
    public static SyncRecipeListPacket decode(FriendlyByteBuf buf) {
        int batchIndex = buf.readInt();
        int totalBatches = buf.readInt();
        int totalRecipes = buf.readInt();
        int count = buf.readInt();
        
        List<UnifiedRecipeInfo> recipes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation id = buf.readResourceLocation();
            String source = buf.readUtf();
            boolean isBlacklisted = buf.readBoolean();
            boolean hasOverride = buf.readBoolean();
            String description = buf.readUtf();
            
            recipes.add(new UnifiedRecipeInfo(id, source, isBlacklisted, hasOverride, description));
        }
        
        return new SyncRecipeListPacket(recipes, batchIndex, totalBatches, totalRecipes);
    }
    
    public static void handle(SyncRecipeListPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                handleOnClient(packet);
            }
        });
        context.setPacketHandled(true);
    }
    
    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient(SyncRecipeListPacket packet) {
        try {
            // 如果是第一批，重置状态
            if (packet.batchIndex == 0) {
                batchBuffer.clear();
                receivedBatches = 0;
                expectedBatches = packet.totalBatches;
                expectedTotal = packet.totalRecipes;
                LOGGER.debug("开始接收配方数据，预期 {} 批，共 {} 个配方", 
                    packet.totalBatches, packet.totalRecipes);
            }
            
            // 存储当前批次
            batchBuffer.put(packet.batchIndex, new ArrayList<>(packet.recipes));
            receivedBatches++;
            
            LOGGER.debug("收到配方批次 {}/{}, 本批 {} 个配方", 
                packet.batchIndex + 1, packet.totalBatches, packet.recipes.size());
            
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