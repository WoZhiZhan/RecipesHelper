package com.wzz.registerhelper.gui.recipe;

public enum FillMode {
        NORMAL("普通模式"),
        BRUSH("画笔模式"),
        FILL("填充模式");

        private final String displayName;

        FillMode(String displayName) {
                this.displayName = displayName;
        }

        public String getDisplayName() {
                return displayName;
        }
}