package com.wzz.registerhelper.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Small helpers for keeping GUI text translatable at render time. */
public final class GuiText {
    private GuiText() {
    }

    public static MutableComponent component(String key, Object... args) {
        if (args.length == 0) {
            return Component.translatable(key);
        }

        Object[] safeArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            safeArgs[i] = arg instanceof Component
                    || arg instanceof Number
                    || arg instanceof Boolean
                    || arg instanceof String
                    ? arg : String.valueOf(arg);
        }
        return Component.translatable(key, safeArgs);
    }

    public static String string(String key, Object... args) {
        return component(key, args).getString();
    }
}
