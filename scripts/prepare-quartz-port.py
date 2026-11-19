"""Adapt Quartz's closest source set to the tested Minecraft patch targets."""

import argparse
from pathlib import Path
import re

def replace_in(file, substitutions):
    source = file.read_text(encoding="utf-8")
    for old, new in substitutions:
        source = source.replace(old, new)
    file.write_text(source, encoding="utf-8")


def prepare_remapped(root):
    for file in (root / "src").rglob("*.java"):
        if file.name == "QuartzChatMixin.java":
            replace_in(file, [
                (";Lnet/minecraft/client/gui/Font;IIIZZ)V", ";IIIZ)V"),
                ("GuiGraphics graphics, Font font, int tick", "GuiGraphics graphics, int tick"),
                ("boolean focused, boolean updateCursor, Operation<Void>", "boolean focused, Operation<Void>"),
                ("graphics, font, tick, mouseX, mouseY, focused, updateCursor", "graphics, tick, mouseX, mouseY, focused"),
                ("accept(Lnet/minecraft/client/GuiMessage$Line;IF)V", "accept(IIILnet/minecraft/client/GuiMessage$Line;IF)V"),
                ("line = args.get(0)", "line = args.get(3)"),
                ("float alpha = args.get(2)", "float alpha = args.get(5)"),
                ("args.set(2, alpha", "args.set(5, alpha"),
                ("import net.minecraft.client.gui.Font;\n", ""),
            ])
        if file.name == "QuartzStyleLayer.java":
            replace_in(file, [("net.minecraft.client.renderer.rendertype.RenderTypes",
                              "net.minecraft.client.renderer.RenderType"),
                              ("RenderTypes.", "RenderType.")])
        if file.name == "QuartzButton.java":
            replace_in(file, [("renderContents(GuiGraphics", "renderWidget(GuiGraphics")])
        if file.name == "QuartzHud.java":
            replace_in(file, [(".identifier().withPrefix", ".location().withPrefix")])
        source = file.read_text(encoding="utf-8")
        source = re.sub(r"\bIdentifier\b", "ResourceLocation", source)
        source = source.replace("net.minecraft.client.model.player.PlayerModel", "net.minecraft.client.model.PlayerModel")
        source = source.replace("net/minecraft/client/model/player/PlayerModel", "net/minecraft/client/model/PlayerModel")
        file.write_text(source, encoding="utf-8")


def prepare_named(root):
    client = root / "src/client/java/com/quartz/client"
    replace_in(client / "QuartzModClient.java", [
        ("InputConstants.Type.KEYBOARD", "InputConstants.Type.KEYSYM"),
    ])
    replace_in(client / "QuartzStyleLayer.java", [
        ("MOUTH.color());", "MOUTH.color(), null);"),
        ("LIP.color());", "LIP.color(), null);"),
        ("piece.color());", "piece.color(), null);"),
    ])
    replace_in(client / "QuartzTitleScreen.java", [
        ("new OptionsScreen(this, minecraft.options)", "new OptionsScreen(this, minecraft.options, true)"),
    ])


