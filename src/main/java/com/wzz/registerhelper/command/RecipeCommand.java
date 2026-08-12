package com.wzz.registerhelper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.network.OpenGUIPacket;
import com.wzz.registerhelper.util.CrtUtils;
import com.wzz.registerhelper.util.KubeJsUtils;
import com.wzz.registerhelper.util.RecipeReloadHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * RegisterHelper 命令注册。
 *
 * NeoForge 1.21.1 command registration and export entry points.
 */
public class RecipeCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> export = Commands.literal("export")
                .then(Commands.literal("kubejs")
                        .then(Commands.literal("single")
                                .executes(RecipeCommand::exportAllToKubeJSSingle))
                        .then(Commands.literal("multiple")
                                .executes(RecipeCommand::exportAllToKubeJSMultiple)));

        if (ModList.get().isLoaded("crafttweaker")) {
            export.then(Commands.literal("crafttweaker")
                    .then(Commands.literal("single")
                            .executes(RecipeCommand::exportAllToCraftTweakerSingle))
                    .then(Commands.literal("multiple")
                            .executes(RecipeCommand::exportAllToCraftTweakerMultiple)));
        }

        dispatcher.register(Commands.literal("recipe_helper")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("openGUI")
                        .executes(RecipeCommand::openGUI))
                .then(Commands.literal("reload")
                        .executes(RecipeCommand::reloadRecipes))
                .then(export));
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
            source.sendFailure(Component.translatable("registerhelper.command.reload.failed", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("配方重载失败", e);
            return 0;
        }
    }

    private static int exportAllToKubeJSSingle(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            KubeJsUtils.exportAllJsonRecipesToJS(true);
            source.sendSuccess(() -> Component.translatable(
                    "registerhelper.command.export.kubejs.single.success"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("registerhelper.command.export.failed", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("导出配方失败", e);
            return 0;
        }
    }

    private static int exportAllToKubeJSMultiple(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            KubeJsUtils.exportAllJsonRecipesToJS(false);
            source.sendSuccess(() -> Component.translatable(
                    "registerhelper.command.export.kubejs.multiple.success"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("registerhelper.command.export.failed", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("导出配方失败", e);
            return 0;
        }
    }

    private static int exportAllToCraftTweakerSingle(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!ModList.get().isLoaded("crafttweaker")) {
            source.sendFailure(Component.translatable("registerhelper.command.export.crafttweaker.missing")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            CrtUtils.exportAllJsonRecipesToZS(true);
            source.sendSuccess(() -> Component.translatable(
                    "registerhelper.command.export.crafttweaker.single.success"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("registerhelper.command.export.failed", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("导出 CraftTweaker 配方失败", e);
            return 0;
        }
    }

    private static int exportAllToCraftTweakerMultiple(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!ModList.get().isLoaded("crafttweaker")) {
            source.sendFailure(Component.translatable("registerhelper.command.export.crafttweaker.missing")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            CrtUtils.exportAllJsonRecipesToZS(false);
            source.sendSuccess(() -> Component.translatable(
                    "registerhelper.command.export.crafttweaker.multiple.success"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("registerhelper.command.export.failed", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            LOGGER.error("导出 CraftTweaker 配方失败", e);
            return 0;
        }
    }

    private static int openGUI(CommandContext<CommandSourceStack> context) {
        try {
            if (context.getSource().getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new OpenGUIPacket());
                return 1;
            } else {
                context.getSource().sendFailure(Component.translatable(
                        "registerhelper.command.open_gui.player_only"));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.translatable(
                    "registerhelper.command.open_gui.failed", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }
}
