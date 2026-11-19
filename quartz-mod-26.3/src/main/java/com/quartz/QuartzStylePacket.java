package com.quartz;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record QuartzStylePacket(int actor, int mask, int emote) implements CustomPacketPayload {
    public static final Type<QuartzStylePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("quartz", "style_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuartzStylePacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.actor);
                buffer.writeByte(packet.mask);
                buffer.writeByte(packet.emote);
            },
            buffer -> new QuartzStylePacket(buffer.readVarInt(), buffer.readByte(), buffer.readByte()));

    @Override public Type<QuartzStylePacket> type() { return TYPE; }

    public static boolean validSelection(int mask) {
        return mask >= 0 && mask < 64 && (mask & 3) != 3 && (mask & 12) != 12;
    }
}