def prepare_pre_submit(root):
    client = root / "src/client/java/com/quartz/client"
    paths = list((root / "src").rglob("*.java"))
    for file in paths:
        replace_in(file, [("AvatarRenderState", "PlayerRenderState"),
                          (".player.AvatarRenderer", ".player.PlayerRenderer"),
                          ("@Mixin(AvatarRenderer.class)", "@Mixin(PlayerRenderer.class)"),
                          ("net.minecraft.world.entity.player.PlayerSkin", "net.minecraft.client.resources.PlayerSkin")])

    replace_in(client / "QuartzModClient.java", [
        ("KeyMapping.Category.MISC", "KeyMapping.CATEGORY_MISC"),
        ('KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("quartz", "client"))', '"key.categories.quartz.client"'),
    ])
    replace_in(client / "QuartzEmoteWheel.java", [
        ("import net.minecraft.client.input.KeyEvent;\n", ""),
        ("import net.minecraft.client.input.MouseButtonEvent;\n", ""),
        ("keyReleased(KeyEvent event)", "keyReleased(int keyCode, int scanCode, int modifiers)"),
        ("key.matches(event)", "key.matches(keyCode, scanCode)"),
        ("super.keyReleased(event)", "super.keyReleased(keyCode, scanCode, modifiers)"),
        ("mouseReleased(MouseButtonEvent event)", "mouseReleased(double x, double y, int button)"),
        ("key.matchesMouse(event)", "key.matchesMouse(button)"),
        ("super.mouseReleased(event)", "super.mouseReleased(x, y, button)"),
    ])
    for screen in ("QuartzHudEditorScreen.java", "QuartzColorPickerScreen.java"):
        replace_in(client / screen, [
            ("import net.minecraft.client.input.MouseButtonEvent;\n", ""),
            ("mouseClicked(MouseButtonEvent e, boolean doubleClick)", "mouseClicked(double mx, double my, int mouseButton)"),
            ("super.mouseClicked(e, doubleClick)", "super.mouseClicked(mx, my, mouseButton)"),
            ("mouseDragged(MouseButtonEvent e, double dx, double dy)", "mouseDragged(double mx, double my, int mouseButton, double dx, double dy)"),
            ("super.mouseDragged(e, dx, dy)", "super.mouseDragged(mx, my, mouseButton, dx, dy)"),
            ("mouseReleased(MouseButtonEvent e)", "mouseReleased(double mx, double my, int mouseButton)"),
            ("super.mouseReleased(e)", "super.mouseReleased(mx, my, mouseButton)"),
            ("e.button()", "mouseButton"), ("e.x()", "mx"), ("e.y()", "my"),
        ])
    replace_in(client / "QuartzHudEditorScreen.java", [
        ("select(e,", "select(mx, my,"),
        ("select(MouseButtonEvent e,", "select(double mx, double my,"),
    ])
    replace_in(client / "QuartzPacksScreen.java", [
        ("mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick)", "mouseClicked(double mx, double my, int mouseButton)"),
        ("super.mouseClicked(event, doubleClick)", "super.mouseClicked(mx, my, mouseButton)"),
        ("event.button()", "mouseButton"), ("event.x()", "mx"), ("event.y()", "my"),
    ])
    replace_in(client / "QuartzWardrobe.java", [
        ("import net.minecraft.world.entity.player.PlayerModelType;\n", ""),
        ("import net.minecraft.core.ClientAsset;\n", ""),
        ("PlayerModelType.", "PlayerSkin.Model."),
        ("PlayerModelType model", "PlayerSkin.Model model"),
        ("new ClientAsset.ResourceTexture(TEXTURE, TEXTURE), account.cape(), account.elytra()",
         "TEXTURE, account.textureUrl(), account.capeTexture(), account.elytraTexture()"),
    ])
    replace_in(client / "mixin/MouseHandlerMixin.java", [
        ("import net.minecraft.client.input.MouseButtonInfo;\n", ""),
        ('method = "onButton"', 'method = "onPress"'),
        ("long window, MouseButtonInfo input, int action,", "long window, int button, int action, int modifiers,"),
        ("input.button()", "button"),
    ])
    replace_in(client / "mixin/QuartzCapeMixin.java", [
        ("SubmitNodeCollector", "MultiBufferSource"), ('method = "submit(', 'method = "render('),
    ])
    layer = client / "QuartzStyleLayer.java"
    replace_in(layer, [("SubmitNodeCollector", "MultiBufferSource"), ("public void submit(", "public void render(")])
    source = layer.read_text(encoding="utf-8")
    source = re.sub(r"collector\.order\(1\)\.submitModelPart\(([^,]+), pose, ([^,]+), ([^,]+),\s*OverlayTexture\.NO_OVERLAY,\s*null, ([^,]+), null\);",
                    r"\1.render(pose, collector.getBuffer(\2), \3, OverlayTexture.NO_OVERLAY, \4);", source)
    layer.write_text(source, encoding="utf-8")


