package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.beam.BeamFrame;
import com.goldskinmc.lightworks.beam.ConeFalloff;
import com.goldskinmc.lightworks.host.BeamPose;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * A faint haze over the whole view while the camera stands inside a beam: the haze of the visible beam fades out
 * within a few blocks of the camera, so it never stands in a white wall, and stepping into a beam otherwise changed
 * nothing until one looked at the lamp. Strongest on the beam's axis and looking towards the lamp, gone at the beam's
 * edge, fading with the beam's own falloff and by day; the beam's colour, washed towards white like the visible beam.
 * Added to the picture after the world is drawn, so it lifts what is dark a little and hides nothing. Client config
 * {@code insideHaze}. Client only.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class InsideHaze {
    /** Most it adds to each channel, standing on the axis of a full-bright narrow beam near its lamp. */
    private static final float MAX = 0.07f;
    /** Haze at the beam's edge: none from this share of the half-width out to the edge itself. */
    private static final double EDGE_START = 0.55;
    /** Looking away from the lamp keeps this share of the haze; looking straight at it, all of it. */
    private static final float AWAY = 0.55f;
    private static final int WHITE = 0xFFF4DC;

    private InsideHaze() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !ClientSafety.works(ClientSafety.BEAMS)
                || !LightworksClientConfig.insideHaze()) {
            return;
        }
        try {
            draw(event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        } catch (RuntimeException e) {
            ClientSafety.failed(ClientSafety.BEAMS, e);
        }
    }

    private static void draw(Camera camera, float partialTick) {
        Vec3 eye = camera.getPosition();
        Vector3f look = camera.getLookVector();
        float day = BeamVisual.daylight();
        float alpha = LightworksClientConfig.beamAlpha();
        float red = 0;
        float green = 0;
        float blue = 0;
        for (BeamSource source : ClientBeams.snapshot()) {
            ClientBeam beam = source.beam();
            if (beam.preview() || beam.brightness() <= 0 || beam.length() <= 0) {
                continue;
            }
            float amount = inside(source.renderBeam(partialTick), beam, eye);
            if (amount <= 0) {
                continue;
            }
            float toLamp = Math.max(0, -look.dot(direction(source.renderBeam(partialTick))));
            amount *= alpha * beam.brightness() / 15f * (AWAY + (1 - AWAY) * toLamp)
                    * (1 - BeamVisual.DAY_FADE * day * beam.skyExposure());
            int rgb = beam.tintRgb() < 0 ? WHITE : beam.tintRgb();
            red += amount * BeamVisual.mix((rgb >> 16 & 0xFF) / 255f);
            green += amount * BeamVisual.mix((rgb >> 8 & 0xFF) / 255f);
            blue += amount * BeamVisual.mix((rgb & 0xFF) / 255f);
        }
        red = Math.min(red, 1) * MAX;
        green = Math.min(green, 1) * MAX;
        blue = Math.min(blue, 1) * MAX;
        if (red + green + blue > 0.003f) {
            fill(red, green, blue);
        }
    }

    private static Vector3f direction(BeamPose pose) {
        Vec3 d = pose.direction();
        return new Vector3f((float) d.x, (float) d.y, (float) d.z);
    }

    /** 0 outside the beam, up to 1 on its axis: how far inside its square the eye is, and the beam's falloff there. */
    private static float inside(BeamPose pose, ClientBeam beam, Vec3 eye) {
        Vec3 a = pose.direction();
        Vec3 fromLens = eye.subtract(pose.origin());
        double along = fromLens.dot(a);
        if (along <= 0 || along >= beam.length()) {
            return 0;
        }
        ConeFalloff shape = beam.shape();
        double[] u = BeamFrame.side(a.x, a.y, a.z);
        double[] v = BeamFrame.up(a.x, a.y, a.z, u);
        double spread = Math.tan(BeamTrace.visibleHalfAngle(shape));
        double half = beam.lensHalf() + along * spread;
        double au = Math.abs(fromLens.x * u[0] + fromLens.y * u[1] + fromLens.z * u[2]);
        double av = Math.abs(fromLens.x * v[0] + fromLens.y * v[1] + fromLens.z * v[2]);
        double out = Math.max(au, av) / half;
        if (out >= 1) {
            return 0;
        }
        double edge = 1 - Mth.clamp((out - EDGE_START) / (1 - EDGE_START), 0, 1);
        // A wide beam spreads the same light over more air, so its haze is fainter, as the visible beam's is
        double wide = Math.min(1, Math.sqrt(BeamVisual.NARROW_SPREAD / spread));
        double falloff = shape.onAxis(along) / shape.onAxis(1e-3);
        return (float) (edge * edge * (3 - 2 * edge) * wide * falloff);
    }

    /** Adds the colour to the whole screen. */
    private static void fill(float red, float green, float blue) {
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            builder.addVertex(-1, -1, 0).setColor(red, green, blue, 1);
            builder.addVertex(1, -1, 0).setColor(red, green, blue, 1);
            builder.addVertex(1, 1, 0).setColor(red, green, blue, 1);
            builder.addVertex(-1, 1, 0).setColor(red, green, blue, 1);
            MeshData mesh = builder.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setProjectionMatrix(projection, sorting);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }
}
