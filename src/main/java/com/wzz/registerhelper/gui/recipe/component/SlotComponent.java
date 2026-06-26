package com.wzz.registerhelper.gui.recipe.component;

/**
 * 物品槽位组件
 */
public class SlotComponent extends RecipeComponent {
    private final int slotIndex; // 槽位索引
    
    public SlotComponent(int x, int y, String id,int slotIndex) {
        super(x, y, 18, 18, id);
        this.slotIndex = slotIndex;
    }
    
    @Override
    public ComponentType getType() {
        return ComponentType.SLOT;
    }

    public int getSlotIndex() { return slotIndex; }
}