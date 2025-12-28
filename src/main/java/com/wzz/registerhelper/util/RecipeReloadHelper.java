package com.wzz.registerhelper.util;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.storage.WorldData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 配方重载工具类
 * 以服务器身份执行 /reload 命令
 */
public class RecipeReloadHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 重载数据包
     * 先以服务器身份执行，再让所有玩家执行
     */
    public static boolean reloadDataPacks(CommandSourceStack source) {
        try {
            MinecraftServer server = source.getServer();
            if (server == null) {
                source.sendFailure(Component.literal("§c无法获取服务器实例"));
                return false;
            }

            LOGGER.info("=== 开始重载配方 ===");

            PackRepository packRepository = server.getPackRepository();
            WorldData worldData = server.getWorldData();
            Collection<String> selectedIds = packRepository.getSelectedIds();

            // 完全按照原版 ReloadCommand.discoverNewPacks 的逻辑
            packRepository.reload();
            Collection<String> newPacks = Lists.newArrayList(selectedIds);
            Collection<String> disabledPacks = worldData.getDataConfiguration().dataPacks().getDisabled();

            for (String packId : packRepository.getAvailableIds()) {
                if (!disabledPacks.contains(packId) && !newPacks.contains(packId)) {
                    newPacks.add(packId);
                }
            }

            // 使用服务器的 CommandSourceStack 发送消息
            CommandSourceStack serverSource = server.createCommandSourceStack();
            serverSource.sendSuccess(() -> Component.translatable("commands.reload.success"), true);

            // 第一步：服务器身份执行 reloadResources
            server.reloadResources(newPacks).thenRunAsync(() -> {
                LOGGER.info("服务器 reload 完成，开始让所有玩家同步...");

                // 第二步：让所有玩家执行 reload
                reloadForAllPlayers(server);

            }, server).exceptionally((throwable) -> {
                LOGGER.warn("Failed to execute reload", throwable);
                source.sendFailure(Component.translatable("commands.reload.failure"));
                return null;
            });

            return true;

        } catch (Exception e) {
            LOGGER.error("重载数据包时出错", e);
            source.sendFailure(Component.literal("§c重载失败: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 让所有在线玩家执行 reload 命令
     */
    private static void reloadForAllPlayers(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        if (players.isEmpty()) {
            LOGGER.info("没有在线玩家需要同步");
            return;
        }

        LOGGER.info("开始为 {} 个玩家执行 reload...", players.size());

        for (ServerPlayer player : players) {
            try {
                // 以玩家身份执行 reload（提升权限）
                CommandSourceStack playerSource = player.createCommandSourceStack().withPermission(4);
                server.getCommands().performPrefixedCommand(playerSource, "reload");
                LOGGER.debug("玩家 {} 执行 reload 完成", player.getName().getString());
            } catch (Exception e) {
                LOGGER.error("玩家 {} 执行 reload 失败", player.getName().getString(), e);
            }
        }

        LOGGER.info("所有玩家 reload 完成");
    }

    /**
     * 重载配方（调用完整的数据包重载）
     */
    public static boolean reloadRecipesOnly(CommandSourceStack source) {
        return reloadDataPacks(source);
    }

    /**
     * 同步配方到所有客户端
     */
    public static void syncRecipesToAllClients(MinecraftServer server) {
        try {
            RecipeManager recipeManager = server.getRecipeManager();
            Collection<Recipe<?>> recipes = recipeManager.getRecipes();

            ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(recipes);

            int playerCount = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(packet);
                playerCount++;
            }

            LOGGER.info("配方已同步到 {} 个客户端，共 {} 个配方", playerCount, recipes.size());

        } catch (Exception e) {
            LOGGER.error("同步配方到客户端失败", e);
        }
    }
}