package com.wzz.registerhelper.network;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Client cache for authoritative server recipe JSON responses. */
public final class RecipeJsonClientCache {
    private static final Map<ResourceLocation, String> JSON = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, CopyOnWriteArrayList<Consumer<String>>> CALLBACKS =
            new ConcurrentHashMap<>();

    private RecipeJsonClientCache() {
    }

    public static String get(ResourceLocation id) {
        return JSON.get(id);
    }

    public static boolean contains(ResourceLocation id) {
        return JSON.containsKey(id);
    }

    public static void request(ResourceLocation id, Consumer<String> callback) {
        String cached = JSON.get(id);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        var callbacks = CALLBACKS.computeIfAbsent(id, ignored -> new CopyOnWriteArrayList<>());
        callbacks.add(callback);
        if (callbacks.size() == 1) {
            RequestRecipeJsonPacket.sendToServer(id);
        }
    }

    public static void complete(ResourceLocation id, String json) {
        JSON.put(id, json == null ? "" : json);
        var callbacks = CALLBACKS.remove(id);
        if (callbacks != null) {
            for (Consumer<String> callback : callbacks) callback.accept(JSON.get(id));
        }
    }

    public static void clear() {
        JSON.clear();
        CALLBACKS.clear();
    }
}
