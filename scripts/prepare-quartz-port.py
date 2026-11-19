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
    replace_in(client / "QuartzHudEditorScreen.java", [
        ("import net.minecraft.client.input.MouseButtonEvent;\n", ""),
        ("mouseClicked(MouseButtonEvent e, boolean doubleClick)", "mouseClicked(double mx, double my, int mouseButton)"),
        ("super.mouseClicked(e, doubleClick)", "super.mouseClicked(mx, my, mouseButton)"),
        ("select(e,", "select(mx, my,"),
        ("select(MouseButtonEvent e,", "select(double mx, double my,"),
        ("mouseDragged(MouseButtonEvent e, double dx, double dy)", "mouseDragged(double mx, double my, int mouseButton, double dx, double dy)"),
        ("super.mouseDragged(e, dx, dy)", "super.mouseDragged(mx, my, mouseButton, dx, dy)"),
        ("mouseReleased(MouseButtonEvent e)", "mouseReleased(double mx, double my, int mouseButton)"),
        ("super.mouseReleased(e)", "super.mouseReleased(mx, my, mouseButton)"),
        ("e.button()", "mouseButton"), ("e.x()", "mx"), ("e.y()", "my"),
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


parser = argparse.ArgumentParser()
parser.add_argument("version")
parser.add_argument("project", type=Path)
args = parser.parse_args()
if args.version in {"1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10"}:
    prepare_remapped(args.project)
    if args.version in {"1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8"}:
        prepare_pre_submit(args.project)
    if args.version in {"1.21.2", "1.21.3", "1.21.4", "1.21.5"}:
        prepare_pre_pipeline(args.project)
    if args.version in {"1.21.2", "1.21.3", "1.21.4"}:
        prepare_older_texture_constructor(args.project)
elif args.version in {"26.1", "26.1.1", "26.1.2", "26.2"}:
    prepare_named(args.project)
    if args.version.startswith("26.1"):
        for source in (args.project / "src").rglob("*.java"):
            replace_in(source, [(".gui.setScreen(", ".setScreen("),
                                (".gui.screen()", ".screen"),
                                (".gui.hud.isHidden()", ".options.hideGui")])
