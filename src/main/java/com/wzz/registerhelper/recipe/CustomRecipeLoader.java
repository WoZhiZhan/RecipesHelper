package com.wzz.registerhelper.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.wzz.registerhelper.util.OldUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.slf4j.Logger;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 自定义配方加载器
 * 用于加载不支持原生JSON的配方类型（酿造台、铁砧等）
 *
 * NeoForge 1.21.1 迁移要点：
 * - PotionUtils.setPotion -> DataComponents.POTION_CONTENTS + PotionContents
 * - stack.setTag/hasTag -> OldUtils 兼容层
 * - ItemStack.isSameItemSameTags -> ItemStack.isSameItemSameComponents
 * - @Mod.EventBusSubscriber -> @EventBusSubscriber
 * - net.minecraftforge.event.* -> net.neoforged.neoforge.event.*
 */
@EventBusSubscriber(modid = "registerhelper")
public class CustomRecipeLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    private static final List<BrewingRecipeData> BREWING_RECIPES = new ArrayList<>();
    private static final List<AnvilRecipeData> ANVIL_RECIPES = new ArrayList<>();

    public static class BrewingRecipeData {
        public ItemStack input;
        public ItemStack ingredient;
        public ItemStack output;

        public BrewingRecipeData(ItemStack input, ItemStack ingredient, ItemStack output) {
            this.input = input;
            this.ingredient = ingredient;
            this.output = output;
        }
    }

    public static class AnvilRecipeData {
        public ItemStack left;
        public ItemStack right;
        public ItemStack output;
        public int cost;
        public int materialCost;

        public AnvilRecipeData(ItemStack left, ItemStack right, ItemStack output, int cost, int materialCost) {
            this.left = left;
            this.right = right;
            this.output = output;
            this.cost = cost;
            this.materialCost = materialCost;
        }
    }

    public static void loadCustomRecipes() {
        Path recipesDir = FMLPaths.CONFIGDIR.get().resolve("registerhelper/custom_recipes");
        try {
            Files.createDirectories(recipesDir);
            loadBrewingRecipes(recipesDir.resolve("brewing"));
            loadAnvilRecipes(recipesDir.resolve("anvil"));
        } catch (Exception e) {
            LOGGER.error("加载自定义配方时出错", e);
        }
    }

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

            String itemId = obj.get("item").getAsString();
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item == null || item == Items.AIR) {
                LOGGER.warn("未知物品: {}", itemId);
                return null;
            }

            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
            ItemStack stack = new ItemStack(item, count);

            // 解析NBT（写入 CUSTOM_DATA，通过兼容层）
            if (obj.has("nbt")) {
                String nbtString = obj.get("nbt").getAsString();
                try {
                    CompoundTag nbt = TagParser.parseTag(nbtString);
                    OldUtils.setTag(stack, nbt);
                } catch (Exception e) {
                    LOGGER.error("解析NBT失败: {}", nbtString, e);
                }
            }

            // 解析药水类型（快捷方式）：1.21 用 POTION_CONTENTS 组件
            if (obj.has("potion")) {
                String potionId = obj.get("potion").getAsString();
                ResourceLocation potionRL = ResourceLocation.parse(potionId);
                Optional<Holder.Reference<Potion>> potionHolder =
                        BuiltInRegistries.POTION.getHolder(potionRL);
                if (potionHolder.isPresent()) {
                    stack.set(DataComponents.POTION_CONTENTS,
                            new PotionContents(potionHolder.get()));
                } else {
                    LOGGER.warn("未知药水: {}", potionId);
                }
            }

            return stack;
        } catch (Exception e) {
            LOGGER.error("解析ItemStack失败", e);
            return null;
        }
    }

    public static boolean hasBrewingRecipe(ItemStack input, ItemStack ingredient) {
        for (BrewingRecipeData recipe : BREWING_RECIPES) {
            if (itemStackMatches(input, recipe.input) &&
                    itemStackMatches(ingredient, recipe.ingredient)) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack getBrewingResult(ItemStack input, ItemStack ingredient) {
        for (BrewingRecipeData recipe : BREWING_RECIPES) {
            if (itemStackMatches(input, recipe.input) &&
                    itemStackMatches(ingredient, recipe.ingredient)) {
                return recipe.output.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    public static List<BrewingRecipeData> getBrewingRecipes() {
        return new ArrayList<>(BREWING_RECIPES);
    }

    public static List<AnvilRecipeData> getAnvilRecipes() {
        return new ArrayList<>(ANVIL_RECIPES);
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        for (AnvilRecipeData recipe : ANVIL_RECIPES) {
            if (itemStackMatches(left, recipe.left) && itemStackMatches(right, recipe.right)) {
                event.setOutput(recipe.output.copy());
                event.setCost(recipe.cost);
                event.setMaterialCost(recipe.materialCost);
                return;
            }
        }
    }

    /**
     * 检查ItemStack是否匹配（忽略数量）
     * 1.21: isSameItemSameTags -> isSameItemSameComponents
     */
    private static boolean itemStackMatches(ItemStack stack, ItemStack template) {
        if (stack.getItem() != template.getItem()) {
            return false;
        }
        if (OldUtils.hasTag(template)) {
            return ItemStack.isSameItemSameComponents(stack, template);
        }
        return true;
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        clearRecipes();
        loadCustomRecipes();
    }

    public static void clearRecipes() {
        BREWING_RECIPES.clear();
        ANVIL_RECIPES.clear();
    }
}
