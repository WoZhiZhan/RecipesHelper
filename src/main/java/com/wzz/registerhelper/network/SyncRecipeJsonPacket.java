package com.wzz.registerhelper.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Returns one authoritative recipe JSON document to the requesting client. */
public record SyncRecipeJsonPacket(ResourceLocation recipeId, String json) {
    private static final int MAX_JSON_LENGTH = 1_048_576;

    public static void encode(SyncRecipeJsonPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.recipeId);
        buffer.writeUtf(packet.json, MAX_JSON_LENGTH);
    }

    public static SyncRecipeJsonPacket decode(FriendlyByteBuf buffer) {
        return new SyncRecipeJsonPacket(buffer.readResourceLocation(),
                buffer.readUtf(MAX_JSON_LENGTH));
    }

    public static void handle(SyncRecipeJsonPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> RecipeJsonClientCache.complete(packet.recipeId, packet.json));
        context.setPacketHandled(true);
    }
}
