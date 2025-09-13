package com.wzz.registerhelper.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wzz.registerhelper.init.ModNetwork;
import com.wzz.registerhelper.network.OpenGUIPacket;
import com.wzz.registerhelper.Registerhelper;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.ResourceLocationArgument;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.*;

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
                .then(Commands.literal("reload")
                        .executes((context) -> {
                            Registerhelper.getRecipeManager().triggerClientResourceReload();
                            Registerhelper.getRecipeManager().triggerServerRecipeReload();
                            MinecraftServer server = context.getSource().getServer();
                            server.getCommands().performPrefixedCommand(
                                    context.getSource(), "reload"
                            );
                            return 1;
                        }))
                .then(Commands.literal("delete")
                        .then(Commands.literal("recipe")
                                .then(Commands.argument("recipe_id", ResourceLocationArgument.id())
                                        .executes(RecipeCommand::deleteSpecificRecipe)))
                        .then(Commands.literal("all")
                                .then(Commands.literal("confirm")
                                        .executes(RecipeCommand::deleteAllRecipes)))
                        .then(Commands.literal("list")
                                .executes(RecipeCommand::listAllRecipes)))

                .then(Commands.literal("reload")
                        .then(Commands.literal("from_json")
                                .executes(RecipeCommand::reloadFromJson)))

                .then(Commands.literal("clear")
                        .executes(RecipeCommand::clearRecipes))

                .then(Commands.literal("count")
                        .executes(RecipeCommand::countRecipes))

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

    // 删除指定配方
    private static int deleteSpecificRecipe(CommandContext<CommandSourceStack> context) {
        try {
            ResourceLocation recipeId = ResourceLocationArgument.getId(context, "recipe_id");

            boolean success = deleteRecipe(recipeId);

            if (success) {
                context.getSource().sendSuccess(() -> Component.literal("§a成功删除配方: " + recipeId), true);

                // 通知所有玩家
                MinecraftServer server = context.getSource().getServer();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.sendSystemMessage(Component.literal("§e使用 §b/recipe_helper reload§e 刷新"));
                }
            } else {
                context.getSource().sendFailure(Component.literal("配方不存在或删除失败: " + recipeId));
            }

            return success ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("删除配方失败: " + e.getMessage()));
            return 0;
        }
    }

    // 删除所有配方（危险操作）
    private static int deleteAllRecipes(CommandContext<CommandSourceStack> context) {
        try {
            context.getSource().sendSuccess(() -> Component.literal("§c§l警告：正在删除所有配方..."), true);

            int deletedCount = deleteAllRecipesInternal();

            if (deletedCount > 0) {
                context.getSource().sendSuccess(() -> Component.literal("§c已删除所有配方，共 " + deletedCount + " 个"), true);
                context.getSource().sendSuccess(() -> Component.literal("§e包括原版、mod和自定义配方"), false);

                // 警告所有玩家
                MinecraftServer server = context.getSource().getServer();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.sendSystemMessage(Component.literal("§c§l[严重警告] 所有合成配方已被管理员删除！"));
                    player.sendSystemMessage(Component.literal("§e这包括原版和mod的所有配方"));
                    player.sendSystemMessage(Component.literal("§f请按 §bF3+T§f 刷新JEI以更新显示"));
                }
            } else {
                context.getSource().sendFailure(Component.literal("删除失败或没有配方可删除"));
            }

            return deletedCount > 0 ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("删除所有配方失败: " + e.getMessage()));
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

            context.getSource().sendSuccess(() -> Component.literal("§6使用 /recipe_helper delete recipe <配方ID> 删除指定配方"), false);
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

    private static int countRecipes(CommandContext<CommandSourceStack> context) {
        int pendingCount = Registerhelper.getRecipeManager().getPendingRecipeCount();
        List<ResourceLocation> allRecipes = getAllRecipeIds();

        context.getSource().sendSuccess(() -> Component.literal("§6=== 配方统计 ==="), false);
        context.getSource().sendSuccess(() -> Component.literal("§f待注册配方: §a" + pendingCount + " §f个"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f游戏中总配方: §b" + allRecipes.size() + " §f个"), false);

        return 1;
    }

    // 帮助指令 - 更新以包含GUI命令
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
        context.getSource().sendSuccess(() -> Component.literal("§f§l管理配方:"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper register - 注册待添加的配方"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper clear - 清空待注册配方"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper count - 查看配方统计"), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("§f§l删除配方:"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper delete recipe <配方ID> - 删除指定配方"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper delete list - 列出所有配方"), false);
        context.getSource().sendSuccess(() -> Component.literal("§c  /recipe_helper delete all confirm - 删除所有配方"), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("§f§lJSON功能:"), false);
        context.getSource().sendSuccess(() -> Component.literal("§f  /recipe_helper reload from_json - 从JSON重新加载"), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("§a推荐使用GUI界面进行配方编辑，更加直观易用！"), false);
        context.getSource().sendSuccess(() -> Component.literal("§e注意: 配方自动保存到 config/recipes/ 目录"), false);

        return 1;
    }

    /**
     * 删除配方 - 同时从内存和JSON文件中删除
     * @param recipeId 要删除的配方ID
     * @return 是否删除成功
     */
    public static boolean deleteRecipe(ResourceLocation recipeId) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                LOGGER.error("服务器实例为空，无法删除配方");
                return false;
            }

            boolean deletedFromMemory = deleteFromMemory(server, recipeId);
            boolean deletedFromJson = deleteFromJson(recipeId);

            if (deletedFromMemory || deletedFromJson) {
                // 同步到所有客户端
                syncRecipesToClients(server);

                String status = "";
                if (deletedFromMemory && deletedFromJson) {
                    status = "从内存和JSON文件中";
                } else if (deletedFromMemory) {
                    status = "从内存中";
                } else if (deletedFromJson) {
                    status = "从JSON文件中";
                }

                LOGGER.info("成功{}删除配方: {}", status, recipeId);
                return true;
            }

            LOGGER.warn("配方未找到: {}", recipeId);
            return false;

        } catch (Exception e) {
            LOGGER.error("删除配方失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 从内存（RecipeManager）中删除配方
     */
    @SuppressWarnings("unchecked")
    private static boolean deleteFromMemory(MinecraftServer server, ResourceLocation recipeId) {
        try {
            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();

            Field recipesField;
            try {
                recipesField = RecipeManager.class.getDeclaredField("f_44007_");
            } catch (NoSuchFieldException e) {
                recipesField = RecipeManager.class.getDeclaredField("recipes");
            }
            recipesField.setAccessible(true);

            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> currentRecipes =
                    (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) recipesField.get(recipeManager);

            // 拷贝成新的可变 Map
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> newRecipes = new HashMap<>();
            boolean removed = false;

            for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> entry : currentRecipes.entrySet()) {
                Map<ResourceLocation, Recipe<?>> typeRecipes = new HashMap<>(entry.getValue());
                if (typeRecipes.remove(recipeId) != null) {
                    removed = true;
                    LOGGER.debug("从内存中删除配方: {}", recipeId);
                }
                newRecipes.put(entry.getKey(), typeRecipes);
            }

            if (removed) {
                recipesField.set(recipeManager, newRecipes);
            }

            return removed;

        } catch (Exception e) {
            LOGGER.error("从内存中删除配方失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 从JSON文件中删除配方
     */
    private static boolean deleteFromJson(ResourceLocation recipeId) {
        try {
            boolean deleted = RecipeJsonManager.deleteRecipe(recipeId.toString());
            if (deleted) {
                LOGGER.debug("从JSON文件中删除配方: {}", recipeId);
            }
            return deleted;
        } catch (Exception e) {
            LOGGER.error("从JSON文件中删除配方失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 同步配方到所有客户端
     */
    private static void syncRecipesToClients(MinecraftServer server) {
        try {
            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();

            ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(recipeManager.getRecipes());

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(packet);
            }

            LOGGER.debug("已同步配方到所有客户端");
        } catch (Exception e) {
            LOGGER.error("同步配方到客户端失败", e);
        }
    }

    /**
     * 批量删除配方
     * @param recipeIds 要删除的配方ID列表
     * @return 成功删除的数量
     */
    public static int deleteRecipes(ResourceLocation... recipeIds) {
        int deletedCount = 0;

        for (ResourceLocation recipeId : recipeIds) {
            if (deleteRecipe(recipeId)) {
                deletedCount++;
            }
        }

        LOGGER.info("批量删除完成，成功删除 {} 个配方", deletedCount);
        return deletedCount;
    }

    /**
     * 清空所有自定义配方
     * @return 删除的配方数量
     */
    public static int clearAllCustomRecipes() {
        try {
            List<String> allRecipeIds = RecipeJsonManager.getAllSavedRecipeIds();
            int count = 0;

            for (String recipeIdStr : allRecipeIds) {
                try {
                    ResourceLocation recipeId = new ResourceLocation(recipeIdStr);
                    if (deleteRecipe(recipeId)) {
                        count++;
                    }
                } catch (Exception e) {
                    LOGGER.warn("处理配方ID时出错: " + recipeIdStr, e);
                }
            }

            LOGGER.info("清空完成，删除了 {} 个自定义配方", count);
            return count;

        } catch (Exception e) {
            LOGGER.error("清空自定义配方失败", e);
            return 0;
        }
    }

    /**
     * 检查配方是否存在（检查内存或JSON文件）
     */
    public static boolean recipeExists(ResourceLocation recipeId) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

            // 检查内存中是否存在
            if (server != null) {
                ServerLevel serverLevel = server.overworld();
                RecipeManager recipeManager = serverLevel.getRecipeManager();

                for (Recipe<?> recipe : recipeManager.getRecipes()) {
                    if (recipe.getId().equals(recipeId)) {
                        return true;
                    }
                }
            }

            // 检查JSON文件中是否存在
            return RecipeJsonManager.recipeFileExists(recipeId.toString());

        } catch (Exception e) {
            LOGGER.error("检查配方存在性失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 获取所有自定义配方的ID
     */
    public static ResourceLocation[] getAllCustomRecipeIds() {
        try {
            List<String> stringIds = RecipeJsonManager.getAllSavedRecipeIds();
            ResourceLocation[] result = new ResourceLocation[stringIds.size()];

            for (int i = 0; i < stringIds.size(); i++) {
                try {
                    result[i] = new ResourceLocation(stringIds.get(i));
                } catch (Exception e) {
                    LOGGER.warn("解析配方ID失败: " + stringIds.get(i), e);
                    result[i] = new ResourceLocation("registerhelper", "invalid_" + i);
                }
            }

            return result;

        } catch (Exception e) {
            LOGGER.error("获取自定义配方ID失败", e);
            return new ResourceLocation[0];
        }
    }

    /**
     * 重新加载所有配方
     */
    public static void reloadAllRecipes() {
        try {
            LOGGER.info("开始重新加载所有配方...");
            RecipeJsonManager.reloadAllSavedRecipes();
            LOGGER.info("配方重新加载完成");
        } catch (Exception e) {
            LOGGER.error("重新加载配方失败", e);
        }
    }

    /**
     * 获取配方统计信息
     */
    public static String getRecipeStats() {
        try {
            List<String> savedRecipes = RecipeJsonManager.getAllSavedRecipeIds();
            int jsonCount = savedRecipes.size();

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            int memoryCount = 0;

            if (server != null) {
                ServerLevel serverLevel = server.overworld();
                RecipeManager recipeManager = serverLevel.getRecipeManager();
                memoryCount = recipeManager.getRecipes().size();
            }

            return String.format("JSON文件中的配方: %d, 内存中的配方: %d", jsonCount, memoryCount);

        } catch (Exception e) {
            LOGGER.error("获取配方统计信息失败", e);
            return "无法获取配方统计信息";
        }
    }

    // 删除所有配方
    private static int deleteAllRecipesInternal() {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return 0;
            }

            ServerLevel serverLevel = server.overworld();
            RecipeManager recipeManager = serverLevel.getRecipeManager();

            Field recipesField;
            try {
                recipesField = RecipeManager.class.getDeclaredField("f_44007_");
            } catch (NoSuchFieldException e) {
                recipesField = RecipeManager.class.getDeclaredField("recipes");
            }

            recipesField.setAccessible(true);
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> currentRecipes =
                    (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) recipesField.get(recipeManager);

            int totalDeleted = 0;

            // 计算总配方数
            for (Map<ResourceLocation, Recipe<?>> typeRecipes : currentRecipes.values()) {
                totalDeleted += typeRecipes.size();
            }

            // 清空所有配方
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> emptyRecipes = new HashMap<>();
            for (RecipeType<?> type : currentRecipes.keySet()) {
                emptyRecipes.put(type, new HashMap<>());
            }

            recipesField.set(recipeManager, emptyRecipes);

            // 同步到所有客户端
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(recipeManager.getRecipes());
                player.connection.send(packet);
            }

            LOGGER.warn("警告：已删除所有配方，共 " + totalDeleted + " 个");
            return totalDeleted;

        } catch (Exception e) {
            LOGGER.error("删除所有配方失败", e);
            return 0;
        }
    }

    // 获取所有配方ID
    private static List<ResourceLocation> getAllRecipeIds() {
        List<ResourceLocation> recipeIds = new ArrayList<>();

        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ServerLevel serverLevel = server.overworld();
                RecipeManager recipeManager = serverLevel.getRecipeManager();

                Field recipesField;
                try {
                    recipesField = RecipeManager.class.getDeclaredField("f_44007_");
                } catch (NoSuchFieldException e) {
                    recipesField = RecipeManager.class.getDeclaredField("recipes");
                }

                recipesField.setAccessible(true);
                Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> currentRecipes =
                        (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) recipesField.get(recipeManager);

                for (Map<ResourceLocation, Recipe<?>> typeRecipes : currentRecipes.values()) {
                    recipeIds.addAll(typeRecipes.keySet());
                }
            }
        } catch (Exception e) {
            LOGGER.error("获取配方列表失败", e);
        }

        return recipeIds;
    }
}