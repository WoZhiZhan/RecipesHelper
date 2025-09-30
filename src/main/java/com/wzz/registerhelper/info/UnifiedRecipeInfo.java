package com.wzz.registerhelper.info;

import net.minecraft.resources.ResourceLocation;

public class UnifiedRecipeInfo {
    public final ResourceLocation id;
    public final String source;
    public final boolean isBlacklisted;
    public final boolean hasOverride;
    public final String description;
    
    public UnifiedRecipeInfo(ResourceLocation id, String source, boolean isBlacklisted, boolean hasOverride, String description) {
        this.id = id;
        this.source = source;
        this.isBlacklisted = isBlacklisted;
        this.hasOverride = hasOverride;
        this.description = description;
    }
    
    public String getStatusText() {
        if (isBlacklisted) return "§c[已禁用]";
        if (hasOverride) return "§e[已覆盖]";
        return "§a[正常]";
    }
    
    public String getDisplayText() {
        return source + getStatusText();
    }
    
    public int getStatusColor() {
        if (isBlacklisted) return 0xFFFF5555; // 红色
        if (hasOverride) return 0xFFFFAA00;   // 橙色
        return 0xFF55FF55;                    // 绿色
    }

    public ResourceLocation getRecipeId() {
        return id;
    }
}