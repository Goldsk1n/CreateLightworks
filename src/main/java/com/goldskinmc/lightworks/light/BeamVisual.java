package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.beam.BeamFrame;
import com.goldskinmc.lightworks.render.MainTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
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
    /**
     * How much of the beam daylight washes out under open sky at noon, like a real searchlight's. At 70% a beam's colour
     * and aim were hard to read by day (by day, from the side).
     */
    static final float DAY_FADE = 0.5f;
    /** How far a dyed beam is lifted toward white, so dark dyes still read as light. */
    private static final float WHITE_MIX = 0.3f;
    /** Spread (tangent of the half-angle) of a 10-degree beam: beams up to this wide keep their full haze. */
    static final double NARROW_SPREAD = Math.tan(Math.toRadians(10));
    /** How much of the beam's brightness is lost over its full range, on top of its falloff. */
    private static final float HAZE = 0.6f;
    private static final float GLASS_LIFT = 0.01f;
    private static final Vector3f NORMAL = new Vector3f();
    private static final Vector3f EDGE = new Vector3f();
    private static final Vector3f VIEW = new Vector3f();
    /**
     * How far (blocks) a side runs past where its ray stopped on a block. The part inside the block is hidden by the
     * depth test, so the block's own face cuts the beam: a crisp edge along every step and corner, where a side that
     * ended at its ray's stop drew a line across the blocks at whatever angle the rays gave it. Short of a block, so a
     * beam stopped by one shows no more than a sliver behind it.
     */
    private static final float INTO_BLOCK = 0.8f;
    /** Neighbouring rays whose lengths differ by more than this (blocks) are drawn as a torn side. */
    private static final float TORN_LENGTH = 1.5f;
    /** Distance in front of the lens where the beam is at full strength, to compare its falloff against. */
    private static final double LENS_SAMPLE = 1e-3;
    /** Haze closer to the camera than this (blocks) is at its faintest... */
    private static final float NEAR_FADE_START = 0.75f;
    /** ...and from this far on at full strength. */
    private static final float NEAR_FADE_END = 4f;
    private static final float NEAR_FLOOR = 0.1f;
    /**
     * A side within {@link #NEAR_FADE_END} of the camera is drawn in squares about this big (blocks), each corner faded
     * by its own distance: faded only at its far-off corners, a long side the camera flew through stayed at full haze
     * right up to it and came or went across the whole view in one frame (in testing).
     */
    private static final float NEAR_CELL = 0.75f;
    private static final int MAX_NEAR_CELLS = 64;
    /** Points per side of the grid that finds how near the camera a side comes. */
    private static final int NEAR_SAMPLES = 8;
    private static final Vector3f CELL_A = new Vector3f();
    private static final Vector3f CELL_B = new Vector3f();
    private static final Vector3f CELL_C = new Vector3f();
    private static final Vector3f CELL_D = new Vector3f();
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
                    .setOutputState(MainTarget.OUTPUT)
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
                    .setOutputState(MainTarget.OUTPUT)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));
    /** How finely the line from the eye to the lens is checked for blocks, in blocks. */
    private static final double SIGHT_STEP = 0.25;

    private BeamVisual() {
    }

    /**
     * The beam as a square truncated pyramid from the lens, its four sides made of the rays traced round its rim
     * ({@link ClientBeam#rimLength}), so a beam partly blocked by a wall or rough ground stops on it there. Its start is closed with the lens-sized square. It fades with the beam's own falloff, so it
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

        // Lens and far points of every ray round the rim, and how bright the beam still is at each far point
        int rays = BeamFrame.RIM;
        Vector3f[] near = new Vector3f[rays];
        Vector3f[] far = new Vector3f[rays];
        Vector3f[] directions = new Vector3f[rays];
        float[] lengths = new float[rays];
        float[] farAlpha = new float[rays];
        for (int k = 0; k < rays; k++) {
            Vector3f offset = new Vector3f(u).mul((float) BeamFrame.rimU(k)).add(new Vector3f(v).mul((float) BeamFrame.rimV(k)));
            near[k] = new Vector3f(offset).mul(beam.lensHalf()).add(lens);
            directions[k] = new Vector3f(offset).mul(spread).add(axis).normalize();
            lengths[k] = beam.rimLength(k) + (beam.rimHit(k) ? INTO_BLOCK : 0);
            far[k] = new Vector3f(directions[k]).mul(lengths[k]).add(near[k]);
            farAlpha[k] = alphaAt(alpha, shape, lengths[k]);
        }

        Matrix4f pose = poseStack.last().pose();
        // Ponder draws block entities before the scene's blocks: there the beam goes in the late phase. In the End under a
        // shader pack it goes another way, which the pack does not break up (EndPackBeam)
        VertexConsumer out = buffers instanceof SuperRenderTypeBuffer phased ? phased.getLateBuffer(BEAM)
                : !beam.preview() && PackParticleBeam.active()
                        ? PackParticleBeam.wrap(buffers.getBuffer(PackParticleBeam.BEAM))
                : !beam.preview() && PackBeaconBeam.active() ? PackBeaconBeam.wrap(buffers.getBuffer(PackBeaconBeam.BEAM))
                : !beam.preview() && EndPackBeam.active() ? EndPackBeam.wrap(buffers.getBuffer(EndPackBeam.BEAM))
                : buffers.getBuffer(BEAM);
        // The four sides, walking the square's rim from ray to ray
        for (int k = 0; k < rays; k++) {
            int a = k;
            int b = (k + 1) % rays;
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
        Vector3f c0 = near[0];
        Vector3f c1 = near[BeamFrame.RIM_PER_SIDE];
        Vector3f c2 = near[2 * BeamFrame.RIM_PER_SIDE];
        Vector3f c3 = near[3 * BeamFrame.RIM_PER_SIDE];
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
        // Faded by day under open sky as the beam is, never more: a lamp pointed at you still shows in the sun
        float alpha = GLOW_ALPHA * (float) inBeam * beam.brightness() / 15f * (0.35f + 0.65f * onAxis)
                * (1 - DAY_FADE * daylight() * beam.skyExposure());
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
        VertexConsumer out = PackParticleBeam.active() ? PackParticleBeam.wrap(buffers.getBuffer(PackParticleBeam.GLOW))
                : PackBeaconBeam.active() ? PackBeaconBeam.wrap(buffers.getBuffer(PackBeaconBeam.GLOW))
                : EndPackBeam.active() ? EndPackBeam.wrap(buffers.getBuffer(EndPackBeam.GLOW))
                : buffers.getBuffer(GLOW);
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

    /**
     * The beam of a moving light, drawn in the world from its pose this frame rather than inside its contraption: Minecraft
     * skips a contraption whose box is out of view, and its beams went with it when the lamps left the screen.
     *
     * @param poseStack placed at the camera
     * @param lens      where the beam leaves the light, in the world
     * @param axis      unit vector the beam points along, in the world
     */
    public static void renderInWorld(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, Vec3 lens, Vec3 axis,
            ClientBeam beam, int rgb) {
        poseStack.pushPose();
        // Taken off in double, so a beam far from the world's origin stays steady
        poseStack.translate(lens.x - camera.x, lens.y - camera.y, lens.z - camera.z);
        render(poseStack, buffers, new Vector3f(), new Vector3f((float) axis.x, (float) axis.y, (float) axis.z), beam, rgb);
        poseStack.popPose();
    }

    /** Draws what {@link #renderInWorld} queued at once, so none of it waits for the next frame. */
    public static void flush(MultiBufferSource.BufferSource buffers) {
        buffers.endBatch(BEAM);
        buffers.endBatch(GLOW);
        buffers.endBatch(EndPackBeam.BEAM);
        buffers.endBatch(EndPackBeam.GLOW);
        buffers.endBatch(PackParticleBeam.BEAM);
        buffers.endBatch(PackParticleBeam.GLOW);
        buffers.endBatch(PackBeaconBeam.BEAM);
        buffers.endBatch(PackBeaconBeam.GLOW);
    }

    static float mix(float channel) {
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
        int along = 1;
        int across = 1;
        if (nearCamera(pose, normal, nearA, nearB, farB, farA)) {
            along = Mth.clamp(Mth.ceil(Math.max(nearA.distance(farA), nearB.distance(farB)) / NEAR_CELL), 1, MAX_NEAR_CELLS);
            across = Mth.clamp(Mth.ceil(Math.max(nearA.distance(nearB), farA.distance(farB)) / NEAR_CELL), 1, MAX_NEAR_CELLS);
        }
        for (int i = 0; i < along; i++) {
            float s0 = (float) i / along;
            float s1 = (float) (i + 1) / along;
            for (int j = 0; j < across; j++) {
                float t0 = (float) j / across;
                float t1 = (float) (j + 1) / across;
                vertex(out, pose, point(CELL_A, nearA, nearB, farB, farA, s0, t0), normal,
                        blend(nearAlphaA, nearAlphaB, farAlphaB, farAlphaA, s0, t0), red, green, blue);
                vertex(out, pose, point(CELL_B, nearA, nearB, farB, farA, s0, t1), normal,
                        blend(nearAlphaA, nearAlphaB, farAlphaB, farAlphaA, s0, t1), red, green, blue);
                vertex(out, pose, point(CELL_C, nearA, nearB, farB, farA, s1, t1), normal,
                        blend(nearAlphaA, nearAlphaB, farAlphaB, farAlphaA, s1, t1), red, green, blue);
                vertex(out, pose, point(CELL_D, nearA, nearB, farB, farA, s1, t0), normal,
                        blend(nearAlphaA, nearAlphaB, farAlphaB, farAlphaA, s1, t0), red, green, blue);
            }
        }
    }

    /** True when some of the side comes within {@link #NEAR_FADE_END} of the camera, where its haze fades. */
    private static boolean nearCamera(Matrix4f pose, Vector3f viewNormal, Vector3f nearA, Vector3f nearB, Vector3f farB,
            Vector3f farA) {
        // The camera sits at the origin of the space the pose leads to; a side whose plane is that far off is too
        if (Math.abs(pose.transformPosition(VIEW.set(nearA)).dot(viewNormal)) >= NEAR_FADE_END) {
            return false;
        }
        for (int i = 0; i <= NEAR_SAMPLES; i++) {
            for (int j = 0; j <= NEAR_SAMPLES; j++) {
                point(VIEW, nearA, nearB, farB, farA, (float) i / NEAR_SAMPLES, (float) j / NEAR_SAMPLES);
                if (pose.transformPosition(VIEW).length() < NEAR_FADE_END + NEAR_CELL) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The point {@code s} of the way from the lens end to the far end and {@code t} of the way from A to B. */
    private static Vector3f point(Vector3f into, Vector3f nearA, Vector3f nearB, Vector3f farB, Vector3f farA, float s,
            float t) {
        float nx = nearA.x + (nearB.x - nearA.x) * t;
        float ny = nearA.y + (nearB.y - nearA.y) * t;
        float nz = nearA.z + (nearB.z - nearA.z) * t;
        return into.set(nx + (farA.x + (farB.x - farA.x) * t - nx) * s, ny + (farA.y + (farB.y - farA.y) * t - ny) * s,
                nz + (farA.z + (farB.z - farA.z) * t - nz) * s);
    }

    private static float blend(float nearA, float nearB, float farB, float farA, float s, float t) {
        float near = nearA + (nearB - nearA) * t;
        return near + (farA + (farB - farA) * t - near) * s;
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
