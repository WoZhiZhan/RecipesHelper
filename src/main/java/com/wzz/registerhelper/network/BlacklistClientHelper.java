package com.wzz.registerhelper.network;

import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 客户端黑名单操作辅助类
 * 自动判断是单人游戏还是远程服务器，选择正确的操作方式
 *
 * NeoForge 1.21.1 版本
 */
@OnlyIn(Dist.CLIENT)
public class BlacklistClientHelper {

    /**
     * 检查是否为远程服务器
     */
    public static boolean isRemoteServer() {
        return ServerLifecycleHooks.getCurrentServer() == null;
    }

    /**
     * 添加配方到黑名单
     */
    public static boolean addToBlacklist(ResourceLocation recipeId) {
        if (isRemoteServer()) {
            // 远程服务器：发送网络包
            PacketDistributor.sendToServer(
                    new RecipeBlacklistPacket(RecipeBlacklistPacket.Operation.ADD, recipeId.toString())
            );
            return true; // 假设成功，实际结果由服务器反馈
        } else {
            // 单人游戏：直接操作
            return RecipeBlacklistManager.addToBlacklist(recipeId);
        }
    }

    /**
     * 从黑名单移除配方
     */
    public static boolean removeFromBlacklist(ResourceLocation recipeId) {
        if (isRemoteServer()) {
            // 远程服务器：发送网络包
            PacketDistributor.sendToServer(
                    new RecipeBlacklistPacket(RecipeBlacklistPacket.Operation.REMOVE, recipeId.toString())
            );
            return true;
        } else {
            // 单人游戏：直接操作
            return RecipeBlacklistManager.removeFromBlacklist(recipeId);
        }
    }

    /**
     * 批量添加配方到黑名单
     */
    public static boolean addMultipleToBlacklist(java.util.Collection<ResourceLocation> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) return false;
        java.util.List<String> ids = recipeIds.stream().map(ResourceLocation::toString).toList();
        if (isRemoteServer()) {
            sendBatches(RecipeBlacklistPacket.Operation.ADD_BATCH, ids);
            return true;
        } else {
            return RecipeBlacklistManager.addMultipleToBlacklist(new java.util.HashSet<>(recipeIds)) > 0;
        }
    }

    /**
     * 批量从黑名单移除配方
     */
    public static boolean removeMultipleFromBlacklist(java.util.Collection<ResourceLocation> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) return false;
        java.util.List<String> ids = recipeIds.stream().map(ResourceLocation::toString).toList();
        if (isRemoteServer()) {
            sendBatches(RecipeBlacklistPacket.Operation.REMOVE_BATCH, ids);
            return true;
        } else {
            return RecipeBlacklistManager.removeMultipleFromBlacklist(new java.util.HashSet<>(recipeIds)) > 0;
        }
    }

    /**
     * 清空黑名单
     */
    public static boolean clearBlacklist() {
        if (isRemoteServer()) {
            // 远程服务器：发送网络包
            PacketDistributor.sendToServer(
                    new RecipeBlacklistPacket(RecipeBlacklistPacket.Operation.CLEAR)
            );
            return true;
        } else {
            // 单人游戏：直接操作
            return RecipeBlacklistManager.clearBlacklist();
        }
    }

    private static void sendBatches(RecipeBlacklistPacket.Operation operation, java.util.List<String> ids) {
        for (int start = 0; start < ids.size(); start += RecipeBlacklistPacket.MAX_BATCH_SIZE) {
            int end = Math.min(start + RecipeBlacklistPacket.MAX_BATCH_SIZE, ids.size());
            PacketDistributor.sendToServer(new RecipeBlacklistPacket(operation, ids.subList(start, end)));
        }
    }
}
