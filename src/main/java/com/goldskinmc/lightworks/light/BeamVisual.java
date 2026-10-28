package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.LightworksClientConfig;
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
 * The cosmetic beam: a faint additive cone from the lens to where the beam stops. It needs no client
 * mods, so it is what players without LambDynamicLights see of a moving light. Drawn in the light block's
 * own coordinates (block from 0 to 1 on each axis). Its opacity follows the light's brightness and the
 * client config ({@code beamVisual}, {@code beamVisualWithShaders}). Client only.
 */
public final class BeamVisual {
    private static final int SEGMENTS = 16;
    private static final float LENS_RADIUS = 0.3f;
    /** Opacity at the lens; it fades to nothing where the beam stops. Additive, so this is brightness. */
    private static final float ALPHA = 0.18f;
    /** How much of the beam daylight washes out under open sky at noon, like a real searchlight's. */
    private static final float DAY_FADE = 0.8f;
    /** How far a dyed beam is lifted toward white, so dark dyes still read as light. */
    private static final float WHITE_MIX = 0.3f;
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
     * @param lens where the beam leaves the light, in block coordinates
     * @param axis unit vector the beam points along, in block coordinates
     * @param rgb  beam colour, 0xRRGGBB
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vector3f lens, Vector3f axis,
            ClientBeam beam, int rgb) {
        float length = (float) beam.length();
        float alpha = ALPHA * LightworksClientConfig.beamAlpha() * beam.brightness() / 15f
                * (1 - DAY_FADE * daylight() * beam.skyExposure());
        if (length <= 0 || alpha <= 0) {
            return;
        }
        float red = mix((rgb >> 16 & 0xFF) / 255f);
        float green = mix((rgb >> 8 & 0xFF) / 255f);
        float blue = mix((rgb & 0xFF) / 255f);
        // Cone half-angle halfway into the soft edge, where the lit spot visibly ends
        double halfAngle = Math.toRadians(beam.shape().coreDegrees() + beam.shape().edgeDegrees() / 2);
        float endRadius = LENS_RADIUS + length * (float) Math.tan(halfAngle);

        Vector3f helper = Math.abs(axis.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        Vector3f u = new Vector3f(axis).cross(helper).normalize();
        Vector3f v = new Vector3f(axis).cross(u);
        Vector3f end = new Vector3f(lens).add(new Vector3f(axis).mul(length));

        Matrix4f pose = poseStack.last().pose();
        // Ponder draws block entities before the scene's blocks: there the beam goes in the late phase
        VertexConsumer out = buffers instanceof SuperRenderTypeBuffer phased ? phased.getLateBuffer(BEAM)
                : buffers.getBuffer(BEAM);
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            Vector3f n0 = ring(u, v, a0);
            Vector3f n1 = ring(u, v, a1);
            Vector3f lens0 = new Vector3f(n0).mul(LENS_RADIUS).add(lens);
            Vector3f lens1 = new Vector3f(n1).mul(LENS_RADIUS).add(lens);
            Vector3f end0 = new Vector3f(n0).mul(endRadius).add(end);
            Vector3f end1 = new Vector3f(n1).mul(endRadius).add(end);
            // Both windings, so the cone shows from inside and outside whatever the culling state
            quad(out, pose, lens0, lens1, end1, end0, red, green, blue, alpha);
            quad(out, pose, lens1, lens0, end0, end1, red, green, blue, alpha);
        }
    }

    /** 0 at night to 1 at a clear noon, in the player's world; rain and storms darken it. */
    private static float daylight() {
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

    private static Vector3f ring(Vector3f u, Vector3f v, float angle) {
        return new Vector3f(u).mul(Mth.cos(angle)).add(new Vector3f(v).mul(Mth.sin(angle)));
    }

    private static void quad(VertexConsumer out, Matrix4f pose, Vector3f lensA, Vector3f lensB, Vector3f endB,
            Vector3f endA, float red, float green, float blue, float alpha) {
        out.addVertex(pose, lensA.x, lensA.y, lensA.z).setColor(red, green, blue, alpha);
        out.addVertex(pose, lensB.x, lensB.y, lensB.z).setColor(red, green, blue, alpha);
        out.addVertex(pose, endB.x, endB.y, endB.z).setColor(red, green, blue, 0f);
        out.addVertex(pose, endA.x, endA.y, endA.z).setColor(red, green, blue, 0f);
    }
}
