package com.quartz.client;

import com.quartz.QuartzStylePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

final class QuartzSocial {
    private static final Map<Integer, Integer> APPEARANCES = new HashMap<>();
    private static int sentMask = -1;
    private static int sentActor = -1;

    private QuartzSocial() {}

    static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientPlayConnectionEvents.INIT.register((handler, client) -> clear());
        ClientPlayNetworking.registerGlobalReceiver(QuartzStylePacket.TYPE, (packet, context) ->
                context.client().execute(() -> {
                    if (QuartzStyle.local(packet.actor())) return;
                    if (packet.mask() == -1) {
                        APPEARANCES.remove(packet.actor());
                        QuartzMotion.remote(packet.actor(), null);
                    } else if (QuartzStylePacket.validSelection(packet.mask())) {
                        APPEARANCES.put(packet.actor(), packet.mask());
                        int position = packet.emote() - 1;
                        if (position >= 0 && position < QuartzStyle.emotes().size())
                            QuartzMotion.remote(packet.actor(), QuartzStyle.emotes().get(position));
                    }
                }));
    }

    static void tick(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return;
        int mask = QuartzStyle.mask(), actor = client.player.getId();
        if ((sentMask != mask || sentActor != actor) && send(mask, 0)) {
            sentMask = mask;
            sentActor = actor;
        }
    }

    static void play(QuartzStyle.Emote emote) {
        int index = QuartzStyle.emotes().indexOf(emote);
        if (index >= 0) send(QuartzStyle.mask(), index + 1);
    }

    static boolean equipped(int actor, QuartzStyle.Accessory item) {
        int position = QuartzStyle.accessories().indexOf(item);
        return position >= 0 && (APPEARANCES.getOrDefault(actor, 0) & (1 << position)) != 0;
    }

    private static boolean send(int mask, int emote) {
        if (!ClientPlayNetworking.canSend(QuartzStylePacket.TYPE)) return false;
        ClientPlayNetworking.send(new QuartzStylePacket(0, mask, emote));
        return true;
    }

    private static void clear() {
        APPEARANCES.clear();
        sentMask = sentActor = -1;
        QuartzMotion.clearRemote();
    }
}
