package com.wzz.registerhelper.gui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import com.wzz.registerhelper.gui.GuiTheme;

/** EditBox that keeps its text and cursor vertically centered without moving its hit box. */
public class CenteredEditBox extends EditBox {
    public CenteredEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        setFormatter((text, cursor) -> FormattedCharSequence.forward(
                text, Style.EMPTY.withFont(GuiTheme.EDITOR_FONT)));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        try {
            int offset = Math.max(0, (getHeight() - 9) / 2);
            graphics.pose().translate(0, offset, 0);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }
    }
}
