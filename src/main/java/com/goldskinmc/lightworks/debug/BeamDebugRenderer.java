package com.goldskinmc.lightworks.debug;

import com.goldskinmc.lightworks.Lightworks;
import com.goldskinmc.lightworks.LightworksConfig;
import com.goldskinmc.lightworks.compat.DynamicLights;
import com.goldskinmc.lightworks.LightworksClientConfig;
import com.goldskinmc.lightworks.content.directional.DirectionalLightBlockEntity;
import com.goldskinmc.lightworks.host.BeamPose;
import com.goldskinmc.lightworks.light.BeamSource;
import com.goldskinmc.lightworks.light.BeamTrace;
import com.goldskinmc.lightworks.light.ClientBeams;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Beam paths, drawn with the F3 screen open and the client config {@code debugOverlay} on. Each beam (placed,
 * on a vehicle or on a contraption)
 * as a line from lens to hit point with a box around the hit block, plus details of the searchlight under
 * the crosshair.
 */
@EventBusSubscriber(modid = Lightworks.MOD_ID, value = Dist.CLIENT)
public final class BeamDebugRenderer {
    private static final double RANGE = 48;
    private static final double MAX_VIEW_DISTANCE_SQR = 96 * 96;

    private BeamDebugRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null || mc.player == null
                || !mc.getDebugOverlay().showDebugScreen() || !LightworksClientConfig.debugOverlay()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (BeamSource light : ClientBeams.snapshot()) {
            // Render pose, so a beam on a vehicle or contraption is drawn where it is this frame
            BeamPose beam = light.renderBeam(partialTick);
            if (beam.origin().distanceToSqr(camera) > MAX_VIEW_DISTANCE_SQR) {
                continue;
            }
            BeamTrace.Hit hit = BeamTrace.trace(mc.level, beam.origin(), beam.direction(), RANGE);
            line(poseStack, lines, beam.origin(), hit.end(), 1f, 0.85f, 0.2f);
            if (hit.block() != null) {
                LevelRenderer.renderLineBox(poseStack, lines, new AABB(hit.block()).inflate(0.002),
                        1f, 0.4f, 0.1f, 1f);
            }
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !LightworksClientConfig.debugOverlay()) {
            return;
        }
        event.getRight().add("");
        event.getRight().add("[Lightworks] beams tracked: " + ClientBeams.count());
        event.getRight().add("Server light blocks: " + (LightworksConfig.serverLightBlocks() ? "on" : "off"));
        event.getRight().add(DynamicLights.status());
        if (mc.hitResult instanceof BlockHitResult target && target.getType() == HitResult.Type.BLOCK) {
            BlockEntity be = mc.level.getBlockEntity(target.getBlockPos());
            if (be instanceof DirectionalLightBlockEntity light) {
                BeamPose beam = light.tickBeam();
                BeamTrace.Hit hit = BeamTrace.trace(mc.level, beam.origin(), beam.direction(), RANGE);
                event.getRight().add(String.format("Beam %s, aim %s, brightness %d, from %s%s",
                        light.mountFacing().getSerializedName(), light.aim(), light.brightness(),
                        format(beam.origin()), beam.moving() ? " (on vehicle)" : ""));
                event.getRight().add(hit.block() == null
                        ? String.format("No hit within %.0f blocks", RANGE)
                        : String.format("Hits %s at %.2f blocks, block light %d", shortPos(hit.block()),
                                hit.distance(), lightInFrontOf(mc, hit, beam.direction())));
            }
        }
    }

    /** Block light in the air the beam passed through just before it hit. */
    private static int lightInFrontOf(Minecraft mc, BeamTrace.Hit hit, Vec3 direction) {
        BlockPos air = BlockPos.containing(hit.end().subtract(direction.normalize().scale(0.5)));
        return mc.level.getBrightness(LightLayer.BLOCK, air);
    }

    private static void line(PoseStack poseStack, VertexConsumer lines, Vec3 from, Vec3 to,
            float r, float g, float b) {
        PoseStack.Pose pose = poseStack.last();
        Vec3 normal = to.subtract(from).normalize();
        lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, 1f).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        lines.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, 1f).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static String format(Vec3 v) {
        return String.format("%.2f, %.2f, %.2f", v.x, v.y, v.z);
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
