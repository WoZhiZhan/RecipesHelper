package com.wzz.registerhelper.util;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.storage.WorldData;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;

/**
 * 配方重载工具类
 * 以服务器身份执行 /reload 命令
 *
 * NeoForge 1.21.1 迁移要点：
 * - 删除未使用的 net.minecraftforge.server.ServerLifecycleHooks 引用
 * - recipeManager.getRecipes() 现在返回 Collection<RecipeHolder<?>>
 * - ClientboundUpdateRecipesPacket 在 1.21.1 接受 Collection<RecipeHolder<?>>
 */
public class RecipeReloadHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 重载数据包
     * 由服务器执行一次，资源重载流程负责同步客户端
     */
    public static boolean reloadDataPacks(CommandSourceStack source) {
        try {
            MinecraftServer server = source.getServer();
            if (server == null) {
                source.sendFailure(Component.translatable("registerhelper.server.unavailable")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                return false;
            }

            LOGGER.info("=== 开始重载配方 ===");

            PackRepository packRepository = server.getPackRepository();
            WorldData worldData = server.getWorldData();
            Collection<String> selectedIds = Lists.newArrayList(packRepository.getSelectedIds());

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
                LOGGER.info("服务器 reload 完成，所有客户端将使用本次重载结果");
            }, server).exceptionally((throwable) -> {
                LOGGER.warn("Failed to execute reload", throwable);
                source.sendFailure(Component.translatable("commands.reload.failure"));
                return null;
            });

            return true;

        } catch (Exception e) {
            LOGGER.error("重载数据包时出错", e);
            source.sendFailure(Component.translatable("registerhelper.command.reload.failed", e.getMessage())
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
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
            Collection<RecipeHolder<?>> recipes = recipeManager.getRecipes();

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
