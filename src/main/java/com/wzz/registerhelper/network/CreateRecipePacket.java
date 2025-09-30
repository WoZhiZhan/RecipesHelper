package com.wzz.registerhelper.network;

import com.wzz.registerhelper.recipe.UniversalRecipeManager;
import com.wzz.registerhelper.recipe.RecipeRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 简化的配方创建网络包 - 替换复杂的旧版本
 */
public class CreateRecipePacket {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateRecipePacket.class);

    private final String modId;
    private final String recipeType;
    private final String recipeId;
    private final ItemStack result;
    private final String[] pattern;
    private final String[] ingredientItems;
    private final Map<String, Object> properties;

    /**
     * 通用构造函数
     */
    public CreateRecipePacket(String modId, String recipeType, String recipeId,
                              ItemStack result, String[] pattern, String[] ingredientItems,
                              Map<String, Object> properties) {
        this.modId = modId;
        this.recipeType = recipeType;
        this.recipeId = recipeId;
        this.result = result;
        this.pattern = pattern;
        this.ingredientItems = ingredientItems;
        this.properties = properties != null ? properties : new HashMap<>();
    }

    /**
     * 便捷构造函数 - 有形状配方
     */
    public static CreateRecipePacket shaped(String modId, String recipeId, ItemStack result,
                                            String[] pattern, String[] ingredientItems) {
        return new CreateRecipePacket(modId, "shaped", recipeId, result, pattern, ingredientItems, null);
    }

    /**
     * 便捷构造函数 - 无形状配方
     */
    public static CreateRecipePacket shapeless(String modId, String recipeId, ItemStack result,
                                               String[] ingredientItems) {
        return new CreateRecipePacket(modId, "shapeless", recipeId, result, null, ingredientItems, null);
    }

    /**
     * 便捷构造函数 - 烹饪配方
     */
    public static CreateRecipePacket cooking(String modId, String cookingType, String recipeId,
                                             ItemStack result, String ingredient, float experience, int cookingTime) {
        Map<String, Object> props = new HashMap<>();
        props.put("experience", experience);
        props.put("cookingTime", cookingTime);

        return new CreateRecipePacket(modId, cookingType, recipeId, result, null,
                new String[]{ingredient}, props);
    }

    /**
     * 便捷构造函数 - Avaritia配方
     */
    public static CreateRecipePacket avaritia(String recipeType, String recipeId, ItemStack result,
                                              String[] pattern, String[] ingredientItems, int tier) {
        Map<String, Object> props = new HashMap<>();
        props.put("tier", tier);

        return new CreateRecipePacket("avaritia", recipeType, recipeId, result, pattern,
                ingredientItems, props);
    }

    /**
     * 网络序列化
     */
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(modId);
        buf.writeUtf(recipeType);
        buf.writeUtf(recipeId);
        buf.writeItem(result);

        // 写入pattern
        if (pattern != null) {
            buf.writeBoolean(true);
            buf.writeInt(pattern.length);
            for (String row : pattern) {
                buf.writeUtf(row);
            }
        } else {
            buf.writeBoolean(false);
        }

        // 写入ingredients
        if (ingredientItems != null) {
            buf.writeBoolean(true);
            buf.writeInt(ingredientItems.length);
            for (String ingredient : ingredientItems) {
                buf.writeUtf(ingredient);
            }
        } else {
            buf.writeBoolean(false);
        }

        // 写入properties
        buf.writeInt(properties.size());
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            buf.writeUtf(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof Integer) {
                buf.writeUtf("int");
                buf.writeInt((Integer) value);
            } else if (value instanceof Float) {
                buf.writeUtf("float");
                buf.writeFloat((Float) value);
            } else if (value instanceof String) {
                buf.writeUtf("string");
                buf.writeUtf((String) value);
            } else if (value instanceof Boolean) {
                buf.writeUtf("boolean");
                buf.writeBoolean((Boolean) value);
            } else {
                buf.writeUtf("string");
                buf.writeUtf(value.toString());
            }
        }
    }

    /**
     * 网络反序列化
     */
    public static CreateRecipePacket fromBytes(FriendlyByteBuf buf) {
        String modId = buf.readUtf();
        String recipeType = buf.readUtf();
        String recipeId = buf.readUtf();
        ItemStack result = buf.readItem();

        // 读取pattern
        String[] pattern = null;
        if (buf.readBoolean()) {
            int patternLength = buf.readInt();
            pattern = new String[patternLength];
            for (int i = 0; i < patternLength; i++) {
                pattern[i] = buf.readUtf();
            }
        }

        // 读取ingredients
        String[] ingredientItems = null;
        if (buf.readBoolean()) {
            int ingredientLength = buf.readInt();
            ingredientItems = new String[ingredientLength];
            for (int i = 0; i < ingredientLength; i++) {
                ingredientItems[i] = buf.readUtf();
            }
        }

        // 读取properties
        Map<String, Object> properties = new HashMap<>();
        int propertyCount = buf.readInt();
        for (int i = 0; i < propertyCount; i++) {
            String key = buf.readUtf();
            String type = buf.readUtf();

            Object value = switch (type) {
                case "int" -> buf.readInt();
                case "float" -> buf.readFloat();
                case "string" -> buf.readUtf();
                case "boolean" -> buf.readBoolean();
                default -> buf.readUtf();
            };

            properties.put(key, value);
        }

        return new CreateRecipePacket(modId, recipeType, recipeId, result, pattern, ingredientItems, properties);
    }

    /**
     * 处理网络包 - 核心逻辑
     */
    public static void handle(CreateRecipePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            try {
                // 验证权限
                if (context.getSender() == null || !context.getSender().hasPermissions(2)) {
                    if (context.getSender() != null) {
                        context.getSender().sendSystemMessage(Component.literal("§c您没有权限创建配方"));
                    }
                    return;
                }

                // 构建配方请求
                RecipeRequest request = buildRecipeRequest(packet);
                if (request == null) {
                    context.getSender().sendSystemMessage(Component.literal("§c无效的配方数据"));
                    return;
                }

                // 创建配方
                boolean success = UniversalRecipeManager.getInstance().createRecipe(request);

                // 发送结果消息
                if (success) {
                    context.getSender().sendSystemMessage(
                            Component.literal("§a配方创建成功: " + packet.recipeId + " 使用 /reload 刷新配方")
                    );
                    LOGGER.info("玩家 {} 创建了配方: {} ({})",
                            context.getSender().getName().getString(), packet.recipeId, packet.modId);
                } else {
                    context.getSender().sendSystemMessage(Component.literal("§c配方创建失败"));
                    LOGGER.warn("配方创建失败: {} ({})", packet.recipeId, packet.modId);
                }

            } catch (Exception e) {
                LOGGER.error("处理配方包时发生错误", e);
                if (context.getSender() != null) {
                    context.getSender().sendSystemMessage(
                            Component.literal("§c处理配方时发生错误: " + e.getMessage())
                    );
                }
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * 构建配方请求对象
     */
    private static RecipeRequest buildRecipeRequest(CreateRecipePacket packet) {
        try {
            RecipeRequest request = new RecipeRequest();
            request.modId = packet.modId;
            request.recipeType = packet.recipeType;
            request.recipeId = packet.recipeId;
            request.result = packet.result;
            request.resultCount = packet.result.getCount();
            request.pattern = packet.pattern;
            request.properties = new HashMap<>(packet.properties);

            // 转换材料字符串为对象
            if (packet.ingredientItems != null) {
                if (isShapedRecipe(packet.recipeType)) {
                    // 有形状配方：转换为符号-物品映射
                    request.ingredients = convertToShapedIngredients(packet.ingredientItems);
                } else {
                    // 无形状/烹饪配方：直接转换为物品数组
                    request.ingredients = convertToSimpleIngredients(packet.ingredientItems);
                }
            }

            return request;

        } catch (Exception e) {
            LOGGER.error("构建配方请求失败", e);
            return null;
        }
    }

    /**
     * 检查是否是有形状配方
     */
    private static boolean isShapedRecipe(String recipeType) {
        return recipeType.contains("shaped");
    }

    /**
     * 转换为有形状配方的材料格式 (char, item, char, item, ...)
     */
    private static Object[] convertToShapedIngredients(String[] ingredientItems) {
        Object[] result = new Object[ingredientItems.length];

        for (int i = 0; i < ingredientItems.length; i += 2) {
            if (i + 1 < ingredientItems.length) {
                // 符号 (char)
                result[i] = ingredientItems[i].charAt(0);
                // 物品 (string，将在处理器中转换为Item)
                result[i + 1] = ingredientItems[i + 1];
            }
        }

        return result;
    }

    /**
     * 转换为简单的材料格式
     */
    private static Object[] convertToSimpleIngredients(String[] ingredientItems) {
        Object[] result = new Object[ingredientItems.length];
        System.arraycopy(ingredientItems, 0, result, 0, ingredientItems.length);
        return result;
    }
}