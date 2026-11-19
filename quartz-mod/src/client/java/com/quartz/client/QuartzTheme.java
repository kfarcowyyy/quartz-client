package com.quartz.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class QuartzTheme {
    private static final Identifier CRYSTAL = Identifier.fromNamespaceAndPath("quartz", "textures/gui/app-crystal.png");

    static void crystal(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CRYSTAL, x, y, 0, 0, width, height, 48, 72, 48, 72);
    }

    static final int INK = 0xFF382648;
    static final int MUTED = 0xFF705D80;
    static final int ACCENT = 0xFF8145B5;
    static final int OUTLINE = 0xFFDFCCEB;
    static final int WHITE = 0xFFFFFCFF;

    private QuartzTheme() {}

    static void background(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, 0xFFFDF6FE, 0xFFEFE2FC);
        g.fillGradient(0, height * 2 / 3, width, height, 0x00F5D9F4, 0x99F5D9F4);
    }

    static void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int radius = Math.min(7, Math.min(w, h) / 2);
        g.fill(x, y + radius, x + w, y + h - radius, color);
        for (int row = 0; row < radius; row++) {
            double dy = radius - row - 0.5;
            int inset = (int) Math.round(radius - Math.sqrt(radius * radius - dy * dy));
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            g.fill(x + inset, y + h - 1 - row, x + w - inset, y + h - row, color);
        }
    }

    static void hudBackground(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int radius = Math.min(6, Math.min(w, h) / 2) * 2;
        var pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y).scale(0.5f, 0.5f);
            int width = w * 2, height = h * 2;
            g.fill(0, radius, width, height - radius, color);
            for (int row = 0; row < radius; row++) {
                double dy = radius - row - 0.5;
                double edge = radius - Math.sqrt(radius * radius - dy * dy);
                int partial = (int) edge;
                int alpha = (int) Math.round((color >>> 24) * (1 - (edge - partial)));
                int edgeColor = (color & 0x00FFFFFF) | (alpha << 24);
                hudRow(g, width, row, partial, color, edgeColor);
                hudRow(g, width, height - 1 - row, partial, color, edgeColor);
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static void hudRow(GuiGraphics g, int width, int row, int edge, int color, int edgeColor) {
        g.fill(edge + 1, row, width - edge - 1, row + 1, color);
        if ((edgeColor >>> 24) == 0) return;
        g.fill(edge, row, edge + 1, row + 1, edgeColor);
        g.fill(width - edge - 1, row, width - edge, row + 1, edgeColor);
    }

    static void card(GuiGraphics g, int x, int y, int w, int h, int background, int line) {
        rounded(g, x, y + 2, w, h, 0x1F663F83);
        rounded(g, x, y, w, h, line);
        rounded(g, x + 1, y + 1, w - 2, h - 2, background);
    }

    static int mix(int from, int to, float amount) {
        int value = 0xFF000000;
        for (int i = 0; i <= 16; i += 8) {
            int a = from >> i & 255, b = to >> i & 255;
            value |= Math.round(a + (b - a) * amount) << i;
        }
        return value;
    }
}
