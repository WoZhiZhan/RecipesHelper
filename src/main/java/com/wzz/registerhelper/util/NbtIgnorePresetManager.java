package com.wzz.registerhelper.util;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * 持久化管理"忽略 NBT Key"自定义预设。
 * 存储路径：config/registerhelper/nbt_ignore_presets.json
 * 格式：
 * {
 *   "presets": [
 *     { "name": "拔刀剑", "keys": ["bladeState.lastActionTime", "bladeState.Damage", ...] },
 *     { "name": "我的预设", "keys": ["someKey"] }
 *   ]
 * }
 */
public class NbtIgnorePresetManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path PRESET_FILE = FMLPaths.CONFIGDIR.get()
            .resolve("registerhelper")
            .resolve("nbt_ignore_presets.json");

    /** 预设条目 */
    public record Preset(String name, List<String> keys) {
        public Preset(String name, List<String> keys) {
            this.name = name;
            this.keys = List.copyOf(keys);
        }
    }

    private static List<Preset> presets = null; // null = 未加载

    // ── 内置预设（首次加载时写入，不强制覆盖用户修改）──────────────
    private static final List<Preset> BUILTIN_PRESETS = List.of(
            new Preset("拔刀剑", List.of(
                    "bladeState.lastActionTime",
                    "bladeState.TargetEntity",
                    "bladeState.Damage",
                    "bladeState.currentCombo",
                    "bladeState._onClick",
                    "bladeState.killCount",
                    "bladeState.proudSoul",
                    "bladeState.RepairCounter"
            ))
    );

    // ── 读取 ──────────────────────────────────────────────────────

    public static List<Preset> getAll() {
        if (presets == null) load();
        return Collections.unmodifiableList(presets);
    }

    private static void load() {
        presets = new ArrayList<>();
        File file = PRESET_FILE.toFile();

        if (!file.exists()) {
            // 首次：写入内置预设
            presets.addAll(BUILTIN_PRESETS);
            save();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null && root.has("presets")) {
                for (JsonElement el : root.getAsJsonArray("presets")) {
                    JsonObject obj = el.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    List<String> keys = new ArrayList<>();
                    obj.getAsJsonArray("keys").forEach(k -> keys.add(k.getAsString()));
                    presets.add(new Preset(name, keys));
                }
            }
        } catch (Exception e) {
            LOGGER.error("[RegisterHelper] 读取 NBT 忽略预设失败", e);
            presets.addAll(BUILTIN_PRESETS); // 回退内置
        }
    }

    // ── 写入 ──────────────────────────────────────────────────────

    private static void save() {
        try {
            File file = PRESET_FILE.toFile();
            file.getParentFile().mkdirs();

            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (Preset p : presets) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", p.name());
                JsonArray keys = new JsonArray();
                p.keys().forEach(keys::add);
                obj.add("keys", keys);
                arr.add(obj);
            }
            root.add("presets", arr);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            LOGGER.error("[RegisterHelper] 保存 NBT 忽略预设失败", e);
        }
    }

    /**
     * 添加或覆盖同名预设
     */
    public static void addOrUpdate(String name, List<String> keys) {
        if (presets == null) load();
        presets.removeIf(p -> p.name().equals(name));
        presets.add(new Preset(name, keys));
        save();
    }

    /**
     * 删除预设（按名称）
     */
    public static boolean remove(String name) {
        if (presets == null) load();
        boolean removed = presets.removeIf(p -> p.name().equals(name));
        if (removed) save();
        return removed;
    }

    /** 强制重新加载（热重载用） */
    public static void reload() {
        presets = null;
    }
}