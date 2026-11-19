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
        return x * x * x * (x * (6 * x - 15) + 10);
    }
    private static float blend(float from, float to, float amount) { return from + (to - from) * amount; }
    private static void turn(ModelPart part, float x, float y, float z, float mix) {
        part.xRot += (float) Math.IEEEremainder(x - part.xRot, Math.TAU) * mix;
        part.yRot += (float) Math.IEEEremainder(y - part.yRot, Math.TAU) * mix;
        part.zRot += (float) Math.IEEEremainder(z - part.zRot, Math.TAU) * mix;
    }

    static float shout(int id) {
        float t;
        if (QuartzStyle.local(id) && id == avatar && active != null && active.id().equals("boo")) {
            t = (float) (elapsed() / active.seconds());
        } else {
            Play current = REMOTE.get(id);
            if (current == null || !current.emote().id().equals("boo")) return 0;
            t = (float) ((System.nanoTime() - current.when()) / (current.emote().seconds() * 1_000_000_000d));
        }
        return smooth((t - .44f) / .055f) * smooth((.77f - t) / .19f);
    }

    private record BooPose(float t, float armX, float armY, float armZ, float headX,
                           float chestX, float legX, float lift) {}

    private static final BooPose[] BOO = {
            new BooPose(0f, -.42f, .12f, .18f, .04f, 0f, 0f, 0f),
            new BooPose(.23f, -2.46f, -.20f, -.36f, .37f, .24f, -.35f, 1.3f),
            new BooPose(.40f, -2.53f, -.22f, -.39f, .45f, .31f, -.40f, 1.8f),
            new BooPose(.49f, -2.84f, .09f, 1.04f, -.27f, -.15f, .26f, -3.4f),
            new BooPose(.60f, -2.63f, .18f, .88f, -.14f, -.07f, -.16f, -.5f),
            new BooPose(.73f, -2.36f, .12f, .78f, -.08f, .03f, -.12f, .5f),
            new BooPose(1f, -.35f, .04f, .14f, .05f, 0f, 0f, 0f)
    };

    private static void boo(PlayerModel p, float t, float mix) {
        for (int i = 1; i < BOO.length; i++) {
            BooPose from = BOO[i - 1], to = BOO[i];
            if (t > to.t()) continue;
            float f = smooth((t - from.t()) / (to.t() - from.t()));
            float armX = blend(from.armX(), to.armX(), f);
            float armY = blend(from.armY(), to.armY(), f);
            float armZ = blend(from.armZ(), to.armZ(), f);
            turn(p.rightArm, armX, armY, armZ, mix);
            turn(p.leftArm, armX, -armY, -armZ, mix);
            turn(p.head, blend(from.headX(), to.headX(), f), 0f, 0f, mix);
            turn(p.body, blend(from.chestX(), to.chestX(), f), 0f, 0f, mix);
            float leg = blend(from.legX(), to.legX(), f);
            turn(p.rightLeg, leg, -.04f, .09f, mix);
            turn(p.leftLeg, leg * .8f, .04f, -.09f, mix);
            p.root().y += blend(from.lift(), to.lift(), f) * mix;
            return;
        }
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
        float mix = smooth(seconds / .30f) * smooth((duration - seconds) / .43f);
        float pulse = (float) Math.sin(seconds * 7.2f);
        float bounce = (1f - (float) Math.cos(seconds * 11f)) * .5f;
        switch (emote.id()) {
            case "dab" -> {
                float hit = smooth((t - .12f) / .20f);
                float lean = mix * hit;
                turn(p.leftArm, -1.20f, -.32f, -1.58f, lean);
                turn(p.rightArm, -1.56f, .76f, .51f, lean);
                turn(p.head, .56f, -.58f, -.27f, lean);
                turn(p.body, .13f, .18f, -.16f, lean);
                turn(p.rightLeg, -.14f, -.12f, .15f, lean);
                turn(p.leftLeg, .19f, .10f, -.12f, lean);
            }
            case "hello" -> {
                float wave = (float) Math.sin(seconds * 9.0f) * smooth((t - .13f) / .15f);
                turn(p.rightArm, -2.52f + .11f * wave, .14f, .46f + .32f * wave, mix);
                turn(p.leftArm, -.25f, -.05f, -.12f, mix);
                turn(p.body, 0f, -.09f, .05f, mix);
                turn(p.head, .02f + .055f * wave, -.13f, .13f, mix);
            }
            case "happy" -> {
                float sway = (float) Math.sin(seconds * 7.0f);
                turn(p.rightArm, -2.40f, -.14f, .44f + .63f * sway, mix);
                turn(p.leftArm, -2.40f, .14f, -.44f + .63f * sway, mix);
                turn(p.rightLeg, -.52f * bounce, 0f, .16f, mix);
                turn(p.leftLeg, -.38f * bounce, 0f, -.16f, mix);
                turn(p.body, -.10f * bounce, 0f, .12f * sway, mix);
                turn(p.head, -.12f + .09f * bounce, 0f, -.07f * sway, mix);
                p.root().y -= 4.8f * bounce * mix;
            }
            case "greedy" -> {
                float reach = .5f - .5f * (float) Math.cos(seconds * 7.5f);
                float arm = blend(-.95f, -1.98f, reach);
                float spread = blend(.54f, .30f, reach);
                turn(p.rightArm, arm, -.13f, spread, mix);
                turn(p.leftArm, arm, .13f, -spread, mix);
                turn(p.rightLeg, -.14f - .18f * reach, -.12f, .15f, mix);
                turn(p.leftLeg, -.14f - .18f * reach, .12f, -.15f, mix);
                turn(p.body, .08f + .22f * reach, 0f, 0f, mix);
                turn(p.head, .12f + .24f * reach, 0f, 0f, mix);
                p.root().y += .9f * reach * mix;
            }
            case "six_seven" -> {
                float shift = (float) Math.sin(seconds * 8.2f);
                turn(p.rightArm, -1.51f - .57f * shift, -.22f, .37f, mix);
                turn(p.leftArm, -1.51f + .57f * shift, .22f, -.37f, mix);
                turn(p.body, .05f, .14f * shift, -.09f * shift, mix);
                turn(p.head, .11f * pulse, -.12f * shift, .11f * shift, mix);
                turn(p.rightLeg, -.18f * shift, 0f, .11f, mix);
                turn(p.leftLeg, .18f * shift, 0f, -.11f, mix);
            }
            case "zombie_rise" -> {
                float enter = smooth(t / .16f);
                float rise = smooth((t - .16f) / .49f);
                float crawl = (1f - rise) * enter;
                p.root().y += 21f * crawl;
                turn(p.rightArm, -1.54f + .30f * pulse * (1f - rise), -.13f, .27f, mix);
                turn(p.leftArm, -1.54f - .30f * pulse * (1f - rise), .13f, -.27f, mix);
                turn(p.rightLeg, -.76f * crawl, 0f, .10f, mix);
                turn(p.leftLeg, .56f * crawl, 0f, -.10f, mix);
                turn(p.body, .33f * crawl, 0f, .10f * pulse * crawl, mix);
                turn(p.head, .40f * crawl - .10f * rise, .15f * pulse, .14f, mix);
            }
            case "lose_your_head" -> {
                float lift = smooth((t - .20f) / .17f);
                float hold = smooth((t - .35f) / .16f) * smooth((1f - t) / .21f);
                turn(p.rightArm, blend(-2.49f, -1.07f, hold), .22f, blend(.43f, .19f, hold), mix);
                turn(p.leftArm, blend(-2.47f, -.69f, hold), -.19f, -.49f, mix);
                turn(p.body, .16f - .19f * hold, -.16f * hold, .05f * hold, mix);
                var hand = new org.joml.Vector3f(0, 10, 0)
                        .rotateX(p.rightArm.xRot).rotateY(p.rightArm.yRot).rotateZ(p.rightArm.zRot);
                p.head.x = blend(p.head.x, p.rightArm.x + hand.x, hold);
                p.head.y = blend(p.head.y - 4f * lift * (1f - hold) * mix, p.rightArm.y + hand.y, hold);
                p.head.z = blend(p.head.z, p.rightArm.z + hand.z, hold);
                turn(p.head, .14f - .23f * hold, -.36f * hold, .23f * hold, mix);
            }
            case "boo" -> boo(p, t, mix);
        }
    }
}
