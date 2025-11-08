package com.wzz.registerhelper.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.mixinaccess.IRecipeManager;
import com.wzz.registerhelper.recipe.RecipeBlacklistManager;
import com.wzz.registerhelper.recipe.UnifiedRecipeOverrideManager;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.FileReader;
import java.util.*;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin implements IRecipeManager {
    @Unique
    private static final Logger registerhelper$LOGGER = LogUtils.getLogger();
    @Unique
    private static final Gson registerhelper$GSON = new Gson();
    @Unique
    private static final String RECIPES_DIR = getCustomRecipeDir();

    @Shadow
    @Mutable
    private Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes;

    @Shadow
    @Mutable
    private Map<ResourceLocation, Recipe<?>> byName;

    /**
     * 在配方加载完成后注入自定义配方、应用覆盖和黑名单规则
     */
    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD")
    )
    private void injectCustomRecipesAndApplyRules(Map<ResourceLocation, JsonElement> originalRecipes,
                                                  ResourceManager resourceManager,
                                                  ProfilerFiller profiler,
                                                  CallbackInfo ci) {
        try {
            Map<ResourceLocation, JsonElement> customRecipes = loadCustomRecipes();
            if (!customRecipes.isEmpty()) {
                registerhelper$LOGGER.info("注入 {} 个自定义配方到游戏中", customRecipes.size());
                originalRecipes.putAll(customRecipes);
            }
            UnifiedRecipeOverrideManager.applyOverridesToRecipeMap(originalRecipes);
            applyRecipeDeletions(originalRecipes);
            if (!customRecipes.isEmpty()) {
                syncRecipesToClients();
            }

        } catch (Exception e) {
            registerhelper$LOGGER.error("处理配方规则失败", e);
        }
    }

    /**
     * 应用配方删除规则
     */
    private void applyRecipeDeletions(Map<ResourceLocation, JsonElement> recipes) {
        try {
            Set<ResourceLocation> blacklistedRecipes = RecipeBlacklistManager.getBlacklistedRecipes();

            if (blacklistedRecipes.isEmpty()) {
                return;
            }

            int removedCount = 0;
            Iterator<Map.Entry<ResourceLocation, JsonElement>> iterator = recipes.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<ResourceLocation, JsonElement> entry = iterator.next();
                ResourceLocation recipeId = entry.getKey();

                if (blacklistedRecipes.contains(recipeId)) {
                    iterator.remove();
                    removedCount++;
                    registerhelper$LOGGER.debug("删除黑名单配方: {}", recipeId);
                }
            }

            if (removedCount > 0) {
                registerhelper$LOGGER.info("已删除 {} 个黑名单配方", removedCount);
            }

        } catch (Exception e) {
            registerhelper$LOGGER.error("应用配方删除规则失败", e);
        }
    }

    /**
     * 加载所有自定义配方
     */
    private Map<ResourceLocation, JsonElement> loadCustomRecipes() {
        Map<ResourceLocation, JsonElement> recipes = new HashMap<>();

        File recipesDir = new File(RECIPES_DIR);
        if (!recipesDir.exists()) {
            registerhelper$LOGGER.debug("配方目录不存在: {}", RECIPES_DIR);
            return recipes;
        }

        // 扫描所有mod目录
        File[] modDirs = recipesDir.listFiles(File::isDirectory);
        if (modDirs == null) {
            return recipes;
        }

        for (File modDir : modDirs) {
            String modId = modDir.getName();
            loadModRecipes(modId, modDir, recipes);
        }

        return recipes;
    }

    /**
     * 加载特定mod的配方
     */
    private void loadModRecipes(String modId, File modDir, Map<ResourceLocation, JsonElement> recipes) {
        List<File> recipeFiles = scanRecipeFiles(modDir);

        registerhelper$LOGGER.info("扫描到 {} 个 {} 配方文件", recipeFiles.size(), modId);

        for (File recipeFile : recipeFiles) {
            try {
                String recipeId = getRecipeIdFromFile(modDir, recipeFile);
                ResourceLocation resourceLocation = new ResourceLocation(modId, recipeId);

                // 检查是否在黑名单中
                if (RecipeBlacklistManager.isBlacklisted(resourceLocation)) {
                    registerhelper$LOGGER.debug("跳过黑名单配方: {}", resourceLocation);
                    continue;
                }

                try (FileReader reader = new FileReader(recipeFile)) {
                    JsonElement jsonElement = registerhelper$GSON.fromJson(reader, JsonElement.class);

                    if (jsonElement != null && jsonElement.isJsonObject()) {
                        recipes.put(resourceLocation, jsonElement);
                        registerhelper$LOGGER.debug("加载配方: {}", resourceLocation);
                    } else {
                        registerhelper$LOGGER.warn("无效的配方JSON: {}", recipeFile.getPath());
                    }
                }

            } catch (Exception e) {
                registerhelper$LOGGER.error("加载配方文件失败: " + recipeFile.getPath(), e);
            }
        }
    }

    /**
     * 递归扫描配方文件
     */
    private List<File> scanRecipeFiles(File directory) {
        List<File> files = new ArrayList<>();

        File[] children = directory.listFiles();
        if (children == null) {
            return files;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                files.addAll(scanRecipeFiles(child));
            } else if (child.getName().endsWith(".json")) {
                files.add(child);
            }
        }

        return files;
    }

    /**
     * 从文件路径获取配方ID
     */
    private String getRecipeIdFromFile(File modDir, File recipeFile) {
        String modPath = modDir.getAbsolutePath();
        String filePath = recipeFile.getAbsolutePath();

        // 获取相对路径
        String relativePath = filePath.substring(modPath.length() + 1);

        // 移除.json扩展名
        if (relativePath.endsWith(".json")) {
            relativePath = relativePath.substring(0, relativePath.length() - 5);
        }

        // 将路径分隔符替换为下划线
        return relativePath.replace(File.separatorChar, '_');
    }

    /**
     * 同步配方到所有客户端
     */
    private void syncRecipesToClients() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> {
                    try {
                        RecipeManager recipeManager = server.getRecipeManager();
                        ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(
                                recipeManager.getRecipes()
                        );

                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            player.connection.send(packet);
                        }

                        registerhelper$LOGGER.debug("配方已同步到所有客户端");

                    } catch (Exception e) {
                        registerhelper$LOGGER.error("同步配方到客户端失败", e);
                    }
                });
            }
        } catch (Exception e) {
            registerhelper$LOGGER.error("获取服务器实例失败", e);
        }
    }

    @Unique
    private static String getCustomRecipeDir() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("registerhelper/recipes")
                .toAbsolutePath()
                .toString();
    }

    @Override
    public Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> getRecipes0() {
        return recipes;
    }

    @Override
    public Map<ResourceLocation, Recipe<?>> getByName0() {
        return byName;
    }

    @Override
    public void setByName(Map<ResourceLocation, Recipe<?>> byName) {
        this.byName = byName;
    }

    @Override
    public void setRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes) {
        this.recipes = recipes;
    }

    @Override
    public void safeSetRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes) {
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> safe = new HashMap<>();
        for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> entry : recipes.entrySet()) {
            safe.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        this.setRecipes(safe);
    }
}