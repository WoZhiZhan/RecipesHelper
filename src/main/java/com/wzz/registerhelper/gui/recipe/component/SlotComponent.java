package com.wzz.registerhelper.gui.recipe.component;

/**
 * 物品槽位组件
 */
public class SlotComponent extends RecipeComponent {
    private final int slotIndex; // 槽位索引
    private final SlotRole role;

    public enum SlotRole {
        INPUT,
        CATALYST,
        REAGENT,
        TRANSITIONAL,
        OUTPUT
    }
    
    public SlotComponent(int x, int y, String id, int slotIndex) {
        this(x, y, id, slotIndex, SlotRole.INPUT);
    }

    public SlotComponent(int x, int y, String id, int slotIndex, SlotRole role) {
        super(x, y, 18, 18, id);
        this.slotIndex = slotIndex;
        this.role = role == null ? SlotRole.INPUT : role;
    }
    
    @Override
    public ComponentType getType() {
        return ComponentType.SLOT;
    }

    public int getSlotIndex() { return slotIndex; }
    public SlotRole getRole() { return role; }
}
