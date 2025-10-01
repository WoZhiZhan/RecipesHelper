package com.wzz.registerhelper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.network.OpenGUIPacket;
import com.wzz.registerhelper.util.CrtUtils;
import com.wzz.registerhelper.util.KubeJsUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class RecipeCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("recipe_helper")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("openGUI")
                        .executes(RecipeCommand::openGUI))
                .then(Commands.literal("export")
                        .then(Commands.literal("kubejs")
                                .then(Commands.literal("single")
                                        .executes(RecipeCommand::exportAllToKubeJSSingle))
                                .then(Commands.literal("multiple")
                                        .executes(RecipeCommand::exportAllToKubeJSMultiple)))
                        .then(Commands.literal("crafttweaker")
                                .then(Commands.literal("single")
                                        .executes(RecipeCommand::exportAllToCraftTweakerSingle))
                                .then(Commands.literal("multiple")
                                        .executes(RecipeCommand::exportAllToCraftTweakerMultiple)))));
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

    private static int exportAllToCraftTweakerSingle(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!ModList.get().isLoaded("crafttweaker")) {
            source.sendFailure(Component.literal("§c导出失败: crafttweaker mod未找到"));
            return 0;
        }
        try {
            CrtUtils.exportAllJsonRecipesToZS(true);
            source.sendSuccess(() -> Component.literal("§a成功导出所有配方到 scripts/registerhelper_recipes.zs"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c导出失败: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int exportAllToCraftTweakerMultiple(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!ModList.get().isLoaded("crafttweaker")) {
            source.sendFailure(Component.literal("§c导出失败: crafttweaker mod未找到"));
            return 0;
        }
        try {
            CrtUtils.exportAllJsonRecipesToZS(false);
            source.sendSuccess(() -> Component.literal("§a成功导出所有配方到 scripts (按目录分文件)"), true);
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
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenGUIPacket());
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