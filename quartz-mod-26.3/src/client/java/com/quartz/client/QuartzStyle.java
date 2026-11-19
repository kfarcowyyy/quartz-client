package com.quartz.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class QuartzStyle {
    public record Box(String bone, float x, float y, float z, float w, float h, float d, String color, int[] uv) {
        Box(String bone, float x, float y, float z, float w, float h, float d, String color) {
            this(bone, x, y, z, w, h, d, color, null);
        }
    }
    public record Accessory(String id, String en, String pl, String slot, List<Box> boxes) {
        public Component label() { return QuartzText.of(en, pl); }
    }
    public record Emote(String id, String en, String pl, double seconds) {
        public Component label() { return QuartzText.of(en, pl); }
    }
    private record Catalog(List<Accessory> cosmetics, List<Emote> emotes) {}
    private static final Gson JSON = new Gson();
    private static final Catalog CATALOG = loadCatalog();
    private static Map<String, String> models = Map.of();
    private static List<String> slots = Collections.nCopies(8, null);
    private static int ticks;

    private QuartzStyle() {}

    private static Catalog loadCatalog() {
        try (var stream = Objects.requireNonNull(QuartzStyle.class.getResourceAsStream("/assets/quartz/style-catalog.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JSON.fromJson(reader, Catalog.class);
        } catch (IOException | RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger("Quartz").error("Could not load the cosmetics catalogue", error);
            return new Catalog(List.of(), List.of());
        }
    }

    public static List<Accessory> accessories() { return CATALOG.cosmetics(); }
    public static List<Emote> emotes() { return CATALOG.emotes(); }
    public static boolean equipped(Accessory item) { return item.id().equals(models.get(item.slot())); }
    public static boolean equipped(int actor, Accessory item) {
        return local(actor) ? equipped(item) : QuartzSocial.equipped(actor, item);
    }

    public static boolean wearingWings(int actor) {
        for (Accessory item : accessories()) {
            if (item.slot().equals("back") && equipped(actor, item)) return true;
        }
        return false;
    }

    public static boolean hasSuit(int actor) {
        for (Accessory item : accessories()) if (item.id().equals("web_suit")) return equipped(actor, item);
        return false;
    }

    static int mask() {
        int result = 0;
        for (int i = 0; i < accessories().size(); i++) if (equipped(accessories().get(i))) result |= 1 << i;
        return result;
    }
    public static boolean local(int id) {
        var player = Minecraft.getInstance().player;
        return player != null && id == player.getId();
    }
    public static Emote emote(int i) {
        String id = slots.get(i);
        for (Emote emote : emotes()) if (emote.id().equals(id)) return emote;
        return null;
    }

    private static Path file(String name) {
        String shared = System.getProperty("quartz.wardrobe");
        Path base = shared == null ? Minecraft.getInstance().gameDirectory.toPath().resolve("config/quartz-wardrobe.json")
                : Path.of(URI.create(shared));
        String stem = base.getFileName().toString();
        return base.resolveSibling(stem.substring(0, stem.length() - 5) + "-" + name + ".json");
    }

    static void refresh() {
        try {
            Map<String, String> value = JSON.fromJson(Files.readString(file("models")), new TypeToken<Map<String, String>>() {}.getType());
            var valid = new HashMap<String, String>();
            if (value != null) for (Accessory item : accessories()) {
                if (item.id().equals(value.get(item.slot()))) valid.put(item.slot(), item.id());
            }
            models = Map.copyOf(valid);
        } catch (IOException | RuntimeException ignored) { models = Map.of(); }
        try {
            List<String> value = JSON.fromJson(Files.readString(file("emotes")), new TypeToken<List<String>>() {}.getType());
            var valid = new ArrayList<String>(Collections.nCopies(8, null));
            if (value != null) for (int i = 0; i < Math.min(8, value.size()); i++) {
                final String id = value.get(i);
                if (emotes().stream().anyMatch(entry -> entry.id().equals(id))) valid.set(i, id);
            }
            slots = Collections.unmodifiableList(valid);
        } catch (IOException | RuntimeException ignored) { slots = Collections.nCopies(8, null); }
    }

    static void tick() { if (++ticks % 10 == 0) refresh(); }

    static void toggle(Accessory item) throws IOException {
        refresh();
        var changed = new HashMap<>(models);
        if (equipped(item)) changed.remove(item.slot());
        else changed.put(item.slot(), item.id());
        save(file("models"), changed);
        models = Map.copyOf(changed);
    }

    static void setEmote(int index, Emote emote) throws IOException {
        refresh();
        var changed = new ArrayList<>(slots);
        changed.set(index, emote == null ? null : emote.id());
        save(file("emotes"), changed);
        slots = Collections.unmodifiableList(changed);
    }

    private static void save(Path file, Object data) throws IOException {
        Files.createDirectories(file.getParent());
        Path pending = Files.createTempFile(file.getParent(), "quartz-style-", ".tmp");
        try {
            Files.writeString(pending, JSON.toJson(data));
            try { Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(pending); }
    }
}
