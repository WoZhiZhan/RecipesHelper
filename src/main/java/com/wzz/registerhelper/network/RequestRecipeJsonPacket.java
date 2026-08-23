package com.wzz.registerhelper.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.recipe.RecipeJsonResolver;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Requests one recipe's authoritative JSON from the server. */
public record RequestRecipeJsonPacket(ResourceLocation recipeId) {
    private static final Gson GSON = new Gson();

    public static void encode(RequestRecipeJsonPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.recipeId);
    }

    public static RequestRecipeJsonPacket decode(FriendlyByteBuf buffer) {
        return new RequestRecipeJsonPacket(buffer.readResourceLocation());
    }

    public static void handle(RequestRecipeJsonPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || player.getServer() == null) return;
            JsonObject json = RecipeJsonResolver.resolve(player.getServer(), packet.recipeId);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new SyncRecipeJsonPacket(packet.recipeId, json == null ? "" : GSON.toJson(json)));
        });
        context.setPacketHandled(true);
    }

    public static void sendToServer(ResourceLocation recipeId) {
        ModNetwork.CHANNEL.sendToServer(new RequestRecipeJsonPacket(recipeId));
    }
}
