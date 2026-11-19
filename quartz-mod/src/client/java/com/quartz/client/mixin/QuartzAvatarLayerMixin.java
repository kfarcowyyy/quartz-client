package com.quartz.client.mixin;

import com.quartz.client.QuartzStyleLayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class QuartzAvatarLayerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    @SuppressWarnings("unchecked")
    private void quartz$registerStyleLayer(CallbackInfo info) {
        var parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this;
        ((QuartzLayerInvoker) this).quartz$addLayer(new QuartzStyleLayer(parent));
    }
}
