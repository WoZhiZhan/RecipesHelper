package com.wzz.registerhelper.init;

import com.wzz.registerhelper.Registerhelper;
import com.wzz.registerhelper.network.CreateRecipePacket;
import com.wzz.registerhelper.network.OpenGUIPacket;
import com.wzz.registerhelper.network.RecipeDataPacket;
import com.wzz.registerhelper.network.TriggerReloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Registerhelper.MODID, "main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, TriggerReloadPacket.class,
                TriggerReloadPacket::encode,
                TriggerReloadPacket::decode,
                TriggerReloadPacket::handle
        );
        CHANNEL.messageBuilder(RecipeDataPacket.class, id++)
                .encoder(RecipeDataPacket::encode)
                .decoder(RecipeDataPacket::decode)
                .consumerMainThread(RecipeDataPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenGUIPacket.class, id++)
                .encoder(OpenGUIPacket::encode)
                .decoder(OpenGUIPacket::decode)
                .consumerMainThread(OpenGUIPacket::handle)
                .add();
        CHANNEL.registerMessage(id++, CreateRecipePacket.class,
                CreateRecipePacket::toBytes, CreateRecipePacket::new, CreateRecipePacket::handle);
    }
}
