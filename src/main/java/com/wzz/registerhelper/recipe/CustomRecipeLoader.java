package com.wzz.registerhelper.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 自定义配方加载器
 * 用于加载不支持原生JSON的配方类型（酿造台、铁砧等）
 */
@Mod.EventBusSubscriber(modid = "registerhelper")
public class CustomRecipeLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    // 存储加载的配方
    private static final List<BrewingRecipeData> BREWING_RECIPES = new ArrayList<>();
    private static final List<AnvilRecipeData> ANVIL_RECIPES = new ArrayList<>();

    /**
     * 酿造台配方数据
     */
    public static class BrewingRecipeData {
        public ItemStack input;        // 输入药水/物品
        public ItemStack ingredient;   // 酿造材料
        public ItemStack output;       // 输出药水/物品

        public BrewingRecipeData(ItemStack input, ItemStack ingredient, ItemStack output) {
            this.input = input;
            this.ingredient = ingredient;
            this.output = output;
        }
    }

    /**
     * 铁砧配方数据
     */
    public static class AnvilRecipeData {
        public ItemStack left;         // 左侧物品
        public ItemStack right;        // 右侧物品（材料）
        public ItemStack output;       // 输出物品
        public int cost;               // 经验等级消耗
        public int materialCost;       // 材料消耗数量

        public AnvilRecipeData(ItemStack left, ItemStack right, ItemStack output, int cost, int materialCost) {
            this.left = left;
            this.right = right;
            this.output = output;
            this.cost = cost;
            this.materialCost = materialCost;
        }
    }

    /**
     * 加载所有自定义配方
     */
    public static void loadCustomRecipes() {
        Path recipesDir = FMLPaths.CONFIGDIR.get().resolve("registerhelper/custom_recipes");

        try {
            Files.createDirectories(recipesDir);

            // 加载酿造台配方
            loadBrewingRecipes(recipesDir.resolve("brewing"));

            // 加载铁砧配方
            loadAnvilRecipes(recipesDir.resolve("anvil"));

        } catch (Exception e) {
            LOGGER.error("加载自定义配方时出错", e);
        }
    }

    /**
     * 加载酿造台配方
     */
    private static void loadBrewingRecipes(Path brewingDir) {
        try {
            Files.createDirectories(brewingDir);

            try (Stream<Path> paths = Files.walk(brewingDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json"))
                        .forEach(CustomRecipeLoader::loadBrewingRecipe);
            }
        } catch (Exception e) {
            LOGGER.error("加载酿造台配方失败", e);
        }
    }

    /**
     * 加载单个酿造台配方文件
     */
    private static void loadBrewingRecipe(Path file) {
        try (FileReader reader = new FileReader(file.toFile())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            ItemStack input = parseItemStack(json.get("input"));
            ItemStack ingredient = parseItemStack(json.get("ingredient"));
            ItemStack output = parseItemStack(json.get("output"));

            if (input != null && ingredient != null && output != null) {
                BREWING_RECIPES.add(new BrewingRecipeData(input, ingredient, output));
            }
        } catch (Exception e) {
            LOGGER.error("加载酿造台配方文件失败: {}", file, e);
        }
    }

    /**
     * 加载铁砧配方
     */
    private static void loadAnvilRecipes(Path anvilDir) {
        try {
            Files.createDirectories(anvilDir);

            try (Stream<Path> paths = Files.walk(anvilDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json"))
                        .forEach(CustomRecipeLoader::loadAnvilRecipe);
            }
        } catch (Exception e) {
            LOGGER.error("加载铁砧配方失败", e);
        }
    }

    /**
     * 加载单个铁砧配方文件
     */
    private static void loadAnvilRecipe(Path file) {
        try (FileReader reader = new FileReader(file.toFile())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            ItemStack left = parseItemStack(json.get("left"));
            ItemStack right = parseItemStack(json.get("right"));
            ItemStack output = parseItemStack(json.get("output"));
            int cost = json.has("cost") ? json.get("cost").getAsInt() : 1;
            int materialCost = json.has("material_cost") ? json.get("material_cost").getAsInt() : 1;

            if (left != null && right != null && output != null) {
                ANVIL_RECIPES.add(new AnvilRecipeData(left, right, output, cost, materialCost));
            }
        } catch (Exception e) {
            LOGGER.error("加载铁砧配方文件失败: {}", file, e);
        }
    }

    /**
     * 解析ItemStack（支持NBT和药水）
     */
    private static ItemStack parseItemStack(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            JsonObject obj = element.getAsJsonObject();

            // 解析物品ID
            String itemId = obj.get("item").getAsString();
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item == null || item == Items.AIR) {
                LOGGER.warn("未知物品: {}", itemId);
                return null;
            }

            // 解析数量
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
            ItemStack stack = new ItemStack(item, count);

            // 解析NBT
            if (obj.has("nbt")) {
                String nbtString = obj.get("nbt").getAsString();
                try {
                    CompoundTag nbt = TagParser.parseTag(nbtString);
                    stack.setTag(nbt);
                } catch (Exception e) {
                    LOGGER.error("解析NBT失败: {}", nbtString, e);
                }
            }

            // 解析药水类型（快捷方式）
            if (obj.has("potion")) {
                String potionId = obj.get("potion").getAsString();
                Potion potion = BuiltInRegistries.POTION.get(new ResourceLocation(potionId));
                if (potion != null && potion != Potions.EMPTY) {
                    PotionUtils.setPotion(stack, potion);
                }
            }

            return stack;

        } catch (Exception e) {
            LOGGER.error("解析ItemStack失败", e);
            return null;
        }
    }

    /**
     * 检查是否有匹配的酿造配方（供Mixin调用）
     */
    public static boolean hasBrewingRecipe(ItemStack input, ItemStack ingredient) {
        for (BrewingRecipeData recipe : BREWING_RECIPES) {
            if (itemStackMatches(input, recipe.input) &&
                    itemStackMatches(ingredient, recipe.ingredient)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取酿造结果（供Mixin调用）
     */
    public static ItemStack getBrewingResult(ItemStack input, ItemStack ingredient) {
        for (BrewingRecipeData recipe : BREWING_RECIPES) {
            if (itemStackMatches(input, recipe.input) &&
                    itemStackMatches(ingredient, recipe.ingredient)) {
                return recipe.output.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 获取所有酿造配方（供JEI使用）
     */
    public static List<BrewingRecipeData> getBrewingRecipes() {
        return new ArrayList<>(BREWING_RECIPES);
    }

    /**
     * 获取所有铁砧配方（供JEI使用）
     */
    public static List<AnvilRecipeData> getAnvilRecipes() {
        return new ArrayList<>(ANVIL_RECIPES);
    }

    /**
     * 处理铁砧配方
     */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (left.isEmpty() || right.isEmpty()) {
            return;
        }

        // 检查是否匹配自定义铁砧配方
        for (AnvilRecipeData recipe : ANVIL_RECIPES) {
            if (itemStackMatches(left, recipe.left) && itemStackMatches(right, recipe.right)) {
                // 设置输出
                event.setOutput(recipe.output.copy());

                // 设置经验消耗
                event.setCost(recipe.cost);

                // 设置材料消耗
                event.setMaterialCost(recipe.materialCost);

                return;
            }
        }
    }

    /**
     * 检查ItemStack是否匹配（忽略数量）
     */
    private static boolean itemStackMatches(ItemStack stack, ItemStack template) {
        if (stack.getItem() != template.getItem()) {
            return false;
        }

        // 如果模板有NBT，检查NBT是否匹配
        if (template.hasTag()) {
            return ItemStack.isSameItemSameTags(stack, template);
        }

        return true;
    }

    /**
     * 监听数据包同步事件，自动重载自定义配方
     * 当服务器执行/reload时会触发
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        clearRecipes();
        loadCustomRecipes();
    }

    /**
     * 清空配方缓存（用于重载）
     */
    public static void clearRecipes() {
        BREWING_RECIPES.clear();
        ANVIL_RECIPES.clear();
    }
}