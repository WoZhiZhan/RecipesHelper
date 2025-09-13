package com.wzz.registerhelper.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TriggerReloadPacket {
    public TriggerReloadPacket() {}

    public static void encode(TriggerReloadPacket pkt, FriendlyByteBuf buf) {}

    public static TriggerReloadPacket decode(FriendlyByteBuf buf) {
        return new TriggerReloadPacket();
    }

    public static void handle(TriggerReloadPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                doing();
            }
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void doing() {
        Minecraft mc = Minecraft.getInstance();
        mc.reloadResourcePacks();
    }
}
