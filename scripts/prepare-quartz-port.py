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


parser = argparse.ArgumentParser()
parser.add_argument("version")
parser.add_argument("project", type=Path)
args = parser.parse_args()
if args.version in {"1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10"}:
    prepare_remapped(args.project)
    if args.version in {"1.21.5", "1.21.6", "1.21.7", "1.21.8"}:
        prepare_pre_submit(args.project)
elif args.version in {"26.1", "26.1.1", "26.1.2", "26.2"}:
    prepare_named(args.project)
    if args.version.startswith("26.1"):
        for source in (args.project / "src").rglob("*.java"):
            replace_in(source, [(".gui.setScreen(", ".setScreen("),
                                (".gui.screen()", ".screen"),
                                (".gui.hud.isHidden()", ".options.hideGui")])
