package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.beam.BeamFrame;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The cosmetic beam: a faint additive square beam from the lens to where it stops. It needs no client
 * mods, so it is what players without LambDynamicLights see of a moving light. Drawn in the light block's
 * own coordinates (block from 0 to 1 on each axis). Its opacity follows the light's brightness and the
 * client config ({@code beamVisual}, {@code beamVisualWithShaders}). Client only.
 */
public final class BeamVisual {
    /** Opacity at the lens; it fades with the beam's falloff. Additive, so this is brightness. */
    private static final float ALPHA = 0.18f;
    /** How much of the beam daylight washes out under open sky at noon, like a real searchlight's. */
    private static final float DAY_FADE = 0.7f;
    /** How far a dyed beam is lifted toward white, so dark dyes still read as light. */
    private static final float WHITE_MIX = 0.3f;
    /** Spread (tangent of the half-angle) of a 10-degree beam: beams up to this wide keep their full haze. */
    private static final double NARROW_SPREAD = Math.tan(Math.toRadians(10));
    /** How much of the beam's brightness is lost over its full range, on top of its falloff. */
    private static final float HAZE = 0.6f;
    private static final float GLASS_LIFT = 0.01f;
    private static final Vector3f NORMAL = new Vector3f();
    private static final Vector3f EDGE = new Vector3f();
    private static final Vector3f VIEW = new Vector3f();
    /** Neighbouring rays whose lengths differ by more than this (blocks) are drawn as a torn side. */
    private static final float TORN_LENGTH = 1.5f;
    /** Distance in front of the lens where the beam is at full strength, to compare its falloff against. */
    private static final double LENS_SAMPLE = 1e-3;
    /** Haze closer to the camera than this (blocks) is at its faintest... */
    private static final float NEAR_FADE_START = 0.75f;
    /** ...and from this far on at full strength. */
    private static final float NEAR_FADE_END = 4f;
    private static final float NEAR_FLOOR = 0.1f;
    /** A face seen exactly edge-on keeps this share of its haze. */
    private static final float EDGE_FLOOR = 0.4f;
    /** The closed lens end is fainter than the sides, which overlap along the view. */
    private static final float CAP_ALPHA = 0.5f;
    /**
     * Vanilla's lightning (additive, untextured) without depth writes, so a beam never hides what is drawn
     * after it: particles and water in the world, the whole scene in Ponder, which draws blocks last.
     */
    private static final RenderType BEAM = RenderType.create("lightworks_beam", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setOutputState(RenderStateShard.WEATHER_TARGET)
                    .setCullState(RenderStateShard.CULL)
                    .createCompositeState(false));

    /** Strongest the lens glow gets, looking straight into a beam at full brightness. */
    private static final float GLOW_ALPHA = 0.5f;
    /** Its size: some of the lens round it, and more with distance, so it stays a similar size on screen. */
    private static final float GLOW_LENS = 2.5f;
    private static final float GLOW_PER_BLOCK = 0.06f;
    /** Drawn this far in front of the lens, toward the eye, so the lamp's own frame does not cut it. */
    private static final float GLOW_LIFT = 0.3f;
    private static final int GLOW_SEGMENTS = 16;
    /** Brightest share of the glow, its core, out to this fraction of its size. */
    private static final float GLOW_CORE = 0.3f;

