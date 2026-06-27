package com.wzz.registerhelper.network;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.ModMain;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/**
 * 配方黑名单操作网络包
 * 客户端 → 服务器
 *
 * NeoForge 1.21.1 版本：使用 CustomPacketPayload + StreamCodec
 */
public record RecipeBlacklistPacket(
        Operation operation,
        String recipeId,                   // 对于 ADD/REMOVE 操作
        java.util.List<String> recipeIds   // 对于 ADD_BATCH/REMOVE_BATCH 操作
) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Operation {
        ADD,          // 添加到黑名单
        REMOVE,       // 从黑名单移除
        CLEAR,        // 清空黑名单
        ADD_BATCH,    // 批量添加到黑名单
        REMOVE_BATCH, // 批量从黑名单移除
    }

    public RecipeBlacklistPacket(Operation operation, String recipeId) {
        this(operation, recipeId != null ? recipeId : "", java.util.Collections.emptyList());
    }

    public RecipeBlacklistPacket(Operation operation) {
        this(operation, "");
    }

    public RecipeBlacklistPacket(Operation operation, java.util.Collection<String> recipeIds) {
        this(operation, "",
                new java.util.ArrayList<>(recipeIds != null ? recipeIds : java.util.Collections.emptyList()));
    }

    public static final Type<RecipeBlacklistPacket> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "recipe_blacklist")
            );

    private static final StreamCodec<ByteBuf, java.util.List<String>> STRING_LIST_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, RecipeBlacklistPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RecipeBlacklistPacket decode(ByteBuf buf) {
                    Operation operation = Operation.values()[ByteBufCodecs.VAR_INT.decode(buf)];
                    String recipeId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    java.util.List<String> ids = STRING_LIST_CODEC.decode(buf);
                    if (operation == Operation.ADD_BATCH || operation == Operation.REMOVE_BATCH) {
                        return new RecipeBlacklistPacket(operation, ids);
                    }
                    return new RecipeBlacklistPacket(operation, recipeId);
                }

                @Override
                public void encode(ByteBuf buf, RecipeBlacklistPacket packet) {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.operation().ordinal());
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.recipeId());
                    STRING_LIST_CODEC.encode(buf, packet.recipeIds());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 处理（服务端）
    public static void handle(RecipeBlacklistPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                LOGGER.warn("收到黑名单操作包但发送者不是服务器玩家");
                return;
            }

            // 检查权限（需要OP权限）
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c你没有权限执行此操作"));
                LOGGER.warn("玩家 {} 尝试操作黑名单但没有权限", player.getName().getString());
                return;
            }

            switch (packet.operation()) {
                case ADD -> packet.handleAdd(player);
                case REMOVE -> packet.handleRemove(player);
                case CLEAR -> packet.handleClear(player);
                case ADD_BATCH -> packet.handleAddBatch(player);
                case REMOVE_BATCH -> packet.handleRemoveBatch(player);
            }
        }).exceptionally(e -> {
            LOGGER.error("处理黑名单操作包异步错误", e);
            return null;
        });
    }

    private void handleAdd(ServerPlayer player) {
        if (recipeId.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c配方ID为空"));
            return;
        }

        try {
            ResourceLocation id = ResourceLocation.parse(recipeId);
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
            ResourceLocation id = ResourceLocation.parse(recipeId);
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
                ids.add(ResourceLocation.parse(s));
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
                ids.add(ResourceLocation.parse(s));
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
}
