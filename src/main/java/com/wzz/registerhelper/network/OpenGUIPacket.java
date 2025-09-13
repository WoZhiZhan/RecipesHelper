package com.wzz.registerhelper.network;

import com.wzz.registerhelper.gui.RecipeCreatorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenGUIPacket {
    
    public OpenGUIPacket() {
    }
    
    public static void encode(OpenGUIPacket packet, FriendlyByteBuf buf) {
    }
    
    public static OpenGUIPacket decode(FriendlyByteBuf buf) {
        return new OpenGUIPacket();
    }
    
    public static void handle(OpenGUIPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (contextSupplier.get().getDirection().getReceptionSide().isClient()) {
                doing();
            }
        });
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void doing() {
        Minecraft.getInstance().setScreen(new RecipeCreatorScreen());
    }
}