package com.quartz.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QuartzStyleLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private record Piece(String bone, ModelPart part, int color) {}
    private static final Identifier MATERIAL = Identifier.fromNamespaceAndPath("quartz", "textures/wardrobe/style-material.png");
    private static final Map<String, List<Piece>> MODELS = new HashMap<>();
    private static final Piece MOUTH = bake(new QuartzStyle.Box("head", -1.25f, -2.65f, -4.64f, 2.5f, 1.67f, .12f, "#24172c"));

    public QuartzStyleLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

    private static Piece bake(QuartzStyle.Box box) {
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("box", CubeListBuilder.create().texOffs(0, 0)
                .addBox(box.x(), box.y(), box.z(), box.w(), box.h(), box.d()), PartPose.ZERO);
        return new Piece(box.bone(), LayerDefinition.create(mesh, 64, 64).bakeRoot(),
                0xFF000000 | Integer.parseInt(box.color().substring(1), 16));
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        if (state.isInvisible || state.isSpectator) return;
        boolean mouth = QuartzMotion.shouting(state.id);
        if (!mouth && QuartzStyle.accessories().stream().noneMatch(item -> QuartzStyle.equipped(state.id, item))) return;
        var model = getParentModel();
        model.setupAnim(state);
        var type = RenderTypes.entityCutout(MATERIAL);
        if (mouth) {
            pose.pushPose();
            try {
                model.root().translateAndRotate(pose);
                model.head.translateAndRotate(pose);
                collector.order(1).submitModelPart(MOUTH.part(), pose, type, light, OverlayTexture.NO_OVERLAY,
                        null, MOUTH.color());
            } finally { pose.popPose(); }
        }
        for (var item : QuartzStyle.accessories()) {
            if (!QuartzStyle.equipped(state.id, item)) continue;
            boolean glow = item.id().equals("halo");
            for (var piece : MODELS.computeIfAbsent(item.id(), key -> item.boxes().stream().map(QuartzStyleLayer::bake).toList())) {
                pose.pushPose();
                try {
                    model.root().translateAndRotate(pose);
                    bone(model, piece.bone()).translateAndRotate(pose);
                    if (piece.bone().equals("wingL") || piece.bone().equals("wingR")) {
                        float sign = piece.bone().equals("wingL") ? -1 : 1;
                        pose.translate(sign * .12f, .14f, .18f);
                        float beat = (float) Math.sin(state.ageInTicks * .40f);
                        pose.mulPose(new Matrix4f().rotationY(sign * (.40f + beat * .36f)));
                        pose.mulPose(new Matrix4f().rotationZ(sign * (.13f + beat * .10f)));
                    }
                    collector.order(1).submitModelPart(piece.part(), pose, type, glow ? 0xF000F0 : light,
                            OverlayTexture.NO_OVERLAY, null, piece.color());
                } finally { pose.popPose(); }
            }
        }
    }

    private static ModelPart bone(PlayerModel model, String name) {
        return switch (name) {
            case "head" -> model.head;
            case "leftArm" -> model.leftArm;
            case "rightArm" -> model.rightArm;
            case "leftLeg" -> model.leftLeg;
            case "rightLeg" -> model.rightLeg;
            default -> model.body;
        };
    }
}
