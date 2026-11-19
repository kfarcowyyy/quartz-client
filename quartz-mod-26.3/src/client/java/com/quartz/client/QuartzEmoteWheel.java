package com.quartz.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class QuartzEmoteWheel extends QuartzSafeScreen {
    private final KeyMapping key;
    private final int[] x = new int[8], y = new int[8];
    private int cardWidth, centerX, centerY;
    private int selected = -1;
    private long hoverStart;

    QuartzEmoteWheel(KeyMapping key) {
        super(QuartzText.of("Emote wheel", "Koło emotek"));
        this.key = key;
    }

    @Override protected void init() {
        centerX = width / 2;
        centerY = height / 2;
        int radius = Math.min(Math.max(43, (height - 68) / 2), Math.max(48, (width - 100) / 2));
        cardWidth = Math.min(76, Math.max(44, radius - 6));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            x[i] = centerX + (int) (Math.sin(angle) * radius);
            y[i] = centerY - (int) (Math.cos(angle) * radius);
        }
        addRenderableWidget(new QuartzButton(QuartzText.of("Edit wheel", "Edytuj koło"),
                centerX - 36, centerY + 7, 72, 17, true,
                () -> Minecraft.getInstance().gui.setScreen(new QuartzWardrobeScreen(null, true))));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void drawQuartz(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x50302146);
        QuartzTheme.card(g, centerX - 45, centerY - 26, 90, 58, QuartzTheme.WHITE, QuartzTheme.OUTLINE);
        var caption = font.plainSubstrByWidth(title.getString(), 82);
        g.text(font, caption, centerX - font.width(caption) / 2, centerY - 12, QuartzTheme.ACCENT, false);
        int hit = hit(mx, my);
        if (hit != selected) {
            selected = hit;
            hoverStart = System.nanoTime();
        }
        for (int i = 0; i < 8; i++) {
            var item = QuartzStyle.emote(i);
            boolean active = i == selected && item != null;
            int left = x[i] - cardWidth / 2, top = y[i] - 13;
            QuartzTheme.card(g, left, top, cardWidth, 26, active ? 0xFFE3C5FF : QuartzTheme.WHITE, QuartzTheme.OUTLINE);
            String label = item == null ? "–" : font.plainSubstrByWidth(item.label().getString(), cardWidth - 7);
            g.text(font, label, x[i] - font.width(label) / 2, y[i] - 4,
                    item == null ? QuartzTheme.MUTED : QuartzTheme.INK, false);
        }
        drawWidgets(g, mx, my, delta);
    }

    private int hit(int mx, int my) {
        for (int i = 0; i < 8; i++) if (Math.abs(mx - x[i]) < cardWidth / 2 && Math.abs(my - y[i]) < 13) return i;
        return -1;
    }

    @Override public void tick() {
        super.tick();
        if (selected >= 0 && QuartzStyle.emote(selected) != null && System.nanoTime() - hoverStart > 260_000_000L) choose();
    }

    private void choose() {
        var emote = selected < 0 ? null : QuartzStyle.emote(selected);
        onClose();
        if (emote != null) QuartzMotion.play(emote);
    }

    @Override public boolean keyReleased(KeyEvent event) {
        if (key.matches(event)) { choose(); return true; }
        return super.keyReleased(event);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (key.matchesMouse(event)) { choose(); return true; }
        return super.mouseReleased(event);
    }

    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(null); }
}
