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


parser = argparse.ArgumentParser()
parser.add_argument("version")
parser.add_argument("project", type=Path)
args = parser.parse_args()
if args.version in {"1.21.8", "1.21.9", "1.21.10"}:
    prepare_remapped(args.project)
elif args.version in {"26.1", "26.1.1", "26.1.2", "26.2"}:
    prepare_named(args.project)
    if args.version.startswith("26.1"):
        for source in (args.project / "src").rglob("*.java"):
            replace_in(source, [(".gui.setScreen(", ".setScreen("),
                                (".gui.screen()", ".screen"),
                                (".gui.hud.isHidden()", ".options.hideGui")])
