package com.wzz.registerhelper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.network.OpenGUIPacket;
import com.wzz.registerhelper.util.KubeJsUtils;
import com.wzz.registerhelper.util.RecipeReloadHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * RegisterHelper 命令注册。
 *
 * NeoForge 1.21.1 迁移要点：
 * - 删除 CraftTweaker(CrtUtils) 导出分支（联动不移植）
 * - 保留 KubeJS 导出（KubeJsUtils 已存在）
 * - 补回 1.20.1 中的 reload 命令（以服务器身份执行 /reload）
 * - PacketDistributor: ModNetwork.CHANNEL.send(...) -> PacketDistributor.sendToPlayer(...)
 * - net.minecraftforge.fml.ModList 已不再需要（CraftTweaker 检查随分支删除）
 */
public class RecipeCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("recipe_helper")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("openGUI")
                        .executes(RecipeCommand::openGUI))
                .then(Commands.literal("reload")
                        .executes(RecipeCommand::reloadRecipes))
                .then(Commands.literal("export")
                        .then(Commands.literal("kubejs")
                                .then(Commands.literal("single")
                                        .executes(RecipeCommand::exportAllToKubeJSSingle))
                                .then(Commands.literal("multiple")
                                        .executes(RecipeCommand::exportAllToKubeJSMultiple)))));
    }

    /**
     * 重载配方（OP可用，以服务器身份执行 /reload）
     */
    private static int reloadRecipes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            // 直接调用，服务器会自己发送 "Reloading!" 消息
            boolean success = RecipeReloadHelper.reloadRecipesOnly(source);

            if (success) {
                LOGGER.info("玩家 {} 触发了配方重载",
                        source.getEntity() != null ? source.getEntity().getName().getString() : "Console");
                return 1;
            } else {
                return 0;
            }

        } catch (Exception e) {
            source.sendFailure(Component.literal("§c重载失败: " + e.getMessage()));
            LOGGER.error("配方重载失败", e);
            return 0;
        }
    }

    private static int exportAllToKubeJSSingle(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            KubeJsUtils.exportAllJsonRecipesToJS(true);
            source.sendSuccess(() -> Component.literal("§a成功导出所有配方到 kubejs/server_scripts/registerhelper_recipes.js"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c导出失败: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int exportAllToKubeJSMultiple(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            KubeJsUtils.exportAllJsonRecipesToJS(false);
            source.sendSuccess(() -> Component.literal("§a成功导出所有配方到 kubejs/server_scripts (按目录分文件)"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c导出失败: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int openGUI(CommandContext<CommandSourceStack> context) {
        try {
            if (context.getSource().getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new OpenGUIPacket());
                return 1;
            } else {
                context.getSource().sendFailure(Component.literal("只有玩家可以使用GUI"));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("打开GUI失败: " + e.getMessage()));
            return 0;
        }
    }
}
