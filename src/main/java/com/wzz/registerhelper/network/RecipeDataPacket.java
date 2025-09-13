package com.wzz.registerhelper.network;

import com.wzz.registerhelper.Registerhelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class RecipeDataPacket {
    private final String recipeType;
    private final String resultItem;
    private final int resultCount;
    private final String[] pattern;
    private final String[] ingredients;
    private final int tier;
    private final float experience;
    private final int cookingTime;
    
    public RecipeDataPacket(String recipeType, String resultItem, int resultCount, 
                           String[] pattern, String[] ingredients, int tier, 
                           float experience, int cookingTime) {
        this.recipeType = recipeType;
        this.resultItem = resultItem;
        this.resultCount = resultCount;
        this.pattern = pattern;
        this.ingredients = ingredients;
        this.tier = tier;
        this.experience = experience;
        this.cookingTime = cookingTime;
    }
    
    public static void encode(RecipeDataPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.recipeType);
        buf.writeUtf(packet.resultItem);
        buf.writeInt(packet.resultCount);
        
        // 写入pattern
        buf.writeInt(packet.pattern.length);
        for (String line : packet.pattern) {
            buf.writeUtf(line);
        }
        
        // 写入ingredients
        buf.writeInt(packet.ingredients.length);
        for (String ingredient : packet.ingredients) {
            buf.writeUtf(ingredient);
        }
        
        buf.writeInt(packet.tier);
        buf.writeFloat(packet.experience);
        buf.writeInt(packet.cookingTime);
    }
    
    public static RecipeDataPacket decode(FriendlyByteBuf buf) {
        String recipeType = buf.readUtf();
        String resultItem = buf.readUtf();
        int resultCount = buf.readInt();
        
        // 读取pattern
        int patternLength = buf.readInt();
        String[] pattern = new String[patternLength];
        for (int i = 0; i < patternLength; i++) {
            pattern[i] = buf.readUtf();
        }
        
        // 读取ingredients
        int ingredientsLength = buf.readInt();
        String[] ingredients = new String[ingredientsLength];
        for (int i = 0; i < ingredientsLength; i++) {
            ingredients[i] = buf.readUtf();
        }
        
        int tier = buf.readInt();
        float experience = buf.readFloat();
        int cookingTime = buf.readInt();
        
        return new RecipeDataPacket(recipeType, resultItem, resultCount, pattern, ingredients, tier, experience, cookingTime);
    }
    
    public static void handle(RecipeDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.hasPermissions(2)) {
                try {
                    Item resultItemObj = ForgeRegistries.ITEMS.getValue(new ResourceLocation(packet.resultItem));
                    if (resultItemObj == null) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c无效的物品ID: " + packet.resultItem));
                        return;
                    }
                    
                    ItemStack result = new ItemStack(resultItemObj, packet.resultCount);
                    ResourceLocation recipeId = generateRecipeId(packet.recipeType, result);

                    switch (packet.recipeType) {
                        case "SHAPED" -> handleShapedRecipe(recipeId, result, packet, player);
                        case "SHAPELESS" -> handleShapelessRecipe(recipeId, result, packet, player);
                        case "SMELTING" -> handleSmeltingRecipe(recipeId, result, packet, player);
                        case "AVARITIA_TABLE" -> handleAvaritiaRecipe(recipeId, result, packet, player);
                        default -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c未知的配方类型: " + packet.recipeType));
                    }
                    
                } catch (Exception e) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c创建配方失败: " + e.getMessage()));
                }
            } else {
                if (player != null) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c您没有权限创建配方"));
                }
            }
        });
        context.setPacketHandled(true);
    }
    
    private static ResourceLocation generateRecipeId(String type, ItemStack result) {
        String itemName = ForgeRegistries.ITEMS.getKey(result.getItem()).getPath();
        String timestamp = String.valueOf(System.currentTimeMillis() % 100000);
        return new ResourceLocation("registerhelper", itemName + "_" + type.toLowerCase() + "_" + timestamp);
    }
    
    private static void handleShapedRecipe(ResourceLocation id, ItemStack result, RecipeDataPacket packet, ServerPlayer player) {
        try {
            Object[] ingredients = parseShapedIngredients(packet.ingredients);
            
            if (packet.recipeType.equals("AVARITIA_TABLE")) {
                Registerhelper.getRecipeManager().addAvaritiaTableRecipe(id, result, packet.tier, packet.pattern, ingredients);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aAvaritia工作台配方已创建: " + id + " (等级" + packet.tier + ")"));
            } else {
                Registerhelper.getRecipeManager().addShapedRecipe(id, result, packet.pattern, ingredients);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a有形状配方已创建: " + id));
            }

            Registerhelper.getRecipeManager().registerRecipes();
            
        } catch (Exception e) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c解析有形状配方失败: " + e.getMessage()));
        }
    }
    
    private static void handleShapelessRecipe(ResourceLocation id, ItemStack result, RecipeDataPacket packet, ServerPlayer player) {
        try {
            Object[] ingredients = parseShapelessIngredients(packet.ingredients);
            
            Registerhelper.getRecipeManager().addShapelessRecipe(id, result, ingredients);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a无形状配方已创建: " + id));

            Registerhelper.getRecipeManager().registerRecipes();
            
        } catch (Exception e) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c解析无形状配方失败: " + e.getMessage()));
        }
    }
    
    private static void handleSmeltingRecipe(ResourceLocation id, ItemStack result, RecipeDataPacket packet, ServerPlayer player) {
        try {
            if (packet.ingredients.length == 0) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c熔炼配方需要输入材料"));
                return;
            }
            
            Item ingredientItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(packet.ingredients[0]));
            if (ingredientItem == null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c无效的材料ID: " + packet.ingredients[0]));
                return;
            }
            
            Registerhelper.getRecipeManager().addSmeltingRecipe(id, ingredientItem, result.getItem(), packet.experience, packet.cookingTime);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a熔炼配方已创建: " + id));

            Registerhelper.getRecipeManager().registerRecipes();
            
        } catch (Exception e) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c解析熔炼配方失败: " + e.getMessage()));
        }
    }
    
    private static void handleAvaritiaRecipe(ResourceLocation id, ItemStack result, RecipeDataPacket packet, ServerPlayer player) {
        handleShapedRecipe(id, result, packet, player);
    }
    
    private static Object[] parseShapedIngredients(String[] ingredientStrings) {
        Object[] result = new Object[ingredientStrings.length * 2];
        
        for (int i = 0; i < ingredientStrings.length; i++) {
            String ingredientStr = ingredientStrings[i];
            String[] parts = ingredientStr.split(":", 2);
            
            if (parts.length >= 2) {
                char symbol = parts[0].charAt(0);
                String itemId = parts[1];
                
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                if (item != null) {
                    result[i * 2] = symbol;
                    result[i * 2 + 1] = item;
                }
            }
        }
        
        return result;
    }
    
    private static Object[] parseShapelessIngredients(String[] ingredientStrings) {
        Object[] result = new Object[ingredientStrings.length];
        
        for (int i = 0; i < ingredientStrings.length; i++) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ingredientStrings[i]));
            if (item != null) {
                result[i] = item;
            }
        }
        
        return result;
    }
}