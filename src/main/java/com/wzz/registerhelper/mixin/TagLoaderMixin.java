package com.wzz.registerhelper.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.FileReader;
import java.util.*;

@Mixin(TagLoader.class)
public class TagLoaderMixin {
    @Unique
    private static final Logger registerhelper$LOGGER = LogUtils.getLogger();
    @Unique
    private static final Gson registerhelper$GSON = new Gson();
    @Unique
    private static final String CUSTOM_TAGS_DIR = getCustomTagsDir();

    /**
     * 在标签加载完成后注入自定义标签
     */
    @Inject(
            method = "load",
            at = @At("RETURN")
    )
    private void injectCustomTags(ResourceManager resourceManager,
                                  CallbackInfoReturnable<Map<ResourceLocation, List<TagLoader.EntryWithSource>>> cir) {
        try {
            Map<ResourceLocation, List<TagLoader.EntryWithSource>> originalTags = cir.getReturnValue();
            Map<ResourceLocation, List<TagLoader.EntryWithSource>> customTags = loadCustomTags();

            if (!customTags.isEmpty()) {
                for (Map.Entry<ResourceLocation, List<TagLoader.EntryWithSource>> entry : customTags.entrySet()) {
                    ResourceLocation tagId = entry.getKey();
                    List<TagLoader.EntryWithSource> customEntries = entry.getValue();
                    originalTags.merge(tagId, customEntries, (existing, custom) -> {
                        List<TagLoader.EntryWithSource> merged = new ArrayList<>(existing);
                        merged.addAll(custom);
                        return merged;
                    });
                }
            }

        } catch (Exception e) {
            registerhelper$LOGGER.error("注入自定义标签失败", e);
        }
    }

    /**
     * 加载所有自定义标签
     */
    @Unique
    private Map<ResourceLocation, List<TagLoader.EntryWithSource>> loadCustomTags() {
        Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags = new HashMap<>();

        File tagsDir = new File(CUSTOM_TAGS_DIR);
        if (!tagsDir.exists()) {
            registerhelper$LOGGER.debug("自定义标签目录不存在: {}", CUSTOM_TAGS_DIR);
            return tags;
        }

        // 扫描所有命名空间目录
        File[] namespaceDirs = tagsDir.listFiles(File::isDirectory);
        if (namespaceDirs == null) {
            return tags;
        }

        for (File namespaceDir : namespaceDirs) {
            String namespace = namespaceDir.getName();
            loadNamespaceTags(namespace, namespaceDir, tags);
        }

        return tags;
    }

    /**
     * 加载特定命名空间的标签
     */
    @Unique
    private void loadNamespaceTags(String namespace, File namespaceDir,
                                   Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags) {
        // 扫描items子目录
        File itemsDir = new File(namespaceDir, "items");
        if (itemsDir.exists() && itemsDir.isDirectory()) {
            loadTagFiles(namespace, "items", itemsDir, tags);
        }

        // 扫描blocks子目录
        File blocksDir = new File(namespaceDir, "blocks");
        if (blocksDir.exists() && blocksDir.isDirectory()) {
            loadTagFiles(namespace, "blocks", blocksDir, tags);
        }
    }

    /**
     * 加载标签文件
     */
    @Unique
    private void loadTagFiles(String namespace, String type, File directory,
                              Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags) {
        List<File> tagFiles = scanTagFiles(directory);
        for (File tagFile : tagFiles) {
            try {
                String tagPath = getTagPathFromFile(directory, tagFile);
                ResourceLocation tagId = new ResourceLocation(namespace, tagPath);
                String source = "registerhelper:custom/" + namespace + "/" + type + "/" + tagPath;

                try (FileReader reader = new FileReader(tagFile)) {
                    JsonObject tagJson = registerhelper$GSON.fromJson(reader, JsonObject.class);

                    if (tagJson != null && tagJson.has("values")) {
                        List<TagLoader.EntryWithSource> entries = parseTagEntries(tagJson, source);

                        if (!entries.isEmpty()) {
                            tags.put(tagId, entries);
                        }
                    } else {
                        registerhelper$LOGGER.warn("无效的标签JSON (缺少values字段): {}",
                                tagFile.getPath());
                    }
                }

            } catch (Exception e) {
                registerhelper$LOGGER.error("加载标签文件失败: " + tagFile.getPath(), e);
            }
        }
    }

    /**
     * 解析标签条目
     */
    @Unique
    private List<TagLoader.EntryWithSource> parseTagEntries(JsonObject tagJson, String source) {
        List<TagLoader.EntryWithSource> entries = new ArrayList<>();
        JsonArray valuesArray = tagJson.getAsJsonArray("values");

        for (JsonElement element : valuesArray) {
            try {
                if (element.isJsonPrimitive()) {
                    // 简单的字符串条目: "minecraft:diamond" - 默认设为可选
                    String value = element.getAsString();
                    entries.add(createTagEntry(value, source, false));

                } else if (element.isJsonObject()) {
                    // 对象条目: {"id": "minecraft:diamond", "required": false}
                    JsonObject entryObj = element.getAsJsonObject();
                    String value = entryObj.get("id").getAsString();
                    // 默认为false（可选），避免加载错误
                    boolean required = entryObj.has("required") ?
                            entryObj.get("required").getAsBoolean() : false;

                    entries.add(createTagEntry(value, source, required));
                }

            } catch (Exception e) {
                registerhelper$LOGGER.error("解析标签条目失败: " + element, e);
            }
        }

        return entries;
    }

    /**
     * 创建标签条目
     */
    @Unique
    private TagLoader.EntryWithSource createTagEntry(String value, String source, boolean required) {
        TagEntry entry;

        if (value.startsWith("#")) {
            // 标签引用: #minecraft:planks
            ResourceLocation tagRef = new ResourceLocation(value.substring(1));
            entry = required ? TagEntry.tag(tagRef) : TagEntry.optionalTag(tagRef);
        } else {
            // 普通条目: minecraft:diamond
            ResourceLocation itemId = new ResourceLocation(value);
            // 默认设置为可选，避免加载错误
            entry = required ? TagEntry.element(itemId) : TagEntry.optionalElement(itemId);
        }

        return new TagLoader.EntryWithSource(entry, source);
    }

    @Unique
    private static String getCustomTagsDir() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("registerhelper/custom_tags")
                .toAbsolutePath()
                .toString();
    }

    /**
     * 递归扫描标签文件
     */
    @Unique
    private List<File> scanTagFiles(File directory) {
        List<File> files = new ArrayList<>();

        File[] children = directory.listFiles();
        if (children == null) {
            return files;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                files.addAll(scanTagFiles(child));
            } else if (child.getName().endsWith(".json")) {
                files.add(child);
            }
        }

        return files;
    }

    /**
     * 从文件路径获取标签路径
     */
    @Unique
    private String getTagPathFromFile(File baseDir, File tagFile) {
        String basePath = baseDir.getAbsolutePath();
        String filePath = tagFile.getAbsolutePath();

        // 获取相对路径
        String relativePath = filePath.substring(basePath.length() + 1);

        // 移除.json扩展名
        if (relativePath.endsWith(".json")) {
            relativePath = relativePath.substring(0, relativePath.length() - 5);
        }

        // 将路径分隔符替换为斜杠（标签路径使用斜杠）
        return relativePath.replace(File.separatorChar, '/');
    }
}