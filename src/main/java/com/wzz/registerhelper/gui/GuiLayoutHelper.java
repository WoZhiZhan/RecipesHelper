package com.wzz.registerhelper.gui;

import net.minecraft.client.gui.Font;

/**
 * Common helpers for screen layouts that need to adapt to the current GUI size.
 */
public final class GuiLayoutHelper {
    private GuiLayoutHelper() {
    }

    public record Bounds(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right()
                    && pointY >= y && pointY < bottom();
        }
    }

    /** Geometry and pointer conversion for a vertical scrollbar. */
    public record Scrollbar(Bounds track, int thumbY, int thumbHeight, int maxOffset) {
        public boolean visible() {
            return maxOffset > 0;
        }

        public boolean contains(double pointX, double pointY) {
            return visible() && track.contains(pointX, pointY);
        }

        public boolean thumbContains(double pointX, double pointY) {
            return contains(pointX, pointY)
                    && pointY >= thumbY && pointY < thumbY + thumbHeight;
        }

        public double grabOffset(double pointY) {
            return thumbContains(track.x() + track.width() / 2.0, pointY)
                    ? pointY - thumbY
                    : thumbHeight / 2.0;
        }

        public int offsetForPointer(double pointY, double grabOffset) {
            int travel = track.height() - thumbHeight;
            if (maxOffset <= 0 || travel <= 0) {
                return 0;
            }
            double thumbTop = pointY - grabOffset - track.y();
            return clamp((int) Math.round(thumbTop * maxOffset / (double) travel), 0, maxOffset);
        }
    }

    public static Scrollbar scrollbar(Bounds track, int totalUnits, int visibleUnits,
                                      int offset, int minimumThumbHeight) {
        int maxOffset = Math.max(0, totalUnits - visibleUnits);
        int safeHeight = Math.max(1, track.height());
        if (maxOffset == 0 || totalUnits <= 0) {
            return new Scrollbar(track, track.y(), safeHeight, 0);
        }
        int thumbHeight = Math.max(minimumThumbHeight,
                safeHeight * Math.max(1, visibleUnits) / totalUnits);
        thumbHeight = clamp(thumbHeight, 1, safeHeight);
        int travel = safeHeight - thumbHeight;
        int safeOffset = clamp(offset, 0, maxOffset);
        int thumbY = track.y() + (travel == 0 ? 0
                : (int) Math.round(travel * safeOffset / (double) maxOffset));
        return new Scrollbar(track, thumbY, thumbHeight, maxOffset);
    }

    /**
     * Fits a preferred dimension into the available space while preserving a
     * smaller usable minimum whenever the screen can provide it.
     */
    public static int fit(int preferred, int minimum, int available) {
        if (available <= 0) {
            return 0;
        }
        return Math.max(Math.min(preferred, available), Math.min(minimum, available));
    }

    public static Bounds centered(int screenWidth, int screenHeight,
                                  int preferredWidth, int preferredHeight,
                                  int minimumWidth, int minimumHeight,
                                  int horizontalMargin, int verticalMargin) {
        int availableWidth = Math.max(1, screenWidth - horizontalMargin * 2);
        int availableHeight = Math.max(1, screenHeight - verticalMargin * 2);
        int width = fit(preferredWidth, minimumWidth, availableWidth);
        int height = fit(preferredHeight, minimumHeight, availableHeight);
        return new Bounds((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    public static int clamp(int value, int minimum, int maximum) {
        if (maximum < minimum) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Returns a string that fits in maxWidth, preserving the suffix ellipsis
     * where possible.
     */
    public static String ellipsis(Font font, String value, int maxWidth) {
        if (value == null || maxWidth <= 0) {
            return "";
        }
        if (font.width(value) <= maxWidth) {
            return value;
        }
        if (maxWidth <= font.width("...")) {
            return font.plainSubstrByWidth(value, maxWidth);
        }
        return font.plainSubstrByWidth(value, maxWidth - font.width("...")) + "...";
    }
}
