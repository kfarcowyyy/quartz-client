package com.quartz.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class QuartzHud {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    // Title-screen previews are available before the item component registry is bound.
    private static final List<Identifier> EXAMPLE_ARMOR = List.of(
            Identifier.withDefaultNamespace("textures/item/diamond_helmet.png"),
            Identifier.withDefaultNamespace("textures/item/diamond_chestplate.png"),
            Identifier.withDefaultNamespace("textures/item/diamond_leggings.png"),
            Identifier.withDefaultNamespace("textures/item/diamond_boots.png"));
    private static final Identifier HOTBAR_TEXTURE = Identifier.withDefaultNamespace("textures/gui/sprites/hud/hotbar.png");
    private static final List<Identifier> EMPTY_ARMOR = List.of(
            Identifier.withDefaultNamespace("container/slot/helmet"),
            Identifier.withDefaultNamespace("container/slot/chestplate"),
            Identifier.withDefaultNamespace("container/slot/leggings"),
            Identifier.withDefaultNamespace("container/slot/boots"));
    private static final int ARMOR_SLOT = 20;
    private static final int ARMOR_THICKNESS = ARMOR_SLOT + 2;
    private static final int ARMOR_LENGTH = SLOTS.length * ARMOR_SLOT + 2;
    private static final int CELL_WIDTH = 34;
    private static final java.time.format.DateTimeFormatter CLOCK = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    private static final com.sun.management.OperatingSystemMXBean OS =
            java.lang.management.ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean ? bean : null;
    private static long lastSample, joined;
    private static String cpuRam = "";
    private static Object world;
    private static double previousX, previousZ, speed;
    private static boolean hasPosition, wasDead;
    private static String lastDeath = "", lastDeathXZ = "";
    private static long lastAttack, stopwatchStart, stopwatchElapsed;
    private static double reach;

    static void attack(Minecraft client, net.minecraft.world.entity.Entity target) {
        if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() == target) {
            reach = client.player.getEyePosition().distanceTo(hit.getLocation());
            lastAttack = System.nanoTime();
        }
    }

    static boolean stopwatchRunning() { return stopwatchStart != 0; }
    static void toggleStopwatch() {
        long now = System.nanoTime();
        if (stopwatchStart == 0) stopwatchStart = now;
        else {
            stopwatchElapsed += now - stopwatchStart;
            stopwatchStart = 0;
        }
    }

    static void resetStopwatch() {
        stopwatchElapsed = 0;
        stopwatchStart = 0;
    }

    static void tick(Minecraft client) {
        if (client.level != world) {
            world = client.level;
            joined = System.nanoTime();
            hasPosition = false;
            speed = 0;
            wasDead = false;
            lastAttack = 0;
        }
        if (client.player == null) return;
        boolean dead = client.player.isDeadOrDying();
        if (dead && !wasDead) {
            lastDeath = coords(client, true);
            lastDeathXZ = coords(client, false);
        }
        wasDead = dead;
        double x = client.player.getX(), z = client.player.getZ();
        if (hasPosition) {
            double distance = Math.hypot(x - previousX, z - previousZ);
            speed = distance > 12 ? 0 : (speed + 20 * distance) / 2;
        }
        previousX = x;
        previousZ = z;
        hasPosition = true;
    }

    private QuartzHud() {}

    record Cell(String value, ItemStack item, Identifier icon) {}
    record Panel(String text, List<Cell> cells, int width, int height) {}
    record Position(int x, int y) {}

    static Panel panel(QuartzHudModule module, Minecraft client, boolean preview, int width, int height) {
        var settings = module.settings();
        if (!module.icons()) {
            String template = settings.text == null || settings.text.isBlank() ? module.template : settings.text;
            String measurement = value(module, client, preview);
            if (measurement.isBlank()) return new Panel("", List.of(), 0, 0);
            String text = template.replace("{value}", measurement);
            if (settings.brackets) text = "[" + text + "]";
            text = client.font.plainSubstrByWidth(text, Math.max(0, width - 12));
            return new Panel(text, List.of(), Math.min(width, client.font.width(text) + 12), 18);
        }

        List<Cell> cells = new ArrayList<>();
        if (module == QuartzHudModule.ARMOR_BAR) {
            int armor = preview ? 14 : client.player.getArmorValue();
            return new Panel(Integer.toString(armor), List.of(), 92, 12);
        }
        if (module == QuartzHudModule.INVENTORY) {
            int columns = Math.min(9, Math.max(1, width / 20));
            int count = Math.min(27, columns * Math.max(1, height / 20));
            for (int i = 0; i < count; i++) {
                ItemStack item = preview ? ItemStack.EMPTY : client.player.getInventory().getItem(i + 9);
                Identifier icon = preview && i < EXAMPLE_ARMOR.size() ? EXAMPLE_ARMOR.get(i) : null;
                cells.add(new Cell(item.getCount() > 1 ? Integer.toString(item.getCount()) : "", item, icon));
            }
            return new Panel("", cells, columns * 20, ((count + columns - 1) / columns) * 20);
        }
        if (module == QuartzHudModule.ARMOR) {
            for (int i = 0; i < SLOTS.length; i++) {
                if (preview) {
                    cells.add(new Cell("100%", ItemStack.EMPTY, EXAMPLE_ARMOR.get(i)));
                    continue;
                }
                ItemStack item = client.player.getItemBySlot(SLOTS[i]);
                int max = item.getMaxDamage();
                String value = max > 0 ? Math.max(0, 100 - (int) (100L * item.getDamageValue() / max)) + "%" : "";
                cells.add(new Cell(value, item, item.isEmpty() ? EMPTY_ARMOR.get(i) : null));
            }
            int armorWidth = settings.armorHorizontal ? ARMOR_LENGTH : ARMOR_THICKNESS;
            int armorHeight = settings.armorHorizontal ? ARMOR_THICKNESS : ARMOR_LENGTH;
            return new Panel("", cells, armorWidth, armorHeight);
        } else if (preview) {
            cells.add(new Cell("1:24", ItemStack.EMPTY, Identifier.withDefaultNamespace("mob_effect/speed")));
            cells.add(new Cell("0:48", ItemStack.EMPTY, Identifier.withDefaultNamespace("mob_effect/regeneration")));
        } else {
            for (var effect : client.player.getActiveEffects()) {
                var key = effect.getEffect().unwrapKey();
                if (key.isEmpty() || !effect.showIcon()) continue;
                int seconds = Math.max(0, effect.getDuration() / 20);
                String value = effect.isInfiniteDuration() ? "∞" : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
                cells.add(new Cell(value, ItemStack.EMPTY, key.get().identifier().withPrefix("mob_effect/")));
            }
        }
        int columns = Math.max(1, width / CELL_WIDTH);
        int line = settings.showValue ? 35 : 26;
        int count = Math.min(cells.size(), columns * Math.max(1, height / line));
        return new Panel("", cells.subList(0, count), Math.min(columns, count) * CELL_WIDTH, ((count + columns - 1) / columns) * line);
    }

    private static String value(QuartzHudModule module, Minecraft client, boolean preview) {
        return switch (module) {
            case FPS -> Integer.toString(preview ? 144 : client.getFps());
            case CPS -> preview ? (module.settings().alternate ? "8 | 3" : "8") : QuartzConfig.cps(module.settings().alternate);
            case COORDS -> preview ? "128  72  -45" : coords(client, module.settings().alternate);
            case DIRECTION -> preview ? "North" : client.player.getDirection().getName();
            case PING -> preview ? "42 ms" : ping(client);
            case SPEED -> String.format(Locale.ROOT, "%.1f %s", (preview ? 5.6 : speed) * (module.settings().alternate ? 3.6 : 1),
                    module.settings().alternate ? "km/h" : "m/s");
            case TIME -> (preview ? java.time.LocalTime.of(15, 27) : java.time.LocalTime.now())
                    .format(module.settings().alternate ? CLOCK_12 : CLOCK);
            case PLAYTIME -> preview ? "01:12:02" : elapsed();
            case SATURATION -> preview ? "20.0" : String.format(Locale.ROOT, "%.1f", client.player.getFoodData().getSaturationLevel());
            case RESOURCES -> preview ? (module.settings().alternate ? "RAM 800/2048 MB" : "CPU 38% · RAM 800/2048 MB") : system(module.settings().alternate);
            case PACK -> preview ? "Example resource pack" : pack(client);
            case BLOCK -> preview ? "Grass Block" : block(client);
            case ITEM_INFO -> preview ? "Diamond Pickaxe" : held(client);
            case ITEM_COUNT -> preview ? "Arrow ×64" : countItem(client);
            case ELYTRA -> preview ? "92%" : elytra(client);
            case TOTEM -> preview ? "3" : totems(client, module.settings().alternate);
            case ATTACK -> preview ? "89%" : Math.round(client.player.getAttackStrengthScale(0.0f) * 100) + "%";
            case KEYSTROKES -> preview ? "W  A  S  D  ␣" : keys(client);
            case MOUSESTROKES -> preview ? "LMB ●  RMB ○" : mouse(client);
            case LIGHT -> preview ? "14" : light(client, module.settings().alternate);
            case REACH -> preview ? "2.92" : lastAttack == 0 || System.nanoTime() - lastAttack > 3_000_000_000L ? ""
                    : String.format(Locale.ROOT, "%.2f", reach);
            case DEATH -> preview ? "89  71  102" : (module.settings().alternate ? lastDeath : lastDeathXZ);
            case STOPWATCH -> preview ? "00:23:12" : duration(stopwatchElapsed + (stopwatchStart == 0 ? 0 : System.nanoTime() - stopwatchStart));
            default -> "";
        };
    }

    private static String coords(Minecraft client, boolean vertical) {
        var p = client.player.blockPosition();
        return p.getX() + (vertical ? "  " + p.getY() : "") + "  " + p.getZ();
    }

    private static String ping(Minecraft client) {
        if (client.getConnection() == null) return "—";
        var entry = client.getConnection().getPlayerInfo(client.player.getUUID());
        return entry == null ? "—" : entry.getLatency() + " ms";
    }

    private static String elapsed() { return duration(System.nanoTime() - joined); }

    private static String duration(long nanos) {
        long s = Math.max(0, nanos / 1_000_000_000L);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    private static String light(Minecraft client, boolean blockOnly) {
        var pos = client.player.blockPosition();
        return Integer.toString(blockOnly ? client.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos)
                : client.level.getMaxLocalRawBrightness(pos));
    }

    private static String totems(Minecraft client, boolean hideZero) {
        int amount = count(client, Items.TOTEM_OF_UNDYING);
        return hideZero && amount == 0 ? "" : Integer.toString(amount);
    }

    private static final java.time.format.DateTimeFormatter CLOCK_12 =
            java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);

    private static String system(boolean memoryOnly) {
        long now = System.nanoTime();
        if (now - lastSample < 1_000_000_000L && !cpuRam.isEmpty()) return memoryOnly ? cpuRam.substring(cpuRam.indexOf("RAM ")) : cpuRam;
        var ram = Runtime.getRuntime();
        double cpu = OS == null ? -1 : OS.getCpuLoad();
        cpuRam = (cpu < 0 ? "" : "CPU " + Math.round(cpu * 100) + "% · ")
                + "RAM " + ((ram.totalMemory() - ram.freeMemory()) >> 20) + "/" + (ram.maxMemory() >> 20) + " MB";
        lastSample = now;
        return memoryOnly ? cpuRam.substring(cpuRam.indexOf("RAM ")) : cpuRam;
    }

    private static String pack(Minecraft client) {
        var ids = client.getResourcePackRepository().getSelectedIds().stream().filter(id -> id.startsWith("file/")).toList();
        return ids.isEmpty() ? QuartzText.of("Default", "Domyślna").getString() : ids.getLast().substring(5);
    }

    private static String block(Minecraft client) {
        return client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && client.level != null
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? client.level.getBlockState(hit.getBlockPos()).getBlock().getName().getString() : "";
    }

    private static String held(Minecraft client) {
        var stack = client.player.getMainHandItem();
        return stack.isEmpty() ? "" : stack.getHoverName().getString();
    }

    private static int count(Minecraft client, net.minecraft.world.item.Item item) {
        int total = 0;
        var inventory = client.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static String countItem(Minecraft client) {
        var stack = client.player.getMainHandItem();
        String target = QuartzHudModule.ITEM_COUNT.settings().target;
        if (target != null && !target.isBlank()) {
            var id = Identifier.tryParse(target);
            if (id != null) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);
                if (item.isPresent()) stack = new ItemStack(item.get());
            }
        }
        return stack.isEmpty() ? "" : stack.getHoverName().getString() + " ×" + count(client, stack.getItem());
    }

    private static String elytra(Minecraft client) {
        var chest = client.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.is(Items.ELYTRA) || chest.getMaxDamage() <= 0) return "";
        return Math.max(0, 100 - chest.getDamageValue() * 100 / chest.getMaxDamage()) + "%";
    }

    private static String keys(Minecraft client) {
        var k = client.options;
        return key(k.keyUp) + "  " + key(k.keyLeft) + "  " + key(k.keyDown) + "  " + key(k.keyRight) + "  " + key(k.keyJump);
    }

    private static String key(net.minecraft.client.KeyMapping key) {
        String name = key.getTranslatedKeyMessage().getString();
        return key.isDown() ? "[" + name + "]" : name;
    }

    private static String mouse(Minecraft client) {
        return "LMB " + (client.options.keyAttack.isDown() ? "●" : "○")
                + "  RMB " + (client.options.keyUse.isDown() ? "●" : "○");
    }

    static Position position(QuartzConfig.Hud settings, Panel panel, int width, int height) {
        int x = Math.max(0, width - panel.width()), y = Math.max(0, height - panel.height());
        return new Position(settings.oldX == null ? x * clamp(settings.x, 1000) / 1000 : clamp(settings.oldX, x),
                settings.oldY == null ? y * clamp(settings.y, 1000) / 1000 : clamp(settings.oldY, y));
    }

    static void move(QuartzConfig.Hud settings, Panel panel, int x, int y, int width, int height) {
        settings.oldX = null;
        settings.oldY = null;
        int maxX = Math.max(1, width - panel.width()), maxY = Math.max(1, height - panel.height());
        settings.x = Math.round(1000f * clamp(x, maxX) / maxX);
        settings.y = Math.round(1000f * clamp(y, maxY) / maxY);
    }

    private static int clamp(int n, int max) { return Math.max(0, Math.min(max, n)); }

    static void preview(GuiGraphicsExtractor g, Minecraft client, QuartzHudModule module, Panel panel,
                        int x, int y, int width, int height) {
        if (panel.width() <= 0 || panel.height() <= 0 || width <= 0 || height <= 0) return;
        float scale = Math.min(1, Math.min((float) width / panel.width(), (float) height / panel.height()));
        var pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(x + (width - panel.width() * scale) / 2, y + (height - panel.height() * scale) / 2);
            pose.scale(scale, scale);
            draw(g, client, module, panel, 0, 0);
        } finally {
            pose.popMatrix();
        }
    }

    private static void drawCellItem(GuiGraphicsExtractor g, Cell cell, int x, int y) {
        if (cell.icon() == null) {
            if (!cell.item().isEmpty()) g.item(cell.item(), x, y);
        } else if (EXAMPLE_ARMOR.contains(cell.icon())) {
            g.blit(RenderPipelines.GUI_TEXTURED, cell.icon(), x, y, 0, 0, 16, 16, 16, 16, 16, 16);
        } else {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, cell.icon(), x, y, 16, 16);
        }
    }

    private static void armor(GuiGraphicsExtractor g, Minecraft client, Panel panel, int x, int y, QuartzConfig.Hud settings) {
        if (settings.background) {
            if (settings.armorSmooth) QuartzTheme.hudBackground(g, x, y, panel.width(), panel.height(), settings.bg);
            else armorHotbar(g, x, y, settings.armorHorizontal);
        }
        for (int i = 0; i < panel.cells().size(); i++) {
            Cell cell = panel.cells().get(i);
            int left = x + 3 + (settings.armorHorizontal ? i * ARMOR_SLOT : 0);
            int top = y + 3 + (settings.armorHorizontal ? 0 : i * ARMOR_SLOT);
            drawCellItem(g, cell, left, top);
            if (settings.showValue && (settings.fg >>> 24) >= 4 && !cell.value().isEmpty()) {
                var pose = g.pose();
                pose.pushMatrix();
                try {
                    pose.translate(left + 16 - client.font.width(cell.value()) * 0.67f, top + 12).scale(0.67f, 0.67f);
                    g.text(client.font, cell.value(), 0, 0, settings.fg, settings.shadow);
                } finally {
                    pose.popMatrix();
                }
            }
        }
    }

    private static void armorHotbar(GuiGraphicsExtractor g, int x, int y, boolean horizontal) {
        var pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(horizontal ? x : x + ARMOR_THICKNESS, y);
            if (!horizontal) pose.rotate((float) (Math.PI / 2));
            // Preserve the original slot spacing. The last column closes the shortened bar.
            int interior = ARMOR_LENGTH - 1;
            g.blit(RenderPipelines.GUI_TEXTURED, HOTBAR_TEXTURE, 0, 0, 0, 0,
                    interior, ARMOR_THICKNESS, interior, ARMOR_THICKNESS, 182, 22);
            g.blit(RenderPipelines.GUI_TEXTURED, HOTBAR_TEXTURE, interior, 0, 181, 0,
                    1, ARMOR_THICKNESS, 1, ARMOR_THICKNESS, 182, 22);
        } finally {
            pose.popMatrix();
        }
    }

    static void draw(GuiGraphicsExtractor g, Minecraft client, QuartzHudModule module, Panel panel, int x, int y) {
        if (panel.width() == 0) return;
        var settings = module.settings();
        var font = client.font;
        if (module == QuartzHudModule.ARMOR) {
            armor(g, client, panel, x, y, settings);
            return;
        }
        if (settings.background) QuartzTheme.hudBackground(g, x, y, panel.width(), panel.height(), settings.bg);
        if (module == QuartzHudModule.INVENTORY) {
            int columns = Math.max(1, panel.width() / 20);
            for (int i = 0; i < panel.cells().size(); i++) {
                Cell cell = panel.cells().get(i);
                int left = x + i % columns * 20, top = y + i / columns * 20;
                drawCellItem(g, cell, left + 2, top + 2);
                if (settings.showValue && (settings.fg >>> 24) >= 4 && !cell.value().isEmpty())
                    g.text(font, cell.value(), left + 18 - font.width(cell.value()), top + 11, settings.fg, settings.shadow);
            }
            return;
        }
        if (module == QuartzHudModule.ARMOR_BAR) {
            int armor = Integer.parseInt(panel.text());
            for (int i = 0; i < 10; i++) {
                String sprite = armor >= (i + 1) * 2 ? "full" : armor == i * 2 + 1 ? "half" : "empty";
                g.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/armor_" + sprite), x + 1 + i * 9, y + 1, 9, 9);
            }
            return;
        }
        if (!module.icons()) {
            if ((settings.fg >>> 24) >= 4)
                g.text(font, panel.text(), x + 6, y + 5, settings.fg, settings.shadow);
            return;
        }
        int columns = Math.max(1, panel.width() / CELL_WIDTH);
        int line = settings.showValue ? 35 : 26;
        for (int i = 0; i < panel.cells().size(); i++) {
            Cell cell = panel.cells().get(i);
            int left = x + i % columns * CELL_WIDTH, top = y + i / columns * line;
            drawCellItem(g, cell, left + 9, top + 5);
            if (settings.showValue && (settings.fg >>> 24) >= 4) {
                String value = font.plainSubstrByWidth(cell.value(), CELL_WIDTH - 2);
                g.text(font, value, left + (CELL_WIDTH - font.width(value)) / 2, top + 26, settings.fg, settings.shadow);
            }
        }
    }

    static void inGame(GuiGraphicsExtractor g, Minecraft client) {
        int width = g.guiWidth(), height = g.guiHeight();
        for (QuartzHudModule module : QuartzHudModule.values()) {
            if (!module.settings().enabled) continue;
            Panel panel = panel(module, client, false, width, height);
            Position pos = position(module.settings(), panel, width, height);
            draw(g, client, module, panel, pos.x(), pos.y());
        }
    }
}
