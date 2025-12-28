package com.wzz.registerhelper.network;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.info.UnifiedRecipeInfo;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 请求配方列表的网络包
 * 客户端 -> 服务器
 */
public class RequestRecipeListPacket {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 请求类型: 0=所有配方, 1=可编辑配方(排除黑名单)
    private final int requestType;
    
    public RequestRecipeListPacket() {
        this(0);
    }
    
    public RequestRecipeListPacket(int requestType) {
        this.requestType = requestType;
    }
    
    public static void encode(RequestRecipeListPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.requestType);
    }
    
    public static RequestRecipeListPacket decode(FriendlyByteBuf buf) {
        return new RequestRecipeListPacket(buf.readInt());
    }
    
    public static void handle(RequestRecipeListPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                LOGGER.warn("收到配方列表请求，但发送者为空");
                return;
            }
            
            MinecraftServer server = player.getServer();
            if (server == null) {
                LOGGER.warn("无法获取服务器实例");
                return;
            }
            
            try {
                List<UnifiedRecipeInfo> recipes = collectRecipes(server, packet.requestType);
                LOGGER.info("为玩家 {} 收集了 {} 个配方", player.getName().getString(), recipes.size());
                
                // 发送配方列表给客户端（可能需要分包）
                sendRecipesToClient(player, recipes);
                
            } catch (Exception e) {
                LOGGER.error("收集配方列表时出错", e);
            }
        });
        context.setPacketHandled(true);
    }
    
    /**
     * 从服务器收集配方列表
     */
    private static List<UnifiedRecipeInfo> collectRecipes(MinecraftServer server, int requestType) {
        List<UnifiedRecipeInfo> recipes = new ArrayList<>();
        
        RecipeManager recipeManager = server.getRecipeManager();
        
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            ResourceLocation id = recipe.getId();
            boolean isBlacklisted = RecipeBlacklistManager.isBlacklisted(id);
            boolean hasOverride = UnifiedRecipeOverrideManager.hasOverride(id);
            
            // 如果请求类型是1，跳过黑名单配方
            if (requestType == 1 && isBlacklisted) {
                continue;
            }
            
            String source = determineRecipeSource(id);
            String description;
            try {
                description = recipe.getType().toString() + " -> " + 
                    recipe.getResultItem(server.registryAccess()).getHoverName().getString();
            } catch (Exception e) {
                description = recipe.getType().toString() + " -> ?";
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
            
            List<UnifiedRecipeInfo> batch = recipes.subList(start, end);
            
            SyncRecipeListPacket packet = new SyncRecipeListPacket(
                batch,
                batchIndex,
                totalBatches,
                totalRecipes
            );
            
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
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
        ModNetwork.CHANNEL.sendToServer(new RequestRecipeListPacket(requestType));
    }
}