def prepare_pre_pipeline(root):
    client = root / "src/client/java/com/quartz/client"
    replace_in(client / "QuartzModClient.java", [
        ("import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;",
         "import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;"),
        ("import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;\n", ""),
        ('HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,\n'
         '                ResourceLocation.fromNamespaceAndPath("quartz", "in_game_hud"), (graphics, tickCounter) -> {',
         'HudRenderCallback.EVENT.register((graphics, tickCounter) -> {'),
    ])
    for file in (root / "src").rglob("*.java"):
        replace_in(file, [
            ("import net.minecraft.client.renderer.RenderPipelines;", "import net.minecraft.client.renderer.RenderType;"),
            ("net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED",
             "net.minecraft.client.renderer.RenderType::guiTextured"),
            ("RenderPipelines.GUI_TEXTURED", "RenderType::guiTextured"),
            ("pose.pushMatrix()", "pose.pushPose()"),
            ("pose.popMatrix()", "pose.popPose()"),
        ])
    replace_in(client / "QuartzHud.java", [
        ("y + (height - panel.height() * scale) / 2);", "y + (height - panel.height() * scale) / 2, 0);"),
        ("pose.scale(scale, scale);", "pose.scale(scale, scale, 1);"),
        (", top + 12).scale(0.67f, 0.67f);", ", top + 12, 0);\n                    pose.scale(0.67f, 0.67f, 1);"),
        ("pose.translate(horizontal ? x : x + ARMOR_THICKNESS, y);",
         "pose.translate(horizontal ? x : x + ARMOR_THICKNESS, y, 0);"),
        ("pose.rotate((float) (Math.PI / 2));", "pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));"),
    ])
    replace_in(client / "QuartzTheme.java", [
        ("pose.translate(x, y).scale(0.5f, 0.5f);", "pose.translate(x, y, 0);\n            pose.scale(0.5f, 0.5f, 1);"),
    ])
    chat = client / "mixin/QuartzChatMixin.java"
    replace_in(chat, [
        ("import org.spongepowered.asm.mixin.injection.ModifyArgs;",
         "import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;"),
        ("import org.spongepowered.asm.mixin.injection.invoke.arg.Args;\n", ""),
        ("@Unique private long quartz$frameTime;", "@Unique private long quartz$frameTime;\n    @Unique private int quartz$frameTick;"),
        ("quartz$frameTime = now;", "quartz$frameTime = now;\n        quartz$frameTick = tick;"),
        ("pose.translate(0f, 5f * (1f - quartz$progress(now - quartz$lastArrival)));",
         "pose.translate(0f, 5f * (1f - quartz$progress(now - quartz$lastArrival)), 0);"),
    ])
    source = chat.read_text(encoding="utf-8")
    start = source.index("    @ModifyArgs(")
    end = source.index("    @Unique", start)
    source = source[:start] + '''    @WrapOperation(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;getTimeFactor(I)D"))
    private double quartz$fadeNewMessages(int age, Operation<Double> original) {
        double opacity = original.call(age);
        if (quartz$frameTime == 0L) return opacity;
        Long arrival = quartz$arrivals.get(quartz$frameTick - age);
        return arrival == null ? opacity
                : opacity * (.12f + .88f * quartz$progress(quartz$frameTime - arrival));
    }

''' + source[end:]
    chat.write_text(source, encoding="utf-8")


def prepare_older_texture_constructor(root):
    for file in (root / "src").rglob("*.java"):
        replace_in(file, [
            ("new DynamicTexture(path.getFileName()::toString, image)", "new DynamicTexture(image)"),
            ('new DynamicTexture(() -> "Quartz imported skin", image)', "new DynamicTexture(image)"),
            ('new DynamicTexture(() -> "Modrinth image", image)', "new DynamicTexture(image)"),
        ])


