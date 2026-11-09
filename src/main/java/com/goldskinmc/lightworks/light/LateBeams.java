package com.goldskinmc.lightworks.light;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.compat.Shaders;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * Under a shader pack, the visible beams, lens glows, coloured light and the haze inside a beam are recorded while the
 * world is drawn and drawn after the pack has finished the frame, on its final picture, as without a pack, tested
 * against the main depth buffer, which every pack tested (Complementary, BSL, MakeUp, Solas, Bliss, Photon) leaves
 * holding the scene's depth. So they look alike in every pack, keep their colour, and the pack's sky no longer paints
 * over them; before, each pack's own programs drew them, some dark, some not at all (the per-pack routes, kept for
 * when this is off). The beams are tested against the depth from before translucent blocks ({@link SolidDepth}): some
 * packs leave glass and water in the final depth, which cut beams off behind every pane and under every water surface.
 * The colour layers keep the final depth, as without a pack. Client config {@code beamsAfterShaderPack}. Only once the hook after the level
 * ({@code mixin/GameRendererMixin}) has been seen to run: should it not apply, the beams keep the per-pack routes.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class LateBeams {

    private record Recorded(Matrix4f pose, Vector3f lens, Vector3f axis, ClientBeam beam, int rgb) {
    }

    private static final List<Recorded> FRAME = new ArrayList<>();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static boolean haveMatrices;
    private static net.minecraft.world.phys.Vec3 camera = net.minecraft.world.phys.Vec3.ZERO;
    private static float partialTick;
    private static boolean drawing;
    /** Set the first time the hook after the level runs: the mixin applied. */
    private static boolean hookSeen;

    private LateBeams() {
    }

    /** True while a shader pack draws the world and this pass takes the beams over from it. */
    public static boolean active() {
        return hookSeen && Shaders.inUse() && LightworksClientConfig.beamsAfterShaderPack();
    }

    /** True while beams should be recorded rather than drawn. */
    static boolean capturing() {
        return !drawing && active();
    }

    /** True while this pass draws: the beams take their usual vanilla route then. */
    public static boolean drawing() {
        return drawing;
    }

    static void record(PoseStack poseStack, Vector3f lens, Vector3f axis, ClientBeam beam, int rgb) {
        FRAME.add(new Recorded(new Matrix4f(poseStack.last().pose()), new Vector3f(lens), new Vector3f(axis), beam, rgb));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES && active()) {
            // Where the usual beams are drawn: what they are tested against, before glass and water
            SolidDepth.take(Minecraft.getInstance().getMainRenderTarget());
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            FRAME.clear();
            MODEL_VIEW.set(event.getModelViewMatrix());
            PROJECTION.set(event.getProjectionMatrix());
            camera = event.getCamera().getPosition();
            partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            haveMatrices = true;
        }
    }

    /** Called right after the level is drawn (the shader pack's final pass included), before the hand and the GUI. */
    public static void drawRecorded() {
        hookSeen = true;
        if (!haveMatrices || !active()) {
            FRAME.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        drawing = true;
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f(PROJECTION), VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.identity().mul(MODEL_VIEW);
            RenderSystem.applyModelViewMatrix();
            mc.getMainRenderTarget().bindWrite(false);
            // The colour and spot layers, off under shader packs otherwise, on the finished picture as without one; first,
            // against the final depth, so that as without a pack they stay off faces behind glass and water
            ProjectedTint.drawSafely(camera, new PoseStack(), partialTick);
            // The beams against the depth from before translucent blocks, so they show through glass and water as usual
            SolidDepth.putBack(mc.getMainRenderTarget());
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            PoseStack poseStack = new PoseStack();
            for (Recorded beam : FRAME) {
                poseStack.last().pose().set(beam.pose());
                BeamVisual.render(poseStack, buffers, beam.lens(), beam.axis(), beam.beam(), beam.rgb());
            }
            BeamVisual.flush(buffers);
            InsideHaze.drawSafely(mc.gameRenderer.getMainCamera(), partialTick);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            drawing = false;
            FRAME.clear();
        }
    }
}
