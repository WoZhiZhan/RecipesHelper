package com.wzz.registerhelper.network;

import com.wzz.registerhelper.ModMain;
import com.wzz.registerhelper.gui.RecipeCreatorScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenGUIPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenGUIPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "open_gui")
            );

    // 空包，不需要编码任何数据
    public static final StreamCodec<ByteBuf, OpenGUIPacket> STREAM_CODEC =
            StreamCodec.unit(new OpenGUIPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理数据包（客户端）
     */
    public static void handle(OpenGUIPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
                    if (context.flow().isClientbound()) {
                        openScreen();
                    }
                })
                .exceptionally(e -> {
                    // 处理异常
                    return null;
                });
    }

    @OnlyIn(Dist.CLIENT)
    private static void openScreen() {
        Minecraft.getInstance().setScreen(new RecipeCreatorScreen());
    }
}