def prepare_entity_based_player_rendering(root):
    client = root / "src/client/java/com/quartz/client"
    for file in (root / "src").rglob("*.java"):
        replace_in(file, [
            ("net.minecraft.client.renderer.entity.state.PlayerRenderState", "net.minecraft.client.player.AbstractClientPlayer"),
            ("net/minecraft/client/renderer/entity/state/PlayerRenderState", "net/minecraft/client/player/AbstractClientPlayer"),
            ("PlayerRenderState", "AbstractClientPlayer"),
            ("state.id", "state.getId()"),
            ("net.minecraft.client.renderer.RenderType::guiTextured, ", ""),
            ("RenderType::guiTextured, ", ""),
        ])
    replace_in(client / "QuartzShotPreview.java", [("id, left, top, 0f, 0f, w, h,", "id, left, top, w, h, 0f, 0f,")])
    replace_in(client / "QuartzWardrobeScreen.java", [("index * 64, 0, 24, 24, 64, 64, 384, 64", "24, 24, index * 64, 0, 64, 64, 384, 64")])
    replace_in(client / "QuartzTheme.java", [("CRYSTAL, x, y, 0, 0, width, height,", "CRYSTAL, x, y, width, height, 0, 0,")])
    replace_in(client / "QuartzPackImages.java", [("0, 0, tw, th, picture.width", "tw, th, 0, 0, picture.width")])
    replace_in(client / "QuartzHud.java", [
        ("HOTBAR_TEXTURE, 0, 0, 0, 0,\n                    interior, ARMOR_THICKNESS,", "HOTBAR_TEXTURE, 0, 0, interior, ARMOR_THICKNESS,\n                    0, 0,"),
        ("HOTBAR_TEXTURE, interior, 0, 181, 0,\n                    1, ARMOR_THICKNESS,", "HOTBAR_TEXTURE, interior, 0, 1, ARMOR_THICKNESS,\n                    181, 0,"),
    ])
    replace_in(client / "QuartzStyleLayer.java", [
        ("RenderLayer<AbstractClientPlayer, PlayerModel>", "RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>"),
        ("RenderLayerParent<AbstractClientPlayer, PlayerModel>", "RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>"),
        ("AbstractClientPlayer state, float yaw, float pitch)", "AbstractClientPlayer state, float swing, float swingAmount, float delta, float age, float yaw, float pitch)"),
        ("state.isInvisible || state.isSpectator", "state.isInvisible() || state.isSpectator()"),
        ("        model.setupAnim(state);\n", ""),
        ("                model.root().translateAndRotate(pose);\n", ""),
        ("    float age = state.ageInTicks;", ""),
        ("bone(PlayerModel model", "bone(PlayerModel<?> model"),
    ])
    replace_in(client / "mixin/QuartzCapeMixin.java", [
        ("AbstractClientPlayer;FF)V", "AbstractClientPlayer;FFFFFF)V"),
        ("AbstractClientPlayer state, float yaw, float pitch,",
         "AbstractClientPlayer state, float swing, float amount, float delta, float age, float yaw, float pitch,"),
    ])
    replace_in(client / "mixin/QuartzPlayerModelMixin.java", [
        ("import org.spongepowered.asm.mixin.Mixin;", "import net.minecraft.world.entity.LivingEntity;\nimport org.spongepowered.asm.mixin.Mixin;"),
        ("setupAnim(Lnet/minecraft/client/player/AbstractClientPlayer;)V", "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"),
        ("quartz$stylePose(AbstractClientPlayer state, CallbackInfo info)", "quartz$stylePose(LivingEntity entity, float swing, float amount, float age, float yaw, float pitch, CallbackInfo info)"),
        ("QuartzMotion.apply((PlayerModel) (Object) this, state);",
         "if (entity instanceof AbstractClientPlayer player) QuartzMotion.apply((PlayerModel<?>) (Object) this, player);"),
    ])
    replace_in(client / "mixin/QuartzAvatarLayerMixin.java", [
        ("import com.quartz.client.QuartzStyleLayer;", """import com.mojang.blaze3d.vertex.PoseStack;
import com.quartz.client.QuartzMotion;
import com.quartz.client.QuartzStyleLayer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.MultiBufferSource;"""),
        ("RenderLayerParent<AbstractClientPlayer, PlayerModel>", "RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>"),
    ])
    file = client / "mixin/QuartzAvatarLayerMixin.java"
    source = file.read_text(encoding="utf-8").rstrip()
    file.write_text(source[:-1] + '''
    @WrapMethod(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void quartz$moveEmotePose(AbstractClientPlayer player, float yaw, float delta, PoseStack pose,
                                     MultiBufferSource buffers, int light, Operation<Void> original) {
        float offset = QuartzMotion.bodyOffset(player);
        if (offset == 0f) {
            original.call(player, yaw, delta, pose, buffers, light);
            return;
        }
        pose.pushPose();
        try {
            pose.translate(0, -offset / 16f, 0);
            original.call(player, yaw, delta, pose, buffers, light);
        } finally {
            pose.popPose();
        }
    }
}
''', encoding="utf-8")
    file = client / "QuartzMotion.java"
    source = file.read_text(encoding="utf-8").replace("PlayerModel p,", "PlayerModel<?> p,")
    source = re.sub(r"(?m)^\s*p\.root\(\)\.y [^\n]*\n", "\n", source)
    pos = source.index("    public static void apply")
    source = source[:pos] + '''    public static float bodyOffset(AbstractClientPlayer player) {
        int id = player.getId();
        Play remote = REMOTE.get(id);
        boolean local = QuartzStyle.local(id) && id == avatar;
        QuartzStyle.Emote emote = local ? active : remote == null ? null : remote.emote();
        if (emote == null) return 0f;
        float seconds = local ? (float) elapsed() : (System.nanoTime() - remote.when()) / 1_000_000_000f;
        float duration = (float) emote.seconds();
        float t = seconds / duration;
        if (t >= 1f) return 0f;
        float mix = smooth(seconds / .30f) * smooth((duration - seconds) / .43f);
        return switch (emote.id()) {
            case "happy" -> -2.4f * (1f - (float) Math.cos(seconds * 11f)) * mix;
            case "greedy" -> {
                float stride = (float) Math.sin(seconds * 11.5f);
                yield -1.6f * stride * stride * mix;
            }
            case "zombie_rise" -> 21f * (1f - smooth((t - .16f) / .49f)) * smooth(t / .16f);
            case "boo" -> {
                float lift = 0f;
                for (int i = 1; i < BOO.length; i++) {
                    BooPose from = BOO[i - 1], to = BOO[i];
                    if (t > to.t()) continue;
                    lift = blend(from.lift(), to.lift(), smooth((t - from.t()) / (to.t() - from.t())));
                    break;
                }
                yield lift * mix;
            }
            default -> 0f;
        };
    }

''' + source[pos:]
    file.write_text(source, encoding="utf-8")


