package com.quartz;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class QuartzServerStyle implements ModInitializer {
    private record Look(int actor, int mask, long when) {}
    private final Map<UUID, Look> players = new HashMap<>();

    @Override public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(QuartzStylePacket.TYPE, QuartzStylePacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuartzStylePacket.TYPE, QuartzStylePacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(QuartzStylePacket.TYPE, (packet, context) ->
                context.server().execute(() -> receive(context.server(), context.player(), packet)));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (ServerPlayNetworking.canSend(player, QuartzStylePacket.TYPE)) {
                for (Look look : players.values())
                    ServerPlayNetworking.send(player, new QuartzStylePacket(look.actor(), look.mask(), 0));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            var look = players.remove(player.getUUID());
            if (look != null) broadcast(server, player, new QuartzStylePacket(look.actor(), -1, 0));
        });
    }

    private void receive(MinecraftServer server, ServerPlayer player, QuartzStylePacket packet) {
        if (!QuartzStylePacket.validSelection(packet.mask()) || packet.emote() < 0 || packet.emote() > 8) return;
        long now = System.nanoTime();
        var previous = players.get(player.getUUID());
        if (previous != null && now - previous.when() < 90_000_000L) return;
        if (previous != null && previous.actor() != player.getId())
            broadcast(server, player, new QuartzStylePacket(previous.actor(), -1, 0));
        players.put(player.getUUID(), new Look(player.getId(), packet.mask(), now));
        broadcast(server, player, new QuartzStylePacket(player.getId(), packet.mask(), packet.emote()));
    }

    private static void broadcast(MinecraftServer server, ServerPlayer owner, QuartzStylePacket packet) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != owner && ServerPlayNetworking.canSend(player, QuartzStylePacket.TYPE))
                ServerPlayNetworking.send(player, packet);
        }
    }
}
