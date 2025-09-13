package com.wzz.registerhelper.network;

import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.Registerhelper;
import com.wzz.registerhelper.core.RecipeJsonManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CreateRecipePacket {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum RecipeType {
        SHAPED,
        SHAPELESS,
        SMELTING,
        AVARITIA_SHAPED,
        AVARITIA_SHAPELESS,
    }

    private final RecipeType recipeType;
    private final ItemStack result;
    private final String[] pattern;
    private final Object[] ingredients;
    private final int avaritiaTeir;
    private final ResourceLocation editingRecipeId;

    public CreateRecipePacket(RecipeType recipeType, ItemStack result, String[] pattern,
                              Object[] ingredients, int avaritiaTeir, @Nullable ResourceLocation editingRecipeId) {
        this.recipeType = recipeType;
        this.result = result;
        this.pattern = pattern;
        this.ingredients = ingredients;
        this.avaritiaTeir = avaritiaTeir;
        this.editingRecipeId = editingRecipeId;
    }

    public CreateRecipePacket(RecipeType recipeType, ItemStack result, String[] pattern,
                              Object[] ingredients, int avaritiaTeir) {
        this(recipeType, result, pattern, ingredients, avaritiaTeir, null);
    }

    public CreateRecipePacket(FriendlyByteBuf buf) {
        this.recipeType = buf.readEnum(RecipeType.class);
        this.result = buf.readItem();

        if (buf.readBoolean()) {
            int patternLength = buf.readInt();
            this.pattern = new String[patternLength];
            for (int i = 0; i < patternLength; i++) {
                this.pattern[i] = buf.readUtf();
            }
        } else {
            this.pattern = null;
        }

        int ingredientCount = buf.readInt();
        this.ingredients = new Object[ingredientCount];
        for (int i = 0; i < ingredientCount; i++) {
            String type = buf.readUtf();
            switch (type) {
                case "item":
                    ResourceLocation itemId = buf.readResourceLocation();
                    Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    this.ingredients[i] = item != null ? item : Items.AIR;
                    break;
                case "char":
                    this.ingredients[i] = buf.readChar();
                    break;
                case "float":
                    this.ingredients[i] = buf.readFloat();
                    break;
                case "int":
                    this.ingredients[i] = buf.readInt();
                    break;
                default:
                    LOGGER.warn("未知的ingredient类型: " + type);
                    this.ingredients[i] = Items.AIR;
            }
        }

        this.avaritiaTeir = buf.readInt();

        if (buf.readBoolean()) {
            this.editingRecipeId = buf.readResourceLocation();
        } else {
            this.editingRecipeId = null;
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(recipeType);
        buf.writeItem(result);

        if (pattern != null) {
            buf.writeBoolean(true);
            buf.writeInt(pattern.length);
            for (String s : pattern) {
                buf.writeUtf(s);
            }
        } else {
            buf.writeBoolean(false);
        }

        buf.writeInt(ingredients.length);
        for (Object ingredient : ingredients) {
            if (ingredient instanceof Item item) {
                buf.writeUtf("item");
                buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(item));
            } else if (ingredient instanceof Character ch) {
                buf.writeUtf("char");
                buf.writeChar(ch);
            } else if (ingredient instanceof Float f) {
                buf.writeUtf("float");
                buf.writeFloat(f);
            } else if (ingredient instanceof Integer i) {
                buf.writeUtf("int");
                buf.writeInt(i);
            } else {
                buf.writeUtf("item");
                buf.writeResourceLocation(ForgeRegistries.ITEMS.getKey(Items.AIR));
                LOGGER.warn("不支持的ingredient类型: " + ingredient.getClass());
            }
        }

        buf.writeInt(avaritiaTeir);

        if (editingRecipeId != null) {
            buf.writeBoolean(true);
            buf.writeResourceLocation(editingRecipeId);
        } else {
            buf.writeBoolean(false);
        }
    }

    public static void handle(CreateRecipePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            try {
                boolean isEditing = packet.editingRecipeId != null;
                ResourceLocation recipeId;
                if (isEditing) {
                    recipeId = packet.editingRecipeId;
                } else {
                    String itemName = ForgeRegistries.ITEMS.getKey(packet.result.getItem()).getPath();
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    recipeId = new ResourceLocation("registerhelper",
                            "custom_" + packet.recipeType.name().toLowerCase() + "_" + itemName + "_" + timestamp);
                }
                RecipeJsonManager.RecipeData recipeData = createRecipeData(packet, recipeId);
                if (recipeData == null) {
                    LOGGER.error("创建配方数据失败");
                    return;
                }
                boolean success;
                success = createNewRecipe(packet, recipeData, recipeId);
                if (success) {
                    String actionText = isEditing ? "覆盖" : "创建";
                    LOGGER.info("成功{}配方: {} (类型: {}, ID: {})",
                            actionText, packet.result.getDisplayName().getString(),
                            packet.recipeType, recipeId);
                } else {
                    LOGGER.error("{}配方失败: {}", isEditing ? "覆盖" : "创建", recipeId);
                }

            } catch (Exception e) {
                LOGGER.error("处理配方包时发生错误", e);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 创建配方数据对象
     */
    private static RecipeJsonManager.RecipeData createRecipeData(CreateRecipePacket packet, ResourceLocation recipeId) {
        try {
            RecipeJsonManager.RecipeData data = new RecipeJsonManager.RecipeData();
            data.id = recipeId.toString();
            data.result = ForgeRegistries.ITEMS.getKey(packet.result.getItem()).toString();
            data.count = packet.result.getCount();

            switch (packet.recipeType) {
                case SHAPED:
                    data.type = "shaped";
                    data.pattern = packet.pattern;
                    data.materialMapping = convertIngredientsToStrings(packet.ingredients);
                    break;

                case SHAPELESS:
                    data.type = "shapeless";
                    data.ingredients = convertIngredientsToStrings(packet.ingredients);
                    break;

                case SMELTING:
                    data.type = "smelting";
                    data.ingredients = new String[]{
                            ForgeRegistries.ITEMS.getKey((Item) packet.ingredients[0]).toString()
                    };
                    data.experience = (Float) packet.ingredients[1];
                    data.cookingTime = (Integer) packet.ingredients[2];
                    break;

                case AVARITIA_SHAPED:
                    data.type = "avaritia_shaped";
                    data.pattern = packet.pattern;
                    data.materialMapping = convertIngredientsToStrings(packet.ingredients);
                    data.tier = packet.avaritiaTeir;
                    break;

                case AVARITIA_SHAPELESS:
                    data.type = "avaritia_shapeless";
                    data.ingredients = convertIngredientsToStrings(packet.ingredients);
                    data.tier = packet.avaritiaTeir;
                    break;
            }

            return data;

        } catch (Exception e) {
            LOGGER.error("创建配方数据失败: " + recipeId, e);
            return null;
        }
    }

    /**
     * 创建新配方（非覆盖模式）
     */
    private static boolean createNewRecipe(CreateRecipePacket packet,
                                           RecipeJsonManager.RecipeData recipeData,
                                           ResourceLocation recipeId) {
        try {
            switch (packet.recipeType) {
                case SHAPED:
                    Registerhelper.getRecipeManager().addShapedRecipe(
                            packet.result, packet.pattern, packet.ingredients);
                    break;

                case SHAPELESS:
                    Registerhelper.getRecipeManager().addShapelessRecipe(
                            packet.result, packet.ingredients);
                    break;

                case SMELTING:
                    Item ingredient = (Item) packet.ingredients[0];
                    float experience = (Float) packet.ingredients[1];
                    int cookingTime = (Integer) packet.ingredients[2];
                    Registerhelper.getRecipeManager().addSmeltingRecipe(
                            ingredient, packet.result.getItem(), experience, cookingTime);
                    break;

                case AVARITIA_SHAPED:
                    Registerhelper.getRecipeManager().addAvaritiaTableRecipe(
                            packet.result, packet.avaritiaTeir, packet.pattern, packet.ingredients);
                    break;

                case AVARITIA_SHAPELESS:
                    List<ItemStack> avaritiaIngredients = new ArrayList<>();
                    for (Object a : packet.ingredients) {
                        if (a instanceof Item item) {
                            avaritiaIngredients.add(new ItemStack(item));
                        }
                    }
                    Registerhelper.getRecipeManager().addAvaritiaShapelessRecipe(
                            recipeId, packet.result, packet.avaritiaTeir, avaritiaIngredients);
                    break;
            }
            RecipeJsonManager.saveRecipe(recipeId.toString(), recipeData);
            Registerhelper.getRecipeManager().registerRecipes();
            return true;
        } catch (Exception e) {
            LOGGER.error("创建新配方失败: " + recipeId, e);
            return false;
        }
    }

    /**
     * 将配料对象数组转换为字符串数组
     */
    private static String[] convertIngredientsToStrings(Object[] ingredients) {
        String[] result = new String[ingredients.length];
        for (int i = 0; i < ingredients.length; i++) {
            if (ingredients[i] instanceof Item item) {
                result[i] = ForgeRegistries.ITEMS.getKey(item).toString();
            } else if (ingredients[i] instanceof Character ch) {
                result[i] = ch.toString();
            } else if (ingredients[i] instanceof Float f) {
                result[i] = f.toString();
            } else if (ingredients[i] instanceof Integer integer) {
                result[i] = integer.toString();
            } else {
                result[i] = "minecraft:air";
            }
        }
        return result;
    }
}