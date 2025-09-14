package com.wzz.registerhelper.event;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.core.RecipeJsonManager;
import com.wzz.registerhelper.core.RecipeJsonManagerExtension;
import com.wzz.registerhelper.core.RecipeOverrideResolver;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * 配方覆盖系统的主要管理器
 * 
 * 工作原理：
 * 1. 在config/recipes/文件夹中创建与现有配方同名的JSON文件
 * 2. 系统检测到同名配方时，自动删除内存中的原始配方
 * 3. 加载JSON中的新配方来替换它
 * 4. 实现永久的配方覆盖，重启游戏后覆盖仍然有效
 */
@Mod.EventBusSubscriber
public class RecipeOverrideManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean hasInitialized = false;

    /**
     * 服务器启动时自动解决配方覆盖冲突
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        server.execute(() -> {
            try {
                Thread.sleep(1000);
                initializeOverrideSystem();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("配方覆盖系统初始化被中断");
            }
        });
    }

    /**
     * 服务器完全启动后再次检查
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!hasInitialized) {
            event.getServer().execute(RecipeOverrideManager::initializeOverrideSystem);
        }
    }

    /**
     * 初始化配方覆盖系统
     */
    public static synchronized void initializeOverrideSystem() {
        if (hasInitialized) {
            return;
        }

        try {
            int jsonRecipeCount = RecipeJsonManager.getAllSavedRecipeIds().size();
            LOGGER.info("发现 {} 个配方覆盖文件", jsonRecipeCount);
            if (jsonRecipeCount == 0) {
                LOGGER.info("没有发现配方覆盖文件，跳过覆盖处理");
                hasInitialized = true;
                return;
            }

            int overriddenCount = RecipeOverrideResolver.resolveConflictsPreferJson();
            
            if (overriddenCount > 0) {
                LOGGER.info("成功覆盖 {} 个配方", overriddenCount);
                syncOverridesToClients();
                notifyAdminsAboutOverrides(overriddenCount);
                
            } else {
                LOGGER.info("没有发现需要覆盖的配方冲突");
            }

            hasInitialized = true;
            LOGGER.info("=== 配方覆盖系统初始化完成 ===");

        } catch (Exception e) {
            LOGGER.error("配方覆盖系统初始化失败", e);
        }
    }

    /**
     * 手动触发配方覆盖解析
     * @return 覆盖的配方数量
     */
    public static int resolveOverridesManually() {
        try {
            LOGGER.info("手动解决配方覆盖冲突...");
            
            int overriddenCount = RecipeOverrideResolver.resolveConflictsPreferJson();
            
            if (overriddenCount > 0) {
                syncOverridesToClients();
                LOGGER.info("手动覆盖完成，覆盖了 {} 个配方", overriddenCount);
            } else {
                LOGGER.info("没有发现需要覆盖的配方");
            }
            
            return overriddenCount;
            
        } catch (Exception e) {
            LOGGER.error("手动解决覆盖冲突失败", e);
            return 0;
        }
    }

    /**
     * 创建配方覆盖
     * @param recipeId 要覆盖的配方ID
     * @return 是否创建成功
     */
    public static boolean createRecipeOverride(ResourceLocation recipeId) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.error("服务器实例为空，无法创建配方覆盖");
                return false;
            }

            var recipeManager = server.overworld().getRecipeManager();
            var originalRecipe = recipeManager.byKey(recipeId).orElse(null);
            
            if (originalRecipe == null) {
                LOGGER.error("原始配方不存在: {}", recipeId);
                return false;
            }

            boolean templateCreated = RecipeJsonManagerExtension.createOverrideTemplate(
                recipeId.toString(), originalRecipe);
                
            if (templateCreated) {
                LOGGER.info("成功创建配方覆盖模板: {}", recipeId);
                LOGGER.info("覆盖文件路径: {}", RecipeJsonManagerExtension.getOverrideFilePath(recipeId.toString()));
                return true;
            } else {
                LOGGER.error("创建配方覆盖模板失败: {}", recipeId);
                return false;
            }
            
        } catch (Exception e) {
            LOGGER.error("创建配方覆盖失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 删除配方覆盖（恢复原始配方）
     * @param recipeId 配方ID
     * @return 是否删除成功
     */
    public static boolean removeRecipeOverride(ResourceLocation recipeId) {
        try {
            boolean success = RecipeJsonManager.deleteRecipe(recipeId.toString());
            
            if (success) {
                LOGGER.info("成功删除配方覆盖: {}", recipeId);
                LOGGER.info("请重新加载游戏以恢复原始配方");
            } else {
                LOGGER.warn("配方覆盖文件不存在或删除失败: {}", recipeId);
            }
            
            return success;
            
        } catch (Exception e) {
            LOGGER.error("删除配方覆盖失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 检查配方是否被覆盖
     * @param recipeId 配方ID
     * @return 是否被覆盖
     */
    public static boolean isRecipeOverridden(ResourceLocation recipeId) {
        try {
            // 检查内存中是否被标记为覆盖
            if (RecipeOverrideResolver.isRecipeOverridden(recipeId)) {
                return true;
            }
            
            // 检查是否存在覆盖文件
            return RecipeJsonManagerExtension.hasOverrideFile(recipeId.toString());
            
        } catch (Exception e) {
            LOGGER.error("检查配方覆盖状态失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 获取覆盖状态报告
     */
    public static OverrideStatus getOverrideStatus() {
        try {
            RecipeOverrideResolver.OverrideReport report = RecipeOverrideResolver.generateOverrideReport();
            
            OverrideStatus status = new OverrideStatus();
            status.jsonRecipeCount = report.jsonRecipeCount;
            status.memoryRecipeCount = report.memoryRecipeCount;
            status.overriddenCount = report.overriddenRecipes.size();
            status.conflictingCount = report.conflictingRecipes.size();
            status.hasConflicts = report.hasConflicts();
            status.overriddenRecipes = report.overriddenRecipes.stream().map(ResourceLocation::toString).toList();
            
            return status;
            
        } catch (Exception e) {
            LOGGER.error("获取覆盖状态失败", e);
            return new OverrideStatus();
        }
    }

    /**
     * 同步配方覆盖到所有客户端
     */
    private static void syncOverridesToClients() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                var recipeManager = server.overworld().getRecipeManager();
                var packet = new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(
                    recipeManager.getRecipes());
                
                for (var player : server.getPlayerList().getPlayers()) {
                    player.connection.send(packet);
                }
                
                LOGGER.debug("已同步配方覆盖到所有客户端");
            }
        } catch (Exception e) {
            LOGGER.error("同步配方覆盖到客户端失败", e);
        }
    }

    /**
     * 通知管理员关于配方覆盖
     */
    private static void notifyAdminsAboutOverrides(int overriddenCount) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                var message = net.minecraft.network.chat.Component.literal(
                    String.format("§a[配方覆盖] 自动覆盖了 %d 个配方", overriddenCount));
                
                for (var player : server.getPlayerList().getPlayers()) {
                    if (server.getPlayerList().isOp(player.getGameProfile())) {
                        player.sendSystemMessage(message);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("通知管理员失败", e);
        }
    }

    /**
     * 重置覆盖系统（用于重新加载）
     */
    public static void reset() {
        hasInitialized = false;
        LOGGER.info("配方覆盖系统已重置");
    }

    /**
     * 配方覆盖状态类
     */
    public static class OverrideStatus {
        public int jsonRecipeCount = 0;
        public int memoryRecipeCount = 0;
        public int overriddenCount = 0;
        public int conflictingCount = 0;
        public boolean hasConflicts = false;
        public java.util.List<String> overriddenRecipes = new java.util.ArrayList<>();

        @Override
        public String toString() {
            return String.format(
                "OverrideStatus{JSON文件: %d, 内存配方: %d, 已覆盖: %d, 冲突: %d, 有冲突: %s}",
                jsonRecipeCount, memoryRecipeCount, overriddenCount, conflictingCount, hasConflicts
            );
        }
    }

    /**
     * 用于GUI的覆盖管理接口
     */
    public static class GUIInterface {
        
        /**
         * 为GUI提供的创建配方覆盖方法
         */
        public static boolean createOverrideFromGUI(String recipeIdString) {
            try {
                ResourceLocation recipeId = new ResourceLocation(recipeIdString);
                return createRecipeOverride(recipeId);
            } catch (Exception e) {
                LOGGER.error("从GUI创建配方覆盖失败: " + recipeIdString, e);
                return false;
            }
        }

        /**
         * 为GUI提供的删除配方覆盖方法
         */
        public static boolean removeOverrideFromGUI(String recipeIdString) {
            try {
                ResourceLocation recipeId = new ResourceLocation(recipeIdString);
                return removeRecipeOverride(recipeId);
            } catch (Exception e) {
                LOGGER.error("从GUI删除配方覆盖失败: " + recipeIdString, e);
                return false;
            }
        }

        /**
         * 检查配方是否可以被覆盖
         */
        public static boolean canOverrideRecipe(String recipeIdString) {
            try {
                ResourceLocation recipeId = new ResourceLocation(recipeIdString);
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                
                if (server != null) {
                    var recipeManager = server.overworld().getRecipeManager();
                    return recipeManager.byKey(recipeId).isPresent();
                }
                
                return false;
            } catch (Exception e) {
                return false;
            }
        }
    }
}