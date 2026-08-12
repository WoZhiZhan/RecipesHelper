package com.wzz.registerhelper.init;

import com.wzz.registerhelper.network.CreateRecipeJsonPacket;
import com.wzz.registerhelper.network.OpenGUIPacket;
import com.wzz.registerhelper.network.RecipeBlacklistPacket;
import com.wzz.registerhelper.network.RequestRecipeListPacket;
import com.wzz.registerhelper.network.SyncRecipeListPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("4");

        // 注册打开GUI的包（服务器 -> 客户端）
        registrar.playToClient(
                OpenGUIPacket.TYPE,
                OpenGUIPacket.STREAM_CODEC,
                OpenGUIPacket::handle
        );

        // 注册创建配方的包（客户端 -> 服务器）
        registrar.playToServer(
                CreateRecipeJsonPacket.TYPE,
                CreateRecipeJsonPacket.STREAM_CODEC,
                CreateRecipeJsonPacket::handle
        );

        // 请求配方列表（客户端 -> 服务器）
        registrar.playToServer(
                RequestRecipeListPacket.TYPE,
                RequestRecipeListPacket.STREAM_CODEC,
                RequestRecipeListPacket::handle
        );

        // 同步配方列表（服务器 -> 客户端）
        registrar.playToClient(
                SyncRecipeListPacket.TYPE,
                SyncRecipeListPacket.STREAM_CODEC,
                SyncRecipeListPacket::handle
        );

        // 配方黑名单操作（客户端 -> 服务器）
        registrar.playToServer(
                RecipeBlacklistPacket.TYPE,
                RecipeBlacklistPacket.STREAM_CODEC,
                RecipeBlacklistPacket::handle
        );
    }
}
