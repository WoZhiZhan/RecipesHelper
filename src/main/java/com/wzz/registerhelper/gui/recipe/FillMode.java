package com.wzz.registerhelper.gui.recipe;

import com.wzz.registerhelper.gui.GuiText;

public enum FillMode {
        NORMAL("registerhelper.gui.recipe_creator.fill_mode.normal"),
        BRUSH("registerhelper.gui.recipe_creator.fill_mode.brush"),
        FILL("registerhelper.gui.recipe_creator.fill_mode.fill");

        private final String translationKey;

        FillMode(String translationKey) {
                this.translationKey = translationKey;
        }

        public String getDisplayName() {
                return GuiText.string(translationKey);
        }

        public String getTranslationKey() { return translationKey; }
}
