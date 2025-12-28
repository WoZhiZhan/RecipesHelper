package com.wzz.registerhelper.network;

import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 客户端黑名单操作辅助类
 * 自动判断是单人游戏还是远程服务器，选择正确的操作方式
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
            ModNetwork.CHANNEL.sendToServer(
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
            ModNetwork.CHANNEL.sendToServer(
                new RecipeBlacklistPacket(RecipeBlacklistPacket.Operation.REMOVE, recipeId.toString())
            );
            return true;
        } else {
            // 单人游戏：直接操作
            return RecipeBlacklistManager.removeFromBlacklist(recipeId);
        }
    }
    
    /**
     * 清空黑名单
     */
    public static boolean clearBlacklist() {
        if (isRemoteServer()) {
            // 远程服务器：发送网络包
            ModNetwork.CHANNEL.sendToServer(
                new RecipeBlacklistPacket(RecipeBlacklistPacket.Operation.CLEAR)
            );
            return true;
        } else {
            // 单人游戏：直接操作
            return RecipeBlacklistManager.clearBlacklist();
        }
    }
}