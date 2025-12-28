package com.wzz.registerhelper.init;

import com.wzz.registerhelper.RecipeHelper;
import com.wzz.registerhelper.network.CreateRecipeJsonPacket;
import com.wzz.registerhelper.network.OpenGUIPacket;
import com.wzz.registerhelper.network.RecipeBlacklistPacket;
import com.wzz.registerhelper.network.RequestRecipeListPacket;
import com.wzz.registerhelper.network.SyncRecipeListPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RecipeHelper.MODID, "main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;

        // 打开GUI的包（服务器 -> 客户端）
        CHANNEL.messageBuilder(OpenGUIPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenGUIPacket::encode)
                .decoder(OpenGUIPacket::decode)
                .consumerMainThread(OpenGUIPacket::handle)
                .add();

        // 创建配方的包
        CHANNEL.registerMessage(
                id++,
                CreateRecipeJsonPacket.class,
                CreateRecipeJsonPacket::toBytes,
                CreateRecipeJsonPacket::fromBytes,
                CreateRecipeJsonPacket::handle
        );

        // 请求配方列表（客户端 -> 服务器）
        CHANNEL.messageBuilder(RequestRecipeListPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestRecipeListPacket::encode)
                .decoder(RequestRecipeListPacket::decode)
                .consumerMainThread(RequestRecipeListPacket::handle)
                .add();

        // 同步配方列表（服务器 -> 客户端）
        CHANNEL.messageBuilder(SyncRecipeListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncRecipeListPacket::encode)
                .decoder(SyncRecipeListPacket::decode)
                .consumerMainThread(SyncRecipeListPacket::handle)
                .add();

        // 配方黑名单操作（客户端 -> 服务器）
        CHANNEL.messageBuilder(RecipeBlacklistPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RecipeBlacklistPacket::encode)
                .decoder(RecipeBlacklistPacket::decode)
                .consumerMainThread(RecipeBlacklistPacket::handle)
                .add();
    }
}