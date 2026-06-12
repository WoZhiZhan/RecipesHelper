package com.wzz.registerhelper.network;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.util.RecipeReloadHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 配方黑名单操作网络包
 * 客户端 → 服务器
 */
public class RecipeBlacklistPacket {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public enum Operation {
        ADD,          // 添加到黑名单
        REMOVE,       // 从黑名单移除
        CLEAR,        // 清空黑名单
        ADD_BATCH,    // 批量添加到黑名单
        REMOVE_BATCH, // 批量从黑名单移除
    }
    
    private final Operation operation;
    private final String recipeId; // 对于 ADD/REMOVE 操作
    private final java.util.List<String> recipeIds; // 对于 ADD_BATCH/REMOVE_BATCH 操作
    
    public RecipeBlacklistPacket(Operation operation, String recipeId) {
        this.operation = operation;
        this.recipeId = recipeId != null ? recipeId : "";
        this.recipeIds = java.util.Collections.emptyList();
    }
    
    public RecipeBlacklistPacket(Operation operation) {
        this(operation, "");
    }

    public RecipeBlacklistPacket(Operation operation, java.util.Collection<String> recipeIds) {
        this.operation = operation;
        this.recipeId = "";
        this.recipeIds = new java.util.ArrayList<>(recipeIds != null ? recipeIds : java.util.Collections.emptyList());
    }
    
    // 编码
    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(operation);
        buf.writeUtf(recipeId);
        buf.writeVarInt(recipeIds.size());
        for (String id : recipeIds) {
            buf.writeUtf(id);
        }
    }
    
    // 解码
    public static RecipeBlacklistPacket decode(FriendlyByteBuf buf) {
        Operation operation = buf.readEnum(Operation.class);
        String recipeId = buf.readUtf();
        int size = buf.readVarInt();
        java.util.List<String> ids = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readUtf());
        }
        if (operation == Operation.ADD_BATCH || operation == Operation.REMOVE_BATCH) {
            return new RecipeBlacklistPacket(operation, ids);
        }
        return new RecipeBlacklistPacket(operation, recipeId);
    }
    
    // 处理（服务端）
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                LOGGER.warn("收到黑名单操作包但发送者为空");
                return;
            }
            
            // 检查权限（需要OP权限）
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c你没有权限执行此操作"));
                LOGGER.warn("玩家 {} 尝试操作黑名单但没有权限", player.getName().getString());
                return;
            }
            
            switch (operation) {
                case ADD -> handleAdd(player);
                case REMOVE -> handleRemove(player);
                case CLEAR -> handleClear(player);
                case ADD_BATCH -> handleAddBatch(player);
                case REMOVE_BATCH -> handleRemoveBatch(player);
            }
        });
        context.setPacketHandled(true);
    }
    
    private void handleAdd(ServerPlayer player) {
        if (recipeId.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c配方ID为空"));
            return;
        }
        
        try {
            ResourceLocation id = new ResourceLocation(recipeId);
            boolean success = RecipeBlacklistManager.addToBlacklist(id);
            
            if (success) {
                player.sendSystemMessage(Component.literal("§a配方已添加到黑名单: " + recipeId));
                LOGGER.info("玩家 {} 将配方 {} 添加到黑名单", player.getName().getString(), recipeId);
            } else {
                player.sendSystemMessage(Component.literal("§e配方已在黑名单中: " + recipeId));
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§c无效的配方ID: " + recipeId));
            LOGGER.error("添加配方到黑名单失败: {}", recipeId, e);
        }
    }
    
    private void handleRemove(ServerPlayer player) {
        if (recipeId.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c配方ID为空"));
            return;
        }
        
        try {
            ResourceLocation id = new ResourceLocation(recipeId);
            boolean success = RecipeBlacklistManager.removeFromBlacklist(id);
            
            if (success) {
                player.sendSystemMessage(Component.literal("§a配方已从黑名单移除: " + recipeId));
                LOGGER.info("玩家 {} 将配方 {} 从黑名单移除", player.getName().getString(), recipeId);
            } else {
                player.sendSystemMessage(Component.literal("§e配方不在黑名单中: " + recipeId));
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§c无效的配方ID: " + recipeId));
            LOGGER.error("从黑名单移除配方失败: {}", recipeId, e);
        }
    }
    
    private void handleAddBatch(ServerPlayer player) {
        if (recipeIds.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c批量添加列表为空"));
            return;
        }
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        for (String s : recipeIds) {
            try {
                ids.add(new ResourceLocation(s));
            } catch (Exception e) {
                LOGGER.warn("无效的配方ID: {}", s);
            }
        }
        int added = RecipeBlacklistManager.addMultipleToBlacklist(ids);
        player.sendSystemMessage(Component.literal("§a批量添加完成: 新增 " + added + " 个，提交 " + ids.size() + " 个"));
        LOGGER.info("玩家 {} 批量添加 {} 个配方到黑名单", player.getName().getString(), added);
    }

    private void handleRemoveBatch(ServerPlayer player) {
        if (recipeIds.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c批量移除列表为空"));
            return;
        }
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        for (String s : recipeIds) {
            try {
                ids.add(new ResourceLocation(s));
            } catch (Exception e) {
                LOGGER.warn("无效的配方ID: {}", s);
            }
        }
        int removed = RecipeBlacklistManager.removeMultipleFromBlacklist(ids);
        player.sendSystemMessage(Component.literal("§a批量移除完成: 移除 " + removed + " 个，提交 " + ids.size() + " 个"));
        LOGGER.info("玩家 {} 批量移除 {} 个黑名单配方", player.getName().getString(), removed);
    }

    private void handleClear(ServerPlayer player) {
        int count = RecipeBlacklistManager.getBlacklistedRecipes().size();
        boolean success = RecipeBlacklistManager.clearBlacklist();
        
        if (success) {
            player.sendSystemMessage(Component.literal("§a黑名单已清空，移除了 " + count + " 个配方"));
            LOGGER.info("玩家 {} 清空了黑名单（{} 个配方）", player.getName().getString(), count);
        } else {
            player.sendSystemMessage(Component.literal("§c清空黑名单失败"));
        }
    }
    
    private void handleReload(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            player.sendSystemMessage(Component.literal("§c无法获取服务器实例"));
            return;
        }
        
        // 如果是专用服务器，使用 handleConsoleInput（模拟终端输入）
        if (server instanceof net.minecraft.server.dedicated.DedicatedServer dedicatedServer) {
            dedicatedServer.handleConsoleInput("reload", server.createCommandSourceStack());
            LOGGER.info("玩家 {} 通过 handleConsoleInput 触发了配方重载", player.getName().getString());
        } else {
            // 集成服务器（单人游戏）
            server.execute(() -> {
                server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    "reload"
                );
            });
            LOGGER.info("玩家 {} 触发了配方重载", player.getName().getString());
        }
    }
}