package com.wzz.registerhelper.util;

import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class EnvironmentUtil {
    public static boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    public static boolean isClient(Level level) {
        if (level == null)
            return isClient();
        return level.isClientSide;
    }
}
