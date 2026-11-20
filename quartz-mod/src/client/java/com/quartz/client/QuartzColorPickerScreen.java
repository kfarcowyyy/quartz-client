package com.quartz.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.Locale;

final class QuartzColorPickerScreen extends QuartzSafeScreen {
    private enum Control { NONE, TONE, HUE, OPACITY }

    private final Screen parent;
    private final QuartzHudModule module;
    private final QuartzConfig.Hud settings;
    private final boolean background;
    private final int originalColor;
    private float hue, saturation, brightness, opacity;
    private int left, top, span, cardHeight, colorX, colorY, colorWidth, colorHeight, hueY, opacityY;
    private EditBox hex;
    private QuartzButton done;
    private boolean updatingHex, accepted;
    private Control control = Control.NONE;

    QuartzColorPickerScreen(Screen parent, QuartzHudModule module, Component label, boolean background) {
        super(label);
        this.parent = parent;
        this.module = module;
        this.settings = module.settings();
        this.background = background;
        originalColor = background ? settings.bg : settings.fg;
        unpack(originalColor);
    }

    @Override protected void init() {
        span = Math.min(width - 8, 398);
        cardHeight = Math.min(height - 8, 219);
        left = (width - span) / 2;
        top = (height - cardHeight) / 2;
        colorX = left + 12;
        colorY = top + 30;
        colorWidth = (span - 35) * 3 / 5;
        colorHeight = Math.max(12, cardHeight - 132);
        hueY = colorY + colorHeight + 17;
        opacityY = hueY + 23;
        control = Control.NONE;

        var label = QuartzText.of("HEX (AARRGGBB)", "HEX (AARRGGBB)");
        hex = addRenderableWidget(new EditBox(font, colorX, top + cardHeight - 51, colorWidth, 19, label));
        hex.setMaxLength(9);
        hex.setValue(hex(color()));
        done = addRenderableWidget(new QuartzButton(QuartzText.of("Apply", "Zastosuj"), left + span - 84,
                top + cardHeight - 25, 72, 18, true, () -> {
            if (!done.active) return;
            accepted = true;
            onClose();
        }));
        addRenderableWidget(new QuartzButton(QuartzText.of("Cancel", "Anuluj"), left + span - 162,
                top + cardHeight - 25, 72, 18, false, this::onClose));
        hex.setResponder(this::readHex);
    }

    private static String hex(int color) { return String.format(Locale.ROOT, "#%08X", color); }

    private static float clamp(double fraction) { return (float) Math.max(0d, Math.min(1d, fraction)); }

    private int rgb() { return Color.HSBtoRGB(hue, saturation, brightness); }

    private int color() { return (Math.round(opacity * 255) << 24) | (rgb() & 0xFFFFFF); }

    private void unpack(int color) {
        float[] hsv = Color.RGBtoHSB((color >>> 16) & 255, (color >>> 8) & 255, color & 255, null);
        if (hsv[1] > 0f) hue = hsv[0];
        saturation = hsv[1];
        brightness = hsv[2];
        opacity = (color >>> 24) / 255f;
    }

    private void setColor(int color) {
        if (background) settings.bg = color;
        else settings.fg = color;
    }

    private void readHex(String value) {
        if (updatingHex) return;
        String digits = value.strip().replaceFirst("^#", "");
        boolean valid = digits.matches("(?i)[0-9a-f]{6}([0-9a-f]{2})?");
        done.active = valid;
        hex.setTextColor(valid ? 0xFFFFFF : 0xFF8484);
        if (!valid) return;
        int next = (int) Long.parseLong(digits.length() == 6 ? "FF" + digits : digits, 16);
        unpack(next);
        setColor(next);
    }

    private void pick(double x, double y) {
        float across = clamp((x - colorX) / Math.max(1, colorWidth - 1));
        switch (control) {
            case TONE -> {
                saturation = across;
                brightness = 1f - clamp((y - colorY) / Math.max(1, colorHeight - 1));
            }
            case HUE -> hue = across;
            case OPACITY -> opacity = across;
            case NONE -> { return; }
        }
        int next = color();
        setColor(next);
        updatingHex = true;
        hex.setValue(hex(next));
        updatingHex = false;
        hex.setTextColor(0xFFFFFF);
        done.active = true;
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        if (e.button() != 0 || e.x() < colorX || e.x() >= colorX + colorWidth) return false;
        double y = e.y();
        if (y >= colorY && y < colorY + colorHeight) control = Control.TONE;
        else if (y >= hueY && y < hueY + 9) control = Control.HUE;
        else if (y >= opacityY && y < opacityY + 9) control = Control.OPACITY;
        else return false;
        setFocused(null);
        pick(e.x(), y);
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (e.button() != 0 || control == Control.NONE) return super.mouseDragged(e, dx, dy);
        pick(e.x(), e.y());
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if (e.button() != 0 || control == Control.NONE) return super.mouseReleased(e);
        control = Control.NONE;
        return true;
    }