parser = argparse.ArgumentParser()
parser.add_argument("version")
parser.add_argument("project", type=Path)
args = parser.parse_args()
if args.version in {"1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10"}:
    prepare_remapped(args.project)
    if args.version in {"1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8"}:
        prepare_pre_submit(args.project)
    if args.version in {"1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5"}:
        prepare_pre_pipeline(args.project)
    if args.version in {"1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4"}:
        prepare_older_texture_constructor(args.project)
    if args.version in {"1.20.5", "1.20.6", "1.21", "1.21.1"}:
        prepare_entity_based_player_rendering(args.project)
    if args.version in {"1.20.5", "1.20.6"}:
        for file in (args.project / "src").rglob("*.java"):
            replace_in(file, [
                ("net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(", "new net.minecraft.resources.ResourceLocation("),
                ("ResourceLocation.fromNamespaceAndPath(", "new ResourceLocation("),
                ("ResourceLocation.withDefaultNamespace(", 'new ResourceLocation("minecraft", '),
                ("ResourceLocation.parse(", "new ResourceLocation("),
            ])
        client = args.project / "src/client/java/com/quartz/client"
        replace_in(client / "QuartzTitleScreen.java", [
            ("net.minecraft.client.gui.screens.options.OptionsScreen", "net.minecraft.client.gui.screens.OptionsScreen"),
        ])
        style_path = client / "QuartzStyleLayer.java"
        style = style_path.read_text(encoding="utf-8").rstrip()
        style = re.sub(r"(MOUTH|LIP|piece)\.part\(\)\.render\((pose, collector\.getBuffer\(\w+\), [^\n]+?, OverlayTexture\.NO_OVERLAY), \1\.color\(\)\);",
                       r"renderTinted(\1.part(), \2, \1.color());", style)
        style_path.write_text(style[:-1] + """
    private static void renderTinted(ModelPart part, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vertices,
                                     int light, int overlay, int color) {
        part.render(pose, vertices, light, overlay, ((color >>> 16) & 255) / 255f,
                ((color >>> 8) & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
    }
}
""", encoding="utf-8")
    if args.version == "1.20.5":
        replace_in(args.project / "src/main/java/com/quartz/QuartzServerStyle.java", [
            ("context.server()", "context.player().server"),
        ])
elif args.version in {"26.1", "26.1.1", "26.1.2", "26.2"}:
    prepare_named(args.project)
    if args.version.startswith("26.1"):
        for source in (args.project / "src").rglob("*.java"):
            replace_in(source, [(".gui.setScreen(", ".setScreen("),
                                (".gui.screen()", ".screen"),
                                (".gui.hud.isHidden()", ".options.hideGui")])
