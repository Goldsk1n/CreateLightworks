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
    private static final float DAY_FADE = 0.8f;
    /** How far a dyed beam is lifted toward white, so dark dyes still read as light. */
    private static final float WHITE_MIX = 0.3f;
    /** Spread (tangent of the half-angle) of a 10-degree beam: beams up to this wide keep their full haze. */
    private static final double NARROW_SPREAD = Math.tan(Math.toRadians(10));
    /** How much of the beam's brightness is lost over its full range, on top of its falloff. */
    private static final float HAZE = 0.6f;
    private static final float GLASS_LIFT = 0.01f;
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
                    .createCompositeState(false));

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
        float spread = (float) Math.tan(ClientBeam.visibleHalfAngle(shape));
        // Starts on the lens glass, lifted just off it so the closed end never fights the glass for depth
        lens = new Vector3f(axis).mul(GLASS_LIFT).add(lens);
        // A wide beam spreads the same light over more air, so its haze is fainter than a narrow one's
        alpha *= (float) Math.min(1, Math.sqrt(NARROW_SPREAD / spread));

        // Lens and far points of every corner ray, and how bright the beam still is at each far point
        int corners = BeamFrame.GRID * BeamFrame.GRID;
        Vector3f[] near = new Vector3f[corners];
        Vector3f[] far = new Vector3f[corners];
        float[] farAlpha = new float[corners];
        for (int i = 0; i < corners; i++) {
            Vector3f offset = new Vector3f(u).mul(BeamFrame.cornerU(i)).add(new Vector3f(v).mul(BeamFrame.cornerV(i)));
            near[i] = new Vector3f(offset).mul(beam.lensHalf()).add(lens);
            Vector3f direction = new Vector3f(offset).mul(spread).add(axis).normalize();
            float length = beam.cornerLength(i);
            far[i] = new Vector3f(direction).mul(length).add(near[i]);
            // The light's own falloff, and a little haze, so the far end never outshines the lens
            farAlpha[i] = alpha * (float) (shape.onAxis(length) / shape.onAxis(0) * (1 - HAZE * length / shape.range()));
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
            // Both windings, so the beam shows from inside and outside whatever the culling state
            side(out, pose, near[a], near[b], far[b], far[a], alpha, farAlpha[b], farAlpha[a], red, green, blue);
            side(out, pose, near[b], near[a], far[a], far[b], alpha, farAlpha[a], farAlpha[b], red, green, blue);
        }
        // The lens end, closed so the beam has no dark hole where it starts
        Vector3f c0 = near[BeamFrame.corner(-1, -1)];
        Vector3f c1 = near[BeamFrame.corner(1, -1)];
        Vector3f c2 = near[BeamFrame.corner(1, 1)];
        Vector3f c3 = near[BeamFrame.corner(-1, 1)];
        float capAlpha = alpha * CAP_ALPHA;
        side(out, pose, c0, c1, c2, c3, capAlpha, capAlpha, capAlpha, red, green, blue);
        side(out, pose, c1, c0, c3, c2, capAlpha, capAlpha, capAlpha, red, green, blue);
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


    private static void side(VertexConsumer out, Matrix4f pose, Vector3f nearA, Vector3f nearB, Vector3f farB,
            Vector3f farA, float nearAlpha, float farAlphaB, float farAlphaA, float red, float green, float blue) {
        out.addVertex(pose, nearA.x, nearA.y, nearA.z).setColor(red, green, blue, nearAlpha);
        out.addVertex(pose, nearB.x, nearB.y, nearB.z).setColor(red, green, blue, nearAlpha);
        out.addVertex(pose, farB.x, farB.y, farB.z).setColor(red, green, blue, farAlphaB);
        out.addVertex(pose, farA.x, farA.y, farA.z).setColor(red, green, blue, farAlphaA);
    }
}