    @Override protected void drawQuartz(GuiGraphics g, int mx, int my, float delta) {
        QuartzTheme.background(g, width, height);
        QuartzTheme.card(g, left, top, span, cardHeight, QuartzTheme.WHITE, QuartzTheme.OUTLINE);
        g.drawString(font, title, colorX, top + 12, QuartzTheme.ACCENT, false);
        g.drawString(font, QuartzText.of("Hue", "Barwa"), colorX, hueY - 11, QuartzTheme.MUTED, false);
        g.drawString(font, QuartzText.of("Opacity", "Krycie"), colorX, opacityY - 11, QuartzTheme.MUTED, false);
        String percent = Math.round(opacity * 100) + "%";
        g.drawString(font, percent, colorX + colorWidth - font.width(percent), opacityY - 11, QuartzTheme.MUTED, false);
        g.drawString(font, "HEX  RGB / ARGB", colorX, top + cardHeight - 20, QuartzTheme.MUTED, false);
        drawPalette(g);
        drawPreview(g);
        drawWidgets(g, mx, my, delta);
    }

    private void drawPalette(GuiGraphics g) {
        checker(g, colorX, opacityY, colorWidth, 9);
        int base = rgb() & 0xFFFFFF;
        for (int step = 0; step < colorWidth; step++) {
            int x = colorX + step;
            float ratio = step / (float) Math.max(1, colorWidth - 1);
            g.fillGradient(x, colorY, x + 1, colorY + colorHeight, Color.HSBtoRGB(hue, ratio, 1f), 0xFF000000);
            g.fill(x, hueY, x + 1, hueY + 9, Color.HSBtoRGB(ratio, 1f, 1f));
            g.fill(x, opacityY, x + 1, opacityY + 9, (Math.round(ratio * 255) << 24) | base);
        }
        marker(g, colorX + Math.round(saturation * (colorWidth - 1)), colorY + Math.round((1f - brightness) * (colorHeight - 1)));
        marker(g, colorX + Math.round(hue * (colorWidth - 1)), hueY + 4);
        marker(g, colorX + Math.round(opacity * (colorWidth - 1)), opacityY + 4);
    }

    private void drawPreview(GuiGraphics g) {
        int x = colorX + colorWidth + 11, y = colorY;
        int w = left + span - x - 12, h = opacityY + 9 - y;
        QuartzTheme.card(g, x, y, w, h, 0xFF594964, QuartzTheme.OUTLINE);
        g.drawString(font, QuartzText.of("Live preview", "Podgląd"), x + 6, y + 8, 0xFFEEDCFB, false);
        var panel = QuartzHud.panel(module, minecraft, true, w - 8, h - 21);
        g.enableScissor(x + 2, y + 19, x + w - 2, y + h - 2);
        try {
            QuartzHud.preview(g, minecraft, module, panel, x + 4, y + 20, w - 8, h - 23);
        } finally {
            g.disableScissor();
        }
        int sampleY = top + cardHeight - 51;
        checker(g, x, sampleY, w, 19);
        QuartzTheme.hudBackground(g, x, sampleY, w, 19, color());
    }

    static void checker(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFFFFFFF);
        for (int row = 0; row < h; row += 4) {
            for (int col = row % 8; col < w; col += 8) {
                g.fill(x + col, y + row, x + Math.min(w, col + 4), y + Math.min(h, row + 4), 0xFFE5DAEF);
            }
        }
    }

    private static void marker(GuiGraphics g, int x, int y) {
        QuartzTheme.rounded(g, x - 4, y - 4, 9, 9, 0xFF33213E);
        QuartzTheme.rounded(g, x - 3, y - 3, 7, 7, 0xFFFCF6FF);
        QuartzTheme.rounded(g, x - 1, y - 1, 3, 3, 0xFF5C3B68);
    }

    @Override public void removed() {
        if (accepted) QuartzConfig.save();
        else setColor(originalColor);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