    /**
     * The glow's own render type: the beam's, drawn over everything, since round a low lamp the ground would cut it in
     * half. Whether a wall hides it is decided by {@link #seen} instead.
     */
    private static final RenderType GLOW = RenderType.create("lightworks_lens_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 512, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.WEATHER_TARGET)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));
    /** How finely the line from the eye to the lens is checked for blocks, in blocks. */
    private static final double SIGHT_STEP = 0.25;

    private BeamVisual() {
    }

    /**
     * The beam as a square truncated pyramid from the lens, its four sides made of the traced corner and
     * edge-middle rays ({@link ClientBeam#cornerLength}), so a beam partly blocked by a wall stops on it
     * there. Its start is closed with the lens-sized square. It fades with the beam's own falloff, so it
     * stays visible right up to where it lands.
     *
     * @param lens where the beam leaves the light, in block coordinates
     * @param axis unit vector the beam points along, in block coordinates
     * @param rgb  beam colour, 0xRRGGBB
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vector3f lens, Vector3f axis,
            ClientBeam beam, int rgb) {
        if (ClientSafety.works(ClientSafety.BEAMS)) {
            try {
                draw(poseStack, buffers, lens, axis, beam, rgb);
                glow(poseStack, buffers, lens, axis, beam, rgb);
            } catch (RuntimeException e) {
                ClientSafety.failed(ClientSafety.BEAMS, e);
            }
        }
    }

    private static void draw(PoseStack poseStack, MultiBufferSource buffers, Vector3f lens, Vector3f axis,
            ClientBeam beam, int rgb) {
        float alpha = ALPHA * LightworksClientConfig.beamAlpha() * beam.brightness() / 15f
                * (1 - DAY_FADE * daylight() * beam.skyExposure());
        if (beam.length() <= 0 || alpha <= 0) {
            return;
        }
        float red = mix((rgb >> 16 & 0xFF) / 255f);
        float green = mix((rgb >> 8 & 0xFF) / 255f);
        float blue = mix((rgb & 0xFF) / 255f);
        ConeFalloff shape = beam.shape();
        double[] su = BeamFrame.side(axis.x, axis.y, axis.z);
        double[] sv = BeamFrame.up(axis.x, axis.y, axis.z, su);
        Vector3f u = new Vector3f((float) su[0], (float) su[1], (float) su[2]);
        Vector3f v = new Vector3f((float) sv[0], (float) sv[1], (float) sv[2]);
        float spread = (float) Math.tan(BeamTrace.visibleHalfAngle(shape));
        // Starts on the lens glass, lifted just off it so the closed end never fights the glass for depth
        lens = new Vector3f(axis).mul(GLASS_LIFT).add(lens);
        // A wide beam spreads the same light over more air, so its haze is fainter than a narrow one's. Not in a Ponder
        // scene: it has no darkness to show up against, and the beam is all that shows the light
        if (!beam.preview()) {
            alpha *= (float) Math.min(1, Math.sqrt(NARROW_SPREAD / spread));
        }

        // Lens and far points of every corner ray, and how bright the beam still is at each far point
        int corners = BeamFrame.GRID * BeamFrame.GRID;
        Vector3f[] near = new Vector3f[corners];
        Vector3f[] far = new Vector3f[corners];
        Vector3f[] directions = new Vector3f[corners];
        float[] lengths = new float[corners];
        float[] farAlpha = new float[corners];
        for (int i = 0; i < corners; i++) {
            Vector3f offset = new Vector3f(u).mul(BeamFrame.cornerU(i)).add(new Vector3f(v).mul(BeamFrame.cornerV(i)));
            near[i] = new Vector3f(offset).mul(beam.lensHalf()).add(lens);
            directions[i] = new Vector3f(offset).mul(spread).add(axis).normalize();
            lengths[i] = beam.cornerLength(i);
            far[i] = new Vector3f(directions[i]).mul(lengths[i]).add(near[i]);
            farAlpha[i] = alphaAt(alpha, shape, lengths[i]);
        }

        Matrix4f pose = poseStack.last().pose();
        // Ponder draws block entities before the scene's blocks: there the beam goes in the late phase
        VertexConsumer out = buffers instanceof SuperRenderTypeBuffer phased ? phased.getLateBuffer(BEAM)
                : buffers.getBuffer(BEAM);
        // The four sides, two segments each, walking the square's rim
        int[] rim = {
                BeamFrame.corner(-1, -1), BeamFrame.corner(0, -1), BeamFrame.corner(1, -1), BeamFrame.corner(1, 0),
                BeamFrame.corner(1, 1), BeamFrame.corner(0, 1), BeamFrame.corner(-1, 1), BeamFrame.corner(-1, 0)
        };
        for (int k = 0; k < rim.length; k++) {
            int a = rim[k];
            int b = rim[(k + 1) % rim.length];
            float shorter = Math.min(lengths[a], lengths[b]);
            if (Math.max(lengths[a], lengths[b]) - shorter < TORN_LENGTH) {
                twoSided(out, pose, near[a], near[b], far[b], far[a], alpha, farAlpha[b], farAlpha[a], red, green, blue);
                continue;
            }
            // One ray stopped on something and its neighbour went on: the face runs to where the first
            // stopped, then only a strip along the longer ray goes on, fading in from the obstacle (one
            // skewed face from the obstacle to the far end would lie as a bright sheet along the ground)
            int stop = lengths[a] < lengths[b] ? a : b;
            int go = stop == a ? b : a;
            Vector3f level = new Vector3f(directions[go]).mul(shorter).add(near[go]);
            float levelAlpha = alphaAt(alpha, shape, shorter);
            Vector3f farA = a == go ? level : far[a];
            Vector3f farB = b == go ? level : far[b];
            twoSided(out, pose, near[a], near[b], farB, farA, alpha, b == go ? levelAlpha : farAlpha[b],
                    a == go ? levelAlpha : farAlpha[a], red, green, blue);
            twoSided(out, pose, far[stop], level, far[go], far[stop], 0, farAlpha[go], 0, red, green, blue,
                    levelAlpha);
        }
        // The lens end, closed so the beam has no dark hole where it starts
        Vector3f c0 = near[BeamFrame.corner(-1, -1)];
        Vector3f c1 = near[BeamFrame.corner(1, -1)];
        Vector3f c2 = near[BeamFrame.corner(1, 1)];
        Vector3f c3 = near[BeamFrame.corner(-1, 1)];
        float capAlpha = alpha * CAP_ALPHA;
        twoSided(out, pose, c0, c1, c2, c3, capAlpha, capAlpha, capAlpha, red, green, blue);
    }

    /**
     * A soft halo round the lens while the eye is inside the beam, strongest looking straight down it: what a lamp
     * shining at you looks like, without hiding anything. A solid block between the eye and the lens hides it; glass,
     * leaves and the like do not.
     */
    private static void glow(PoseStack poseStack, MultiBufferSource buffers, Vector3f lens, Vector3f axis,
            ClientBeam beam, int rgb) {
        if (beam.preview() || beam.brightness() <= 0 || beam.length() <= 0 || !LightworksClientConfig.lensGlow()) {
            return;
        }
        Matrix4f pose = poseStack.last().pose();
        // The camera sits at the origin of the space the pose leads to
        Vector3f eye = new Matrix4f(pose).invert().transformPosition(new Vector3f());
        Vector3f toEye = new Vector3f(eye).sub(lens);
        float distance = toEye.length();
        if (distance < 0.5f) {
            return;
        }
        ConeFalloff shape = beam.shape();
        double[] su = BeamFrame.side(axis.x, axis.y, axis.z);
        double[] sv = BeamFrame.up(axis.x, axis.y, axis.z, su);
        double inBeam = shape.lightInBeam(toEye.x, toEye.y, toEye.z, axis.x, axis.y, axis.z,
                su[0], su[1], su[2], sv[0], sv[1], sv[2], beam.lensHalf()) / 15;
        toEye.div(distance);
        float onAxis = (float) Math.pow(Math.max(0, toEye.dot(axis)), 16);
        // Not faded by day, unlike the beam: a lamp pointed at you shows in the sun, as a car's headlights do
        float alpha = GLOW_ALPHA * (float) inBeam * beam.brightness() / 15f * (0.35f + 0.65f * onAxis);
        if (alpha < 0.01f) {
            return;
        }
        // The pose only moves and turns from the camera, so where it puts the lens is the lens from the eye in the world
        Vector3f fromEye = pose.transformPosition(new Vector3f(lens));
        if (!seen(new Vec3(fromEye.x, fromEye.y, fromEye.z))) {
            return;
        }
        float radius = (beam.lensHalf() * GLOW_LENS + distance * GLOW_PER_BLOCK) * (0.6f + 0.4f * onAxis);
        Vector3f centre = new Vector3f(toEye).mul(Math.min(GLOW_LIFT, distance * 0.5f)).add(lens);
        Vector3f right = new Vector3f(toEye).cross(Math.abs(toEye.y) > 0.99f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0))
                .normalize();
        Vector3f up = new Vector3f(right).cross(toEye).normalize();
        float red = mix((rgb >> 16 & 0xFF) / 255f);
        float green = mix((rgb >> 8 & 0xFF) / 255f);
        float blue = mix((rgb & 0xFF) / 255f);
        VertexConsumer out = buffers.getBuffer(GLOW);
        float[] rings = {0, GLOW_CORE, 1};
        float[] ringAlpha = {alpha, alpha * 0.45f, 0};
        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < GLOW_SEGMENTS; i++) {
                Vector3f[] quad = new Vector3f[4];
                float[] a = {ringAlpha[ring], ringAlpha[ring], ringAlpha[ring + 1], ringAlpha[ring + 1]};
                int[] step = {i, i + 1, i + 1, i};
                float[] at = {rings[ring], rings[ring], rings[ring + 1], rings[ring + 1]};
                for (int k = 0; k < 4; k++) {
                    double angle = 2 * Math.PI * step[k] / GLOW_SEGMENTS;
                    quad[k] = new Vector3f(right).mul((float) Math.cos(angle) * radius * at[k])
                            .add(new Vector3f(up).mul((float) Math.sin(angle) * radius * at[k])).add(centre);
                }
                for (int k = 0; k < 4; k++) {
                    out.addVertex(pose, quad[k].x, quad[k].y, quad[k].z).setColor(red, green, blue, a[k]);
                }
            }
        }
    }

    /** False when a block that hides what is behind it stands between the eye and the lens, {@code fromEye} away. */
    private static boolean seen(Vec3 fromEye) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BlockPos lamp = BlockPos.containing(eye.add(fromEye));
        double length = fromEye.length();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (double t = SIGHT_STEP; t < length; t += SIGHT_STEP) {
            Vec3 point = eye.add(fromEye.scale(t / length));
            at.set(point.x, point.y, point.z);
            if (at.equals(lamp)) {
                return true;
            }
            if (level.getBlockState(at).canOcclude()) {
                return false;
            }
        }
        return true;
    }

    /** The light's own falloff, and a little haze, so the far end never outshines the lens. */
    private static float alphaAt(float alpha, ConeFalloff shape, float length) {
        // onAxis is 0 at the origin itself, so compare against just in front of it
        double atLens = shape.onAxis(LENS_SAMPLE);
        double share = atLens > 0 ? Math.min(1, shape.onAxis(length) / atLens) : 0;
        return alpha * (float) (share * Math.max(0, 1 - HAZE * length / shape.range()));
    }

    /** Both windings, so the beam shows from inside and outside whatever the culling state. */
    private static void twoSided(VertexConsumer out, Matrix4f pose, Vector3f nearA, Vector3f nearB, Vector3f farB,
            Vector3f farA, float nearAlpha, float farAlphaB, float farAlphaA, float red, float green, float blue) {
        twoSided(out, pose, nearA, nearB, farB, farA, nearAlpha, farAlphaB, farAlphaA, red, green, blue, nearAlpha);
    }

    /** As above, with its own opacity at {@code nearB}. */
    private static void twoSided(VertexConsumer out, Matrix4f pose, Vector3f nearA, Vector3f nearB, Vector3f farB,
            Vector3f farA, float nearAlphaA, float farAlphaB, float farAlphaA, float red, float green, float blue,
            float nearAlphaB) {
        side(out, pose, nearA, nearB, farB, farA, nearAlphaA, nearAlphaB, farAlphaB, farAlphaA, red, green, blue);
        side(out, pose, nearB, nearA, farA, farB, nearAlphaB, nearAlphaA, farAlphaA, farAlphaB, red, green, blue);
    }

    /** 0 at night to 1 at a clear noon, in the player's world; rain and storms darken it. */
    static float daylight() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : Mth.clamp((level.getSkyDarken(1) - 0.2f) / 0.8f, 0, 1);
    }

    /** Same beam drawn inside a moving contraption, from {@code MovementBehaviour.renderInContraption}. */
    public static void renderInContraption(ContraptionMatrices matrices, MultiBufferSource buffers,
            Vector3f lens, Vector3f axis, ClientBeam beam, int rgb) {
        PoseStack poseStack = matrices.getViewProjection();
        poseStack.pushPose();
        poseStack.last().pose().mul(matrices.getModel().last().pose());
        render(poseStack, buffers, lens, axis, beam, rgb);
        poseStack.popPose();
    }

    private static float mix(float channel) {
        return channel + (1 - channel) * WHITE_MIX;
    }


    /**
     * One quad of the beam. Each corner's opacity is softened by how the camera sees it: haze within a few
     * blocks of the camera fades out (standing in or beside a beam, its faces would otherwise stack into a
     * white wall), and a face seen edge-on fades a little, so the beam's outline is soft.
     */
    private static void side(VertexConsumer out, Matrix4f pose, Vector3f nearA, Vector3f nearB, Vector3f farB,
            Vector3f farA, float nearAlphaA, float nearAlphaB, float farAlphaB, float farAlphaA, float red,
            float green, float blue) {
        // Render thread only: scratch vectors, so 50 beams make no garbage every frame
        Vector3f normal = NORMAL.set(nearB).sub(nearA).cross(EDGE.set(farA).sub(nearA));
        pose.transformDirection(normal);
        if (normal.lengthSquared() > 1e-12f) {
            normal.normalize();
        }
        vertex(out, pose, nearA, normal, nearAlphaA, red, green, blue);
        vertex(out, pose, nearB, normal, nearAlphaB, red, green, blue);
        vertex(out, pose, farB, normal, farAlphaB, red, green, blue);
        vertex(out, pose, farA, normal, farAlphaA, red, green, blue);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, Vector3f at, Vector3f normal, float alpha,
            float red, float green, float blue) {
        // Camera-relative position: the poses here only move and turn, so its length is the distance
        Vector3f view = pose.transformPosition(VIEW.set(at));
        float distance = view.length();
        float near = Mth.clamp((distance - NEAR_FADE_START) / (NEAR_FADE_END - NEAR_FADE_START), 0, 1);
        near = NEAR_FLOOR + (1 - NEAR_FLOOR) * near * near * (3 - 2 * near);
        float facing = distance > 1e-4f ? Math.abs(view.dot(normal)) / distance : 1;
        float edge = EDGE_FLOOR + (1 - EDGE_FLOOR) * facing;
        out.addVertex(pose, at.x, at.y, at.z).setColor(red, green, blue, alpha * near * edge);
    }
}
