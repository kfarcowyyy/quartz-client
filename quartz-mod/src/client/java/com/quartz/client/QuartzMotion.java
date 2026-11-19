package com.quartz.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class QuartzMotion {
    private static QuartzStyle.Emote active;
    private static long started;
    private static CameraType previous;
    private static int avatar;
    private static Object world;
    private record Play(QuartzStyle.Emote emote, long when) {}
    private static final java.util.Map<Integer, Play> REMOTE = new java.util.HashMap<>();

    static void remote(int actor, QuartzStyle.Emote emote) {
        if (emote == null) REMOTE.remove(actor);
        else REMOTE.put(actor, new Play(emote, System.nanoTime()));
    }

    static void clearRemote() { REMOTE.clear(); }

    private QuartzMotion() {}

    static void play(QuartzStyle.Emote emote) {
        Minecraft client = Minecraft.getInstance();
        if (emote == null || client.player == null || client.level == null) return;
        if (active == null) {
            previous = client.options.getCameraType();
            if (previous == CameraType.FIRST_PERSON) client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        avatar = client.player.getId();
        world = client.level;
        active = emote;
        started = System.nanoTime();
        QuartzSocial.play(emote);
    }

    static void tick(Minecraft client) {
        long now = System.nanoTime();
        REMOTE.values().removeIf(play -> now - play.when() >= (long) (play.emote().seconds() * 1_000_000_000d));
        if (active == null) return;
        if (client.level == world && client.player != null && client.player.getId() == avatar
                && elapsed() < active.seconds() && client.player.isAlive()) return;
        active = null;
        world = null;
        if (previous == CameraType.FIRST_PERSON && client.options.getCameraType() == CameraType.THIRD_PERSON_BACK)
            client.options.setCameraType(previous);
        previous = null;
    }

    private static double elapsed() { return (System.nanoTime() - started) / 1_000_000_000d; }
    private static float smooth(float value) {
        float x = Math.max(0, Math.min(1, value));
        return x * x * (3 - 2 * x);
    }
    private static float blend(float from, float to, float amount) { return from + (to - from) * amount; }
    private static void turn(ModelPart part, float x, float y, float z, float mix) {
        part.xRot = blend(part.xRot, x, mix);
        part.yRot = blend(part.yRot, y, mix);
        part.zRot = blend(part.zRot, z, mix);
    }

    static boolean shouting(int id) {
        if (QuartzStyle.local(id) && id == avatar && active != null && active.id().equals("boo")) {
            double t = elapsed() / active.seconds();
            return t > .44 && t < .84;
        }
        Play current = REMOTE.get(id);
        if (current == null || !current.emote().id().equals("boo")) return false;
        double t = (System.nanoTime() - current.when()) / (current.emote().seconds() * 1_000_000_000d);
        return t > .44 && t < .84;
    }

    public static void apply(PlayerModel p, AvatarRenderState state) {
        if (QuartzStyle.hasSuit(state.id)) {
            p.jacket.visible = false;
            p.rightSleeve.visible = false;
            p.leftSleeve.visible = false;
            p.rightPants.visible = false;
            p.leftPants.visible = false;
        }
        Play remote = REMOTE.get(state.id);
        boolean local = QuartzStyle.local(state.id) && state.id == avatar;
        QuartzStyle.Emote emote = local ? active : remote == null ? null : remote.emote();
        if (emote == null) return;
        float seconds = local ? (float) elapsed() : (System.nanoTime() - remote.when()) / 1_000_000_000f;
        float duration = (float) emote.seconds(), t = seconds / duration;
        if (t >= 1) return;
        float mix = smooth(seconds / .21f) * smooth((duration - seconds) / .32f);
        float pulse = (float) Math.sin(seconds * 7.2);
        float stomp = (float) Math.abs(Math.sin(seconds * 5.7));
        switch (emote.id()) {
            case "dab" -> {
                turn(p.leftArm, -1.14f, -.22f, -1.65f, mix);
                turn(p.rightArm, -1.5f, .92f, .56f, mix);
                turn(p.head, .48f, -.52f, -.28f, mix);
            }
            case "hello" -> {
                turn(p.rightArm, -2.55f, .1f, .4f + .26f * pulse, mix);
                turn(p.head, .04f, -.15f + .1f * pulse, .1f, mix);
            }
            case "happy" -> {
                turn(p.rightArm, -2.38f, -.16f, .42f + .53f * pulse, mix);
                turn(p.leftArm, -2.38f, .16f, -.42f + .53f * pulse, mix);
                turn(p.rightLeg, -.4f * pulse, 0, .13f, mix);
                turn(p.leftLeg, .4f * pulse, 0, -.13f, mix);
                p.root().y -= 5f * stomp * mix;
            }
            case "greedy" -> {
                float step = (float) Math.sin(seconds * 8);
                turn(p.rightArm, -1.38f + .75f * step, -.13f, .46f, mix);
                turn(p.leftArm, -1.38f - .75f * step, .13f, -.46f, mix);
                turn(p.rightLeg, .6f * step, 0, .1f, mix);
                turn(p.leftLeg, -.6f * step, 0, -.1f, mix);
                turn(p.head, .14f + .13f * step, -.17f * step, 0, mix);
                p.root().y -= 2.4f * stomp * mix;
            }
            case "six_seven" -> {
                turn(p.rightArm, -1.36f - .39f * pulse, -.24f, .31f, mix);
                turn(p.leftArm, -1.36f + .39f * pulse, .24f, -.31f, mix);
                turn(p.head, .14f * pulse, .18f * pulse, .13f * pulse, mix);
            }
            case "zombie_rise" -> {
                float lift = smooth((t - .09f) / .50f);
                p.root().y += 24 * (1 - lift) * smooth(seconds / .23f);
                turn(p.rightArm, -1.65f + .28f * pulse, -.09f, .18f, mix);
                turn(p.leftArm, -1.65f - .28f * pulse, .09f, -.18f, mix);
                turn(p.rightLeg, -.75f * (1-lift), 0, .1f, mix);
                turn(p.leftLeg, .56f * (1-lift), 0, -.1f, mix);
                turn(p.head, .48f * (1-lift), .28f * pulse, .16f, mix);
            }
            case "lose_your_head" -> {
                float lift = smooth((t - .22f) / .12f);
                float hold = smooth((t - .34f) / .19f) * smooth((1 - t) / .15f);
                turn(p.rightArm, blend(-2.53f, -1.03f, hold), .2f, blend(.51f, .06f, hold), mix);
                turn(p.leftArm, -2.52f + 2.02f * hold, -.2f, -.5f, mix);
                var hand = new org.joml.Vector3f(0, 10, 0)
                        .rotateX(p.rightArm.xRot).rotateY(p.rightArm.yRot).rotateZ(p.rightArm.zRot);
                p.head.x = blend(p.head.x, p.rightArm.x + hand.x, hold);
                p.head.y = blend(p.head.y - 4f * lift * (1 - hold) * mix, p.rightArm.y + hand.y, hold);
                p.head.z = blend(p.head.z, p.rightArm.z + hand.z, hold);
                turn(p.head, .15f - .23f * hold, -.29f * hold, .21f * hold, mix);
            }
            case "boo" -> {
                float reveal = smooth((t - .36f) / .10f);
                turn(p.rightArm, blend(-2.36f, -2.94f, reveal), blend(.6f, -.13f, reveal),
                        blend(.64f, .61f, reveal), mix);
                turn(p.leftArm, blend(-2.36f, -2.94f, reveal), blend(-.6f, .13f, reveal),
                        blend(-.64f, -.61f, reveal), mix);
                turn(p.head, blend(.38f, -.18f, reveal), 0, 0, mix);
                p.root().y -= 3f * (float) Math.sin(Math.PI * reveal) * mix;
            }
        }
    }
}
