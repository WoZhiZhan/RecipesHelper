package com.wzz.registerhelper.gui.recipe.component;

import com.wzz.registerhelper.gui.recipe.component.renderer.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 组件渲染管理器 - 改进版
 */
public class ComponentRenderManager {
    private final List<ComponentRenderer> renderers = new ArrayList<>();
    private final List<EditBox> editBoxes = new ArrayList<>();
    private final ComponentDataManager dataManager;
    private Font font;

    // 槽位数据映射
    private final Map<Integer, ItemStack> slotItems = new HashMap<>();
    private ItemStack resultItem = ItemStack.EMPTY;

    // 回调函数
    private Consumer<Integer> onSlotLeftClick;
    private Consumer<Integer> onSlotRightClick;
    private Runnable onResultClick;

    public ComponentRenderManager(Font font) {
        this.font = font;
        this.dataManager = new ComponentDataManager();
    }

    /**
     * 设置槽位回调
     */
    public void setSlotCallbacks(Consumer<Integer> onLeftClick, Consumer<Integer> onRightClick) {
        this.onSlotLeftClick = onLeftClick;
        this.onSlotRightClick = onRightClick;
    }

    /**
     * 设置结果回调
     */
    public void setResultCallback(Runnable onClick) {
        this.onResultClick = onClick;
    }

    /**
     * 更新槽位物品
     */
    public void updateSlotItem(int index, ItemStack item) {
        slotItems.put(index, item.copy());
    }

    /**
     * 更新结果物品
     */
    public void updateResultItem(ItemStack item) {
        this.resultItem = item.copy();
    }

    /**
     * 初始化组件渲染器
     */
    public void initializeRenderers(List<RecipeComponent> components) {
        renderers.clear();
        editBoxes.clear();

        for (RecipeComponent component : components) {
            ComponentRenderer renderer = createRenderer(component);
            if (renderer != null) {
                renderers.add(renderer);
                if (renderer instanceof NumberInputRenderer numberRenderer) {
                    editBoxes.add(numberRenderer.getEditBox());
                } else if (renderer instanceof StringInputRenderer stringRenderer) {
                    editBoxes.add(stringRenderer.getEditBox());
                }
            }
        }
    }

    /**
     * 获取所有 EditBox（供 Screen 注册）
     */
    public List<EditBox> getEditBoxes() {
        return new ArrayList<>(editBoxes);
    }

    /**
     * 创建对应类型的渲染器
     */
    private ComponentRenderer createRenderer(RecipeComponent component) {
        switch (component.getType()) {
            case SLOT:
                if (component instanceof SlotComponent slotComp) {
                    int slotIndex = slotComp.getSlotIndex();
                    return new SlotRenderer(slotComp,
                            () -> slotItems.getOrDefault(slotIndex, ItemStack.EMPTY),
                            idx -> {
                                if (onSlotLeftClick != null) onSlotLeftClick.accept(slotIndex);
                            },
                            idx -> {
                                if (onSlotRightClick != null) onSlotRightClick.accept(slotIndex);
                            });
                }

            case LABEL:
                if (component instanceof LabelComponent labelComponent)
                    return new LabelRenderer(labelComponent);

            case NUMBER_INPUT:
                if (component instanceof NumberInputComponent numberInputComponent)
                    return new NumberInputRenderer(numberInputComponent, font, dataManager);

            case STRING_INPUT:
                if (component instanceof StringInputComponent stringInputComponent)
                    return new StringInputRenderer(stringInputComponent, font, dataManager);

            default:
                return null;
        }
    }

    /**
     * 渲染所有组件
     */
    public void renderAll(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.font == null)
            this.font = Minecraft.getInstance().font;
        for (ComponentRenderer renderer : renderers) {
            renderer.render(guiGraphics, font, mouseX, mouseY);
        }
    }

    /**
     * 处理鼠标点击（只处理槽位，EditBox 由 Screen 自动处理）
     */
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        for (ComponentRenderer renderer : renderers) {
            if (!(renderer instanceof NumberInputRenderer) &&
                    !(renderer instanceof StringInputRenderer)) {
                if (renderer.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取数据管理器
     */
    public ComponentDataManager getDataManager() {
        return dataManager;
    }

    /**
     * 清空所有数据
     */
    public void clear() {
        dataManager.clear();
        slotItems.clear();
        resultItem = ItemStack.EMPTY;
        editBoxes.clear();
    }
}