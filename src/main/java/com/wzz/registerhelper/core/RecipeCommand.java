package com.wzz.registerhelper.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.network.OpenGUIPacket;
import com.wzz.registerhelper.Registerhelper;
import com.wzz.registerhelper.util.RecipeUtil;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

import static com.wzz.registerhelper.util.RecipeUtil.getAllRecipeIds;

public class RecipeCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("recipe_helper")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("gui")
                        .executes(RecipeCommand::openGUI))
                .then(Commands.literal("add")
                        .then(Commands.literal("shaped")
                                .then(Commands.argument("result", ItemArgument.item(buildContext))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                                        .executes(RecipeCommand::addShapedRecipe)))))
                        .then(Commands.literal("shapeless")
                                .then(Commands.argument("result", ItemArgument.item(buildContext))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .then(Commands.argument("ingredients", StringArgumentType.greedyString())
                                                        .executes(RecipeCommand::addShapelessRecipe)))))
                        .then(Commands.literal("smelting")
                                .then(Commands.argument("ingredient", ItemArgument.item(buildContext))
                                        .then(Commands.argument("result", ItemArgument.item(buildContext))
                                                .executes(RecipeCommand::addSmeltingRecipe))))
                        .then(Commands.literal("avaritia")
                                .then(Commands.argument("result", ItemArgument.item(buildContext))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 4))
                                                        .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                                                .executes(RecipeCommand::addAvaritiaRecipe)))))))
                .then(Commands.literal("register")
                        .executes(RecipeCommand::registerRecipes))

                .then(Commands.literal("delete")
                        .then(Commands.literal("recipe")
                                .then(Commands.argument("recipe_id", ResourceLocationArgument.id())
                                        .executes(RecipeCommand::deleteRecipePermanently)))
                        .then(Commands.literal("all_deleted")
                                .then(Commands.literal("confirm")
                                        .executes(RecipeCommand::restoreAllDeletedRecipes)))
                        .then(Commands.literal("list")
                                .executes(RecipeCommand::listAllRecipes))
                        .then(Commands.literal("deleted")
                                .executes(RecipeCommand::listDeletedRecipes)))

                .then(Commands.literal("reload")
                        .then(Commands.literal("from_json")
                                .executes(RecipeCommand::reloadFromJson))
                        .executes((context) -> {
                            applyDeletionsAndReload(context);
                            return 1;
                        }))

                .then(Commands.literal("clear")
                        .executes(RecipeCommand::clearRecipes))
                .then(Commands.literal("status")
                        .executes(RecipeCommand::showDeletionStatus))

                .then(Commands.literal("help")
                        .executes(RecipeCommand::showHelp)));
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

    private static int deleteRecipePermanently(CommandContext<CommandSourceStack> context) {
        try {
            ResourceLocation recipeId = ResourceLocationArgument.getId(context, "recipe_id");

            // 检查配方是否存在
            if (!recipeExists(recipeId)) {
                context.getSource().sendFailure(Component.literal("配方不存在: " + recipeId));
                return 0;
            }

            // 获取原始配方信息用于生成空覆盖
            Recipe<?> originalRecipe = getRecipeById(recipeId);
            if (originalRecipe == null) {
                context.getSource().sendFailure(Component.literal("无法获取配方信息: " + recipeId));
                return 0;
            }

            // 生成空的覆盖配方
            boolean success = createEmptyOverrideRecipe(recipeId, originalRecipe);

            if (success) {
                // 应用覆盖使删除生效
                int overriddenCount = RecipeOverrideResolver.resolveConflictsPreferJson();

                if (overriddenCount > 0) {
                    context.getSource().sendSuccess(() -> Component.literal("§a成功永久删除配方: " + recipeId), true);
                    context.getSource().sendSuccess(() -> Component.literal("§e配方已被空配方覆盖，永久从游戏中移除"), false);

                    // 通知所有玩家
                    MinecraftServer server = context.getSource().getServer();
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.sendSystemMessage(Component.literal("§c配方已被永久删除: " + recipeId));
                    }
                } else {
                    context.getSource().sendFailure(Component.literal("生成覆盖成功，但应用覆盖失败"));
                    return 0;
                }
            } else {
                context.getSource().sendFailure(Component.literal("永久删除配方失败: " + recipeId));
                return 0;
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("永久删除配方失败: " + e.getMessage()));
            LOGGER.error("永久删除配方命令执行失败", e);
            return 0;
        }
    }

    private static int restoreAllDeletedRecipes(CommandContext<CommandSourceStack> context) {
        try {
            context.getSource().sendSuccess(() -> Component.literal("§c§l警告：正在恢复所有已删除的配方..."), true);

            List<String> allDeletedIds = RecipeJsonManager.getAllSavedRecipeIds();
            int restoredCount = (int) allDeletedIds.stream().filter(RecipeCommand::isEmptyOverrideRecipe).filter(RecipeJsonManager::deleteRecipe).count();

            if (restoredCount > 0) {
                context.getSource().sendSuccess(() -> Component.literal("§a成功恢复 " + restoredCount + " 个已删除的配方"), true);
                context.getSource().sendSuccess(() -> Component.literal("§e使用 §b/recipe_helper reload§e 使恢复生效"), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("§e没有找到已删除的配方"), false);
            }

            return restoredCount > 0 ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("恢复已删除配方失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int listDeletedRecipes(CommandContext<CommandSourceStack> context) {
        try {
            List<String> deletedRecipes = getDeletedRecipeIds();

            if (deletedRecipes.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("§e没有已删除的配方"), false);
                return 1;
            }

            context.getSource().sendSuccess(() -> Component.literal("§6=== 已删除的配方 (共 " + deletedRecipes.size() + " 个) ==="), false);

            int count = 0;
            for (String id : deletedRecipes) {
                if (count >= 20) {
                    int finalCount = count;
                    context.getSource().sendSuccess(() -> Component.literal("§e... 还有 " + (deletedRecipes.size() - finalCount) + " 个配方"), false);
                    break;
                }

                context.getSource().sendSuccess(() -> Component.literal("§c[已删除] §f" + id), false);
                count++;
            }

            context.getSource().sendSuccess(() -> Component.literal("§e使用 §b/recipe_helper delete all_deleted confirm§e 恢复所有已删除配方"), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("列出已删除配方失败: " + e.getMessage()));
            return 0;
        }
    }

    // 显示删除状态
    private static int showDeletionStatus(CommandContext<CommandSourceStack> context) {
        try {
            List<String> deletedRecipes = getDeletedRecipeIds();
            List<String> allRecipes = RecipeJsonManager.getAllSavedRecipeIds();
            int totalRecipes = getAllRecipeIds().size();

            context.getSource().sendSuccess(() -> Component.literal("§6=== 配方删除状态 ==="), false);
            context.getSource().sendSuccess(() -> Component.literal("§f游戏中总配方: §b" + totalRecipes + " §f个"), false);
            context.getSource().sendSuccess(() -> Component.literal("§f覆盖配方文件: §a" + allRecipes.size() + " §f个"), false);
            context.getSource().sendSuccess(() -> Component.literal("§f已删除配方: §c" + deletedRecipes.size() + " §f个"), false);

            if (deletedRecipes.size() > 0) {
                context.getSource().sendSuccess(() -> Component.literal("§e已删除的配方通过空覆盖实现永久移除"), false);
                context.getSource().sendSuccess(() -> Component.literal("§e使用 §b/recipe_helper delete deleted§e 查看已删除列表"), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("§a没有配方被删除"), false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("获取删除状态失败: " + e.getMessage()));
            return 0;
        }
    }

    private static void applyDeletionsAndReload(CommandContext<CommandSourceStack> context) {
        try {
            int overriddenCount = RecipeOverrideResolver.resolveConflictsPreferJson();
            if (overriddenCount > 0) {
                List<String> deletedRecipes = getDeletedRecipeIds();
                context.getSource().sendSuccess(() -> Component.literal("§a自动应用了 " + overriddenCount + " 个配方覆盖"), true);
                if (!deletedRecipes.isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.literal("§c其中 " + deletedRecipes.size() + " 个配方已被永久删除"), false);
                }
            }
            context.getSource().sendSuccess(() -> Component.literal("§a正在重载配方..."), true);
            Registerhelper.getRecipeManager().triggerClientResourceReload();
            Registerhelper.getRecipeManager().triggerServerRecipeReload();
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("重新加载失败: " + e.getMessage()));
        }
    }

    private static int addAvaritiaRecipe(CommandContext<CommandSourceStack> context) {
        ItemInput resultInput = ItemArgument.getItem(context, "result");
        int count = IntegerArgumentType.getInteger(context, "count");
        int tier = IntegerArgumentType.getInteger(context, "tier");
        String patternAndIngredients = StringArgumentType.getString(context, "pattern");
        try {
            // 验证等级
            if (tier < 1 || tier > 4) {
                context.getSource().sendFailure(Component.literal("无效的工作台等级！等级必须在1-4之间"));
                return 0;
            }

            String[] parts = patternAndIngredients.split(" ");
            int maxSize = tier * 2 + 1; // 1级=3x3, 2级=5x5, 3级=7x7, 4级=9x9

            if (parts.length < maxSize) {
                context.getSource().sendFailure(Component.literal("配方模式不完整！等级" + tier + "需要" + maxSize + "行模式"));
                return 0;
            }

            String[] pattern = new String[maxSize];
            for (int i = 0; i < maxSize; i++) {
                if (i < parts.length) {
                    // 清理引号和多余的空白字符
                    pattern[i] = parts[i].replaceAll("^\"|\"$", "").trim();
                } else {
                    pattern[i] = "";
                }
            }
            String[] materialMappings = Arrays.copyOfRange(parts, maxSize, parts.length);

            Object[] ingredients = new Object[materialMappings.length * 2];
            for (int i = 0; i < materialMappings.length; i++) {
                String[] mapping = materialMappings[i].split(":");
                if (mapping.length < 3) continue;

                char symbol = mapping[0].charAt(0);
                String namespace = mapping[1];
                String itemName = mapping[2];

                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, itemName));
                if (item != null && item != Items.AIR) {
                    ingredients[i * 2] = symbol;
                    ingredients[i * 2 + 1] = item;
                }
            }

            ItemStack result = new ItemStack(resultInput.getItem(), count);

            // 使用SuperRuntimeRecipeManager添加Avaritia配方
            if (Registerhelper.getRecipeManager() != null) {
                Registerhelper.getRecipeManager().addAvaritiaTableRecipe(result, tier, pattern, ingredients);
                context.getSource().sendSuccess(() -> Component.literal("§a成功添加Avaritia工作台配方: " + result.getDisplayName().getString() + " x" + count + " (等级" + tier + ")"), true);
            } else {
                context.getSource().sendFailure(Component.literal("SuperRuntimeRecipeManager未初始化"));
            }

            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("添加Avaritia配方失败: " + e.getMessage()));
            return 0;
        }
    }

    // 保留原有的方法...
    private static int addShapedRecipe(CommandContext<CommandSourceStack> context) {
        ItemInput resultInput = ItemArgument.getItem(context, "result");
        int count = IntegerArgumentType.getInteger(context, "count");
        String patternAndIngredients = StringArgumentType.getString(context, "pattern");

        try {
            String[] parts = patternAndIngredients.split(" ", 4);
            if (parts.length < 4) {
                context.getSource().sendFailure(Component.literal("格式错误！使用: /recipe_helper add shaped <物品> <数量> <行1> <行2> <行3> <材料映射>"));
                return 0;
            }

            String[] pattern = {parts[0], parts[1], parts[2]};
            String[] materialMappings = parts[3].split(" ");

            Object[] ingredients = new Object[materialMappings.length * 2];
            for (int i = 0; i < materialMappings.length; i++) {
                String[] mapping = materialMappings[i].split(":");
                if (mapping.length < 3) continue;

                char symbol = mapping[0].charAt(0);
                String namespace = mapping[1];
                String itemName = mapping[2];

                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, itemName));
                if (item != null && item != Items.AIR) {
                    ingredients[i * 2] = symbol;
                    ingredients[i * 2 + 1] = item;
                }
            }

            ItemStack result = new ItemStack(resultInput.getItem(), count);
            Registerhelper.getRecipeManager().addShapedRecipe(result, pattern, ingredients);

            context.getSource().sendSuccess(() -> Component.literal("§a成功添加有形状配方: " + result.getDisplayName().getString() + " x" + count), true);
            context.getSource().sendSuccess(() -> Component.literal("§e配方已保存到 config/recipes/ 目录"), false);
            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("添加配方失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int addShapelessRecipe(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemInput resultInput = ItemArgument.getItem(context, "result");
        int count = IntegerArgumentType.getInteger(context, "count");
        String ingredientsStr = StringArgumentType.getString(context, "ingredients");

        try {
            String[] ingredientNames = ingredientsStr.split(" ");
            Object[] ingredients = new Object[ingredientNames.length];

            for (int i = 0; i < ingredientNames.length; i++) {
                String[] parts = ingredientNames[i].split(":");
                if (parts.length >= 2) {
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(parts[0], parts[1]));
                    if (item != null && item != Items.AIR) {
                        ingredients[i] = item;
                    }
                }
            }

            ItemStack result = new ItemStack(resultInput.getItem(), count);
            Registerhelper.getRecipeManager().addShapelessRecipe(result, ingredients);

            context.getSource().sendSuccess(() -> Component.literal("§a成功添加无形状配方: " + result.getDisplayName().getString() + " x" + count), true);
            context.getSource().sendSuccess(() -> Component.literal("§e配方已保存到 config/recipes/ 目录"), false);
            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("添加配方失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int addSmeltingRecipe(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemInput ingredientInput = ItemArgument.getItem(context, "ingredient");
        ItemInput resultInput = ItemArgument.getItem(context, "result");

        try {
            Registerhelper.getRecipeManager().addSmeltingRecipe(
                    ingredientInput.getItem(),
                    resultInput.getItem(),
                    0.7f,
                    200
            );

            context.getSource().sendSuccess(() -> Component.literal("§a成功添加熔炼配方: " +
                    ingredientInput.getItem().getDescription().getString() + " -> " +
                    resultInput.getItem().getDescription().getString()), true);
            context.getSource().sendSuccess(() -> Component.literal("§e配方已保存到 config/recipes/ 目录"), false);
            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("添加熔炼配方失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int registerRecipes(CommandContext<CommandSourceStack> context) {
        try {
            int count = Registerhelper.getRecipeManager().getPendingRecipeCount();
            if (count > 0) {
                Registerhelper.getRecipeManager().registerRecipes();
                context.getSource().sendSuccess(() -> Component.literal("§a成功注册 " + count + " 个配方到游戏中！"), true);
            } else {
                context.getSource().sendFailure(Component.literal("没有待注册的配方！"));
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("注册配方失败: " + e.getMessage()));
            return 0;
        }
    }

    // 列出所有配方
    private static int listAllRecipes(CommandContext<CommandSourceStack> context) {
        try {
            List<ResourceLocation> recipeIds = getAllRecipeIds();

            context.getSource().sendSuccess(() -> Component.literal("§6=== 所有配方列表 (共 " + recipeIds.size() + " 个) ==="), false);

            int count = 0;
            for (ResourceLocation id : recipeIds) {
                if (count >= 20) { // 限制显示数量，避免聊天框刷屏
                    int finalCount = count;
                    context.getSource().sendSuccess(() -> Component.literal("§e... 还有 " + (recipeIds.size() - finalCount) + " 个配方（数量过多，只显示前20个）"), false);
                    break;
                }

                String prefix = "§f";
                if (id.getNamespace().equals("minecraft")) {
                    prefix = "§a[原版] ";
                } else if (id.toString().contains("_recipe")) {
                    prefix = "§b[自定义] ";
                } else {
                    prefix = "§e[Mod] ";
                }

                final String displayText = prefix + id.toString();
                context.getSource().sendSuccess(() -> Component.literal(displayText), false);
                count++;
            }

            context.getSource().sendSuccess(() -> Component.literal("§6使用 /recipe_helper delete recipe <配方ID> 永久删除指定配方"), false);
            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("获取配方列表失败: " + e.getMessage()));
            return 0;
        }
    }

    // 从JSON重新加载配方
    private static int reloadFromJson(CommandContext<CommandSourceStack> context) {
        try {
            context.getSource().sendSuccess(() -> Component.literal("§e正在从JSON文件重新加载配方..."), true);

            Registerhelper.getRecipeManager().reloadFromJson();

            int pendingCount = Registerhelper.getRecipeManager().getPendingRecipeCount();
            if (pendingCount > 0) {
                context.getSource().sendSuccess(() -> Component.literal("§a成功从JSON加载 " + pendingCount + " 个配方"), true);
                context.getSource().sendSuccess(() -> Component.literal("§e使用 /recipe_helper register 注册这些配方"), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("§c没有找到有效的JSON配方文件"), false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("从JSON重新加载失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int clearRecipes(CommandContext<CommandSourceStack> context) {
        int count = Registerhelper.getRecipeManager().getPendingRecipeCount();
        Registerhelper.getRecipeManager().clearPendingRecipes();
        context.getSource().sendSuccess(() -> Component.literal("§e已清空 " + count + " 个待注册配方！"), true);
        return 1;
    }

    // 帮助指令 - 更新以反映新的删除逻辑
    private static int showHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("§6========== Recipe Helper 帮助 =========="), false);
        context.getSource().sendSuccess(() -> Component.literal("§f§l图形界面:"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper gui - 打开配方编辑器GUI"), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("§f§l添加配方:"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper add shaped <物品> <数量> <模式> <材料>"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper add shapeless <物品> <数量> <材料列表>"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper add smelting <原料> <结果>"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper add avaritia <物品> <数量> <等级> <模式> <材料>"), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("§f§l永久删除配方:"), false);
        context.getSource().sendSuccess(() -> Component.literal("§c  /recipe_helper delete recipe <配方ID> - 永久删除配方"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper delete deleted - 列出已删除的配方"), false);
        context.getSource().sendSuccess(() -> Component.literal("§c  /recipe_helper delete all_deleted confirm - 恢复所有已删除配方"), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("§f§l配方管理:"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper register - 注册待注册配方"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper reload - 重新加载配方"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper status - 显示删除状态"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper delete list - 列出所有配方"), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("§c删除配方会通过生成空覆盖实现永久移除！"), false);
        context.getSource().sendSuccess(() -> Component.literal("§e删除文件保存在 config/recipes/ 目录"), false);

        return 1;
    }

    /**
     * 删除配方的公共接口 - 供其他类调用
     * 现在使用永久删除逻辑（生成空覆盖配方）
     */
    public static boolean deleteRecipe(ResourceLocation recipeId) {
        try {
            if (!recipeExists(recipeId)) {
                LOGGER.warn("尝试删除不存在的配方: " + recipeId);
                return false;
            }
            Recipe<?> originalRecipe = getRecipeById(recipeId);
            if (originalRecipe == null) {
                LOGGER.error("无法获取配方信息进行删除: " + recipeId);
                return false;
            }
            boolean success = createEmptyOverrideRecipe(recipeId, originalRecipe);
            if (success) {
                int overriddenCount = RecipeOverrideResolver.resolveConflictsPreferJson();
                if (overriddenCount > 0) {
                    return true;
                } else {
                    LOGGER.error("生成覆盖成功，但应用覆盖失败: " + recipeId);
                    return false;
                }
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("永久删除配方失败: " + recipeId, e);
            return false;
        }
    }

    private static boolean recipeExists(ResourceLocation recipeId) {
        return RecipeUtil.recipeExists(recipeId);
    }

    private static Recipe<?> getRecipeById(ResourceLocation recipeId) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerLevel serverLevel = server.overworld();
                RecipeManager recipeManager = serverLevel.getRecipeManager();

                return recipeManager.byKey(recipeId).orElse(null);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean createEmptyOverrideRecipe(ResourceLocation recipeId, Recipe<?> originalRecipe) {
        try {
            String recipeType = getRecipeTypeString(originalRecipe);
            RecipeJsonManager.RecipeData emptyData = createEmptyRecipeData(recipeId.toString(), recipeType, originalRecipe);
            ItemStack emptyResult = new ItemStack(Items.AIR, 0);
            List<ItemStack> emptyIngredients = new ArrayList<>();
            return RecipeJsonManager.saveRecipe(recipeId.toString(), emptyData, emptyResult, emptyIngredients);
        } catch (Exception e) {
            LOGGER.error("创建空覆盖配方失败", e);
            return false;
        }
    }

    private static RecipeJsonManager.RecipeData createEmptyRecipeData(String recipeId, String recipeType, Recipe<?> originalRecipe) {
        RecipeJsonManager.RecipeData data = new RecipeJsonManager.RecipeData();
        data.id = recipeId;
        data.type = recipeType;
        data.result = "minecraft:air";
        data.count = 0;
        switch (recipeType) {
            case "shaped":
                data.pattern = new String[]{};
                data.materialMapping = new Object[]{};
                break;
            case "shapeless":
                data.ingredients = new String[]{};
                break;
            case "smelting":
                data.ingredients = new String[]{"minecraft:air"};
                data.experience = 0.0f;
                data.cookingTime = 200;
                break;
            case "avaritia_shaped":
                data.pattern = new String[]{};
                data.materialMapping = new Object[]{};
                data.tier = 1;
                break;
            case "avaritia_shapeless":
                data.ingredients = new String[]{};
                data.tier = 1;
                break;
            default:
                data.ingredients = new String[]{};
                break;
        }

        return data;
    }

    // 检查是否为空覆盖配方（用于恢复功能）
    private static boolean isEmptyOverrideRecipe(String recipeId) {
        try {
            RecipeJsonManager.RecipeData data = RecipeJsonManager.loadRecipe(recipeId);
            return data != null && "minecraft:air".equals(data.result) && data.count == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // 获取已删除的配方ID列表
    private static List<String> getDeletedRecipeIds() {
        List<String> deletedIds = new ArrayList<>();
        List<String> allSavedIds = RecipeJsonManager.getAllSavedRecipeIds();

        for (String recipeId : allSavedIds) {
            if (isEmptyOverrideRecipe(recipeId)) {
                deletedIds.add(recipeId);
            }
        }

        return deletedIds;
    }

    private static String getRecipeTypeString(Recipe<?> recipe) {
        String typeName = recipe.getType().toString().toLowerCase();
        if (typeName.contains("crafting_shaped")) return "shaped";
        if (typeName.contains("crafting_shapeless")) return "shapeless";
        if (typeName.contains("smelting")) return "smelting";
        if (typeName.contains("avaritia")) {
            if (typeName.contains("shaped")) return "avaritia_shaped";
            return "avaritia_shapeless";
        }
        return "unknown";
    }
}