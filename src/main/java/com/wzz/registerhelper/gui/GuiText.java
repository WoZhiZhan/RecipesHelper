package com.wzz.registerhelper.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Small helpers for keeping GUI text translatable at render time. */
public final class GuiText {
    private GuiText() {
    }

    public static MutableComponent component(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static String string(String key, Object... args) {
        return component(key, args).getString();
    }
}
