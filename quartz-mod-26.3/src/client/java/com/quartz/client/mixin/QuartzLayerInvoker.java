package com.quartz.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface QuartzLayerInvoker {
    @Invoker("addLayer")
    boolean quartz$addLayer(RenderLayer<?, ?> layer);
}
