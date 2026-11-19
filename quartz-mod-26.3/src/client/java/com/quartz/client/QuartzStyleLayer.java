package com.quartz.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.CubeDeformation;
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
    private static final Identifier SUIT = Identifier.fromNamespaceAndPath("quartz", "textures/wardrobe/web-suit.png");
    private static final Map<String, Map<String, List<Piece>>> MODELS = new HashMap<>();
    private static final Piece MOUTH = bake(new QuartzStyle.Box("head", -1.24f, -2.8f, -4.59f, 2.48f, 1.92f, .10f, "#3b2538"));

    private static final Piece LIP = bake(new QuartzStyle.Box("head", -.68f, -1.32f, -4.61f, 1.36f, .27f, .05f, "#da7781"));

    public QuartzStyleLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) { super(parent); }

    private static Piece bake(QuartzStyle.Box box) {
        var mesh = new MeshDefinition();
        var cube = CubeListBuilder.create();
        if (box.uv() == null) {
            cube.texOffs(0, 0).addBox(box.x(), box.y(), box.z(), box.w(), box.h(), box.d());
        } else {
            cube.texOffs(box.uv()[0], box.uv()[1]).addBox(box.x(), box.y(), box.z(), box.w(), box.h(), box.d(),
                    new CubeDeformation(.19f));
        }
        mesh.getRoot().addOrReplaceChild("box", cube, PartPose.ZERO);
        return new Piece(box.bone(), LayerDefinition.create(mesh, 64, 64).bakeRoot(),
                0xFF000000 | Integer.parseInt(box.color().substring(1), 16));
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        if (state.isInvisible || state.isSpectator) return;
        float mouth = QuartzMotion.shout(state.id);
        if (mouth <= 0 && QuartzStyle.accessories().stream().noneMatch(item -> QuartzStyle.equipped(state.id, item))) return;
        var model = getParentModel();
        model.setupAnim(state);
        var material = RenderTypes.entityCutout(MATERIAL);
        if (mouth > 0) {
            pose.pushPose();
            try {
                model.root().translateAndRotate(pose);
                model.head.translateAndRotate(pose);
                pose.translate(0, -.11f, 0);
                pose.scale(.85f + .15f * mouth, .19f + .81f * mouth, 1);
                pose.translate(0, .11f, 0);
                collector.order(1).submitModelPart(MOUTH.part(), pose, material, light, OverlayTexture.NO_OVERLAY,
                        null, MOUTH.color());
                collector.order(1).submitModelPart(LIP.part(), pose, material, light, OverlayTexture.NO_OVERLAY,
                        null, LIP.color());
            } finally { pose.popPose(); }
        }
        for (var item : QuartzStyle.accessories()) {
            if (!QuartzStyle.equipped(state.id, item)) continue;
            boolean glow = item.id().equals("halo");
            var type = item.id().equals("web_suit") ? RenderTypes.entityCutout(SUIT) : material;
            var groups = MODELS.computeIfAbsent(item.id(), key -> item.boxes().stream().map(QuartzStyleLayer::bake)
                    .collect(java.util.stream.Collectors.groupingBy(Piece::bone, java.util.LinkedHashMap::new,
                            java.util.stream.Collectors.toList())));
            for (var group : groups.entrySet()) {
                String attachment = group.getKey();
                pose.pushPose();
                try {
                    model.root().translateAndRotate(pose);
                    float age = state.ageInTicks;
                    if (glow) {
                        pose.translate(model.head.x / 16f, model.head.y / 16f, model.head.z / 16f);
                        pose.translate(0, .015f * (float) Math.sin(age * .145f), 0);
                        pose.mulPose(new Matrix4f().rotationY(age * .047f));
                    } else {
                        bone(model, attachment).translateAndRotate(pose);
                        if (attachment.equals("wingL") || attachment.equals("wingR")) {
                            float side = attachment.equals("wingL") ? -1 : 1;
                            float beat = (float) Math.cos(age * .30f);
                            float lift = (float) Math.sin(age * .30f - .38f);
                            pose.translate(side * .082f, .105f, .164f);
                            pose.mulPose(new Matrix4f().rotationY(-side * (.43f + .24f * beat)));
                            pose.mulPose(new Matrix4f().rotationZ(-side * (.12f + .095f * lift)));
                            pose.scale(.78f, .87f, .86f);
                        }
                    }
                    for (Piece piece : group.getValue()) {
                        collector.order(1).submitModelPart(piece.part(), pose, type, glow ? 0xF000F0 : light,
                                OverlayTexture.NO_OVERLAY, null, piece.color());
                    }
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
