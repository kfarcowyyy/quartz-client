package com.quartz.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.quartz.client.QuartzConfig;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.HashMap;
import java.util.Map;

@Mixin(ChatComponent.class)
abstract class QuartzChatMixin {
    @Unique private static final long QUARTZ_CHAT_DURATION = 220_000_000L;
    @Unique private final Map<Integer, Long> quartz$arrivals = new HashMap<>();
    @Unique private long quartz$lastArrival;
    @Unique private long quartz$frameTime;

    @Inject(method = "addMessageToQueue", at = @At("TAIL"))
    private void quartz$noteArrival(GuiMessage message, CallbackInfo callback) {
        if (!QuartzConfig.data.smoothChat || ((ChatComponent) (Object) this).isChatFocused()) return;
        long now = System.nanoTime();
        quartz$arrivals.values().removeIf(then -> now - then >= QUARTZ_CHAT_DURATION);
        quartz$arrivals.putIfAbsent(message.addedTime(), now);
        quartz$lastArrival = now;
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void quartz$clearArrivals(boolean clearHistory, CallbackInfo callback) {
        quartz$arrivals.clear();
        quartz$lastArrival = 0L;
    }

    @WrapMethod(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V")
    private void quartz$slide(GuiGraphicsExtractor graphics, Font font, int tick, int mouseX, int mouseY,
                             ChatComponent.DisplayMode mode, boolean updateCursor, Operation<Void> original) {
        long now = System.nanoTime();
        if (mode.foreground || !QuartzConfig.data.smoothChat || quartz$lastArrival == 0L
                || now - quartz$lastArrival >= QUARTZ_CHAT_DURATION) {
            original.call(graphics, font, tick, mouseX, mouseY, mode, updateCursor);
            return;
        }
        var pose = graphics.pose();
        pose.pushMatrix();
        quartz$frameTime = now;
        try {
            pose.translate(0f, 5f * (1f - quartz$progress(now - quartz$lastArrival)));
            original.call(graphics, font, tick, mouseX, mouseY, mode, updateCursor);
        } finally {
            quartz$frameTime = 0L;
            pose.popMatrix();
        }
    }

    @ModifyArgs(method = "forEachLine", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V"))
    private void quartz$fadeNewMessages(Args args) {
        if (quartz$frameTime == 0L) return;
        GuiMessage.Line line = args.get(0);
        Long arrival = quartz$arrivals.get(line.addedTime());
        if (arrival != null) {
            float alpha = args.get(2);
            args.set(2, alpha * (.12f + .88f * quartz$progress(quartz$frameTime - arrival)));
        }
    }

    @Unique
    private static float quartz$progress(long elapsed) {
        float t = Math.clamp((float) elapsed / QUARTZ_CHAT_DURATION, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }
}
